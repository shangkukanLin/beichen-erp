# Guard (2026-09-29, user rule): 「物料收退」= 收货 + 物料退货 + 结单/反结单（原名「物料收货」）
#   用户口径：
#     ① 委外加工子菜单「物料收货」**改名「物料收退」**（只改文案：id 415 / path / perms / 组件全不变）；
#     ② 「物料收退详情」（/outsource/material-order/delivery/:id）工具栏要有**「物料退货」与「结单」**；
#     ③ 结单 / 反结单**统一收到本页** ⇒ 「物料订单详情」不再有这两个按钮、收货列表「已结单」页签行内也不再「反结单」。
#   工具栏形态（与后端状态机 + material-order/delivery.vue 的 can* 同口径）：
#     生产中（不论有无已收量）-> 新增收货 · **物料退货** · 结单
#     已结单                  -> 反结单 + **物料退货**（无 新增收货/结单）
#   ⚠️ 2026-09-29 追加口径「**物料退货没必要隐藏**」：入口**只按订单状态**给（= 后端 returnMaterial 的第一道
#     校验），不再要求"某明细已收 > 0" —— 那条闸门害得**刚建、还没收过货**的单（实测 MWO-20260929002
#     生产中、已收 0）整页看不到入口。可退量由弹窗（逐行 可退 = 已收量，可为 0）与后端（可退 ≤ 已收量）判定。
#   「物料退货」= **本页唯一**退货入口（工具栏）—— 落 POST /outsource/material-order/{id}/return，按订单明细
#     预填（可退 = 该明细已收量）。2026-09-29 最终口径：收货记录行内的「新增退货」**已去掉**（它只是把可退
#     上限缩到该条记录，而后端 returnMaterial 不落来源收货单 ⇒ 没有追溯价值）；带真实收货记录的行级强断言
#     在 ui-e2e-11 的 S2（本脚本的夹具订单没有收货记录，只能做页面级"已无该按钮"的弱断言）。
#   自建夹具（SQL）+ 自清理，可重复运行。中文直写（本脚本带 UTF-8 BOM，与 verify-* 同范式）。
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$base = 'http://localhost:5173'
$global:fail = 0
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $global:fail = $global:fail + 1 }
# 菜单缓存要清（页面标题/菜单名都吃它）—— 与 verify-delivery-menu 同款做法
function OpenFresh($url) {
  EvalJs "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs "'p=' + location.pathname") -match '/login') {
    Write-Output '（会话失效，先登录）'
    $snap = (agent-browser snapshot -i) -join "`n"
    $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
    $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
    $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500
    agent-browser open $url | Out-Null
    agent-browser wait 3000
  }
}
function SqlOne([string]$q) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' } | Select-Object -First 1)
}
function SqlExec([string]$q) {
  & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null
}
function ReadJson($js, $want) {
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("未读到 " + $want + "：" + $raw); return $null }
  return ($m.Value | ConvertFrom-Json)
}
# 详情页工具栏形态：一次读全（含跳转按钮文案），避免多次导航
function Buttons([string]$url, [string]$want) {
  OpenFresh $url
  return (ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({recv:b.includes('新增收货'),ret:b.includes('物料退货'),fin:b.includes('结单'),reopen:b.includes('反结单'),jump:b.includes('物料收退'),newRet:b.includes('新增退货')});})()" $want)
}
function Confirm() {
  EvalJs "(()=>{const bs=[...document.querySelectorAll('.el-message-box__btns button')];const t=bs.find(x=>(x.innerText||'').indexOf('确定')>=0);if(!t)return 'noconfirm';t.click();return 'confirmed'})()" | Out-Null
  Start-Sleep -Milliseconds 2600
}

