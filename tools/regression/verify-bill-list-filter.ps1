# Guard (2026-09-29, permanent) for the finance BILL LIST rules:
#   A) the type filter offers an "all" option, and "all" really queries BOTH kinds -- proven by comparing the
#      pager total against the DB count for each filter state (not by eyeballing a page of rows).
#   B) the two amount columns follow the selected type (user rule: what was received is "received" and what was
#      paid is "paid" -- a blended wording like "paid/received" must NOT be used). Checked through ZH keys:
#      the receivable pair, the payable pair, and (for the all state) the neutral ledger pair.
#   C) while "all" is selected the partner filter is DISABLED (customer ids and supplier ids live in separate
#      id spaces, so filtering both kinds by one partner_id silently cross-hits another party).
# ASCII ONLY on purpose (no BOM in this file; PS 5.1 would read Chinese literals as GBK). All Chinese
# needles come from ui-e2e-zh.json via ZH.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}

$parts = (ZH 'text_bill_col_parts') -split '\|'
$recvPaid = $parts[0]; $recvUnpaid = $parts[1]
$payPaid = $parts[2]; $payUnpaid = $parts[3]
$allPaid = $parts[4]; $allUnpaid = $parts[5]
$allOpt = ZH 'opt_all'
$recvOpt = ZH 'opt_bill_receivable'
$payOpt = ZH 'opt_bill_payable'
$queryBtn = ZH 'btn_query'
$forbidden = ((ZH 'text_statement_parts') -split '\|')[8]   # the combined term that must NOT appear

$nAll = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill')
$nRecv = [int](SqlOne "SELECT COUNT(*) FROM finance_bill WHERE bill_type='RECEIVABLE'")
$nPay = [int](SqlOne "SELECT COUNT(*) FROM finance_bill WHERE bill_type='PAYABLE'")
Write-Host ('[BASE] bills: all=' + $nAll + ' receivable=' + $nRecv + ' payable=' + $nPay)
Ok (($nAll -gt 0) -and ($nRecv -gt 0) -and ($nPay -gt 0)) 'fixture: the DB holds BOTH kinds of bill'

# --- helpers: drive the query bar + read the rendered header / pager ---
function OpenTypeSelect() {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.query-form .el-select')].filter(vis)[0];if(!s)return 'NOSEL';const inp=s.querySelector('input')||s;inp.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));inp.click();return 'OPEN'})()")
}
function PickOption([string]$text) {
  $b = B64 $text
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$b');const vis=e=>e.getClientRects().length>0;for(const d of [...document.querySelectorAll('.el-select-dropdown')].filter(vis)){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()!=='');const hit=li.find(e=>(e.innerText||'').trim()===N);if(hit){hit.click();return 'OK'}}return 'NOHIT'})()")
}
function ClickQuery() {
  $b = B64 $queryBtn
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$b');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(e=>(e.innerText||'').trim()===N);if(!b)return 'NOBTN';b.click();return 'OK'})()")
}
function Heads() {
  return (EvalJs "JSON.stringify([...document.querySelectorAll('.el-table th')].map(e=>(e.innerText||'').trim()).filter(x=>x!==''))")
}
function PagerTotal() {
  $s = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const e=[...document.querySelectorAll('.el-pagination__total')].filter(vis)[0];return e?(e.innerText||''):'NOTOTAL'})()"
  $m = [regex]::Match($s, '\d+')
  if (-not $m.Success) { return -1 }
  return [int]$m.Value
}
function PartnerDisabled() {
  # NOTE: Element Plus puts is-disabled on the inner .el-select__wrapper (not on the .el-select root), so the
  # reliable probe is the inner <input disabled>. Also dump the class names for debugging.
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ss=[...document.querySelectorAll('.query-form .el-select')].filter(vis);const s=ss[1];if(!s)return 'NOSEL count='+ss.length;const inp=s.querySelector('input');const dis=(inp&&inp.disabled)?'INPUTDISABLED':'INPUTENABLED';const w=(s.firstElementChild&&s.firstElementChild.className)||'';return dis+'|root='+(s.className||'')+'|wrapper='+w})()")
}
function ApplyType([string]$typeText) {
  Write-Host ('  type: ' + (OpenTypeSelect) + ' / ' + (PickOption $typeText) + ' / query: ' + (ClickQuery))
  Start-Sleep -Milliseconds 2200
}

Open '/finance/bill' 3400
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1700

Write-Host '--- A) the type filter offers the "all" option ---'
Ok ((OpenTypeSelect) -match 'OPEN') 'type select opens'
Start-Sleep -Milliseconds 700
$opts = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()!=='');if(li.length)return JSON.stringify(li.map(e=>(e.innerText||'').trim()))}return '[]'})()"
Write-Host ('  options: ' + $opts)
Ok ($opts -match [regex]::Escape($allOpt)) ('type filter offers the all option (' + $allOpt + ')')
Ok (($opts -match [regex]::Escape($recvOpt)) -and ($opts -match [regex]::Escape($payOpt))) 'type filter still offers both concrete types'
Ok ((([regex]::Matches($opts, '","').Count) + 1) -ge 3) 'type filter has >= 3 options'

Write-Host '--- B1) default (receivable) state ---'
$heads = Heads
Write-Host ('  headers: ' + $heads)
$tot = PagerTotal
Write-Host ('  pager total: ' + $tot)
Ok (($heads -match [regex]::Escape($recvPaid)) -and ($heads -match [regex]::Escape($recvUnpaid))) 'receivable list uses its own wording (paid/received)'
Ok (-not ($heads -match [regex]::Escape($forbidden))) ('no combined wording on the page (' + $forbidden + ')')
Ok ($tot -eq $nRecv) ('receivable filter returns exactly the receivable bills (' + $tot + ' == ' + $nRecv + ')')

Write-Host '--- B2) payable state ---'
ApplyType $payOpt
$heads = Heads
$tot = PagerTotal
Write-Host ('  headers: ' + $heads + ' | pager total: ' + $tot)
Ok (($heads -match [regex]::Escape($payPaid)) -and ($heads -match [regex]::Escape($payUnpaid))) 'payable list uses its own wording (paid/payable)'
Ok ($tot -eq $nPay) ('payable filter returns exactly the payable bills (' + $tot + ' == ' + $nPay + ')')

Write-Host '--- B3) all state ---'
ApplyType $allOpt
$heads = Heads
$tot = PagerTotal
$dis = PartnerDisabled
Write-Host ('  headers: ' + $heads + ' | pager total: ' + $tot + ' | partner filter: ' + $dis)
Ok (($heads -match [regex]::Escape($allPaid)) -and ($heads -match [regex]::Escape($allUnpaid))) 'all state uses the neutral wording'
Ok ($tot -eq $nAll) ('all filter returns EVERY bill (' + $tot + ' == ' + $nAll + ')')
Ok ($dis -match 'INPUTDISABLED') 'partner filter is disabled while the type filter is all (no cross-id mixing)'

Write-Host ('errs=' + (Errs))
Ok ((Errs) -eq '[]') 'bill list recorded no JS/API errors'
Summary 'verify bill list type filter (all option + type-aware wording)'
