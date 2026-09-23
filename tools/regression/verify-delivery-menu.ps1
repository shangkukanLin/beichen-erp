# 「成品收货 / 物料收货」独立菜单校验（2026-09-16 建立，2026-09-18 改为以库为准，可复跑）
# 背景：原「加工订单详情 → 交货管理」「物料订单详情 → 交货管理」页签移出为委外加工下的两个子菜单
# 断言：① 侧栏委外加工子菜单 = sys_menu(parent_id=4, visible=1) 按 sort_order
#          （2026-09-17 用户定稿：加工订单 → 成品收货 → 加工退货 → 物料订单 → 物料收货 → 物料退货；409 供应商管理已下线）
#       ② /outsource/order/delivery 直达不 403，只列正在加工（PRODUCING）的加工单
#       ③ 行内「收货」→ 进入 /outsource/order/delivery/:id 且**自动弹出新增收货弹窗**（一步收货）
#       ④ 收货详细页有 汇总卡 / 收货记录区块 / 返回列表
#       ⑤ 加工单详情页签已无「交货管理」，且**不再有**「成品收货」跳转按钮
#          （2026-09-21 用户口径：收货统一从「成品收货」菜单进）
#       ⑥ /outsource/material-order/delivery 直达不 403（收货中订单可能为 0 行 ⇒ 只验渲染/不报错，
#          并**负向守卫**：行内操作文案不得再出现「收料」—— 2026-09-21 用户口径已改为「收货」）
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
function ReadJson($js, $want) {
  $raw = (EvalJs $js).Replace('\"', '"')
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

# ② 成品收货列表：直达不 403 + 只出正在加工的加工单
OpenFresh "$base/outsource/order/delivery"
$p2 = EvalJs "location.pathname"
if ($p2 -match '/login' -or $p2 -match '403') { Bad ('/outsource/order/delivery 未正常进入，落在 ' + $p2) }
else { Ok '/outsource/order/delivery 直达正常（非 403）' }
$d2 = ReadJson "(()=>{const rows=[...document.querySelectorAll('.el-table__body tbody tr')];const t=document.body.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({n:rows.length,prod:t.includes('生产中')==true,pending:t.includes('待审核')==true,prog:t.includes('收货进度')==true,btn:t.includes('收货')==true,ret:b.some(x=>x==='退货'),cls:b.some(x=>x==='结单')});})()" '成品收货列表'
if ($d2) {
  Write-Output ('成品收货列表行数 = ' + $d2.n)
  if ($d2.n -ge 1) { Ok ('列表有数据（' + $d2.n + ' 行）') } else { Bad '列表无数据（库中应有 1 张 PRODUCING 加工单）' }
  if ($d2.prog -and $d2.btn) { Ok '列表含 收货进度 列与 收货 按钮' } else { Bad '列表缺少 收货进度 列或 收货 按钮' }
  if ($d2.prod -and -not $d2.pending) { Ok '列表只含正在加工（生产中）的加工单' } else { Bad ('列表含非生产中订单：prod=' + $d2.prod + ' pending=' + $d2.pending) }
  # 2026-09-21（用户口径「成品收货只留退不良、退回走红冲收货」）：列表页**不得**再有「退货」入口。
  # 断言按**按钮**取值而不是 body 文本 —— 侧栏还有「委外加工退货」菜单，用文本判定必然假通过。
  if (-not $d2.ret) { Ok '列表页已无「退货」入口（退回走红冲收货）' } else { Bad '列表页仍出现「退货」按钮（应已移除）' }
  # 2026-09-21（用户口径「结单按钮放到成品收货里」）：列表行内必须有「结单」入口（仅生产中的单才在列表里）
  if ($d2.cls) { Ok '列表行内有「结单」入口（结单按钮已从加工单详情搬到成品收货）' } else { Bad '列表行内缺少「结单」入口' }
}

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
  # 2026-09-21（用户口径「结单按钮放到成品收货里」）：本页（成品收货的"收货详细"）必须有「结单」入口
  if ($d4.cls) { Ok '收货详细页有「结单」入口（跳结单报表页）' } else { Bad '收货详细页缺少「结单」入口' }
}

