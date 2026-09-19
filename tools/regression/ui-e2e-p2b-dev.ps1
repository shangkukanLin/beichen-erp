# P2b (2026-09-18 full-flow E2E): R&D part through the frontend.
#   projects x2 (project page form), then per project: BOM tab -> add 3 material lines -> save (BOM is project-scoped)
#   drawings: best effort (the dialog uploads a FILE, which is hard to drive from the browser CLI) -> recorded as tooling limit
# ALL DATA KEPT. ASCII ONLY (Chinese via zh.json keys).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Has([string]$t) { return ((BodyHas $t) -match 'true') }

$seedPath = Join-Path $PSScriptRoot 'e2e-seed.json'
$seed = Get-Content $seedPath -Raw -Encoding UTF8 | ConvertFrom-Json
$matBase = ZH 'val_material'

# ---------------- 1. projects ----------------
Step 'projects x2'
$projNames = @()
$projIds = @()
$fields = @(
  @{ k = 'lbl_proj_name'; v = { param($n) $n } },
  @{ k = 'lbl_assy_name'; v = { param($n) $n } },
  @{ k = 'lbl_orig_size'; v = { '6.1' } },
  @{ k = 'lbl_orig_res'; v = { '1080x2340' } },
  @{ k = 'lbl_drv_ic'; v = { 'RM692E5' } },
  @{ k = 'lbl_touch_ic'; v = { 'FT8006' } },
  @{ k = 'lbl_glass_size'; v = { '6.1' } },
  @{ k = 'lbl_glass_res'; v = { '1080x2340' } }
)
for ($i = 1; $i -le 2; $i++) {
  $nm = (ZH 'val_proj') + 'E2E' + $i
  $projNames += $nm
  Open '/dev/project' 2200
  if ((Has $nm) -match 'true') { Write-Host ('skip existing project ' + $nm) }
  else {
    ClearErrs | Out-Null
    ClickBtn 'btn_new' | Out-Null
    Start-Sleep -Milliseconds 2000
    foreach ($f in $fields) { FillLabel $f.k (& $f.v $nm) | Out-Null }
    SelectLabelContains 'lbl_brand' (ZH 'val_brand') | Out-Null
    Start-Sleep -Milliseconds 600
    # 改配信息 IC 下拉按物料类型过滤 -> must pick a material of the right type by NAME
    OpenSelectLabelIdx 'lbl_drv_ic' 1 | Out-Null
    Start-Sleep -Milliseconds 1400
    Write-Host ('pick drv ic: ' + (PickOptionContains ($matBase + '3')))
    Start-Sleep -Milliseconds 800
    OpenSelectLabelIdx 'lbl_touch_ic' 1 | Out-Null
    Start-Sleep -Milliseconds 1400
    Write-Host ('pick touch ic: ' + (PickOptionContains ($matBase + '21')))
    Start-Sleep -Milliseconds 800
    OpenSelectLabelIdx 'lbl_chip_ic' 0 | Out-Null
    Start-Sleep -Milliseconds 1400
    Write-Host ('pick code ic: ' + (PickOptionContains ($matBase + '22')))
    Start-Sleep -Milliseconds 800
    ClickBtn 'btn_create_proj' | Out-Null
    Start-Sleep -Milliseconds 2800
    Write-Host ('msg=' + (Txt '.el-message'))
  }
  Open '/dev/project' 2200
  $idx = [int](FindRow $nm)
  Ok ($idx -ge 0) ('project created: ' + $nm)
  if ($idx -ge 0) {
    ClickRowBtnContains $idx (ZH 'btn_detail') | Out-Null
    Start-Sleep -Milliseconds 2200
    # NOTE: do NOT use $pid - it is a READ-ONLY automatic variable (process id) in PowerShell!
    $prjId = ((EvalJs 'String(location.pathname)') -replace '.*/', '')
    $projIds += $prjId
    Write-Host ('project ' + $nm + ' id=' + $prjId)
  }
}

