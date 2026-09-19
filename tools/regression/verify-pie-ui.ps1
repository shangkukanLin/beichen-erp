# 销售分析：① 6 张饼图卡片「有数据 / 无数据」等高 ② 退货率/换货率「金额/件数」switch（2026-09-15）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-pie-ui.ps1
$ErrorActionPreference = 'Continue'
$url = 'http://localhost:5173/analysis/sale'
$jsCards = "JSON.stringify([...document.querySelectorAll('.pie-grid .pie-card')].map(c=>({t:(c.querySelector('.pie-title')||{}).innerText,h:Math.round(c.getBoundingClientRect().height),v:(c.querySelector('.pie-total')||{}).innerText,empty:!!c.querySelector('.pie-empty')&&getComputedStyle(c.querySelector('.pie-empty')).display!=='none'})))"
$jsButtons = "JSON.stringify([...document.querySelectorAll('.stat-range .el-radio-button__inner')].map(b=>b.innerText.trim()))"

function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function GetCards() {
  $raw = EvalJs $jsCards
  # eval 输出是「被转义过的 JSON 字符串」：先把 \" 还原成 "，再取数组
  $raw = $raw.Replace('\"', '"')
  $m = [regex]::Match($raw, '\[\{.*\}\]')
  if (-not $m.Success) { return $null }
  try { return ($m.Value | ConvertFrom-Json) } catch { Write-Output ('WARN 解析失败：' + $m.Value); return $null }
}
function ShowCards($cards, $tag) { foreach ($c in $cards) { Write-Output ("   {0} {1,-12} 高={2,4}px 合计={3,-8} 空={4}" -f $tag, $c.t, $c.h, $c.v, $c.empty) } }
function ClickButton($label) {
  # 统计区间是 el-radio-button（渲染为 label），按文案点击
  $js = "(()=>{const a=[...document.querySelectorAll('.stat-range .el-radio-button__inner')].find(el=>el.innerText.trim()==='" + $label + "');if(!a)return 'notfound';a.click();return 'ok';})()"
  return ((EvalJs $js) -match 'ok')
}

# ---------- 0) 登录（会话失效时） ----------
agent-browser open $url | Out-Null
agent-browser wait 2500
if ((EvalJs "'path=' + location.pathname") -match '/login') {
  Write-Output '（会话已失效，先登录）'
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
  if (-not ($mu.Success -and $mp.Success -and $mb.Success)) { Write-Output 'FAIL 登录页元素未找到'; exit 1 }
  agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
  agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
  agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
  agent-browser wait 3500
  agent-browser open $url | Out-Null
  agent-browser wait 3500
}

# ---------- 1) 有数据态 ----------
$c1 = GetCards
if ($null -eq $c1) { Write-Output 'FAIL 未读到卡片'; exit 1 }
Write-Output '=== ① 本月（全部有数据） ==='
ShowCards $c1 '本月'
Write-Output ('   区间按钮 = ' + (EvalJs $jsButtons))

# ---------- 2) 空区间态 ----------
$c2 = $null
if (ClickButton '今日') {
  agent-browser wait 3000
  $c2 = GetCards
  Write-Output '=== ② 今日（全部无数据） ==='
  if ($null -ne $c2) { ShowCards $c2 '空区间' } else { Write-Output '   （未读到卡片）' }
} else {
  Write-Output 'WARN 未找到「今日」按钮，跳过空态高度检查'
}

$fail = 0
function HeightsEqual($cards, $tag) {
  $hs = @($cards | ForEach-Object { [int]$_.h })
  $mx = ($hs | Measure-Object -Maximum).Maximum; $mn = ($hs | Measure-Object -Minimum).Minimum
  if (($mx - $mn) -le 1) { Write-Output ("PASS {0}：6 张卡等高（{1}px）" -f $tag, $mn) } else { Write-Output ("FAIL {0}：高度不一致 {1}~{2}px" -f $tag, $mn, $mx); $script:fail++ }
}
HeightsEqual $c1 '本月'
if ($null -ne $c2 -and $c2.Count -eq 6) {
  HeightsEqual $c2 '空区间'
  $h1 = [int]$c1[0].h; $h2 = [int]$c2[0].h
  if ([Math]::Abs($h1 - $h2) -le 1) { Write-Output ("PASS 有数据高度 = 无数据高度（{0}px）" -f $h1) } else { Write-Output ("FAIL 有数据 {0}px ≠ 无数据 {1}px" -f $h1, $h2); $fail++ }
}

# ---------- 3) switch（回本月） ----------
if ($null -ne $c2) { if (ClickButton '本月') { agent-browser wait 3000 } else { agent-browser open $url | Out-Null; agent-browser wait 3500 } }
$c3 = GetCards
Write-Output '=== ③ 金额/件数 switch（两卡各自独立） ==='
Write-Output ("   初始：退货率={0}  换货率={1}" -f $c3[4].v, $c3[5].v)
EvalJs "document.querySelectorAll('.pie-grid .pie-card')[4].querySelector('.el-switch').click(); 'ok'" | Out-Null
agent-browser wait 1500
$c4 = GetCards
Write-Output ("   只切退货率：退货率={0}  换货率={1}" -f $c4[4].v, $c4[5].v)
if ($c4[4].v -ne $c3[4].v) { Write-Output 'PASS 退货率 switch 生效（合计值已变）' } else { Write-Output 'FAIL 退货率 switch 未生效'; $fail++ }
if ($c4[5].v -eq $c3[5].v) { Write-Output 'PASS 换货率 未受连带影响（开关独立）' } else { Write-Output 'FAIL 换货率被连带改变'; $fail++ }
EvalJs "document.querySelectorAll('.pie-grid .pie-card')[5].querySelector('.el-switch').click(); 'ok'" | Out-Null
agent-browser wait 1500
$c5 = GetCards
Write-Output ("   再切换货率：退货率={0}  换货率={1}" -f $c5[4].v, $c5[5].v)
if ($c5[5].v -ne $c4[5].v) { Write-Output 'PASS 换货率 switch 生效' } else { Write-Output 'FAIL 换货率 switch 未生效'; $fail++ }
if ($c5[4].v -eq $c4[4].v) { Write-Output 'PASS 退货率 保持件数口径（未被连带回退）' } else { Write-Output 'FAIL 退货率被连带改变'; $fail++ }

if ($fail -eq 0) { Write-Output 'RESULT PASS 卡片等高 + 双开关独立生效' } else { Write-Output ("RESULT FAIL 项数 " + $fail); exit 1 }
