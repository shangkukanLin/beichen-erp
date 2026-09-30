# 「研发项目 → 研发立项」文案统一校验（2026-09-16，可复跑）
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
}

foreach ($c in @(@('/dev/project/add', '新增研发立项'), @('/dev/project/edit/5', '研发立项详情'))) {
  agent-browser open ($base + $c[0]) | Out-Null
  agent-browser wait 2800
  $title = EvalJs "document.title"
  if ($title -match [regex]::Escape($c[1])) { Ok ($c[0] + ' 页签标题 = ' + $title) } else { Bad ($c[0] + ' 页签标题仍为 ' + $title) }
  $bc = EvalJs "document.querySelector('.el-breadcrumb')?.innerText.replace(/\n+/g,'/')"
  if ($bc -match '研发立项') { Ok ($c[0] + ' 面包屑含研发立项：' + $bc) } else { Bad ($c[0] + ' 面包屑：' + $bc) }
}

# 阶段模板提示文案（模版管理页签）
agent-browser open "$base/template?tab=phase" | Out-Null
agent-browser wait 2800
$tip = EvalJs "document.querySelector('.panel-tip')?.innerText"
if ($tip -match '新建研发立项') { Ok ('阶段模板提示 = ' + $tip) } else { Bad ('阶段模板提示：' + $tip) }

# 研发物料弹窗 placeholder
agent-browser open "$base/dev/material" | Out-Null
agent-browser wait 2800
$clicked = EvalJs "(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='新增');if(!b)return 'no';b.click();return 'ok';})()"
agent-browser wait 2000
# el-select 的 placeholder 渲染在 span 里（不是 input 属性），所以断言整个弹窗文本
$dlg = EvalJs "(document.querySelector('.el-dialog')?.innerText||'').replace(/\n+/g,' ')"
if ($dlg -match '不关联研发立项') { Ok ('研发物料弹窗已含「不关联研发立项」（点击=' + $clicked + '）') } else { Bad ('研发物料弹窗文案：' + $dlg) }
if ($dlg -match '不关联研发项目') { Bad '研发物料弹窗仍有旧文案「不关联研发项目」' } else { Ok '研发物料弹窗无旧文案' }

if ($fail -eq 0) { Write-Output 'RESULT PASS 研发立项文案已统一' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
