# 「加工收退 / 物料收退」独立菜单校验（2026-09-16 建立，2026-09-18 改为以库为准，可复跑）
# 背景：原「加工订单详情 → 交货管理」「物料订单详情 → 交货管理」页签移出为委外加工下的两个子菜单
# 断言：① 侧栏委外加工子菜单 = sys_menu(parent_id=4, visible=1) 按 sort_order
#          （2026-09-17 用户定稿的书写顺序；**2026-09-28/29 两次改名**：成品收货 → 加工收货 → 加工收退、
#            物料收货 → 物料收退，只改文案、id/path/perms 不变；409 供应商管理已下线）
#       ② /outsource/order/delivery 直达不 403，只列正在加工（PRODUCING）的加工单
#       ③ 行内「收货」→ 进入 /outsource/order/delivery/:id 且**自动弹出新增收货弹窗**（一步收货）
#       ④ 收货详细页有 汇总卡 / 收货记录区块 / 返回列表
#       ⑤ 加工单详情页签已无「交货管理」，且**不再有**「成品收货」跳转按钮
#          （2026-09-21 用户口径：收货统一从「成品收货」菜单进）
#       ⑥ /outsource/material-order/delivery 直达不 403（生产中订单可能为 0 行 ⇒ 只验渲染/不报错，
#          并**负向守卫**：行内操作文案不得再出现「收料」—— 2026-09-21 用户口径已改为「收货」）
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$global:fail = 0
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
# 读某父菜单下可见子菜单名称（按 sort_order）——期望值以库为准，菜单调整后本脚本无需再改
function MenuNames([int]$parentId) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=$parentId AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $global:fail = $global:fail + 1 }
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
  # 2026-10-03: `agent-browser eval` 回传的中文在本机被按 GBK 解码（同一坑见 verify-bill-export.ps1:67）
  # ⇒ 本脚本所有"实际值 vs zh.json/DB 期望值"的比较曾经**恒红**（实测 19 条：侧栏顺序、页签文案、列名…）。
  # 修法：让 JS 先把结果转 base64 再回传，本地按 UTF-8 解码 —— 只有输出方向有此问题（入参里的中文
  # 作为命令行参数传给 node 是好的，故各断言里的 JS 字面量不必改）。ASCII only.
  $b64 = ((EvalJs ("btoa(unescape(encodeURIComponent(" + $js + ")))")) -replace '[^A-Za-z0-9+/=]', '')
  $raw = ''
  try { $raw = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) }
  catch { $raw = (EvalJs $js).Replace('\"', '"') }
  $raw = $raw.Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("未读到 " + $want + "：" + $raw); return $null }
  return ($m.Value | ConvertFrom-Json)
}

# ① 侧栏委外加工子菜单顺序
OpenFresh "$base/outsource/order"
$d1 = ReadJson "(()=>{const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].find(x=>(x.querySelector('.el-sub-menu__title')?.innerText||'').includes('委外加工'));return JSON.stringify({c:s?[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim()):[]});})()" '侧栏'
if ($d1) {
  Write-Output ('委外加工 子菜单 = ' + ($d1.c -join ' | '))
  $expect = MenuNames 4
  Write-Output ('委外加工 DB 期望 = ' + ($expect -join ' | '))
  if (($d1.c -join ',') -eq ($expect -join ',')) { Ok ('子菜单顺序与库一致（' + $expect.Count + ' 项）') }
  else {
    $missing = @($expect | Where-Object { $d1.c -notcontains $_ })
    if ($missing.Count -gt 0) { Bad ('缺失/顺序不符：期望 ' + ($expect -join '/') + '，实际 ' + ($d1.c -join '/')) }
    else { Bad ('顺序不符：期望 ' + ($expect -join '/') + '，实际 ' + ($d1.c -join '/')) }
  }
  # 下线项（visible=0）不得再出现在侧栏
  $hidden = @()
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=4 AND visible=0" 2>$null
  foreach ($h in @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })) {
    if ($d1.c -contains $h) { $hidden += $h }
  }
  if ($hidden.Count -eq 0) { Ok '已下线菜单未出现在侧栏' } else { Bad ('已下线菜单仍显示：' + ($hidden -join '、')) }
}

# ② 加工收退列表：直达不 403 + 只出正在加工的加工单
OpenFresh "$base/outsource/order/delivery"
$p2 = EvalJs "location.pathname"
if ($p2 -match '/login' -or $p2 -match '403') { Bad ('/outsource/order/delivery 未正常进入，落在 ' + $p2) }
else { Ok '/outsource/order/delivery 直达正常（非 403）' }
$d2 = ReadJson "(()=>{const rows=[...document.querySelectorAll('.el-table__body tbody tr')];const th=[...document.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim());const t=document.body.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({n:rows.length,prod:t.includes('生产中')==true,pending:t.includes('待审核')==true,prog:th.some(x=>x.indexOf('已收')>=0),cols:th,btn:t.includes('收货')==true,ret:b.some(x=>x==='退货'),cls:b.some(x=>x==='结单'),unaudit:b.some(x=>x==='反审核'),tabs:[...document.querySelectorAll('.page-list .el-tabs__item')].map(x=>x.innerText.trim())});})()" '成品收货列表'
if ($d2) {
  Write-Output ('加工收退列表行数 = ' + $d2.n)
  Write-Output ('加工收退 页签 = ' + ($d2.tabs -join ' | '))
  # 夹具前提：②~⑤ 与 ⑧b/⑧c 需要**库里至少 1 张 PRODUCING 加工单**才能走完"列表 → 行内收货 → 收货详细页"链路。
  # 库里没有时（本库 2026-10-03 实测 0 张）**跳过**而不是判红 —— 否则会一路红 12 条，全是级联假红（0 行 ⇒
  # 没有按钮可点 ⇒ 详情页没打开 ⇒ 后面每一条都"缺东西"），把真问题淹掉。与 ui-e2e-14 / 下方 §⑤ 既有的
  # "缺夹具就打印跳过"同范式。
  $script:po = ([int]$d2.n -ge 1)
  if ($script:po) { Ok ('列表有数据（' + $d2.n + ' 行）') }
  else { Write-Output '（库中无 PRODUCING 加工单 ⇒ 跳过「列表有数据」断言；②~⑤/⑧b/⑧c 一并不验，避免级联假红）' }
  # 2026-09-27（用户口径「成品收货应该有 收货中｜已结单 两个页签」）；2026-09-28 用户口径：该页签文案改「生产中」
  $tabStr = ($d2.tabs -join ',')
  if ($tabStr -match '生产中' -and $tabStr -match '已结单') { Ok '加工收退有两个页签：生产中｜已结单' }
  else { Bad ('加工收退页签不符（期望 生产中/已结单）：' + $tabStr) }
  if ($tabStr -match '^生产中') { Ok '默认页签 = 生产中（口径与"只列生产中"一致）' } else { Bad ('默认页签不是 生产中：' + $tabStr) }
  if ($d2.prog -and $d2.btn) { Ok '列表含「下单/已收/剩余」进度列与 收货 按钮' } else { Bad ('列表缺少进度列或 收货 按钮（当前列：' + ($d2.cols -join '/') + '）') }
  if ($d2.prod -and -not $d2.pending) { Ok '列表只含正在加工（生产中）的加工单' } else { Bad ('列表含非生产中订单：prod=' + $d2.prod + ' pending=' + $d2.pending) }
  # 2026-09-24（用户口径）：成品收货**列表**的行内操作是「收货 + 退货」，与物料收退列表口径一致；
  # 「退货」跳「加工退货（拆分还料）」页（按本加工单还料/红冲收货），「结单」不在列表里（保留在详情页）。
  # ⚠️ 断言按**按钮**取值而不是 body 文本 —— 侧栏还有「委外加工退货」菜单，用文本判定必然假通过。
  # 行内按钮要有行才渲染 ⇒ 无夹具时跳过（否则就是"0 行 ⇒ 缺按钮"的假红）
  if (-not $script:po) { Write-Output '（库中无 PRODUCING 加工单 ⇒ 跳过「列表有退货入口」断言）' }
  elseif ($d2.ret) { Ok '列表页有「退货」入口（跳加工退货·拆分还料页）' } else { Bad '列表页缺少「退货」按钮' }
  if (-not $d2.cls) { Ok '列表行内已无「结单」入口（结单保留在成品收货详情页）' } else { Bad '列表行内仍出现「结单」按钮（应已移除）' }
  # 2026-09-29 晚（用户口径「加工收退页面，列表的操作不需要有反审核」）：**生产中**行内**不得**有「反审核」——
  #   反审核是**加工单级**动作（级联逆回该单所有已审核的收货记录：库存 / 成品流水 / 应付），入口只在
  #   「加工单详情」页头；本页是收货工作台，行内保持 收货 ｜ 退货（已结单页签 = 收货详细 ｜ 结单报表）。
  #   注：本页操作列由 v-if 按页签切换、行数据也只含当前页签 ⇒ 全局按钮判定即"生产中那些行"。
  if (-not $d2.unaudit) { Ok '生产中的行内无「反审核」（收货 ｜ 退货；反审核在加工单详情页头）' } else { Bad '生产中行内出现了「反审核」（2026-09-29 口径：列表不需要）' }
}

