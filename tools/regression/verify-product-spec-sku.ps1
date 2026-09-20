# Product "spec + editable SKU" verification (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per project convention.
#
# Feature: (1) new product pre-fills an auto-generated SKU that the user may edit (new + edit modes,
#   edit asks for confirmation); (2) the old free-text "category" is replaced by a REQUIRED "spec"
#   dropdown with exactly 3 values (ORIGINAL / MATCHED / MODIFIED).
#
# Part 1 (API, PS + Invoke-RestMethod):
#   a. GET /product/next-sku returns SKU-######
#   b. POST /product WITHOUT spec -> rejected (required)
#   c. POST /product with an illegal spec code -> rejected
#   d. POST /product with a custom SKU + valid spec -> ok, and the row stores both
#   e. POST /product with the SAME SKU -> rejected, message mentions the SKU (proves clear error, not 500)
#   f. PUT  /product/{id} changing the SKU -> ok and persisted  (edit may change SKU)
#   g. PUT  /product/{id} with an empty spec -> rejected (required on edit too)
#   h. GET  /product/page -> spec_type filter param accepted
#   (probe product is disabled at the end via DELETE = status lifecycle, no physical delete)
# Part 2 (UI):
#   i. /product/add -> SKU input is NOT disabled and pre-filled with SKU-######; spec select exists
#   j. spec dropdown offers EXACTLY the 3 values
#   k. saving without a spec shows the required-field error
#   l. /product list has a spec column and the table does not scroll horizontally
#   m. edit page: SKU input is editable; spec select is present
param([int]$Part = 0)
$ErrorActionPreference = 'Continue'
# NOTE: do NOT name this $base -- ui-e2e-lib.ps1 sets $script:BASE = 'http://localhost:5173' and,
# once that lib is dot-sourced below, PS (case-insensitive) would clobber this variable and every
# API call in Part 2 would hit the dev server instead of the backend.
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

