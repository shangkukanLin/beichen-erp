# 「物料仓库」新目录 + 子菜单搬迁 校验（2026-09-16 建立，2026-09-17 改为**从 DB 取基准**，可复跑）
# 断言：① 一级菜单含「物料仓库」、**紧随「委外加工」**且在「成品库存」之前
#          （2026-10-02 用户口径「菜单物料仓库放在委外加工下面」：原断言只比"在成品库存之前"太宽，
#            旧序（销售业务之后）同样满足 ⇒ 现按索引钉死邻接关系）
#       ② 物料仓库子菜单 = sys_menu(parent_id=11, visible=1) 按 sort_order（查询 + 作业单据）
#          （2026-09-22 用户要求：委外仓库 / 自有物料仓 迁入「基础数据」⇒ 本组 7 项变 5 项；
#           同日再新增「物料库存流水」(417) 插第 2 位 ⇒ 6 项。本脚本按库取基准，菜单再调无需改）
#       ②b **用户点定的顺序按 id 钉死**（2026-09-29 新增）：② 只能证明"侧栏 == 库"，钉不住"库是不是用户要的顺序"。
#          2026-09-29 用户口径「物料其他出入库放在物料报损前面」⇒ 413/407 对调（与成品侧
#          verify-returnsort-menu-move.ps1 钉 '706,707,705,712,703,711,704,713' 同范式）。
#       ③ 委外加工子菜单 = sys_menu(parent_id=4, visible=1) 按 sort_order
#          （2026-09-17 用户定稿的书写顺序；2026-09-29 改名后 = 加工收退 / 物料收退；供应商管理下线）
#       ④ 5 个页面直达不 403（路由路径未变 → 白名单不受影响）
#       ⑤ 委外仓库 / 自有物料仓 已归「基础数据」（2026-09-22 新增）
# 说明：②③ 的期望值不写死中文，而是**以库为准**与侧栏渲染比对（菜单改名无需改本脚本）；
#       只有 ②b 这一条故意写死 id 顺序 —— 它锁的是**用户点定的排序口径**。
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$global:fail = 0
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
# 读某父菜单下可见子菜单名称（按 sort_order）
function MenuNames([int]$parentId) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=$parentId AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
# 读某父菜单下可见菜单的路由路径（按 sort_order）
function MenuRoutes([int]$parentId) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT route_path FROM sys_menu WHERE parent_id=$parentId AND visible=1 AND status=1 AND menu_type='menu' ORDER BY sort_order" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
# 按 id 读菜单名（**不写死中文**：菜单只改展示名时本脚本不该变红）
function MenuNameById([int]$id) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE id=$id" 2>$null
  return (@($o) | Select-Object -Skip 1 | Select-Object -First 1 | ForEach-Object { ("$_").Trim() })
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
function ReadJsonLike($js, $want) {
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("未读到 " + $want + "：" + $raw); return $null }
  return ($m.Value | ConvertFrom-Json)
}
function SubMenu($catalog) {
  # 2026-09-29 修复（**既存**缺陷，与本次菜单排序无关）：三级菜单（2026-09-27 起 419「加工退货」/ 423「物料退货」
  #   是目录）在侧栏是**嵌套**渲染 —— 取 li.innerText 会把父项与子叶子拼成一串
  #   （"加工退货关联退货无单退货成品维修退货"）⇒ 与库里父项名比对必然不等，③ 长期 FAIL、且掩盖真实回归。
  #   改为只取**本项标题**：子菜单取 :scope > .el-sub-menu__title，普通项取自身；与 ① 一级菜单探针同口径（取首行）。
  $js = "(()=>{const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].find(x=>(x.querySelector('.el-sub-menu__title')?.innerText||'').includes('$catalog'));const txt=li=>{const t=li.querySelector(':scope > .el-sub-menu__title');return ((t||li).innerText||'').trim().split('\n')[0];};return JSON.stringify({c:s?[...s.querySelectorAll(':scope > .el-menu > li')].map(txt):[]});})()"
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("未读到侧栏 " + $catalog + "：" + $raw); return @() }
  return @((($m.Value | ConvertFrom-Json).c))
}

OpenFresh "$base/dashboard"