# ⚠️ ③~⑤ 需要列表里**有行可点**（= 库里有 PRODUCING 加工单）⇒ 无夹具时整段跳过（说明见 §② 的注释）。
if ($script:po) {
# ③ 行内「收货」→ 自动弹出新增收货弹窗（一步收货）
$clicked = EvalJs "(()=>{const tr=document.querySelectorAll('.el-table__body tbody tr')[0];if(!tr)return 'no-row';for(const b of tr.querySelectorAll('button')){if(b.innerText.trim()==='收货'){b.click();return 'clicked'}}return 'no-btn'})()"
Write-Output ('点击 收货 按钮 = ' + $clicked)
agent-browser wait 3200
$d3 = ReadJson "(()=>{const d=[...document.querySelectorAll('.el-dialog')].find(x=>x.offsetParent!==null);return JSON.stringify({p:location.pathname,title:d?(d.querySelector('.el-dialog__title')?.innerText.trim()||''):''});})()" '收货详细页'
if ($d3) {
  Write-Output ('跳转后地址 = ' + $d3.p + ' / 弹窗标题 = ' + $d3.title)
  if ($d3.p -match '^/outsource/order/delivery/\d+') { Ok ('已进入收货详细页（' + $d3.p + '）') } else { Bad ('未进入收货详细页：' + $d3.p) }
  if ($d3.title -match '新增收货记录') { Ok '进入即自动弹出「新增收货记录」弹窗' } else { Bad ('未自动弹出新增收货弹窗，标题=' + $d3.title) }
}

# ④ 关闭弹窗后：汇总卡 / 收货记录 / 统一「返回」按钮
EvalJs "(()=>{const d=[...document.querySelectorAll('.el-dialog')].find(x=>x.offsetParent!==null);if(d){const btns=[...d.querySelectorAll('button')];for(const b of btns){if(b.innerText.trim()==='取消'){b.click();return 'cancel'}}}return 'none'})()" | Out-Null
agent-browser wait 1600
# ⚠️ 2026-09-23 次级页面统一模板：本页原「← 返回列表」按钮已并入统一骨架 PageShell 的页头「← 返回」
#    ⇒ 断言改为「有 .page-shell 页头里的返回按钮」且「不再出现『返回列表』」（保留旧断言会误红）。
$d4 = ReadJson "(()=>{const t=document.body.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());const pb=[...document.querySelectorAll('.page-header__left button')].map(x=>x.innerText.trim());return JSON.stringify({sum:t.includes('订单总量')&&t.includes('已收数量')&&t.includes('剩余数量'),rec:t.includes('收货记录'),back:pb.includes('返回'),backList:t.includes('返回列表'),shell:!!document.querySelector('.page-shell'),ret:b.some(x=>x==='退货'),mfg:b.some(x=>x==='加工退货'),old:b.some(x=>x==='退不良')||b.some(x=>x==='不良退货'),cls:b.some(x=>x==='结单')});})()" '收货详细页'
if ($d4) {
  if ($d4.sum) { Ok '收货详细页有汇总卡（订单总量/已收数量/剩余数量）' } else { Bad '收货详细页缺少汇总卡' }
  if ($d4.rec) { Ok '收货详细页有「收货记录」区块' } else { Bad '收货详细页缺少「收货记录」区块' }
  if ($d4.shell -and $d4.back -and (-not $d4.backList)) { Ok '收货详细页有统一骨架（.page-shell）与页头「← 返回」，且旧「返回列表」已移除' } else { Bad ('收货详细页统一返回校验失败：shell=' + $d4.shell + ' hasBack=' + $d4.back + ' backList=' + $d4.backList) }
  # 2026-09-21（用户口径「文案改成加工退货」）：本页**只保留「加工退货」**（红冲收货，记录挂在这张委外加工单上）：
  # 「退货」入口（跳独立加工退货单）已整体移除；按钮文案沿革 退不良 → 加工退货 → 不良退货 → **定稿加工退货**
  # （实现不变，仍是负数红冲）。三条断言一起锁住，防止被加回来或改回去。
  if (-not $d4.ret) { Ok '收货详细页已无「退货」入口' } else { Bad '收货详细页仍出现「退货」按钮（应已移除）' }
  if ($d4.mfg) { Ok '收货详细页有「加工退货」入口（红冲收货）' } else { Bad '收货详细页缺少「加工退货」按钮' }
  if (-not $d4.old) { Ok '收货详细页已无「退不良/不良退货」字样（全链统一为加工退货）' } else { Bad '收货详细页仍出现「退不良」或「不良退货」按钮' }
  # 2026-09-21（用户口径「结单按钮放到成品收货里」）：本页（加工收退的"收货详细"）必须有「结单」入口
  if ($d4.cls) { Ok '收货详细页有「结单」入口（跳结单报表页）' } else { Bad '收货详细页缺少「结单」入口' }
}

# ④b 「收货记录」行内动作（2026-09-29 用户口径）：**加工退货（红冲）草稿 = 「作废」**（留痕可查，
#     与原「关联退货」台账的「已作废」同口径），普通收货草稿仍为「删除」（物理删草稿）。
#     关联退货叶子下线后本表是有单加工退货**唯一的常驻入口** ⇒ 这个动作口径必须锁住。
#     2026-09-29 同日追加（用户口径「加工收退详情的收货记录也需要反审核功能」）：**已审核行 = 详情 ｜ 反审核**
#     （记录详情页只读，故本表是唯一入口）；草稿行不得误加「反审核」。
$d4b = ReadJson "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis).find(x=>[...x.querySelectorAll('.el-table__header th')].map(h=>(h.innerText||'').trim()).includes('收货日期'));const rows=t?[...t.querySelectorAll('.el-table__body tbody tr')]:[];let defectDraft=0,draftRecv=0,cancelOnDefect=0,delOnDefect=0,delOnRecv=0,cancelOnRecv=0,audited=0,unOnAudited=0,unOnDraft=0;for(const tr of rows){const txt=[...tr.querySelectorAll('td')].map(td=>(td.innerText||'').trim());const bs=[...tr.querySelectorAll('button')].map(b=>(b.innerText||'').trim());const isDefect=txt.includes('加工退货');const isDraft=txt.includes('草稿');const isAudited=txt.includes('已审核');if(isAudited){audited++;if(bs.includes('反审核'))unOnAudited++}if(isDraft&&bs.includes('反审核'))unOnDraft++;if(isDefect&&isDraft){defectDraft++;if(bs.includes('作废'))cancelOnDefect++;if(bs.includes('删除'))delOnDefect++}else if(isDraft&&txt.includes('收货')){draftRecv++;if(bs.includes('删除'))delOnRecv++;if(bs.includes('作废'))cancelOnRecv++}}return JSON.stringify({rows:rows.length,defectDraft:defectDraft,draftRecv:draftRecv,cancelOnDefect:cancelOnDefect,delOnDefect:delOnDefect,delOnRecv:delOnRecv,cancelOnRecv:cancelOnRecv,audited:audited,unOnAudited:unOnAudited,unOnDraft:unOnDraft});})()" '收货记录动作'
if ($d4b) {
  Write-Output ('收货记录行数 = ' + $d4b.rows + ' ；红冲草稿 = ' + $d4b.defectDraft + '（带作废 ' + $d4b.cancelOnDefect + ' / 带删除 ' + $d4b.delOnDefect + '）；普通收货草稿 = ' + $d4b.draftRecv)
  if ([int]$d4b.defectDraft -gt 0) {
    if ([int]$d4b.delOnDefect -eq 0) { Ok '加工退货草稿行已无「删除」（改为「作废」留痕）' } else { Bad ('加工退货草稿行仍出现「删除」按钮：' + $d4b.delOnDefect) }
    if ([int]$d4b.cancelOnDefect -eq [int]$d4b.defectDraft) { Ok ('每条加工退货草稿都有「作废」（' + $d4b.cancelOnDefect + '/' + $d4b.defectDraft + '）') } else { Bad ('部分加工退货草稿缺「作废」：' + $d4b.cancelOnDefect + '/' + $d4b.defectDraft) }
  } else { Write-Output '（本单收货记录里没有加工退货草稿 ⇒ 跳过红冲「作废」断言，避免假 FAIL）' }
  if ([int]$d4b.draftRecv -gt 0) {
    if ([int]$d4b.cancelOnRecv -eq 0) { Ok '普通收货草稿仍只有「删除」（未被误加「作废」）' } else { Bad ('普通收货草稿被误加「作废」：' + $d4b.cancelOnRecv) }
  } else { Write-Output '（本单没有普通收货草稿 ⇒ 跳过「删除」断言）' }
  # 2026-09-29（用户口径「加工收退详情的收货记录也需要反审核功能」）：**已审核行**必须有「反审核」
  #   （记录详情页 /outsource/order/delivery/record/:id 是**只读**的 ⇒ 本表是唯一入口）；
  #   草稿行不得有（草稿的动作是 审核 / 编辑 / 删除·作废）。这条锁住 2026-09-24「反审核移出操作列」
  #   造成的功能真空（后端端点与 handleUnaudit 一直存在，只是没有按钮）。
  Write-Output ('已审核行 = ' + $d4b.audited + '（带反审核 ' + $d4b.unOnAudited + '）；草稿行被误加反审核 = ' + $d4b.unOnDraft)
  if ([int]$d4b.audited -gt 0) {
    if ([int]$d4b.unOnAudited -eq [int]$d4b.audited) { Ok ('每条已审核收货记录都有「反审核」（' + $d4b.unOnAudited + '/' + $d4b.audited + '）') } else { Bad ('部分已审核收货记录缺「反审核」：' + $d4b.unOnAudited + '/' + $d4b.audited) }
  } else { Write-Output '（本单没有已审核收货记录 ⇒ 跳过「反审核」断言，避免假 FAIL）' }
  if ([int]$d4b.unOnDraft -eq 0) { Ok '草稿行未被误加「反审核」（草稿走 审核/编辑/删除·作废）' } else { Bad ('草稿行被误加「反审核」：' + $d4b.unOnDraft) }
}

# ⑤ 加工单详情：页签已无「交货管理」，且**不再有**「成品收货」跳转按钮
#    2026-09-21（用户口径「委外加工单详情页面里面的成品收货按钮不要了。在成品收货里面收货就行」）：
#    收货统一从「委外加工 → 成品收货」菜单进，故这里**反过来**断言"按钮必须不存在"。
$oid = if ($d3 -and $d3.p -match '(\d+)$') { $Matches[1] } else { '' }
if ($oid) {
  OpenFresh "$base/outsource/order/detail/$oid"
  # 2026-09-29：菜单由「加工收货」改「加工收退」⇒ 这条负向断言**新旧两个词都查**（防"旧按钮被加回来"）
  # 2026-10-03：用户口径又改回「加工收货」（物料侧同步改回「物料收货」）⇒ 仍查这两个词（其中一个已是现名）
  $d5 = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim());const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({tabs:t,hasRecv:(b.includes('加工收退')==true||b.includes('加工收货')==true),hasDefect:(b.includes('退不良')==true),hasMfg:(b.includes('加工退货')==true||b.includes('不良退货')==true),cls:b.some(x=>x==='结单')});})()" '加工单详情'
  if ($d5) {
    Write-Output ('加工单详情 页签 = ' + ($d5.tabs -join ' | '))
    if (($d5.tabs -join ',') -match '交货管理') { Bad '加工单详情仍有「交货管理」页签（应已移出）' } else { Ok '加工单详情已无「交货管理」页签' }
    if ($d5.hasRecv) { Bad '加工单详情仍有「成品收货」跳转按钮（2026-09-21 用户口径应已移除）' } else { Ok '加工单详情已无「成品收货」跳转按钮（收货统一从菜单进）' }
    if (-not $d5.hasDefect -and -not $d5.hasMfg) { Ok '加工单详情已无「退不良/加工退货」按钮（随交货管理移出）' } else { Bad ('加工单详情仍有退回按钮：defect=' + $d5.hasDefect + ' mfg=' + $d5.hasMfg) }
    # 2026-09-21（用户口径「结单按钮放到成品收货里」）：本页的「结单」按钮必须已撤掉
    if (-not $d5.cls) { Ok '加工单详情已无「结单」按钮（结单入口已搬到成品收货）' } else { Bad '加工单详情仍有「结单」按钮（应已移除）' }
    # 已无按钮可点：只确认本页直达正常、没有被重定向走（防"移除按钮时顺手改坏了路由"）
    $p5 = (EvalJs "location.pathname").Trim('"')
    if ($p5 -like "/outsource/order/detail/*") { Ok ('加工单详情直达正常（' + $p5 + '）') } else { Bad ('加工单详情路径异常：' + $p5) }
  }
} else { Write-Output '（未取到加工单 id，跳过详情页校验）' }
} else { Write-Output '（库中无 PRODUCING 加工单 ⇒ 跳过 ③~⑤：收货弹窗 / 收货详细页 / 加工单详情页签）' }

