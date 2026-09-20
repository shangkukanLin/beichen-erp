# Supplier "supply SKU" -> product SKU prefix (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per project convention.
#
# Feature: a supplier (typeCodes=product, i.e. the "供货商" kind) gains a "supply SKU" prefix field.
# When a product is created with that supplier selected, its SKU becomes "<PREFIX>-######", and each
# prefix keeps its OWN independent sequence. Products without a supplier keep the legacy SKU-######.
#
# Part 1 (API):
#   a. POST /supplier with a lowercase prefix -> persisted UPPERCASED (normalisation)
#   b. POST /supplier with an illegal prefix -> rejected with a readable business message
#   c. POST /supplier re-using an existing prefix -> rejected (unique)
#   d. PUT  /supplier keeping its OWN prefix -> accepted (uniqueness excludes self)
#   e. PUT  /supplier with an empty prefix -> cleared to null, then restored
#   f. GET  /product/next-sku?supplierId= -> "<PREFIX>-000001"
#   g. POST /product (no sku) with that supplier -> stored with the prefix, supplierId persisted
#   h. POST /product again -> sequence +1 (independent PER PREFIX)
#   i. POST /product without a supplier -> still "SKU-######" (legacy path untouched)
#   j. PUT  /product assigning that supplier -> SKU UNCHANGED (no recompute on edit)
#   k. PUT  /product without supplierId -> the column is CLEARED (explicit null write)
#   (probe products + the probe supplier are disabled at the end via DELETE = status lifecycle,
#    nothing is physically deleted)
# Part 2 (UI):
#   l. /product/add -> a "supplier" field exists
#   m. its dropdown lists the probe supplier; picking it re-fills SKU with "<PREFIX>-######"
#   n. /outsource/supplier/manage -> the "new" dialog contains a "supply SKU" field
param([int]$Part = 0)
$ErrorActionPreference = 'Continue'
# NOTE: do NOT name this $base -- ui-e2e-lib.ps1 sets $script:BASE = 'http://localhost:5173' and, once
# that lib is dot-sourced below, PS (case-insensitive) would clobber this variable and every API call in
# Part 2 would hit the dev server instead of the backend.
$apiBase = 'http://localhost:8080/api'
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
# unique prefix per run: makes the expected sequence deterministic and avoids clashing with earlier runs
$prefix = 'ZQ' + (Get-Date -Format 'HHmmss')
$supName = 'PROBE-SUPPLY-SKU'