# ① 一级菜单顺序
$jsTop = "(()=>{const r=document.querySelector('.el-menu');if(!r)return JSON.stringify({top:[]});const tops=[...r.children].map(li=>{const t=li.querySelector('.el-sub-menu__title');return (t||li).innerText.trim().split('\n')[0];});return JSON.stringify({top:tops});})()"
$rawTop = (EvalJs $jsTop).Replace('\"', '"')
$mt = [regex]::Match($rawTop, '\{.*\}')
if ($mt.Success) {
  $top = @((($mt.Value | ConvertFrom-Json).top))
  Write-Output ('一级菜单 = ' + ($top -join ' | '))
  $iWh = [array]::IndexOf($top, '物料仓库')
  $iOut = [array]::IndexOf($top, '委外加工')
  $iProd = [array]::IndexOf($top, '成品库存')
  if ($iWh -ge 0) { Ok '一级菜单存在「物料仓库」' } else { Bad '一级菜单缺少「物料仓库」' }
  # 2026-10-02 用户口径「菜单物料仓库放在委外加工下面」：把"紧随其后"按索引钉死 ——
  #   只断言"在成品库存之前"太宽（旧序：销售业务之后、成品库存之前，同样满足）⇒ 钉不住用户要的邻接关系。
  if ($iOut -ge 0 -and $iWh -eq ($iOut + 1)) { Ok ('位置正确：「物料仓库」紧随「委外加工」（index ' + $iOut + ' → ' + $iWh + '）') }
  else { Bad ('「物料仓库」未紧随「委外加工」：委外加工 index=' + $iOut + '，物料仓库 index=' + $iWh) }
  if ($iWh -ge 0 -and $iProd -ge 0 -and $iWh -lt $iProd) { Ok ('「物料仓库」(' + $iWh + ') 仍在「成品库存」(' + $iProd + ') 之前') }
  elseif ($iWh -ge 0) { Bad '「物料仓库」位置不在「成品库存」之前' }
} else { Bad ('未读到一级菜单：' + $rawTop) }

# ② 物料仓库子菜单（期望值取自 sys_menu：parent_id=11）
$wh = SubMenu '物料仓库'
$expWh = MenuNames 11
Write-Output ('物料仓库 子菜单 = ' + ($wh -join ' | '))
Write-Output ('物料仓库 DB 期望 = ' + ($expWh -join ' | '))
if (($wh -join '|') -eq ($expWh -join '|')) { Ok ('物料仓库顺序与库一致：' + ($wh -join ' → ')) } else { Bad ('物料仓库应为 ' + ($expWh -join '|') + '，实际 ' + ($wh -join '|')) }

