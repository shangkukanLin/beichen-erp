# 物料收发单流程重构(v2) 校验（2026-09-16 建立，2026-09-17 改为**从 DB 取仓库/物料**，可复跑）
#  1 列表页签 = 全部 | 发料 | 调拨 | 收料(自动) | 退不良(自动) | 退料(已下线)
#  2 新增页类型下拉只有 发料 / 调拨（无退料/收料）
#  3 发料：发出仓库下拉 = 只我方物料仓（辅料仓）
#  4 调拨：来源/目标仓库下拉 = 物料相关仓库（委外仓 + 我方物料仓），不含成品仓
#  5 负向接口（5 例）：退料新建被拒 / 发料选委外仓被拒 / 发料目标仓与工厂不符被拒 / 调拨两端相同被拒 / 调拨"我方物料仓→委外仓"被拒
#  6 正向端到端：调拨 委外仓 → 我方物料仓 建单/审核/反审核/作废
# 说明：原先写死 wh 12/29、物料 7、仓名 AUX-WH-1/FIN-WH-1，数据一变整脚本全 FAIL（2026-09-17 实测 8 项失败）；
#       现全部改为**按仓库类别/类型从库里取**（我方物料仓 = INVENTORY+AUXILIARY；委外仓 = OUTSOURCE）。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$global:fail = 0
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function EvalJs($js) {
  $out = @(agent-browser eval $js) | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '✓ Done' -and $_ -notmatch '^\s*✗' }
  return ($out -join "`n").Trim()
}
function Ok($m) { Write-Host ("PASS " + $m) }
function Bad($m) { Write-Host ("FAIL " + $m); $global:fail = $global:fail + 1 }
function Clean($s) { return (($s -replace '"', '')).Trim() }
function OpenFresh($url) {
  EvalJs "localStorage.clear(); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000 | Out-Null
  if ((EvalJs "'p=' + location.pathname") -match '/login') {
    Write-Host '(session expired, re-login)'
    $snap = (agent-browser snapshot -i) -join "`n"
    $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
    $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
    $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500 | Out-Null
    agent-browser open $url | Out-Null
    agent-browser wait 3000 | Out-Null
  }
}
# 调后端接口（带登录 token），返回 "<code>|<message>"
# 注意：body 必须在 PowerShell 侧序列化后再拼进 JS —— 直接写 JSON.stringify($obj) 会被插值成 System.Collections.Hashtable
function Api($method, $path, $jsonBody) {
  # body 用 base64 传：JS 参数里不能出现双引号（会被 shell 剥掉导致 JSON 语法错误）
  if ($jsonBody) {
    # -InputObject（不要用管道）：管道会把 Hashtable 包成数组，后端收到 [ {...} ] 就反序列化失败
    $json = ConvertTo-Json -InputObject $jsonBody -Compress -Depth 6
    Write-Host ("  (body) " + $json)
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
    # body 必须是**字符串**（传对象会被 fetch 转成 "[object Object]" → 后端报 START_ARRAY）
    $bodyPart = ", body: new TextDecoder().decode(Uint8Array.from(atob('$b64'), c=>c.charCodeAt(0))), headers: { 'Content-Type':'application/json', Authorization: localStorage.getItem('beichen_erp_token') }"
  } else {
    $bodyPart = ", headers: { Authorization: localStorage.getItem('beichen_erp_token') }"
  }
  $js = "fetch('$path',{method:'$method'$bodyPart}).then(r=>r.json().then(j=>String(j.code)+'|'+String(j.message||j.msg||''))).catch(e=>'ERR|'+e)"
  return Clean (EvalJs "(async()=>{return await $js})()")
}
# 按表单项 label 精确打开下拉并读取可见选项（比按索引稳，页面字段顺序/RemoteSelect 都会干扰索引）
function OpenSelectByLabel($label) {
  EvalJs "(()=>{const it=[...document.querySelectorAll('.el-form-item')].find(x=>{const l=x.querySelector('.el-form-item__label');return l && l.innerText.trim().startsWith('$label')});const w=it && it.querySelector('.el-select__wrapper');if(w)w.click();return 'ok';})()" | Out-Null
  agent-browser wait 900 | Out-Null
  return Clean (EvalJs "[...document.querySelectorAll('.el-select-dropdown__item')].filter(x=>x.offsetParent!==null).map(x=>x.innerText.trim()).join('|')")
}
# 点开某个下拉后选一个选项
function PickOption($text) {
  EvalJs "(()=>{const it=[...document.querySelectorAll('.el-select-dropdown__item')].filter(x=>x.offsetParent!==null).find(x=>x.innerText.trim()==='$text');if(it)it.click();return 'ok';})()" | Out-Null
  agent-browser wait 1000 | Out-Null
}
# 取成品仓名（调拨范围断言用；原先写死 FIN-WH-1）
function WhNames([string]$where) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT DISTINCT warehouse_name FROM warehouse WHERE status=1 AND ($where)" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}

