# 「BOM表类型 → 物料类型」全链重命名校验（2026-09-15）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-material-type.ps1
# 覆盖：库表/接口/字段（material_type / materialTypeId）改名后 —— 菜单与页面、旧地址重定向、
#       委外物料按类型过滤链路、页面上不再出现「BOM类型」字样
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
  # 清菜单缓存：后端改过菜单（103 改名/改路径）后，旧缓存会让新路径首屏 403
  EvalJs "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs "'p=' + location.pathname") -match '/login') { LoginFlow; agent-browser open $url | Out-Null; agent-browser wait 3000 }
}

# ① 物料类型管理页（新地址）
OpenFresh "$base/dev/material-type"
$js = "JSON.stringify({path:location.pathname,heads:[...document.querySelectorAll('.el-table__header th')].map(t=>t.innerText.trim()),rows:document.querySelectorAll('.el-table__body tr').length,body:document.body.innerText.replace(/\n+/g,' ').slice(0,200)})"
$raw = (EvalJs $js).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  $d = $m.Value | ConvertFrom-Json
  Write-Output ('路径 = ' + $d.path + ' ; 列头 = ' + ($d.heads -join ' | ') + ' ; 数据行 = ' + $d.rows)
  if ($d.path -eq '/dev/material-type') { Ok '新地址 /dev/material-type 可打开' } else { Bad ('落在 ' + $d.path) }
  if ($d.rows -ge 9) { Ok ('物料类型列表有数据（' + $d.rows + ' 行）') } else { Bad ('列表无数据或过少（' + $d.rows + ' 行）') }
  if ($d.body -notmatch 'BOM类型') { Ok '页面不再出现 BOM类型 字样' } else { Bad '页面仍出现 BOM类型' }
} else { Bad ('未能读取物料类型页：' + $raw) }

# ② 旧地址兼容
agent-browser open "$base/dev/bom-type" | Out-Null
agent-browser wait 2500
$p = EvalJs "location.pathname"
# 注意：agent-browser eval 的返回值是「带引号的 JSON 字符串」，比较用 -match 而不是 -eq
if ($p -match '/dev/material-type') { Ok '/dev/bom-type 已重定向到 /dev/material-type' } else { Bad ('旧地址重定向异常：' + $p) }

# ③ 侧栏：基础数据顺序 —— **按库取基准**
# 2026-09-22：原断言"第 6 项 = 物料类型管理"写死了位置，用户重排基础数据（方案 A）后必然失效；
# 改为与 sys_menu(parent_id=2) 的顺序逐项比对 —— 菜单怎么调，本脚本都不用再改。
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$rawDb = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=2 AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
$expBase = @((@($rawDb) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
OpenFresh "$base/dashboard"
$jsMenu = "(()=>{const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].find(x=>(x.querySelector('.el-sub-menu__title')?.innerText||'').includes('基础数据'));return JSON.stringify({c:s?[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim()):[]});})()"
$rawM = (EvalJs $jsMenu).Replace('\"', '"')
$mM = [regex]::Match($rawM, '\{.*\}')
if ($mM.Success) {
  $dm = $mM.Value | ConvertFrom-Json
  Write-Output ('基础数据（侧栏） = ' + ($dm.c -join ' | '))
  Write-Output ('基础数据（库）   = ' + ($expBase -join ' | '))
  if (($dm.c -join '|') -eq ($expBase -join '|')) { Ok ('基础数据顺序与库一致：' + ($dm.c -join ' → ')) } else { Bad ('基础数据顺序与库不一致：库=' + ($expBase -join '|') + ' 实际=' + ($dm.c -join '|')) }
  if (($dm.c -join ',') -notmatch 'BOM表类型') { Ok '侧栏已无 BOM表类型管理' } else { Bad '侧栏仍有旧菜单名' }
}
$jsBtn = "(()=>{const b=[...document.querySelectorAll('button')].filter(x=>x.innerText.trim().includes('物料类型'));return JSON.stringify(b.map(x=>x.innerText.trim()));})()"
$rawB = (EvalJs $jsBtn).Replace('\"', '"')
Write-Output ('首页快捷按钮 = ' + $rawB)
if ($rawB -match '物料类型') { Ok '首页快捷按钮已改名 物料类型' } else { Write-Output 'WARN 首页未找到物料类型快捷按钮（可能该 TAB 未展示）' }

# ④ 委外物料按「物料类型」过滤链路（materialTypeId 参数 + material_type 表）
OpenFresh "$base/outsource/material-info"
$jsMi = "JSON.stringify({tabs:[...document.querySelectorAll('.el-tabs__item')].map(t=>t.innerText.trim()).slice(0,12),rows:document.querySelectorAll('.el-table__body tr').length,body:document.body.innerText.replace(/\n+/g,' ').slice(0,160)})"
$rawMi = (EvalJs $jsMi).Replace('\"', '"')
$mMi = [regex]::Match($rawMi, '\{.*\}')
if ($mMi.Success) {
  $dMi = $mMi.Value | ConvertFrom-Json
  Write-Output ('物料信息 TAB = ' + ($dMi.tabs -join ' | ') + ' ; 行数 = ' + $dMi.rows)
  if (($dMi.tabs -join ',') -match '玻璃') { Ok '物料信息可按物料类型分 TAB（读到 玻璃 等类型）' } else { Bad '物料信息未读到物料类型 TAB' }
  if ($dMi.rows -gt 0) { Ok '物料信息表格有数据（materialTypeId 过滤链路可用）' } else { Bad '物料信息表格无数据' }
  if ($dMi.body -notmatch 'BOM类型') { Ok '物料信息页无 BOM类型 字样' } else { Bad '物料信息页仍有 BOM类型' }
} else { Bad ('未能读取物料信息页：' + $rawMi) }

# ⑤ 一个委外单据页（新增物料订单）——列头应为「物料类型」
agent-browser open "$base/outsource/material-order/add" | Out-Null
agent-browser wait 3000
$body = EvalJs "document.body.innerText.replace(/\n+/g,' ').slice(0,400)"
if ($body -match '物料类型') { Ok '委外物料订单新增页出现 物料类型' } else { Write-Output 'WARN 委外物料订单新增页未检出物料类型字样' }
if ($body -match 'BOM类型') { Bad '委外物料订单新增页仍有 BOM类型' } else { Ok '委外物料订单新增页无 BOM类型' }

if ($fail -eq 0) { Write-Output 'RESULT PASS 物料类型改名全链（库表/接口/字段/菜单/页面/旧地址）符合预期' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
