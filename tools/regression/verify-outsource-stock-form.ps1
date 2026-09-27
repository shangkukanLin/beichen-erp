# verify-outsource-stock-form.ps1 (2026-09-28): 「库存读查询必须带 stock_form」的定向守卫。
#
# 背景（当天实锤，用户侧现象＝"成品收货保存没反应"）：
#   warehouse_stock 的**唯一键含 stock_form**
#   （uk_wh_material_company = 仓 + 物料 + 形态 + 公司；uk_wh_prod_quality_company 同理含形态，
#    2026-09-25 P0-2 已定调"形态是定位键的一部分"）。
#   而 OutsourceOrderController.materialStock（成品收货弹窗的缺料检查，GET /outsource/order/{id}/material-stock）
#   读库存时**漏了形态** ⇒ 同一物料在该委外仓既有正常账(MATERIAL)又在厂维修账(MATERIAL_REPAIR)时，
#   selectOne 查到 2 行 ⇒ TooManyResultsException ⇒ 该接口 500 ⇒ 前端报
#   "系统异常: Expected one result (or null) to be returned by selectOne(), but found: 2"、草稿落不了库
#   ⇒ **该加工厂的成品收货彻底不可用**（ui-e2e-p4c 的 delivery draft row not found 真因）。
#
# 断言：
#   A) 目标单的缺料检查在**只有正常账**时 200，且该物料 stockQuantity == 正常账数量；
#   B) 人为造一行同仓同物料同品质的 MATERIAL_REPAIR(数量 0) 后，接口仍 **200**（修复前必 500）；
#   C) 此时 stockQuantity 仍等于**正常账**数量（证明读的是正常账，而不是恰好抓到维修账那行 0）；
#   D) 清理自建 fixture 行。
# Fixture 自建自清；纯 ASCII。
$ErrorActionPreference = 'Continue'
$script:fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlOne([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return '' }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function SqlExec([string]$q) { SqlRaw $q | Out-Null }

$API = 'http://localhost:8080/api'
function ApiGet([string]$path, [string]$token) {
  try { return Invoke-RestMethod -Uri ($API + $path) -Headers @{ Authorization = $token } } catch {
    $r = $_.Exception.Response
    if ($r) { try { return ($r.GetResponseStream() | ForEach-Object { (New-Object IO.StreamReader($_)).ReadToEnd() } | ConvertFrom-Json) } catch { return $null } }
    return $null
  }
}
function ApiPost([string]$path, [string]$token, [string]$json) {
  try {
    return Invoke-RestMethod -Uri ($API + $path) -Method Post -Headers @{ Authorization = $token } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch { return $null }
}
$lg = ApiPost '/auth/login' '' '{"username":"lin","password":"123","companyId":1}'
Ok ($lg.code -eq 200 -and $lg.data.token) 'login as admin'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-outsource-stock-form (no token)'; exit 1 }
$tok = $lg.data.token

# 找一个 PRODUCING 且"BOM 物料在该厂委外仓有正常账库存"的加工单
$orderId = ''
$whId = ''
$matId = ''
$cand = @((SqlRaw "SELECT o.id FROM outsource_order o WHERE o.status='PRODUCING' ORDER BY o.id DESC LIMIT 20") -split "`n" |
  Select-Object -Skip 1 | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
foreach ($o in $cand) {
  $w = SqlOne ("SELECT w.id FROM warehouse w JOIN outsource_order o ON o.factory_id=w.factory_id WHERE o.id=" + $o + " AND w.warehouse_category='OUTSOURCE' ORDER BY w.id LIMIT 1")
  if ($w -eq '') { continue }
  # 注意 outsource_order_material 的物料列叫 outsource_material_id（实体里映射成 materialId）
  $m = SqlOne ("SELECT ws.material_id FROM warehouse_stock ws JOIN outsource_order_material om ON om.outsource_material_id=ws.material_id JOIN outsource_order_product op ON op.id=om.product_id WHERE op.order_id=" + $o + " AND ws.warehouse_id=" + $w + " AND ws.stock_form='MATERIAL' AND ws.quantity > 0 ORDER BY ws.material_id LIMIT 1")
  if ($m -ne '') { $orderId = $o; $whId = $w; $matId = $m; break }
}
Ok ($orderId -ne '' -and $whId -ne '' -and $matId -ne '') ("fixture resolved (order=" + $orderId + ", wh=" + $whId + ", material=" + $matId + ")")
if ($orderId -eq '') { Write-Host 'RESULT FAIL verify-outsource-stock-form (no suitable order/material fixture)'; exit 1 }

$goodQty = SqlOne ("SELECT quantity FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND material_id=" + $matId + " AND stock_form='MATERIAL' AND quality_type='GOOD'")
Info ('normal (MATERIAL) stock row quantity = ' + $goodQty)

Step 'A) without a second stock_form row the shortage check answers 200 and reads the normal book'
$r1 = ApiGet ("/outsource/order/" + $orderId + "/material-stock") $tok
Ok ($r1.code -eq 200) ('A: material-stock answers 200 (code=' + $r1.code + ' msg=' + $r1.msg + ')')
$row1 = @($r1.data.materials | Where-Object { [string]$_.materialId -eq $matId })
Ok ($row1.Count -eq 1) ('A: the BOM material appears in the response (' + $row1.Count + ' row)')
if ($row1.Count -eq 1) {
  Ok ([decimal]$row1[0].stockQuantity -eq [decimal]$goodQty) ('A: stockQuantity == normal book (' + $row1[0].stockQuantity + ' == ' + $goodQty + ')')
}

Step 'B) inject a MATERIAL_REPAIR row for the same (warehouse, material, GOOD) -> must still answer 200'
$repairExists = SqlOne ("SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND material_id=" + $matId + " AND stock_form='MATERIAL_REPAIR' AND quality_type='GOOD'")
$injected = $false
if ([int]$repairExists -eq 0) {
  SqlExec ("INSERT INTO warehouse_stock (warehouse_id, material_id, quality_type, stock_form, company_id, quantity) VALUES (" + $whId + ", " + $matId + ", 'GOOD', 'MATERIAL_REPAIR', 1, 0);")
  $injected = $true
}
$twoRows = SqlOne ("SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND material_id=" + $matId + " AND quality_type='GOOD'")
Ok ([int]$twoRows -ge 2) ('B: the same (warehouse, material, GOOD) now has ' + $twoRows + ' stock_form rows (the 500 trigger)')
$r2 = ApiGet ("/outsource/order/" + $orderId + "/material-stock") $tok
Ok ($r2.code -eq 200) ('B: material-stock still answers 200 with 2 stock_form rows (code=' + $r2.code + ' msg=' + $r2.msg + ')')

Step 'C) the value still comes from the normal book (not from the 0-qty repair row)'
$row2 = @($r2.data.materials | Where-Object { [string]$_.materialId -eq $matId })
Ok ($row2.Count -eq 1) 'C: the BOM material still appears in the response'
if ($row2.Count -eq 1) {
  Ok ([decimal]$row2[0].stockQuantity -eq [decimal]$goodQty) ('C: stockQuantity still == normal book (' + $row2[0].stockQuantity + ' == ' + $goodQty + ')')
}

Step 'D) cleanup: drop the injected fixture row'
if ($injected) {
  SqlExec ("DELETE FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND material_id=" + $matId + " AND stock_form='MATERIAL_REPAIR' AND quality_type='GOOD';")
}
$repairLeft = [int](SqlOne ("SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND material_id=" + $matId + " AND stock_form='MATERIAL_REPAIR'"))
if ($injected) {
  Ok ($repairLeft -eq 0) ('D: injected fixture row removed (MATERIAL_REPAIR rows left=' + $repairLeft + ')')
} else {
  Ok ($repairLeft -ge 1) ('D: no inspection needed -- a MATERIAL_REPAIR row already existed (left=' + $repairLeft + ')')
}

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-outsource-stock-form  (FAIL=' + $script:fail + ')')
if ($script:fail -gt 0) { exit 1 }