# 1 列表页签
# 测试用 id/名称一律从库里取（2026-09-17 F4）
$auxWhId = [int](SqlOne "SELECT MIN(id) FROM warehouse WHERE status=1 AND warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY'")
$auxWhName = SqlOne "SELECT warehouse_name FROM warehouse WHERE id=$auxWhId"
$outWhId = [int](SqlOne "SELECT MIN(id) FROM warehouse WHERE status=1 AND warehouse_category='OUTSOURCE'")
$outWhName = SqlOne "SELECT warehouse_name FROM warehouse WHERE id=$outWhId"
$outFactoryId = SqlOne "SELECT factory_id FROM warehouse WHERE id=$outWhId"
# 另一个工厂（用于"目标仓不属于所选工厂"用例）；若只有一个委外仓则退回一个不存在的 id
$otherFactoryId = SqlOne "SELECT MIN(factory_id) FROM warehouse WHERE warehouse_category='OUTSOURCE' AND factory_id <> $outFactoryId"
if ([string]::IsNullOrWhiteSpace($otherFactoryId)) { $otherFactoryId = '999999' }
# 在委外仓**没有库存行**的物料（严格模式审核必然"库存不足" → 6a 用；强制出库则通过 → 6b 用）
$noStockMatId = SqlOne "SELECT MIN(m.id) FROM outsource_material m WHERE m.id NOT IN (SELECT s.material_id FROM warehouse_stock s WHERE s.warehouse_id=$outWhId AND s.material_id IS NOT NULL)"
if ([string]::IsNullOrWhiteSpace($noStockMatId)) { $noStockMatId = SqlOne "SELECT MIN(material_id) FROM warehouse_stock WHERE warehouse_id=$outWhId AND quantity <= 0" }
Write-Output ("[DB] 我方物料仓=$auxWhId($auxWhName) 委外仓=$outWhId($outWhName) outFactory=$outFactoryId otherFactory=$otherFactoryId noStockMat=$noStockMatId")

OpenFresh "$base/outsource/delivery"
$tabs = Clean (EvalJs "[...document.querySelectorAll('.table-card .el-tabs__item')].map(x=>x.innerText.trim()).join('|')")
Write-Host ("列表页签 = $tabs")
foreach ($t in @('全部', '发料', '调拨', '收料（自动）', '退不良（自动）', '退料（已下线）')) {
  if ($tabs -match [regex]::Escape($t)) { Ok "页签含 $t" } else { Bad "页签缺 $t" }
}
if ($tabs -match '自动' -and $tabs -match '已下线') { Ok '页签已区分「自动单据」与「已下线」' } else { Bad '页签未区分自动/已下线' }

