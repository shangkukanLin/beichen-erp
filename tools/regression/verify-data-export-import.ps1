# Verify the whole-DB export / import feature (settings -> data management, super-admin only).
#   export:   GET  /api/system/export-data          -> R{ data:{ exportInfo{time,tableCount,dbTableCount,recordCount,failedTables}, tables{} } }
#   precheck: POST /api/system/import-data/precheck -> read-only preview (counts, empty tables, tablesNotInBackup)
#   import:   POST /api/system/import-data          -> multipart file; semantics = REPLACE the whole DB from the backup
#
# Usage:
#   .\verify-data-export-import.ps1                 # read-only checks only (safe any time)
#   .\verify-data-export-import.ps1 -RoundTrip      # ALSO: export -> import -> per-table diff, the 409 confirm flow,
#                                                   #       and the "missing table" restore path (mysqldump safety net)
# Covers the 2026-09-18 five fixes:
#   #1 import tolerates both the R-wrapped raw response and the UI-style unwrapped file
#   #2 counts distinguish backup tables / tables with data / empty tables
#   #3 export reports failedTables instead of silently skipping
#   #4 import refuses to clear tables missing from the backup unless confirmMissingTables=true
#   #5 export response carries Content-Length (PowerShell 5.1 Invoke-WebRequest can download it)
# Pure ASCII file: all Chinese goes through ui-e2e-zh.json (ZH).
param([switch]$RoundTrip)
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$MYSQLDUMP = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysqldump.exe'
$fail = 0
$script:keepWork = $false
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }
function EvalJs2($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }
  return ("$($l[0])" -split "`t")[0].Trim()
}
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function Login([string]$u, [string]$p) {
  try {
    $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
    $r = Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b))
    return [string]$r.data.token
  } catch { return '' }
}
function CodeOf($r) { if ($null -eq $r) { return 'null' } else { return "$($r.code)" } }
function MsgOf($r) { if ($null -eq $r) { return '' } else { return "$($r.msg)" } }
$qt = [char]34
function BuildCountSql([string[]]$tables) {
  $parts = @()
  foreach ($t in $tables) { $parts += ("SELECT '" + $t + "' AS tbl, COUNT(*) AS c FROM " + $qt + $t + $qt) }
  return ($parts -join " UNION ALL ")
}
function CountAllTables([string[]]$tables) {
  $map = @{}
  $chunk = 15
  for ($i = 0; $i -lt $tables.Count; $i += $chunk) {
    $end = [Math]::Min($i + $chunk - 1, $tables.Count - 1)
    $sql = BuildCountSql @($tables[$i..$end])
    $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $sql 2>$null
    foreach ($l in @($o)) {
      $p = "$l" -split "`t"
      if ($p.Count -ge 2) { $map[$p[0].Trim()] = [int]$p[1].Trim() }
    }
  }
  return $map
}
function PostFile([string]$url, [string]$token, [string]$filePath) {
  $boundary = '----beichen' + [guid]::NewGuid().ToString('N')
  $head = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"backup.json`"`r`nContent-Type: application/json`r`n`r`n"
  $tail = "`r`n--$boundary--`r`n"
  $hb = [Text.Encoding]::UTF8.GetBytes($head)
  $tb = [Text.Encoding]::UTF8.GetBytes($tail)
  $fb = @()
  if ($filePath -ne '' -and (Test-Path $filePath)) { $fb = [IO.File]::ReadAllBytes($filePath) }
  $body = New-Object byte[] ($hb.Length + $fb.Length + $tb.Length)
  [Array]::Copy($hb, 0, $body, 0, $hb.Length)
  if ($fb.Length -gt 0) { [Array]::Copy($fb, 0, $body, $hb.Length, $fb.Length) }
  [Array]::Copy($tb, 0, $body, $hb.Length + $fb.Length, $tb.Length)
  try {
    return Invoke-RestMethod -Uri $url -Method Post -Headers @{ Authorization = $token } -ContentType ("multipart/form-data; boundary=" + $boundary) -Body $body -TimeoutSec 600
  } catch {
    $resp = $_.Exception.Response
    if ($resp) { return [pscustomobject]@{ code = [int]$resp.StatusCode; msg = 'HTTPEX ' + [int]$resp.StatusCode; data = $null } }
    return [pscustomobject]@{ code = -1; msg = 'HTTPEX ' + $_.Exception.Message; data = $null }
  }
}
function PostImport([string]$token, [string]$filePath, [switch]$Confirm) {
  $url = "$api/system/import-data"
  if ($Confirm) { $url = $url + '?confirmMissingTables=true' }
  return PostFile $url $token $filePath
}
function PostPrecheck([string]$token, [string]$filePath) { return PostFile "$api/system/import-data/precheck" $token $filePath }
function DiffCounts($before, $after, [string[]]$tables) {
  $d = @()
  foreach ($t in $tables) {
    $a = if ($before.ContainsKey($t)) { $before[$t] } else { -1 }
    $b = if ($after.ContainsKey($t)) { $after[$t] } else { -1 }
    if ($a -ne $b) { $d += ($t + ':' + $a + '->' + $b) }
  }
  return $d
}

