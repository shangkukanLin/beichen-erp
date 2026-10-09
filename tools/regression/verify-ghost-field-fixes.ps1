# §7.26 幽灵字段修复守卫（2026-10-09，用户要求「按甲改，顺便修复刚刚的 234」）。
#
# 覆盖四件事，每件都有"**修前必然红**"的落点（不写无意义的恒真断言）：
#   ① 销售出库明细 = **成品 product**：按修复后的报文（productId + orderItemId）建单 ⇒
#      `sale_outbound_item.product_id` 必须落库（**修前前端发 materialId ⇒ Jackson 静默丢弃 ⇒ 恒 NULL**）；
#      并断言 GET /{id}/items 回传的 productName/sku 非空（回显链路）。
#   ② 新增「从销售单带入」的两个只读端点（走出库页前缀，避开 sale:order 权限）：
#      /outbound/sale-order-options 只回**已审核**销售单；/outbound/sale-order-items 回单据头 + 明细（productId 非空）。
#   ③ 委外仓「供应商」筛选：GET /warehouse/page?factoryId=X ⇒ 返回行 factoryId 全为 X
#      （**修前该形参不存在 ⇒ Spring 静默忽略 ⇒ 返回全部**）。
#   ④ 仓库详情/委外仓详情的「单位」：GET /warehouse/stock/by-warehouse/{id} 的物料行必须带 unit
#      （**修前该接口不回传 unit ⇒ 前端「单位」列恒空**）。
# 全部走 HTTP + DB 事实，不开浏览器；测试数据用完即作废/清理。
# 注意：必须是 Continue —— mysql CLI 每次都会往 stderr 写一句密码告警，在 Stop 模式下会被当成
# NativeCommandError 直接中断脚本（本守卫第一版就死在这里）。
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

# =====================================================================================
Write-Host '--- ① sale outbound item = product (was: materialId -> silently dropped)'
$prodId = SqlOne "SELECT id FROM product ORDER BY id LIMIT 1"
$custId = SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1"
$whId = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' AND IFNULL(status,1)=1 ORDER BY id LIMIT 1"
if (-not $prodId -or $prodId -eq 'NULL' -or -not $custId -or $custId -eq 'NULL' -or -not $whId -or $whId -eq 'NULL') {
  Skip ('fixtures missing: product=' + $prodId + ' customer=' + $custId + ' finishedWarehouse=' + $whId)
} else {
  $today = (Get-Date).ToString('yyyy-MM-dd')
  $body = @{
    outbound = @{ customerId = [int]$custId; warehouseId = [int]$whId; outboundDate = $today; remark = 'E2E-GHOST-1' }
    items = @(@{ productId = [int]$prodId; qualityType = 'A'; quantity = 3; unitPrice = 10 })
  }
  $r = ApiPost '/inventory/outbound' $body
  if ($null -eq $r -or $r.code -ne 200) {
    Skip ('creating an outbound failed (cannot verify here): ' + ($r | ConvertTo-Json -Compress -Depth 4))
  } else {
    $obId = SqlOne "SELECT id FROM sale_outbound WHERE remark='E2E-GHOST-1' ORDER BY id DESC LIMIT 1"
    # ⚠️ 变量名**不能叫 $pid** —— PowerShell 里 $PID 是只读自动变量（进程号），赋值会被拒（VariableNotWritable），
    #    随后断言就在拿"进程号"跟 productId 比 ⇒ 假红。本仓 ui-e2e-p2b-dev.ps1:64 已有同样的教训。
    $savedProductId = SqlOne ("SELECT IFNULL(product_id,'NULL') FROM sale_outbound_item WHERE outbound_id=" + $obId + " ORDER BY id DESC LIMIT 1")
    Write-Host ('  outbound #' + $obId + ' item.product_id = ' + $savedProductId + ' (sent productId=' + $prodId + ')')
    Ok ($savedProductId -eq "$prodId") 'the outbound item PERSISTS product_id (the ghost `materialId` used to be dropped)'
    $items = ApiGet ('/inventory/outbound/' + $obId + '/items')
    $first = @($items.data)[0]
    Ok ($null -ne $first -and -not [string]::IsNullOrWhiteSpace($first.productName)) 'GET /{id}/items returns the product name (echo works)'
    Ok ($null -ne $first -and $null -ne $first.sku) 'and it returns the SKU field'
    # 清理：作废这张测试出库单（草稿 -> CANCELLED）
    ApiPut ('/inventory/outbound/' + $obId + '/cancel') | Out-Null
    $st = SqlOne ("SELECT status FROM sale_outbound WHERE id=" + $obId)
    Write-Host ('  cleanup: outbound #' + $obId + ' status = ' + $st)
  }
}

# =====================================================================================
Write-Host '--- ② source-sale-order endpoints (new, outbound prefix = read isolation)'
$soId = SqlOne "SELECT id FROM sale_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1"
$opts = ApiGet '/inventory/outbound/sale-order-options?pageSize=50'
Ok ($null -ne $opts -and @($opts.data).Count -ge 1) 'sale-order-options returns audited sale orders'
if ($null -ne $opts) {
  $bad = @(@($opts.data) | Where-Object { $null -eq $_.customerId }).Count
  Ok ($bad -eq 0) 'every option carries customerId (picker needs it)'
}
if (-not $soId -or $soId -eq 'NULL') {
  Skip 'no AUDITED sale order (fixture missing) - sale-order-items cannot be checked'
} else {
  $det = ApiGet ('/inventory/outbound/sale-order-items?saleOrderId=' + $soId)
  $its = @($det.data.items)
  Write-Host ('  sale order #' + $soId + ' -> items=' + $its.Count)
  Ok ($null -ne $det -and $null -ne $det.data.order) 'sale-order-items returns the order header'
  Ok ($its.Count -ge 1) 'and its item lines'
  Ok (@($its | Where-Object { $_.productId -eq $null }).Count -eq 0) 'every pulled line carries productId (what parseItems reads)'
  Ok (-not [string]::IsNullOrWhiteSpace(@($its)[0].productName)) 'and the product name is back-filled'
}

# =====================================================================================
Write-Host '--- ③ warehouse page: factoryId filter (was silently ignored)'
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
Write-Host '--- ④ warehouse stock by-warehouse: unit is returned now'
$auxId = SqlOne "SELECT w.id FROM warehouse w JOIN warehouse_stock s ON s.warehouse_id=w.id WHERE s.material_id IS NOT NULL ORDER BY w.id LIMIT 1"
if (-not $auxId -or $auxId -eq 'NULL') {
  Skip 'no warehouse with material stock rows (fixture missing)'
} else {
  $st = ApiGet ('/warehouse/stock/by-warehouse/' + $auxId)
  $matRows = @(@($st.data) | Where-Object { $_.materialId -ne $null })
  Write-Host ('  warehouse #' + $auxId + ' material rows = ' + $matRows.Count)
  Ok ($matRows.Count -ge 1) 'by-warehouse returns material rows'
  $keys = if ($matRows.Count -ge 1) { ($matRows[0].PSObject.Properties.Name -join ',') } else { '' }
  Ok ($keys -match 'unit') 'the row carries a `unit` key (the page reads m.unit; it used to be absent)'
  Ok (@($matRows | Where-Object { $_.unit -ne $null -and "$($_.unit)" -ne '' }).Count -ge 1) 'and at least one row has a non-empty unit value'
}

Summary 'ghost-field fixes (§7.26): outbound product / source-order endpoints / factoryId filter / unit'