# ⑥ 物料收退页：直达不 403 且渲染正常（当前库中生产中订单 0 条属正常）
OpenFresh "$base/outsource/material-order/delivery"
$p6 = EvalJs "location.pathname"
if ($p6 -match '/login' -or $p6 -match '403') { Bad ('/outsource/material-order/delivery 未正常进入，落在 ' + $p6) }
else { Ok '/outsource/material-order/delivery 直达正常（非 403）' }
# 2026-09-29（用户口径）：菜单名「物料收货」→「物料收退」⇒ 标题断言同步（只改文案，path 不变）
# 2026-10-03（用户口径）：又改回「物料收货」⇒ **两个词的角色互换**（下面的 title/oldTitle 由此对调，
#   否则这条断言会拿旧名当新名、拿新名当旧名，页面明明对了却报 FAIL）
$d6 = ReadJson "(()=>{const t=document.body.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({title:(t.includes('物料收货')==true),oldTitle:(t.includes('物料收退')==true),err:(t.includes('加载待收货订单失败')==true),rows:document.querySelectorAll('.el-table__body tbody tr').length,oldrecv:(t.includes('收料')==true),btn:b.some(x=>x==='收料'),tabs:[...document.querySelectorAll('.page-list .el-tabs__item')].map(x=>x.innerText.trim())});})()" '物料收货列表'
if ($d6) {
  # 2026-09-27：页签化后标题由「物料收货（收货中的物料订单）」简化为页名（状态由页签表达）
  if ($d6.title) { Ok '物料收货页标题渲染正常' } else { Bad '物料收货页标题未渲染（页面可能报错）' }
  if (-not $d6.oldTitle) { Ok '物料收货页已无旧名「物料收退」' } else { Bad '物料收货页仍出现旧名「物料收退」' }
  if (-not $d6.err) { Ok '物料收货页无加载错误提示' } else { Bad '物料收货页出现「加载待收货订单失败」' }
  $tabStr6 = ($d6.tabs -join ',')
  Write-Output ('物料收货 页签 = ' + $tabStr6)
  # 2026-09-28 用户口径：该页签文案同步改「生产中」（与加工收货页一致）；同日订单**状态**文案也由「收货中」统一为「生产中」
  if ($tabStr6 -match '生产中' -and $tabStr6 -match '已结单') { Ok '物料收货有两个页签：生产中｜已结单' }
  else { Bad ('物料收货页签不符（期望 生产中/已结单）：' + $tabStr6) }
  if ($tabStr6 -match '^生产中') { Ok '默认页签 = 生产中（口径与"只列生产中"一致）' } else { Bad ('默认页签不是 生产中：' + $tabStr6) }
  Write-Output ('物料收货列表行数 = ' + $d6.rows + '（库中可能 0 行"生产中"订单 ⇒ 0 行属正常）')
  # 2026-09-21（用户口径「操作文案从收料/退料改成收货/退货」）：行内按钮「收料」必须已改为「收货」。
  # ⚠️ 库里可能没有"生产中"的物料订单（0 行 ⇒ 按钮不渲染）⇒ 这条是**负向守卫**（防改回去），0 行时天然通过。
  if (-not $d6.oldrecv -and -not $d6.btn) { Ok '物料收货列表页已无「收料」（行内按钮已改为「收货」）' }
  else { Bad ('物料收货列表页仍出现「收料」：文本=' + $d6.oldrecv + ' 按钮=' + $d6.btn) }
}