$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$work = Join-Path $env:TEMP "beichen_data_io_$ts"
New-Item -ItemType Directory -Path $work -Force | Out-Null
$jsonPath = Join-Path $work 'export.json'       # raw API response (R-wrapped, like curl/Postman capture)
$importPath = Join-Path $work 'import.json'     # UI-style file (unwrapped inner object)
$missPath = Join-Path $work 'missing_table.json'# unwrapped file with one table dropped (#4 test)
$badJson = Join-Path $work 'bad.json'
$hdrs = Join-Path $work 'headers.txt'
$dumpPath = Join-Path $work 'safety_dump.sql'
$psIwr = Join-Path $work 'ps_iwr_download.json'

Write-Output '--- 1) preflight: tooling, table inventory, unsafe column types'
if (Test-Path $MYSQLDUMP) { Ok 'mysqldump available (safety copy possible)' } else { Bad 'mysqldump not found' }
if (Get-Command curl.exe -ErrorAction SilentlyContinue) { Ok 'curl.exe available' } else { Bad 'curl.exe missing' }
$allTables = @(SqlLines "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME NOT LIKE 'flyway%' ORDER BY TABLE_NAME")
Write-Output ("  base tables = " + $allTables.Count)
$unsafe = SqlOne "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE IN ('blob','longblob','mediumblob','tinyblob','binary','varbinary','json')"
$gen = SqlOne "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND EXTRA LIKE '%GENERATED%' AND EXTRA NOT LIKE '%DEFAULT_GENERATED%'"
if ($unsafe -eq '0') { Ok 'no blob/binary/json columns (string quoting in import is safe)' } else { Bad ("unsafe column types = " + $unsafe) }
if ($gen -eq '0') { Ok 'no virtual/stored generated columns (explicit insert is safe)' } else { Bad ("generated columns = " + $gen) }

$tok = Login 'lin' '123'
if ($tok -eq '') { Bad 'cannot login as super admin (lin)'; Write-Output 'RESULT FAIL count 1'; exit 1 }
$tokMerch = Login 'audit_merch' '123'
Ok 'tokens acquired (super admin; merchandiser for the 403 checks)'

Write-Output '--- 2) baseline per-table row counts (exact COUNT(*))'
$base1 = CountAllTables $allTables
$sum1 = 0; foreach ($k in $base1.Keys) { $sum1 += $base1[$k] }
Write-Output ("  tables counted = " + $base1.Count + " ; total rows = " + $sum1)
if ($base1.Count -eq $allTables.Count) { Ok ('every table counted (' + $base1.Count + ')') } else { Bad ('counted ' + $base1.Count + ' of ' + $allTables.Count) }
# pick a non-empty table to drop in the #4 test (must have rows so the guard is meaningful;
# skip the first key so the JSON stays a plain object after removal)
$dropTable = ''
foreach ($t in @($allTables | Select-Object -Skip 1)) { if ($base1[$t] -gt 0 -and $t -notlike '*flyway*') { $dropTable = $t; break } }
Write-Output ("  table used for the missing-table test = " + $dropTable + " (rows=" + $base1[$dropTable] + ")")

