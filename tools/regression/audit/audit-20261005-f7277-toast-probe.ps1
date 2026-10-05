# audit 2026-10-05 - F7-277 diagnosis: why is the bill-export guard's toast assertion always red?
# Installs a MutationObserver BEFORE the click so no toast can be missed by polling, then reports
# (a) every message-like element that appeared, (b) the DOM state right after, (c) the export request.
# READ ONLY against the app (it triggers one export download). ASCII ONLY in every printed string.
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'ui-e2e-lib.ps1')
Write-Host 'STEP 0: lib loaded, logging in...'   # heartbeat: this file must never run silent (idle-timeout killed it once)
EnsureLogin
Write-Host 'STEP 1: logged in, opening the bill detail page...'
$billId = 198   # the fixture the export guard itself used (its perf entry showed /finance/bill/198/export)

Open ('/finance/bill/detail/' + $billId) 3600
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1700

$btnTxt = ZH 'btn_export_excel_ws'
$bb = B64 $btnTxt

# 1) install the observer first (records the text of any element whose class contains "message")
$obs = EvalJs "(()=>{if(window.__mObs)return 'ALREADY';window.__msgs=[];const B=s=>btoa(unescape(encodeURIComponent(s||'')));const rec=el=>{try{const c=el.className?String(el.className):'';if(c.indexOf('message')>=0){const t=(el.innerText||'').trim();if(t)window.__msgs.push(B(t))}}catch(e){}};const scan=n=>{if(n&&n.nodeType===1){rec(n);if(n.querySelectorAll)n.querySelectorAll('[class*=message]').forEach(rec)}};window.__mObs=new MutationObserver(ms=>{ms.forEach(m=>{if(m.addedNodes)m.addedNodes.forEach(scan)})});window.__mObs.observe(document.body,{childList:true,subtree:true});return 'OBSERVING'})()"
Write-Host ('observer: ' + $obs)

# 2) click the export button by EXACT text (same way the guard does)
$found = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$bb');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(e=>(e.innerText||'').trim()===N);if(!b)return 'NOBTN';b.click();return 'CLICKED'})()"
Write-Host ('click: ' + $found)

# 3) wait well past the toast's ~1s lifetime, then read what the observer captured
Start-Sleep -Milliseconds 5200
$js = "(()=>{const B=s=>btoa(unescape(encodeURIComponent(s||'')));const m=(window.__msgs||[]).slice(0,6);const cur=document.querySelector('.el-message');return JSON.stringify({n:(window.__msgs||[]).length,msgs:m,cur:cur?B((cur.innerText||'').trim()):'',msgEls:document.querySelectorAll('[class*=message]').length,perf:performance.getEntriesByType('resource').filter(x=>x.name.indexOf('/export')>=0).length})})()"
$raw = ((EvalJs $js) -replace '\s', '')   # strip whitespace: the EvalJs return path can carry trailing junk
Write-Host ('raw: ' + $raw)
try {
  $o = $raw | ConvertFrom-Json
  Write-Host ('captured count = ' + $o.n + ' ; message-like elements now = ' + $o.msgEls + ' ; /export requests = ' + $o.perf)
  $i = 0
  foreach ($m in @($o.msgs)) { $i++; Write-Host ('  msg[' + $i + '] = [' + (Dec $m) + ']') }
  Write-Host ('  current .el-message = [' + (Dec $o.cur) + ']')
  if ([int]$o.n -ge 1) { Write-Host 'DIAG: the toast DOES appear -> the guard''s .el-message polling is what misses it' }
  else { Write-Host 'DIAG: NO message element ever appeared -> the toast is not rendered in this environment' }
} catch { Write-Host 'DIAG: could not parse the probe result' }
Write-Host ('page errors: ' + (Errs))
