# 模版管理菜单与页面校验（2026-09-15）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-template-menu.ps1
# 校验点：① 侧栏「基础数据 → 模版管理」存在且旧的两个入口消失 ② /template 两个 TAB 各有数据
#         ③ ?tab=contract 深链直达 ④ 旧地址 /dev/phase-template、/outsource/contract-template 正确重定向
#         ⑤ 首页两个快捷按钮指向新页对应 TAB
# 注意：每次先清 localStorage 的菜单缓存 —— 后端新增菜单后，浏览器里的旧缓存会让新路径被判 403（见文档 §12.73）
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }

function LoginFlow() {
  Write-Output '（会话失效，先登录）'
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
  agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
  agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
  agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
  agent-browser wait 3500
}

function OpenFresh($url) {
  EvalJs "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs "'p=' + location.pathname") -match '/login') { LoginFlow; agent-browser open $url | Out-Null; agent-browser wait 3000 }
}

OpenFresh "$base/template"

# ① 侧栏结构：基础数据 下的子项 + 是否还有旧入口
$jsMenu = "(()=>{const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:s.querySelector('.el-sub-menu__title')?.innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));const titles=[...document.querySelectorAll('.el-menu .el-sub-menu__title')].map(t=>t.innerText.trim());return JSON.stringify({groups:g,titles:titles});})()"
$raw = (EvalJs $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  $d = $m.Value | ConvertFrom-Json
  $base1 = ($d.groups | Where-Object { $_.t -like '*基础数据*' } | Select-Object -First 1)
  if ($base1) {
    Write-Output ('基础数据 子菜单 = ' + ($base1.c -join ' | '))
    if ($base1.c -contains '模版管理') { Ok '侧栏「基础数据 → 模版管理」存在' } else { Bad '侧栏缺少「模版管理」' }
    if ($base1.c -contains '阶段模板管理') { Bad '基础数据下仍残留旧入口「阶段模板管理」' } else { Ok '基础数据下旧入口「阶段模板管理」已下线' }
    # 顺序断言（2026-09-22 改为**按库取基准**）：原先写死 8 项，用户方案 A 重排（11 项）+ 702/404/410 迁入后必然假红；
    # 改为读 sys_menu(parent_id=2) 的顺序与侧栏逐项比对 —— 菜单怎么调本脚本都不用再改。
    $rawDb = & 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe' --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=2 AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
    $expect = @((@($rawDb) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
    if ((@($base1.c) -join '|') -eq ($expect -join '|')) { Ok ('基础数据顺序与库一致：' + ($base1.c -join ' → ')) }
    else { Bad ('基础数据顺序与库不一致；库 ' + ($expect -join '|') + '，实际 ' + ($base1.c -join '|')) }
    if (($base1.c -join ',') -match '物料信息管理') { Ok '「物料信息管理」已在基础数据（紧跟物料类型管理）' } else { Bad '基础数据下缺少「物料信息管理」' }
  } else { Bad '侧栏找不到「基础数据」分组' }
  $os = ($d.groups | Where-Object { $_.t -like '*委外加工*' } | Select-Object -First 1)
  if ($os) {
    Write-Output ('委外加工 子菜单 = ' + ($os.c -join ' | '))
    if ($os.c -contains '加工合同模板') { Bad '委外加工下仍残留旧入口「加工合同模板」' } else { Ok '委外加工下旧入口「加工合同模板」已下线' }
    if ($os.c -contains '物料信息') { Bad '委外加工下仍残留「物料信息」（应已迁出）' } else { Ok '「物料信息」已从委外加工迁出' }
  }
  if ($d.titles -contains '阶段模板管理' -or $d.titles -contains '加工合同模板') { Bad '菜单树里仍有旧菜单标题' } else { Ok '旧菜单标题已从菜单树消失' }
  # 研发管理：首项菜单名（2026-09-16「研发项目」→「研发立项」，仅文案）
  $dev = ($d.groups | Where-Object { $_.t -like '*研发管理*' } | Select-Object -First 1)
  if ($dev) {
    Write-Output ('研发管理 子菜单 = ' + ($dev.c -join ' | '))
    if ($dev.c[0] -eq '研发立项') { Ok '研发管理首项 = 研发立项' } else { Bad ('研发管理首项为 ' + $dev.c[0]) }
  }
} else { Bad ('未能读取侧栏：' + $raw) }

# ② /template 两个 TAB 与数据
$jsTabs = "JSON.stringify({tabs:[...document.querySelectorAll('.el-tabs__item')].map(t=>t.innerText.trim()),rows:[...document.querySelectorAll('.el-tab-pane')].map(p=>p.querySelectorAll('.el-table__body tr').length)})"
$raw2 = (EvalJs $jsTabs).Replace('\"', '"')
$m2 = [regex]::Match($raw2, '\{.*\}')
if ($m2.Success) {
  $d2 = $m2.Value | ConvertFrom-Json
  Write-Output ('页签 = ' + ($d2.tabs -join ' | ') + ' ; 各 TAB 数据行 = ' + ($d2.rows -join ','))
  if (($d2.tabs -join ',') -match '阶段模板管理' -and ($d2.tabs -join ',') -match '加工合同模板') { Ok '两个 TAB（阶段模板管理 / 加工合同模板）均在' } else { Bad 'TAB 文案不符' }
  if (($d2.rows | Measure-Object -Sum).Sum -gt 0) { Ok '页签内表格有数据' } else { Bad '页签内表格无数据' }
} else { Bad ('未能读取 TAB：' + $raw2) }

# ③ 深链 ?tab=contract 直达
agent-browser open "$base/template?tab=contract" | Out-Null
agent-browser wait 2500
$act = EvalJs "document.querySelector('.el-tabs__item.is-active')?.innerText.trim()"
if ($act -match '加工合同模板') { Ok ('深链 ?tab=contract 直达「' + $act + '」') } else { Bad ('深链未生效，激活 TAB = ' + $act) }
$qn = EvalJs "location.search"
if ($qn -match 'tab=contract') { Ok 'URL 保留 tab=contract' } else { Bad ('URL 未保留参数：' + $qn) }

# ④ 旧地址重定向
foreach ($old in @(@('/dev/phase-template', 'phase'), @('/outsource/contract-template', 'contract'))) {
  agent-browser open ($base + $old[0]) | Out-Null
  agent-browser wait 2500
  $p = EvalJs "location.pathname + location.search"
  if ($p -match ('/template' + [regex]::Escape('?tab=' + $old[1]))) { Ok ($old[0] + ' 已重定向到 ' + $p) }
  else { Bad ($old[0] + ' 重定向异常，落在 ' + $p) }
  if ($p -match '/403') { Bad ($old[0] + ' 被权限拦截（403）') }
}

# ⑤ 首页快捷按钮指向新页
OpenFresh "$base/dashboard"
$jsBtn = "(()=>{const b=[...document.querySelectorAll('button')].filter(x=>['阶段模板','加工合同模板'].includes(x.innerText.trim()));return JSON.stringify(b.map(x=>x.innerText.trim()));})()"
$raw3 = (EvalJs $jsBtn).Replace('\"', '"')
Write-Output ('首页快捷按钮 = ' + $raw3)
if ($raw3 -match '阶段模板' -or $raw3 -match '加工合同模板') {
  # 点「阶段模板」→ 应落到 /template?tab=phase
  EvalJs "(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='阶段模板');if(b){b.click();return 'ok'}return 'no'})()" | Out-Null
  agent-browser wait 2500
  $p2 = EvalJs "location.pathname + location.search"
  if ($p2 -match '/template' -and $p2 -match 'tab=phase') { Ok ('首页「阶段模板」按钮跳到 ' + $p2) } else { Bad ('首页按钮跳转异常：' + $p2) }
} else { Write-Output 'WARN 首页未发现这两个快捷按钮（可能该 TAB 未展示，不判失败）' }

if ($fail -eq 0) { Write-Output 'RESULT PASS 模版管理菜单/页面/深链/重定向 全部符合预期' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
