# TEMP diagnostic (deleted after use). ASCII ONLY.
# User report: /inventory/purchase (chengpin purchase order list) still scrolls horizontally.
# Root-cause hypothesis: my earlier sweep only measured ONE viewport (1262px). A narrower window
# shrinks the content area, so a column set that fits at 1262 can overflow below that.
# Measure the same table at several viewport widths and report where it starts to scroll.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
EnsureLogin | Out-Null

$js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return JSON.stringify({tables:ts.length});const bw=t.querySelector('.el-table__body-wrapper');const rows=[...t.querySelectorAll('.el-table__header tr')];const ths=[...rows[rows.length-1].querySelectorAll('th')];let sum=0;const w=[];ths.forEach(th=>{const r=Math.round(th.getBoundingClientRect().width);w.push(r);sum+=r;});const tr=t.getBoundingClientRect();const card=t.closest('.el-card__body');const doc=document.documentElement;return JSON.stringify({vw:window.innerWidth,tables:ts.length,tableW:Math.round(tr.width),cardW:card?Math.round(card.getBoundingClientRect().width):-1,colSum:sum,cols:w.length,ov:bw?Math.round(bw.scrollWidth-bw.clientWidth):-1,pageOv:Math.round(doc.scrollWidth-doc.clientWidth),fixedRight:document.querySelectorAll('.el-table__fixed-right').length,colW:w})})()"

foreach ($w in @(1280, 1200, 1152, 1024, 1440, 1600)) {
  agent-browser viewport $w 900 | Out-Null
  Start-Sleep -Milliseconds 400
  Open '/inventory/purchase' 2000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 700
  Write-Host ('  viewport=' + $w + ' -> ' + (EvalJs $js))
}
Write-Host ('  errs = ' + (Errs))
