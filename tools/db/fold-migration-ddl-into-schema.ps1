# =====================================================================================
# P0+P1 (2026-09-30, "pre-launch: drop upgrade/migration code" plan)
#
#   P0: export the DDL that DataInitializer's migration code applies to OLD databases
#       into a baseline inventory file (so we can prove nothing is lost).
#   P1: fold every one of those columns INTO schema.sql's CREATE TABLE blocks, so a
#       brand-new database gets the exact same shape without any startup migration.
#
# Why fold instead of appending ALTERs: schema.sql is re-executed on every boot
#   (spring.sql.init.mode=always, CREATE TABLE IF NOT EXISTS is idempotent), but a
#   plain "ALTER TABLE ... ADD COLUMN" is NOT idempotent - it would fail on the 2nd
#   run. Folding into the CREATE TABLE keeps schema.sql replayable.
#
# Idempotent: re-running produces the same schema.sql (already-present columns skipped).
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File .\fold-migration-ddl-into-schema.ps1
# =====================================================================================
$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$diPath = Join-Path $repo 'beichen-erp-server\src\main\java\com\beichen\erp\config\DataInitializer.java'
$schemaPath = Join-Path $repo 'beichen-erp-server\src\main\resources\schema.sql'
$invPath = Join-Path $repo 'tools\db\migration-ddl-inventory.txt'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$di = [System.IO.File]::ReadAllText($diPath, [System.Text.Encoding]::UTF8)
$schema = [System.IO.File]::ReadAllText($schemaPath, [System.Text.Encoding]::UTF8)

