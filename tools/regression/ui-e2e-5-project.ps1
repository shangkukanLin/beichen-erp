# Temp test 5: R&D project creation through the UI (needed by work order)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
$P = ZH 'val_proj'
Open '/dev/project' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 2200
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('proj name: ' + (FillLabel 'lbl_proj_name' $P))
Write-Host ('assy name: ' + (FillLabel 'lbl_assy_name' $P))
Write-Host ('orig size: ' + (FillLabel 'lbl_orig_size' '6.1'))
Write-Host ('orig res: ' + (FillLabel 'lbl_orig_res' '1080x2340'))
Write-Host ('drv ic txt: ' + (FillLabel 'lbl_drv_ic' 'RM692E5'))
Write-Host ('touch ic txt: ' + (FillLabel 'lbl_touch_ic' 'FT8006'))
Write-Host ('glass size: ' + (FillLabel 'lbl_glass_size' '6.1'))
Write-Host ('glass res: ' + (FillLabel 'lbl_glass_res' '1080x2340'))
Start-Sleep -Milliseconds 500
Write-Host ('brand: ' + (SelectLabelContains 'lbl_brand' (ZH 'val_brand')))
Write-Host ('drv ic sel: ' + (OpenSelectLabelIdx 'lbl_drv_ic' 1))
Write-Host ('pick drv: ' + (PickFirstOption 1400))
Write-Host ('touch ic sel: ' + (OpenSelectLabelIdx 'lbl_touch_ic' 1))
Write-Host ('pick touch: ' + (PickFirstOption 1400))
Write-Host ('chip ic sel: ' + (OpenSelectLabelIdx 'lbl_chip_ic' 0))
Write-Host ('pick chip: ' + (PickFirstOption 1400))
Write-Host ('create: ' + (ClickBtn 'btn_create_proj'))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/dev/project' 2600
$r = Rows 0
Write-Host ('projects=' + $r.n + ' head=' + ($r.head -join '|'))
$idx = [int](FindRow $P)
Ok ($idx -ge 0) 'project created and listed'
if ($idx -ge 0) { Write-Host ('row=' + ($r.rows[$idx] -join ' | ')) }
Summary 'project chain'