# ⑦ 首页「委外加工」TAB 快捷入口含 加工收货 / 物料收货
OpenFresh "$base/dashboard"
# 非活动页签在 DOM 中但不可见（offsetParent=null），必须先切到「委外加工」TAB 再数按钮
$sw = EvalJs "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='\u59d4\u5916\u52a0\u5de5');if(!t)return 'nf';t.click();return 'ok'})()"
Write-Output ('切换委外加工 TAB = ' + $sw)
# 2026-09-17：菜单缓存被清后仪表盘按菜单树异步渲染快捷入口，1.8s 偶发读不到 → 放宽到 3s（消除假失败）
agent-browser wait 3000
# 2026-09-29：首页快捷入口按钮文案随菜单改名 → 加工收退（'\u52a0\u5de5\u6536\u9000'）/ 物料收退（'\u7269\u6599\u6536\u9000'）
# 2026-10-03：用户口径改回 → 加工收货（'\u52a0\u5de5\u6536\u8d27'）/ 物料收货（'\u7269\u6599\u6536\u8d27'）
#   ⚠️ 首页按钮中文是**写死在 dashboard/index.vue** 的（不读 DB 菜单名）⇒ 这里必须与前端同步改，否则必红
$d7 = ReadJson "(()=>{const pane=document.querySelector('#pane-outsource')||document;const b=[...pane.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({d:(b.includes('\u52a0\u5de5\u6536\u8d27')==true),m:(b.includes('\u7269\u6599\u6536\u8d27')==true),all:b.filter(x=>x)});})()" '仪表盘'
if ($d7) {
  Write-Output ('委外加工 TAB 按钮 = ' + ($d7.all -join ' | '))
  if ($d7.d) { Ok '首页委外加工 TAB 有「加工收货」快捷入口' } else { Bad '首页委外加工 TAB 缺少「加工收货」快捷入口' }
  if ($d7.m) { Ok '首页委外加工 TAB 有「物料收货」快捷入口' } else { Bad '首页委外加工 TAB 缺少「物料收货」快捷入口' }
}

# ⑧ 两个收货列表「一行显示完、不横向滑动」（2026-09-16 用户要求）
#    2026-09-27（两个页签）：**两个页签各自都要测** —— 已结单页签的列集不同（加 结单日期/时间、操作列更宽），
#    只测默认页签会把新页签的溢出漏掉（本页列宽合计常年贴着内容区 948px）。
foreach ($u in @("$base/outsource/order/delivery", "$base/outsource/material-order/delivery")) {
  OpenFresh $u
  foreach ($tab in @('生产中', '已结单')) {
    if ($tab -ne '生产中') {
      $c8 = EvalJs "(()=>{const ts=[...document.querySelectorAll('.page-list .el-tabs__item')];const t=ts.find(x=>(x.innerText||'').indexOf('$tab')>=0);if(!t)return 'no-tab';t.click();return 'clicked'})()"
      Write-Output ($u + ' 切页签 → ' + $tab + ' = ' + $c8)
      Start-Sleep -Milliseconds 1500
    }
    $d8 = ReadJson "(()=>{const t=document.querySelector('.el-table');const ths=[...document.querySelectorAll('.el-table__header th')];const sum=ths.reduce((s,x)=>s+x.offsetWidth,0);const box=t?t.clientWidth:0;const sc=t?t.classList.contains('el-table--scrollable-x'):true;const w=document.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');return JSON.stringify({sum:sum,box:box,sc:sc,wrapOver:(w?w.scrollWidth>w.clientWidth:true),cols:ths.length});})()" '表格宽度'
    if ($d8) {
      Write-Output ($u + ' [' + $tab + '] 列数=' + $d8.cols + ' 列宽合计=' + $d8.sum + ' 容器=' + $d8.box)
      if (-not $d8.sc -and -not $d8.wrapOver) { Ok ($u + ' [' + $tab + '] 无横向滚动（一行显示完）') } else { Bad ($u + ' [' + $tab + '] 出现横向滚动条') }
    }
  }
}

# ⑧b 收货详细页的「收货记录」表「一行显示完、不横向滑动」（2026-09-21 用户要求）
#   注：⑧ 覆盖的是两个**列表页**的首张表；这里补的是**收货详细页**里的「收货记录」表
#   （该页还有一张「按产品分类统计」表 ⇒ 遍历本页所有可见表，任一张溢出即判失败）。
#   改动背景：该表原 11 列、列宽合计 1310px，而内容区仅约 963px ⇒ 横向溢出 347px（真机实测）；
#   现重排为 10 列约 924px（附件列并入操作列、长文本列一律 tooltip），实测 scrollWidth == clientWidth。
if ($oid) {
  OpenFresh "$base/outsource/order/delivery/$oid"
  $d8b = ReadJson "(()=>{const ts=[...document.querySelectorAll('.el-table')].filter(t=>t.getClientRects().length>0);return JSON.stringify(ts.map(t=>{const ths=[...t.querySelectorAll('.el-table__header th')];const sum=ths.reduce((s,x)=>s+x.offsetWidth,0);const w=t.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');return {cols:ths.length,sum:sum,box:t.clientWidth,sc:t.classList.contains('el-table--scrollable-x'),over:(w?w.scrollWidth>w.clientWidth:true)};}));})()" '收货记录表宽度'
  if ($d8b) {
    Write-Output ('收货详细页 表数=' + @($d8b).Count)
    $i = 0
    foreach ($tb in @($d8b)) {
      $i++
      Write-Output ('  表#' + $i + ' 列数=' + $tb.cols + ' 列宽合计=' + $tb.sum + ' 容器=' + $tb.box)
      if (-not $tb.sc -and -not $tb.over) { Ok ('收货详细页 表#' + $i + ' 无横向滚动（一行显示完）') }
      else { Bad ('收货详细页 表#' + $i + ' 出现横向滚动条（列宽合计 ' + $tb.sum + ' vs 容器 ' + $tb.box + '）') }
    }
  }
}

