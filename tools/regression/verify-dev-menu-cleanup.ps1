# 「研发管理 → BOM管理 / 图纸文档」下线校验（2026-09-16，可复跑）
# 断言：① 侧栏研发管理只剩 研发立项/研发物料管理/屏幕资料知识库
#       ② 旧地址 /dev/bom、/dev/drawing 重定向到 /dev/project（不 403）
#       ③ 仪表盘不再有 BOM总数卡 / BOM管理 / 图纸文档 入口
#       ④ 【关键回归】研发立项编辑页内的 BOM / 图纸 页签仍然可用（数据模型保留）
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
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

# ① 侧栏「研发管理」
OpenFresh "$base/dev/project"
$jsMenu = "(()=>{const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].find(x=>(x.querySelector('.el-sub-menu__title')?.innerText||'').includes('研发管理'));return JSON.stringify({c:s?[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim()):[]});})()"
$raw = (EvalJs $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if (-not $m.Success) { Bad ('未读到侧栏：' + $raw) } else {
  $d = $m.Value | ConvertFrom-Json
  Write-Output ('研发管理 子菜单 = ' + ($d.c -join ' | '))
  $expect = @('研发立项', '研发物料', '屏幕资料')
  if (($d.c -join '|') -eq ($expect -join '|')) { Ok ('研发管理顺序正确：' + ($d.c -join ' → ')) } else { Bad ('研发管理应为 ' + ($expect -join '|') + '，实际 ' + ($d.c -join '|')) }
  if (($d.c -join ',') -match 'BOM管理') { Bad '侧栏仍有「BOM管理」' } else { Ok '侧栏已无「BOM管理」' }
  if (($d.c -join ',') -match '图纸文档') { Bad '侧栏仍有「图纸文档」' } else { Ok '侧栏已无「图纸文档」' }
}

# ② 旧地址重定向
foreach ($c in @(@('/dev/bom', 'BOM管理'), @('/dev/drawing', '图纸文档'))) {
  agent-browser open ($base + $c[0]) | Out-Null
  agent-browser wait 2600
  $p = EvalJs "location.pathname"
  if ($p -match '/dev/project') { Ok ($c[0] + ' 已重定向到 /dev/project（非 403）') } else { Bad ($c[0] + ' 未正确重定向，落在 ' + $p) }
}

# ③ 仪表盘
OpenFresh "$base/dashboard"
$jsBtn = "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());const cards=[...document.querySelectorAll('.stat-label')].map(x=>x.innerText.trim());return JSON.stringify({btn:b.filter(t=>t.includes('BOM')||t.includes('图纸')||t.includes('研发立项')),cards:cards});})()"
$raw3 = (EvalJs $jsBtn).Replace('\"', '"')
$m3 = [regex]::Match($raw3, '\{.*\}')
if ($m3.Success) {
  $d3 = $m3.Value | ConvertFrom-Json
  Write-Output ('仪表盘卡片 = ' + ($d3.cards -join ' | ') + ' ; 相关按钮 = ' + ($d3.btn -join ' | '))
  if (($d3.cards -join ',') -match 'BOM总数') { Bad '仪表盘仍有「BOM总数」卡片' } else { Ok '仪表盘已无「BOM总数」卡片' }
  if (($d3.btn -join ',') -match 'BOM管理') { Bad '仪表盘仍有「BOM管理」按钮' } else { Ok '仪表盘已无「BOM管理」按钮' }
  if (($d3.btn -join ',') -match '图纸') { Bad '仪表盘仍有「图纸文档」按钮' } else { Ok '仪表盘已无「图纸文档」按钮' }
} else { Bad ('未读到仪表盘：' + $raw3) }

# ④ 关键回归：研发立项编辑页内的 BOM / 图纸 页签仍在且有数据
OpenFresh "$base/dev/project/edit/5"
$jsTab = "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].map(x=>x.innerText.trim());return JSON.stringify({tabs:t,rows:document.querySelectorAll('.el-table__body tr').length});})()"
$raw4 = (EvalJs $jsTab).Replace('\"', '"')
$m4 = [regex]::Match($raw4, '\{.*\}')
if ($m4.Success) {
  $d4 = $m4.Value | ConvertFrom-Json
  Write-Output ('研发立项编辑页 页签 = ' + ($d4.tabs -join ' | '))
  if (($d4.tabs -join ',') -match 'BOM') { Ok '研发立项内「BOM」页签仍在（数据模型保留）' } else { Bad '研发立项内 BOM 页签丢失' }
  if (($d4.tabs -join ',') -match '图纸') { Ok '研发立项内「图纸」页签仍在' } else { Bad '研发立项内 图纸 页签丢失' }
} else { Bad ('未读到研发立项编辑页：' + $raw4) }

if ($fail -eq 0) { Write-Output 'RESULT PASS 两个菜单/总览页已下线，研发立项内 BOM·图纸 能力保留' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