# ================= 夹具（三张单：A 生产中已收 / B 生产中未收 / C 已结单已收） =================
$ts = (Get-Date).ToString('HHmmss')
$sup = SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1'
$mat = SqlOne 'SELECT id FROM outsource_material ORDER BY id LIMIT 1'
$mt = SqlOne 'SELECT id FROM material_type ORDER BY id LIMIT 1'
if ($sup -eq '' -or $mat -eq '' -or $mt -eq '') { Write-Output 'RESULT SKIP verify-material-receive-return（缺主数据 supplier / outsource_material / material_type）'; exit 0 }
function NewOrder([string]$tag, [string]$status, [int]$received) {
  $code = 'MWO-MRR-' + $tag + '-' + $ts
  SqlExec ("INSERT INTO outsource_material_order (code, supplier_id, order_type, status, remark, company_id, deleted) VALUES ('" + $code + "', " + $sup + ", 'PURCHASE', '" + $status + "', 'verify-material-receive-return fixture', 1, 0);")
  $oid = SqlOne ("SELECT id FROM outsource_material_order WHERE code='" + $code + "'")
  if ($oid -ne '') {
    SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, material_type_id, unit, order_quantity, received_quantity, company_id, deleted) VALUES (" + $oid + ", " + $mat + ", " + $mt + ", 'PCS', 100, " + $received + ", 1, 0);")
  }
  return $oid
}
$oidA = NewOrder 'A' 'RECEIVING' 20
$oidB = NewOrder 'B' 'RECEIVING' 0
$oidC = NewOrder 'C' 'FINISHED' 20
Write-Output ('fixture: A(生产中,已收20)=' + $oidA + '  B(生产中,未收)=' + $oidB + '  C(已结单,已收20)=' + $oidC)
if ($oidA -eq '' -or $oidB -eq '' -or $oidC -eq '') { Write-Output 'RESULT FAIL verify-material-receive-return（夹具建不出来）'; exit 1 }

# ① 生产中 + 已收>0：新增收货 · 物料退货 · 结单（无 反结单）
$bA = Buttons ("$base/outsource/material-order/delivery/" + $oidA) 'A 收退详情（生产中）'
if ($bA) {
  if ($bA.recv) { Ok 'A 生产中（已收 20）：有「新增收货」' } else { Bad 'A 缺「新增收货」' }
  if ($bA.ret) { Ok 'A 生产中且有已收量：有「物料退货」' } else { Bad 'A 缺「物料退货」' }
  if (-not $bA.newRet) { Ok 'A 收退详情：已无「新增退货」（2026-09-29 最终口径，退货统一走工具栏）' } else { Bad 'A 详情仍出现「新增退货」' }
  if ($bA.fin) { Ok 'A 生产中：有「结单」' } else { Bad 'A 缺「结单」' }
  if (-not $bA.reopen) { Ok 'A 生产中：无「反结单」' } else { Bad 'A 生产中不该有「反结单」' }
}

# ② 工具栏「物料退货」：打开同一弹窗、**按订单明细**预填（可退 = 已收量 20），随后取消
$clickRet = EvalJs "(()=>{for(const b of document.querySelectorAll('button')){if((b.innerText||'').trim()==='物料退货'){b.click();return 'clicked'}}return 'nobtn'})()"
Start-Sleep -Milliseconds 1600
$dlg = ReadJson "(()=>{const ds=[...document.querySelectorAll('.el-dialog')].filter(x=>x.getClientRects().length>0);const d=ds[ds.length-1];if(!d)return JSON.stringify({open:false});const t=(d.querySelector('.el-dialog__title')||{}).innerText||'';const rows=[...d.querySelectorAll('.el-table__body tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>td.innerText.trim()));const txt=d.innerText||'';return JSON.stringify({open:true,title:t,rows:rows,src:(txt.indexOf('按订单明细退货')>=0)});})()" '物料退货弹窗'
if ($dlg) {
  if ($dlg.open -and $dlg.title -match '退货') { Ok ('工具栏「物料退货」打开弹窗（' + $dlg.title + '）') } else { Bad ('「物料退货」未打开弹窗：' + $clickRet) }
  if (@($dlg.rows).Count -eq 1 -and (@($dlg.rows)[0][1]) -eq '20') { Ok ('弹窗按订单明细预填：1 行、可退 = 已收 20（' + ((@($dlg.rows)[0]) -join ' / ') + '）') }
  else { Bad ('弹窗明细不符（期望 1 行且可退=20）：' + ((@($dlg.rows) | ForEach-Object { $_ -join '/' }) -join ' ｜ ')) }
  if ($dlg.src) { Ok '弹窗标明「按订单明细退货」（工具栏入口不带来源收货单）' } else { Bad '弹窗未标明「按订单明细退货」' }
}
EvalJs "(()=>{const ds=[...document.querySelectorAll('.el-dialog')].filter(x=>x.getClientRects().length>0);const d=ds[ds.length-1];if(!d)return 'nodlg';for(const b of d.querySelectorAll('button')){if((b.innerText||'').trim()==='取消'){b.click();return 'closed'}}return 'nocancel'})()" | Out-Null
Start-Sleep -Milliseconds 900

