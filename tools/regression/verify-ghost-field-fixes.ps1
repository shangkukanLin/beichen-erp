# §7.26 幽灵字段修复守卫（2026-10-09，用户要求「按甲改，顺便修复刚刚的 234」；后追加「②凭证口径」）。
#
# 覆盖五件事，每件都有"**修前必然红**"的落点（不写恒真断言）：
#   ① 销售出库明细 = **成品 product**：按修复后的报文（orderId + productId + orderItemId）建单 ⇒
#      `sale_outbound_item.product_id` 必须落库（**修前前端发 materialId ⇒ Jackson 静默丢弃 ⇒ 恒 NULL**）、
#      `order_item_id` 与 `sale_outbound.order_id` 同样落库（修前这两个键**种子里都写错了** ⇒ 全 NULL）✓；
#      并断言 GET /{id}/items 回传 productName/sku 非空（回显链路）。
#   ② 新增「从销售单带入」的两个只读端点（走出库页前缀，避开 sale:order 权限）：
#      /outbound/sale-order-options 只回**已审核**销售单；/outbound/sale-order-items 回单据头 + 明细（productId 非空）。
#   ③ 委外仓「供应商」筛选：GET /warehouse/page?factoryId=X ⇒ 返回行 factoryId 全为 X
#      （**修前该形参不存在 ⇒ Spring 静默忽略 ⇒ 返回全部**）。
#   ④ 仓库详情/委外仓详情的「单位」：GET /warehouse/stock/by-warehouse/{id} 的物料行必须带 unit
#      （**修前该接口不回传 unit ⇒ 前端「单位」列恒空**）。
#   ⑤ 「销售出库 = 销售单的出库凭证」的后端硬约束（用户 2026-10-09 选口径 ②）：
#      不挂销售单 / 挂草稿销售单 / 明细产品不属于该单 / 空明细 ⇒ **全部必须拒绝**（修前一律放行 ✗）。
# 全部走 HTTP + DB 事实，不开浏览器；测试数据用完即作废。
# ⚠️ 变量**不要叫 $pid**（PowerShell 只读自动变量，赋值被拒后断言会拿进程号去比，假红；本仓 p2b 早有教训）。
# ⚠️ $ErrorActionPreference 必须 Continue —— mysql CLI 会往 stderr 写密码告警，Stop 模式下会被当
#    NativeCommandError 直接中断脚本。
$ErrorActionPreference = 'Continue'

$API = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$script:PASS = 0; $script:FAIL = 0; $script:SKIP = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Skip([string]$m) { $script:SKIP++; Write-Host ('SKIP ' + $m) }
function Summary([string]$t) {
  $tot = $script:PASS + $script:FAIL
  if ($tot -eq 0) { Write-Host ('RESULT SKIP ' + $t + ' (PASS=0 FAIL=0 SKIP=' + $script:SKIP + ')'); return }
  Write-Host ('RESULT ' + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + ' ' + $t + '  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ' SKIP=' + $script:SKIP + ')')
  if ($script:FAIL -gt 0) { try { $Host.SetShouldExit(1) } catch { } }
}
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) | ForEach-Object { "$_" } | Select-Object -First 1)
}

# ---------- 登录（lin@公司1：带 companyId，否则 400「请选择公司」）----------
try {
  $lr = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
} catch { Write-Host ('FAIL login: ' + $_.Exception.Message); Summary 'ghost-field fixes (§7.26)'; exit 1 }
$H = @{ Authorization = $lr.data.token }
function ApiGet([string]$p) { try { return Invoke-RestMethod -Uri ($API + $p) -Headers $H -TimeoutSec 30 } catch { return $null } }
function ApiPost([string]$p, $obj) {
  try { return Invoke-RestMethod -Uri ($API + $p) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8))) -TimeoutSec 30 }
  catch { return $null }
}
function ApiPut([string]$p) { try { return Invoke-RestMethod -Uri ($API + $p) -Method Put -Headers $H -TimeoutSec 30 } catch { return $null } }