if ($Part -eq 0 -or $Part -eq 1) {
  Write-Host '--- STEP 1  product spec + editable SKU (API)'

  # a) next-sku
  $ns = Call 'Get' "$apiBase/product/next-sku" $null
  $nextSku = [string]$ns.data
  Write-Host ('  next-sku = ' + $nextSku)
  Ok ($nextSku -match '^SKU-\d{6}$') 'GET /product/next-sku returns an auto-generated SKU code'

  # b) spec required
  $r1 = Call 'Post' "$apiBase/product" @{ name = 'PROBE-SPEC-NOSPEC' }
  Write-Host ('  no-spec -> code=' + $r1.code + ' msg=' + $r1.msg)
  Ok ([int]$r1.code -ne 200) 'POST /product without spec is REJECTED (spec required)'
  Ok ([string]$r1.msg -ne '') 'rejection carries a business message (not an empty 500)'

  # c) illegal spec code
  $r2 = Call 'Post' "$apiBase/product" @{ name = 'PROBE-SPEC-BAD'; specType = 'XXX' }
  Write-Host ('  bad-spec -> code=' + $r2.code)
  Ok ([int]$r2.code -ne 200) 'POST /product with an illegal spec code is REJECTED'

  # d) custom SKU + valid spec
  $probeSku = 'SKU-T' + (Get-Date -Format 'HHmmss')
  $r3 = Call 'Post' "$apiBase/product" @{ name = 'PROBE-SPEC-OK'; sku = $probeSku; specType = 'ORIGINAL'; unit = 'pcs' }
  Write-Host ('  custom-sku -> code=' + $r3.code + ' sku=' + $probeSku)
  Ok ([int]$r3.code -eq 200) 'POST /product accepts a user-supplied SKU'
  # NOTE: do NOT name this $pid -- PS reserves $PID (read-only) and the assignment silently fails,
  # which then makes every later id-based assertion pass vacuously (MP updateById affects 0 rows
  # without error). See MEMORY.md "reserved variable names".
  $probeId = $null
  if ([int]$r3.code -eq 200) {
    $page = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=200&sku=$probeSku" $null
    $probeId = @($page.data.records)[0].id
    $row = @($page.data.records)[0]
    Write-Host ('  stored -> id=' + $probeId + ' sku=' + $row.sku + ' specType=' + $row.specType)
    Ok ([string]$row.sku -eq $probeSku) 'the custom SKU is persisted (not overwritten by the generator)'
    Ok ([string]$row.specType -eq 'ORIGINAL') 'the chosen spec code is persisted'
    # guard: a missing id would make all the id-based assertions below pass vacuously
    Ok ($probeId -ne $null -and [int]$probeId -gt 0) 'probe product id resolved (guards against vacuous PASS)'
  }

  # e) duplicate SKU -> clear business error
  $r4 = Call 'Post' "$apiBase/product" @{ name = 'PROBE-SPEC-DUP'; sku = $probeSku; specType = 'MATCHED' }
  Write-Host ('  dup-sku -> code=' + $r4.code + ' msg=' + $r4.msg)
  Ok ([int]$r4.code -ne 200) 'POST /product with a DUPLICATE SKU is rejected'
  Ok ([string]$r4.msg -like ('*' + $probeSku + '*')) 'the duplicate error names the offending SKU (clear message, not a 500)'

  if ($probeId -ne $null -and [int]$probeId -gt 0) {
    # f) edit may change the SKU
    $newSku = $probeSku + 'X'
    $r5 = Call 'Put' "$apiBase/product/$probeId" @{ sku = $newSku; specType = 'ORIGINAL' }
    $page2 = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=200&sku=$newSku" $null
    $row2 = @($page2.data.records)[0]
    Write-Host ('  edit-sku -> code=' + $r5.code + ' stored=' + $row2.sku)
    Ok ($row2 -ne $null) 'the edited product can be looked up by its NEW sku'
    Ok ([string]$row2.sku -eq $newSku) 'the changed SKU is persisted'

    # g) spec still required on edit
    $r6 = Call 'Put' "$apiBase/product/$probeId" @{ sku = $newSku; specType = '' }
    Write-Host ('  edit-empty-spec -> code=' + $r6.code)
    Ok ([int]$r6.code -ne 200) 'PUT /product/{id} without spec is REJECTED (required on edit too)'

    # h) spec filter param accepted
    $page3 = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=50&specType=ORIGINAL" $null
    Write-Host ('  filter -> code=' + $page3.code + ' total=' + $page3.data.total)
    Ok ([int]$page3.code -eq 200) 'GET /product/page accepts the specType filter'

    # cleanup: disable the probe product (status lifecycle; no physical delete)
    $r7 = Call 'Delete' "$apiBase/product/$probeId" $null
    $chk = Call 'Get' "$apiBase/product/$probeId" $null
    Write-Host ('  cleanup disable -> code=' + $r7.code + ' status=' + $chk.data.status)
    Ok ([string]$chk.data.status -eq 'DISCONTINUED') 'probe product disabled (status lifecycle, nothing physically deleted)'
  }
  Summary 'product spec + SKU (API)'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Write-Host '--- STEP 2  product spec + editable SKU (UI)'
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  function FromB64([string]$s) { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) }
  EnsureLogin | Out-Null
  $B_SPEC = B64 (ZH 'prod_lbl_spec')
  $B_SKU = B64 (ZH 'prod_lbl_sku')
  $B_MSG = B64 (ZH 'prod_msg_spec_required')

  # shape probe (SKU input state + spec select presence)
  $jsShape = "(()=>{" +
    "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "const SPEC=T('$B_SPEC'),SKU=T('$B_SKU');" +
    "function items(){return [].slice.call(document.querySelectorAll('.el-form-item'));}" +
    "function byLabel(L){return items().filter(function(it){var b=it.querySelector('.el-form-item__label');return b&&(b.innerText||'').trim()===L;})[0]||null;}" +
    "var out={};" +
    "var si=byLabel(SKU),sInput=si?si.querySelector('input'):null;" +
    "out.skuVal=sInput?(sInput.value||''):'NOINPUT';" +
    "out.skuDisabled=sInput?(sInput.disabled===true):true;" +
    "var pp=byLabel(SPEC);out.hasSpecItem=!!pp;" +
    "out.specText=pp?((pp.querySelector('.el-select')||{}).innerText||'').trim():'';" +
    "return JSON.stringify(out);})()"

  Open '/product/add' 5000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 2500
  $raw = (EvalJs $jsShape).Trim()
  Write-Host ('  add-page probe = ' + $raw)
  $j = $null
  try { $j = $raw | ConvertFrom-Json } catch { Ok $false ('probe parse failed: ' + $raw) }
  if ($null -ne $j) {
    Write-Host ('  SKU input value = ' + $j.skuVal + '  disabled=' + $j.skuDisabled)
    Ok ($j.skuDisabled -eq $false) 'add page: SKU input is EDITABLE (not disabled)'
    Ok ([string]$j.skuVal -match '^SKU-\d{6}$') 'add page: SKU is pre-filled with an auto-generated code'
    Ok ($j.hasSpecItem -eq $true) 'add page: spec field exists'

    # j) spec dropdown offers exactly the 3 values
    $jsOpen = "(()=>{" +
      "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
      "const SPEC=T('$B_SPEC');" +
      "var it=[].slice.call(document.querySelectorAll('.el-form-item')).filter(function(x){var b=x.querySelector('.el-form-item__label');return b&&(b.innerText||'').trim()===SPEC;})[0];" +
      "if(!it)return 'noitem';var w=it.querySelector('.el-select__wrapper');if(!w)return 'nowrapper';w.click();return 'ok';})()"
    $op = (EvalJs $jsOpen).Trim()
    Start-Sleep -Milliseconds 900
    $B_O = @((B64 (ZH 'prod_spec_original')), (B64 (ZH 'prod_spec_matched')), (B64 (ZH 'prod_spec_modified')))
    $jsOpts = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
      "var os=[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).filter(function(e){return e.getClientRects().length>0;});" +
      "var B=s=>{var u=new TextEncoder().encode(s),x='';u.forEach(function(c){x+=String.fromCharCode(c)});return btoa(x);};" +
      "return JSON.stringify({n:os.length,texts:os.map(function(e){return B((e.innerText||'').trim());})});})()"
    $oraw = (EvalJs $jsOpts).Trim()
    Write-Host ('  spec options = ' + $oraw)
    $o = $null
    try { $o = $oraw | ConvertFrom-Json } catch { }
    if ($null -ne $o) {
      $texts = @(@($o.texts) | ForEach-Object { FromB64 $_ })
      Write-Host ('  spec labels = ' + ($texts -join ' | '))
      Ok ([int]$o.n -eq 3) 'spec dropdown offers EXACTLY 3 options'
      Ok (($texts -contains (ZH 'prod_spec_original')) -and ($texts -contains (ZH 'prod_spec_matched')) -and ($texts -contains (ZH 'prod_spec_modified'))) 'the 3 spec options are the expected labels'
    }
    # close the popper
    EvalJs "(()=>{document.body.click();return 'ok'})()" | Out-Null
    Start-Sleep -Milliseconds 400

    # k) saving without a spec shows the required error
    $jsSave = "(()=>{var bs=[].slice.call(document.querySelectorAll('button')).filter(function(b){return b.getClientRects().length>0 && (b.innerText||'').trim().length>0 && b.classList.contains('el-button--primary');});" +
      "var b=bs[0];if(!b)return 'nobtn';b.click();return 'ok';})()"
    $sv = (EvalJs $jsSave).Trim()
    Start-Sleep -Milliseconds 1200
    $jsErr = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const M=T('$B_MSG');" +
      "var es=[].slice.call(document.querySelectorAll('.el-form-item__error')).filter(function(e){return e.getClientRects().length>0;});" +
      "return JSON.stringify({msgs:es.map(function(e){return (e.innerText||'').trim();}), hit:es.some(function(e){return (e.innerText||'').trim()===M;})});})()"
    $er = (EvalJs $jsErr).Trim()
    Write-Host ('  save-without-spec -> click=' + $sv + ' err=' + $er)
    Ok ($er -match '"hit":true') 'saving without a spec shows the required-field error'
    Write-Host ('  errs=' + (Errs))
    Ok ((Errs) -eq '[]') 'no JS/API errors on the add page'

    # l) list page: spec column + no horizontal scroll
    Open '/product' 5000
    Start-Sleep -Milliseconds 2000
    $jsList = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const SPEC=T('$B_SPEC');" +
      "var ths=[].slice.call(document.querySelectorAll('.el-table__header th'));" +
      "var has=ths.some(function(t){return (t.innerText||'').trim()===SPEC;});" +
      "var w=document.querySelector('.el-table .el-scrollbar__wrap');" +
      "return JSON.stringify({hasSpecCol:has,cols:ths.length,scrollW:w?w.scrollWidth:-1,clientW:w?w.clientWidth:-1});})()"
    $lraw = (EvalJs $jsList).Trim()
    Write-Host ('  list probe = ' + $lraw)
    $l = $null
    try { $l = $lraw | ConvertFrom-Json } catch { }
    if ($null -ne $l) {
      Ok ($l.hasSpecCol -eq $true) 'product list shows the spec column'
      Write-Host ('  scrollW=' + $l.scrollW + ' clientW=' + $l.clientW + ' cols=' + $l.cols)
      Ok ([int]$l.scrollW -le [int]$l.clientW) 'product list fits without horizontal scrolling (columns were narrowed)'
    }

    # m) edit page: SKU editable + spec present
    $pageX = Call 'Get' "$apiBase/product/page?pageNum=1&pageSize=1" $null
    $eid = @($pageX.data.records)[0].id
    Write-Host ('  edit probe product id = ' + $eid)
    if ($eid) {
      Open "/product/detail/$eid" 5000
      Start-Sleep -Milliseconds 2000
      $xraw = (EvalJs $jsShape).Trim()
      Write-Host ('  edit-page probe = ' + $xraw)
      $x = $null
      try { $x = $xraw | ConvertFrom-Json } catch { }
      if ($null -ne $x) {
        Ok ($x.skuDisabled -eq $false) 'edit page: SKU input is EDITABLE'
        Ok ([string]$x.skuVal -match '^SKU-\d{6}$') 'edit page: existing SKU is shown'
        Ok ($x.hasSpecItem -eq $true) 'edit page: spec field exists'
      }
    }
  }
  Summary 'product spec + SKU (UI)'
}