if ($Part -eq 0 -or $Part -eq 1) {
  Write-Host '--- STEP 1  supplier supply-SKU prefix (API)'
  Write-Host ('  run prefix = ' + $prefix)

  # a) create the probe supplier with a LOWERCASE prefix
  $s1 = Call 'Post' "$apiBase/supplier" @{ name = $supName; typeCodes = @('product'); supplySku = $prefix.ToLower() }
  $supId = $s1.data
  Write-Host ('  create-supplier -> code=' + $s1.code + ' id=' + $supId)
  Ok ([int]$s1.code -eq 200) 'POST /supplier accepts a supply SKU'
  # guard: a missing id would make every id-based assertion below pass vacuously
  Ok ($supId -ne $null -and [int]$supId -gt 0) 'probe supplier id resolved (guards against vacuous PASS)'

  if ($supId -ne $null -and [int]$supId -gt 0) {
    $g1 = Call 'Get' "$apiBase/supplier/$supId" $null
    Write-Host ('  stored supplySku = ' + $g1.data.supplySku)
    Ok ([string]$g1.data.supplySku -eq $prefix) 'the supply SKU is persisted and normalised to UPPERCASE'

    # b) illegal prefix
    $s2 = Call 'Post' "$apiBase/supplier" @{ name = 'PROBE-SUPPLY-BAD'; typeCodes = @('product'); supplySku = 'ZQ A!' }
    Write-Host ('  bad-prefix -> code=' + $s2.code + ' msg=' + $s2.msg)
    Ok ([int]$s2.code -ne 200) 'POST /supplier with an ILLEGAL supply SKU is rejected'
    Ok ([string]$s2.msg -ne '') 'the rejection carries a business message (not an empty 500)'

    # c) duplicate prefix
    $s3 = Call 'Post' "$apiBase/supplier" @{ name = 'PROBE-SUPPLY-DUP'; typeCodes = @('product'); supplySku = $prefix }
    Write-Host ('  dup-prefix -> code=' + $s3.code + ' msg=' + $s3.msg)
    Ok ([int]$s3.code -ne 200) 'POST /supplier re-using an existing supply SKU is rejected'
    Ok ([string]$s3.msg -like ('*' + $prefix + '*')) 'the duplicate error names the offending prefix'

    # d) PUT keeping its own prefix (self must be excluded from the uniqueness check)
    $s4 = Call 'Put' "$apiBase/supplier" @{ id = $supId; name = $supName; typeCodes = @('product'); supplySku = $prefix }
    Write-Host ('  self-update -> code=' + $s4.code)
    Ok ([int]$s4.code -eq 200) 'PUT /supplier keeping its OWN supply SKU is accepted (self excluded)'

    # e) clear, then restore
    $s5 = Call 'Put' "$apiBase/supplier" @{ id = $supId; name = $supName; typeCodes = @('product'); supplySku = '' }
    $g2 = Call 'Get' "$apiBase/supplier/$supId" $null
    Write-Host ('  cleared-supplySku -> code=' + $s5.code + ' value=' + $g2.data.supplySku)
    Ok ($null -eq $g2.data.supplySku -or [string]$g2.data.supplySku -eq '') 'an EMPTY supply SKU clears the prefix (nullable)'
    Call 'Put' "$apiBase/supplier" @{ id = $supId; name = $supName; typeCodes = @('product'); supplySku = $prefix } | Out-Null
    $g3 = Call 'Get' "$apiBase/supplier/$supId" $null
    Ok ([string]$g3.data.supplySku -eq $prefix) 'the prefix can be set again (restored for the parts below)'

    # f) next-sku with the supplier
    $ns = Call 'Get' "$apiBase/product/next-sku?supplierId=$supId" $null
    $nextSku = [string]$ns.data
    Write-Host ('  next-sku(supplier) = ' + $nextSku)
    Ok ($nextSku -match ('^' + [regex]::Escape($prefix) + '-\d{6}$')) 'GET /product/next-sku?supplierId= uses the supply SKU as prefix'

    # g) create a product without sku but with the supplier
    $n1 = 'PROBE-P1-' + $prefix
    $p1 = Call 'Post' "$apiBase/product" @{ name = $n1; specType = 'ORIGINAL'; unit = 'pcs'; supplierId = $supId }
    $q1 = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=20&keyword=$n1" $null
    $row1 = @($q1.data.records)[0]
    $p1id = $null
    if ($row1) { $p1id = $row1.id }
    Write-Host ('  create-P1 -> code=' + $p1.code + ' id=' + $p1id + ' sku=' + $row1.sku + ' supplierId=' + $row1.supplierId)
    Ok ($p1id -ne $null) 'probe product P1 id resolved (guards against vacuous PASS)'
    Ok ([string]$row1.sku -eq $nextSku) 'the generated SKU uses the supply SKU as prefix'
    Ok ([int]$row1.supplierId -eq [int]$supId) 'the chosen supplier is persisted on the product'

    # h) a 2nd product of the same supplier -> sequence +1
    $n2 = 'PROBE-P2-' + $prefix
    $p2 = Call 'Post' "$apiBase/product" @{ name = $n2; specType = 'ORIGINAL'; unit = 'pcs'; supplierId = $supId }
    $q2 = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=20&keyword=$n2" $null
    $row2 = @($q2.data.records)[0]
    $p2id = $null
    if ($row2) { $p2id = $row2.id }
    $seq1 = 0; $seq2 = 0
    if ([string]$row1.sku -match '(\d{6})$') { $seq1 = [int]$Matches[1] }
    if ([string]$row2.sku -match '(\d{6})$') { $seq2 = [int]$Matches[1] }
    Write-Host ('  create-P2 -> id=' + $p2id + ' sku=' + $row2.sku + ' (seq ' + $seq1 + ' -> ' + $seq2 + ')')
    Ok ([string]$row2.sku -match ('^' + [regex]::Escape($prefix) + '-\d{6}$')) 'the 2nd product of the same supplier also gets the prefix'
    Ok ($seq2 -eq ($seq1 + 1)) 'the sequence is independent PER PREFIX and increments by 1'

    # i) no supplier -> legacy scheme
    $n3 = 'PROBE-P3-' + $prefix
    $p3 = Call 'Post' "$apiBase/product" @{ name = $n3; specType = 'ORIGINAL'; unit = 'pcs' }
    $q3 = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=20&keyword=$n3" $null
    $row3 = @($q3.data.records)[0]
    $p3id = $null
    if ($row3) { $p3id = $row3.id }
    Write-Host ('  create-P3 (no supplier) -> id=' + $p3id + ' sku=' + $row3.sku + ' supplierId=' + $row3.supplierId)
    Ok ([string]$row3.sku -match '^SKU-\d{6}$') 'a product WITHOUT a supplier still gets the legacy SKU-###### code'
    Ok ($null -eq $row3.supplierId) 'a product without a supplier has supplierId = null'

    if ($p3id -ne $null -and [int]$p3id -gt 0) {
      # j) assigning a supplier on EDIT must NOT recompute the SKU
      $e1 = Call 'Put' "$apiBase/product/$p3id" @{ sku = [string]$row3.sku; specType = 'ORIGINAL'; supplierId = $supId }
      $q4 = Call 'Get' "$apiBase/product/$p3id" $null
      Write-Host ('  edit(P3, add supplier) -> code=' + $e1.code + ' sku=' + $q4.data.sku + ' supplierId=' + $q4.data.supplierId)
      Ok ([string]$q4.data.sku -eq [string]$row3.sku) 'editing a product to add a supplier does NOT recompute its SKU'
      Ok ([int]$q4.data.supplierId -eq [int]$supId) 'the supplier change itself IS persisted on edit'

      # k) clearing the supplier (MP updateById skips nulls -> explicit write in the controller)
      $e2 = Call 'Put' "$apiBase/product/$p3id" @{ sku = [string]$row3.sku; specType = 'ORIGINAL' }
      $q5 = Call 'Get' "$apiBase/product/$p3id" $null
      Write-Host ('  edit(P3, clear supplier) -> code=' + $e2.code + ' supplierId=' + $q5.data.supplierId)
      Ok ($null -eq $q5.data.supplierId) 'removing the supplier on edit CLEARS the column (explicit null write)'
      Call 'Put' "$apiBase/product/$p3id" @{ sku = [string]$row3.sku; specType = 'ORIGINAL'; supplierId = $supId } | Out-Null
    }

    # cleanup: disable the probe products (status lifecycle; nothing physically deleted)
    foreach ($pidX in @($p1id, $p2id, $p3id)) {
      if ($pidX -ne $null -and [int]$pidX -gt 0) { Call 'Delete' "$apiBase/product/$pidX" $null | Out-Null }
    }
    if ($p3id -ne $null -and [int]$p3id -gt 0) {
      $chk = Call 'Get' "$apiBase/product/$p3id" $null
      Ok ([string]$chk.data.status -eq 'DISCONTINUED') 'probe products disabled (status lifecycle, nothing physically deleted)'
    }
    # cleanup: disable the probe supplier
    Call 'Delete' "$apiBase/supplier/$supId" $null | Out-Null
    $gs = Call 'Get' "$apiBase/supplier/$supId" $null
    Write-Host ('  cleanup supplier -> status=' + $gs.data.status)
    Ok ([int]$gs.data.status -eq 0) 'probe supplier disabled (status lifecycle, nothing physically deleted)'
  }
  Summary 'supplier supply-SKU prefix (API)'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Write-Host '--- STEP 2  supplier supply-SKU prefix (UI)'
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  function FromB64([string]$s) { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) }
  EnsureLogin | Out-Null
  $B_SUP = B64 (ZH 'prod_lbl_supplier')
  $B_SKU = B64 (ZH 'prod_lbl_sku')
  $B_SUPSKU = B64 (ZH 'sup_lbl_supply_sku')
  $B_NEW = B64 (ZH 'btn_new')

  # find the probe supplier (newest one carrying a prefix); create one when Part 2 runs standalone
  $pg = Call 'Get' "$apiBase/supplier/page?pageSize=200&supplierType=product" $null
  $probe = @($pg.data.records) | Where-Object { $_.name -eq $supName -and $_.supplySku } | Sort-Object id -Descending | Select-Object -First 1
  if ($null -eq $probe) {
    $prefix = 'ZQ' + (Get-Date -Format 'HHmmss')
    $cr = Call 'Post' "$apiBase/supplier" @{ name = $supName; typeCodes = @('product'); supplySku = $prefix }
    Write-Host ('  no probe supplier found -> created id=' + $cr.data + ' prefix=' + $prefix)
    $probe = @{ id = $cr.data; supplySku = $prefix }
  }
  $probeId = $probe.id
  $probePfx = [string]$probe.supplySku
  Write-Host ('  probe supplier id=' + $probeId + ' prefix=' + $probePfx)
  Ok ($probeId -ne $null -and [int]$probeId -gt 0) 'probe supplier resolved (guards against vacuous PASS)'
  Ok ($probePfx -ne '') 'probe supplier carries a supply SKU prefix'

  $jsShape = "(()=>{" +
    "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "const SUP=T('$B_SUP'),SKU=T('$B_SKU');" +
    "function items(){return [].slice.call(document.querySelectorAll('.el-form-item'));}" +
    "function byLabel(L){return items().filter(function(it){var b=it.querySelector('.el-form-item__label');return b&&(b.innerText||'').trim()===L;})[0]||null;}" +
    "var out={};" +
    "out.hasSupItem=!!byLabel(SUP);" +
    "var si=byLabel(SKU),sInput=si?si.querySelector('input'):null;" +
    "out.skuVal=sInput?(sInput.value||''):'NOINPUT';" +
    "return JSON.stringify(out);})()"

  Open '/product/add' 5000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 2500
  $raw = (EvalJs $jsShape).Trim()
  Write-Host ('  add-page probe = ' + $raw)
  $j = $null
  try { $j = $raw | ConvertFrom-Json } catch { Ok $false ('probe parse failed: ' + $raw) }
  if ($null -ne $j) {
    Ok ($j.hasSupItem -eq $true) 'add page: the supplier field exists'
    Write-Host ('  SKU before picking a supplier = ' + $j.skuVal)
    Ok ([string]$j.skuVal -match '^SKU-\d{6}$') 'add page: without a supplier the SKU keeps the legacy prefix'

    # m) open the supplier dropdown and list its options
    $jsOpen = "(()=>{" +
      "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
      "const SUP=T('$B_SUP');" +
      "var it=[].slice.call(document.querySelectorAll('.el-form-item')).filter(function(x){var b=x.querySelector('.el-form-item__label');return b&&(b.innerText||'').trim()===SUP;})[0];" +
      "if(!it)return 'noitem';var w=it.querySelector('.el-select__wrapper');if(!w)return 'nowrapper';w.click();return 'ok';})()"
    $op = (EvalJs $jsOpen).Trim()
    Start-Sleep -Milliseconds 1200
    $jsOpts = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
      "var os=[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).filter(function(e){return e.getClientRects().length>0;});" +
      "var B=s=>{var u=new TextEncoder().encode(s),x='';u.forEach(function(c){x+=String.fromCharCode(c)});return btoa(x);};" +
      "return JSON.stringify({n:os.length,texts:os.map(function(e){return B((e.innerText||'').trim());})});})()"
    $oraw = (EvalJs $jsOpts).Trim()
    Write-Host ('  supplier options = ' + $oraw + '  (open=' + $op + ')')
    $o = $null
    try { $o = $oraw | ConvertFrom-Json } catch { }
    $texts = @()
    if ($null -ne $o) { $texts = @(@($o.texts) | ForEach-Object { FromB64 $_ }) }
    $hitName = @($texts | Where-Object { $_ -like ('*' + $supName + '*') }).Count -gt 0
    Ok ($hitName) 'the supplier dropdown lists the probe supplier'

    # pick the probe option -> SKU prefix must follow
    $jsPick = "(()=>{var os=[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).filter(function(e){return e.getClientRects().length>0;});" +
      "var t=os.filter(function(e){return (e.innerText||'').indexOf('$supName')>=0;})[0];" +
      "if(!t)return 'noopt';t.click();return 'ok';})()"
    $pk = (EvalJs $jsPick).Trim()
    Start-Sleep -Milliseconds 1800
    $raw2 = (EvalJs $jsShape).Trim()
    Write-Host ('  after picking -> ' + $raw2 + '  (pick=' + $pk + ')')
    $j2 = $null
    try { $j2 = $raw2 | ConvertFrom-Json } catch { }
    if ($null -ne $j2) {
      Write-Host ('  SKU after picking = ' + $j2.skuVal)
      Ok ([string]$j2.skuVal -match ('^' + [regex]::Escape($probePfx) + '-\d{6}$')) 'picking a supplier re-fills the SKU with that supply SKU as prefix'
      Ok ([string]$j2.skuVal -ne [string]$j.skuVal) 'the SKU actually CHANGED after picking (not a stale value)'
    }
    Write-Host ('  errs=' + (Errs))
    Ok ((Errs) -eq '[]') 'no JS/API errors on the add page'

    # n) the supplier dialog itself exposes the supply SKU field
    Open '/outsource/supplier/manage' 5000
    Start-Sleep -Milliseconds 2000
    $jsNew = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const NE=T('$B_NEW');" +
      "var bs=[].slice.call(document.querySelectorAll('button')).filter(function(b){return b.getClientRects().length>0 && (b.innerText||'').trim()===NE;});" +
      "if(!bs.length)return 'nobtn';bs[0].click();return 'ok';})()"
    $nb = (EvalJs $jsNew).Trim()
    Start-Sleep -Milliseconds 1200
    $jsDlg = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$B_SUPSKU');" +
      "var dl=[].slice.call(document.querySelectorAll('.el-dialog')).filter(function(d){return d.getClientRects().length>0;});" +
      "if(!dl.length)return JSON.stringify({hasDialog:false});" +
      "var labels=[].slice.call(dl[0].querySelectorAll('.el-form-item__label')).map(function(l){return (l.innerText||'').trim();});" +
      "return JSON.stringify({hasDialog:true,hit:labels.indexOf(L)>=0,n:labels.length});})()"
    $draw = (EvalJs $jsDlg).Trim()
    Write-Host ('  new-supplier dialog = ' + $draw + '  (click=' + $nb + ')')
    $d = $null
    try { $d = $draw | ConvertFrom-Json } catch { }
    if ($null -ne $d) {
      Ok ($d.hasDialog -eq $true) 'the new-supplier dialog opened'
      Ok ($d.hit -eq $true) 'the new-supplier dialog contains a supply SKU field'
    }
    # close it (class selector only - no Chinese needed)
    EvalJs "(()=>{var b=document.querySelector('.el-dialog__headerbtn');if(b){b.click();return 'ok'}return 'nobtn';})()" | Out-Null
    Start-Sleep -Milliseconds 400
  }
  Summary 'supplier supply-SKU prefix (UI)'
}
