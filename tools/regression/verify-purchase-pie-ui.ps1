# 进货分析两个饼图 UI 校验：卡片等高 / 有无数据同高 / 位置在列表卡（供货商分析，2026-09-22 前为采购单据明细）上方 / 金额·件数 switch（2026-09-15）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-purchase-pie-ui.ps1
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$url = 'http://localhost:5173/analysis/purchase'
$jsCards = "JSON.stringify([...document.querySelectorAll('.pie-grid .pie-card')].map(c=>({t:(c.querySelector('.pie-title')||{}).innerText,h:Math.round(c.getBoundingClientRect().height),v:(c.querySelector('.pie-total')||{}).innerText,empty:!!c.querySelector('.pie-empty')&&getComputedStyle(c.querySelector('.pie-empty')).display!=='none'})))"
$jsPos = "(()=>{const g=document.querySelector('.pie-grid'),c=document.querySelector('.section-card');if(!g||!c)return 'missing';return String(Math.round(g.getBoundingClientRect().top))+'|'+String(Math.round(c.getBoundingClientRect().top));})()"

function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function GetCards() {
  $raw = (EvalJs $jsCards).Replace('\"', '"')
  $m = [regex]::Match($raw, '\[\{.*\}\]')
  if (-not $m.Success) { return $null }
  try { return ($m.Value | ConvertFrom-Json) } catch { Write-Output ('WARN 解析失败：' + $m.Value); return $null }
}

agent-browser open $url | Out-Null
agent-browser wait 3000
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

$fail = 0
$c1 = GetCards
if ($null -eq $c1) { Write-Output 'FAIL 未读到饼图卡片'; exit 1 }
foreach ($c in $c1) { Write-Output ("   {0,-16} 高={1,4}px 合计={2,-18} 空={3}" -f $c.t, $c.h, $c.v, $c.empty) }

# ① 两张卡 + 等高（本月：直接采购有数据、委外为空 → 恰好是"有/无数据"混合态）
if ($c1.Count -eq 2) { Write-Output 'PASS 有两个饼图卡片（直接采购成品 / 委外加工成品入库）' } else { Write-Output ("FAIL 卡片数 " + $c1.Count + "（应为 2）"); $fail++ }
$diff = [Math]::Abs([int]$c1[0].h - [int]$c1[1].h)
if ($diff -le 1) { Write-Output ("PASS 两卡等高（{0}px）：有数据 vs 空态 高度一致" -f $c1[0].h) } else { Write-Output ("FAIL 两卡不等高 {0} vs {1}px" -f $c1[0].h, $c1[1].h); $fail++ }
if ($c1[0].empty -ne $c1[1].empty) { Write-Output 'PASS 本月为混合态（一张有数据 / 一张空态）→ 等高成立' } else { Write-Output 'INFO 本月两卡同为有数据或同为空的态' }

# ② 位置：饼图在列表卡（供货商分析 / 2026-09-22 前为「采购单据明细（下钻）」）上方
$pos = EvalJs $jsPos
if ($pos -match '"?(\d+)\|(\d+)"?') {
  if ([int]$Matches[1] -lt [int]$Matches[2]) { Write-Output ("PASS 饼图在列表卡上方（top {0} < {1}）" -f $Matches[1], $Matches[2]) }
  else { Write-Output ("FAIL 饼图不在列表卡上方（top {0} >= {1}）" -f $Matches[1], $Matches[2]); $fail++ }
} else { Write-Output ('WARN 位置读取失败：' + $pos) }

# ③ switch：直接采购卡「金额 ↔ 件数」，且不影响另一卡
Write-Output ('   切换前：直接采购=' + $c1[0].v + '  委外=' + $c1[1].v)
EvalJs "document.querySelectorAll('.pie-grid .pie-card')[0].querySelector('.el-switch').click(); 'ok'" | Out-Null
agent-browser wait 1500
$c2 = GetCards
Write-Output ('   切「直接采购」后：直接采购=' + $c2[0].v + '  委外=' + $c2[1].v)
if ($c2[0].v -ne $c1[0].v) { Write-Output 'PASS 直接采购 switch 生效（合计已从金额变件数）' } else { Write-Output 'FAIL 直接采购 switch 未生效'; $fail++ }
if ($c2[1].v -eq $c1[1].v) { Write-Output 'PASS 委外卡未受影响（开关独立）' } else { Write-Output 'FAIL 委外卡被连带改变'; $fail++ }
if ([Math]::Abs([int]$c2[0].h - [int]$c2[1].h) -le 1) { Write-Output 'PASS 切换后两卡仍等高' } else { Write-Output ('FAIL 切换后不等高 ' + $c2[0].h + ' vs ' + $c2[1].h); $fail++ }

if ($fail -eq 0) { Write-Output 'RESULT PASS 进货分析两饼图 UI 正常' } else { Write-Output ("RESULT FAIL 项数 " + $fail); exit 1 }