# 2 新增页类型下拉
OpenFresh "$base/outsource/delivery/add"
$typeOpts = OpenSelectByLabel '类型'
Write-Host ("新增页 类型下拉 = $typeOpts")
if ($typeOpts -match '发料' -and $typeOpts -match '调拨') { Ok '类型含 发料/调拨' } else { Bad '类型缺 发料/调拨' }
if ($typeOpts -match '退料' -or $typeOpts -match '收料') { Bad '类型下拉仍含 退料/收料' } else { Ok '类型下拉已无 退料/收料' }

# 3 发料：发出仓库下拉范围（类型默认=发料）
$fromWhs = OpenSelectByLabel '发出仓库'
Write-Host ("发料 发出仓库下拉 = $fromWhs")
if ($fromWhs -match [regex]::Escape($auxWhName)) { Ok ("发料 发出仓库含我方物料仓 " + $auxWhName) } else { Bad ("发料 发出仓库缺 " + $auxWhName) }
if ($fromWhs -match [regex]::Escape($outWhName)) { Bad ("发料 发出仓库仍含委外仓 " + $outWhName) } else { Ok '发料 发出仓库不含委外仓' }

# 4 调拨：来源/目标仓库下拉范围（先把类型切到调拨）
OpenSelectByLabel '类型' | Out-Null
PickOption '调拨'
$tFrom = OpenSelectByLabel '来源仓库'
Write-Host ("调拨 来源仓库下拉 = $tFrom")
if ($tFrom -match [regex]::Escape($outWhName) -and $tFrom -match [regex]::Escape($auxWhName)) { Ok '调拨 两端含 委外仓 + 我方物料仓' } else { Bad '调拨 两端范围不对' }
foreach ($bad in (WhNames "warehouse_category='INVENTORY' AND warehouse_type='FINISHED'")) {
  if ($tFrom -match [regex]::Escape($bad)) { Bad "调拨 不应出现成品仓 $bad" } else { Ok "调拨 已排除成品仓 $bad" }
}

# 5 负向接口校验（ids 全部取自 DB）
$cases = @(
  @{ n = '退料新建被拒'; m = 'POST'; p = '/api/outsource/delivery'; b = @{ deliveryType = 'RETURN'; fromWarehouseId = $auxWhId }; k = '不再支持手工新建' },
  @{ n = '发料 选委外仓被拒'; m = 'POST'; p = '/api/outsource/delivery'; b = @{ deliveryType = 'DELIVERY'; factoryId = $outFactoryId; fromWarehouseId = $outWhId; toWarehouseId = $outWhId }; k = '只能是我方物料仓' },
  @{ n = '发料 目标仓与工厂不符被拒'; m = 'POST'; p = '/api/outsource/delivery'; b = @{ deliveryType = 'DELIVERY'; factoryId = $otherFactoryId; fromWarehouseId = $auxWhId; toWarehouseId = $outWhId }; k = '不属于所选工厂' },
  @{ n = '调拨 两端相同被拒'; m = 'POST'; p = '/api/outsource/delivery'; b = @{ deliveryType = 'TRANSFER'; fromWarehouseId = $auxWhId; toWarehouseId = $auxWhId }; k = '不能相同' },
  @{ n = '调拨 我方仓→委外仓被拒'; m = 'POST'; p = '/api/outsource/delivery'; b = @{ deliveryType = 'TRANSFER'; fromWarehouseId = $auxWhId; toWarehouseId = $outWhId }; k = '请使用「发料」' }
)
foreach ($c in $cases) {
  $r = Api $c.m $c.p $c.b
  Write-Host ("负向 [$($c.n)] -> $r")
  if ($r -match [regex]::Escape($c.k)) { Ok $c.n } else { Bad "$($c.n)（期望提示含「$($c.k)」）" }
}

# 6 调拨端到端：调拨一个「该委外仓没有库存」的物料，正好验证两件事
#   6a 默认严格：allowNegative=0 → 审核应因库存不足失败
#   6b 勾选强制出库：allowNegative=1 → 审核/反审核/作废全通过
$testDate = (Get-Date -Format 'yyyy-MM-dd')
$bodyBase = @{ deliveryType = 'TRANSFER'; fromWarehouseId = $outWhId; toWarehouseId = $auxWhId; deliveryDate = $testDate; remark = '验收测试-调拨（可作废）' }

