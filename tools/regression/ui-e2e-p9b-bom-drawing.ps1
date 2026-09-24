# P9b (2026-09-18): 研发 BOM 补足 12 条 + 探测「图纸」页签（BOM 总览页/图纸总览页 2026-09-16 已下线，
#   两者都在「研发立项」编辑页的页签内维护）。
# ALL DATA KEPT; rerunnable. ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
$pass = 0; $fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:pass++; Write-Host ('PASS ' + $m) } else { $script:fail++; Write-Host ('FAIL ' + $m) } }
$tablesDump = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);return JSON.stringify(ts.map((t,i)=>({i:i,head:[...t.querySelectorAll('thead th')].map(h=>(h.innerText||'').trim()),rows:[...t.querySelectorAll('.el-table__body tbody tr')].length})))})()"
$btnsDump = "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('button')].filter(vis).map(b=>(b.innerText||'').trim()).filter(t=>t))})()"
$tabsDump = "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-tabs__item')].filter(vis).map(x=>(x.innerText||'').trim()))})()"

$TARGET = 12
$bomBefore = D (SqlOne 'SELECT COUNT(*) FROM dev_bom')
$projId = [int](SqlOne 'SELECT id FROM dev_project ORDER BY id LIMIT 1')
$projCode = SqlOne ("SELECT code FROM dev_project WHERE id=" + $projId)
$need = [int]([Math]::Max(0, $TARGET - $bomBefore))
Write-Host ('[BASE] bom=' + $bomBefore + ' target=' + $TARGET + ' need=' + $need + ' project=' + $projId + '(' + $projCode + ')')

Step '1) open the project edit page and inspect the BOM tab'
Open '/dev/project' 3000
$idx = [int](FindRow $projCode)
if ($idx -lt 0) { $idx = 0 }
# the project list has 详情/取消立项 only (no 编辑) -> the tabs live on the DETAIL page
Write-Host ('  detail: ' + (ClickRowBtnContains $idx (ZH 'btn_open_detail')))
Start-Sleep -Milliseconds 3000
Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('  tabs=' + (EvalJs $tabsDump))
Write-Host ('  buttons=' + (EvalJs $btnsDump))
Write-Host ('  tables=' + (EvalJs $tablesDump))
Write-Host ('  bom tab: ' + (ClickText (ZH 'tab_bom_info')))
Start-Sleep -Milliseconds 2200
Write-Host ('  tables(bom)=' + (EvalJs $tablesDump))
Write-Host ('  buttons(bom)=' + (EvalJs $btnsDump))

Step '2) add BOM lines until ' + $TARGET
for ($k = 1; $k -le $need; $k++) {
  $before = D (SqlOne 'SELECT COUNT(*) FROM dev_bom')
  Write-Host ('--- bom line #' + $k + ' (db=' + $before + ')')
  Write-Host ('  add: ' + (ClickBtn 'btn_add_bom'))
  Start-Sleep -Milliseconds 1500
  Write-Host ('  tables=' + (EvalJs $tablesDump))
  # fill the LAST row: first select = 类型, second = 物料, numeric input = 数量 (layout may vary -> dump after)
  $rowJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(!rs.length)return 'NOROW';const r=rs[rs.length-1];return (r.innerText||'').replace(/\s+/g,' ')+' IN='+[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value).join('/')})()"
  Write-Host ('  lastRow=' + (EvalJs $rowJs))
  # pick options for the row selects (types/materials are small lists -> take the first option)
  foreach ($si in @(0, 1)) {
    $sel = (EvalJs ("(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];const rs=[...t.querySelectorAll('.el-table__body tbody tr')];const r=rs[rs.length-1];const sels=[...r.querySelectorAll('.el-select')];if(sels.length<=" + ($si + 1) + ")return 'NOSEL';sels[" + $si + "].querySelector('.el-select__wrapper').click();return 'OK'})()"))
    Start-Sleep -Milliseconds 1200
    $pk = (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of ds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e));if(li.length){li[0].click();return 'PICKED'}}return 'NOOPT'})()")
    Write-Host ('  sel' + $si + ': open=' + $sel + ' pick=' + $pk)
    Start-Sleep -Milliseconds 700
  }
  $qty = (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];const rs=[...t.querySelectorAll('.el-table__body tbody tr')];const r=rs[rs.length-1];const ins=[...r.querySelectorAll('input:not([type=hidden])')];if(!ins.length)return 'NOINPUT';const el=ins[ins.length-1];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,'10');el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()")
  Write-Host ('  qty: ' + $qty)
  Start-Sleep -Milliseconds 700
  Write-Host ('  save bom: ' + (ClickBtn 'btn_save_bom'))
  Start-Sleep -Milliseconds 2600
  $msg = Txt '.el-message'
  Write-Host ('  toast=' + $msg)
  $after = D (SqlOne 'SELECT COUNT(*) FROM dev_bom')
  Write-Host ('  db ' + $before + ' -> ' + $after)
}
$bom = D (SqlOne 'SELECT COUNT(*) FROM dev_bom')
$bomQty = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM dev_bom')
$bomProj = D (SqlOne 'SELECT COUNT(DISTINCT project_id) FROM dev_bom')
Write-Host ("[DB] bomLines=$bom totalQty=$bomQty projects=" + $bomProj)
Ok (($bom -ge $TARGET)) ('BOM lines >= ' + $TARGET + ' (got ' + $bom + ')')
Ok (($bomQty -gt 0)) ('BOM total quantity > 0 (got ' + $bomQty + ')')

