# verify-material-order-reopen.ps1 (2026-09-27, user request "E 也要做"): 物料订单「反结单」能力。
#
# 背景：物料订单原先结单即**终态**（无 reopen），结错了只能新建单。本批新增
#   PUT /api/outsource/material-order/{id}/reopen  ->  FINISHED => RECEIVING / PENDING，并清空 finish_time。
#
# 断言：
#   A) 已结单 + 已收货（received>0）      => 回 RECEIVING 收货中，finish_time 清空（不是"只改状态"就算完）
#   B) 已结单 + 未收货但**曾审核**（auditor_id 非空）=> 仍回 RECEIVING  <-- 关键回归：
#      判据不能只看收货记录（unAudit 不清审核人；"审核了但一件没收到就结单"很常见），
#      否则会把这类单悄悄退回未审核，而详情页「审核人」还留着旧值（自相矛盾）。
#   C) 已结单 + 从未审核（auditor_id 为空，只有 PENDING->FINISHED 的 API 路径会产生）=> 回 PENDING 待审核
#   D) NEGATIVE：非"已结单"状态（RECEIVING / CANCELLED / 不存在）=> 拒绝，且状态不被改动
#   E) 重复调用：反结单成功后状态已不是 FINISHED => 再调必须拒绝（幂等保护，不重复动状态）
# 权限不在此断言：该端点在 /api/outsource/material-order 前缀下（页面码 outsource:material-order /
#   outsource:material-delivery，见 ApiPermGuard 的 RULES），与 /finish 同一道门，无新增权限面。
#
# Fixture 全部走 SQL 自建自清（code 前缀 MWO-VRO-），纯 ASCII，可重复跑。
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
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return @(($ls[1] -split "`t") | ForEach-Object { "$_".Trim() })
}
function SqlExec([string]$q) { SqlRaw $q | Out-Null }

$API = 'http://localhost:8080/api'
function ApiPost([string]$path, [string]$token, [string]$json) {
  $h = @{}
  if ($token) { $h['Authorization'] = $token }
  try {
    return Invoke-RestMethod -Uri ($API + $path) -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch {
    $r = $_.Exception.Response
    if ($r) { try { return ($r.GetResponseStream() | ForEach-Object { (New-Object IO.StreamReader($_)).ReadToEnd() } | ConvertFrom-Json) } catch { return $null } }
    return $null
  }
}
function ApiPut([string]$path, [string]$token) {
  try { return Invoke-RestMethod -Uri ($API + $path) -Method Put -Headers @{ Authorization = $token } } catch {
    $r = $_.Exception.Response
    if ($r) { try { return ($r.GetResponseStream() | ForEach-Object { (New-Object IO.StreamReader($_)).ReadToEnd() } | ConvertFrom-Json) } catch { return $null } }
    return $null
  }
}
function Login([string]$u, [string]$p) { return (ApiPost '/auth/login' '' ('{"username":"' + $u + '","password":"' + $p + '","companyId":1}')) }

$lg = Login 'lin' '123'
Ok ($lg.code -eq 200 -and $lg.data.token) 'login as admin'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-material-order-reopen (no token)'; exit 1 }
$tok = $lg.data.token

$supplierId = SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1'
$matId = SqlOne 'SELECT id FROM outsource_material ORDER BY id LIMIT 1'
$matTypeId = SqlOne 'SELECT id FROM material_type ORDER BY id LIMIT 1'
Ok ($supplierId -ne '' -and $matId -ne '' -and $matTypeId -ne '') ("fixture prerequisites (supplier=" + $supplierId + ", material=" + $matId + ", materialType=" + $matTypeId + ")")
if ($supplierId -eq '' -or $matId -eq '' -or $matTypeId -eq '') { Write-Host 'RESULT FAIL verify-material-order-reopen (missing master data)'; exit 1 }

$stamp = (Get-Date).ToString('HHmmss')
$madeCodes = @()
function NewOrder([string]$tag, [string]$status, [string]$finish, [string]$auditor, [int]$received) {
  $code = 'MWO-VRO-' + $stamp + '-' + $tag
  $script:madeCodes += $code
  # 结单人（2026-09-27）：已结单的单都盖章「verify」，用于断言反结单会清空结单人
  SqlExec ("INSERT INTO outsource_material_order (code, supplier_id, order_type, status, remark, company_id, finish_time, finisher_id, finisher_name, auditor_id, auditor_name, deleted) VALUES ('" + $code + "', " + $supplierId + ", 'PURCHASE', '" + $status + "', 'verify-material-order-reopen fixture', 1, " + $finish + ", 1, 'verify', " + $auditor + ", " + $(if ($auditor -eq 'NULL') { 'NULL' } else { "'verify'" }) + ", 0);")
  $oid = SqlOne ("SELECT id FROM outsource_material_order WHERE code='" + $code + "'")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, material_type_id, unit, order_quantity, received_quantity, company_id, deleted) VALUES (" + $oid + ", " + $matId + ", " + $matTypeId + ", 'PCS', 100, " + $received + ", 1, 0);")
  return $oid
}
function StateOf([string]$id) { return (SqlOne ("SELECT CONCAT(status, '/', IFNULL(finish_time,'NULL')) FROM outsource_material_order WHERE id=" + $id)) }