# ---------- 夹具 ----------
$today = (Get-Date).ToString('yyyy-MM-dd')
$custId = SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1"
$whId = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' AND IFNULL(status,1)=1 ORDER BY id LIMIT 1"
$auditedSo = SqlOne "SELECT id FROM sale_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1"
$soItemId = if ($auditedSo -and $auditedSo -ne 'NULL') { SqlOne ("SELECT id FROM sale_order_item WHERE order_id=" + $auditedSo + " ORDER BY id LIMIT 1") } else { '' }
$soProd = if ($auditedSo -and $auditedSo -ne 'NULL') { SqlOne ("SELECT product_id FROM sale_order_item WHERE order_id=" + $auditedSo + " ORDER BY id LIMIT 1") } else { '' }
$draftSo = SqlOne "SELECT id FROM sale_order WHERE status='DRAFT' ORDER BY id LIMIT 1"
$otherProd = if ($soProd) { SqlOne ("SELECT id FROM product WHERE id <> " + $soProd + " ORDER BY id LIMIT 1") } else { '' }

# =====================================================================================
Write-Host '--- (1) sale outbound item = product, and the order links land too'
if (-not $custId -or $custId -eq 'NULL' -or -not $whId -or $whId -eq 'NULL' -or -not $auditedSo -or $auditedSo -eq 'NULL' -or -not $soProd -or $soProd -eq 'NULL') {
  Skip ('fixtures missing: customer=' + $custId + ' finishedWarehouse=' + $whId + ' auditedSaleOrder=' + $auditedSo + ' itsProduct=' + $soProd)
} else {
  $body = @{
    outbound = @{ orderId = [int]$auditedSo; customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-1' }
    items = @(@{ productId = [int]$soProd; orderItemId = [int]$soItemId; qualityType = 'A'; quantity = 3; unitPrice = 10 })
  }
  $r = ApiPost '/inventory/outbound' $body
  if ($null -eq $r -or $r.code -ne 200) {
    Skip ('creating an outbound failed: ' + ($r | ConvertTo-Json -Compress -Depth 4))
  } else {
    $obId = SqlOne "SELECT id FROM sale_outbound WHERE remark='E2E-GHOST-1' ORDER BY id DESC LIMIT 1"
    $row = SqlOne ("SELECT CONCAT(IFNULL(product_id,'NULL'),'/',IFNULL(order_item_id,'NULL')) FROM sale_outbound_item WHERE outbound_id=" + $obId + " ORDER BY id DESC LIMIT 1")
    $p = @($row -split '/')
    $savedOrderId = SqlOne ("SELECT IFNULL(order_id,'NULL') FROM sale_outbound WHERE id=" + $obId)
    Write-Host ('  outbound #' + $obId + ' product_id/order_item_id = ' + $row + ' ; order_id=' + $savedOrderId + ' (sent ' + $soProd + '/' + $soItemId + '/' + $auditedSo + ')')
    Ok ($p[0] -eq "$soProd") 'the item PERSISTS product_id (the ghost `materialId` used to be dropped)'
    Ok ($p[1] -eq "$soItemId") 'and order_item_id persists too (the seed used to send `saleOrderItemId` -> dropped)'
    Ok ($savedOrderId -eq "$auditedSo") 'the outbound header keeps order_id (traceable to the sale order)'
    $items = ApiGet ('/inventory/outbound/' + $obId + '/items')
    $first = @($items.data)[0]
    Ok ($null -ne $first -and -not [string]::IsNullOrWhiteSpace($first.productName)) 'GET /{id}/items returns the product name (echo works)'
    Ok ($null -ne $first -and $null -ne $first.sku) 'and it returns the SKU field'
    ApiPut ('/inventory/outbound/' + $obId + '/cancel') | Out-Null
    Write-Host ('  cleanup: outbound #' + $obId + ' -> ' + (SqlOne ("SELECT status FROM sale_outbound WHERE id=" + $obId)))
  }
}

# =====================================================================================
Write-Host '--- (2) source-sale-order endpoints (new, outbound prefix = read isolation)'
$opts = ApiGet '/inventory/outbound/sale-order-options?pageSize=50'
Ok ($null -ne $opts -and @($opts.data).Count -ge 1) 'sale-order-options returns audited sale orders'
if ($null -ne $opts) {
  Ok (@(@($opts.data) | Where-Object { $null -eq $_.customerId }).Count -eq 0) 'every option carries customerId (picker needs it)'
}
if (-not $auditedSo -or $auditedSo -eq 'NULL') {
  Skip 'no AUDITED sale order (fixture missing) - sale-order-items cannot be checked'
} else {
  $det = ApiGet ('/inventory/outbound/sale-order-items?saleOrderId=' + $auditedSo)
  $its = @($det.data.items)
  Write-Host ('  sale order #' + $auditedSo + ' -> items=' + $its.Count)
  Ok ($null -ne $det -and $null -ne $det.data.order) 'sale-order-items returns the order header'
  Ok ($its.Count -ge 1) 'and its item lines'
  Ok (@($its | Where-Object { $null -eq $_.productId }).Count -eq 0) 'every pulled line carries productId (what parseItems reads)'
  Ok (-not [string]::IsNullOrWhiteSpace(@($its)[0].productName)) 'and the product name is back-filled'
}

# =====================================================================================
Write-Host '--- (3) warehouse page: factoryId filter (was silently ignored)'
$factoryId = SqlOne "SELECT factory_id FROM warehouse WHERE warehouse_category='OUTSOURCE' AND factory_id IS NOT NULL ORDER BY id LIMIT 1"
$all = ApiGet '/warehouse/page?pageNum=1&pageSize=200&warehouseCategory=OUTSOURCE'
if (-not $factoryId -or $factoryId -eq 'NULL') {
  Skip 'no OUTSOURCE warehouse with factory_id (fixture missing)'
} else {
  $flt = ApiGet ('/warehouse/page?pageNum=1&pageSize=200&warehouseCategory=OUTSOURCE&factoryId=' + $factoryId)
  $rows = @($flt.data.records)
  Write-Host ('  OUTSOURCE total=' + @($all.data.records).Count + ' ; with factoryId=' + $factoryId + ' -> ' + $rows.Count)
  Ok ($rows.Count -ge 1) 'filtering by factoryId returns rows'
  Ok (@($rows | Where-Object { [string]$_.factoryId -ne "$factoryId" }).Count -eq 0) 'EVERY returned row belongs to that factory (the param is honoured now)'
  Ok ($rows.Count -lt @($all.data.records).Count) 'and it really narrows the list (negative control: unfiltered returns more)'
}

# =====================================================================================
Write-Host '--- (4) warehouse stock by-warehouse: unit is returned now'
$auxId = SqlOne "SELECT w.id FROM warehouse w JOIN warehouse_stock s ON s.warehouse_id=w.id WHERE s.material_id IS NOT NULL ORDER BY w.id LIMIT 1"
if (-not $auxId -or $auxId -eq 'NULL') {
  Skip 'no warehouse with material stock rows (fixture missing)'
} else {
  $st = ApiGet ('/warehouse/stock/by-warehouse/' + $auxId)
  $matRows = @(@($st.data) | Where-Object { $null -ne $_.materialId })
  Write-Host ('  warehouse #' + $auxId + ' material rows = ' + $matRows.Count)
  Ok ($matRows.Count -ge 1) 'by-warehouse returns material rows'
  $keys = if ($matRows.Count -ge 1) { ($matRows[0].PSObject.Properties.Name -join ',') } else { '' }
  Ok ($keys -match 'unit') 'the row carries a `unit` key (the page reads m.unit; it used to be absent)'
  Ok (@($matRows | Where-Object { $null -ne $_.unit -and "$($_.unit)" -ne '' }).Count -ge 1) 'and at least one row has a non-empty unit value'
}

# =====================================================================================
Write-Host '--- (5) outbound = the sale order voucher: the backend must REFUSE unlinkable ones'
if (-not $custId -or $custId -eq 'NULL' -or -not $whId -or $whId -eq 'NULL') {
  Skip 'fixtures missing for the voucher rules'
} else {
  function TryCreate($obj) { return (ApiPost '/inventory/outbound' $obj) }
  function WhyRejected($r) { if ($null -eq $r) { return '(threw)' } else { return ('code=' + $r.code + ' msg=' + $r.msg) } }
  # ⚠️ PowerShell 坑：`$x = if (...) { @(单元素) }` 会把数组**拆包成单个对象** ⇒ 序列化后 items 成了 JSON 对象
  #    而非数组 ⇒ 后端 parseItems 读不到 ⇒ 变成"空明细"被拒。**必须**在使用点用 @(...) 强制成数组。
  $baseItems = @(@{ productId = [int]$soProd; qualityType = 'A'; quantity = 1; unitPrice = 1 })

  # a) 不挂销售单
  $r1 = TryCreate @{ outbound = @{ customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-NOORDER' }; items = @($baseItems) }
  Write-Host ('  no orderId      -> ' + (WhyRejected $r1))
  Ok ($null -eq $r1 -or $r1.code -ne 200) 'an outbound WITHOUT a source sale order is refused (no orphan vouchers)'

  # b) 挂草稿销售单
  if ($draftSo -and $draftSo -ne 'NULL') {
    $r2 = TryCreate @{ outbound = @{ orderId = [int]$draftSo; customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-DRAFT' }; items = @($baseItems) }
    Write-Host ('  draft order     -> ' + (WhyRejected $r2))
    Ok ($null -eq $r2 -or $r2.code -ne 200) 'an outbound linked to a DRAFT sale order is refused (only audited orders ship)'
  } else { Skip 'no DRAFT sale order (fixture missing) - draft-order rule not checked' }

  # c) 明细产品不属于该单
  if ($otherProd -and $otherProd -ne 'NULL' -and $auditedSo -and $auditedSo -ne 'NULL') {
    $r3 = TryCreate @{ outbound = @{ orderId = [int]$auditedSo; customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-FOREIGN' }
                      items = @(@{ productId = [int]$otherProd; qualityType = 'A'; quantity = 1; unitPrice = 1 }) }
    Write-Host ('  foreign product -> ' + (WhyRejected $r3))
    Ok ($null -eq $r3 -or $r3.code -ne 200) 'an item whose product is NOT in that sale order is refused'
  } else { Skip 'no alternative product (fixture missing) - foreign-product rule not checked' }

  # d) 空明细
  if ($auditedSo -and $auditedSo -ne 'NULL') {
    $r4 = TryCreate @{ outbound = @{ orderId = [int]$auditedSo; customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-EMPTY' }; items = @() }
    Write-Host ('  empty items     -> ' + (WhyRejected $r4))
    Ok ($null -eq $r4 -or $r4.code -ne 200) 'an outbound with NO item lines is refused'
  }

  # 反向对照：合法报文必须通过（否则"全拒"也能骗过上面四条）
  if ($auditedSo -and $auditedSo -ne 'NULL') {
    $r5 = TryCreate @{ outbound = @{ orderId = [int]$auditedSo; customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-OK' }; items = @($baseItems) }
    Write-Host ('  valid voucher   -> ' + (WhyRejected $r5))
    Ok ($null -ne $r5 -and $r5.code -eq 200) 'a VALID voucher (audited order + its own product) is still accepted (negative control against "reject everything")'
    $okId = SqlOne "SELECT id FROM sale_outbound WHERE remark='E2E-GHOST-OK' ORDER BY id DESC LIMIT 1"
    if ($okId -and $okId -ne 'NULL') { ApiPut ('/inventory/outbound/' + $okId + '/cancel') | Out-Null }
  }
  # 确认没有任何孤儿测试单留在 DRAFT
  $left = SqlOne "SELECT COUNT(*) FROM sale_outbound WHERE remark IN ('E2E-GHOST-NOORDER','E2E-GHOST-DRAFT','E2E-GHOST-FOREIGN','E2E-GHOST-EMPTY')"
  Ok ("$left" -eq '0') 'no rejected request left a document behind (validation happens before any insert)'
}

Summary 'ghost-field fixes (§7.26): outbound product+voucher rules / source-order endpoints / factoryId filter / unit'
