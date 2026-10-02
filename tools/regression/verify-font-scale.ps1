# 全站字号阶梯校验（2026-09-16，可复跑）
# 阶梯：12 辅助 / 14 正文（表格·表单·按钮·菜单）/ 15 卡片·弹窗标题 / 18 页面标题 / 16·22 统计数字
# 断言：① 源码无字号硬编码（tokens.css 除外，403 插图例外）② 无 --app-font-sm 残留
#       ③ 浏览器逐页：表格 td/th=14px、表头字重 500、卡片标题=15px、标签=12px、统计数字=22/16px
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$root = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web'
$src = Join-Path $root 'src'
$base = 'http://localhost:5173'
$global:fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $global:fail = $global:fail + 1 }
function OpenFresh($url) {
  EvalJs "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 2600
  if ((EvalJs "location.pathname") -match '/login') {
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
    agent-browser wait 2600
  }
}
function ReadJson($js, $want) {
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("未读到 " + $want + "：" + $raw); return $null }
  return ($m.Value | ConvertFrom-Json)
}

# ① 源码守卫：字号硬编码 / 废弃 token
$fontBad = Get-ChildItem -Path $src -Recurse -Include *.vue, *.css, *.ts -File |
  Where-Object { $_.Name -ne 'tokens.css' -and $_.FullName -notlike '*\views\error\403.vue' } |
  Select-String -Pattern 'font-size:\s*[0-9.]+px'
if ($fontBad) {
  Bad ("源码仍有字号硬编码 " + $fontBad.Count + " 处")
  $fontBad | ForEach-Object { Write-Output ("  " + $_.Path + ":" + $_.LineNumber + ": " + $_.Line.Trim()) }
} else { Ok '源码无字号硬编码（403 插图 96px 已白名单）' }
$smLeft = Get-ChildItem -Path $src -Recurse -Include *.vue, *.css, *.ts -File |
  Select-String -Pattern 'var\(--app-font-sm\)'
if ($smLeft) { Bad ("仍有废弃 token --app-font-sm " + $smLeft.Count + " 处") } else { Ok '无废弃 token --app-font-sm 残留' }
$tokens = [System.IO.File]::ReadAllText((Join-Path $src 'styles\tokens.css'))
foreach ($need in @('--app-font-xs: 12px', '--app-font-base: 14px', '--app-font-md: 15px', '--app-font-lg: 18px', '--app-font-num: 22px', '--app-font-num-sm: 16px')) {
  if ($tokens -match [regex]::Escape($need)) { Ok ('token ' + $need) } else { Bad ('token 缺失/被改：' + $need) }
}
if ($tokens -match '--el-font-size-extra-small:\s*var\(--app-font-base\)') { Ok 'EP small/extra-small 已统一到 14px' } else { Bad 'EP 小尺寸字号未统一（--el-font-size-extra-small 未指向 base）' }

# ② 页面实测（列表 / 详情 / 报表 / 首页 / 分析）
$pages = @(
  '/outsource/order', '/outsource/material-order', '/outsource/order/delivery', '/outsource/material-order/delivery',
  '/inventory/sale', '/system/user', '/outsource/order/detail/23', '/outsource/order/close/23',
  '/dashboard', '/analysis/cash', '/analysis/overview',
  # 2026-10-02 新增「产品分析」（经营分析目录第 4 项）：本页同时有 表格 + 折线图 + 饼图 + stat-value.sm 卡
  '/analysis/product'
)
foreach ($p in $pages) {
  OpenFresh ($base + $p)
  $d = ReadJson "(()=>{const g=(s,n)=>{const e=document.querySelector(s);return e?getComputedStyle(e)[n]:''};return JSON.stringify({p:location.pathname,td:g('.el-table__body td .cell','fontSize'),th:g('.el-table__header th .cell','fontSize'),thW:g('.el-table__header th .cell','fontWeight'),card:g('.el-card__header','fontSize'),tag:g('.el-tag','fontSize'),stat:g('.stat-value','fontSize'),statSm:g('.stat-value.sm','fontSize')})})()" '字号'
  if (-not $d) { continue }
  $issues = New-Object System.Collections.ArrayList
  if ($d.th -ne '' -and $d.th -ne '14px') { $issues.Add('表头=' + $d.th) | Out-Null }
  if ($d.thW -ne '' -and $d.thW -ne '500') { $issues.Add('表头字重=' + $d.thW) | Out-Null }
  if ($d.td -ne '' -and $d.td -ne '14px') { $issues.Add('表格正文=' + $d.td) | Out-Null }
  if ($d.card -ne '' -and $d.card -ne '15px') { $issues.Add('卡片标题=' + $d.card) | Out-Null }
  if ($d.tag -ne '' -and $d.tag -ne '12px') { $issues.Add('标签=' + $d.tag) | Out-Null }
  foreach ($sv in @($d.stat, $d.statSm)) { if ($sv -ne '' -and $sv -ne '22px' -and $sv -ne '16px') { $issues.Add('统计数字=' + $sv) | Out-Null } }
  if ($issues.Count -eq 0) { Ok ($p + ' 字号合规（表头/正文 14、标题 15、标签 12、统计 16/22）') }
  else { Bad ($p + ' 字号异常：' + ($issues -join '、')) }
}

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 全站字号阶梯统一（12/14/15/18/16/22）' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
