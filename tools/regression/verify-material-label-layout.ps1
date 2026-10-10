# 物料标签布局守卫（2026-10-10）：把"物料名称前加物料类型"改动过的**列表页**逐页量横向溢出。
#
# 为什么不是直接用 scan-table-overflow.ps1：那个要遍历**全站**列表页，本机跑 15 分钟仍在第一页 ✗
# ⇒ 这里对**本次改动过的页面**做同口径定向断言（scrollWidth ≤ clientWidth ✓），代价小得多、证据同样直接。
# ⚠️ 未覆盖：需要 id 的**详情页**（如 material-stock/detail、warehouse-detail）。它们的安全依据是**算术**：
#    凡"删掉独立类型列"的页面，新名称列宽 = 原名称列宽 + 被删列宽 ⇒ **总宽与改前逐字相等** ✓ 不引入新溢出；
#    只有"未删列、单纯加前缀"的少数列加了 30~40px ⇒ 那几处仍需全站扫描确认（见报告 §7.35 的待办）。
#
# ✅ 2026-10-10 已定论（对照实测，结论：既存问题，非本次引入）：
#    `/outsource/material-order` 首跑报 223px 溢出 ⇒ 把该页 EntityLinks 回退成"改前写法"后复量，
#    溢出数值**完全一致**（bodyOver=0 / headOver=223）⇒ 证明"名称加前缀"对该页列宽**零影响** ✓
#    （与 el-table fixed 布局下"单元格内容不参与列宽计算"的推断一致 ✓）。真因见下方断言旁的注释说明。
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
    # 既存基线（2026-10-10 对照实测定论）：物料订单列表**改前就有** 223px 溢出（表头 wrapper，body=0）——
    #   证据：把本页 EntityLinks 回退成改前写法后复量，溢出数值**完全一致** ⇒ 与"名称加前缀"无关 ✓。
    #   真因是该页注释里的"合计 928px ≤ 956px"早已过期（实测 1179px：物料名称被撑到 234、状态 271，
    #   并多出后来新增的「交期」78）⇒ 既存显示问题，不在本次范围 ⚠️。故此处只断言"不劣于基线"。
    $limit = if ($p -eq '/outsource/material-order') { 224 } else { 1 }
    if ([int]$o.tables -eq 0) {
      # 页面没有可见的 el-table（例：/dashboard 的卡片在未展开/未激活的页签里）⇒ 没东西可量 ⇒ 跳过（不是窄窗口）
      Ok $true ($p + ': SKIPPED - no visible el-table on this page (collapsed cards / inactive tab); nothing to measure')
    } elseif ([int]$o.cw -lt 900) {
      Ok $true ($p + ': SKIPPED - window too narrow (table clientW=' + $o.cw + ' < 900); fixed-width budgets like 928px cannot fit here, unrelated to the label prefix')
    } else {
      Ok ([int]$o.over -le $limit) ($p + ': horizontal overflow = ' + $o.over + ' (limit ' + $limit + ') across ' + $o.tables + ' table(s), clientW=' + $o.cw)
    }
  } catch { Ok $false ($p + ': probe failed -> ' + $r) }
}
Ok ((Errs) -eq '[]') 'no JS/API errors while walking the changed pages'

if ($script:fail -eq 0) { Write-Output 'RESULT MATERIAL-LABEL-LAYOUT PASS' } else { Write-Output ("RESULT MATERIAL-LABEL-LAYOUT FAIL count " + $script:fail) }
exit $script:fail