# ③ 结单（UI）→ 已结单形态 → 反结单（UI）→ 回生产中
$clickFin = EvalJs "(()=>{for(const b of document.querySelectorAll('button')){if((b.innerText||'').trim()==='结单'){b.click();return 'clicked'}}return 'nobtn'})()"
Start-Sleep -Milliseconds 1300
Confirm
$stA = SqlOne ("SELECT status FROM outsource_material_order WHERE id=" + $oidA)
if ($stA -eq 'FINISHED') { Ok 'UI 结单 → status=FINISHED' } else { Bad ('UI 结单 失败（click=' + $clickFin + '），status=' + $stA) }
$bA2 = Buttons ("$base/outsource/material-order/delivery/" + $oidA) 'A 收退详情（已结单）'
if ($bA2) {
  if ($bA2.reopen) { Ok 'A 已结单：有「反结单」' } else { Bad 'A 已结单缺「反结单」' }
  if (-not $bA2.fin -and -not $bA2.recv) { Ok 'A 已结单：无「结单」/「新增收货」（终态 + 后端拒绝收货）' } else { Bad 'A 已结单不该有「结单」/「新增收货」' }
  if ($bA2.ret) { Ok 'A 已结单且仍有已收量：仍可「物料退货」（后端允许已结单退货）' } else { Bad 'A 已结单缺「物料退货」' }
}
$clickRe = EvalJs "(()=>{for(const b of document.querySelectorAll('button')){if((b.innerText||'').trim()==='反结单'){b.click();return 'clicked'}}return 'nobtn'})()"
Start-Sleep -Milliseconds 1300
Confirm
$stA2 = SqlOne ("SELECT status FROM outsource_material_order WHERE id=" + $oidA)
$ftA = SqlOne ("SELECT IFNULL(finish_time,'NULL') FROM outsource_material_order WHERE id=" + $oidA)
if ($stA2 -eq 'RECEIVING' -and $ftA -eq 'NULL') { Ok 'UI 反结单 → 回 RECEIVING 且 finish_time 清空' } else { Bad ('UI 反结单 不符（click=' + $clickRe + '）：status=' + $stA2 + ' finish_time=' + $ftA) }

# ④ 生产中 + 未收：**仍给「物料退货」**（2026-09-29 用户口径「物料退货没必要隐藏」）——
#    入口只按订单状态给（与后端 returnMaterial 第一道校验同口径），可退量交弹窗/后端判定。
$bB = Buttons ("$base/outsource/material-order/delivery/" + $oidB) 'B 收退详情（生产中·未收）'
if ($bB) {
  if ($bB.ret) { Ok 'B 生产中无已收量：**仍给**「物料退货」入口（不再按可退量隐藏）' } else { Bad 'B 缺「物料退货」（口径：入口只按订单状态给）' }
  if ($bB.fin -and $bB.recv) { Ok 'B 生产中：有「新增收货」+「结单」' } else { Bad 'B 缺「新增收货」或「结单」' }
  # ④b 「没必要隐藏」的落地形态：未收单也能打开弹窗，且**看得到"可退 = 0"**（列全部订单明细，
  #     不是一片空白）—— 弹窗/后端才是判定可退量的地方。
  $clickB = EvalJs "(()=>{for(const b of document.querySelectorAll('button')){if((b.innerText||'').trim()==='物料退货'){b.click();return 'clicked'}}return 'nobtn'})()"
  Start-Sleep -Milliseconds 1600
  $dlgB = ReadJson "(()=>{const ds=[...document.querySelectorAll('.el-dialog')].filter(x=>x.getClientRects().length>0);const d=ds[ds.length-1];if(!d)return JSON.stringify({open:false});const rows=[...d.querySelectorAll('.el-table__body tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>td.innerText.trim()));return JSON.stringify({open:true,rows:rows});})()" 'B 物料退货弹窗'
  if ($dlgB) {
    $bRows = @($dlgB.rows)
    if ($dlgB.open -and ($bRows.Count -ge 1) -and ((@($bRows[0]))[1] -eq '0')) {
      Ok ('B 弹窗列出全部订单明细且 可退 = 0（' + ((@($bRows[0])) -join ' / ') + '）')
    } else {
      Bad ('B 弹窗明细不符（期望 ≥1 行且 可退=0）：click=' + $clickB + ' → ' + (($bRows | ForEach-Object { $_ -join '/' }) -join ' ｜ '))
    }
    EvalJs "(()=>{const ds=[...document.querySelectorAll('.el-dialog')].filter(x=>x.getClientRects().length>0);const d=ds[ds.length-1];if(!d)return 'nodlg';for(const b of d.querySelectorAll('button')){if((b.innerText||'').trim()==='取消'){b.click();return 'closed'}}return 'nocancel'})()" | Out-Null
    Start-Sleep -Milliseconds 800
  }
}

