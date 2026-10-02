# Guard (2026-09-29, permanent) for the finance BILL LIST rules:
#   A) the type filter offers an "all" option AND the DEFAULT is "all" (user rule 2026-09-29), and every filter
#      state really queries what it says -- proven by comparing the pager total against the DB count for each
#      state (not by eyeballing a page of rows).
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

# 2026-10-02 BUGFIX -- why 6 assertions could never pass even though the page was correct:
#   Chinese text COMING BACK from the page (EvalJs/agent-browser stdout) is decoded as GBK on this host,
#   so comparing it against the ZH needles ('全部' / '已结算' ...) always missed -- while every ASCII-only
#   check (pager total, disabled state) passed. The page itself was measured correct (options = 全部|应收|应付,
#   headers = ...|已结算|未结算|...). The repo convention for page text is BASE64 in the probe + decode here
#   (same as scan-col-truncation.ps1's Dec); passing Chinese INTO the page already used B64.
#   NOTE the encoding bug also made the "no combined wording" assertion a FALSE PASS (mojibake can never
#   contain the forbidden word), so this fix makes that check real too.
function Dec([string]$b) {
  if ([string]::IsNullOrEmpty($b)) { return '' }
  try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b)) } catch { return '?' }
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
# 2026-10-02: clicking an already-open select TOGGLES IT CLOSED (measured: section A leaves the dropdown
# open, so B2's click closed it -> PickOption found nothing -> NOHIT -> the receivable state never applied,
# which looked like a page bug but was this probe's). Only click when no dropdown is open => idempotent.
function OpenTypeSelect() {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const already=[...document.querySelectorAll('.el-select-dropdown')].filter(vis).some(d=>[...d.querySelectorAll('li')].some(e=>vis(e)));if(already)return 'OPEN';const s=[...document.querySelectorAll('.query-form .el-select')].filter(vis)[0];if(!s)return 'NOSEL';const inp=s.querySelector('input')||s;inp.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));inp.click();return 'OPEN'})()")
}
function PickOption([string]$text) {
  $b = B64 $text
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$b');const vis=e=>e.getClientRects().length>0;for(const d of [...document.querySelectorAll('.el-select-dropdown')].filter(vis)){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()!=='');const hit=li.find(e=>(e.innerText||'').trim()===N);if(hit){hit.click();return 'OK'}}return 'NOHIT'})()")
}
function ClickQuery() {
  $b = B64 $queryBtn
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$b');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(e=>(e.innerText||'').trim()===N);if(!b)return 'NOBTN';b.click();return 'OK'})()")
}
# headers come back as ONE base64 blob of items joined by U+0001 (a JSON array round-trip turned into a
# single concatenated string on this host -- measured "账单号类型往来单位..." with no separators) and are then
# re-joined with '|' so the existing -match assertions keep working.
function Heads() {
  $raw = EvalJs "(()=>{const B=s=>btoa(unescape(encodeURIComponent(s||'')));return B([...document.querySelectorAll('.el-table th')].map(e=>(e.innerText||'').trim()).filter(x=>x!=='').join('\u0001'))})()"
  return (((Dec $raw) -split ([char]1)) -join '|')
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
# options come back as ONE base64 blob joined by U+0001 (same reason as Heads above)
$optsRaw = EvalJs "(()=>{const B=s=>btoa(unescape(encodeURIComponent(s||'')));const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()!=='');if(li.length)return B(li.map(e=>(e.innerText||'').trim()).join('\u0001'))}return B('')})()"
$optArr = @(((Dec $optsRaw) -split ([char]1)) | Where-Object { $_ -ne '' })
$opts = ($optArr -join '|')
Write-Host ('  options: ' + $opts)
Ok ($opts -match [regex]::Escape($allOpt)) ('type filter offers the all option (' + $allOpt + ')')
Ok (($opts -match [regex]::Escape($recvOpt)) -and ($opts -match [regex]::Escape($payOpt))) 'type filter still offers both concrete types'
Ok ($optArr.Count -ge 3) ('type filter has >= 3 options (' + $optArr.Count + ')')

Write-Host '--- B1) DEFAULT state must be all (2026-09-29 user rule: default = all) ---'
$heads = Heads
$tot = PagerTotal
$dis = PartnerDisabled
Write-Host ('  headers: ' + $heads + ' | pager total: ' + $tot + ' | partner filter: ' + $dis)
Ok (($heads -match [regex]::Escape($allPaid)) -and ($heads -match [regex]::Escape($allUnpaid))) 'default (= all) list uses the neutral wording'
Ok (-not ($heads -match [regex]::Escape($forbidden))) ('no combined wording on the page (' + $forbidden + ')')
Ok ($tot -eq $nAll) ('default filter returns EVERY bill (' + $tot + ' == ' + $nAll + ')')
Ok ($dis -match 'INPUTDISABLED') 'partner filter is disabled in the default (all) state (no cross-id mixing)'

Write-Host '--- B2) receivable state ---'
ApplyType $recvOpt
$heads = Heads
$tot = PagerTotal
Write-Host ('  headers: ' + $heads + ' | pager total: ' + $tot)
Ok (($heads -match [regex]::Escape($recvPaid)) -and ($heads -match [regex]::Escape($recvUnpaid))) 'receivable list uses its own wording (received)'
Ok ($tot -eq $nRecv) ('receivable filter returns exactly the receivable bills (' + $tot + ' == ' + $nRecv + ')')

Write-Host '--- B3) payable state ---'
ApplyType $payOpt
$heads = Heads
$tot = PagerTotal
Write-Host ('  headers: ' + $heads + ' | pager total: ' + $tot)
Ok (($heads -match [regex]::Escape($payPaid)) -and ($heads -match [regex]::Escape($payUnpaid))) 'payable list uses its own wording (paid/payable)'
Ok ($tot -eq $nPay) ('payable filter returns exactly the payable bills (' + $tot + ' == ' + $nPay + ')')

Write-Host '--- B4) switching back to all ---'
ApplyType $allOpt
$tot = PagerTotal
$dis = PartnerDisabled
Write-Host ('  pager total: ' + $tot + ' | partner filter: ' + $dis)
Ok ($tot -eq $nAll) ('switching back to all returns every bill again (' + $tot + ' == ' + $nAll + ')')
Ok ($dis -match 'INPUTDISABLED') 'partner filter is disabled again after switching back to all'

Write-Host ('errs=' + (Errs))
Ok ((Errs) -eq '[]') 'bill list recorded no JS/API errors'
Summary 'verify bill list type filter (all option + type-aware wording)'
