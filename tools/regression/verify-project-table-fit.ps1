# 研发立项列表「不横向滚动」校验（2026-09-16，可复跑）
# 两部分：
#  ① 静态：从 SFC 源码解析 4 张表的「配置列宽合计」（width + min-width），断言 ≤ 940px
#     —— 940 ≈ 1200px 宽窗口 − 侧栏 210 − 主区左右内边距 32（再留余量）
#  ② 浏览器：逐页签实测「没有横向滚动条」且滚动容器无横向溢出
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$vueFile = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views\dev\project\index.vue'
$fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }

Write-Output '=== ① 静态：配置列宽合计 ==='
$txt = [System.IO.File]::ReadAllText($vueFile, (New-Object System.Text.UTF8Encoding($false)))
$tables = [regex]::Matches($txt, '<el-table\b[\s\S]*?</el-table>')
if ($tables.Count -ne 4) { Bad ('预期 4 张表，实际 ' + $tables.Count) }
$i = 0
foreach ($tb in $tables) {
  $i++
  $sum = 0
  foreach ($w in [regex]::Matches($tb.Value, '(?:min-)?width="(\d+)"')) { $sum += [int]$w.Groups[1].Value }
  $label = ([regex]::Match($tb.Value, 'label="[^"]*"')).Value
  Write-Output ('  表' + $i + ' 配置列宽合计 = ' + $sum + 'px')
  if ($sum -le 940) { Ok ('表' + $i + ' 列宽合计 ' + $sum + 'px ≤ 940px') } else { Bad ('表' + $i + ' 列宽合计 ' + $sum + 'px > 940px') }
}

Write-Output '=== ② 浏览器：实际渲染是否横向滚动 ==='
agent-browser open "$base/dev/project" | Out-Null
agent-browser wait 3000
if ((EvalJs "'p=' + location.pathname") -match '/login') {
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
  agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
  agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
  agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
  agent-browser wait 3500
  agent-browser open "$base/dev/project" | Out-Null
  agent-browser wait 3000
}
$tabs = @('全部', '进行中', '已结项', '已取消')
foreach ($t in $tabs) {
  EvalJs ("(()=>{const t=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='" + $t + "');if(t){t.click();return 'ok'}return 'no'})()") | Out-Null
  agent-browser wait 1400
  $js = "(()=>{const t=document.querySelector('.el-table');if(!t)return JSON.stringify({ok:false});const outer=t.clientWidth;const bar=t.querySelector('.el-scrollbar__bar.is-horizontal');const barW=bar?Math.round(bar.getBoundingClientRect().width):0;const barVisible=!!bar&&getComputedStyle(bar).display!=='none'&&barW>0;const wrap=t.querySelector('.el-scrollbar__wrap');const scrollW=wrap?wrap.scrollWidth:0;const clientW=wrap?wrap.clientWidth:0;const cols=[...t.querySelectorAll('.el-table__header th')].length;return JSON.stringify({ok:true,cols:cols,outer:outer,barW:barW,barVisible:barVisible,scrollW:scrollW,clientW:clientW});})()"
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ($t + ' 未读到表格'); continue }
  $d = $m.Value | ConvertFrom-Json
  if (-not $d.ok) { Bad ($t + ' 未找到表格'); continue }
  Write-Output ('[' + $t + '] 列数=' + $d.cols + ' 容器=' + $d.outer + 'px 横向条宽=' + $d.barW + ' 滚动宽/可视宽=' + $d.scrollW + '/' + $d.clientW)
  if (-not $d.barVisible) { Ok ($t + ' 无横向滚动条') } else { Bad ($t + ' 出现横向滚动条（' + $d.barW + 'px）') }
  if ([double]$d.scrollW -le ([double]$d.clientW + 1)) { Ok ($t + ' 滚动容器无横向溢出') } else { Bad ($t + ' 横向溢出 ' + ([int]$d.scrollW - [int]$d.clientW) + 'px') }
}

if ($fail -eq 0) { Write-Output 'RESULT PASS 研发立项列表一行显示完毕、无横向滚动' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
