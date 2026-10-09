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
# 2026-10-09 修正（本脚本自 2026-09-30 起恒红）：DDL 已从 schema.sql 迁到 Flyway ——
# schema.sql 在仓库里**已不存在**（commit「表结构演化改为 Flyway（schema.sql 迁为 V1__base.sql）」），
# 于是"source schema.sql"必然报错、临时库 0 张表 ⇒ 全部 114 张被误判成 live-only。
# 全新部署现在的口径是"按版本号顺序应用 db/migration/V*.sql" ⇒ 这里改用同一口径建临时库；
# 比较对象仍是 information_schema 的列集合与索引，守卫要抓的漂移一点没少。
$migDir = 'c:/Users/75629/CodeBuddy/20260710123705/beichen-erp/beichen-erp-server/src/main/resources/db/migration'
$script:DDL = @()
if (Test-Path $migDir) {
  $script:DDL = @(Get-ChildItem $migDir -Filter 'V*.sql' |
    Sort-Object { [int]($_.Name -replace '^V(\d+).*$', '$1') } |
    ForEach-Object { $_.FullName -replace '\\', '/' })
}
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

Write-Host '--- schema drift scan (Flyway db/migration/V*.sql -> fresh DB) vs live DB'
& $exe --default-character-set=utf8mb4 -uroot -proot -e "DROP DATABASE IF EXISTS $tmp; CREATE DATABASE $tmp CHARACTER SET utf8mb4;" 2>$null
$errs = @()
foreach ($f in $script:DDL) {
  $out = & $exe --default-character-set=utf8mb4 -uroot -proot -D $tmp -e "source $f" 2>&1
  $e = @($out | Where-Object { $_ -match 'ERROR' })
  if ($e.Count -gt 0) { $errs += $e; Write-Host ('  ! ' + (Split-Path $f -Leaf) + ' -> ' + $e.Count + ' error(s)') }
}
Write-Host ('  sourced ' + $script:DDL.Count + ' migration file(s); errors=' + $errs.Count)
Ok ($script:DDL.Count -gt 0) 'the DDL source exists (db/migration/V*.sql)'
Ok ($errs.Count -eq 0) 'all Flyway migrations source into a fresh DB without errors'
if ($errs.Count -gt 0) { $errs | Select-Object -First 8 | ForEach-Object { Write-Host ('    ' + $_) } }

$sqlCols = "SELECT TABLE_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ',') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{0}' GROUP BY TABLE_NAME ORDER BY TABLE_NAME;"
$sqlIdx = "SELECT TABLE_NAME, GROUP_CONCAT(CONCAT(INDEX_NAME,':',NON_UNIQUE,':',SEQ_IN_INDEX) ORDER BY INDEX_NAME, SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='{0}' GROUP BY TABLE_NAME ORDER BY TABLE_NAME;"
$liveCols = Map (Rows $live $sqlCols)
# 2026-10-09：`flyway_schema_history` 是 **Flyway 自己在运行时建**的记账表，不由任何迁移声明 ⇒
# 不豁免它本守卫恒红（1 条 live-only）。它没有业务列，漂移与否与"全新部署"无关。
$liveCols.Remove('flyway_schema_history') | Out-Null
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
Ok ($liveOnly.Count -eq 0) ('every live table is declared by the migrations (live-only=' + $liveOnly.Count + ')')
Ok ($schemaOnly.Count -eq 0) ('the migrations declare no table that is missing from the live DB (schema-only=' + $schemaOnly.Count + ')')
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
