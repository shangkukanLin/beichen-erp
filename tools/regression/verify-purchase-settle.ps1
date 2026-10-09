# 采购侧「结算方式」——2026-10-09 用户需求（参考新增销售单做法）。
# 口径（用户拍板）：现金 = 挂应付后**自动生成并立即审核付款单**核销本单应付（立刻付钱）；账期 = 只挂应付。
# 老单据默认现金（V6 列 DEFAULT 'CASH'；未指定即现金）。
#
# 断言策略（本项目纪律：不写快照数字、每条绿色断言都要能报红）：
#   ① 现金单审核 ⇒ 生成**已审核**付款单 + 应付被核销（paid = amount）；
#   ② 账期单审核 ⇒ **不生成**付款单 + 应付保持未核销（这是①的反向对照 = 天然负例）；
#   ③ 现金单反审核 ⇒ 自动付款单被冲正并作废，且**能反审核成功**（不会被我方自动付款永久拦死）；
#   ④ 入口校验：现金不选账户 / 未知结算方式 ⇒ **保存即报错**（绝不静默当成现金去动钱）；
#   ⑤ 缺夹具（无供应商/成品仓/产品/资金账户）⇒ SKIP 不判红。
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return ($out | Select-Object -First 1)
}
# 同步 XHR。⚠️ 请求体走 **base64** 传进浏览器再解码：本机 PowerShell 5.1 向原生 exe 传参时**双引号会被拆坏**
# （本仓已知坑，与「提交消息不能用多个 -m」同一根因）—— 直接拼 JSON 会让 {"supplierId" 变成 {supplierId，
# 服务端报 “Unexpected character ('s')… expecting double-quote”，实测踩过一次。
function ApiSend([string]$method, [string]$path, [string]$body) {
  $b64 = if ([string]::IsNullOrEmpty($body)) { '' } else { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($body)) }
  $js = "(()=>{const x=new XMLHttpRequest();x.open('" + $method + "','" + $path + "',false);" +
        "x.setRequestHeader('Authorization',String(localStorage.getItem('beichen_erp_token')));" +
        "let b=null;const B='" + $b64 + "';if(B){x.setRequestHeader('Content-Type','application/json');" +
        "b=new TextDecoder().decode(Uint8Array.from(atob(B),c=>c.charCodeAt(0)))}" +
        "x.send(b);return String(x.status)+' '+String(x.responseText).slice(0,400)})()"
  return (EvalJs $js).Trim([char]34)
}
function PaymentRows([int]$orderId) {
  return [int](SqlOne ("SELECT COUNT(*) FROM finance_payment WHERE source_bill_type='PURCHASE_ORDER' AND source_id=" + $orderId))
}
function PaymentAudited([int]$orderId) {
  return [int](SqlOne ("SELECT COUNT(*) FROM finance_payment WHERE source_bill_type='PURCHASE_ORDER' AND source_id=" + $orderId + " AND status='AUDITED'"))
}