Write-Output '--- 3) export endpoint (#3 failedTables, #5 Content-Length)'
$curlOut = & cmd /c "curl.exe -sS -D `"$hdrs`" -o `"$jsonPath`" -w `"%{http_code} %{size_download}`" -H `"Authorization: $tok`" $api/system/export-data 2>&1"
Write-Output ("  curl -> " + $curlOut)
$size = if (Test-Path $jsonPath) { (Get-Item $jsonPath).Length } else { 0 }
if ("$curlOut" -like '200*' -and $size -gt 1000) { Ok ('export returned 200 with ' + $size + ' bytes') } else { Bad ('export failed: ' + $curlOut) }
$hdrTxt = if (Test-Path $hdrs) { [IO.File]::ReadAllText($hdrs) } else { '' }
$hasLen = $hdrTxt -match 'Content-Length:\s*\d+'
$isChunked = $hdrTxt -match 'Transfer-Encoding:\s*chunked'
$lenVal = [regex]::Match($hdrTxt, 'Content-Length:\s*(\d+)')
Write-Output ("  headers: Content-Length=" + $(if ($hasLen) { $lenVal.Groups[1].Value } else { 'MISSING' }) + " chunked=" + $isChunked)
if ($hasLen) { Ok 'export response carries Content-Length (#5)' } else { Bad 'export response has no Content-Length (#5 not fixed)' }
if (-not $isChunked) { Ok 'export response is not chunked (#5)' } else { Bad 'export response is still chunked (#5)' }
if ($hasLen -and [int]$lenVal.Groups[1].Value -eq $size) { Ok 'Content-Length matches the actual payload size' } else { Bad 'Content-Length does not match the payload size' }
# the original PowerShell 5.1 failure mode: Invoke-WebRequest -OutFile on a large response.
# NOTE: PS 5.1 IWR is known to be flaky (occasional "connection forcibly closed") even when the
# server response is correct, so retry once before failing this one assertion.
$psSize = 0; $psErr = ''
for ($attempt = 1; $attempt -le 2; $attempt++) {
  try {
    if (Test-Path $psIwr) { Remove-Item -Force $psIwr }
    Invoke-WebRequest -Uri "$api/system/export-data" -Headers @{ Authorization = $tok } -OutFile $psIwr -TimeoutSec 180 -ErrorAction Stop | Out-Null
    $psSize = if (Test-Path $psIwr) { (Get-Item $psIwr).Length } else { 0 }
    if ($psSize -gt 0) { break }
  } catch { $psErr = $_.Exception.Message }
  Start-Sleep -Seconds 1
}
if ($psSize -eq $size) { Ok ('PowerShell 5.1 Invoke-WebRequest -OutFile now downloads it (' + $psSize + ' bytes) (#5)') }
elseif ($psSize -gt 0) { Bad ('PS download size ' + $psSize + ' vs ' + $size) }
else { Bad ('PowerShell 5.1 download failed after retry: ' + $psErr) }
$raw = if ($size -gt 0) { [IO.File]::ReadAllText($jsonPath, [Text.Encoding]::UTF8) } else { '' }
$mC = [regex]::Match($raw, '"tableCount":(\d+)'); $mD = [regex]::Match($raw, '"dbTableCount":(\d+)')
$mR = [regex]::Match($raw, '"recordCount":(\d+)'); $mF = [regex]::Match($raw, '"failedTables":\[(.*?)\]')
$expTables = if ($mC.Success) { [int]$mC.Groups[1].Value } else { -1 }
$expDb = if ($mD.Success) { [int]$mD.Groups[1].Value } else { -1 }
$expRows = if ($mR.Success) { [int]$mR.Groups[1].Value } else { -1 }
Write-Output ("  exportInfo: tableCount=$expTables dbTableCount=$expDb recordCount=$expRows failedTables=[" + $(if ($mF.Success) { $mF.Groups[1].Value } else { 'MISSING' }) + "]")
if ($expTables -eq $allTables.Count -and $expDb -eq $allTables.Count) { Ok ('table counts match the live schema (' + $expTables + '/' + $expDb + ')') } else { Bad ('tableCount=' + $expTables + ' dbTableCount=' + $expDb + ' vs live ' + $allTables.Count) }
if ($expRows -eq $sum1) { Ok ('recordCount equals the exact DB row sum (' + $expRows + ')') } else { Bad ('recordCount=' + $expRows + ' vs DB sum ' + $sum1) }
if ($mF.Success) { Ok 'exportInfo.failedTables is present (#3)' } else { Bad 'exportInfo.failedTables missing (#3)' }
if ($mF.Success -and $mF.Groups[1].Value.Trim() -eq '') { Ok 'no table failed to export (failedTables empty)' } else { Bad ('some tables failed to export: ' + $mF.Groups[1].Value) }
$names = [regex]::Matches($raw, '"([a-z_0-9]+)":\[') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
$missing = @($allTables | Where-Object { $names -notcontains $_ })
if ($missing.Count -eq 0) { Ok 'every table appears in the export payload (no silent skips)' } else { Bad ('tables missing from export: ' + ($missing -join ', ')) }

# UI-style file: exactly what the download button writes (inner object, R unwrapped)
$di = $raw.IndexOf('"data":')
$inner = $raw.Substring($di + 7).Trim()
if ($inner.EndsWith('}')) { $inner = $inner.Substring(0, $inner.Length - 1).TrimEnd() }
[IO.File]::WriteAllText($importPath, $inner, (New-Object Text.UTF8Encoding($false)))
Ok 'UI-style (unwrapped) backup file prepared'

Write-Output '--- 4) precheck endpoint + import negative cases'
$pre = PostPrecheck $tok $importPath
Write-Output ('  precheck(UI file) -> code=' + (CodeOf $pre) + ' unwrapped=' + $pre.data.unwrapped + ' backupTables=' + $pre.data.backupTables + ' tablesWithData=' + $pre.data.tablesWithData + ' empty=' + ($pre.data.emptyTables | Measure-Object).Count + ' records=' + $pre.data.backupRecords + ' notInBackup=' + ($pre.data.tablesNotInBackup | Measure-Object).Count)
if ((CodeOf $pre) -eq '200') { Ok 'precheck accepted the UI-style file (read-only preview available)' } else { Bad ('precheck failed: ' + (MsgOf $pre)) }
if ("$($pre.data.backupTables)" -eq "$($allTables.Count)") { Ok ('precheck backupTables = ' + $pre.data.backupTables + ' (#2)') } else { Bad ('precheck backupTables=' + $pre.data.backupTables) }
if ([int]$pre.data.backupRecords -eq $sum1) { Ok ('precheck backupRecords = ' + $pre.data.backupRecords + ' (equals DB row sum)') } else { Bad ('precheck backupRecords=' + $pre.data.backupRecords + ' vs ' + $sum1) }
if ([int]$pre.data.tablesWithData + @($pre.data.emptyTables).Count -eq $allTables.Count) { Ok ('tablesWithData + emptyTables = backupTables (' + $pre.data.tablesWithData + ' + ' + @($pre.data.emptyTables).Count + ' = ' + $allTables.Count + ') (#2)') } else { Bad 'tablesWithData + emptyTables != backupTables (#2)' }
if (@($pre.data.tablesNotInBackup).Count -eq 0) { Ok 'no tables would be cleared by this backup (same-DB export)' } else { Bad ('unexpected tablesNotInBackup: ' + @($pre.data.tablesNotInBackup).Count) }
$preRaw = PostPrecheck $tok $jsonPath
Write-Output ('  precheck(raw R-wrapped file) -> code=' + (CodeOf $preRaw) + ' unwrapped=' + $preRaw.data.unwrapped + ' backupTables=' + $preRaw.data.backupTables)
if ((CodeOf $preRaw) -eq '200' -and "$($preRaw.data.unwrapped)" -eq 'True') { Ok 'precheck auto-unwraps the R-wrapped raw response (#1)' } else { Bad ('R-wrapped precheck: code=' + (CodeOf $preRaw) + ' unwrapped=' + $preRaw.data.unwrapped) }
$r = Invoke-RestMethod -Uri "$api/system/export-data" -Method Get -Headers @{ Authorization = $tokMerch } -ErrorAction SilentlyContinue
if ((CodeOf $r) -eq '403') { Ok 'merchandiser export rejected (403)' } else { Bad ('merchandiser export not rejected: ' + (CodeOf $r)) }
$rm = PostImport $tokMerch ''
if ("$($rm.code)" -ne '200') { Ok ('merchandiser import rejected: code=' + (CodeOf $rm)) } else { Bad 'merchandiser import was allowed' }
$rn = PostImport $tok ''
if ("$($rn.code)" -ne '200') { Ok ('import without a file rejected: ' + (MsgOf $rn)) } else { Bad 'import without a file succeeded' }
[IO.File]::WriteAllText($badJson, '{"foo":"bar"}', (New-Object Text.UTF8Encoding($false)))
$rb = PostImport $tok $badJson
if ("$($rb.code)" -ne '200') { Ok ('malformed payload rejected before any DELETE: ' + (MsgOf $rb)) } else { Bad 'malformed payload was accepted' }
$base2 = CountAllTables $allTables
$drift = @(DiffCounts $base1 $base2 $allTables)
if ($drift.Count -eq 0) { Ok 'row counts unchanged after the rejected imports (nothing was deleted)' } else { Bad ('row counts changed for ' + $drift.Count + ' tables') }

if (-not $RoundTrip) {
  Write-Output '--- 5) round trip: SKIPPED (run with -RoundTrip)'
} else {
  Write-Output '--- 5) round trip + #4 confirm flow (safety dump first)'
  & cmd /c "`"$MYSQLDUMP`" --single-transaction --routines --triggers -uroot -proot beichen_erp > `"$dumpPath`" 2>nul"
  $dumpSize = if (Test-Path $dumpPath) { (Get-Item $dumpPath).Length } else { 0 }
  Write-Output ("  safety dump bytes = " + $dumpSize)
  if ($dumpSize -gt 10000) { Ok 'safety copy taken (mysqldump)' } else { Bad 'safety dump looks empty; ABORTING round trip'; Write-Output 'RESULT FAIL count 1'; exit 1 }

  # 5a) faithful round trip with the UI-style file
  $ri = PostImport $tok $importPath
  $emptyCnt = @($ri.data.emptyTables).Count
  Write-Output ('  import -> code=' + (CodeOf $ri) + ' backupTables=' + $ri.data.backupTables + ' dataTables=' + $ri.data.dataTables + ' emptyTables=' + $emptyCnt + ' records=' + $ri.data.totalRecords + ' cleared=' + @($ri.data.clearedNotInBackup).Count)
  if ((CodeOf $ri) -eq '200') { Ok 'import accepted the UI-style backup' } else { Bad ('import failed: ' + (MsgOf $ri)) }
  if ([int]$ri.data.dataTables + $emptyCnt -eq [int]$ri.data.backupTables) { Ok ('response splits dataTables/emptyTables correctly (' + $ri.data.dataTables + ' + ' + $emptyCnt + ' = ' + $ri.data.backupTables + ') (#2)') } else { Bad ('dataTables/emptyTables split wrong (#2)') }
  if ([int]$ri.data.totalRecords -eq $sum1) { Ok ('import reported the exact record count (' + $ri.data.totalRecords + ')') } else { Bad ('import totalRecords=' + $ri.data.totalRecords + ' vs ' + $sum1) }
  # 5a-2) F5-1 (2026-09-18 review fix): the server must leave a rollback point BEFORE wiping the DB
  $rbp = "" + $ri.data.preImportBackupPath
  Write-Output ('  preImportBackupPath = ' + $rbp)
  if ($rbp -ne '' -and (Test-Path $rbp)) { Ok ('import left a rollback point on disk: ' + $rbp) } else { Bad ('no rollback point returned/stored (F5-1): ' + $rbp) }
  if (Test-Path $rbp) {
    try {
      # NOTE: the file is UTF-8 WITHOUT BOM (Jackson writes UTF-8 bytes) -> must read with an
      # explicit UTF-8 decoder; PS 5.1 Get-Content defaults to ANSI and GBK mis-decoding can swallow
      # the following " or } byte and corrupt the JSON structure.
      $rbj = [IO.File]::ReadAllText($rbp, [Text.Encoding]::UTF8) | ConvertFrom-Json
      $rbn = @($rbj.tables.PSObject.Properties).Count
      $rbRec = 0
      foreach ($p in $rbj.tables.PSObject.Properties) { $rbRec += @($p.Value).Count }
      Write-Output ('  rollback point: tables=' + $rbn + ' records=' + $rbRec)
      if ($rbn -eq $allTables.Count) { Ok ('rollback point covers all ' + $rbn + ' tables') } else { Bad ('rollback point tables=' + $rbn + ' expected ' + $allTables.Count) }
      if ($rbRec -eq $sum1) { Ok ('rollback point holds the pre-import row count (' + $rbRec + ')') } else { Bad ('rollback point records=' + $rbRec + ' vs ' + $sum1) }
    } catch { Bad ('rollback point is not readable JSON: ' + $_.Exception.Message) }
  }
  # 5a-3) the rollback point must actually be usable: re-import it and check the counts come back
  if (Test-Path $rbp) {
    $rback = PostImport $tok $rbp
    Write-Output ('  re-import(rollback point) -> code=' + (CodeOf $rback) + ' records=' + $rback.data.totalRecords)
    if ((CodeOf $rback) -eq '200') { Ok 'rollback point can be re-imported (restore path works)' } else { Bad ('rollback point re-import failed: ' + (MsgOf $rback)) }
    $baseRb = CountAllTables $allTables
    $diffRb = @(DiffCounts $base1 $baseRb $allTables)
    if ($diffRb.Count -eq 0) { Ok 'row counts match the pre-import snapshot after restoring from the rollback point' } else { Bad ('restore drift: ' + ($diffRb -join ', ')) }
  }
  $base3 = CountAllTables $allTables
  $diff = @(DiffCounts $base1 $base3 $allTables)
  if ($diff.Count -eq 0) { Ok ('all ' + $allTables.Count + ' tables identical after the round trip') } else { Bad ('row-count drift: ' + ($diff -join ', ')) }
  $relogin = Login 'lin' '123'
  if ($relogin -ne '') { Ok 'login still works after the import' } else { Bad 'cannot login after the import' }
  $tok = $relogin

  # 5b) #1: the raw R-wrapped response is now importable too
  $riRaw = PostImport $tok $jsonPath
  Write-Output ('  import(raw R-wrapped file) -> code=' + (CodeOf $riRaw) + ' records=' + $riRaw.data.totalRecords)
  if ((CodeOf $riRaw) -eq '200' -and [int]$riRaw.data.totalRecords -eq $sum1) { Ok 'raw R-wrapped API response is accepted as a backup (#1)' } else { Bad ('R-wrapped import failed: ' + (CodeOf $riRaw) + ' ' + (MsgOf $riRaw)) }
  $base3b = CountAllTables $allTables
  $diff3b = @(DiffCounts $base1 $base3b $allTables)
  if ($diff3b.Count -eq 0) { Ok 'counts identical after the R-wrapped import' } else { Bad ('drift after R-wrapped import: ' + ($diff3b -join ', ')) }

  # 5c) #4: a backup missing one table must be refused unless confirmed, and clearing must be reversible
  $obj = [IO.File]::ReadAllText($importPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
  $obj.tables.PSObject.Properties.Remove($dropTable)
  $missRaw = $obj | ConvertTo-Json -Depth 12 -Compress
  [IO.File]::WriteAllText($missPath, $missRaw, (New-Object Text.UTF8Encoding($false)))
  $okShape = ($missRaw.IndexOf('"' + $dropTable + '":') -lt 0) -and ($missRaw.IndexOf('"tables"') -ge 0)
  if ($okShape) { Ok ('built a backup missing one table (' + $dropTable + ') for the #4 test') } else { Bad 'could not build the missing-table backup' }
  $preMiss = PostPrecheck $tok $missPath
  Write-Output ('  precheck(missing-table file) -> notInBackup=' + @($preMiss.data.tablesNotInBackup).Count + ' [' + (@($preMiss.data.tablesNotInBackup) -join ',') + ']')
  if (@($preMiss.data.tablesNotInBackup).Count -eq 1 -and @($preMiss.data.tablesNotInBackup)[0] -eq $dropTable) { Ok 'precheck lists the table that would be cleared (#4)' } else { Bad ('precheck risk list wrong: ' + (@($preMiss.data.tablesNotInBackup) -join ',')) }
  $rNo = PostImport $tok $missPath
  Write-Output ('  import(missing table, no confirm) -> code=' + (CodeOf $rNo) + ' msg=' + (MsgOf $rNo))
  if ((CodeOf $rNo) -eq '409') { Ok 'import without confirm is refused (409) (#4)' } else { Bad ('expected 409, got ' + (CodeOf $rNo)) }
  $afterNo = SqlOne ("SELECT COUNT(*) FROM " + $qt + $dropTable + $qt)
  Write-Output ('  ' + $dropTable + ' rows after the refused import = ' + $afterNo + ' (baseline ' + $base1[$dropTable] + ')')
  if ("$afterNo" -eq "$($base1[$dropTable])") { Ok 'refused import deleted nothing (data intact) (#4)' } else { Bad ('data changed despite the refusal') }
  $rYes = PostImport $tok $missPath -Confirm
  Write-Output ('  import(missing table, confirmed) -> code=' + (CodeOf $rYes) + ' clearedNotInBackup=[' + (@($rYes.data.clearedNotInBackup) -join ',') + ']')
  if ((CodeOf $rYes) -eq '200') { Ok 'confirmed import accepted (#4)' } else { Bad ('confirmed import failed: ' + (MsgOf $rYes)) }
  $afterYes = SqlOne ("SELECT COUNT(*) FROM " + $qt + $dropTable + $qt)
  if ("$afterYes" -eq '0') { Ok ($dropTable + ' was cleared as warned (expected behaviour)') } else { Bad ($dropTable + ' rows = ' + $afterYes + ' (expected 0)') }
  # restore the full backup (no confirm needed) and verify the cleared table comes back
  $rBack = PostImport $tok $importPath
  $afterBack = SqlOne ("SELECT COUNT(*) FROM " + $qt + $dropTable + $qt)
  $base4 = CountAllTables $allTables
  $diff4 = @(DiffCounts $base1 $base4 $allTables)
  Write-Output ('  restored from the full backup -> code=' + (CodeOf $rBack) + ' ' + $dropTable + ' rows=' + $afterBack)
  if ($diff4.Count -eq 0) { Ok ('full restore brings every table back to the baseline (' + $allTables.Count + ' tables)') } else { Bad ('restore drift: ' + ($diff4 -join ', ')) }

  $curlOut2 = & cmd /c "curl.exe -sS -o `"$jsonPath`" -w `"%{http_code} %{size_download}`" -H `"Authorization: $tok`" $api/system/export-data 2>&1"
  $raw2 = if (Test-Path $jsonPath) { [IO.File]::ReadAllText($jsonPath, [Text.Encoding]::UTF8) } else { '' }
  $r2 = [regex]::Match($raw2, '"recordCount":(\d+)')
  $rows2 = if ($r2.Success) { [int]$r2.Groups[1].Value } else { -1 }
  Write-Output ("  re-export -> " + $curlOut2 + " recordCount=" + $rows2)
  if ($rows2 -eq $sum1) { Ok ('re-export after imports reports the same record count (' + $rows2 + ')') } else { Bad ('re-export recordCount=' + $rows2 + ' vs ' + $sum1) }

  if ($diff4.Count -gt 0) {
    Write-Output '  !! restoring from the safety dump ...'
    & cmd /c "`"$MYSQL`" -uroot -proot beichen_erp < `"$dumpPath`" 2>nul"
    $base5 = CountAllTables $allTables
    $stillBad = @(DiffCounts $base1 $base5 $allTables)
    $script:keepWork = $true
    if ($stillBad.Count -eq 0) { Ok 'DB restored from the safety dump' } else { Bad ("restore incomplete; dump kept at $dumpPath") }
  }
}

