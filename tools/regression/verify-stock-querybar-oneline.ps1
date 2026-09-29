# verify-stock-querybar-oneline.ps1 (2026-09-29, user request): the three search fields of
#   成品库存详情   /inventory/product-stock   (产品 / 品牌 / 所在仓库)
#   物料库存详情   /outsource/material-stock  (物料 / 物料类型 / 所在仓库)
# must sit on ONE row. They used to wrap onto two: the global query bar is a grid
# ".query-bar { grid-template-columns: 1fr auto }" (form left / toolbar right), and inside the form
# el-form--inline lays the items out as inline-flex with a 32px margin-right each, so at viewport 1262
# the form column is only 614px and the third field (所在仓库) dropped to a second line.
#
# Per page:
#   1) exactly 3 search fields exist (form items that hold an input/select -- the 仅看低于安全库存
#      checkbox is NOT a search field and is allowed to wrap);
#   2) all three share the same top => they are on one row;
#   3) neither the query bar nor the form overflows horizontally (a "one line" achieved by squeezing
#      the row out of its column would be a failure, not a pass);
#   4) no field was silently shrunk by flex (each rendered field width >= 120px).
# Read-only, rerunnable, PURE ASCII.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
$fail = 0
function Ok($m) { Write-Output ('PASS ' + $m) }
function Bad($m) { Write-Output ('FAIL ' + $m); $script:fail++ }

$probe = "(()=>{const vis=e=>e.getClientRects().length>0;const bar=document.querySelector('.query-card .query-bar');const f=document.querySelector('.query-card .query-form');const its=[...document.querySelectorAll('.query-card .query-form .el-form-item')].filter(vis);const flt=its.filter(i=>i.querySelector('.el-input,.el-select'));const tops=[...new Set(flt.map(i=>Math.round(i.getBoundingClientRect().top)))];const fw=flt.map(i=>{const e=i.querySelector('.el-input,.el-select');return Math.round((e||i).getBoundingClientRect().width)});return JSON.stringify({items:its.length,filters:flt.length,rows:tops.length,tops:tops,fieldW:fw,minField:Math.min.apply(null,fw),formW:Math.round(f.getBoundingClientRect().width),formOver:f.scrollWidth-f.clientWidth,barOver:bar.scrollWidth-bar.clientWidth})})()"

foreach ($p in @('/inventory/product-stock', '/outsource/material-stock')) {
  Write-Output ('--- ' + $p)
  Open $p 3400
  $raw = EvalJs $probe
  Write-Output ('  probe = ' + $raw)
  $d = $null
  try { $d = $raw | ConvertFrom-Json } catch { }
  if ($null -eq $d) { Bad ($p + ': probe failed -> ' + $raw); continue }
  if ([int]$d.filters -eq 3) { Ok ($p + ': the three search fields are present') }
  else { Bad ($p + ': expected 3 search fields, got ' + $d.filters) }
  if ([int]$d.rows -eq 1) { Ok ($p + ': all three search fields sit on ONE row') }
  else { Bad ($p + ': the search fields wrap onto ' + $d.rows + ' rows (tops=' + ($d.tops -join '/') + ')') }
  if (([int]$d.barOver -le 2) -and ([int]$d.formOver -le 2)) { Ok ($p + ': the query bar does not overflow (bar=' + $d.barOver + ' form=' + $d.formOver + ' px)') }
  else { Bad ($p + ': the query bar overflows (bar=' + $d.barOver + ' form=' + $d.formOver + ' px)') }
  if ([int]$d.minField -ge 120) { Ok ($p + ': no field was shrunk by flex (min=' + $d.minField + 'px, widths=' + ($d.fieldW -join '/') + ')') }
  else { Bad ($p + ': a search field shrank to ' + $d.minField + 'px (widths=' + ($d.fieldW -join '/') + ')') }
}
if ($fail -eq 0) { Write-Output 'RESULT PASS stock query bars: the three search fields stay on one row (both pages)' }
else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
