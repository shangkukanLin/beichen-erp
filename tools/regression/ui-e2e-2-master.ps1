# Temp test 2: master data through the UI (brand / customer / product / vendor / supplier / material / template phase)
# 2026-09-24 修复假红：客户/产品/供货商/供应商 4 页的「新增」已由弹框改为**独立页**
# （/inventory/customer/add、/product/add、/outsource/supplier/manage/add、/supplier/manage/add），
# 原脚本仍按弹框操作（ClickDialogBtn/ClickDialogText + 弹框里的 label）⇒ 建单静默失败、末尾 body 断言才挂。
# 现按各页真实文案与按钮改造（本文件里的 Step 只做输出，断言仍是末尾的 BodyHas）。
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
Write-Host ('url=' + (EvalJs 'String(location.pathname)'))
Write-Host ('fill name: ' + (FillLabel 'lbl_cust_name' (V 'val_customer')))
Write-Host ('fill contact: ' + (FillLabel 'lbl_contact' 'auto'))
Write-Host ('fill phone: ' + (FillLabel 'lbl_phone2' '13800000000'))
Write-Host ('select status: ' + (SelectLabelText 'lbl_status' (ZH 'opt_ing')))
Write-Host ('click save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2200
Write-Host ('url=' + (EvalJs 'String(location.pathname)'))
Open '/inventory/customer' 1800
Ok ((BodyHas (V 'val_customer')) -match 'true') ('customer created and listed ' + (V 'val_customer'))
Write-Host ('errs=' + (Errs))

# ---------- 3. product ----------
# product/add 走 material/detail.vue，必填项 = 产品名称 / 规格 / 状态（缺一保存被校验拦下）
Step 'product'
Open '/product' 2000
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1800
Write-Host ('fill name: ' + (FillLabel 'lbl_prod_name' (V 'val_product')))
Write-Host ('select brand: ' + (SelectLabelText 'lbl_brand' (V 'val_brand')))
Start-Sleep -Milliseconds 600
Write-Host ('select spec: ' + (OpenSelect 'lbl_spec') + ' -> ' + (PickFirstOption 900))
Write-Host ('select status: ' + (OpenSelect 'lbl_status') + ' -> ' + (PickFirstOption 900))
Write-Host ('click save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2400
Write-Host ('msg=' + (Txt '.el-message'))
Open '/product' 1900
$tb = Rows 0
Write-Host ('product rows=' + $tb.n)
Ok ((BodyHas (V 'val_product')) -match 'true') ('product created and listed ' + (V 'val_product'))
Write-Host ('errs=' + (Errs))

# ---------- 4. vendor (供货商) ----------
# 类型在供货商侧是**预勾选**的（manage-form: checkedTypes=['product']）；该页保存按钮是页脚「确定」
Step 'vendor'
Open '/outsource/supplier/manage' 2200
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1800
Write-Host ('fill name: ' + (FillLabel 'lbl_vendor_name' (V 'val_vendor')))
Write-Host ('click save: ' + (ClickBtn 'btn_ok'))
Start-Sleep -Milliseconds 2200
Write-Host ('msg=' + (Txt '.el-message'))
Open '/outsource/supplier/manage' 1900
Ok ((BodyHas (V 'val_vendor')) -match 'true') ('vendor created and listed ' + (V 'val_vendor'))
Write-Host ('errs=' + (Errs))

# ---------- 5. supplier (加工厂) ----------
# 供应商侧类型默认为空且**必填**；列表默认「全部」页签会排除成品商 ⇒ 勾「加工厂」
# （与原意图 supplier(factory) 一致，且不会被页签过滤掉 ⇒ 建完能在列表里断言到）
Step 'supplier'
Open '/supplier/manage' 2200
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1800
Write-Host ('boxes before: ' + (CheckedBoxes))
Write-Host ('check factory: ' + (ClickText (ZH 'opt_role_factory')))
Start-Sleep -Milliseconds 500
Write-Host ('boxes after: ' + (CheckedBoxes))
Write-Host ('fill name: ' + (FillLabel 'lbl_supplier_name' (V 'val_factory')))
Write-Host ('click save: ' + (ClickBtn 'btn_ok'))
Start-Sleep -Milliseconds 2200
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
