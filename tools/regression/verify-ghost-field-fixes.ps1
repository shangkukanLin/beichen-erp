# 仓库侧字段回归守卫（原 §7.26「幽灵字段修复」守卫，2026-10-09 随销售出库模块下线**裁剪**为两段）。
#
# 为什么裁剪：原五段里 ①（出库明细 productId/orderItemId 落库）、②（出库页的"来源销售单"只读端点）、
#  ⑤（出库=销售单凭证的后端硬约束）**全部属于已整体下线的销售出库模块** ⇒ 随之移除（见报告 §7.28）。
#  留下的 ③④ 与出库无关、是**仓库侧**的回归，删模块**不能**连带丢掉它们：
#   ③ 委外仓「供应商」筛选：GET /warehouse/page?factoryId=X ⇒ 返回行 factoryId 全为 X
#      （**修前该形参不存在 ⇒ Spring 静默忽略 ⇒ 返回全部**）。
#   ④ 仓库详情/委外仓详情的「单位」：GET /warehouse/stock/by-warehouse/{id} 的物料行必须带 unit
#      （**修前该接口不回传 unit ⇒ 前端「单位」列恒空**）。
# 全部走 HTTP + DB 事实，不开浏览器；**本脚本只读**（不建单、不改库）。
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
} catch { Write-Host ('FAIL login: ' + $_.Exception.Message); Summary 'warehouse field fixes (§7.26 kept ③④)'; exit 1 }
$H = @{ Authorization = $lr.data.token }
function ApiGet([string]$p) { try { return Invoke-RestMethod -Uri ($API + $p) -Headers $H -TimeoutSec 30 } catch { return $null } }

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

Summary 'warehouse field fixes (factoryId filter / by-warehouse unit; kept from §7.26 after §7.28)'
