# 必填项 * 校验（2026-09-16）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-required-star.ps1
# 原理：Element Plus 对 required（或 prop+rules.required）的表单项会加 class="el-form-item is-required"，
#       并渲染出红色 *。这里用真实浏览器打开页面/弹窗，断言必填项确实带 is-required。
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
# 读取当前可见表单里的必填标签
$jsReq = "JSON.stringify([...document.querySelectorAll('.el-form-item.is-required')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0}).map(e=>{const l=e.querySelector('.el-form-item__label');return l?l.innerText.trim():'(无标签)'}))"

function CheckDialog($url, $btnText, $expectLabel) {
  OpenFresh $url
  $clicked = EvalJs ("(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='" + $btnText + "');if(!b)return 'no';b.click();return 'ok';})()")
  if ($clicked -notmatch 'ok') { Bad ($url + " 未找到按钮「" + $btnText + "」"); return }
  agent-browser wait 1600
  $raw = (EvalJs $jsReq).Replace('\"', '"')
  $m = [regex]::Match($raw, '\[.*\]')
  if (-not $m.Success) { Bad ($url + ' 未读到必填项'); return }
  # 注意：ConvertFrom-Json 结果要先解包成普通数组，否则 -join 会打印成 System.Object[]
  $labels = @(($m.Value | ConvertFrom-Json))
  Write-Output ('  ' + $url + ' 「' + $btnText + '」必填项 = ' + ($labels -join ' | '))
  if ($labels.Count -gt 0) { Ok ($url + ' 弹窗有必填项（' + $labels.Count + ' 个）') } else { Bad ($url + ' 弹窗没有任何必填项') }
  if (($labels -join ',') -match [regex]::Escape($expectLabel)) { Ok ('"' + $expectLabel + '" 已标必填 *') } else { Bad ('"' + $expectLabel + '" 未标必填') }
}

function CheckPage($url, $expectLabel) {
  OpenFresh $url
  $raw = (EvalJs $jsReq).Replace('\"', '"')
  $m = [regex]::Match($raw, '\[.*\]')
  if (-not $m.Success) { Bad ($url + ' 未读到必填项'); return }
  $labels = @(($m.Value | ConvertFrom-Json))
  Write-Output ('  ' + $url + ' 必填项 = ' + ($labels -join ' | '))
  if (($labels -join ',') -match [regex]::Escape($expectLabel)) { Ok ($url + ' 的「' + $expectLabel + '」已标必填 *') } else { Bad ($url + ' 的「' + $expectLabel + '」未标必填') }
}

Write-Output '=== 弹窗表单 ==='
CheckDialog "$base/dev/material-type" '新增' '类型名称'
CheckDialog "$base/template?tab=phase" '新增' '阶段名称'
CheckDialog "$base/inventory/brand" '新增' '品牌名称'
CheckDialog "$base/outsource/warehouse" '新增' '仓库名称'

Write-Output '=== 页面内联表单 ==='
CheckPage "$base/dev/project/add" '项目名称'
CheckPage "$base/inventory/material-move/add" '移仓日期'
CheckPage "$base/outsource/material-return/add" '退回对象'

if ($fail -eq 0) { Write-Output 'RESULT PASS 必填项 * 已按业务校验补齐' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
