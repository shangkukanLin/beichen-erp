# 物料标签布局守卫（2026-10-10）：把"物料名称前加物料类型"改动过的**列表页**逐页量横向溢出。
#
# 为什么不是直接用 scan-table-overflow.ps1：那个要遍历**全站**列表页，本机跑 15 分钟仍在第一页 ✗
# ⇒ 这里对**本次改动过的页面**做同口径定向断言（scrollWidth ≤ clientWidth ✓），代价小得多、证据同样直接。
# ⚠️ 未覆盖：需要 id 的**详情页**（如 material-stock/detail、warehouse-detail）。它们的安全依据是**算术**：
#    凡"删掉独立类型列"的页面，新名称列宽 = 原名称列宽 + 被删列宽 ⇒ **总宽与改前逐字相等** ✓ 不引入新溢出；
#    只有"未删列、单纯加前缀"的少数列加了 30~40px ⇒ 那几处仍需全站扫描确认（见报告 §7.35 的待办）。
#
# ⚠️ 2026-10-10 首跑结果（9 页）：8 页 overflow = 0；**`/outsource/material-order` = 223px** ✗（窗口正常，
#    clientW=956 ≈ 设计值，故不是窄窗口假象）。**尚未定论**是本次改动引入还是既存 —— 分析上不应是本次：
#    该"物料名称"列是 `min-width="100"` + `show-overflow-tooltip`，el-table 走 fixed 布局 ⇒ 单元格内容
#    不参与列宽计算 ⇒ 加前缀不该改列宽 ✗；但该页注释里的"合计 928px ≤ 内容区 956px"与实际相差 ~223px，
#    说明**列宽预算注释已过期** ✗（有后来的列被加宽）。⇒ 待查项：用"回退该页 EntityLinks 一行再量"的方式
#    定论（本仓不允许把未定论的红留下来当噪音，也不允许为了变绿而删断言 ⇒ 先挂着，报告 §7.35 已记）。
#
# ASCII-only on purpose（PS 5.1 + BOM 陷阱）：打印一律 ASCII ✓。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$ErrorActionPreference = 'Continue'
$script:fail = 0
function Ok($c, $m) { if ($c) { Write-Output ("PASS " + $m) } else { Write-Output ("FAIL " + $m); $script:fail++ } }

Open '/dashboard' 2400
EvalJs "localStorage.removeItem('beichen_erp_token'); localStorage.removeItem('beichen_erp_user'); 'cleared'" | Out-Null
Start-Sleep -Milliseconds 500
EnsureLogin
WatchErrors

$pages = @(
  '/outsource/material-info',
  '/outsource/material-stock',
  '/outsource/material-stock-log',
  '/outsource/material-order',
  '/outsource/material-return',
  '/outsource/order',
  '/outsource/stock-loss',
  '/inventory/product-stock',
  '/dashboard'
)
$js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);let worst=0;let n=0;let cw=0;for(const t of ts){const b=t.querySelector('.el-table__body-wrapper');const h=t.querySelector('.el-table__header-wrapper');const o=Math.max(b?(b.scrollWidth-b.clientWidth):0,h?(h.scrollWidth-h.clientWidth):0);if(o>worst)worst=o;if(t.clientWidth>cw)cw=t.clientWidth;n++}return JSON.stringify({tables:n,over:worst,cw:cw})})()"

foreach ($p in $pages) {
  Open $p 4200
  Start-Sleep -Milliseconds 1500
  $r = EvalJs $js
  Write-Host ('  ' + $p + ' >> ' + $r)
  try {
    $o = ($r | ConvertFrom-Json)
    # ⚠️ 有些列表页的列宽是**硬预算**（例：物料订单 10 列合计 928px，见该页 :100-111 的注释）——
    #    这类页在**窄窗口**下先天装不下（列不能收缩）✗，与本次"名称加前缀"无关（受影响单元格都带
    #    show-overflow-tooltip ⇒ 内容被裁、不改列宽 ✓）。因此窗口太窄时按"跳过 + 说明"处理，
    #    否则守卫会变成恒红 ✗（本仓明令避免"既存恒红"的守卫）。
    if ([int]$o.cw -lt 900) {
      Ok $true ($p + ': SKIPPED - window too narrow (table clientW=' + $o.cw + ' < 900); fixed-width budgets like 928px cannot fit here, unrelated to the label prefix')
    } else {
      Ok ([int]$o.over -le 1) ($p + ': horizontal overflow = ' + $o.over + ' across ' + $o.tables + ' table(s), clientW=' + $o.cw)
    }
  } catch { Ok $false ($p + ': probe failed -> ' + $r) }
}
Ok ((Errs) -eq '[]') 'no JS/API errors while walking the changed pages'

if ($script:fail -eq 0) { Write-Output 'RESULT MATERIAL-LABEL-LAYOUT PASS' } else { Write-Output ("RESULT MATERIAL-LABEL-LAYOUT FAIL count " + $script:fail) }
exit $script:fail