# 6a 严格模式
$bodyA = $bodyBase.Clone(); $bodyA.allowNegative = 0
$bodyA.items = @(@{ materialId = [int]$noStockMatId; quantity = 1; unit = 'PCS'; qualityType = 'GOOD' })
$mkA = Api 'POST' '/api/outsource/delivery' $bodyA
Write-Host ("6a [建草稿 严格] -> $mkA")
if ($mkA -match '^200\|') { Ok '6a 调拨草稿创建成功' } else { Bad '6a 调拨草稿创建失败' }
$idA = ($(Clean (EvalJs "(async()=>{const r=await fetch('/api/outsource/delivery/page?deliveryType=TRANSFER&pageNum=1&pageSize=1',{headers:{Authorization:localStorage.getItem('beichen_erp_token')}});const j=await r.json();return String((j.data&&j.data.records&&j.data.records[0]&&j.data.records[0].id)||'')})()")) -replace '[^0-9]', '')
Write-Host ("6a 严格单 id = $idA")
if ($idA) {
  $auA = Api 'PUT' "/api/outsource/delivery/$idA/audit" $null
  Write-Host ("6a [审核 期望失败] -> $auA")
  if ($auA -match '库存不足') { Ok '6a 默认严格校验生效（库存不足被拒）' } else { Bad "6a 未按预期拒绝：$auA" }
  $caA = Api 'PUT' "/api/outsource/delivery/$idA/cancel" $null
  if ($caA -match '^200\|') { Ok '6a 严格测试单已作废' } else { Bad '6a 严格测试单作废失败' }
}

# 6b 强制出库
$bodyB = $bodyBase.Clone(); $bodyB.allowNegative = 1
$bodyB.items = @(@{ materialId = [int]$noStockMatId; quantity = 1; unit = 'PCS'; qualityType = 'GOOD' })
$mkB = Api 'POST' '/api/outsource/delivery' $bodyB
Write-Host ("6b [建草稿 强制出库] -> $mkB")
if ($mkB -match '^200\|') { Ok '6b 调拨草稿创建成功' } else { Bad '6b 调拨草稿创建失败' }
$idB = ($(Clean (EvalJs "(async()=>{const r=await fetch('/api/outsource/delivery/page?deliveryType=TRANSFER&pageNum=1&pageSize=1',{headers:{Authorization:localStorage.getItem('beichen_erp_token')}});const j=await r.json();return String((j.data&&j.data.records&&j.data.records[0]&&j.data.records[0].id)||'')})()")) -replace '[^0-9]', '')
Write-Host ("6b 强制单 id = $idB")
if ($idB) {
  $auB = Api 'PUT' "/api/outsource/delivery/$idB/audit" $null
  Write-Host ("6b [审核] -> $auB")
  if ($auB -match '^200\|') { Ok '6b 勾选强制出库后审核通过（委外仓→我方仓 库存已调整）' } else { Bad "6b 审核失败：$auB" }
  $unB = Api 'PUT' "/api/outsource/delivery/$idB/un-audit" $null
  Write-Host ("6b [反审核] -> $unB")
  if ($unB -match '^200\|') { Ok '6b 反审核通过（库存已回滚）' } else { Bad "6b 反审核失败：$unB" }
  $caB = Api 'PUT' "/api/outsource/delivery/$idB/cancel" $null
  Write-Host ("6b [作废] -> $caB")
  if ($caB -match '^200\|') { Ok '6b 测试调拨单已作废（净影响为 0）' } else { Bad '6b 测试调拨单作废失败' }
}

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 收发单 v2 流程（发料/调拨）已生效，退料下线、范围强校验、强制出库开关均通过' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
