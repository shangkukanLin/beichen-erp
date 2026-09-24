# Purchase order detail: the DRAFT branch must render an editable form that saves (user request B, 2026-09-24).
# Regression guard for the change that turned purchase/order/detail.vue from a read-only page into
# "editable while DRAFT, read-only afterwards" (same shape as sale/order/detail.vue).
#
# Self-seeding: it creates its OWN draft purchase order through the API (ids resolved through the API,
# no SQL), drives the UI, then CANCELS the order so repeated runs do not grow the draft set.
# Needs 3306 + 8080 + 5173 up (restart-backend.ps1 / web-dev.ps1).
# ASCII ONLY (Chinese labels come from ui-e2e-zh.json -- PS 5.1 decodes non-BOM .ps1 as GBK).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }
WatchErrors
# Robust login: a leftover token WITHOUT its matching user/company state makes the app fall back to the
# login screen while localStorage still looks "logged in" (observed 2026-09-24: every probe came back 0
# because the page was /login). EnsureLogin only logs in when the token is MISSING, so clear the app
# origin's storage first -- and the clear must run while the browser is ON the app origin (5173), not on
# whatever page the previous run left behind (a clear on the 8080 origin would leave 5173 untouched).
Write-Host ('  BASE = ' + [string]$script:BASE)
Open '/dashboard' 1600
Write-Host ('  bootstrap href = ' + (EvalJs 'String(location.href)'))
EvalJs "localStorage.clear();sessionStorage.clear();'x'" | Out-Null
EnsureLogin | Out-Null
Write-Host ('  post-login href = ' + (EvalJs 'String(location.href)'))

# NOTE: never name this $base -- PowerShell variable names are case-INSENSITIVE, so $base would
# silently overwrite the library's $BASE (http://localhost:5173) and every later Open() would then
# navigate to the API origin instead of the app (cost: an hour of 0-count probes, 2026-09-24).
$apiBase = 'http://localhost:8080/api'
$tok = EvalJs "String(localStorage.getItem('beichen_erp_token')||'')"
Ok ($tok.Length -gt 20) 'UI session token available'

# Authorisation header is the RAW token -- a "Bearer " prefix is rejected with 401 by ApiPermGuard.
$H = @{ Authorization = $tok }
$qty = 2
$price0 = 1
$price1 = 3
$mark = 'e2e-draft-edit'
$newId = 0

