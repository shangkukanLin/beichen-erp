# P13 (2026-09-22 user request): the purchase-analysis page's bottom card is now a SUPPLIER analysis list
#   (it replaced the purchase-bill list), and clicking a supplier name drills into that supplier's own
#   analysis page (/analysis/purchase/supplier/:id).
# This case CREATES NOTHING (rerun-safe): it only reads the two pages and clicks one link.
# ASCII ONLY -- Chinese comes from ui-e2e-zh.json via ZH/B64 (a .ps1 with raw Chinese but no BOM breaks PS 5.1).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

Step '1) bottom card = supplier analysis list (not the old bill list); header total must reconcile with the KPI'
Open '/analysis/purchase' 4500
ClearErrs | Out-Null
Start-Sleep -Milliseconds 2500
# probe: first card title / the list card's visible rows / first row cells / the card header text /
#        the page KPI cards (index 2 = net purchase)
$probe = "(()=>{const vis=e=>e.getClientRects().length>0;" +
  "const t=[...document.querySelectorAll('.card-title')].filter(vis)[0];" +
  "const cards=[...document.querySelectorAll('.section-card')].filter(vis);const c=cards[cards.length-1];" +
  "const rows=c?[...c.querySelectorAll('.el-table__body tbody tr')].filter(vis):[];" +
  "const tds=rows[0]?[...rows[0].querySelectorAll('td')].map(x=>(x.innerText||'').replace(/\s+/g,' ').trim()):[];" +
  "const k=[...document.querySelectorAll('.stat-card.kpi .stat-value')].filter(vis).map(e=>(e.innerText||'').trim());" +
  "return [t?t.innerText.trim():'', rows.length, tds.join('~'), c?(c.innerText||'').replace(/\s+/g,' ').slice(0,160):'', (k[2]||'')].join('@@')})()"
$p = (EvalJs $probe) -split '@@'
$title = $p[0]
$rowCount = [int]$p[1]
$cells = $p[2] -split '~'
$head = $p[3]
$kpiNet = $p[4]
Write-Host ('  list card title = ' + $title)
Write-Host ('  supplier rows = ' + $rowCount)
Write-Host ('  first row = ' + ($cells -join ' | '))
Write-Host ('  card head = ' + $head)
Write-Host ('  page KPI net purchase = ' + $kpiNet)
Ok ($title -eq (ZH 'card_title_supplier_analysis')) 'the bottom card is the supplier analysis list'
Ok ($rowCount -ge 1) ('the list has supplier rows (' + $rowCount + ')')
Ok ($cells.Count -ge 5) ('the first row carries the supplier columns (cells=' + $cells.Count + ')')
Ok ((-not [string]::IsNullOrWhiteSpace($kpiNet)) -and $head.Contains($kpiNet)) 'the list header total net equals the page KPI net purchase (same source -> must reconcile)'
$firstSup = $cells[0]
$firstNet = $cells[3]
Write-Host ('  first supplier = ' + $firstSup + ' ; its net purchase = ' + $firstNet)

Step '2) clicking the supplier name opens that supplier analysis page'
$clickJs = "(()=>{const vis=e=>e.getClientRects().length>0;const cards=[...document.querySelectorAll('.section-card')].filter(vis);" +
  "const c=cards[cards.length-1];const rows=[...c.querySelectorAll('.el-table__body tbody tr')].filter(vis);" +
  "const l=rows[0]?rows[0].querySelector('.bill-link'):null;if(!l)return 'NOLINK';l.click();return l.innerText.trim()})()"
$clicked = EvalJs $clickJs
Start-Sleep -Milliseconds 2800
$path = EvalJs 'String(location.pathname)'
Write-Host ('  clicked = ' + $clicked + ' -> ' + $path)
Ok ($clicked -ne 'NOLINK') 'a supplier name was clicked'
Ok ($path -match '/analysis/purchase/supplier/[0-9]+') ('the drill-down page opened (' + $path + ')')

# page content: back button + product TOP card + bill list card + net KPI == the list row net (same range passed through)
$bBack = B64 (ZH 'btn_back_purchase_analysis')
$bProd = B64 (ZH 'card_sup_products')
$bBills = B64 (ZH 'card_sup_bills')
$probe2 = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;" +
  "const body=document.body.innerText;" +
  "const back=T('$bBack'),prod=T('$bProd'),bills=T('$bBills');" +
  "const k=[...document.querySelectorAll('.stat-card.kpi .stat-value')].filter(vis).map(e=>(e.innerText||'').trim());" +
  "const t=[...document.querySelectorAll('.toolbar .title')].filter(vis)[0];" +
  "return [(body.indexOf(back)>=0),(body.indexOf(prod)>=0),(body.indexOf(bills)>=0),(k[2]||''),(t?t.innerText.trim():'')].join('@@')})()"
$q = (EvalJs $probe2) -split '@@'
Write-Host ('  has back=' + $q[0] + ' products=' + $q[1] + ' bills=' + $q[2])
Write-Host ('  detail net KPI = ' + $q[3] + ' ; page title = ' + $q[4])
Ok ($q[0] -eq 'true') 'the back-to-purchase-analysis button is rendered'
Ok ($q[1] -eq 'true') 'the product TOP card is rendered'
Ok ($q[2] -eq 'true') 'the bill list card is rendered'
Ok ($q[3] -eq $firstNet) ('the drill-down net purchase equals the list row (' + $q[3] + ' = ' + $firstNet + ') -- the range is carried through')
Ok ($q[4] -like ('*' + $firstSup + '*')) ('the page title shows that supplier (' + $q[4] + ')')
Write-Host ('  errs=' + (Errs))
Ok ((Errs) -eq '[]') 'no JS/API errors on either page'
Summary 'purchase analysis -> supplier drill-down (P13)'
