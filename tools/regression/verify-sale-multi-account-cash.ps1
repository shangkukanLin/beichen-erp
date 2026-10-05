# verify: 销售单多账户现金收款 —— F7-264 / F7-265 修复的回归守卫（2026-10-04 审核批 1）
#
# 覆盖（全部只建**草稿**、从不调用审核 ⇒ 不产生库存/台账/流水腿；收尾按 7 张表计数自检还原）：
#   A) F7-264：现金单改成「账期」后 settle_account_id、settle_amount、分款行**三者一起清**（禁止半清）
#   B) F7-265①：无分款行的兼容分支拒绝 负数/零 的 settleAmount（null 仍放行 = 未填 = 全额收款）
#   C) F7-265②：草稿保存即拦"超收"（本次收款总额 > 应收总额），不再等到审核才被拒
#   D) 对照：多账户正常路径可建单（两账户合计 = 总额）
# ASCII ONLY；可重复跑（幂等）。
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return "$($l[0])".Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function Login([string]$u, [string]$p) {
  $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
  return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20
}
function PostJson([string]$url, $obj, [string]$tok) {
  $json = ConvertTo-Json -InputObject $obj -Depth 8 -Compress
  try { return Invoke-RestMethod -Uri $url -Method Post -Headers @{ Authorization = $tok } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 25 }
  catch { try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); return ($sr.ReadToEnd() | ConvertFrom-Json) } catch { return [pscustomobject]@{ code = -1; msg = $_.Exception.Message } } }
}
function PutJson([string]$url, $obj, [string]$tok) {
  $json = ConvertTo-Json -InputObject $obj -Depth 8 -Compress
  try { return Invoke-RestMethod -Uri $url -Method Put -Headers @{ Authorization = $tok } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 25 }
  catch { try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); return ($sr.ReadToEnd() | ConvertFrom-Json) } catch { return [pscustomobject]@{ code = -1; msg = $_.Exception.Message } } }
}
function Counts { return @(
    [int](SqlOne "SELECT COUNT(*) FROM sale_order"),
    [int](SqlOne "SELECT COUNT(*) FROM sale_order_item"),
    [int](SqlOne "SELECT COUNT(*) FROM sale_order_settle_account"),
    [int](SqlOne "SELECT COUNT(*) FROM finance_receipt"),
    [int](SqlOne "SELECT COUNT(*) FROM finance_receivable"),
    [int](SqlOne "SELECT COUNT(*) FROM finance_cashflow"),
    [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock_log")
  ) }

$la = Login 'lin' '123'
if ($null -eq $la -or [string]$la.code -ne '200') { Write-Output 'FAIL cannot login (lin)'; Write-Output 'RESULT SALE-MULTI-ACCOUNT FAIL count 1'; exit 1 }
$tok = [string]$la.data.token
$cust = SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1"
$prod = SqlOne "SELECT id FROM product ORDER BY id LIMIT 1"
$acct = SqlOne "SELECT id FROM finance_account WHERE company_id=1 AND status=1 AND account_type='CASH' ORDER BY id LIMIT 1"
if ($acct -eq '') { $acct = SqlOne "SELECT id FROM finance_account WHERE company_id=1 AND status=1 ORDER BY id LIMIT 1" }
$acct2 = SqlOne "SELECT id FROM finance_account WHERE company_id=1 AND status=1 AND id<>$acct ORDER BY id LIMIT 1"
if ($cust -eq '' -or $prod -eq '' -or $acct -eq '' -or $acct2 -eq '') { Write-Output 'FAIL fixture unavailable'; Write-Output 'RESULT SALE-MULTI-ACCOUNT FAIL count 1'; exit 1 }
Write-Output ("fixture: customer=$cust product=$prod account=$acct account2=$acct2")

$before = Counts
$created = @()
$item = @( @{ productId = [int]$prod; quantity = 1; unitPrice = 300 } )

# ---------- D) 对照：多账户正常路径 ----------
$r1 = PostJson "$api/inventory/sale" @{ order = @{ customerId = [int]$cust; settleType = 'CASH'; settleAmount = 300; settleAccounts = @(
      @{ accountId = [int]$acct; amount = 200 }, @{ accountId = [int]$acct2; amount = 100 } ) }; items = $item } $tok
if ([string]$r1.code -eq '200') { $created += [int]$r1.data; Ok ('control: multi-account draft accepted (order ' + $r1.data + ')') }
else { Bad ('control: multi-account draft rejected: ' + $r1.code + ' ' + $r1.msg) }

# ---------- A) F7-264：切回账期必须三个字段一起清 ----------
if ($created.Count -ge 1) {
  $oid = $created[0]
  $r2 = PutJson "$api/inventory/sale/$oid" @{ order = @{ customerId = [int]$cust; settleType = 'CREDIT' }; items = $item } $tok
  if ([string]$r2.code -ne '200') { Bad ('switch-to-credit update failed: ' + $r2.code + ' ' + $r2.msg) }
  else {
    $sa = SqlOne ("SELECT IFNULL(settle_amount,'NULL') FROM sale_order WHERE id=" + $oid)
    $said = SqlOne ("SELECT IFNULL(settle_account_id,'NULL') FROM sale_order WHERE id=" + $oid)
    $rowsLeft = SqlOne ("SELECT COUNT(*) FROM sale_order_settle_account WHERE order_id=" + $oid)
    Write-Output ("  after switch to CREDIT: settle_amount=$sa settle_account_id=$said split_rows=$rowsLeft")
    if ($sa -eq 'NULL') { Ok 'F7-264: settle_amount cleared on switch to credit' } else { Bad ('F7-264: credit order keeps stale settle_amount=' + $sa) }
    if ($said -eq 'NULL' -and $rowsLeft -eq '0') { Ok 'F7-264: settle_account_id and split rows cleared together (no half-clear)' } else { Bad ("F7-264: half-clear (account=$said rows=$rowsLeft)") }
  }
}

# ---------- B) F7-265①：兼容分支拒绝负数/零总额 ----------
$neg = PostJson "$api/inventory/sale" @{ order = @{ customerId = [int]$cust; settleType = 'CASH'; settleAccountId = [int]$acct; settleAmount = -100; settleAccounts = @() }; items = $item } $tok
if ([string]$neg.code -eq '200') { $created += [int]$neg.data; Bad ('F7-265: negative settleAmount still accepted (order ' + $neg.data + ')') }
else { Ok ('F7-265: negative settleAmount rejected (' + $neg.code + ')') }
$zero = PostJson "$api/inventory/sale" @{ order = @{ customerId = [int]$cust; settleType = 'CASH'; settleAccountId = [int]$acct; settleAmount = 0; settleAccounts = @() }; items = $item } $tok
if ([string]$zero.code -eq '200') { $created += [int]$zero.data; Bad ('F7-265: zero settleAmount still accepted (order ' + $zero.data + ')') }
else { Ok ('F7-265: zero settleAmount rejected (' + $zero.code + ')') }
$legacy = PostJson "$api/inventory/sale" @{ order = @{ customerId = [int]$cust; settleType = 'CASH'; settleAccountId = [int]$acct; settleAccounts = @() }; items = $item } $tok
if ([string]$legacy.code -eq '200') { $created += [int]$legacy.data; Ok ('F7-265 control: legacy no-amount path still allowed (null = full amount, order ' + $legacy.data + ')') }
else { Bad ('F7-265 control: legacy no-amount path rejected: ' + $legacy.code + ' ' + $legacy.msg) }

# ---------- C) F7-265②：草稿保存即拦超收 ----------
$over = PostJson "$api/inventory/sale" @{ order = @{ customerId = [int]$cust; settleType = 'CASH'; settleAmount = 301; settleAccounts = @( @{ accountId = [int]$acct; amount = 301 } ) }; items = $item } $tok
if ([string]$over.code -eq '200') { $created += [int]$over.data; Bad ('F7-265: over-collection accepted at draft time (order ' + $over.data + ')') }
else { Ok ('F7-265: over-collection rejected at draft time (' + $over.code + ')') }

# ---------- cleanup + 计数自检 ----------
foreach ($id in $created) {
  SqlExec ("DELETE FROM sale_order_settle_account WHERE order_id=" + $id)
  SqlExec ("DELETE FROM sale_order_item WHERE order_id=" + $id)
  SqlExec ("DELETE FROM sale_order WHERE id=" + $id)
}
if ($created.Count -gt 0) {
  $left = SqlOne ("SELECT COUNT(*) FROM sale_order WHERE id IN (" + (($created | ForEach-Object { "$_" }) -join ',') + ")")
  if ($left -eq '0') { Ok ('probe rows cleaned up (' + $created.Count + ' orders)') } else { Bad ('probe rows left: ' + $left) }
}
$after = Counts
$names = @('sale_order', 'sale_order_item', 'sale_order_settle_account', 'finance_receipt', 'finance_receivable', 'finance_cashflow', 'warehouse_stock_log')
$drift = @()
for ($i = 0; $i -lt $names.Count; $i++) { if ($before[$i] -ne $after[$i]) { $drift += ($names[$i] + ' ' + $before[$i] + '->' + $after[$i]) } }
Write-Output ("counts before/after: order $($before[0])/$($after[0]) item $($before[1])/$($after[1]) split $($before[2])/$($after[2]) receipt $($before[3])/$($after[3]) recv $($before[4])/$($after[4]) cashflow $($before[5])/$($after[5]) stocklog $($before[6])/$($after[6])")
if ($drift.Count -eq 0) { Ok 'per-table counts identical before/after (zero residual)' } else { Bad ('count drift: ' + ($drift -join '; ')) }

if ($fail -eq 0) { Write-Output 'RESULT SALE-MULTI-ACCOUNT PASS' } else { Write-Output ("RESULT SALE-MULTI-ACCOUNT FAIL count " + $fail) }
exit $fail
