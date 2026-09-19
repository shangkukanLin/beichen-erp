# I4 / I5 hint-text verification (2026-09-18, UI assertions).
#   I5: finished-goods warehouse ADD/EDIT dialog shows the "aux warehouse lives under material-warehouse" hint
#   I4: dev project detail > BOM tab states that quantity/loss-rate are read-only here (tunable at work-order time)
# ASCII ONLY - the Chinese literals live in ui-e2e-zh.json and are passed in as base64 (BOM-less .ps1 is read
# as ANSI by PowerShell 5.1, so any Chinese source text would be mangled).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

# NOTE: the shared lib does NOT define these -> every script must carry its own copies (lesson I19).
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }

EnsureLogin
WatchErrors

Step 'I5: warehouse dialog shows the aux-warehouse hint'
Open '/inventory/warehouse' 3000
Write-Host ('  page body len=' + (EvalJs "String(document.body.innerText.length)"))
Write-Host ('  open add dialog: ' + (ClickText (ZH 'btn_new_text')))
Start-Sleep -Milliseconds 1600
$dlgJs = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];return d?((d.innerText||'').replace(/\s+/g,'')):'NODIALOG'})()"
$dlg = EvalJs $dlgJs
Write-Host ('  dialog text len=' + $dlg.Length)
Ok (($dlg -match [regex]::Escape((ZH 'hint_wh_aux')))) 'I5 hint is rendered inside the warehouse dialog'
# close the dialog (first footer button = 取消)
$closeJs = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODIALOG';const b=[...d.querySelectorAll('.el-dialog__footer button')].filter(vis);if(!b.length)return 'NOBTN';b[0].click();return 'OK'})()"
Write-Host ('  close dialog: ' + (EvalJs $closeJs))
Start-Sleep -Milliseconds 800

Step 'I4: dev project BOM tab shows the read-only hint'
$projId = [int](D (SqlOne 'SELECT id FROM dev_project ORDER BY id LIMIT 1'))
Write-Host ('  project id=' + $projId)
Ok (($projId -gt 0)) ('found a dev project to open (id=' + $projId + ')')
if ($projId -gt 0) {
  Open ('/dev/project/edit/' + $projId) 3400
  Write-Host ('  open BOM tab: ' + (ClickText (ZH 'tab_bom')))
  Start-Sleep -Milliseconds 1600
  $bomJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ps=[...document.querySelectorAll('.el-tab-pane')].filter(vis);const t=ps.map(x=>(x.innerText||'')).join(' ').replace(/\s+/g,'');return t})()"
  $bomTxt = EvalJs $bomJs
  Write-Host ('  bom pane text len=' + $bomTxt.Length)
  Ok (($bomTxt -match [regex]::Escape((ZH 'hint_bom_qty')))) 'I4 hint is rendered in the BOM tab'
}

Summary 'I4/I5 hint texts'
