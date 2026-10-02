# Pie-chart OUTSIDE label verification (2026-09-20). ASCII ONLY - no Chinese in this file.
#
# What it proves, per pie chart (ECharts renders to canvas, so we probe real pixels):
#   1) something IS drawn OUTSIDE the pie disc  -> the new outside labels + label lines exist
#      (assertion: drawn-content extent from the centre > outer radius + 8px)
#   2) nothing is clipped by the canvas edge    -> labels are fully visible, not cut off
#
# 2026-10-02 (3rd fix): assertion 1 says "from the centre" but used to measure the ink bbox HORIZONTALLY
#   only. With a single-slice pie (measured: this DB has 1 product / 1 customer this month) ECharts puts
#   the label BELOW the disc => the horizontal extent stayed at disc radius + 5 and the check failed on
#   the untouched /analysis/sale page while its multi-slice pies passed. 'beyond' is now the max RADIAL
#   distance of any inked pixel from the canvas centre -- exactly what the rule above claims.
# The pie disc itself is the only thing that would be drawn without labels (legend is off on
# every pie in scope), so (1) cannot be satisfied by anything else.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-pie-outside-label.ps1 [-Part 1|2|3|4]
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Skip($m) { Write-Host ('SKIP ' + $m) }

# Probe returns CSV: w,h,n,minX,maxX,minY,maxY,outer,beyond,clipped
#
# 2026-10-02 BUGFIX -- why every probe used to fail with "SyntaxError: Unexpected end of input":
#   agent-browser is a .cmd shim, so PowerShell hands the argument to cmd.exe; a MULTI-LINE argument is
#   cut at the first newline and the browser then evaluates a truncated script. Measured: all six probes
#   failed on /analysis/sale AND /analysis/product (i.e. it was never page-specific) while the same
#   script's other (single-line) evals passed. => keep the readable source below and fold it to ONE line
#   before eval. The JS has no '//' comments and no multi-line string literals, so collapsing whitespace
#   is semantics-preserving. (Same root cause as the ui-e2e-lib's Errs() -- see its comment.)
#   => NEVER put a '//' comment inside $jsProbeSrc: after folding it would comment out the rest of the
#      script (I did exactly that once and every probe went back to "Unexpected end of input"). The
#      assertion right after the fold now blocks it.
#
# 2026-10-02 (2nd fix): an EMPTY pie must not be a failure. When a pie has no data the page shows its
#   该区间暂无数据 overlay and ECharts' clear() leaves no <canvas> behind, so the probe used to answer
#   'no-canvas' (= FAIL) for a perfectly healthy empty card. Measured on /analysis/product: the three
#   pies whose card shows that overlay were exactly the three reporting no-canvas, while their container
#   was a normal 457x300. The probe now answers 'empty' when the visible overlay is found in the same
#   card (same idea as the pre-existing 'blank' skip for a drawn-but-empty canvas); without an overlay
#   it still reports no-canvas, so a genuinely broken chart is still caught.
$jsProbeSrc = @'
(function(){
  var id = '__ID__', pct = __PCT__;
  var el = document.getElementById(id);
  if (!el) return 'no-el';
  var cv = el.querySelector('canvas');
  if (!cv) {
    var box = el.closest('.pie-card') || el.closest('.el-card') || el.parentElement;
    var emp = box ? box.querySelector('.pie-empty') : null;
    if (emp && emp.getClientRects().length > 0) return 'empty';
    return 'no-canvas';
  }
  var w = cv.width, h = cv.height;
  var d = cv.getContext('2d').getImageData(0, 0, w, h).data;
  var minX = 1e9, maxX = -1, minY = 1e9, maxY = -1, n = 0, maxR2 = -1;
  var cx = w / 2, cy = h / 2;
  for (var y = 0; y < h; y += 2) {
    for (var x = 0; x < w; x += 2) {
      if (d[(y * w + x) * 4 + 3] > 8) {
        n++;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        var r2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
        if (r2 > maxR2) maxR2 = r2;
      }
    }
  }
  if (maxX < 0) return 'blank';
  var outer = Math.min(w, h) / 2 * pct;
  var beyond = Math.round(Math.sqrt(maxR2));
  var clipped = (minX <= 1 || maxX >= w - 2 || minY <= 1 || maxY >= h - 2) ? 1 : 0;
  return [w, h, n, minX, maxX, minY, maxY, Math.round(outer), Math.round(beyond), clipped].join(',');
})()
'@
# fold to a single line (see the note above: a multi-line argument is truncated by cmd.exe)
$jsProbe = ($jsProbeSrc -replace '\s*\r?\n\s*', ' ').Trim()
if ($jsProbe -match '\n') { throw 'probe JS still contains a newline -- it would be truncated by cmd.exe' }
if ($jsProbe -match '//') { throw 'probe JS contains a // comment -- after folding to one line it would comment out the rest' }