# ⑧c 收货记录表结构（2026-09-21 用户建议「有一个详细会不会好一点，那列表就不用显示这么多信息了」）：
# ⚠️ 本段必须挂在"已经打开了收货详细页"的前提上（⑧b 打开过）。没有加工单（$oid 为空）时 ⑧b 已被跳过，
#   此时页面还停在加工收退**列表** ⇒ 下面"第一张可见表"会量到列表而不是收货记录表（实测列数 8、无「详情」）。
if ($oid) {
#   列表瘦身为 7 列，「收货仓库 / 物流单号 / 备注 / 附件」移入行内「详情」抽屉 ⇒ 断言列数 + 详情入口，
#   避免日后有人把列又加回列表把横向滚动带回来。（⑧b 已开着收货详细页，此处复用同一页。）
$d8c = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-table')].filter(x=>x.getClientRects().length>0)[0];if(!t)return JSON.stringify({ok:false});const ths=[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim());const tr=t.querySelector('.el-table__body tbody tr');const ops=tr?[...tr.children][[...tr.children].length-1].innerText:'';return JSON.stringify({ok:true,cols:ths.length,hasDetail:ops.indexOf('\u8BE6\u60C5')>=0});})()" '收货记录表结构'
if ($d8c -and $d8c.ok) {
  Write-Output ('收货记录表 列数=' + $d8c.cols)
  if ([int]$d8c.cols -eq 7) { Ok '收货记录表已瘦身为 7 列（仓库/物流单号/备注/附件移入详情抽屉）' } else { Bad ('收货记录表列数=' + $d8c.cols + '，应为 7') }
  if ($d8c.hasDetail) { Ok '收货记录操作列有「详情」入口' } else { Bad '收货记录操作列缺少「详情」入口' }
}
} else { Write-Output '（无加工单 ⇒ 跳过 ⑧c：收货记录表结构，避免量到列表页的表格）' }

# ⑨ 成品收货页**只做收货**（2026-09-21 用户口径「成品收货里面一个页面还有两个列表很别扭」）：
#    原先挂在本页下方的「无单加工退货」区块已迁到「加工售后」（原名「加工退货」目录）页 ⇒ 本页不得再出现该区块/退货按钮。
#    ⚠️ 断言一律按**按钮文本**取值：侧栏有「加工售后」菜单，用 body 文本判定必然假通过。
# 2026-09-29：区块旧名「无单加工退货」、该链路新名「工厂售后」⇒ 两个词都查（防"旧块被加回来"或"新名块被塞进来"）
OpenFresh "$base/outsource/order/delivery"
$d9 = ReadJson "(()=>{const m=document.querySelector('.layout-main')||document.body;const t=m.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({title:(t.includes('无单加工退货')||t.includes('工厂售后')),btn:(b.includes('新增无单加工退货')||b.includes('新增工厂售后')),ret:b.some(x=>x==='退货'),recv:b.some(x=>x==='收货'),fin:b.some(x=>x==='结单')});})()" '加工收退列表页'
if ($d9) {
  if (-not $d9.title) { Ok '加工收退列表页已无「无单加工退货/工厂售后」区块（退回统一到加工售后页）' } else { Bad '加工收退列表页仍有「无单加工退货/工厂售后」区块（应已迁走）' }
  if (-not $d9.btn) { Ok '加工收退列表页已无「新增无单加工退货/新增工厂售后」按钮' } else { Bad '加工收退列表页仍有「新增无单加工退货/新增工厂售后」按钮' }
  # 2026-09-24（用户口径）：成品收货**列表**的行内操作是「收货 + 退货」（与物料收货列表一致）；
  #   「结单」不再出现在列表里（它仍在成品收货详情页）⇒ 断言由「不得有退货」反转为「必须有收货+退货、不得有结单」
  # 行内按钮要有行才渲染 ⇒ 无夹具时跳过（同 §② 口径，避免"0 行 ⇒ 缺按钮"的假红）
  if (-not $script:po) { Write-Output '（库中无 PRODUCING 加工单 ⇒ 跳过「列表行内 = 收货+退货」断言）' }
  elseif ($d9.recv -and $d9.ret -and (-not $d9.fin)) { Ok '成品收货列表页行内操作 = 「收货」+「退货」，且已无「结单」' }
  else { Bad ('成品收货列表页操作不符（期望 收货+退货、无结单）：recv=' + $d9.recv + ' ret=' + $d9.ret + ' fin=' + $d9.fin) }
}

# ⑨c 「已结单」页签（2026-09-27 用户口径「收货中｜已结单 两个页签」）：两个页面各切过去断言 ——
#     ① 列里有「结单日期」（成品 = 加工单 actual_end_date，结单时写入）/「结单时间」（物料 = material_order.finish_time）；
#     ② 行内**不得**出现「收货」按钮（后端明确拒绝：已结单不可再收货）——"点了必被拒的按钮不该出现"；
#     ③ 行内必须有「收货详细」；成品还须有「结单报表」（**反结单在那一页**）、
#        物料须有「退货」且**不得再有「反结单」** —— 2026-09-29 用户口径「结单也收到收退详情」：
#        物料的 结单/反结单 已统一收到「物料收退详情」（material-order/delivery.vue 工具栏），列表行内撤；
#     ④ 列表条数与库中 FINISHED 单数一致（分页上限 10 ⇒ 取 min），证明页签真的按状态过滤而不是摆设。
$closedCases = @(
  @{ u = 'outsource/order/delivery';          name = '加工收退'; col = '结单日期'; extra = '结单报表'; sql = "SELECT COUNT(*) FROM outsource_order WHERE status='FINISHED'" },
  @{ u = 'outsource/material-order/delivery'; name = '物料收退'; col = '结单时间'; extra = '退货';     noExtra = '反结单'; sql = "SELECT COUNT(*) FROM outsource_material_order WHERE status='FINISHED'" }
)
foreach ($cc in $closedCases) {
  OpenFresh "$base/$($cc.u)"
  $c9 = EvalJs "(()=>{const ts=[...document.querySelectorAll('.page-list .el-tabs__item')];const t=ts.find(x=>(x.innerText||'').indexOf('已结单')>=0);if(!t)return 'no-tab';t.click();return 'clicked'})()"
  Write-Output ($cc.name + ' 切页签 → 已结单 = ' + $c9)
  Start-Sleep -Milliseconds 1700
  $d9c = ReadJson "(()=>{const t=document.querySelector('.el-table');const ths=t?[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim()):[];const rows=t?[...t.querySelectorAll('.el-table__body tbody tr')]:[];const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({cols:ths,rows:rows.length,btn:b});})()" ($cc.name + ' 已结单页签')
  if ($d9c) {
    Write-Output ($cc.name + ' 已结单 列 = ' + ($d9c.cols -join '/') + ' ；行数 = ' + $d9c.rows)
    if (($d9c.cols -join ',') -match [regex]::Escape($cc.col)) { Ok ($cc.name + ' 已结单页签有「' + $cc.col + '」列') }
    else { Bad ($cc.name + ' 已结单页签缺「' + $cc.col + '」列：' + ($d9c.cols -join '/')) }
    if ([int]$d9c.rows -ge 1) {
      if ($d9c.btn -contains '收货详细') { Ok ($cc.name + ' 已结单行内 = 收货详细（只读查看）') }
      else { Bad ($cc.name + ' 已结单行内缺「收货详细」') }
      if ($d9c.btn -contains $cc.extra) { Ok ($cc.name + ' 已结单行内有「' + $cc.extra + '」') }
      else { Bad ($cc.name + ' 已结单行内缺「' + $cc.extra + '」') }
      if ($cc.noExtra) {
        if ($d9c.btn -contains $cc.noExtra) { Bad ($cc.name + ' 已结单行内**不应**再有「' + $cc.noExtra + '」（2026-09-29 已统一收到收退详情）') }
        else { Ok ($cc.name + ' 已结单行内已无「' + $cc.noExtra + '」（结单/反结单统一收到收退详情）') }
      }
      if ($d9c.btn -notcontains '收货') { Ok ($cc.name + ' 已结单行内无「收货」（后端拒绝已结单收货，故不放）') }
      else { Bad ($cc.name + ' 已结单行内仍出现「收货」按钮') }
    } else { Write-Output ('（' + $cc.name + ' 已结单页签 0 行，跳过行内按钮断言）') }
    $dbN = [int](SqlOne $cc.sql)
    $exp = [Math]::Min($dbN, 10)
    if ([int]$d9c.rows -eq $exp) { Ok ($cc.name + ' 已结单列表条数与库一致（库 ' + $dbN + ' 单，本页 ' + $d9c.rows + ' 行）') }
    else { Bad ($cc.name + ' 已结单列表条数不符：库 ' + $dbN + '（本页应为 ' + $exp + '）vs 渲染 ' + $d9c.rows) }
  }
}