# ⑤ 已结单夹具 C 的收退详情：反结单（不动它，只看形态）
$bC = Buttons ("$base/outsource/material-order/delivery/" + $oidC) 'C 收退详情（已结单）'
if ($bC) {
  if ($bC.reopen -and -not $bC.fin -and -not $bC.recv) { Ok 'C 已结单：有「反结单」、无「结单」/「新增收货」' } else { Bad ('C 已结单形态不符：' + ($bC | ConvertTo-Json -Compress)) }
}

# ⑥ 物料订单详情：结单/反结单已统一收到收退详情 ⇒ 本页不得再有；跳转按钮文案 = 「物料收退」
OpenFresh ("$base/outsource/material-order/detail/" + $oidA)
$d6 = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());const t=document.body.innerText||'';return JSON.stringify({fin:b.includes('结单'),reopen:b.includes('反结单'),jump:b.includes('物料收退'),oldJump:b.includes('物料收货'),oldTitle:(t.indexOf('物料收货')>=0)});})()" '物料订单详情'
if ($d6) {
  if (-not $d6.fin -and -not $d6.reopen) { Ok '物料订单详情已无「结单」「反结单」（统一收到收退详情）' } else { Bad '物料订单详情仍有「结单」/「反结单」' }
  if ($d6.jump -and -not $d6.oldJump) { Ok '物料订单详情跳转按钮已改名「物料收退」' } else { Bad ('跳转按钮文案不符：' + ($d6 | ConvertTo-Json -Compress)) }
}

# ⑦ 收货列表「已结单」页签：行内不再有「反结单」（改到收退详情办），仍有 收货详细 + 退货
OpenFresh "$base/outsource/material-order/delivery"
EvalJs "(()=>{const ts=[...document.querySelectorAll('.page-list .el-tabs__item')];const t=ts.find(x=>(x.innerText||'').indexOf('已结单')>=0);if(t)t.click();return 'ok'})()" | Out-Null
Start-Sleep -Milliseconds 1900
$d7 = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({reopen:b.includes('反结单'),detail:b.includes('收货详细'),ret:b.includes('退货'),rows:document.querySelectorAll('.el-table__body tbody tr').length});})()" '物料收退列表（已结单）'
if ($d7) {
  if (-not $d7.reopen) { Ok '收货列表 已结单页签行内已无「反结单」' } else { Bad '收货列表 已结单页签行内仍有「反结单」' }
  if ([int]$d7.rows -ge 1) {
    if ($d7.detail -and $d7.ret) { Ok '收货列表 已结单行内仍有「收货详细」+「退货」' } else { Bad ('已结单行内动作缺失：' + ($d7 | ConvertTo-Json -Compress)) }
  } else { Write-Output '（已结单页签 0 行，跳过行内动作断言）' }
}

# ⑧ 清理夹具
foreach ($oid in @($oidA, $oidB, $oidC)) {
  SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=" + $oid + ";")
  SqlExec ("DELETE FROM outsource_material_order WHERE id=" + $oid + ";")
}
$left = SqlOne ("SELECT COUNT(*) FROM outsource_material_order WHERE code LIKE 'MWO-MRR-%-" + $ts + "'")
if ($left -eq '0') { Ok '夹具已清理干净' } else { Bad ('夹具残留 ' + $left + ' 单') }

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 物料收退：改名 + 详情页 物料退货/结单（订单详情与列表行内已统一收到本页）' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
