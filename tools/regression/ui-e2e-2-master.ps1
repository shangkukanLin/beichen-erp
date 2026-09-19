# Temp test 2: master data through the UI (brand / customer / product / vendor / supplier / material / template phase)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
$SFX = '' + (Get-Random -Minimum 10 -Maximum 99)
function V($k) { return (ZH $k) + $SFX }
function Step($name) { Write-Host ('--- STEP ' + $name) }

# ---------- 1. brand ----------
Step 'brand'
Open '/inventory/brand' 2000
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new_brand'))
Start-Sleep -Milliseconds 900
Write-Host ('fill name: ' + (FillLabel 'lbl_brand_name' (V 'val_brand')))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 1600
Open '/inventory/brand' 1800
Ok ((BodyHas (V 'val_brand')) -match 'true') ('brand created and listed ' + (V 'val_brand'))
Write-Host ('errs=' + (Errs))

# ---------- 2. customer ----------
Step 'customer'
Open '/inventory/customer' 2000
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1800
Write-Host ('fill name: ' + (FillLabel 'lbl_name' (V 'val_customer')))
Write-Host ('fill contact: ' + (FillLabel 'lbl_contact' 'auto'))
Write-Host ('fill phone: ' + (FillLabel 'lbl_phone' '13800000000'))
Write-Host ('select status: ' + (SelectLabelText 'lbl_status' (ZH 'opt_ing')))
Write-Host ('click save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2200
Write-Host ('url=' + (EvalJs 'String(location.pathname)'))
Open '/inventory/customer' 1800
Ok ((BodyHas (V 'val_customer')) -match 'true') ('customer created and listed ' + (V 'val_customer'))
Write-Host ('errs=' + (Errs))

# ---------- 3. product ----------
Step 'product'
Open '/product' 2000
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1800
Write-Host ('fill name: ' + (FillLabel 'lbl_name' (V 'val_product')))
Write-Host ('select brand: ' + (SelectLabelText 'lbl_brand' (V 'val_brand')))
Write-Host ('click save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2400
Write-Host ('msg=' + (Txt '.el-message'))
Open '/product' 1900
$tb = Rows 0
Write-Host ('product rows=' + $tb.n)
Ok ((BodyHas (V 'val_product')) -match 'true') ('product created and listed ' + (V 'val_product'))
Write-Host ('errs=' + (Errs))

# ---------- 4. vendor (供货商) ----------
Step 'vendor'
Open '/outsource/supplier/manage' 2200
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1200
Write-Host ('fill name: ' + (FillLabel 'lbl_name' (V 'val_vendor')))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 1800
Write-Host ('msg=' + (Txt '.el-message'))
Open '/outsource/supplier/manage' 1900
Ok ((BodyHas (V 'val_vendor')) -match 'true') ('vendor created and listed ' + (V 'val_vendor'))
Write-Host ('errs=' + (Errs))

# ---------- 5. supplier (加工厂) ----------
Step 'supplier'
Open '/supplier/manage' 2200
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1200
Write-Host ('boxes before: ' + (CheckedBoxes))
Write-Host ('check factory: ' + (ClickDialogText 'opt_role_factory'))
Start-Sleep -Milliseconds 400
Write-Host ('boxes after: ' + (CheckedBoxes))
Write-Host ('fill name: ' + (FillLabel 'lbl_name' (V 'val_factory')))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 1800
Write-Host ('msg=' + (Txt '.el-message'))
Open '/supplier/manage' 1900
Ok ((BodyHas (V 'val_factory')) -match 'true') ('supplier(factory) created and listed ' + (V 'val_factory'))
Write-Host ('errs=' + (Errs))

# ---------- 6. material info ----------
Step 'material'
Open '/outsource/material-info' 2200
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1200
Write-Host ('material type: ' + (SelectLabel 'lbl_material_type' 'opt_mt_glass'))
Write-Host ('fill name: ' + (FillLabel 'lbl_material_name' (V 'val_material')))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 1800
Write-Host ('msg=' + (Txt '.el-message'))
Open '/outsource/material-info' 1900
Ok ((BodyHas (V 'val_material')) -match 'true') ('material created and listed ' + (V 'val_material'))
Write-Host ('errs=' + (Errs))

# ---------- 7. template phase ----------
Step 'template phase'
Open '/template' 2200
ClearErrs | Out-Null
Write-Host ('click new phase: ' + (ClickBtn 'btn_new_phase'))
Start-Sleep -Milliseconds 1200
Write-Host ('fill name: ' + (FillLabel 'lbl_phase_name' (V 'val_phase')))
Write-Host ('save: ' + (ClickDialogBtn 'btn_save'))
Start-Sleep -Milliseconds 1800
Write-Host ('msg=' + (Txt '.el-message'))
Ok ((BodyHas (V 'val_phase')) -match 'true') ('template phase created ' + (V 'val_phase'))
Write-Host ('errs=' + (Errs))
Write-Host ('SFX=' + $SFX)

Summary 'master data flow'