EnsureLogin
# ⚠️ 登录必须**带公司**调接口（仓内套件统一做法，见工作区根 t1.ps1:30-32 的 Login $u $p $cid）：
#   不带 companyId 会 400「请选择公司」；而 UI 登录选中的公司不一定是权限所在的那家 ——
#   实测不带公司上下文的 token 在 purchase:order 上返回业务码 403，本脚本会因此整体 SKIP（而非报假红）。
$lr = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' `
  -Body (@{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
$tok = $lr.data.token
if (-not $tok) { Ok $false 'API login as lin@company1 failed'; Summary 'purchase settle type (cash auto-payment / credit)'; exit 1 }
EvalJs ("localStorage.setItem('beichen_erp_token','" + $tok + "');'ok'") | Out-Null

# ---------- 0) 夹具 ----------
$supId = SqlOne "SELECT id FROM supplier WHERE IFNULL(status,1) <> 0 ORDER BY id LIMIT 1"
$whId = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1"
$prodId = SqlOne "SELECT id FROM product ORDER BY id LIMIT 1"
$acctId = SqlOne "SELECT id FROM finance_account WHERE IFNULL(status,1) <> 0 ORDER BY id LIMIT 1"
if (-not $supId -or $supId -eq 'NULL' -or -not $whId -or $whId -eq 'NULL' -or -not $prodId -or $prodId -eq 'NULL' -or -not $acctId -or $acctId -eq 'NULL') {
  Skip ("fixture missing: supplier=$supId warehouse=$whId product=$prodId account=$acctId")
  Summary 'purchase settle type (cash auto-payment / credit)'
  exit 0
}
$today = (Get-Date).ToString('yyyy-MM-dd')
Write-Host ("  fixtures: supplier=$supId warehouse=$whId product=$prodId account=$acctId")

function NewOrder([string]$settleJson) {
  $body = '{"supplierId":' + $supId + ',"warehouseId":' + $whId + ',"orderDate":"' + $today + '","taxIncluded":0,"taxRate":0,"items":[{"productId":' + $prodId + ',"qualityType":"A","quantity":1,"unitPrice":10}]' + $settleJson + '}'
  # ⚠️ 路径是 /api/inventory/purchase（类级 @RequestMapping，见 PurchaseOrderController:21）——
  #    写成 /api/purchase/order 会被权限拦截器判成无权限并回业务码 403（曾据此误判为"管理员没采购权限"）。
  return (ApiSend 'POST' '/api/inventory/purchase' $body)
}

# ---------- 1) 现金单：审核 ⇒ 自动付款核销 ----------
$rCash = NewOrder (',"settleType":"CASH","settleAccountId":' + $acctId)
# ⚠️ 本仓接口一律 **HTTP 200 + 业务体 `{"code":NNN}`**（无权限/业务拒绝都是 200）⇒ 只能判业务 code，不能判 HTTP 状态。
if ($rCash -match '"code":403') {
  # 当前测试账号没有 purchase:order 权限 ⇒ 本脚本无法验证这条路径。显式 SKIP（绝不伪装成通过）。
  Skip ('the logged-in test user lacks the purchase:order permission, so this path cannot be verified: ' + $rCash.Substring(0, [Math]::Min(120, $rCash.Length)))
  Summary 'purchase settle type (cash auto-payment / credit)'
  exit 0
}
if ($rCash -notmatch '"code":200') {
  Ok $false ('cash purchase order create rejected: ' + $rCash)
  Summary 'purchase settle type (cash auto-payment / credit)'
  exit 1
}
$cashId = [int](($rCash.Substring(4) | ConvertFrom-Json).data)
$cashCode = SqlOne ("SELECT code FROM purchase_order WHERE id=" + $cashId)
Write-Host ("  cash order #$cashId created ($cashCode), settle_type = " + (SqlOne ("SELECT settle_type FROM purchase_order WHERE id=" + $cashId)))
$rAudit = ApiSend 'PUT' ("/api/inventory/purchase/" + $cashId + "/audit") ''
Ok ($rAudit -match '"code":200') ('the cash order audits successfully (' + $rAudit.Substring(0, [Math]::Min(60, $rAudit.Length)) + ')')
Ok ((PaymentRows $cashId) -eq 1) 'auditing a CASH order generated exactly one payment'
Ok ((PaymentAudited $cashId) -eq 1) 'that payment is already AUDITED (cash = paid immediately)'
$fp = SqlOne ("SELECT amount, paid_amount, status FROM finance_payable WHERE source_bill_type='PURCHASE_ORDER' AND source_bill_no='" + $cashCode + "'")
Write-Host ('  payable row (amount/paid/status) = ' + $fp)
$parts = @($fp -split "`t")
Ok ($parts.Count -ge 3 -and [decimal]$parts[1] -eq [decimal]$parts[0]) 'the payable of a cash order is fully settled by the auto payment'
Ok ($parts.Count -ge 3 -and $parts[2] -ne 'UNSETTLED') 'and its status is no longer UNSETTLED'

# ---------- 2) 现金单反审核：自动付款单被冲正作废，且反审核本身能成功 ----------
$rUn = ApiSend 'PUT' ("/api/inventory/purchase/" + $cashId + "/un-audit") ''
Ok ($rUn -match '"code":200') ('a cash order can still be un-audited (auto payment does not lock it): ' + $rUn.Substring(0, [Math]::Min(80, $rUn.Length)))
Ok ((PaymentAudited $cashId) -eq 0) 'un-auditing reversed the auto payment (no AUDITED payment left for this order)'
$fpAfter = SqlOne ("SELECT status FROM finance_payable WHERE source_bill_type='PURCHASE_ORDER' AND source_bill_no='" + $cashCode + "'")
Ok ($fpAfter -ne 'UNSETTLED') 'the payable was reversed as well (status = ' + $fpAfter + ')'

# ---------- 3) 账期单（反向对照）：审核 ⇒ 不生成付款单、应付保持未核销 ----------
$rCredit = NewOrder ',"settleType":"CREDIT"'
if ($rCredit -notmatch '"code":200') {
  Ok $false ('credit purchase order create rejected: ' + $rCredit)
} else {
  $creditId = [int](($rCredit.Substring(4) | ConvertFrom-Json).data)
  $creditCode = SqlOne ("SELECT code FROM purchase_order WHERE id=" + $creditId)
  $rAudit2 = ApiSend 'PUT' ("/api/inventory/purchase/" + $creditId + "/audit") ''
  Write-Host ("  credit order #$creditId created ($creditCode) ; audit => " + $rAudit2.Substring(0, [Math]::Min(60, $rAudit2.Length)))
  Ok ($rAudit2 -match '"code":200') 'the credit order audits successfully'
  Ok ((PaymentRows $creditId) -eq 0) 'a CREDIT order generates NO payment (negative control for the auto-payment branch)'
  $fp2 = SqlOne ("SELECT amount, paid_amount, status FROM finance_payable WHERE source_bill_type='PURCHASE_ORDER' AND source_bill_no='" + $creditCode + "'")
  $p2 = @($fp2 -split "`t")
  Ok ($p2.Count -ge 3 -and [decimal]$p2[1] -eq 0) 'its payable stays unpaid (credit = 挂账)'
  $rUn2 = ApiSend 'PUT' ("/api/inventory/purchase/" + $creditId + "/un-audit") ''
  Write-Host ('  credit order un-audit => ' + $rUn2.Substring(0, [Math]::Min(60, $rUn2.Length)))
}

# ---------- 4) 入口校验：现金不选账户 / 未知结算方式 ⇒ 保存即报错 ----------
$rNoAcct = NewOrder ',"settleType":"CASH"'
Write-Host ('  CASH without account => ' + $rNoAcct.Substring(0, [Math]::Min(90, $rNoAcct.Length)))
Ok ($rNoAcct -notmatch '"code":200') 'CASH without a payment account is rejected at save time'
$rBogus = NewOrder ',"settleType":"BOGUS"'
Write-Host ('  unknown settle type => ' + $rBogus.Substring(0, [Math]::Min(90, $rBogus.Length)))
Ok ($rBogus -notmatch '"code":200') 'an unknown settle type is rejected (never silently treated as cash)'

# ---------- 5) 物料订单：结算方式落在**订单**上（收货审核时透传读取） ----------
# 为什么必测"落库"：控制器是手写白名单，字段漏映射会被静默丢弃（成品采购单那边刚被抓到一模一样的坑）。
$matId = SqlOne "SELECT id FROM outsource_material ORDER BY id LIMIT 1"
if (-not $matId -or $matId -eq 'NULL') {
  Skip 'no outsource material (fixture missing) - the material-order settle check cannot run'
} else {
  function NewMatOrder([string]$settleJson) {
    # ⚠️ 明细数量键是 **orderQuantity**（MaterialOrderServiceImpl.parseItem:1149），写成 quantity 会静默落 0
    #    ⇒ 收货时被"超收"护栏拦下（实测：ordered=0 / over=1），整条透传路径测不到。
    $b = '{"orderType":"PURCHASE","supplierId":' + $supId + ',"targetWarehouseId":' + $whId +
         ',"deliveryDate":"' + $today + '","taxIncluded":0,"taxRate":0,"items":[{"materialId":' + $matId +
         ',"orderQuantity":1,"unitPrice":5}]' + $settleJson + '}'
    return (ApiSend 'POST' '/api/outsource/material-order' $b)
  }
  $rMatCredit = NewMatOrder ',"settleType":"CREDIT"'
  if ($rMatCredit -match '"code":200') {
    $matCreditId = [int](($rMatCredit.Substring(4) | ConvertFrom-Json).data)
    $mrow = SqlOne ("SELECT settle_type, IFNULL(settle_account_id,'NULL') FROM outsource_material_order WHERE id=" + $matCreditId)
    Write-Host ('  material order(CREDIT) #' + $matCreditId + ' => ' + $mrow)
    Ok ($mrow -match '^CREDIT') 'a CREDIT material order persists settle_type=CREDIT (field really reaches the DB)'
    Ok ($mrow -match 'NULL$') 'and its cash fields are cleared (no stale account on a credit order)'
    $rMatCashNoAcct = NewMatOrder ',"settleType":"CASH"'
    Ok ($rMatCashNoAcct -notmatch '"code":200') 'a CASH material order without an account is rejected at save time'
    $rMatCash = NewMatOrder (',"settleType":"CASH","settleAccountId":' + $acctId)
    if ($rMatCash -match '"code":200') {
      $matCashId = [int](($rMatCash.Substring(4) | ConvertFrom-Json).data)
      $mrow2 = SqlOne ("SELECT settle_type, IFNULL(settle_account_id,'NULL') FROM outsource_material_order WHERE id=" + $matCashId)
      Write-Host ('  material order(CASH) #' + $matCashId + ' => ' + $mrow2)
      Ok ($mrow2 -match ('^CASH\s+' + $acctId)) 'a CASH material order persists both the type and the payment account'
    } else {
      Ok $false ('cash material order create rejected: ' + $rMatCash)
    }
  } else {
    # 夹具/前置不满足（例如该订单类型要求带工厂标签的供应商）⇒ 显式 SKIP，不伪装通过
    Skip ('material order create not possible with available fixtures: ' + $rMatCredit.Substring(0, [Math]::Min(120, $rMatCredit.Length)))
  }
}

# ---------- 6) 物料收货路径：订单为现金 ⇒ **收货审核即自动付款**（结算方式透传实测） ----------
# 物料订单审核不产生应付，应付在**收货单审核**时生成 ⇒ 付款发生在那里（从订单读结算方式）。
$auxWhId = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY' ORDER BY id LIMIT 1"
# 明细表名是 outsource_material_order_item（不是 material_order_item —— 第一次写错，导致夹具判空而 SKIP）
$moItemId = if ($matCashId) { SqlOne ("SELECT id FROM outsource_material_order_item WHERE order_id=" + $matCashId + " ORDER BY id LIMIT 1") } else { '' }
if ($matCashId -and $auxWhId -and $auxWhId -ne 'NULL' -and $moItemId -and $moItemId -ne 'NULL') {
  # 订单必须先审核才能收货（后端护栏："订单尚未审核，请先审核订单再收货"）
  $rMoAudit = ApiSend 'PUT' ('/api/outsource/material-order/' + $matCashId + '/audit') ''
  Write-Host ('  material order audit => ' + $rMoAudit.Substring(0, [Math]::Min(130, $rMoAudit.Length)))
  $rcvBody = '{"factoryId":' + $supId + ',"warehouseId":' + $auxWhId + ',"items":[{"itemId":' + $moItemId + ',"quantity":1}]}'
  $rRcv = ApiSend 'POST' ('/api/outsource/material-order/' + $matCashId + '/receive') $rcvBody
  Write-Host ('  material receive => ' + $rRcv.Substring(0, [Math]::Min(150, $rRcv.Length)))
  if ($rRcv -match '"code":200') {
    $dlvId = SqlOne ('SELECT id FROM outsource_delivery WHERE source_order_id=' + $matCashId + ' ORDER BY id DESC LIMIT 1')
    if ($dlvId -and $dlvId -ne 'NULL') {
      $rDa = ApiSend 'PUT' ('/api/outsource/delivery/' + $dlvId + '/audit') ''
      Write-Host ('  delivery audit => ' + $rDa.Substring(0, [Math]::Min(150, $rDa.Length)))
      if ($rDa -match '"code":200') {
        $payCnt = [int](SqlOne ("SELECT COUNT(*) FROM finance_payment WHERE source_bill_type='OUTSOURCE_MATERIAL_DELIVERY' AND source_id=" + $dlvId + " AND status='AUDITED'"))
        Ok ($payCnt -ge 1) 'auditing the delivery of a CASH material order auto-pays the new payable (transfer works)'
        $pv = SqlOne ("SELECT amount, paid_amount FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_DELIVERY' AND source_id=" + $dlvId)
        $pp = @($pv -split "`t")
        Write-Host ('  delivery payable (amount/paid) = ' + $pv)
        Ok ($pp.Count -ge 2 -and [decimal]$pp[1] -gt 0) 'the delivery payable is settled by that auto payment'
      } else {
        Skip ('delivery audit rejected (cannot verify the transfer path here): ' + $rDa.Substring(0, [Math]::Min(110, $rDa.Length)))
      }
    } else {
      Skip 'no delivery row found after receive'
    }
  } else {
    Skip ('material receive rejected (fixture/flow precondition): ' + $rRcv.Substring(0, [Math]::Min(110, $rRcv.Length)))
  }
} else {
  Skip ('missing fixtures for the material delivery check: order=' + $matCashId + ' auxWarehouse=' + $auxWhId + ' item=' + $moItemId)
}

Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'
Summary 'purchase settle type (cash auto-payment / credit)'
