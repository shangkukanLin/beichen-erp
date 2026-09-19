# UI check 13: BOM snapshot visibility (2026-09-17)
#  1) work order detail: BOM snapshot version + origin text
#  2) project edit page, "BOM" tab: history snapshot dialog (version/origin/items/linked orders)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

Open '/outsource/order/detail/28' 3200
Ok ((BodyHas (ZH 'txt_bom_version')) -eq 'True') 'work order detail shows BOM version tag'
$origin = (((BodyHas (ZH 'txt_snap_migrated')) -eq 'True') -or ((BodyHas (ZH 'txt_snap_bom_ok')) -eq 'True') -or ((BodyHas (ZH 'txt_snap_adjusted')) -eq 'True'))
Ok $origin 'work order detail shows snapshot origin text'
Write-Host ('errs=' + (Errs))

Open '/dev/project/edit/9?tab=bom' 3400
Write-Host ('tab click: ' + (ClickText (ZH 'tab_bom_info')))
Start-Sleep -Milliseconds 1500
Write-Host ('open snapshot dialog: ' + (ClickBtn 'btn_bom_snapshot'))
Start-Sleep -Milliseconds 2200
Write-Host ('expand first snapshot: ' + (EvalJs "(()=>{const ds=[...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0);if(!ds.length)return 'NODIALOG';const dl=ds[ds.length-1];const ic=dl.querySelector('.el-table__body tbody tr .el-table__expand-icon');if(!ic)return 'NOICON';ic.click();return 'OK'})()"))
Start-Sleep -Milliseconds 1400
Ok ((BodyHas (ZH 'txt_one_set_qty')) -eq 'True') 'snapshot dialog shows item columns'
Ok ((BodyHas (ZH 'txt_snap_migrated')) -eq 'True') 'snapshot dialog shows snapshot source'
Ok ((BodyHas (ZH 'ord_wo6')) -eq 'True') 'snapshot dialog shows linked work order'
Write-Host ('errs2=' + (Errs))
Summary 'bom snapshot ui'