# ---------- helper: read a balanced (...) argument list starting at $openIdx ----------
function Get-Balanced([string]$s, [int]$openIdx) {
    $depth = 0
    for ($i = $openIdx; $i -lt $s.Length; $i++) {
        $c = $s[$i]
        if ($c -eq '"') {                      # skip a string literal (handles \")
            $i++
            while ($i -lt $s.Length -and $s[$i] -ne '"') {
                if ($s[$i] -eq '\') { $i++ }
                $i++
            }
            continue
        }
        if ($c -eq '(') { $depth++ }
        elseif ($c -eq ')') { $depth--; if ($depth -eq 0) { return $s.Substring($openIdx + 1, $i - $openIdx - 1) } }
    }
    return $null
}
function Split-ColumnDefs([string]$ddl) {
    $d = $ddl -replace '(?i)ALTER\s+TABLE\s+`?\w+`?\s+', ''
    $d = $d -replace '(?i)\bADD\s+COLUMN\b', '||'
    $out = @()
    foreach ($p in ($d -split '\|\|')) {
        $x = $p.Trim().TrimStart(',').Trim().TrimEnd(',').Trim()
        if ($x -ne '') { $out += $x }
    }
    return $out
}

# ---------- 1) addColumnIfMissing("table", "<ddl>"[ + "<ddl>"...]) ----------
$entries = @()          # [pscustomobject]@{ Table; Col; Def }
$i = 0
while (($i = $di.IndexOf('addColumnIfMissing(', $i)) -ge 0) {
    $open = $di.IndexOf('(', $i)
    $args = Get-Balanced $di $open
    $i = $open + 1
    if (-not $args) { continue }
    $lits = @([regex]::Matches($args, '"((?:[^"\\]|\\.)*)"') | ForEach-Object { $_.Groups[1].Value })
    if ($lits.Count -lt 2) { continue }
    $tbl = $lits[0].Trim()
    $ddl = (($lits | Select-Object -Skip 1) -join ' ')
    foreach ($def in (Split-ColumnDefs $ddl)) {
        $col = (($def -split '\s+')[0]).Trim('`')
        if ($col -and $col -notmatch '^(?i)(ALTER|ADD|TABLE)$') {
            $entries += [pscustomobject]@{ Table = $tbl; Col = $col; Def = $def }
        }
    }
}

# ---------- 2) initDocOperatorColumns(): 4 operator columns applied to a table list ----------
$mDoc = [regex]::Match($di, '(?s)String\[\]\s+docTables\s*=\s*\{(.*?)\};')
$docTables = @([regex]::Matches($mDoc.Groups[1].Value, '"(\w+)"') | ForEach-Object { $_.Groups[1].Value })
$mBody = [regex]::Match($di, '(?s)private void initDocOperatorColumns\(\)(.*?)\n    private ')
$body = $mBody.Groups[1].Value
$opDefs = @()
foreach ($mm in [regex]::Matches($body, "ADD COLUMN\s+(\w+)\s+([A-Z]+\w*(?:\([\d,]+\))?(?:\s+(?:NULL|NOT NULL))?\s+COMMENT\s+'[^']*')")) {
    $opDefs += [pscustomobject]@{ Col = $mm.Groups[1].Value; Def = "$($mm.Groups[1].Value) $($mm.Groups[2].Value)" }
}
$opDefs = @($opDefs | Sort-Object Col -Unique)
Write-Host ('parsed: addColumnIfMissing entries=' + $entries.Count + ' ; docTables=' + $docTables.Count + ' ; operator cols=' + $opDefs.Count + ' (' + (($opDefs | ForEach-Object { $_.Col }) -join ',') + ')')
foreach ($t in $docTables) { foreach ($o in $opDefs) { $entries += [pscustomobject]@{ Table = $t; Col = $o.Col; Def = $o.Def } } }

# ---------- 3) locate CREATE TABLE blocks ----------
$rx = [regex]'(?s)(CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s*\()(.*?)(\)\s*ENGINE.*?;)'
$blocks = @{}
foreach ($b in $rx.Matches($schema)) { $blocks[$b.Groups[2].Value] = $b }

# ---------- 4) decide what to insert / what is already there ----------
$inv = New-Object System.Collections.Generic.List[string]
$perTable = @{}
# the source DDL already starts with the column name (e.g. "customized_menu TINYINT DEFAULT 0 COMMENT '...'")
# -> strip it so we can emit `col` <body> without duplicating the name (the 1st run produced
#    "`customized_menu` customized_menu TINYINT ..." which is a MySQL syntax error)
foreach ($e in $entries) {
    $e.Def = ($e.Def -replace ('(?i)^`?' + [regex]::Escape($e.Col) + '`?\s+'), '').Trim()
}
foreach ($e in $entries) {
    $key = "$($e.Table).$($e.Col)"
    if (-not $blocks.ContainsKey($e.Table)) { $inv.Add("TABLE-MISSING`t$key`t$($e.Def)"); continue }
    $inner = $blocks[$e.Table].Groups[3].Value
    $present = $inner -match ('(?i)(^|[\s,(`])`?' + [regex]::Escape($e.Col) + '`?\s')
    if (-not $present) {
        if (-not $perTable.ContainsKey($e.Table)) { $perTable[$e.Table] = New-Object System.Collections.Generic.List[string] }
        if (-not $perTable[$e.Table].Contains($e.Col)) { $perTable[$e.Table].Add($e.Col) }
        $inv.Add("INSERT`t$key`t$($e.Def)")
    }
    elseif (-not ($inv -match ("(?m)^(INSERT|PRESENT)\t" + [regex]::Escape($key) + "\t"))) {
        $inv.Add("PRESENT`t$key`t$($e.Def)")
    }
}

# ---------- 5) write schema.sql back (reverse order so indices stay valid) ----------
$newSchema = $schema
$inserted = 0
$eol = if ($schema.Contains("`r`n")) { "`r`n" } else { "`n" }
$blockList = @($rx.Matches($newSchema))
for ($k = $blockList.Count - 1; $k -ge 0; $k--) {
    $b = $blockList[$k]
    $tbl = $b.Groups[2].Value
    if (-not $perTable.ContainsKey($tbl)) { continue }
    $cols = @($perTable[$tbl])
    $inner = $b.Groups[3].Value
    $needsComma = -not ($inner.TrimEnd().EndsWith(','))
    $ins = ''
    for ($j = 0; $j -lt $cols.Count; $j++) {
        $e = $entries | Where-Object { $_.Table -eq $tbl -and $_.Col -eq $cols[$j] } | Select-Object -First 1
        $sep = if ($j -eq 0) { if ($needsComma) { ',' } else { '' } } else { ',' }
        $ins += $sep + $eol + "  ``" + $e.Col + "`` " + $e.Def
        $inserted++
    }
    $rebuilt = $b.Groups[1].Value + $inner.TrimEnd() + $ins + $eol + $b.Groups[4].Value
    $newSchema = $newSchema.Substring(0, $b.Index) + $rebuilt + $newSchema.Substring($b.Index + $b.Length)
}
[System.IO.File]::WriteAllText($schemaPath, $newSchema, $utf8)

# ---------- 6) report ----------
$invLines = @("# migration DDL inventory (P0 baseline, 2026-09-30)",
              "# source: DataInitializer.addColumnIfMissing(...) + initDocOperatorColumns(docTables x operator cols)",
              "# format: STATUS<TAB>table.column<TAB>column definition",
              "#   INSERT  = was missing from schema.sql's CREATE TABLE -> folded in by P1",
              "#   PRESENT = schema.sql already declared it",
              "#   TABLE-MISSING = no CREATE TABLE block found (needs manual review)")
$invLines += $inv
[System.IO.File]::WriteAllLines($invPath, $invLines, $utf8)

$cntInsert = @($inv | Where-Object { $_ -like 'INSERT*' }).Count
$cntPresent = @($inv | Where-Object { $_ -like 'PRESENT*' }).Count
$cntMissing = @($inv | Where-Object { $_ -like 'TABLE-MISSING*' }).Count
Write-Host ('inventory entries: INSERT=' + $cntInsert + ' PRESENT=' + $cntPresent + ' TABLE-MISSING=' + $cntMissing)
Write-Host ('schema.sql: columns folded in = ' + $inserted + ' ; tables touched = ' + $perTable.Keys.Count)
Write-Host ('inventory written to: ' + $invPath)
if ($cntMissing -gt 0) { Write-Host 'WARNING: TABLE-MISSING entries need manual handling' }
