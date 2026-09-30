# 研发立项列表「改配尺寸」列校验（2026-09-16，一次性/可复跑）
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
$js = "(()=>{const t=document.querySelector('.el-table');if(!t)return JSON.stringify({ok:false});const hs=[...t.querySelectorAll('.el-table__header th')].map(x=>x.innerText.trim());const i=hs.indexOf('改配尺寸');const rows=[...t.querySelectorAll('.el-table__body tr')].slice(0,6).map(r=>{const c=r.querySelectorAll('td');return {code:(c[0]?.innerText||'').trim(),org:(c[hs.indexOf('原机尺寸')]?.innerText||'').trim(),glass:(c[i]?.innerText||'').trim()}});return JSON.stringify({ok:true,hasCol:i>=0,heads:hs,rows:rows});})()"
$raw = (EvalJs $js).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if (-not $m.Success) { Bad ('未读到表格：' + $raw); exit 1 }
$d = $m.Value | ConvertFrom-Json
if (-not $d.ok) { Bad '页面上没找到表格（可能未登录/无权限）'; exit 1 }
Write-Output ('列头 = ' + ($d.heads -join ' | '))
if ($d.hasCol) { Ok '列表已含「改配尺寸」列' } else { Bad '列表缺少「改配尺寸」列' }
foreach ($r in $d.rows) { Write-Output ('  ' + $r.code + '  原机尺寸=' + $r.org + '  改配尺寸=' + $r.glass) }
if ($d.rows.Count -gt 0) { Ok ('读到 ' + $d.rows.Count + ' 行数据（可与 SQL 的 glass_size 对账）') } else { Write-Output 'WARN 当前 TAB 无数据行，仅校验列头' }
if ($fail -eq 0) { Write-Output 'RESULT PASS' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