Write-Output '--- 6) UI: settings -> data management'
$tabsB = B64 (ZH 'text_data_export')
$tabsB2 = B64 (ZH 'text_data_import')
$btnB = B64 (ZH 'btn_export_all')
EvalJs2 "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
agent-browser open "$base/system/data-manage" | Out-Null
agent-browser wait 3000
if ((EvalJs2 "'p=' + location.pathname") -match '/login') {
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, [regex]::Escape((ZH 'lbl_login_user')) + '[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, [regex]::Escape((ZH 'lbl_login_pass')) + '[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, [regex]::Escape((ZH 'btn_login')) + '[^\n]*ref=(e\d+)')
  if ($mu.Success -and $mp.Success -and $mb.Success) {
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500
  }
  agent-browser open "$base/system/data-manage" | Out-Null
  agent-browser wait 3000
}
$uiJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const A=T('$tabsB'),B=T('$tabsB2'),G=T('$btnB');const txt=document.body.innerText||'';const btns=[...document.querySelectorAll('button')].map(b=>(b.innerText||'').trim());return (txt.indexOf(A)>=0?'Y':'N')+(txt.indexOf(B)>=0?'Y':'N')+(btns.indexOf(G)>=0?'Y':'N')})()"
$flags = (EvalJs2 $uiJs).Trim().Trim([char]34)
Write-Output ("  path=" + (EvalJs2 'location.pathname') + " ui flags(exportTab,importTab,exportBtn)=" + $flags)
if ($flags -eq 'YYY') { Ok 'data management page shows both tabs and the export button' } else { Bad ('data management UI flags = ' + $flags) }
$recJs = "(()=>{window.__toasts=[];const obs=new MutationObserver(ms=>{ms.forEach(m=>m.addedNodes.forEach(n=>{try{if(n.nodeType===1&&n.classList&&n.classList.contains('el-message'))window.__toasts.push((n.innerText||'').trim())}catch(e){}}))});obs.observe(document.body,{childList:true,subtree:true});return 'REC'})()"
EvalJs2 $recJs | Out-Null
$clickJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const G=T('$btnB');const b=[...document.querySelectorAll('button')].filter(x=>x.getClientRects().length>0).find(x=>((x.innerText||'').trim())===G);if(!b)return 'NOBTN';b.click();return 'OK'})()"
$clicked = (EvalJs2 $clickJs).Trim().Trim([char]34)
agent-browser wait 5000
$toastB = B64 (ZH 'toast_export_ok')
$toastJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$toastB');const m=(window.__toasts||[]).concat([...document.querySelectorAll('.el-message')].map(x=>(x.innerText||'').trim()));return (m.some(x=>x.indexOf(K)>=0)?'Y':'N')+'@'+m.join('~')})()"
$tp = ((EvalJs2 $toastJs).Trim().Trim([char]34)) -split '@'
Write-Output ("  click=$clicked toast=" + $tp[0] + " msgs=" + $tp[1])
if ($clicked -eq 'OK' -and $tp[0] -eq 'Y') { Ok 'export button flow ends with the success toast' } else { Bad ('export button flow: click=' + $clicked + ' toast=' + $tp[0] + ' msgs=' + $tp[1]) }

if ($script:keepWork) { Write-Output ("  (work dir kept: $work)") } else { Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue }
if ($fail -eq 0) { Write-Output ('RESULT PASS data export/import verified' + $(if ($RoundTrip) { ' (incl. round trip + confirm flow)' } else { ' (read-only)' })) }
else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
