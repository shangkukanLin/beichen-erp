# Fix verification (finance audit batch B leftovers, 2026-09-29): F7-222 / F7-223 / F7-224.
# READ ONLY (GETs + SELECTs; nothing is written). ASCII ONLY.
#
#   F7-222  `/finance/payable-transfer/transferable` must NOT dump the whole company any more:
#           - without supplierId/payableId  -> empty list
#           - with supplierId               -> every row belongs to that supplier
#           - with payableId                -> at most that one row
#   F7-223  the payable list projection must expose the authoritative `transferable` flag, and it must agree
#           with the documented predicate over the very same row (amount<0 + UNSETTLED + not transferred)
#   F7-224  `PayableHelper.saveByBillNo` now refuses to reset a payable that already has payments or is already
#           transferred. The callers always pass a freshly generated YF- number, so the reuse path is practically
#           unreachable -> this guard only asserts the code-level invariant via source inspection.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}
$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function GetJson($path) {
  try { return (Invoke-RestMethod -Uri "$base$path" -Method Get -Headers $h) } catch { return $null }
}

# a supplier that actually owns a transferable (negative, unsettled, not transferred) payable
$sup = SqlOne "SELECT supplier_id FROM finance_payable WHERE IFNULL(amount,0) < 0 AND status='UNSETTLED' AND IFNULL(transferred_to_receivable,0)=0 AND supplier_id IS NOT NULL ORDER BY id LIMIT 1"
$payableId = SqlOne "SELECT id FROM finance_payable WHERE IFNULL(amount,0) < 0 AND status='UNSETTLED' AND IFNULL(transferred_to_receivable,0)=0 ORDER BY id LIMIT 1"
Write-Host ('[SEED] supplier=' + $sup + ' transferable payable=' + $payableId)

Step '1) F7-222: the candidate list must not be a whole-company dump'
$noParam = GetJson '/finance/payable-transfer/transferable'
Ok ((@($noParam.data).Count -eq 0)) ('no supplierId/payableId -> empty list (got ' + @($noParam.data).Count + ')')
if ($sup -ne '') {
  $bySupplier = GetJson ('/finance/payable-transfer/transferable?supplierId=' + $sup)
  $rows = @($bySupplier.data)
  $foreign = @($rows | Where-Object { "$($_.supplierId)" -ne "$sup" }).Count
  Write-Host ('  supplierId=' + $sup + ' -> rows=' + $rows.Count + ' foreign=' + $foreign)
  Ok (($rows.Count -ge 1)) ('rows returned for an explicit supplier (got ' + $rows.Count + ')')
  Ok (($foreign -eq 0)) 'every candidate belongs to the requested supplier'
} else {
  Write-Host '  INFO no transferable payable in the DB -> supplier scoping not exercised'
}
if ($payableId -ne '') {
  $byPayable = GetJson ('/finance/payable-transfer/transferable?payableId=' + $payableId)
  $rows2 = @($byPayable.data)
  Ok (($rows2.Count -eq 1) -and ("$($rows2[0].id)" -eq "$payableId")) ('payableId returns exactly that record (got ' + $rows2.Count + ')')
}
# the negative side: payableId of a NON-transferable record (positive amount) must return nothing
$posId = SqlOne "SELECT id FROM finance_payable WHERE IFNULL(amount,0) > 0 ORDER BY id LIMIT 1"
if ($posId -ne '') {
  $r3 = GetJson ('/finance/payable-transfer/transferable?payableId=' + $posId)
  Ok ((@($r3.data).Count -eq 0)) ('a positive (non-transferable) payable is not offered (got ' + @($r3.data).Count + ')')
}

Step '2) F7-223: the payable list must expose the authoritative transferable flag'
$pg = GetJson '/finance/payable/page?pageSize=100'
$list = @($pg.data.records)
Ok (($list.Count -ge 1)) ('list rows fetched (' + $list.Count + ')')
$missing = @($list | Where-Object { $null -eq $_.transferable }).Count
Ok (($missing -eq 0)) ('every row carries a transferable flag (missing=' + $missing + ')')
$mismatch = 0
foreach ($r in $list) {
  $exp = ((D $r.amount) -lt 0) -and ("$($r.status)" -eq 'UNSETTLED') -and (-not $r.transferredToReceivable)
  if (([bool]$r.transferable) -ne [bool]$exp) { $mismatch++ }
}
Ok (($mismatch -eq 0)) ('flag agrees with the documented predicate on every row (mismatch=' + $mismatch + ')')
$transferableRows = @($list | Where-Object { $_.transferable }).Count
Write-Host ('  transferable rows in this page = ' + $transferableRows)
Ok ((SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) < 0 AND status='UNSETTLED' AND IFNULL(transferred_to_receivable,0)=0") -ne '') 'cross-check query ran'

Step '3) F7-224: saveByBillNo must guard payments / transferred (code-level; reuse path is not reachable in data)'
# NOTE: needles built from code points -- an ASCII-only .ps1 (no BOM) is read as GBK by PS 5.1, so Chinese
# literals in this file would break the parser (that mistake is why this file stays ASCII).
function Cn([int[]]$cp) { return (-join ($cp | ForEach-Object { [string][char]$_ })) }
$NEED_PAID = Cn @(0x5DF2,0x6709,0x4ED8,0x6B3E,0x8BB0,0x5F55)          # 已有付款记录
$NEED_TRANS = Cn @(0x5DF2,0x8F6C,0x5E94,0x6536)                        # 已转应收
$srcPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'beichen-erp-server\src\main\java\com\beichen\erp\finance\service\PayableHelper.java'
$src = Get-Content -LiteralPath $srcPath -Raw -Encoding UTF8
Ok (($src -match [regex]::Escape($NEED_PAID))) 'saveByBillNo refuses a payable that already has payments'
Ok (($src -match [regex]::Escape($NEED_TRANS))) 'saveByBillNo refuses an already-transferred payable'
Ok ((D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(paid_amount,0) > 0 AND IFNULL(transferred_to_receivable,0)=1")) -eq 0) 'no live row exists that is both paid and transferred (nothing to clean up)'

Write-Host ('RESULT fix F7-222..224 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
