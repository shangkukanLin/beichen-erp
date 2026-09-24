# RETIRED 2026-09-24 -- manual material-delivery doc (outsource/delivery) was removed.
# The list/add pages were deleted and POST/PUT create+update endpoints now always reject;
# the replacement is the material warehouse-move doc: /inventory/material-move (backend
# /api/inventory/material-move), covered by verify-material-move.ps1. This script targets the
# removed entry, so it is kept for history only and must NOT be re-added to any suite.

# Temp test 4b: material issue (物料收发单 - 发料) -> audit -> un-audit -> audit
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$OWH = 'TEST-MAT-WH'

Step 'create issue doc'
Open '/outsource/delivery' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 2000
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('type default=' + (Txt '.el-form-item'))
Write-Host ('open factory sel: ' + (OpenSelectIdx 1))
Start-Sleep -Milliseconds 1600
Write-Host ('pick factory: ' + (PickOptionContains (ZH 'val_factory')))
Start-Sleep -Milliseconds 900
Write-Host ('open from-wh sel: ' + (OpenSelectIdx 2))
Start-Sleep -Milliseconds 1400
Write-Host ('pick from-wh: ' + (PickOptionContains $OWH))
Start-Sleep -Milliseconds 900
Write-Host ('open to-wh sel: ' + (OpenSelectIdx 3))
Start-Sleep -Milliseconds 1400
Write-Host ('pick to-wh: ' + (PickOptionContains (ZH 'val_factory')))
Start-Sleep -Milliseconds 900
Write-Host ('add row: ' + (ClickBtn 'btn_add_material_plus'))
Start-Sleep -Milliseconds 900
Write-Host ('open type: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1000
Write-Host ('pick type: ' + (PickOptionContains (ZH 'opt_mt_glass')))
Start-Sleep -Milliseconds 1000
Write-Host ('open material: ' + (OpenRowSelect 0 1))
Start-Sleep -Milliseconds 1200
Write-Host ('pick material: ' + (PickOptionContains (ZH 'val_material')))
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 3 '10'))
Start-Sleep -Milliseconds 500
$dump = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];const r=t.querySelectorAll('.el-table__body tbody tr')[0];return JSON.stringify({row:(r.innerText||'').replace(/\s+/g,' '),vals:[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)})})()"
Write-Host ('ROWDUMP ' + (EvalJs $dump))
Write-Host ('submit: ' + (ClickBtn 'btn_submit_confirm'))
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))

Open '/outsource/delivery' 2400
$r = Rows 0
Write-Host ('docs=' + $r.n)
$idx = [int](FindRow (ZH 'st_draft'))
Write-Host ('draft row=' + $idx)
Ok ($idx -ge 0 -and (($r.rows[$idx] -join ' ') -match $OWH)) 'material issue doc created as draft'
if ($idx -lt 0) { Summary 'material issue chain'; exit 1 }
Write-Host ('our row=' + ($r.rows[$idx] -join ' | '))

Step 'audit issue doc'
Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/outsource/delivery' 2400
$r = Rows 0
$idx2 = [int](FindRow $OWH)
Write-Host ('row after audit=' + ($r.rows[$idx2] -join ' | '))
Ok (($r.rows[$idx2] -join ' ') -match (ZH 'st_audited')) 'material issue doc audited'

Step 'warehouse detail (material stock in outsource warehouse)'
$wb = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];const rs=[...t.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){if((rs[i].innerText||'').indexOf('TEST')<0)continue;const bs=[...rs[i].querySelectorAll('button')];for(const b of bs){if((b.innerText||'').trim()==='\u8be6\u60c5'){b.click();return 'OK:'+i}}}return 'NONE'})()"
Write-Host ('open target wh detail: ' + (EvalJs $wb))
Start-Sleep -Milliseconds 2600
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
$wd = Rows 0
Write-Host ('wh detail tables rows=' + $wd.n + ' head=' + ($wd.head -join '|'))
foreach ($rw in $wd.rows) { Write-Host ('whstock=' + ($rw -join ' | ')) }
Write-Host ('errs=' + (Errs))

Step 'un-audit then re-audit'
Open '/outsource/delivery' 2400
$r = Rows 0
$idx = [int](FindRow $OWH)
Write-Host ('un-audit: ' + (ClickRowBtnContains $idx (ZH 'btn_unaudit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message'))
Open '/outsource/delivery' 2400
$r = Rows 0
$idx = [int](FindRow $OWH)
Write-Host ('row after un-audit=' + ($r.rows[$idx] -join ' | '))
Write-Host ('audit again: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message'))
Open '/outsource/delivery' 2400
$r = Rows 0
$idx = [int](FindRow $OWH)
Write-Host ('row final=' + ($r.rows[$idx] -join ' | '))
Write-Host ('errs=' + (Errs))
Summary 'material issue chain'
