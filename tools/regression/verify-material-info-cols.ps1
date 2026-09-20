# "Material info" list rework (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per project convention.
#
# Requested by the user:
#   (1) drop the "stock total" and "undelivered qty" columns from the material-info list;
#   (2) drop the "owner project" field entirely (it was unused: 30 rows, 0 non-empty).
#
# Part 1 (API + DB):
#   a. GET /outsource/material/page -> 200 and records no longer carry stockTotal /
#      undeliveredTotal / projectName / projectIds, while the kept fields are still present
#   b. an unknown legacy query param (projectId) no longer breaks the endpoint
#   c. information_schema: outsource_material.project_ids has been DROPPED
# Part 2 (UI):
#   d. /outsource/material-info -> the table has EXACTLY 6 columns and none of the 3 removed ones
#   e. the query bar has no "owner project" field
#   f. the add/edit dialog has no "owner project" field, but still has "material type"
#   g. the table fits without horizontal scrolling
#   h. no JS/API errors
param([int]$Part = 0)
$ErrorActionPreference = 'Continue'
# NOTE: do NOT name this $base -- ui-e2e-lib.ps1 sets $script:BASE and would clobber it (case-insensitive).
$apiBase = 'http://localhost:8080/api'
$mysqlExe = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$lg = Invoke-RestMethod -Uri "$apiBase/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$script:PASS = 0
$script:FAIL = 0
function Ok([bool]$cond, [string]$msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:PASS++ } else { Write-Host ('FAIL ' + $msg); $script:FAIL++ }
}
function Summary([string]$title) {
  if ($script:FAIL -eq 0) { Write-Host ('RESULT PASS ' + $title + '  (PASS=' + $script:PASS + ' FAIL=0)') }
  else { Write-Host ('RESULT FAIL ' + $title + '  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ')'); exit 1 }
}
function Call([string]$method, [string]$uri, $body) {
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri $uri -Method $method -Headers $h }
    return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 5)
  } catch {
    return @{ code = -1; msg = ('HTTP error: ' + $_.Exception.Message) }
  }
}

