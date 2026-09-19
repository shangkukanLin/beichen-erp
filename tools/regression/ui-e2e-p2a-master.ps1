# P2a (2026-09-18 full-flow E2E): batch MASTER DATA through the frontend only.
# Volumes: brands 3 / materials 20 / products 12 / customers 10 / vendors 8 / factories 5 / warehouses 4 / accounts 3
# Rules: EVERYTHING IS KEPT (user instruction: do not delete test data). ASCII ONLY (Chinese via zh.json keys).
# Output: e2e-seed.json (generated names, UTF-8) so later phases can reference exact entity names.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Has([string]$t) { return ((BodyHas $t) -match 'true') }

$seed = [ordered]@{}
$N_BRAND = 3; $N_MAT = 20; $N_PROD = 12; $N_CUST = 10; $N_VENDOR = 8; $N_FACTORY = 5; $N_ACC = 3

# ---------------- 1. brands ----------------
Step ("brands x" + $N_BRAND)
$brands = @()
for ($i = 1; $i -le $N_BRAND; $i++) {
  $nm = (ZH 'val_brand') + $i
  $brands += $nm
  Open '/inventory/brand' 1800
  if (Has $nm) { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new_brand' | Out-Null
  Start-Sleep -Milliseconds 800
  FillLabel 'lbl_brand_name' $nm | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1200
  Open '/inventory/brand' 1600
  Ok (Has $nm) ('brand created: ' + $nm)
}
$seed.brands = $brands

# ---------------- 2. materials ----------------
Step ("materials x" + $N_MAT)
$matTypes = @('opt_mt_glass', 'opt_mt_line', 'opt_drv')
$materials = @()
for ($i = 1; $i -le $N_MAT; $i++) {
  $nm = (ZH 'val_material') + $i
  $materials += $nm
  Open '/outsource/material-info' 1800
  if (Has $nm) { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 900
  SelectLabel 'lbl_material_type' $matTypes[($i - 1) % 3] | Out-Null
  Start-Sleep -Milliseconds 500
  FillLabel 'lbl_material_name' $nm | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1200
  Open '/outsource/material-info' 1600
  Ok (Has $nm) ('material created: ' + $nm)
}
$seed.materials = $materials

# ---------------- 3. products (each linked to a brand) ----------------
Step ("products x" + $N_PROD)
$products = @()
for ($i = 1; $i -le $N_PROD; $i++) {
  $nm = (ZH 'val_product') + $i
  $products += $nm
  Open '/product' 1800
  if (Has $nm) { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 1400
  FillLabel 'lbl_name' $nm | Out-Null
  $b = $brands[($i - 1) % $N_BRAND]
  $rb = SelectLabelText 'lbl_brand' $b
  Start-Sleep -Milliseconds 400
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 1800
  Open '/product' 1600
  Ok (Has $nm) ('product created: ' + $nm)
  if (($rb -notmatch 'OK') -and ($i -eq 1)) { Write-Host ('WARN brand pick on 1st product: ' + $rb) }
}
$seed.products = $products

# ---------------- 4. customers ----------------
Step ("customers x" + $N_CUST)
$customers = @()
for ($i = 1; $i -le $N_CUST; $i++) {
  $nm = (ZH 'val_customer') + $i
  $customers += $nm
  Open '/inventory/customer' 1800
  if (Has $nm) { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 1500
  FillLabel 'lbl_name' $nm | Out-Null
  FillLabel 'lbl_contact' 'auto' | Out-Null
  FillLabel 'lbl_phone' ('1380000' + ('{0:D4}' -f $i)) | Out-Null
  SelectLabelText 'lbl_status' (ZH 'opt_ing') | Out-Null
  Start-Sleep -Milliseconds 400
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 1800
  Open '/inventory/customer' 1600
  Ok (Has $nm) ('customer created: ' + $nm)
}
$seed.customers = $customers

# ---------------- 5. vendors (供货商, purchase side) ----------------
Step ("vendors x" + $N_VENDOR)
$vendors = @()
for ($i = 1; $i -le $N_VENDOR; $i++) {
  $nm = (ZH 'val_vendor') + $i
  $vendors += $nm
  Open '/outsource/supplier/manage' 1900
  if (Has $nm) { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 1000
  FillLabel 'lbl_name' $nm | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1200
  Open '/outsource/supplier/manage' 1600
  Ok (Has $nm) ('vendor created: ' + $nm)
}
$seed.vendors = $vendors

# ---------------- 6. factories (加工厂 = supplier with role checkbox) ----------------
Step ("factories x" + $N_FACTORY)
$factories = @()
for ($i = 1; $i -le $N_FACTORY; $i++) {
  $nm = (ZH 'val_factory') + $i
  $factories += $nm
  Open '/supplier/manage' 1900
  if (Has $nm) { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 1000
  ClickDialogText 'opt_role_factory' | Out-Null
  Start-Sleep -Milliseconds 400
  FillLabel 'lbl_name' $nm | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1500
  Open '/supplier/manage' 1600
  Ok (Has $nm) ('factory created: ' + $nm)
}
$seed.factories = $factories

# ---------------- 7. warehouses (2x 成品仓 + 2x 辅料仓; 委外仓 comes with each factory) ----------------
Step 'warehouses'
$whs = @(
  @{ name = (ZH 'wh_finished2'); type = 'opt_inventory' },
  @{ name = (ZH 'wh_aux1'); type = 'opt_aux' },
  @{ name = (ZH 'wh_aux2'); type = 'opt_aux' }
)
$createdWh = @()
foreach ($w in $whs) {
  Open '/inventory/warehouse' 1900
  if (Has $w.name) { Write-Host ('skip existing: ' + $w.name); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 1000
  FillLabel 'lbl_name' $w.name | Out-Null
  SelectLabelText 'lbl_wh_type' (ZH $w.type) | Out-Null
  Start-Sleep -Milliseconds 500
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1400
  Open '/inventory/warehouse' 1600
  Ok (Has $w.name) ('warehouse created: ' + $w.name)
  $createdWh += $w.name
}
$seed.warehouses = $createdWh

# ---------------- 8. finance accounts ----------------
Step ("accounts x" + $N_ACC)
$accts = @(
  @{ name = 'CASH-01'; type = 'opt_cash'; open = '100000' },
  @{ name = 'BANK-01'; type = 'opt_bank'; open = '500000' },
  @{ name = 'WX-01'; type = 'opt_wechat'; open = '20000' }
)
foreach ($a in $accts) {
  Open '/finance/account' 1900
  if (Has $a.name) { Write-Host ('skip existing: ' + $a.name); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new_account' | Out-Null
  Start-Sleep -Milliseconds 1000
  FillLabel 'lbl_name' $a.name | Out-Null
  SelectLabelText 'lbl_type' (ZH $a.type) | Out-Null
  Start-Sleep -Milliseconds 400
  FillLabel 'lbl_initial_balance' $a.open | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1400
  Open '/finance/account' 1600
  Ok (Has $a.name) ('account created: ' + $a.name)
}

$seed | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'e2e-seed.json')
Write-Host 'seed -> e2e-seed.json'
Summary 'P2a master data batch'