# ⑤ 加工单详情：页签已无「交货管理」，且**不再有**「成品收货」跳转按钮
#    2026-09-21（用户口径「委外加工单详情页面里面的成品收货按钮不要了。在成品收货里面收货就行」）：
#    收货统一从「委外加工 → 成品收货」菜单进，故这里**反过来**断言"按钮必须不存在"。
$oid = if ($d3 -and $d3.p -match '(\d+)$') { $Matches[1] } else { '' }
if ($oid) {
  OpenFresh "$base/outsource/order/detail/$oid"
  $d5 = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim());const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({tabs:t,hasRecv:(b.includes('成品收货')==true),hasDefect:(b.includes('退不良')==true),hasMfg:(b.includes('加工退货')==true||b.includes('不良退货')==true),cls:b.some(x=>x==='结单')});})()" '加工单详情'
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

# ⑥ 物料收货页：直达不 403 且渲染正常（当前库中收货中订单 0 条属正常）
OpenFresh "$base/outsource/material-order/delivery"
$p6 = EvalJs "location.pathname"
if ($p6 -match '/login' -or $p6 -match '403') { Bad ('/outsource/material-order/delivery 未正常进入，落在 ' + $p6) }
else { Ok '/outsource/material-order/delivery 直达正常（非 403）' }
$d6 = ReadJson "(()=>{const t=document.body.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({title:(t.includes('物料收货（收货中的物料订单）')==true),err:(t.includes('加载待收货订单失败')==true),rows:document.querySelectorAll('.el-table__body tbody tr').length,oldrecv:(t.includes('收料')==true),btn:b.some(x=>x==='收料')});})()" '物料收货列表'
if ($d6) {
  if ($d6.title) { Ok '物料收货页标题渲染正常' } else { Bad '物料收货页标题未渲染（页面可能报错）' }
  if (-not $d6.err) { Ok '物料收货页无加载错误提示' } else { Bad '物料收货页出现「加载待收货订单失败」' }
  Write-Output ('物料收货列表行数 = ' + $d6.rows + '（库中可能 0 行"收货中"订单 ⇒ 0 行属正常）')
  # 2026-09-21（用户口径「操作文案从收料/退料改成收货/退货」）：行内按钮「收料」必须已改为「收货」。
  # ⚠️ 库里可能没有"收货中"的物料订单（0 行 ⇒ 按钮不渲染）⇒ 这条是**负向守卫**（防改回去），0 行时天然通过。
  if (-not $d6.oldrecv -and -not $d6.btn) { Ok '物料收货列表页已无「收料」（行内按钮已改为「收货」）' }
  else { Bad ('物料收货列表页仍出现「收料」：文本=' + $d6.oldrecv + ' 按钮=' + $d6.btn) }
}

# ⑦ 首页「委外加工」TAB 快捷入口含 成品收货 / 物料收货
OpenFresh "$base/dashboard"
# 非活动页签在 DOM 中但不可见（offsetParent=null），必须先切到「委外加工」TAB 再数按钮
$sw = EvalJs "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='\u59d4\u5916\u52a0\u5de5');if(!t)return 'nf';t.click();return 'ok'})()"
Write-Output ('切换委外加工 TAB = ' + $sw)
# 2026-09-17：菜单缓存被清后仪表盘按菜单树异步渲染快捷入口，1.8s 偶发读不到 → 放宽到 3s（消除假失败）
agent-browser wait 3000
$d7 = ReadJson "(()=>{const pane=document.querySelector('#pane-outsource')||document;const b=[...pane.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({d:(b.includes('\u6210\u54c1\u6536\u8d27')==true),m:(b.includes('\u7269\u6599\u6536\u8d27')==true),all:b.filter(x=>x)});})()" '仪表盘'
if ($d7) {
  Write-Output ('委外加工 TAB 按钮 = ' + ($d7.all -join ' | '))
  if ($d7.d) { Ok '首页委外加工 TAB 有「成品收货」快捷入口' } else { Bad '首页委外加工 TAB 缺少「成品收货」快捷入口' }
  if ($d7.m) { Ok '首页委外加工 TAB 有「物料收货」快捷入口' } else { Bad '首页委外加工 TAB 缺少「物料收货」快捷入口' }
}

