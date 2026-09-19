# P2a-2 (2026-09-18): fix warehouse types created in P2a.
# Cause: /inventory/warehouse (成品仓库管理) only offers the FINISHED type, so the two
#        warehouses meant to be 辅料仓(AUXILIARY) were created as 成品仓.
# Fix (no deletion, UI only):
#   1) rename them to 成品三号仓 / 成品四号仓 (they ARE valid finished warehouses)
#   2) create the two real AUX warehouses on the correct page: 物料仓库 -> 自有物料仓
# ASCII ONLY (Chinese comes from zh.json keys).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Has([string]$t) { return ((BodyHas $t) -match 'true') }

Step 'rename mis-typed warehouses on /inventory/warehouse'
$ren = @(
  @{ from = (ZH 'wh_aux1'); to = (ZH 'wh_finished3') },
  @{ from = (ZH 'wh_aux2'); to = (ZH 'wh_finished4') }
)
foreach ($r in $ren) {
  Open '/inventory/warehouse' 2200
  if ((Has $r.from) -match 'true') {
    $idx = [int](FindRow $r.from)
    Write-Host ('row idx(' + $r.from + ')=' + $idx)
    Ok ($idx -ge 0) ('found warehouse to rename: ' + $r.from)
    if ($idx -ge 0) {
      Write-Host ('click edit: ' + (ClickRowBtnContains $idx (ZH 'btn_edit')))
      Start-Sleep -Milliseconds 1500
      Write-Host ('fill new name: ' + (FillLabel 'lbl_name' $r.to))
      Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok' 1200))
      Start-Sleep -Milliseconds 1500
      Open '/inventory/warehouse' 2000
      Ok (Has $r.to) ('renamed to ' + $r.to)
    }
  } else {
    Write-Host ('already renamed or missing: ' + $r.from)
    Ok (Has $r.to) ('already in target name: ' + $r.to)
  }
}

Step 'create AUX warehouses on /outsource/material-warehouse'
$auxNames = @((ZH 'wh_auxA'), (ZH 'wh_auxB'))
foreach ($nm in $auxNames) {
  Open '/outsource/material-warehouse' 2200
  if ((Has $nm) -match 'true') { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 1100
  FillLabel 'lbl_name' $nm | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1500
  Open '/outsource/material-warehouse' 2000
  Ok (Has $nm) ('AUX warehouse created: ' + $nm)
}
Write-Host ('errs=' + (Errs))
Summary 'P2a-2 warehouse type fix'
