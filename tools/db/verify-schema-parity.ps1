# =====================================================================================
# P1 acceptance check (2026-09-30): can a BRAND-NEW database be built by schema.sql alone?
#
#  1. refuses to run if schema.sql contains USE/CREATE DATABASE (would hit the live DB)
#  2. creates a throw-away DB, loads schema.sql into it
#  3. diffs information_schema (columns: name/type/nullable/default) between
#     [live db: migrations applied over the years]  vs  [fresh db: schema.sql only]
#  4. prints both directions of the diff; 0 differences == the migrations are now redundant
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-schema-parity.ps1 [-Keep]
# =====================================================================================
param([switch]$Keep)
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$schemaPath = Join-Path $repo 'beichen-erp-server\src\main\resources\schema.sql'
$live = 'beichen_erp'
$fresh = 'beichen_erp_p1check'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function SqlAll($db, [string]$sql) {
    @(& $MYSQL --default-character-set=utf8mb4 -uroot -N -B -D $db -e $sql 2>$null) |
        Where-Object { "$_" -notmatch '^(mysql:|ERROR|Using a password)' }
}
function SchemaRows($db) {
    SqlAll $db "SELECT CONCAT(table_name,'|',column_name,'|',column_type,'|',is_nullable,'|',IFNULL(column_default,'')) FROM information_schema.columns WHERE table_schema='$db' ORDER BY table_name, ordinal_position"
}

Write-Host '=== P1 acceptance: fresh DB built from schema.sql must match the migrated live DB ==='

Write-Host '### 0) safety: schema.sql must not target a specific database'
$sqlText = [System.IO.File]::ReadAllText($schemaPath, [System.Text.Encoding]::UTF8)
$hasUse = [regex]::IsMatch($sqlText, '(?im)^\s*(USE\s+|CREATE\s+DATABASE)')
Ok (-not $hasUse) 'schema.sql contains no USE / CREATE DATABASE (safe to load into a scratch DB)'
if ($hasUse) { Write-Host '  ABORT: schema.sql selects a database; refusing to continue'; exit 1 }

Write-Host '### 1) build the fresh database from schema.sql only'
& $MYSQL -uroot -e "DROP DATABASE IF EXISTS $fresh" 2>$null | Out-Null
& $MYSQL -uroot -e "CREATE DATABASE $fresh DEFAULT CHARACTER SET utf8mb4" 2>$null | Out-Null
$load = & cmd /c "`"$MYSQL`" --default-character-set=utf8mb4 -uroot $fresh < `"$schemaPath`" 2>&1"
$loadErr = @($load | Where-Object { $_ -match '(?i)ERROR' })
Write-Host ('  load output lines=' + @($load).Count + ' errors=' + $loadErr.Count)
$loadErr | Select-Object -First 5 | ForEach-Object { Write-Host ('    ' + $_) }
Ok ($loadErr.Count -eq 0) 'schema.sql loaded into the fresh DB without errors'
$freshCols = (SchemaRows $fresh).Count
Write-Host ('  fresh DB columns=' + $freshCols + ' ; live DB columns=' + (SchemaRows $live).Count)
Ok ($freshCols -gt 1000) 'fresh DB has a plausible number of columns (>1000)'

# Known, accepted differences: the LIVE dev database carries drift that schema.sql does not
# (and should not) reproduce. Each entry must state why the fresh/schema.sql side is authoritative.
$knownDrift = @(
    'finance_bill|status|varchar(20)|YES|UNSETTLED',  # live default is a legacy copy-paste from receivables; code always writes DRAFT explicitly, schema.sql says DEFAULT 'DRAFT'
    'finance_bill|status|varchar(20)|YES|DRAFT'       # the other side of the same drift (fresh/schema.sql value) - authoritative
)
Write-Host '### 2) columns present in LIVE but missing in FRESH (== schema.sql is still incomplete)'
$liveRows = SchemaRows $live
$freshRows = SchemaRows $fresh
$missInFresh = @($liveRows | Where-Object { $freshRows -notcontains $_ -and $knownDrift -notcontains $_ })
$drift = @($liveRows | Where-Object { $freshRows -notcontains $_ -and $knownDrift -contains $_ })
Write-Host ('  count=' + $missInFresh.Count + ' (known/accepted drift=' + $drift.Count + ')')
$drift | ForEach-Object { Write-Host ('    KNOWN-DRIFT  ' + $_) }
$missInFresh | Select-Object -First 25 | ForEach-Object { Write-Host ('    MISSING-IN-FRESH ' + $_) }
Ok ($missInFresh.Count -eq 0) 'every live column exists in the fresh DB (migrations are now redundant)'

Write-Host '### 3) columns present in FRESH but not in LIVE (sanity: schema.sql did not invent columns)'
$extraInFresh = @($freshRows | Where-Object { $liveRows -notcontains $_ -and $knownDrift -notcontains $_ })
Write-Host ('  count=' + $extraInFresh.Count)
$extraInFresh | Select-Object -First 25 | ForEach-Object { Write-Host ('    EXTRA-IN-FRESH ' + $_) }
Ok ($extraInFresh.Count -eq 0) 'fresh DB adds nothing the live DB lacks (type/nullable/default matched too)'

Write-Host '### 4) table list diff (informational - legacy leftovers in the live DB are expected)'
$liveT = SqlAll $live "SELECT table_name FROM information_schema.tables WHERE table_schema='$live' AND table_type='BASE TABLE' ORDER BY table_name"
$freshT = SqlAll $fresh "SELECT table_name FROM information_schema.tables WHERE table_schema='$fresh' AND table_type='BASE TABLE' ORDER BY table_name"
$onlyLive = @($liveT | Where-Object { $freshT -notcontains $_ })
$onlyFresh = @($freshT | Where-Object { $liveT -notcontains $_ })
Write-Host ('  live=' + $liveT.Count + ' fresh=' + $freshT.Count + ' only-in-live=' + $onlyLive.Count + ' only-in-fresh=' + $onlyFresh.Count)
if ($onlyLive.Count) { Write-Host ('    only-in-live: ' + ($onlyLive -join ', ')) }
if ($onlyFresh.Count) { Write-Host ('    only-in-fresh: ' + ($onlyFresh -join ', ')) }
Ok ($freshT.Count -ge 100) 'fresh DB has the expected table count'

if (-not $Keep) { & $MYSQL -uroot -e "DROP DATABASE IF EXISTS $fresh" 2>$null | Out-Null; Write-Host ('  (scratch DB ' + $fresh + ' dropped; use -Keep to inspect)') }
else { Write-Host ('  (scratch DB ' + $fresh + ' kept for inspection)') }

Write-Host ''
Write-Host ('RESULT: ' + $pass + ' passed, ' + $fail + ' failed')
if ($fail -gt 0) { exit 1 }