# ⑨b 加工退货 = **2 个叶子**（2026-09-27 拆叶子；**2026-09-29 用户口径「三级菜单关联退货不要了，
#     以后关联退货在「加工收货」（同日改名「加工收退」）里面退就行」⇒ 「关联退货」（408）叶子整体下线**）：
#     ① **2026-10-03 三级菜单合并后**：/outsource/return-order 不再是跳转，而是**合并页本体**
#        （两个 TAB：工厂售后｜客户售后），进入时把 TAB 写进地址栏（默认 ?tab=unlinked）⇒ 该地址仍可直接访问
#        （老书签不吃 403，语义与「/outsource/return-back 重定向」等效）；两个旧叶子地址
#        `/outsource/return-order/unlinked` 与 `/repair` 改为**重定向到合并页的对应 TAB**；
#     ② 该页不得再有**关联退货专属物**：页签「有效单据/已作废单据」、「关联加工单」列、
#        「新增」选单弹窗 —— 有单的加工退货改从「加工收退」进（列表行内「退货」/ 收货详细页按钮，
#        由 §⑨ 与 verify-detail-render 的 return-defect 用例覆盖）；
#     ③ 台账仍是同一张加工退货表（有「退货数量」列），「新增」仍是唯一入口。
#     工厂售后叶子（原无单退货）细节见 ⑨b-2；客户售后（原成品维修退货）见 ui-e2e-16；
#     业务口径（扣成品/还料/冲应付、可反审核、草稿作废）由 verify-no-order-return.ps1 覆盖（API 级）。
OpenFresh "$base/outsource/return-order"
$d9b = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-table')].filter(x=>x.getClientRects().length>0)[0];const ths=t?[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim()):[];const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({p:location.pathname,s:location.search,tabs:[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim()),cols:ths,qty:ths.includes('退货数量'),btn:b.filter(x=>x==='新增').length,dialogs:[...document.querySelectorAll('.el-dialog')].filter(x=>x.getClientRects().length>0).length});})()" '加工售后合并页'
if ($d9b) {
  Write-Output ('合并页落点 = ' + $d9b.p + $d9b.s + ' ；页签 = ' + ($d9b.tabs -join ' | ') + ' ；列 = ' + ($d9b.cols -join '/'))
  if (($d9b.p -eq '/outsource/return-order') -and ($d9b.s -match 'tab=unlinked')) { Ok '合并页 /outsource/return-order 直达且默认 TAB=工厂售后（老地址仍可直接访问，不吃 403）' }
  else { Bad ('合并页落点不符（期望 /outsource/return-order?tab=unlinked）：' + $d9b.p + $d9b.s) }
  if ((($d9b.tabs -join ',') -match '工厂售后') -and (($d9b.tabs -join ',') -match '客户售后')) { Ok '合并页有两个 TAB：工厂售后｜客户售后（三级菜单合并后同页切换）' }
  else { Bad ('合并页 TAB 不符（期望 工厂售后/客户售后）：' + ($d9b.tabs -join '/')) }
  if (($d9b.tabs -join ',') -notmatch '有效单据') { Ok '已无「有效单据」页签（原关联退货叶子专属）' }
  else { Bad ('仍出现「有效单据」页签：' + ($d9b.tabs -join '/')) }
  if (-not ($d9b.cols -contains '关联加工单')) { Ok '台账已无「关联加工单」列（2026-09-29：关联退货不再单独成页）' }
  else { Bad ('台账仍有「关联加工单」列：' + ($d9b.cols -join '/')) }
  if ($d9b.qty) { Ok '台账仍是同一张加工退货表（含「退货数量」列）' } else { Bad ('台账缺「退货数量」列：' + ($d9b.cols -join '/')) }
  if ([int]$d9b.btn -eq 1) { Ok '本页「新增」仍是唯一入口（= 无单退货入口）' } else { Bad ('本页「新增」按钮数应为 1：' + $d9b.btn) }
  if ([int]$d9b.dialogs -eq 0) { Ok '已无「新增关联退货」选单弹窗（该入口随叶子下线）' } else { Bad ('仍出现弹窗（dialogs=' + $d9b.dialogs + '）') }
}
# ⑨b-2 工厂售后 TAB（原无单退货叶子，2026-09-29 改文案；**2026-10-03 三级菜单合并**：状态由 3 个页签
#   改成**下拉**，页签只剩两个 TAB）⇒ 断言：旧叶子地址重定向到合并页对应 TAB + 「退货/已返回」进度列 +
#   无「关联加工单」列 + 唯一「新增」入口 + 状态下拉确实存在（原状态页签的替代物）。
OpenFresh "$base/outsource/return-order/unlinked"
$d9c = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-table')].filter(x=>x.getClientRects().length>0)[0];const ths=t?[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim()):[];const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({p:location.pathname,s:location.search,tabs:[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim()),cols:ths,prog:ths.includes('退货/已返回'),noOrder:ths.includes('关联加工单'),sel:[...document.querySelectorAll('.el-select')].filter(x=>x.getClientRects().length>0).length,btn:b.filter(x=>x==='新增').length});})()" '工厂售后 TAB（旧无单退货叶子地址）'
if ($d9c) {
  Write-Output ('工厂售后 TAB 落点 = ' + $d9c.p + $d9c.s + ' ；页签 = ' + ($d9c.tabs -join ' | ') + ' ；状态下拉 = ' + $d9c.sel + ' ；列 = ' + ($d9c.cols -join '/'))
  if (($d9c.p -eq '/outsource/return-order') -and ($d9c.s -match 'tab=unlinked')) { Ok '旧叶子地址 /outsource/return-order/unlinked 重定向到合并页 ?tab=unlinked' }
  else { Bad ('旧叶子地址未重定向到合并页：' + $d9c.p + $d9c.s) }
  if ((($d9c.tabs -join ',') -match '工厂售后') -and (($d9c.tabs -join ',') -match '客户售后') -and (($d9c.tabs -join ',') -notmatch '待返回')) { Ok 'TAB 只剩 工厂售后｜客户售后（原 3 个状态页签已改为下拉）' }
  else { Bad ('合并页 TAB 不符：' + ($d9c.tabs -join '/')) }
  if ([int]$d9c.sel -ge 1) { Ok '状态筛选已改为下拉（原状态页签的替代物，含「全部」清空项）' } else { Bad '未找到状态下拉（el-select）' }
  if ($d9c.prog) { Ok '无单退货台账含「退货/已返回」进度列（用户口径「还给我们没有、还了多少」）' } else { Bad ('无单退货台账缺「退货/已返回」列：' + ($d9c.cols -join '/')) }
  if (-not $d9c.noOrder) { Ok '无单退货台账不含「关联加工单」列（该叶子恒为未关联，让宽给进度列）' } else { Bad '无单退货台账不应有「关联加工单」列' }
  if ([int]$d9c.btn -eq 1) { Ok '无单退货叶子有唯一「新增」入口' } else { Bad ('无单退货叶子「新增」按钮数应为 1：' + $d9c.btn) }
  # 2026-09-27（用户口径）：新增无单加工退货**由弹窗改独立页面**（含 BOM 快照自动解析/可换版本）
  $clicked9 = EvalJs "(()=>{const b=[...document.querySelectorAll('button')].filter(x=>x.getClientRects().length>0).find(x=>(x.innerText||'').trim()==='新增');if(!b)return 'no-btn';b.click();return 'clicked'})()"
  Start-Sleep -Milliseconds 2200
  $d9d = ReadJson "(()=>{const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item__label')].filter(vis).map(e=>(e.innerText||'').trim());const dialogs=[...document.querySelectorAll('.el-dialog')].filter(vis).length;return JSON.stringify({p:location.pathname,labels:items,dialogs:dialogs});})()" '新增无单加工退货页'
  Write-Output ('点击新增 = ' + $clicked9 + ' ；跳转 = ' + $d9d.p + ' ；表单项 = ' + ($d9d.labels -join '/'))
  if ($d9d.p -eq '/outsource/return-order/unlinked/add') { Ok '「新增」进入独立页面 /outsource/return-order/unlinked/add' }
  else { Bad ('「新增」未进入独立页面：' + $d9d.p) }
  if ([int]$d9d.dialogs -eq 0) { Ok '该页不再是弹窗形式（无 el-dialog）' } else { Bad ('仍出现弹窗 form（dialogs=' + $d9d.dialogs + '）') }
  if (($d9d.labels -join ',') -match 'BOM') { Ok '该页含「BOM 快照」字段（建单时解析/可换版本）' } else { Bad ('该页缺「BOM 快照」字段：' + ($d9d.labels -join '/')) }
  # ⚠️ 上面点过「新增」会跳到独立页 ⇒ 必须回到无单退货叶子，否则紧随其后的「台账表宽度」断言（沿用上一页）会测到空页
  OpenFresh "$base/outsource/return-order/unlinked"
  # ⚠️ 顶部页签会保留「新增无单加工退货」这个标题（localStorage 持久化），而 §⑨ 的「成品收货页不得出现
  #   『无单加工退货』区块」是**body 文本**断言 ⇒ 会被这个残留页签误判成 FAIL。清掉页签记忆即可（幂等）。
  EvalJs "localStorage.removeItem('beichen_tabs');'ok'" | Out-Null
}
# 台账表「一行显示完、不横向滑动」（与其它列表同一家规）
$w9 = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-table')].filter(x=>x.getClientRects().length>0)[0];const ths=[...document.querySelectorAll('.el-table__header th')];const sum=ths.reduce((s,x)=>s+x.offsetWidth,0);const box=t?t.clientWidth:0;const sc=t?t.classList.contains('el-table--scrollable-x'):true;const w=document.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');return JSON.stringify({sum:sum,box:box,sc:sc,wrapOver:(w?w.scrollWidth>w.clientWidth:true),cols:ths.length});})()" '台账表宽度'
if ($w9) {
  Write-Output ('加工退货台账 列数=' + $w9.cols + ' 列宽合计=' + $w9.sum + ' 容器=' + $w9.box)
  if (-not $w9.sc -and -not $w9.wrapOver) { Ok '加工退货台账无横向滚动（一行显示完）' }
  else { Bad ('加工退货台账出现横向滚动条（列宽合计 ' + $w9.sum + ' vs 容器 ' + $w9.box + '）') }
}
$d10 = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({defect:b.includes('新增加工退货')});})()" '加工退货页·加工退货页签'
if ($d10) {
  # 「新增加工退货」= 上一代独立退货单的新增入口：已停止新增（存量单据走详情页 URL 审核/作废）
  # ⚠️ 不能用「新增加工退货」去误伤本页的「新增无单加工退货」—— 后者不含该子串，故断言成立
  if (-not $d10.defect) { Ok '加工退货页签已无「新增加工退货」（独立单据停止新增）' } else { Bad '仍出现「新增加工退货」按钮（应已移除）' }
}
# 客户售后（原成品维修退货独立叶子；**2026-10-03 三级菜单合并**后该地址重定向到合并页 ?tab=repair）⇒
#   断言：落到合并页且「客户售后」TAB 为选中态 + 唯一「新增」入口。
OpenFresh "$base/outsource/return-order/repair"
$d10b = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());const act=[...document.querySelectorAll('.el-tabs__item')].filter(x=>x.getClientRects().length>0&&x.classList.contains('is-active')).map(x=>x.innerText.trim());return JSON.stringify({repair:b.filter(x=>x==='新增').length,path:location.pathname,search:location.search,active:act});})()" '客户售后 TAB（旧成品维修退货叶子地址）'
if ($d10b) {
  Write-Output ('客户售后 TAB 落点 = ' + $d10b.path + $d10b.search + ' ；选中页签 = ' + ($d10b.active -join '/') + ' ；新增按钮=' + $d10b.repair)
  if (($d10b.path -eq '/outsource/return-order') -and ($d10b.search -match 'tab=repair') -and (($d10b.active -join ',') -match '客户售后')) { Ok '旧维修退货地址 /outsource/return-order/repair 重定向到合并页的「客户售后」TAB' }
  else { Bad ('旧维修退货地址落点不符（期望 /outsource/return-order?tab=repair 且选中 客户售后）：' + $d10b.path + $d10b.search + ' active=' + ($d10b.active -join '/')) }
  if ([int]$d10b.repair -eq 1) { Ok '客户售后 TAB 有唯一「新增」入口' } else { Bad ('客户售后 TAB「新增」按钮数应为 1：' + $d10b.repair) }
}

