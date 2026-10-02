# Guard (2026-10-02, permanent) for the user rule "来源单号 must drill down to the source document".
#
# WHY THIS EXISTS -- the defect it locks down:
#   The receivable LIST endpoint returns a HAND-WRITTEN Map projection (FinanceReceivableController#page).
#   On 2026-10-02 it was found to omit `sourceId` (sourceBillType / sourceBillNo were there, the business-doc
#   id was not) => on the customer receivable workbench "来源单号" could never be clicked: sourceRoute()
#   returns '' and the cell silently stays plain text (not a 404 -- just dead). The PAYABLE side was fine
#   (PayableQuery puts sourceId), so the asymmetry is invisible unless you compare the two sides.
#   A field missing from a projection has NO compile-time protection and no guard could see it before this
#   file existed. Same class as the 2026-09-23 `createByName` miss in the very same method (see its comment).
#
# PART A (API): every ledger row that carries a source bill type must also carry the business-doc id.
# PART B (UI) : on the customer receivable workbench, clicking the source bill number must land on
#               /<module>/.../detail/<id> of the SOURCE document -- NOT on the receivable detail page,
#               and the id must be one of the ids the API handed out (a wrong id opens a *different* bill,
#               which is worse than a 404 because it looks like it worked).
#
# Uses seeded data on purpose (no fixture): customer receivables / supplier payables already exist in the
# dev DB, and the fixtures created by other guards are cleaned up when they finish.
# ASCII ONLY (no Chinese literals: PS 5.1 reads this file as GBK).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
$apiBase = 'http://localhost:8080/api'

# Ledger rows as the app sees them (browser token from localStorage, same as the frontend does).
function ApiRecords([string]$path) {
  $t = ((EvalJs "(() => { const x = localStorage.getItem('beichen_erp_token'); return x ? x : 'none' })()") -replace '\s', '')
  if (($t -eq '') -or ($t -eq 'none')) { return @() }
  try { $r = Invoke-RestMethod -Uri ($apiBase + $path) -Headers @{ Authorization = $t } -TimeoutSec 25 } catch { return @() }
  if ($r -and $r.data -and $r.data.records) { return @($r.data.records) }
  return @()
}

Write-Host '--- PART A) the list projections carry the business-doc id ---'
$recRows = ApiRecords '/finance/receivable/page?pageSize=200'
$payRows = ApiRecords '/finance/payable/page?pageSize=200'
foreach ($side in @(@('receivable', $recRows), @('payable', $payRows))) {
  $rows = @($side[1])
  $withType = @($rows | Where-Object { $_.sourceBillType })
  $withId = @($rows | Where-Object { $_.sourceId })
  Write-Host ('  ' + $side[0] + ': rows=' + $rows.Count + ' withSourceType=' + $withType.Count + ' withSourceId=' + $withId.Count)
  Ok ($rows.Count -gt 0) ($side[0] + ' ledger list returns rows (seeded data present)')
  Ok ($withType.Count -eq $withId.Count) ($side[0] + ': every row with a source type also carries sourceId (' + $withId.Count + ' of ' + $withType.Count + ')')
}

Write-Host '--- PART B) the customer receivable workbench drills down ---'
$pick = $null
foreach ($r in $recRows) {
  if ($r.sourceId -and $r.customerId) { $pick = $r; break }
}
if ($pick) {
  $cid = [int]$pick.customerId
  $sid = [int]$pick.sourceId
  Write-Host ('  picked: billNo=' + $pick.sourceBillNo + ' type=' + $pick.sourceBillType + ' sourceId=' + $sid + ' customerId=' + $cid)
  WatchErrors
  Open ('/finance/receivable/customer/' + $cid) 4200
  Start-Sleep -Seconds 1
  $links = (EvalJs "(()=>{return String(document.querySelectorAll('.bill-link').length)})()").Trim()
  Write-Host ('  clickable source bill numbers on the page = ' + $links)
  Ok ([int]$links -ge 1) ('the receivable workbench renders clickable source bill numbers (' + $links + ')')
  EvalJs "(()=>{const l=document.querySelectorAll('.bill-link')[0];if(l)l.click();return 'clicked'})()" | Out-Null
  Start-Sleep -Seconds 2
  $url = (EvalJs "(()=>{return location.pathname})()").Trim()
  Write-Host ('  after click: ' + $url)
  Ok ($url -match '/detail/[0-9]+$') ('a source bill number navigates to a document detail page (' + $url + ')')
  Ok (-not ($url -match '^/finance/receivable/detail/')) 'it goes to the SOURCE document, not back to the receivable detail'
  $ids = @($recRows | Where-Object { $_.sourceId } | ForEach-Object { [string][int]$_.sourceId })
  $tail = ($url -split '/')[-1]
  Write-Host ('  url id=' + $tail + ' ; api sourceIds=' + ($ids -join ','))
  Ok ($ids -contains $tail) ('the id in the url is one of the ledger rows source ids (guards against a wrong id)')
  Ok ((Errs) -eq '[]') 'no JS/API errors on the receivable workbench'
} else {
  Ok $false 'no receivable row carrying a sourceId to click (seeded data missing)'
}
Summary 'verify ledger source-bill drill-down (receivable projection + UI jump)'
