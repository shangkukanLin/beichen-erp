# 销售单明细品质护栏（用户口径 2026-09-21：「销售单，产品明细的品质，不能有不良品和待整理」）的耐久 API 用例。
#   口径：销售单明细品质只允许 A规/B规/C规；DEFECT（不良品）与 PENDING（待整理）一律拒绝，
#         且**保存草稿**与**审核**两处同一口径（assertItemQualitySellable）。
#   覆盖：①DEFECT 建单被拒（中文提示 + 不落库）②PENDING 建单被拒 ③非法品质值被拒
#         ④A 级可建（正例）⑤库内不变量：不存在任何 DEFECT/PENDING 的销售明细
#   自清理：正例单据走 API 作废（不物理删除）；夹具全部运行时从库里取。ASCII ONLY。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) | Select-Object -First 1)
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function SqlList([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @($o | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
}
function MsgOf($r) { if ($null -eq $r) { return '' }; return ([string]$r.msg) }
function CallPost([string]$url, $h, [string]$body) {
  try { return Invoke-RestMethod -Uri $url -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) }
  catch { return $null }
}
function CallPut([string]$url, $h) {
  try { return Invoke-RestMethod -Uri $url -Method Put -Headers $h } catch { return $null }
}

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ([bool]$lg.data.token) 'logged in'

$cust = [int](SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1")
$wh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_type='FINISHED' ORDER BY id LIMIT 1")
$prod = [int](SqlOne "SELECT id FROM product ORDER BY id LIMIT 1")
$ordersBefore = D (SqlOne "SELECT COUNT(*) FROM sale_order")
Write-Host ("fixtures: cust=$cust wh=$wh product=$prod sale_orders=$ordersBefore")

function TryCreate([string]$qt, [string]$tag) {
  $body = @{ order = @{ customerId = $cust; warehouseId = $wh; settleType = 'CREDIT'; remark = ('verify sale quality ' + $tag) }
    items = @(@{ productId = $prod; qualityType = $qt; quantity = 1; unitPrice = 1 }) } | ConvertTo-Json -Depth 6
  # 注意：销售单接口挂在 /api/inventory/sale（SaleOrderController 的 @RequestMapping），不是 /sale/order
  return CallPost "$base/inventory/sale" $h $body
}

if ($cust -gt 0 -and $wh -gt 0 -and $prod -gt 0) {
  # ---- A) DEFECT (不良品) must be refused
  Write-Host '--- A) qualityType=DEFECT must be refused'
  $r = TryCreate 'DEFECT' 'defect'
  $msg = MsgOf $r
  Write-Host ("  DEFECT -> code=$($r.code) msg=$msg")
  Ok (($r.code -ne 200) -and ($r.code -ne 0)) 'a DEFECT sales item is refused'
  Ok ($msg -match '[\u4e00-\u9fa5]') 'the refusal is explained in words'
  Ok ($msg -match 'DEFECT' -or $msg.Contains([char]0x4E0D + [string][char]0x826F)) 'the refusal names the offending quality (bad item)'
  Ok ((D (SqlOne "SELECT COUNT(*) FROM sale_order")) -eq $ordersBefore) 'nothing was persisted for the refused order'

  # ---- B) PENDING (待整理) must be refused
  Write-Host '--- B) qualityType=PENDING must be refused'
  $r2 = TryCreate 'PENDING' 'pending'
  $msg2 = MsgOf $r2
  Write-Host ("  PENDING -> code=$($r2.code) msg=$msg2")
  Ok (($r2.code -ne 200) -and ($r2.code -ne 0)) 'a PENDING sales item is refused'
  Ok ($msg2 -match '[\u4e00-\u9fa5]') 'the PENDING refusal is explained in words'
  Ok ((D (SqlOne "SELECT COUNT(*) FROM sale_order")) -eq $ordersBefore) 'nothing was persisted for the PENDING order'

  # ---- C) an unknown quality code must be refused too
  Write-Host '--- C) unknown quality code must be refused'
  $r3 = TryCreate 'ZZZ' 'unknown'
  Write-Host ("  ZZZ -> code=$($r3.code) msg=$(MsgOf $r3)")
  Ok (($r3.code -ne 200) -and ($r3.code -ne 0)) 'an unknown quality code is refused'

  # ---- D) positive control: quality A must be accepted
  Write-Host '--- D) qualityType=A must be accepted (positive control)'
  $r4 = TryCreate 'A' 'ok'
  Write-Host ("  A -> code=$($r4.code)")
  Ok (($r4.code -eq 200) -or ($r4.code -eq 0)) 'a grade-A sales item is accepted'
  # 建单接口回 R.ok() 不带体（响应里没有 id）⇒ 按 remark 反查本次新建的单
  $oid = [int](SqlOne "SELECT id FROM sale_order WHERE remark='verify sale quality ok' ORDER BY id DESC LIMIT 1")
  Ok ($oid -gt 0) 'the accepted order is persisted'
  if ($oid -gt 0) {
    Ok ((SqlOne "SELECT quality_type FROM sale_order_item WHERE order_id=$oid LIMIT 1") -eq 'A') 'the stored item keeps quality A'
    CallPut "$base/inventory/sale/$oid/cancel" $h | Out-Null
    $st = SqlOne "SELECT status FROM sale_order WHERE id=$oid"
    Write-Host ('  probe order ' + $oid + ' status=' + $st)
    Ok ($st -eq 'CANCELLED') 'the probe order is cancelled (trace kept, never audited)'
  }
  # 清掉本脚本历史版本留下的未作废探针单（幂等自清理）
  foreach ($old in (SqlList "SELECT id FROM sale_order WHERE remark='verify sale quality ok' AND status='DRAFT'")) {
    CallPut "$base/inventory/sale/$old/cancel" $h | Out-Null
  }

  # ---- E) data invariant: no sales item may sit at DEFECT/PENDING
  Write-Host '--- E) invariant: no sale_order_item at DEFECT/PENDING'
  $bad = D (SqlOne "SELECT COUNT(*) FROM sale_order_item WHERE quality_type IN ('DEFECT','PENDING')")
  Ok (($bad -eq 0)) ('no sales item at DEFECT/PENDING in the whole table (got ' + $bad + ')')
}

Write-Host ''
if ($fail -eq 0) { Write-Host 'RESULT PASS sale order quality guard (no DEFECT / no PENDING)' } else { Write-Host ('RESULT FAIL sale order quality guard, failures=' + $fail); exit 1 }
