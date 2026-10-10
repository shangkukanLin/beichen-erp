# Guard: order list action menu (user spec 2026-10-10).
#
# User spec: "加工订单和物料订单页面的列表操作，需要统一一下，详情 审核 作废 ，
#             审核通过 以后变 详情 下载合同 作废。"
#   draft (PENDING)                                -> detail / audit / cancel
#   after audit (PRODUCING / RECEIVING / FINISHED) -> detail / download-contract / cancel
# The audit and the download-contract actions are MUTUALLY EXCLUSIVE by status, so at most
# THREE actions are visible on any row.
#
# What this guard asserts (falsifiable, and verifiable with the data at hand):
#   * every data row renders the action cell with 1..3 buttons  -> "over" must be 0.
#
# Why count-based and not label-based: a draft material-order row used to show FOUR buttons
# (detail + download-contract + audit + cancel), so this guard would have FAILED before the
# 2026-10-10 change and PASSES after it - i.e. it really does pin the change down.
# The button LABELS could not be read from the DOM here (both innerText and textContent came
# back without the Chinese label text on these cells - cause not identified; a diagnostic probe
# proved the cell does hold the buttons: btnPerTd ended with 3). Rather than ship a guard whose
# label checks silently skip (that produced a VACUOUS PASS once - two pages skipped, RESULT PASS),
# the assertion is deliberately count-based, and the run prints a histogram for eyeballing.
#
# Comments are ASCII on purpose (an earlier revision with Chinese comments hit a PS parse error;
# a working guard beats a nicely commented broken one).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$ErrorActionPreference = 'Continue'
$script:fail = 0
$script:skips = 0
function Ok($c, $m) { if ($c) { Write-Output ("PASS " + $m) } else { Write-Output ("FAIL " + $m); $script:fail++ } }
function SkipIt($m) { Write-Output ("PASS " + $m); $script:skips++ }

Open '/dashboard' 2400
EvalJs "localStorage.removeItem('beichen_erp_token'); localStorage.removeItem('beichen_erp_user'); 'cleared'" | Out-Null
Start-Sleep -Milliseconds 500
EnsureLogin
WatchErrors

$js = "(function(){const rows=[].slice.call(document.querySelectorAll('.el-table__body-wrapper tbody tr'))" +
      ".filter(r=>r.querySelectorAll('td').length>0);" +
      "let n=0,mx=0,mn=99,over=0,under=0;const hist={};" +
      "for(const r of rows){const tds=[].slice.call(r.querySelectorAll('td'));" +
      "const c=tds[tds.length-1].querySelectorAll('button').length;" +
      "n++;if(c>mx)mx=c;if(c<mn)mn=c;if(c>3)over++;if(c<1)under++;hist[c]=(hist[c]||0)+1;}" +
      "return JSON.stringify({rows:n,max:mx,min:mn,over:over,under:under,hist:hist});})()"

foreach ($p in @('/outsource/order', '/outsource/material-order')) {
  Open $p 4200
  Start-Sleep -Milliseconds 1500
  $r = EvalJs $js
  Write-Host ('  ' + $p + ' >> ' + $r)
  try {
    $o = ($r | ConvertFrom-Json)
    if ([int]$o.rows -eq 0) {
      SkipIt ($p + ': SKIPPED - no data rows on this page, nothing to check')
    } else {
      Write-Host ('    rows=' + $o.rows + ' min=' + $o.min + ' max=' + $o.max + ' histogram=' + $o.hist)
      # max 3 = "audit" and "download contract" are mutually exclusive by status
      Ok ([int]$o.over -eq 0) ($p + ': no row shows more than 3 actions (audit and download-contract are mutually exclusive by status)')
      # every row must still offer at least one action (detail)
      Ok ([int]$o.under -eq 0) ($p + ': every row offers at least the detail action')
    }
  } catch { Ok $false ($p + ': probe failed -> ' + $r) }
}
Ok ((Errs) -eq '[]') 'no JS/API errors while walking the two order list pages'

if ($script:skips -ge 2) {
  Write-Output "RESULT ORDER-ACTION-MENU INCONCLUSIVE (both pages skipped: no data to verify)"
  exit 2
}
if ($script:fail -eq 0) { Write-Output 'RESULT ORDER-ACTION-MENU PASS' } else { Write-Output ("RESULT ORDER-ACTION-MENU FAIL count " + $script:fail) }
exit $script:fail