# ⑧ 两个收货列表「一行显示完、不横向滑动」（2026-09-16 用户要求）
foreach ($u in @("$base/outsource/order/delivery", "$base/outsource/material-order/delivery")) {
  OpenFresh $u
  $d8 = ReadJson "(()=>{const t=document.querySelector('.el-table');const ths=[...document.querySelectorAll('.el-table__header th')];const sum=ths.reduce((s,x)=>s+x.offsetWidth,0);const box=t?t.clientWidth:0;const sc=t?t.classList.contains('el-table--scrollable-x'):true;const w=document.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');return JSON.stringify({sum:sum,box:box,sc:sc,wrapOver:(w?w.scrollWidth>w.clientWidth:true),cols:ths.length});})()" '表格宽度'
  if ($d8) {
    Write-Output ($u + ' 列数=' + $d8.cols + ' 列宽合计=' + $d8.sum + ' 容器=' + $d8.box)
    if (-not $d8.sc -and -not $d8.wrapOver) { Ok ($u + ' 无横向滚动（一行显示完）') } else { Bad ($u + ' 出现横向滚动条') }
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
#   列表瘦身为 7 列，「收货仓库 / 物流单号 / 备注 / 附件」移入行内「详情」抽屉 ⇒ 断言列数 + 详情入口，
#   避免日后有人把列又加回列表把横向滚动带回来。（⑧b 已开着收货详细页，此处复用同一页。）
$d8c = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-table')].filter(x=>x.getClientRects().length>0)[0];if(!t)return JSON.stringify({ok:false});const ths=[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim());const tr=t.querySelector('.el-table__body tbody tr');const ops=tr?[...tr.children][[...tr.children].length-1].innerText:'';return JSON.stringify({ok:true,cols:ths.length,hasDetail:ops.indexOf('\u8BE6\u60C5')>=0});})()" '收货记录表结构'
if ($d8c -and $d8c.ok) {
  Write-Output ('收货记录表 列数=' + $d8c.cols)
  if ([int]$d8c.cols -eq 7) { Ok '收货记录表已瘦身为 7 列（仓库/物流单号/备注/附件移入详情抽屉）' } else { Bad ('收货记录表列数=' + $d8c.cols + '，应为 7') }
  if ($d8c.hasDetail) { Ok '收货记录操作列有「详情」入口' } else { Bad '收货记录操作列缺少「详情」入口' }
}

# ⑨ 成品收货页**只做收货**（2026-09-21 用户口径「成品收货里面一个页面还有两个列表很别扭」）：
#    原先挂在本页下方的「无单加工退货」区块已迁到「加工退货」页 ⇒ 本页不得再出现该区块/退货按钮。
#    ⚠️ 断言一律按**按钮文本**取值：侧栏有「加工退货」菜单，用 body 文本判定必然假通过。
OpenFresh "$base/outsource/order/delivery"
$d9 = ReadJson "(()=>{const t=document.body.innerText;const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({title:t.includes('无单加工退货'),btn:b.includes('新增无单加工退货'),ret:b.some(x=>x==='退货')});})()" '成品收货列表页'
if ($d9) {
  if (-not $d9.title) { Ok '成品收货列表页已无「无单加工退货」区块（退回统一到加工退货页）' } else { Bad '成品收货列表页仍有「无单加工退货」区块（应已迁走）' }
  if (-not $d9.btn) { Ok '成品收货列表页已无「新增无单加工退货」按钮' } else { Bad '成品收货列表页仍有「新增无单加工退货」按钮' }
  if (-not $d9.ret) { Ok '成品收货列表页已无「退货」入口' } else { Bad '成品收货列表页仍出现「退货」按钮' }
}

# ⑨b 加工退货页「加工退货」页签 = **有单 + 无单一张台账表**（2026-09-21 用户口径）：
#     行来自 outsource_order_delivery 的 DEFECT_RETURN 记录，用「关联加工单」列区分有单/无单；
#     本页可新增"无单"那条（有单的退回仍在该加工单的收货详细页发起）。
#     该台账的业务口径（扣成品/还料/冲应付、可反审核、草稿可删）由 verify-no-order-return.ps1 覆盖（API 级）
OpenFresh "$base/outsource/return-order"
$d9b = ReadJson "(()=>{const t=[...document.querySelectorAll('.el-table')].filter(x=>x.getClientRects().length>0)[0];const ths=t?[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim()):[];const trs=t?[...t.querySelectorAll('.el-table__body tbody tr')]:[];const tr=trs.length?trs[0]:null;const ops=tr?[...tr.children][[...tr.children].length-1].innerText:'';const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({tabs:[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim()),cols:ths,rows:trs.length,w:ths.includes('关联加工单'),qty:ths.includes('退货数量'),btn:b.includes('新增无单加工退货'),det:b.includes('详情'),opsHasDet:(ops.indexOf('\u8BE6\u60C5')>=0)});})()" '加工退货页·加工退货台账'
if ($d9b) {
  Write-Output ('加工退货页签 = ' + ($d9b.tabs -join ' | ') + ' ；台账列 = ' + ($d9b.cols -join '/'))
  if ($d9b.w -and $d9b.qty) { Ok '加工退货台账=一张表：含「关联加工单」（有单显示单号/无单显示未关联）与「退货数量」列' }
  else { Bad ('加工退货台账列不符（缺 关联加工单 或 退货数量）：' + ($d9b.cols -join '/')) }
  if ($d9b.btn) { Ok '加工退货页签有「新增无单加工退货」入口（有单的退回在加工单收货详细页）' } else { Bad '加工退货页签缺少「新增无单加工退货」入口' }
  # 2026-09-21（用户口径「加工退货页面的列表也应该有详情」）：台账行内必须有「详情」入口
  # ⚠️ 台账可能为空（新库）⇒ 无数据行时只提示跳过，避免假 FAIL
  if ($d9b.opsHasDet) { Ok '加工退货台账行内操作列有「详情」入口' }
  elseif ([int]$d9b.rows -eq 0) { Write-Output ('（台账暂无数据行，跳过「详情」入口断言；页面上有「详情」按钮=' + $d9b.det + '）') }
  else { Bad ('加工退货台账操作列缺少「详情」入口（行内操作=' + $d9b.opsHasDet + '）') }
  # 2026-09-21（用户口径「历史加工退货单不要了」）：上一代独立退货单**不再单独列页签** ⇒ 本页只应有两个页签
  if (($d9b.tabs -join ',') -eq '加工退货,维修退货') { Ok '加工退货页只有两个页签（与页面同名 + 维修退货；「历史加工退货单」页签已按口径撤掉）' }
  else { Bad ('加工退货页签不符（期望 加工退货/维修退货）：' + ($d9b.tabs -join '/')) }
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
# 切到「维修退货」页签：独立维修退货单的新增入口应出现（维修退货不在本次统一范围）
EvalJs "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='维修退货');if(t)t.click();return 'clicked';})()" | Out-Null
agent-browser wait 1500
$d10b = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({repair:b.includes('新增维修退货')});})()" '加工退货页·维修退货页签'
if ($d10b) {
  if ($d10b.repair) { Ok '维修退货页签保留「新增维修退货」（维修退货不在本次统一范围）' } else { Bad '维修退货页签缺少「新增维修退货」按钮' }
}

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 成品收货/物料收货独立菜单与一步收货均正常' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
