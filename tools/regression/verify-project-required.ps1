# 新增研发立项：原机配置/改配信息必填校验（2026-09-16，可复跑）
# 断言：① 9 个字段都渲染出红色 *（is-required）② 未填时点「创建项目」会被拦截并提示，且不跳转
# 2026-09-21 适配：立项页改版 —— 「总成名称」→「产品名称」（占位符随之变化）、
#   新增必填「规格」（原配/改配）与「产品SKU」。规格=原配时页面会隐藏改配信息 5 项，
#   故本脚本先选「改配」，再校验原有 9 项必填与提交拦截。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }

agent-browser open "$base/dev/project/add" | Out-Null
agent-browser wait 3200
if ((EvalJs "'p=' + location.pathname") -match '/login') {
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
  agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
  agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
  agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
  agent-browser wait 3500
  agent-browser open "$base/dev/project/add" | Out-Null
  agent-browser wait 3200
}

# ⓪ 2026-09-21：先把「规格」选成「改配」——否则改配信息 5 项被隐藏、必然缺席下面的断言
$openSpec = "(()=>{const it=[...document.querySelectorAll('.el-form-item')].find(x=>{const l=x.querySelector('.el-form-item__label');return l&&l.innerText.trim()==='规格'});if(!it)return 'nospec';const w=it.querySelector('.el-select__wrapper')||it.querySelector('.el-select');if(!w)return 'nowrapper';w.click();return 'opened';})()"
Write-Output ('展开规格下拉 = ' + (EvalJs $openSpec))
agent-browser wait 500
$pickSpec = "(()=>{const items=[...document.querySelectorAll('.el-select-dropdown__item')].filter(i=>i.getClientRects().length>0);const t=items.find(i=>i.innerText.trim()==='改配');if(!t)return 'noopt';t.click();return 'ok';})()"
Write-Output ('选择「改配」 = ' + (EvalJs $pickSpec))
agent-browser wait 700

# ① 必填标记
$jsReq = "JSON.stringify([...document.querySelectorAll('.el-form-item.is-required .el-form-item__label')].map(l=>l.innerText.trim()))"
$raw = (EvalJs $jsReq).Replace('\"', '"')
$m = [regex]::Match($raw, '\[.*\]')
if (-not $m.Success) { Bad ('未读到必填项：' + $raw); exit 1 }
$labels = @(($m.Value | ConvertFrom-Json))
Write-Output ('页面必填项 = ' + ($labels -join ' | '))
$expect = @('原机尺寸', '原分辨率', '驱动IC', '触摸IC', '玻璃尺寸', '玻璃分辨率', '驱动IC', '触摸IC', '码片IC')
foreach ($need in @('原机尺寸', '原分辨率', '玻璃尺寸', '玻璃分辨率', '码片IC')) {
  if ($labels -contains $need) { Ok ('「' + $need + '」已标必填 *') } else { Bad ('「' + $need + '」未标必填') }
}
# 驱动IC/触摸IC 各出现两次（原机配置 + 改配信息）
foreach ($need in @('驱动IC', '触摸IC')) {
  $n = @($labels | Where-Object { $_ -eq $need }).Count
  if ($n -ge 2) { Ok ('「' + $need + '」两处都标了必填（' + $n + '）') } else { Bad ('「' + $need + '」只标了 ' + $n + ' 处') }
}

# ② 未填原机配置时提交应被拦截
$setJs = "(()=>{const set=(ph,v)=>{const el=[...document.querySelectorAll('input')].find(i=>i.placeholder===ph);if(!el)return false;el.value=v;el.dispatchEvent(new Event('input',{bubbles:true}));return true};return JSON.stringify([set('请输入项目名称','REQ-TEST'),set('请输入产品名称','REQ-TEST-ASSY')]);})()"
$setRaw = (EvalJs $setJs).Replace('\"', '"')
Write-Output ('填入项目名称/产品名称 = ' + $setRaw)
agent-browser wait 600
EvalJs "(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='创建项目');if(b){b.click();return 'ok'}return 'no'})()" | Out-Null
agent-browser wait 1200
$msg = EvalJs "(()=>{const ms=[...document.querySelectorAll('.el-message')].map(x=>x.innerText.trim());return JSON.stringify(ms)})()"
$path = EvalJs "location.pathname"
Write-Output ('提示 = ' + $msg + ' ; 当前路径 = ' + $path)
if ($msg -match '原机尺寸') { Ok '未填原机配置时被正确拦截（提示原机尺寸）' } else { Bad ('未出现预期提示：' + $msg) }
if ($path -match '/dev/project/add') { Ok '被拦截后仍停留在新增页（未提交）' } else { Bad ('提交似乎成功了，路径 ' + $path) }

if ($fail -eq 0) { Write-Output 'RESULT PASS 原机配置/改配信息 9 项必填（含提交拦截；规格已选「改配」）' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
