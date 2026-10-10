# temporary probe: does the material-order list actually render action buttons?
# Output is ASCII-only (counts), so no Chinese round-trip issues. ASCII ONLY.
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'ui-e2e-lib.ps1')
$ErrorActionPreference = 'Continue'

Open '/dashboard' 2400
EvalJs "localStorage.removeItem('beichen_erp_token'); localStorage.removeItem('beichen_erp_user'); 'cleared'" | Out-Null
Start-Sleep -Milliseconds 500
EnsureLogin
WatchErrors

foreach ($p in @('/outsource/material-order', '/outsource/order')) {
  Open $p 5000
  Start-Sleep -Milliseconds 2500
  $js = "(function(){const vis=e=>e.getClientRects().length>0;" +
        "const tbls=[].slice.call(document.querySelectorAll('.el-table')).filter(vis);" +
        "const rows=[].slice.call(document.querySelectorAll('.el-table__body-wrapper tbody tr')).filter(r=>r.querySelectorAll('td').length>0);" +
        "const r0=rows[0];const tds=r0?[].slice.call(r0.querySelectorAll('td')):[];" +
        "const btnPerTd=tds.map(td=>td.querySelectorAll('button').length);" +
        "const linkPerTd=tds.map(td=>td.querySelectorAll('a,.el-link').length);" +
        "return JSON.stringify({tables:tbls.length,rows:rows.length,rc:rows.length?rows[0].querySelector('td')!=null:false," +
        "tds:tds.length,btnPerTd:btnPerTd,linkPerTd:linkPerTd,totalBtn:r0?r0.querySelectorAll('button').length:0});})()"
  Write-Host ('  ' + $p + ' >> ' + (EvalJs $js))
}
Write-Host ('errs >> ' + (Errs))