# ②b 2026-09-29 用户口径「物料其他出入库放在物料报损前面」：把用户点定的顺序按 id 钉死
#     （只按库取基准只能证明"侧栏 == 库"，钉不住"库是不是用户要的顺序"）
$dbIds = ((& $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e "SELECT id FROM sys_menu WHERE parent_id=11 AND visible=1 AND status=1 ORDER BY sort_order, id" 2>$null) | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' }) -join ','
Write-Output ('物料仓库 visible ids in order = ' + $dbIds)
if ($dbIds -eq '418,416,417,414,407,413') { Ok 'DB sort_order = 物料移仓→库存详情→库存流水→库存盘点→物料其他出入库→物料报损（2026-09-29 用户口径）' }
else { Bad ('DB sequence mismatch: ' + $dbIds + '（期望 418,416,417,414,407,413）') }

# ⑤ 2026-09-22：委外仓库(404) / 自有物料仓(410) 由「物料仓库」迁入「基础数据」，两边都要验（防只改一半）
#    2026-09-23 改名为「委外仓库管理 / 自有物料仓管理」⇒ 断言不写死中文：名字按 id 从库取、归属按 route_path 判
$expBase = MenuNames 2
$expWhRoutes = MenuRoutes 11
$n404 = MenuNameById 404
$n410 = MenuNameById 410
Write-Output ('基础数据 DB 期望 = ' + ($expBase -join ' | '))
if (($expBase -contains $n404) -and ($expBase -contains $n410)) { Ok ('基础数据 已含 ' + $n404 + ' / ' + $n410) } else { Bad ('基础数据 缺少 ' + $n404 + '/' + $n410 + '：' + ($expBase -join '|')) }
if ((-not ($expWhRoutes -contains '/outsource/warehouse')) -and (-not ($expWhRoutes -contains '/outsource/material-warehouse'))) { Ok ('物料仓库 已不再含 ' + $n404 + ' / ' + $n410 + '（按 route_path）') } else { Bad '物料仓库 仍含 委外仓库/自有物料仓：' + ($expWh -join '|') }

# ③ 委外加工子菜单（期望值取自 sys_menu：parent_id=4；2026-09-17 新顺序，供应商管理已下线）
$ws = SubMenu '委外加工'
$expWs = MenuNames 4
Write-Output ('委外加工 子菜单 = ' + ($ws -join ' | '))
Write-Output ('委外加工 DB 期望 = ' + ($expWs -join ' | '))
if (($ws -join '|') -eq ($expWs -join '|')) { Ok ('委外加工顺序与库一致：' + ($ws -join ' → ')) } else { Bad ('委外加工应为 ' + ($expWs -join '|') + '，实际 ' + ($ws -join '|')) }
# 已迁入「物料仓库」的项不得再出现在「委外加工」（从库里取已迁移项，不再写死）
$leftover = @($expWh | Where-Object { $ws -contains $_ })
if ($leftover.Count -eq 0) { Ok '已迁入「物料仓库」的项不再出现在委外加工' } else { Bad ('委外加工仍残留：' + ($leftover -join '、')) }

# ④ 「物料仓库」下每个菜单页直达不 403（路由从库里取）
$routes = MenuRoutes 11
Write-Output ('物料仓库 路由 = ' + ($routes -join ' | '))
foreach ($p in $routes) {
  agent-browser open ($base + $p) | Out-Null
  agent-browser wait 2200
  $cur = EvalJs "location.pathname"
  if ($cur -match '/403') { Bad ($p + ' 被拦到 403') }
  elseif ($cur -match [regex]::Escape($p)) { Ok ($p + ' 可正常直达（非 403）') }
  else { Bad ($p + ' 落到了 ' + $cur) }
}

# ⑤ 首页 TAB：新增「物料仓库」TAB 且快捷入口已从「委外加工」TAB 迁出
OpenFresh "$base/dashboard"
$d5json = ReadJsonLike "(()=>{const tabs=[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim());
const hit=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='物料仓库');if(hit)hit.click();
return JSON.stringify({tabs:tabs});})()" '首页 TAB'
if ($d5json) {
  Write-Output ('首页 TAB = ' + ($d5json.tabs -join ' | '))
  if (($d5json.tabs -join ',') -match '物料仓库') { Ok '首页存在「物料仓库」TAB' } else { Bad '首页缺少「物料仓库」TAB' }
  agent-browser wait 2200
  # 只数**当前活动 pane** 内的按钮（Element Plus 的非活动 pane 仍留在 DOM 里，全局 querySelectorAll 会数到隐藏项）
  $btns = EvalJs "[...document.querySelectorAll('#pane-materialWarehouse button')].map(x=>x.innerText.trim()).filter(Boolean).join('|')"
  Write-Output ('物料仓库 TAB 按钮 = ' + $btns)
  $exp5 = $expWh   # 期望值 = 库里的物料仓库菜单（不再写死）
  # 2026-09-29：不止"齐全"，还要**逐项同序** —— 该块注释明确承诺"严格 = 本目录子菜单、本块逐项同序"，
  #   但此前只断言存在 ⇒ 本次改 413/407 排序后首页仍留着旧顺序（已同步换位）。取全序列与库期望严格比对。
  #   注意：agent-browser eval 回传的字符串自带双引号（原断言用 -notmatch 逐名匹配所以掩盖了这点）⇒ 先剥引号。
  $got5 = @((($btns -replace '"', '').Trim()) -split '\|' | Where-Object { $_ -ne '' })
  if (($got5 -join '|') -eq ($exp5 -join '|')) { Ok ('物料仓库 TAB 快捷入口与库期望逐项同序（' + $got5.Count + ' 项）') }
  else { Bad ('物料仓库 TAB 顺序不符：实际 ' + ($got5 -join '|') + '，期望 ' + ($exp5 -join '|')) }
  # 切回「委外加工」TAB，确认「物料仓库」的项已不在该 pane 内（名单取库，不写死）
  EvalJs "(()=>{const h=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='委外加工');if(h)h.click();return 'ok';})()" | Out-Null
  agent-browser wait 2200
  $whNamesJs = ($expWh | ForEach-Object { "'" + $_ + "'" }) -join ','
  $btns2 = (EvalJs ("[...document.querySelectorAll('#pane-outsource button')].map(x=>x.innerText.trim()).filter(t=>[" + $whNamesJs + "].includes(t)).join('|')")) -replace '"', ''
  $btns2 = $btns2.Trim()
  Write-Output ('委外加工 TAB 同名列按钮 = [' + $btns2 + ']')
  if ([string]::IsNullOrWhiteSpace($btns2)) { Ok '委外加工 TAB 已无这 5 个快捷入口' } else { Bad ('委外加工 TAB 仍残留：' + $btns2) }
  # 反向核对：物料仓库 pane 在切走后应为空/隐藏（确认按钮确实只属于物料仓库 TAB）
  $btns3 = EvalJs "[...document.querySelectorAll('#pane-materialWarehouse button')].length"
  Write-Output ('物料仓库 pane 按钮数（切走后） = ' + $btns3)
}

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 物料仓库目录 + 首页 TAB 与快捷入口迁移完成，页面可达' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
