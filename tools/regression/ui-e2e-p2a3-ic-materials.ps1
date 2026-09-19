# P2a-3 (2026-09-18): add materials of types 触摸IC / 码片IC.
# Why: the 研发立项 add page requires 改配信息-驱动IC/触摸IC/码片IC, and those dropdowns are filtered by
#      material TYPE. P2a only created 玻璃/排线/驱动IC, so 触摸IC/码片IC were empty and project creation
#      was blocked with "请选择改配信息-触摸IC". Materials are KEPT (no deletion). ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Has([string]$t) { return ((BodyHas $t) -match 'true') }

Step 'materials of type touchIC / codeIC (idx 21..28)'
$plan = @(
  @{ i = 21; t = 'opt_mt_touch' }, @{ i = 22; t = 'opt_mt_code' },
  @{ i = 23; t = 'opt_mt_touch' }, @{ i = 24; t = 'opt_mt_code' },
  @{ i = 25; t = 'opt_mt_touch' }, @{ i = 26; t = 'opt_mt_code' },
  @{ i = 27; t = 'opt_mt_touch' }, @{ i = 28; t = 'opt_mt_code' }
)
foreach ($p in $plan) {
  $nm = (ZH 'val_material') + $p.i
  Open '/outsource/material-info' 1900
  if ((Has $nm) -match 'true') { Write-Host ('skip existing: ' + $nm); continue }
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 900
  $rt = SelectLabel 'lbl_material_type' $p.t
  Start-Sleep -Milliseconds 500
  FillLabel 'lbl_material_name' $nm | Out-Null
  ClickDialogBtn 'btn_ok' 1200 | Out-Null
  Start-Sleep -Milliseconds 1300
  Open '/outsource/material-info' 1700
  Ok (Has $nm) ('material created: ' + $nm + ' type=' + $p.t + ' (' + $rt + ')')
}
Write-Host ('errs=' + (Errs))
Summary 'P2a-3 IC materials'