function ApiGet([string]$path) {
  try { return (Invoke-RestMethod -Uri ($apiBase + $path) -Headers $H -TimeoutSec 10) } catch { return $null }
}
function ApiPost([string]$path, $body) {
  try {
    return (Invoke-RestMethod -Uri ($apiBase + $path) -Method Post -Headers $H -TimeoutSec 10 `
      -ContentType 'application/json; charset=utf-8' -Body ($body | ConvertTo-Json -Depth 6))
  } catch { return $null }
}
function ApiPut([string]$path) {
  try { return (Invoke-RestMethod -Uri ($apiBase + $path) -Method Put -Headers $H -TimeoutSec 10) } catch { return $null }
}
function First([string]$path) {
  $r = ApiGet $path
  if ($null -eq $r) { return $null }
  $rec = @((($r | Select-Object -ExpandProperty data).records))
  if ($rec.Count -eq 0) { return $null }
  return $rec[0]
}

Step '1) seed a DRAFT purchase order through the API'
# The warehouse must be an OWNED FINISHED one: PurchaseOrderServiceImpl.assertFinishedWarehouse rejects others.
$wh = First '/warehouse/page?pageSize=1&warehouseCategory=INVENTORY&warehouseType=FINISHED'
$sup = First '/supplier/page?pageSize=1'
$pro = First '/product/page?pageSize=1'
Ok ($null -ne $wh -and $null -ne $sup -and $null -ne $pro) 'warehouse / supplier / product resolved from the API'
if ($null -eq $wh -or $null -eq $sup -or $null -eq $pro) { Summary 'B purchase draft inline edit'; exit 0 }

$seed = @{
  order = @{ supplierId = $sup.id; warehouseId = $wh.id; orderDate = (Get-Date -Format 'yyyy-MM-dd'); taxIncluded = 0; taxRate = 0; remark = '' }
  items = @(@{ productId = $pro.id; qualityType = 'A'; quantity = $qty; unitPrice = $price0 })
}
$created = ApiPost '/inventory/purchase' $seed
if ($null -ne $created) { $newId = [int]$created.data }
Ok ($newId -gt 0) ('draft purchase order created (id=' + $newId + ')')
if ($newId -le 0) { Summary 'B purchase draft inline edit'; exit 0 }

Step '2) draft detail must render the editable form (not the read-only block)'
Open ("/inventory/purchase/detail/$newId") 3500
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1600
# Context first: if these look wrong (e.g. still /login) the probe below is meaningless.
Write-Host ('  href = ' + (EvalJs 'String(location.href)'))
Write-Host ('  title = ' + (EvalJs 'String(document.title)'))
Write-Host ('  bodyLen = ' + (EvalJs "String((document.body.innerText||'').length)"))
$probe = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const q=s=>[...document.querySelectorAll(s)].filter(vis).length;return JSON.stringify({f:q('.el-form'),d:q('.el-descriptions'),num:q('.el-input-number input'),tabs:q('.el-tabs'),ops:q('.page-header__ops button'),ro:q('.el-form input[readonly]')})})()"
Write-Host ('  probe = ' + $probe)
$j = $probe | ConvertFrom-Json
Ok ([int]$j.f -ge 1) 'draft detail renders the editable form'
Ok ([int]$j.d -eq 0) 'draft detail hides the read-only descriptions block'
Ok ([int]$j.num -ge 2) 'draft detail rows have editable number cells (quantity + unit price)'
Ok ([int]$j.ro -ge 2) 'draft form still shows the operator columns as readonly inputs'
Ok ([int]$j.tabs -eq 0) 'after-sale tabs hidden while draft (same as sale order detail)'
Ok ([int]$j.ops -ge 1) 'header shows a primary action for a draft order'

Step '3) edit the remark + the row unit price, save, then re-read through the API'
$js = "(()=>{const V='$mark';const tas=[...document.querySelectorAll('.el-form textarea')].filter(e=>e.getClientRects().length>0);if(!tas.length)return 'NOTEXTAREA';const el=tas[0];const s=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
Ok ((EvalJs $js) -eq 'OK') 'remark field filled (native setter + input/change events)'

$jsP = "(()=>{const rows=[...document.querySelectorAll('.el-form .el-table__body tbody tr')].filter(e=>e.getClientRects().length>0);if(!rows.length)return 'NOROW';const ins=[...rows[0].querySelectorAll('.el-input-number input')];if(ins.length<2)return 'NOINPUT:'+ins.length;const el=ins[1];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,'$price1');el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));if(el.blur)el.blur();return 'OK'})()"
Ok ((EvalJs $jsP) -eq 'OK') 'row unit price filled'
Start-Sleep -Milliseconds 900
$sum = EvalJs "(()=>{const b=document.querySelector('.sum-bar');return b?(b.innerText||'').replace(/\s+/g,' '):'NOSUM'})()"
Write-Host ('  sum-bar = ' + $sum)
Ok ($sum -match ([string]($qty * $price1))) ('payable total reacts to the edited row (' + $qty + ' x ' + $price1 + ')')

Ok ((ClickText (ZH 'btn_save')) -eq 'OK') 'clicked the page primary action (save)'
Start-Sleep -Milliseconds 3400

$after = (ApiGet ("/inventory/purchase/" + $newId))
$d = $null
if ($null -ne $after) { $d = $after.data }
Ok ($null -ne $d) 'detail API readable after save'
if ($null -ne $d) {
  Ok (([string]$d.remark) -eq $mark) ('remark persisted (got: ' + $d.remark + ')')
  Ok ([double]$d.totalAmount -eq ($qty * $price1)) ('backend recomputed the total from the edited row (totalAmount=' + $d.totalAmount + ')')
  Ok (([string]$d.status) -eq 'DRAFT') 'save keeps the document a draft (it must not audit it)'
}
$rowPrice = EvalJs "(()=>{const rows=[...document.querySelectorAll('.el-form .el-table__body tbody tr')].filter(e=>e.getClientRects().length>0);if(!rows.length)return 'NOROW';const ins=[...rows[0].querySelectorAll('.el-input-number input')];return ins.length<2?'NOINPUT':String(ins[1].value)})()"
Write-Host ('  reloaded unit price = ' + $rowPrice)
Ok (([string]$rowPrice -replace '[^0-9.]','') -ne '' -and [double]([string]$rowPrice -replace '[^0-9.]','') -eq $price1) 'the page reloads the saved row (detail is re-fetched after save)'
Ok ((Errs) -eq '[]') 'no JS/API errors on the draft detail page'

Step '4) self-cleanup: cancel the seeded draft'
ApiPut ("/inventory/purchase/" + $newId + "/cancel") | Out-Null
$c = ApiGet ("/inventory/purchase/" + $newId)
$cs = ''
if ($null -ne $c) { $cs = [string]$c.data.status }
Ok ($cs -eq 'CANCELLED') ('seeded order cancelled (status=' + $cs + ')')

Summary 'B purchase draft inline edit'