function Probe([string]$id, [double]$pct, [string]$label) {
  $js = $jsProbe.Replace('__ID__', $id).Replace('__PCT__', "$pct")
  $r = (EvalJs $js).Trim()
  if ($r -eq 'no-el' -or $r -eq 'no-canvas') { Ok $false ($label + ' element/canvas missing (' + $r + ')'); return }
  if ($r -eq 'blank') { Skip ($label + ' canvas is blank (empty range / no data) -- not a failure'); return }
  if ($r -eq 'empty') { Skip ($label + ' pie has no data in this range -- the page shows its empty state (not a failure)'); return }
  $p = $r.Split(',')
  if ($p.Count -lt 10) { Ok $false ($label + ' probe parse failed: ' + $r); return }
  $w = [int]$p[0]; $h = [int]$p[1]; $n = [int]$p[2]
  $minX = [int]$p[3]; $maxX = [int]$p[4]; $minY = [int]$p[5]; $maxY = [int]$p[6]
  $outer = [int]$p[7]; $beyond = [int]$p[8]; $clipped = [int]$p[9]
  Write-Host ('  {0} canvas={1}x{2} ink={3} bbox=({4},{5})-({6},{7}) outerR={8} beyond={9} clipped={10}' -f `
    $label, $w, $h, $n, $minX, $minY, $maxX, $maxY, $outer, $beyond, $clipped)
  Ok ($beyond -gt ($outer + 8)) ($label + ' draws content OUTSIDE the pie disc (outside labels + label lines)')
  Ok ($clipped -eq 0) ($label + ' nothing clipped by the canvas edge (labels fully visible)')
}

function CheckPage([string]$path, [string]$pageName, $charts) {
  Step ($pageName + ' -> ' + $path)
  Open $path 4500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 2500
  foreach ($c in $charts) { Probe $c[0] ([double]$c[1]) $c[0] }
  $errs = Errs
  Write-Host ('  errs=' + $errs)
  Ok ($errs -eq '[]') ($pageName + ' recorded no JS/API errors')
}

# 2026-09-22 (user request): the pies must sit TWO PER ROW (left/right) on the analysis pages.
# This can NOT be inferred from the clipping probe above (a single-column grid never clips either),
# so the layout itself needs its own assertion -- otherwise a silent revert to 1 column would keep
# every existing assertion green while dropping the requested layout.
# Probe: number of grid tracks + whether the cards actually share a row (same top, different left).
$jsLayout = "(()=>{const g=document.querySelector('.pie-grid');if(!g)return 'no-grid';const cs=getComputedStyle(g);const tracks=cs.gridTemplateColumns.split(' ').filter(Boolean).length;const cards=[...g.querySelectorAll('.pie-card')];if(cards.length<2)return 'lt2-cards:'+cards.length;const a=cards[0].getBoundingClientRect(),b=cards[1].getBoundingClientRect();const sameRow=Math.abs(a.top-b.top)<=2;const apart=b.left-a.left;return tracks+'|'+cards.length+'|'+(sameRow?'row':'stacked')+'|'+Math.round(a.width)+'|'+Math.round(apart)})()"
function CheckTwoPerRow([string]$path, [string]$pageName) {
  $r = (EvalJs $jsLayout).Trim([char]34)
  Write-Host ('  ' + $pageName + ' layout(tracks|cards|arrangement|cardW|gapToNext) = ' + $r)
  $p = @($r -split '\|')
  if ($p.Count -lt 5) { Ok $false ($pageName + ' layout probe failed: ' + $r); return }
  $tracks = [int]$p[0]; $arrangement = $p[2]; $cardW = [int]$p[3]
  Ok ($tracks -eq 2) ($pageName + ' pie grid has 2 tracks (left/right) -- got ' + $tracks)
  Ok ($arrangement -eq 'row') ($pageName + ' the first two pie cards share one row (left/right) -- got ' + $arrangement)
  Ok ($cardW -ge 420) ($pageName + ' each pie card is wide enough for outside labels (' + $cardW + 'px)')
}

if ($Part -eq 0 -or $Part -eq 1) {
  EnsureLogin | Out-Null
  CheckPage '/analysis/sale' 'sales analysis (6 pies)' @(
    @('pieProductQty', 0.50), @('pieProductProfit', 0.50), @('pieCustomerQty', 0.50),
    @('pieCustomerProfit', 0.50), @('pieReturn', 0.50), @('pieExchange', 0.50)
  )
  CheckTwoPerRow '/analysis/sale' 'sales analysis'
  Summary 'pie outside label - sale'
}

if ($Part -eq 0 -or $Part -eq 2) {
  EnsureLogin | Out-Null
  CheckPage '/analysis/purchase' 'purchase analysis (2 pies)' @(
    @('pieDirectPurchase', 0.50), @('pieOutsourceIn', 0.50)
  )
  CheckTwoPerRow '/analysis/purchase' 'purchase analysis'
  Summary 'pie outside label - purchase'
}

if ($Part -eq 0 -or $Part -eq 3) {
  EnsureLogin | Out-Null
  # customer 24 = 测试客户A10, the only customer with recent sales in this DB
  CheckPage '/analysis/customer/24' 'customer profile (2 pies)' @(
    @('custBrandChart', 0.48), @('custQualityChart', 0.48)
  )
  Summary 'pie outside label - customer profile'
}

# 2026-10-02: the new PRODUCT analysis page (经营分析 -> 产品分析) carries 6 pies
# (sales / net sales / net qty / gross profit + product return rate + product exchange rate).
# Same two-per-row layout as the other analysis pages => it needs the same clipping + layout
# assertions (radius/labelLine must stay small enough for a ~460px card, otherwise the two-line
# outside labels get clipped by the canvas). The two rate pies can be EMPTY (no returns/exchanges
# in the range) -- CheckPage reports 'blank' for those, which is the honest outcome: an empty pie
# draws nothing, so there is nothing to clip.
if ($Part -eq 0 -or $Part -eq 4) {
  EnsureLogin | Out-Null
  CheckPage '/analysis/product' 'product analysis (6 pies)' @(
    @('pieSaleAmount', 0.50), @('pieNetAmount', 0.50), @('pieNetQty', 0.50),
    @('pieProfit', 0.50), @('pieReturnRate', 0.50), @('pieExchangeRate', 0.50)
  )
  CheckTwoPerRow '/analysis/product' 'product analysis'
  Summary 'pie outside label - product'
}
