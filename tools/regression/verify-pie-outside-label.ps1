# Pie-chart OUTSIDE label verification (2026-09-20). ASCII ONLY - no Chinese in this file.
#
# What it proves, per pie chart (ECharts renders to canvas, so we probe real pixels):
#   1) something IS drawn OUTSIDE the pie disc  -> the new outside labels + label lines exist
#      (assertion: drawn-content extent from the centre > outer radius + 8px)
#   2) nothing is clipped by the canvas edge    -> labels are fully visible, not cut off
# The pie disc itself is the only thing that would be drawn without labels (legend is off on
# every pie in scope), so (1) cannot be satisfied by anything else.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-pie-outside-label.ps1 [-Part 1|2|3]
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Skip($m) { Write-Host ('SKIP ' + $m) }

# Probe returns CSV: w,h,n,minX,maxX,minY,maxY,outer,beyond,clipped
$jsProbe = @'
(function(){
  var id = '__ID__', pct = __PCT__;
  var el = document.getElementById(id);
  if (!el) return 'no-el';
  var cv = el.querySelector('canvas');
  if (!cv) return 'no-canvas';
  var w = cv.width, h = cv.height;
  var d = cv.getContext('2d').getImageData(0, 0, w, h).data;
  var minX = 1e9, maxX = -1, minY = 1e9, maxY = -1, n = 0;
  for (var y = 0; y < h; y += 2) {
    for (var x = 0; x < w; x += 2) {
      if (d[(y * w + x) * 4 + 3] > 8) {
        n++;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  if (maxX < 0) return 'blank';
  var outer = Math.min(w, h) / 2 * pct;
  var beyond = Math.max(w / 2 - minX, maxX - w / 2);
  var clipped = (minX <= 1 || maxX >= w - 2 || minY <= 1 || maxY >= h - 2) ? 1 : 0;
  return [w, h, n, minX, maxX, minY, maxY, Math.round(outer), Math.round(beyond), clipped].join(',');
})()
'@

function Probe([string]$id, [double]$pct, [string]$label) {
  $js = $jsProbe.Replace('__ID__', $id).Replace('__PCT__', "$pct")
  $r = (EvalJs $js).Trim()
  if ($r -eq 'no-el' -or $r -eq 'no-canvas') { Ok $false ($label + ' element/canvas missing (' + $r + ')'); return }
  if ($r -eq 'blank') { Skip ($label + ' canvas is blank (empty range / no data) -- not a failure'); return }
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

if ($Part -eq 0 -or $Part -eq 1) {
  EnsureLogin | Out-Null
  CheckPage '/analysis/sale' 'sales analysis (6 pies)' @(
    @('pieProductQty', 0.62), @('pieProductProfit', 0.62), @('pieCustomerQty', 0.62),
    @('pieCustomerProfit', 0.62), @('pieReturn', 0.62), @('pieExchange', 0.62)
  )
  Summary 'pie outside label - sale'
}

if ($Part -eq 0 -or $Part -eq 2) {
  EnsureLogin | Out-Null
  CheckPage '/analysis/purchase' 'purchase analysis (2 pies)' @(
    @('pieDirectPurchase', 0.62), @('pieOutsourceIn', 0.62)
  )
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