if ($Part -eq 0 -or $Part -eq 1) {
  Write-Host '--- STEP 1  material-info list rework (API + DB)'

  $r1 = Call 'Get' "$apiBase/outsource/material/page?pageNum=1&pageSize=5" $null
  $rows = @($r1.data.records)
  Write-Host ('  page -> code=' + $r1.code + ' total=' + $r1.data.total + ' rows=' + $rows.Count)
  Ok ([int]$r1.code -eq 200) 'GET /outsource/material/page still returns 200'
  Ok ($rows.Count -gt 0) 'the page returns at least one material row'
  if ($rows.Count -gt 0) {
    $props = @($rows[0].PSObject.Properties.Name)
    Write-Host ('  record fields = ' + ($props -join ','))
    Ok ($props -notcontains 'stockTotal') 'records no longer carry stockTotal'
    Ok ($props -notcontains 'undeliveredTotal') 'records no longer carry undeliveredTotal'
    Ok ($props -notcontains 'projectName') 'records no longer carry projectName'
    Ok ($props -notcontains 'projectIds') 'records no longer carry projectIds'
    Ok ($props -contains 'materialName') 'records still carry materialName'
    Ok ($props -contains 'materialTypeName') 'records still carry materialTypeName'
    Ok ($props -contains 'unit') 'records still carry unit'
    Ok ($props -contains 'price') 'records still carry price'
    Ok ($props -contains 'supplierIds') 'records still carry supplierIds'
  }

  # b) the legacy projectId param is simply ignored (no 500)
  $r2 = Call 'Get' "$apiBase/outsource/material/page?pageNum=1&pageSize=5&projectId=1" $null
  Write-Host ('  legacy projectId param -> code=' + $r2.code)
  Ok ([int]$r2.code -eq 200) 'a legacy projectId query param is ignored instead of breaking the endpoint'

  # c) the DB column is really gone
  $q = & $mysqlExe --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='beichen_erp' AND TABLE_NAME='outsource_material' AND COLUMN_NAME='project_ids';" 2>$null
  Write-Host ('  information_schema project_ids count = ' + $q)
  Ok ([int]$q -eq 0) 'DB column outsource_material.project_ids has been dropped'

  Summary 'material-info list rework (API)'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Write-Host '--- STEP 2  material-info list rework (UI)'
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  EnsureLogin | Out-Null
  $B_PROJ = B64 (ZH 'col_owner_project')
  $B_STOCK = B64 (ZH 'col_stock_total')
  $B_UND = B64 (ZH 'col_undelivered')
  $B_MT = B64 (ZH 'col_material_type')
  $B_NEW = B64 (ZH 'btn_new')

  Open '/outsource/material-info' 5000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 3000

  # d) table header: exactly 6 columns, none of the 3 removed ones
  $jsHead = "(()=>{" +
    "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "var A=T('$B_PROJ'),S=T('$B_STOCK'),U=T('$B_UND');" +
    "var ths=[].slice.call(document.querySelectorAll('.el-table__header th'));" +
    "var ls=ths.map(function(t){return (t.innerText||'').trim();});" +
    "var w=document.querySelector('.el-table .el-scrollbar__wrap');" +
    "return JSON.stringify({n:ths.length,hitA:ls.indexOf(A)>=0,hitS:ls.indexOf(S)>=0,hitU:ls.indexOf(U)>=0," +
    "scrollW:w?w.scrollWidth:-1,clientW:w?w.clientWidth:-1});})()"
  $hraw = (EvalJs $jsHead).Trim()
  Write-Host ('  header probe = ' + $hraw)
  $hd = $null
  try { $hd = $hraw | ConvertFrom-Json } catch { Ok $false ('header probe parse failed: ' + $hraw) }
  if ($null -ne $hd) {
    Ok ([int]$hd.n -eq 6) 'the list has EXACTLY 6 columns'
    Ok ($hd.hitA -eq $false) 'the "owner project" column is gone'
    Ok ($hd.hitS -eq $false) 'the "stock total" column is gone'
    Ok ($hd.hitU -eq $false) 'the "undelivered qty" column is gone'
    Write-Host ('  scrollW=' + $hd.scrollW + ' clientW=' + $hd.clientW)
    Ok ([int]$hd.scrollW -le [int]$hd.clientW) 'the table fits without horizontal scrolling'
  }

  # e) query bar has no "owner project"
  $jsQuery = "(()=>{" +
    "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "var A=T('$B_PROJ');var qb=document.querySelector('.query-bar');" +
    "if(!qb)return JSON.stringify({found:false});" +
    "var ls=[].slice.call(qb.querySelectorAll('.el-form-item__label')).map(function(l){return (l.innerText||'').trim();});" +
    "return JSON.stringify({found:true,n:ls.length,hit:ls.indexOf(A)>=0});})()"
  $qraw = (EvalJs $jsQuery).Trim()
  Write-Host ('  query-bar probe = ' + $qraw)
  $qd = $null
  try { $qd = $qraw | ConvertFrom-Json } catch { }
  if ($null -ne $qd) {
    Ok ($qd.found -eq $true) 'the query bar is present'
    Ok ($qd.hit -eq $false) 'the query bar has NO "owner project" filter'
  }

  # f) the dialog has no "owner project" but still has "material type"
  $jsNew = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const NE=T('$B_NEW');" +
    "var bs=[].slice.call(document.querySelectorAll('button')).filter(function(b){return b.getClientRects().length>0 && (b.innerText||'').trim()===NE;});" +
    "if(!bs.length)return 'nobtn';bs[0].click();return 'ok';})()"
  $nb = (EvalJs $jsNew).Trim()
  Start-Sleep -Milliseconds 1500
  $jsDlg = "(()=>{" +
    "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "var A=T('$B_PROJ'),MT=T('$B_MT');" +
    "var dl=[].slice.call(document.querySelectorAll('.el-dialog')).filter(function(d){return d.getClientRects().length>0;});" +
    "if(!dl.length)return JSON.stringify({open:false});" +
    "var ls=[].slice.call(dl[0].querySelectorAll('.el-form-item__label')).map(function(l){return (l.innerText||'').trim();});" +
    "return JSON.stringify({open:true,n:ls.length,hit:ls.indexOf(A)>=0,hasType:ls.indexOf(MT)>=0});})()"
  $draw = (EvalJs $jsDlg).Trim()
  Write-Host ('  add-dialog probe = ' + $draw + '  (click=' + $nb + ')')
  $dd = $null
  try { $dd = $draw | ConvertFrom-Json } catch { }
  if ($null -ne $dd) {
    Ok ($dd.open -eq $true) 'the add dialog opened'
    if ($dd.open -eq $true) {
      Ok ($dd.hit -eq $false) 'the add dialog has NO "owner project" field'
      Ok ($dd.hasType -eq $true) 'the add dialog still has the "material type" field'
    }
  }
  EvalJs "(()=>{var b=document.querySelector('.el-dialog__headerbtn');if(b){b.click();return 'ok'}return 'nobtn';})()" | Out-Null
  Start-Sleep -Milliseconds 400

  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'no JS/API errors on the material-info page'
  Summary 'material-info list rework (UI)'
}

if ($Part -eq 0 -or $Part -eq 3) {
  Write-Host '--- STEP 3  smoke: other consumers of /outsource/material/page'
  # The same endpoint feeds the supplier-detail "supplied materials" selector and the dev-project
  # BOM material selector. The response no longer carries 4 fields, so those pages are smoked here.
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  EnsureLogin | Out-Null
  $sup = Call 'Get' "$apiBase/supplier/page?pageSize=1" $null
  $supId = @($sup.data.records)[0].id
  $prj = Call 'Get' "$apiBase/dev/project/page?pageNum=1&pageSize=1" $null
  $prjId = @($prj.data.records)[0].id
  Write-Host ('  probe ids: supplier=' + $supId + ' project=' + $prjId)
  # guards: a missing id would make the assertions below pass vacuously
  Ok ($supId -ne $null -and [int]$supId -gt 0) 'probe supplier id resolved (guards against vacuous PASS)'
  Ok ($prjId -ne $null -and [int]$prjId -gt 0) 'probe project id resolved (guards against vacuous PASS)'

  if ($supId -ne $null -and [int]$supId -gt 0) {
    ClearErrs | Out-Null
    Open "/supplier/detail/$supId" 5000
    Start-Sleep -Milliseconds 2500
    Write-Host ('  supplier detail errs=' + (Errs))
    Ok ((Errs) -eq '[]') 'supplier detail (supplied-materials selector) loads without errors'
  }
  if ($prjId -ne $null -and [int]$prjId -gt 0) {
    ClearErrs | Out-Null
    Open "/dev/project/edit/$prjId" 5000
    Start-Sleep -Milliseconds 3000
    Write-Host ('  project edit errs=' + (Errs))
    Ok ((Errs) -eq '[]') 'dev project edit (BOM material selector) loads without errors'
  }
  Summary 'material page consumers (smoke)'
}