# ---------------- 2. BOM per project ----------------
Step 'BOM lines per project'
$bomTypes = @('opt_mt_glass', 'opt_mt_line', 'opt_drv')
for ($p = 0; $p -lt $projIds.Count; $p++) {
  $pid = $projIds[$p]
  Open ("/dev/project/edit/" + $pid) 3200
  Write-Host ('click BOM tab: ' + (ClickText (ZH 'tab_bom_info')))
  Start-Sleep -Milliseconds 1800
  $existing = Rows 0
  Write-Host ('bom rows before=' + $existing.n)
  for ($r = 1; $r -le 3; $r++) {
    Write-Host ('add row: ' + (ClickBtn 'btn_add_bom'))
    Start-Sleep -Milliseconds 900
    $rowIdx = (Rows 0).n - 1
    OpenRowSelect $rowIdx 0 | Out-Null
    Start-Sleep -Milliseconds 1300
    $rt = PickOptionContains (ZH $bomTypes[($r - 1) % 3])
    Write-Host ('row' + $r + ' type pick=' + $rt)
    Start-Sleep -Milliseconds 800
    OpenRowSelect $rowIdx 1 | Out-Null
    Start-Sleep -Milliseconds 1600
    $rp = PickOptionContains ($matBase + $r)
    Write-Host ('row' + $r + ' material pick=' + $rp)
    Ok (($rt -match 'OK') -and ($rp -match 'OK')) ('BOM line ' + $r + ' filled (type+material)')
    Start-Sleep -Milliseconds 600
  }
  Write-Host ('save BOM: ' + (ClickBtn 'btn_save_bom'))
  Start-Sleep -Milliseconds 2600
  Write-Host ('msg=' + (Txt '.el-message'))
  Ok ((Has (ZH 'txt_bom_saved')) -match 'true') 'BOM saved message shown'
  Open ("/dev/project/edit/" + $pid) 3000
  ClickText (ZH 'tab_bom_info') | Out-Null
  Start-Sleep -Milliseconds 2200
  $after = Rows 0
  Write-Host ('bom rows after=' + $after.n + ' head=' + ($after.head -join '|'))
  Ok ($after.n -ge 3) ('BOM persisted with ' + $after.n + ' lines (project ' + $pid + ')')
}

# ---------------- 3. drawings (best effort) ----------------
Step 'drawings (best effort: dialog uploads a file)'
$doneDraw = 0
foreach ($pid in $projIds) {
  Open ("/dev/project/edit/" + $pid) 3000
  ClickText (ZH 'tab_project_info') | Out-Null
  Start-Sleep -Milliseconds 600
  $clicked = ClickText (ZH 'btn_upload_drawing')
  Write-Host ('open drawing dialog: ' + $clicked)
  if ($clicked -match 'OK') {
    Start-Sleep -Milliseconds 1200
    Write-Host ('doc name: ' + (FillLabel 'lbl_doc_name' ((ZH 'pfx_e2e') + 'DWG' + $pid)))
    $ok = ClickDialogBtn 'btn_ok' 900
    if ($ok -notmatch 'OK') { $ok = ClickDialogBtn 'btn_save' 900 }
    Write-Host ('drawing submit: ' + $ok)
    Start-Sleep -Milliseconds 2000
    Write-Host ('msg=' + (Txt '.el-message'))
    if (((Has ((ZH 'pfx_e2e') + 'DWG' + $pid)) -match 'true')) { $doneDraw++ }
  }
}
Write-Host ('drawings created=' + $doneDraw + ' (if 0: file upload cannot be automated -> tooling limit, not a product defect)')

$seed | Add-Member -NotePropertyName projects -NotePropertyValue $projNames -Force
$seed | Add-Member -NotePropertyName projectIds -NotePropertyValue $projIds -Force
$seed | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $seedPath
Write-Host ('errs=' + (Errs))
Summary 'P2b R&D batch'
