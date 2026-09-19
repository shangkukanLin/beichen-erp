# verify-fix-f7-53-54.ps1  (regression for batch-11: F7-53 source_bill_type realignment + F7-54 frontend enum maps)
#
# F7-53: the advance (over-collection / over-payment) ledger used to write
#        source_bill_type = SettlementStatus.ADVANCE ("ADVANCE") -- a value that does not exist in
#        the SourceBillType enum, so the list column "source" rendered the raw English code and the
#        source filter dropdown could not select it. Now writes SourceBillType.ADVANCE_LEDGER, and the
#        9 historical rows (receivable 5 + payable 4) were migrated.
# F7-54: enums.ts maps were incomplete -- SourceBillTypeLabel missed 6 codes (PURCHASE_EXCHANGE_IN /
#        _RETURN have 60 rows each in DB!), SourceBillDetailRoute missed 4 (the "source no." link did
#        nothing), SettlementStatus/Label/Tag missed ADVANCE (status column rendered English).
#        web-check.ps1 gained a 4th guard [enum guard] asserting: backend enum codes ⊆ frontend label keys.
#
# Assertions are read-only (no product row is written). ASCII-only on purpose (PS 5.1 + BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$SERVER = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp'
$WEB = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\api\enums.ts'
$env:MYSQL_PWD = 'root'
$script:fails = 0
function Sql([string]$sql) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>&1
  return ($out | Out-String).Trim()
}
function SqlOne([string]$sql) { $v = Sql $sql; if ($v -eq '') { return '' }; return ($v -split "`n")[0].Trim() }
function Ok([string]$m)   { Write-Output ("  [OK]   " + $m) }
function Bad([string]$m)  { Write-Output ("  [FAIL] " + $m); $script:fails++ }
function Info([string]$m) { Write-Output ("  [INFO] " + $m) }

# ---------- helpers: parse backend enum codes / frontend map keys ----------
function Get-EnumCodes([string]$javaPath) {
  if (-not (Test-Path $javaPath)) { return @() }
  $src = Get-Content $javaPath -Raw -Encoding UTF8
  $name = [System.IO.Path]::GetFileNameWithoutExtension($javaPath)
  return @([regex]::Matches($src, '(?m)^\s{4}([A-Z][A-Z0-9_]*)\s*[(",;]') |
    ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne $name } | Select-Object -Unique)
}
function Get-MapKeys([string]$tsSrc, [string]$mapName) {
  $block = [regex]::Match($tsSrc, ($mapName + '[^=]*=\s*\{([^}]*)\}'))
  if (-not $block.Success) { return $null }
  return @([regex]::Matches($block.Groups[1].Value, '[A-Z][A-Z0-9_]{2,}') |
    ForEach-Object { $_.Value } | Select-Object -Unique)
}

$ts = Get-Content $WEB -Raw -Encoding UTF8

# ================= F7-53 =================
Write-Output '=== F7-53: advance ledger source_bill_type realignment ==='

