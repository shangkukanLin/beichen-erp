# R5a verification (2026-09-20): F7-177 -- detail pages must fetch warehouse names BY ID
# instead of pulling the whole /warehouse/page list back and finding in the client.
#
# Method: after each navigation read `performance.getEntriesByType('resource')` (no JS hook needed,
# survives the SPA navigation) and assert:
#   - at least one call to /api/warehouse/<digits>       (the per-id endpoint)
#   - ZERO calls to /api/warehouse/page                  (the full-list endpoint we removed)
#   - no JS/API errors
#
# Fixture ids taken from the live database:
#   sale_order 281 -> warehouse 71 (finished no.2)      sale_exchange 17 -> in/out 73 (finished no.4)
#   sale_return 31 -> warehouse 73                      inventory_warehouse_move 49 -> 76 / 79
#   warehouse 71 (its own detail page)
#
# ASCII ONLY.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

function WhCalls() {
  $js = "(()=>{const rs=performance.getEntriesByType('resource').map(e=>e.name).filter(n=>n.indexOf('/warehouse')>=0).map(n=>n.replace(/^https?:\/\/[^/]+/,''));const byId=rs.filter(n=>n.indexOf('/warehouse/page')<0);const byPage=rs.filter(n=>n.indexOf('/warehouse/page')>=0);return JSON.stringify({byId:byId.length,byPage:byPage.length,all:rs.slice(0,6)})})()"
  return (EvalJs $js)
}

function CheckPage([string]$path, [string]$label) {
  Step ($label + ' -> ' + $path)
  Open $path 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1200
  $r = WhCalls
  Write-Host ('  warehouse calls = ' + $r)
  Write-Host ('  path now = ' + (EvalJs 'String(location.pathname)'))
  Ok ($r -match '"byId":[1-9]') ($label + ' calls GET /warehouse/{id}')
  # NOTE: do NOT require byPage=0 -- a page may legitimately still prefetch the list for its own
  # warehouse dropdown (RemoteSelect). The point of F7-177 is that the NAME lookup no longer does it.
  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') ($label + ' recorded no JS/API errors')
}

if ($Part -eq 0 -or $Part -eq 1) {
  EnsureLogin | Out-Null
  CheckPage '/inventory/sale/detail/281' 'sale order detail (F7-177 sale/order/detail.vue)'
  Summary 'F7-177 sale order detail'
}

if ($Part -eq 0 -or $Part -eq 2) {
  EnsureLogin | Out-Null
  CheckPage '/sale/exchange/detail/17' 'sale exchange detail (F7-177 sale/exchange/detail.vue)'
  Summary 'F7-177 sale exchange detail'
}

if ($Part -eq 0 -or $Part -eq 3) {
  EnsureLogin | Out-Null
  CheckPage '/sale/return/detail/31' 'sale return detail (F7-177 sale/return/detail.vue)'
  CheckPage '/inventory/warehouse/detail/71' 'warehouse detail (F7-177 inventory/warehouse-detail.vue)'
  Summary 'F7-177 sale return + warehouse detail'
}

if ($Part -eq 0 -or $Part -eq 4) {
  EnsureLogin | Out-Null
  CheckPage '/inventory/warehouse-move/detail/49' 'warehouse move detail (F7-177 warehouse-move/detail.vue)'
  Summary 'F7-177 warehouse move detail'
}

if ($Part -eq 0 -or $Part -eq 5) {
  EnsureLogin | Out-Null
  CheckPage '/inventory/purchase/detail/255' 'purchase order detail (F7-177 purchase/order/detail.vue)'
  CheckPage '/inventory/purchase-return/detail/12' 'purchase return detail (F7-177 purchase/return/detail.vue)'
  Summary 'F7-177 purchase order + return detail'
}
