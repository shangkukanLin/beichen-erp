# 研发立项列表列校验（2026-09-16）：4 个页签都应含「项目名称 / 改配尺寸」，且**不显示**「总成名称」
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-project-cols.ps1
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }

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
  $js = "(()=>{const t=document.querySelector('.el-table');if(!t)return JSON.stringify({ok:false});const hs=[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim());const rows=[...t.querySelectorAll('.el-table__body tr')].map(r=>(r.querySelectorAll('td')[1]?.innerText||'').trim());return JSON.stringify({ok:true,heads:hs,names:rows});})()"
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ($t + ' 未读到表格'); continue }
  $d = $m.Value | ConvertFrom-Json
  if (-not $d.ok) { Bad ($t + ' 没找到表格'); continue }
  Write-Output ('[' + $t + '] ' + ($d.heads -join ' | '))
  foreach ($need in @('项目名称', '改配尺寸')) {
    if ($d.heads -contains $need) { Ok ($t + ' 含「' + $need + '」列') } else { Bad ($t + ' 缺「' + $need + '」列') }
  }
  if ($d.heads -contains '总成名称') { Bad ($t + ' 仍显示「总成名称」列（用户要求不显示）') } else { Ok ($t + ' 已不显示「总成名称」列') }
  if ($d.names.Count -gt 0) { Write-Output ('  数据行（项目名称）= ' + (($d.names | Select-Object -First 3) -join ' ; ')) } else { Write-Output '  （该页签暂无数据行，仅校验列头）' }
}

if ($fail -eq 0) { Write-Output 'RESULT PASS 研发立项 4 个页签：列齐、且总成名称已不再显示' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