Step 'A) FINISHED with receipts -> reopen returns to RECEIVING and clears finish_time'
$idA = NewOrder 'A' 'FINISHED' 'NOW()' '1' 100
Ok ($idA -ne '') ('fixture A created (id=' + $idA + ', state=' + (StateOf $idA) + ')')
Ok ((StateOf $idA) -like 'FINISHED/*') 'A: fixture starts as FINISHED with a finish_time'
$rA = ApiPut ("/outsource/material-order/" + $idA + "/reopen") $tok
Ok ($rA.code -eq 200) ('A: reopen accepted (code=' + $rA.code + ')')
Ok ((StateOf $idA) -like 'RECEIVING/*') ('A: status back to RECEIVING (' + (StateOf $idA) + ')')
Ok ((SqlOne ("SELECT IFNULL(finish_time,'NULL') FROM outsource_material_order WHERE id=" + $idA)) -eq 'NULL') 'A: finish_time cleared (null is explicitly written, not silently skipped)'
Ok ((SqlOne ("SELECT IFNULL(finisher_name,'NULL') FROM outsource_material_order WHERE id=" + $idA)) -eq 'NULL') 'A: 结单人 also cleared on reopen (no "back to RECEIVING but still shows who closed it")'

Step 'B) FINISHED, audited but never received -> RECEIVING (must NOT silently un-audit)'
$idB = NewOrder 'B' 'FINISHED' 'NOW()' '1' 0
Ok ($idB -ne '') ('fixture B created (id=' + $idB + ', state=' + (StateOf $idB) + ')')
$rB = ApiPut ("/outsource/material-order/" + $idB + "/reopen") $tok
Ok ($rB.code -eq 200) ('B: reopen accepted (code=' + $rB.code + ')')
Ok ((StateOf $idB) -like 'RECEIVING/*') ('B: back to RECEIVING even with zero receipts (auditor was stamped) -- ' + (StateOf $idB))
Ok ((SqlOne ("SELECT IFNULL(auditor_name,'') FROM outsource_material_order WHERE id=" + $idB)) -ne '') 'B: auditor stamp kept (state and the shown 审核人 do not contradict)'

Step 'C) FINISHED without any audit (PENDING->FINISHED via API) -> PENDING'
$idC = NewOrder 'C' 'FINISHED' 'NOW()' 'NULL' 0
Ok ($idC -ne '') ('fixture C created (id=' + $idC + ', state=' + (StateOf $idC) + ')')
$rC = ApiPut ("/outsource/material-order/" + $idC + "/reopen") $tok
Ok ($rC.code -eq 200) ('C: reopen accepted (code=' + $rC.code + ')')
Ok ((StateOf $idC) -like 'PENDING/*') ('C: back to PENDING (never audited -> not invented as RECEIVING) -- ' + (StateOf $idC))

Step 'D) NEGATIVE: only FINISHED can be reopened'
$idD = NewOrder 'D' 'RECEIVING' 'NULL' '1' 0
$rD = ApiPut ("/outsource/material-order/" + $idD + "/reopen") $tok
Info ('D receiving -> code=' + $rD.code + ' msg=' + $rD.msg)
Ok ($rD.code -ne 200) 'D: reopen rejected for a RECEIVING order'
Ok ((StateOf $idD) -like 'RECEIVING/*') 'D: state untouched by the rejected call'
$idE = NewOrder 'E' 'CANCELLED' 'NULL' '1' 0
$rE = ApiPut ("/outsource/material-order/" + $idE + "/reopen") $tok
Info ('D cancelled -> code=' + $rE.code + ' msg=' + $rE.msg)
Ok ($rE.code -ne 200) 'D: reopen rejected for a CANCELLED order'
$rF = ApiPut '/outsource/material-order/999999999/reopen' $tok
Ok ($rF.code -ne 200) 'D: reopen rejected for a non-existent order'

Step 'E) idempotent-ish: a second reopen is refused (state is no longer FINISHED)'
$rG = ApiPut ("/outsource/material-order/" + $idA + "/reopen") $tok
Info ('E second reopen -> code=' + $rG.code + ' msg=' + $rG.msg)
Ok ($rG.code -ne 200) 'E: second reopen on the same order is refused'
Ok ((StateOf $idA) -like 'RECEIVING/*') 'E: state still RECEIVING after the refused repeat'

Step 'F) the reopened order is back in the receiving worklist (page filter sees it)'
$cnt = SqlOne ("SELECT COUNT(*) FROM outsource_material_order WHERE status='RECEIVING' AND id IN (" + $idA + "," + $idB + ")")
Ok ($cnt -eq '2') 'F: both reopened orders now count as RECEIVING (they reappear on the 收货中 tab)'

Step 'cleanup: fixture orders + items'
foreach ($c in $madeCodes) {
  $oid = SqlOne ("SELECT id FROM outsource_material_order WHERE code='" + $c + "'")
  if ($oid -ne '') { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=" + $oid + ";") }
}
SqlExec ("DELETE FROM outsource_material_order WHERE code LIKE 'MWO-VRO-" + $stamp + "-%';")
$left = SqlOne ("SELECT COUNT(*) FROM outsource_material_order WHERE code LIKE 'MWO-VRO-%'")
$leftI = SqlOne "SELECT COUNT(*) FROM outsource_material_order_item i WHERE NOT EXISTS (SELECT 1 FROM outsource_material_order o WHERE o.id=i.order_id)"
Ok ($left -eq '0') ('cleanup done (fixture orders left=' + $left + ', orphan items left=' + $leftI + ')')

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-material-order-reopen  (FAIL=' + $script:fail + ')')
if ($script:fail -gt 0) { exit 1 }
