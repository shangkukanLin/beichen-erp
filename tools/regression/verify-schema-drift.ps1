# Full schema drift scan (2026-09-21). ASCII ONLY.
#
# Builds a throwaway DB from beichen-erp-server/src/main/resources/schema.sql and compares EVERY table
# (columns + indexes) against the live beichen_erp database. Purpose: catch "the DDL file and the real
# database disagree" drift, which is invisible at runtime but makes a fresh deployment differ from prod.
#
# Origin: 2026-09-21 found outsource_material declaring warehouse_id + spec + idx_warehouse_id in
# schema.sql while neither the live DB nor the entity had them (both were dropped long ago) - a fresh
# DB would have carried 2 dead columns. The entity-vs-column axis is covered by n1.ps1; this script
# covers the schema-file-vs-live axis.
#
# Read-only w.r.t. the application DB: it only creates/drops its own temp database.
param([switch]$Quiet)
$ErrorActionPreference = 'Continue'
$exe = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$schema = 'c:/Users/75629/CodeBuddy/20260710123705/beichen-erp/beichen-erp-server/src/main/resources/schema.sql'
$live = 'beichen_erp'
$tmp = 'beichen_schema_check'
$script:PASS = 0
$script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m); $script:PASS++ } else { Write-Host ('FAIL ' + $m); $script:FAIL++ } }
function Rows([string]$db, [string]$sql) { return @(& $exe --default-character-set=utf8mb4 -uroot -proot -N -B -e ($sql -f $db) 2>$null) }
function Map([string[]]$rows) {
  $m = @{}
  foreach ($r in $rows) {
    $i = ([string]$r).IndexOf("`t")
    if ($i -lt 0) { continue }
    $m[([string]$r).Substring(0, $i)] = ([string]$r).Substring($i + 1)
  }
  return $m
}

Write-Host '--- schema drift scan (schema.sql -> fresh DB) vs live DB'
& $exe --default-character-set=utf8mb4 -uroot -proot -e "DROP DATABASE IF EXISTS $tmp; CREATE DATABASE $tmp CHARACTER SET utf8mb4;" 2>$null
$out = & $exe --default-character-set=utf8mb4 -uroot -proot -D $tmp -e "source $schema" 2>&1
$errs = @($out | Where-Object { $_ -match 'ERROR' })
Write-Host ('  source schema.sql -> exit=' + $LASTEXITCODE + ' errors=' + $errs.Count)
Ok ($errs.Count -eq 0) 'schema.sql sources into a fresh DB without errors'
if ($errs.Count -gt 0) { $errs | Select-Object -First 8 | ForEach-Object { Write-Host ('    ' + $_) } }

$sqlCols = "SELECT TABLE_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ',') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{0}' GROUP BY TABLE_NAME ORDER BY TABLE_NAME;"
$sqlIdx = "SELECT TABLE_NAME, GROUP_CONCAT(CONCAT(INDEX_NAME,':',NON_UNIQUE,':',SEQ_IN_INDEX) ORDER BY INDEX_NAME, SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='{0}' GROUP BY TABLE_NAME ORDER BY TABLE_NAME;"
$liveCols = Map (Rows $live $sqlCols)
$tmpCols = Map (Rows $tmp $sqlCols)
$liveIdx = Map (Rows $live $sqlIdx)
$tmpIdx = Map (Rows $tmp $sqlIdx)
Write-Host ('  tables: live=' + $liveCols.Count + ' fresh=' + $tmpCols.Count)

$names = @($liveCols.Keys + $tmpCols.Keys | Sort-Object -Unique)
$liveOnly = @(); $schemaOnly = @(); $setDiff = @(); $orderOnly = @(); $idxDiff = @()
foreach ($n in $names) {
  if (-not $tmpCols.ContainsKey($n)) { $liveOnly += $n; continue }
  if (-not $liveCols.ContainsKey($n)) { $schemaOnly += $n; continue }
  # GATE: compare the column SET (order-insensitive). A differing set means a fresh deployment ends up
  # with columns prod does not have (dead columns) or is missing columns prod has (Unknown column).
  $ls = @($liveCols[$n] -split ',' | Sort-Object)
  $ts = @($tmpCols[$n] -split ',' | Sort-Object)
  if (($ls -join ',') -ne ($ts -join ',')) { $setDiff += $n }
  # INFO ONLY: same set but different physical order. Harmless for a fresh DB (prod order is just the
  # historical ALTER order); reported so it stays visible, but it does NOT fail the scan.
  elseif ($liveCols[$n] -ne $tmpCols[$n]) { $orderOnly += $n }
  $li = if ($liveIdx.ContainsKey($n)) { $liveIdx[$n] } else { '' }
  $ti = if ($tmpIdx.ContainsKey($n)) { $tmpIdx[$n] } else { '' }
  if ($li -ne $ti) { $idxDiff += $n }
}

# ---- fatal: these change what a fresh deployment looks like -------------------------------
Ok ($liveOnly.Count -eq 0) ('every live table is declared in schema.sql (live-only=' + $liveOnly.Count + ')')
Ok ($schemaOnly.Count -eq 0) ('schema.sql declares no table that is missing from the live DB (schema-only=' + $schemaOnly.Count + ')')
Ok ($setDiff.Count -eq 0) ('every table declares the SAME SET of columns as the live DB (diff=' + $setDiff.Count + ')')

function Show([string]$title, [string[]]$items) {
  if ($items.Count -eq 0) { return }
  Write-Host ('  --- ' + $title + ' (' + $items.Count + ')')
  foreach ($n in $items) {
    Write-Host ('    * ' + $n)
    if (-not $Quiet) {
      $a = if ($liveCols.ContainsKey($n)) { $liveCols[$n] } else { '(absent)' }
      $b = if ($tmpCols.ContainsKey($n)) { $tmpCols[$n] } else { '(absent)' }
      $ia = if ($liveIdx.ContainsKey($n)) { $liveIdx[$n] } else { '(absent)' }
      $ib = if ($tmpIdx.ContainsKey($n)) { $tmpIdx[$n] } else { '(absent)' }
      Write-Host ('        live   cols: ' + $a)
      Write-Host ('        schema cols: ' + $b)
      if ($ia -ne $ib) {
        Write-Host ('        live   idx : ' + $ia)
        Write-Host ('        schema idx : ' + $ib)
      }
    }
  }
}
Show 'FATAL live-only tables' $liveOnly
Show 'FATAL schema-only tables' $schemaOnly
Show 'FATAL tables with a different column SET' $setDiff
# ---- informational: worth knowing, does not fail -----------------------------------------
Write-Host ('  [info] same column set, different physical order: ' + $orderOnly.Count + ' table(s)')
if ($orderOnly.Count -gt 0 -and -not $Quiet) { Write-Host ('         ' + ($orderOnly -join ', ')) }
Write-Host ('  [info] tables whose INDEXES differ: ' + $idxDiff.Count)
Show '[info] index differences' $idxDiff

& $exe --default-character-set=utf8mb4 -uroot -proot -e "DROP DATABASE IF EXISTS $tmp;" 2>$null
Write-Host ('  temp DB dropped; RESULT PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
if ($script:FAIL -gt 0) { exit 1 }
