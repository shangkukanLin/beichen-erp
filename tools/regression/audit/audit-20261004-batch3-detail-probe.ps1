# audit 2026-10-04 batch 3 - E2 probe for the bill product-detail payload (READ ONLY).
# Question: for a return-sourced group, do the line amounts have a sign that differs from what
# the frontend summary sums (linesSummary sums `amount`, rows display `signedAmount`)?
# ASCII ONLY in every string printed.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }

$b = '{"username":"lin","password":"123","companyId":1}'
$la = Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20
if ([string]$la.code -ne '200') { Write-Output 'FAIL login'; Write-Output 'RESULT BATCH3-DETAIL FAIL count 1'; exit 1 }
$tok = [string]$la.data.token

foreach ($billId in @(197)) {
  $res = Invoke-RestMethod -Uri ("$api/finance/bill/$billId/product-items") -Headers @{ Authorization = $tok } -TimeoutSec 20
  $gs = @($res.data)
  Write-Output ("bill $billId -> groups=" + $gs.Count)
  foreach ($g in $gs) {
    $sumAmount = 0.0; $sumSigned = 0.0
    foreach ($l in @($g.lines)) { $sumAmount += [double]$l.amount; $sumSigned += [double]$l.signedAmount }
    Write-Output ("  billItem=" + $g.billItemId + " type=" + $g.sourceBillType + " kind=" + $g.lineKind +
      " itemAmount=" + $g.itemAmount + " signedLinesAmount=" + $g.signedLinesAmount + " matched=" + $g.matched +
      " lines=" + @($g.lines).Count)
    foreach ($l in @($g.lines)) {
      Write-Output ("     line productId=" + $l.productId + " qty=" + $l.quantity + " amount=" + $l.amount + " signedAmount=" + $l.signedAmount)
    }
    if (@($g.lines).Count -gt 0) {
      # 1) payload shapes: unsigned vs signed differ for return types
      if (($g.sourceBillType -eq 'SALE_RETURN' -or $g.sourceBillType -eq 'PURCHASE_RETURN')) {
        if ($sumAmount -gt 0 -and $sumSigned -lt 0) {
          Ok ("payload shapes differ as designed: SUM(amount)=+$sumAmount (what the frontend linesSummary adds up) vs SUM(signedAmount)=$sumSigned (what each row displays) -> the nested table's summary row would show the opposite sign")
        } else { Bad ("unexpected signs: SUM(amount)=$sumAmount SUM(signedAmount)=$sumSigned") }
      }
      # 2) the group's own reconciliation must hold (backend invariant)
      if ([Math]::Abs([double]$g.signedLinesAmount - [double]$g.itemAmount) -lt 0.0001) {
        Ok ("group reconciles: signedLinesAmount == itemAmount ($($g.itemAmount))")
      } else { Bad ("group does NOT reconcile: signedLinesAmount=$($g.signedLinesAmount) vs itemAmount=$($g.itemAmount)") }
      # 3) F7-274 (fixed 2026-10-04): what the nested table's summary row now shows must equal the reconciliation value
      if ([Math]::Abs($sumSigned - [double]$g.signedLinesAmount) -lt 0.0001) {
        Ok ("F7-274 fixed: nested-table summary (SUM of signedAmount = $sumSigned) == signedLinesAmount == itemAmount")
      } else { Bad ("F7-274 regression: SUM(signedAmount)=$sumSigned != signedLinesAmount=$($g.signedLinesAmount)") }
      # 4) negative control: the OLD expression (unsigned SUM) must NOT agree for a negative group
      if ($sumSigned -lt 0 -and $sumAmount -gt 0) {
        Ok ("negative control: the old unsigned-sum expression would have printed +$sumAmount while rows show $sumSigned (this is exactly F7-274)")
      } else { Bad "negative control not demonstrable on this group" }
    }
  }
}
if ($fail -eq 0) { Write-Output 'RESULT BATCH3-DETAIL PASS' } else { Write-Output ("RESULT BATCH3-DETAIL FAIL count " + $fail) }
exit $fail