# A1: data was migrated (no more "ADVANCE" in source_bill_type; the ledger value is present)
$advR = [int](SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE source_bill_type='ADVANCE'")
$advP = [int](SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='ADVANCE'")
$ledR = [int](SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE source_bill_type='ADVANCE_LEDGER'")
$ledP = [int](SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='ADVANCE_LEDGER'")
if ($advR -eq 0 -and $advP -eq 0) { Ok "no row keeps the illegal value source_bill_type='ADVANCE' (receivable/payable)" }
else { Bad "illegal value still present: receivable=$advR payable=$advP" }
if ($ledR -eq 5 -and $ledP -eq 4) { Ok "advance ledger rows carry the legal value (receivable=$ledR payable=$ledP)" }
else { Bad "expected 5 receivable + 4 payable ledger rows, got $ledR + $ledP" }
# amounts must be untouched by the migration
$amtR = SqlOne "SELECT CONCAT(IFNULL(SUM(amount),0),'|',IFNULL(SUM(paid_amount),0),'|',IFNULL(SUM(unpaid_amount),0)) FROM finance_receivable WHERE source_bill_type='ADVANCE_LEDGER'"
$amtP = SqlOne "SELECT CONCAT(IFNULL(SUM(amount),0),'|',IFNULL(SUM(paid_amount),0),'|',IFNULL(SUM(unpaid_amount),0)) FROM finance_payable WHERE source_bill_type='ADVANCE_LEDGER'"
if ($amtR -eq '-15200.0000|-10000.0000|-5200.0000') { Ok "receivable ledger amounts unchanged ($amtR)" }
else { Bad "receivable ledger amounts drifted: $amtR" }
if ($amtP -eq '-3180.0000|-1090.0000|-2090.0000') { Ok "payable ledger amounts unchanged ($amtP)" }
else { Bad "payable ledger amounts drifted: $amtP" }

# A2: the write sites use the enum (and no write site still uses SettlementStatus for that column)
$rcSrc = Get-Content "$SERVER\finance\service\impl\FinanceReceiptServiceImpl.java" -Raw -Encoding UTF8
$pySrc = Get-Content "$SERVER\finance\service\impl\FinancePaymentServiceImpl.java" -Raw -Encoding UTF8
$mkSrc = Get-Content "$SERVER\finance\common\SourceBillType.java" -Raw -Encoding UTF8
$okWrites = ([regex]::Matches($rcSrc, 'setSourceBillType\(SourceBillType\.ADVANCE_LEDGER')).Count +
            ([regex]::Matches($pySrc, 'setSourceBillType\(SourceBillType\.ADVANCE_LEDGER')).Count
$badWrites = ([regex]::Matches($rcSrc, 'setSourceBillType\(SettlementStatus')).Count +
             ([regex]::Matches($pySrc, 'setSourceBillType\(SettlementStatus')).Count
if ($okWrites -eq 2) { Ok "both advance-ledger write sites use SourceBillType.ADVANCE_LEDGER" }
else { Bad "expected 2 ADVANCE_LEDGER write sites, found $okWrites" }
if ($badWrites -eq 0) { Ok "no write site passes a SettlementStatus value into source_bill_type" }
else { Bad "$badWrites write site(s) still use SettlementStatus for source_bill_type" }
if ($mkSrc -match 'ADVANCE_LEDGER\(') { Ok "SourceBillType declares ADVANCE_LEDGER" } else { Bad "SourceBillType.ADVANCE_LEDGER missing" }

# ================= F7-54 =================
Write-Output '=== F7-54: frontend enum maps completeness ==='

# B1: label maps cover every backend enum code
foreach ($pair in @(
    @{ Java = "$SERVER\finance\common\SourceBillType.java";   Map = 'SourceBillTypeLabel' },
    @{ Java = "$SERVER\finance\common\SettlementStatus.java"; Map = 'SettlementStatusLabel' })) {
  $codes = Get-EnumCodes $pair.Java
  $keys = Get-MapKeys $ts $pair.Map
  if ($null -eq $keys) { Bad ($pair.Map + ' block not found in enums.ts'); continue }
  $missing = @($codes | Where-Object { $keys -notcontains $_ })
  if ($missing.Count -eq 0) { Ok ($pair.Map + " covers all " + $codes.Count + " backend codes") }
  else { Bad ($pair.Map + ' misses ' + $missing.Count + ': ' + ($missing -join ', ')) }
}

# B2: route map covers the 4 newly added prefixes
$routeKeys = Get-MapKeys $ts 'SourceBillDetailRoute'
$needRoutes = @('SALE_RETURN_CHARGE','SALE_EXCHANGE_CHARGE','RETURN_SORT_LOSS','PAYABLE_TRANSFER')
$routeMissing = @($needRoutes | Where-Object { $routeKeys -notcontains $_ })
if ($routeMissing.Count -eq 0) { Ok 'SourceBillDetailRoute covers all 4 newly added source types' }
else { Bad ('SourceBillDetailRoute misses: ' + ($routeMissing -join ', ')) }

# B3: every source_bill_type value actually present in DB can be rendered as Chinese
$keysAll = Get-MapKeys $ts 'SourceBillTypeLabel'
$dbVals = @(Sql "SELECT DISTINCT IFNULL(source_bill_type,'') FROM finance_payable WHERE source_bill_type IS NOT NULL UNION SELECT DISTINCT IFNULL(source_bill_type,'') FROM finance_receivable WHERE source_bill_type IS NOT NULL" )
$dbVals = @($dbVals -split "`n" | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
$unmapped = @($dbVals | Where-Object { $keysAll -notcontains $_ })
if ($unmapped.Count -eq 0) { Ok ("all " + $dbVals.Count + " distinct source_bill_type values in DB are mapped") }
else { Bad ('DB values without a frontend label: ' + ($unmapped -join ', ')) }
Info ("DB source_bill_type values: " + ($dbVals -join ', '))

# B4: negative control -- the guard logic must DETECT a removed key (done in-memory, no file change)
# NOTE: PAYABLE_TRANSFER shares its line with RETURN_SORT_LOSS, so no line-anchored regex here.
$tampered = $ts -replace 'PAYABLE_TRANSFER', '__REMOVED__'
$tamperedKeys = Get-MapKeys $tampered 'SourceBillTypeLabel'
$detected = @(Get-EnumCodes "$SERVER\finance\common\SourceBillType.java" | Where-Object { $tamperedKeys -notcontains $_ })
if ($detected -contains 'PAYABLE_TRANSFER') { Ok "guard logic detects a removed key (negative control: PAYABLE_TRANSFER reported missing)" }
else { Bad 'guard logic failed to detect a removed key (negative control broken)' }
# and on the real file nothing is missing
$realMissing = @(Get-EnumCodes "$SERVER\finance\common\SourceBillType.java" | Where-Object { $keysAll -notcontains $_ })
if ($realMissing.Count -eq 0) { Ok 'real enums.ts passes the same guard logic' }
else { Bad ('real enums.ts misses: ' + ($realMissing -join ', ')) }

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures)' } else { Write-Output ("RESULT FAIL (" + $script:fails + " failures)") }