# ⑩ 「结单 / 反结单」端到端（2026-09-27 用户口径「E 也要做」；**2026-09-29 用户口径：结单/反结单统一收到
#    「物料收退详情」** ⇒ 本段从"列表行内点反结单"改为"进收退详情点"）：造一张**已结单**的物料订单（SQL fixture）
#    → 先在 物料收退 → 已结单 页签断言「结单时间」列渲染了结单人 → 进该单的收退详情断言工具栏形态
#    （已结单 ⇒ 有「反结单」，无「结单」/「新增收货」）→ 点「反结单」→ 弹窗确认 → 断言：
#    ① 库里回到 RECEIVING（可继续收货）；② finish_time 清空；③ 结单人清空；④ 工具栏翻面（有「结单」、无「反结单」）；
#    ⑤ 该行从「已结单」页签消失（列表刷新有效）。⚠️ 必须在临时单上做 —— 反结单会把单拉回生产中，
#    动真实单会影响其它脚本的口径（API 侧的分支覆盖见 verify-material-order-reopen.ps1）。
$roCode = 'MWO-VRO-UI-' + (Get-Date).ToString('HHmmss')
$roSup = SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1'
$roMat = SqlOne 'SELECT id FROM outsource_material ORDER BY id LIMIT 1'
$roMt = SqlOne 'SELECT id FROM material_type ORDER BY id LIMIT 1'
if ($roSup -ne '' -and $roMat -ne '' -and $roMt -ne '') {
  # 结单人盖章 'verify' ⇒ 先断言它渲染在「结单时间」列，再断言反结单把它清空（2026-09-27 用户口径「把结单人做了」）
  SqlExec ("INSERT INTO outsource_material_order (code, supplier_id, order_type, status, remark, company_id, finish_time, finisher_id, finisher_name, auditor_id, auditor_name, deleted) VALUES ('" + $roCode + "', " + $roSup + ", 'PURCHASE', 'FINISHED', 'verify-delivery-menu reopen fixture', 1, NOW(), 1, 'verify', 1, 'verify', 0);")
  $roId = SqlOne ("SELECT id FROM outsource_material_order WHERE code='" + $roCode + "'")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, material_type_id, unit, order_quantity, received_quantity, company_id, deleted) VALUES (" + $roId + ", " + $roMat + ", " + $roMt + ", 'PCS', 100, 0, 1, 0);")
  Write-Output ('reopen fixture: id=' + $roId + ' code=' + $roCode + '（已结单，无收货记录 ⇒ 反结单应回 生产中）')
  OpenFresh "$base/outsource/material-order/delivery"
  EvalJs "(()=>{const ts=[...document.querySelectorAll('.page-list .el-tabs__item')];const t=ts.find(x=>(x.innerText||'').indexOf('已结单')>=0);if(t)t.click();return 'ok'})()" | Out-Null
  Start-Sleep -Milliseconds 1600
  $r10d = (EvalJs "(()=>{const rows=[...document.querySelectorAll('.el-table__body tbody tr')];const tr=rows.find(x=>(x.innerText||'').indexOf('$roCode')>=0);if(!tr)return 'norow';return String((tr.innerText||'').indexOf('verify')>=0)})()") -replace '"', ''
  if ($r10d.Trim() -eq 'true') { Ok '已结单页签的「结单时间」列渲染了结单人（第二行小字）' } else { Bad ('结单人未渲染：' + $r10d) }
  # 2026-09-29：反结单入口已从列表行内移到「物料收退详情」工具栏 ⇒ 进详情页操作
  OpenFresh "$base/outsource/material-order/delivery/$roId"
  $r10e = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({reopen:b.includes('反结单'),fin:b.includes('结单'),recv:b.includes('新增收货')});})()" '物料收退详情（已结单）'
  if ($r10e) {
    if ($r10e.reopen) { Ok '物料收退详情（已结单）工具栏有「反结单」' } else { Bad '物料收退详情（已结单）缺「反结单」' }
    if (-not $r10e.fin) { Ok '物料收退详情（已结单）无「结单」（已结单是终态，不再提供）' } else { Bad '物料收退详情（已结单）仍出现「结单」' }
    if (-not $r10e.recv) { Ok '物料收退详情（已结单）无「新增收货」（后端拒绝已结单收货）' } else { Bad '物料收退详情（已结单）仍出现「新增收货」' }
  }
  $r10a = (EvalJs "(()=>{for(const b of document.querySelectorAll('button')){if((b.innerText||'').trim()==='反结单'){b.click();return 'clicked'}}return 'nobtn'})()") -replace '"', ''
  $r10a = $r10a.Trim()
  if ($r10a -eq 'clicked') { Ok ('物料收退详情 点「反结单」(' + $r10a + ')') } else { Bad ('反结单 按钮未点到：' + $r10a) }
  Start-Sleep -Milliseconds 1200
  $r10b = (EvalJs "(()=>{const bs=[...document.querySelectorAll('.el-message-box__btns button')];const t=bs.find(x=>(x.innerText||'').indexOf('确定')>=0);if(!t)return 'noconfirm';t.click();return 'confirmed'})()") -replace '"', ''
  $r10b = $r10b.Trim()
  if ($r10b -eq 'confirmed') { Ok ('confirm dialog accepted (' + $r10b + ')') } else { Bad ('确认弹窗未点到：' + $r10b) }
  # 反结单后页面会重查列表（loadData + loadCounts 两个请求）—— 等足 3s，别用"刚好"的时间判列表没刷新
  Start-Sleep -Milliseconds 3000
  $roSt = SqlOne ("SELECT status FROM outsource_material_order WHERE id=" + $roId)
  if ($roSt -eq 'RECEIVING') { Ok ('UI 反结单 put the order back to RECEIVING (got ' + $roSt + ')') } else { Bad ('UI 反结单 后状态应为 RECEIVING，实际 ' + $roSt) }
  $roFt = SqlOne ("SELECT IFNULL(finish_time,'NULL') FROM outsource_material_order WHERE id=" + $roId)
  if ($roFt -eq 'NULL') { Ok 'UI 反结单 cleared finish_time' } else { Bad ('UI 反结单 后 finish_time 未清空：' + $roFt) }
  $roFn = SqlOne ("SELECT IFNULL(finisher_name,'NULL') FROM outsource_material_order WHERE id=" + $roId)
  if ($roFn -eq 'NULL') { Ok 'UI 反结单 cleared 结单人（与成品侧反结单清空口径一致）' } else { Bad ('UI 反结单 后结单人未清空：' + $roFn) }
  # 工具栏翻面：回到生产中 ⇒ 有「结单」、无「反结单」（页面 loadAll 后重算 canFinish/canReopen）
  $r10f = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({reopen:b.includes('反结单'),fin:b.includes('结单'),recv:b.includes('新增收货')});})()" '物料收退详情（反结单后）'
  if ($r10f) {
    if ($r10f.fin -and -not $r10f.reopen) { Ok '反结单后详情页工具栏翻面：有「结单」、无「反结单」' } else { Bad ('反结单后工具栏未翻面：' + ($r10f | ConvertTo-Json -Compress)) }
  }
  # 列表复核：该单已离开「已结单」页签（重新打开列表 + 切页签再数）
  OpenFresh "$base/outsource/material-order/delivery"
  EvalJs "(()=>{const ts=[...document.querySelectorAll('.page-list .el-tabs__item')];const t=ts.find(x=>(x.innerText||'').indexOf('已结单')>=0);if(t)t.click();return 'ok'})()" | Out-Null
  Start-Sleep -Milliseconds 1800
  $r10c = EvalJs "(()=>{const rows=[...document.querySelectorAll('.el-table__body tbody tr')].map(x=>(x.innerText||'').replace(/\s+/g,' ').trim());return JSON.stringify({mine:rows.filter(x=>x.indexOf('$roCode')>=0).length,n:rows.length,rows:rows})})()" -replace '"', ''
  Write-Output ('  after-reopen table = ' + $r10c)
  if ($r10c -notmatch ("mine:1")) { Ok 'the reopened order left the 已结单 TAB (list refreshed)' } else { Bad '反结单 后该行仍在「已结单」页签（列表未刷新？）' }
  SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=" + $roId + ";")
  SqlExec ("DELETE FROM outsource_material_order WHERE id=" + $roId + ";")
  if ((SqlOne ("SELECT COUNT(*) FROM outsource_material_order WHERE code='" + $roCode + "'")) -eq '0') { Ok 'reopen fixture cleaned up' } else { Bad '反结单 fixture 未清理干净' }
} else { Write-Output '（缺主数据，跳过 ⑩ 反结单端到端）' }

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 加工收退/物料收退独立菜单与一步收货均正常' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