Step '3) drawing tab: register drawings (fileless registration is allowed by the page)'
Write-Host ('  drawing tab: ' + (ClickText (ZH 'tab_drawing')))
Start-Sleep -Milliseconds 2400
Write-Host ('  tables=' + (EvalJs $tablesDump))
Write-Host ('  buttons=' + (EvalJs $btnsDump))
$dwNeed = [int]([Math]::Max(0, 3 - (D (SqlOne 'SELECT COUNT(*) FROM dev_drawing'))))
Write-Host ('  drawings to register = ' + $dwNeed)
for ($d = 1; $d -le $dwNeed; $d++) {
  Write-Host ('--- drawing #' + $d)
  # the button label carries an emoji prefix -> click by "contains" instead of exact match
  $clickJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$(B64 (ZH 'btn_upload_drawing'))');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(x=>((x.innerText||'').indexOf(L)>=0));if(!b)return 'NOBTN';b.click();return 'OK'})()"
  Write-Host ('  open dialog: ' + (EvalJs $clickJs))
  Start-Sleep -Milliseconds 1800
  Write-Host ('  dlg items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';return JSON.stringify([...d.querySelectorAll('.el-form-item')].map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':(it.querySelector('input')?'input':'x'))}))})()"))
  Write-Host ('  dlg buttons=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';return JSON.stringify([...d.querySelectorAll('button')].map(b=>(b.innerText||'').trim()).filter(t=>t))})()"))
  $docName = 'E2E-DWG-' + (Get-Date -Format 'HHmmss') + '-' + $d
  Write-Host ('  doc name: ' + (FillLabel 'lbl_doc_name' $docName))
  Start-Sleep -Milliseconds 900
  $dk = ClickDialogBtn 'btn_ok'
  if ($dk -notmatch 'OK') { $dk = ClickDialogBtn 'btn_save' }
  Write-Host ('  submit: ' + $dk)
  Start-Sleep -Milliseconds 2600
  Write-Host ('  toast=' + (Txt '.el-message'))
  $dwNow = D (SqlOne 'SELECT COUNT(*) FROM dev_drawing')
  Write-Host ('  db drawings=' + $dwNow)
  if ($dwNow -eq 0) { Write-Host '  (dialog not submitted - stopping)'; break }
}
$dw = D (SqlOne 'SELECT COUNT(*) FROM dev_drawing')
$dwUrl = D (SqlOne "SELECT COUNT(*) FROM dev_drawing WHERE file_url IS NULL OR file_url=''")
Write-Host ("[DB] drawings=$dw fileless=$dwUrl (plan 3)")
Ok (($dw -ge 3)) ('drawings >= 3 (got ' + $dw + ')')

Write-Host ('errs=' + (Errs))
Write-Host ('RESULT bom/drawing PASS=' + $pass + ' FAIL=' + $fail)
