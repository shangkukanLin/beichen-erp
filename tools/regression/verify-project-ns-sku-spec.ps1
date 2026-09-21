# Project creation rework (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per project convention.
#
# Four user requirements on the project-creation page:
#   1. new product-SKU field: auto-filled with an NS-###### code, editable, placed BEFORE the product
#      name; it becomes the SKU of the product that creation auto-builds;
#   2. the old "assembly name" field is renamed to "product name" - INCLUDING the DB column
#      (assembly_name -> product_name);
#   3. new spec field (original-config-match / modified) linked to the created product's spec_type;
#   4. when the spec is "match" the display-plan / touch-plan / modified-config blocks are hidden
#      (and they must NOT block submission - their required checks have to be skipped).
#
# Part 1 (API):
#   a. GET /product/next-sku?prefix=NS -> NS-###### ; default -> SKU-###### ; bogus prefix falls back
#   b. POST /dev/project with productSku + specType=MATCHED -> the auto-built product gets BOTH
#   c. the detail endpoint returns productName (renamed) and never assemblyName
#   d. a duplicate productSku is rejected
#   e. changing the project spec to MODIFIED propagates to the product (linkage)
#   f. changing the project product name propagates to the product (existing behaviour)
# Part 2 (UI):
#   g. the add page pre-fills the product-SKU input with NS-###### and it sits LEFT of the product name
#   h. spec=match hides display-plan / touch-plan / modified-config ; switching to modified shows them
#   i. with spec=match and every original-config field filled, the form SUBMITS without any
#      modified-config info (proof that the required checks were skipped) -> lands on the project list
#   j. no JS/API errors
param([int]$Part = 0)
$ErrorActionPreference = 'Continue'
# NOTE: do NOT name this $base -- ui-e2e-lib.ps1 sets $script:BASE and would clobber it (case-insensitive).
$apiBase = 'http://localhost:8080/api'
$zh = Get-Content (Join-Path $PSScriptRoot 'ui-e2e-zh.json') -Raw -Encoding UTF8 | ConvertFrom-Json
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
    return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 6)
  } catch {
    return @{ code = -1; msg = ('HTTP error: ' + $_.Exception.Message) }
  }
}
# Probe projects use a unique product name so the duplicate-name confirm dialog never appears.
# They do create a real product (that is the point of the feature); they are named PROBE-* so they
# stay identifiable.
function NewProbeProject([string]$suffix, [string]$specType, [string]$sku) {
  $body = @{ name = ('PROBE-PJ-' + $suffix); productName = ('PROBE-PJNAME-' + $suffix); specType = $specType }
  if ($sku) { $body.productSku = $sku }
  return Call 'Post' "$apiBase/dev/project" $body
}

if ($Part -eq 0 -or $Part -eq 1) {
  Write-Host '--- STEP 1  NS product-SKU + spec linkage (API)'
  $stamp = Get-Date -Format 'HHmmss'

  # a) next-sku previews per prefix
  $nsPrev = Call 'Get' "$apiBase/product/next-sku?prefix=NS" $null
  $defPrev = Call 'Get' "$apiBase/product/next-sku" $null
  $badPrev = Call 'Get' "$apiBase/product/next-sku?prefix=%21%21" $null
  Write-Host ('  next-sku prefix=NS -> ' + $nsPrev.data + ' | default -> ' + $defPrev.data + ' | bogus -> ' + $badPrev.data)
  Ok ([string]$nsPrev.data -match '^NS-\d{6}$') 'next-sku?prefix=NS returns an NS-###### code'
  Ok ([string]$defPrev.data -match '^SKU-\d{6}$') 'the default preview still returns SKU-######'
  Ok ([string]$badPrev.data -match '^SKU-\d{6}$') 'a bogus prefix safely falls back to the default prefix'

  # b) create a project with the NS sku + spec=match and check the auto-built product
  $nsSku = [string]$nsPrev.data
  $sfx = 'M-' + $stamp
  $pName = 'PROBE-PJNAME-' + $sfx
  $pProj = 'PROBE-PJ-' + $sfx
  $c1 = NewProbeProject $sfx 'MATCHED' $nsSku
  $pid1 = $c1.data.id
  Write-Host ('  create(NS sku=' + $nsSku + ', spec=MATCHED) -> code=' + $c1.code + ' projectId=' + $pid1)
  Ok ([int]$c1.code -eq 200) 'POST /dev/project accepts a project with a productSku and a spec'
  Ok ($pid1 -ne $null -and [int]$pid1 -gt 0) 'probe project id resolved (guards against vacuous PASS)'

  if ($pid1 -ne $null -and [int]$pid1 -gt 0) {
    $d1 = Call 'Get' "$apiBase/dev/project/$pid1" $null
    $props = @($d1.data.PSObject.Properties.Name)
    Write-Host ('  project detail -> productId=' + $d1.data.productId + ' spec=' + $d1.data.specType + ' sku=' + $d1.data.productSku)
    # c) rename completeness
    Ok ($props -contains 'productName') 'the project detail returns productName'
    Ok ($props -notcontains 'assemblyName') 'the project detail no longer returns assemblyName'
    Ok ([string]$d1.data.productName -eq $pName) 'productName round-trips'
    Ok ([string]$d1.data.specType -eq 'MATCHED') 'the project stores the spec it was created with'
    Ok ($d1.data.productId -ne $null) 'the project is linked to an auto-built product'
    Ok ([string]$d1.data.productSku -eq $nsSku) 'the detail endpoint reports the associated product SKU'

    # the product really carries the NS sku AND the linked spec
    $prod = Call 'Get' "$apiBase/product/$($d1.data.productId)" $null
    Write-Host ('  product -> sku=' + $prod.data.sku + ' spec=' + $prod.data.specType + ' name=' + $prod.data.name)
    Ok ([string]$prod.data.sku -eq $nsSku) 'the auto-built product carries the NS SKU from the creation page'
    Ok ([string]$prod.data.specType -eq 'MATCHED') 'the auto-built product inherits the spec (linked)'
    Ok ([string]$prod.data.name -eq $pName) 'the auto-built product is named after the product name'

    # d) duplicate SKU must be rejected
    $c2 = NewProbeProject ('DUP-' + $stamp) 'MATCHED' $nsSku
    Write-Host ('  duplicate sku -> code=' + $c2.code + ' msg=' + $c2.msg)
    Ok ([int]$c2.code -ne 200) 're-using an existing SKU is rejected'

    # e) spec linkage on update: MATCHED -> MODIFIED must reach the product
    $up = Call 'Put' "$apiBase/dev/project" @{ id = $pid1; name = $pProj; productName = $pName; specType = 'MODIFIED' }
    $prod2 = Call 'Get' "$apiBase/product/$($d1.data.productId)" $null
    Write-Host ('  update spec -> project code=' + $up.code + ' product spec=' + $prod2.data.specType)
    Ok ([int]$up.code -eq 200) 'PUT /dev/project with a new spec returns 200'
    Ok ([string]$prod2.data.specType -eq 'MODIFIED') 'changing the project spec propagates to the product (linkage)'

    # f) name change still propagates (rename regression)
    $newName = 'PROBE-PJNAME2-' + $stamp
    Call 'Put' "$apiBase/dev/project" @{ id = $pid1; name = $pProj; productName = $newName; specType = 'MODIFIED' } | Out-Null
    $prod3 = Call 'Get' "$apiBase/product/$($d1.data.productId)" $null
    Write-Host ('  update name -> product name=' + $prod3.data.name)
    Ok ([string]$prod3.data.name -eq $newName) 'changing the project product name still propagates to the product'
  }
  Summary 'NS product-SKU + spec linkage (API)'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Write-Host '--- STEP 2  add page: NS sku, spec and the match-spec hiding (UI)'
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  EnsureLogin | Out-Null
  $B_PROJN = B64 (ZH 'pj_lbl_project_name')
  $B_SKU = B64 (ZH 'pj_lbl_product_sku')
  $B_PNAME = B64 (ZH 'pj_lbl_product_name')
  $B_SPEC = B64 (ZH 'pj_lbl_spec')
  $B_DISPLAY = B64 (ZH 'pj_lbl_display_plan')
  $B_TOUCH = B64 (ZH 'pj_lbl_touch_plan')
  # The modified-config block is introduced by an el-divider (not a form item), so it is detected via
  # its first field instead: glass size, which only exists inside that block.
  $B_GLASS = B64 (ZH 'pj_lbl_glass_size')
  $B_OSIZE = B64 (ZH 'pj_lbl_orig_size')
  $B_ORES = B64 (ZH 'pj_lbl_orig_res')
  $B_ODRV = B64 (ZH 'pj_lbl_orig_drive')
  $B_OTCH = B64 (ZH 'pj_lbl_orig_touch')
  $B_OMATCH = B64 (ZH 'pj_opt_spec_matched')
  $B_OMOD = B64 (ZH 'pj_opt_spec_modified')
  $B_CREATE = B64 (ZH 'pj_btn_create')

  Open '/dev/project/add' 6000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 3500

  # helper JS shared by the probes below: locate a form item by its label text
  $jsHelper = "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "const item=L=>[...document.querySelectorAll('.el-form-item')].find(x=>{const l=x.querySelector('.el-form-item__label');return l&&(l.innerText||'').trim()===L;});" +
    "const setByLabel=(L,v)=>{const it=item(L);if(!it)return false;const el=it.querySelector('input');if(!el)return false;el.value=v;el.dispatchEvent(new Event('input',{bubbles:true}));return true;};"

  # g) prefill + position: the SKU input must be on the same row as, and LEFT of, the product name
  $jsPos = "(()=>{" + $jsHelper +
    "var a=item(T('$B_SKU')),b=item(T('$B_PNAME'));if(!a||!b)return JSON.stringify({ok:false});" +
    "var ia=a.querySelector('input'),ra=a.getBoundingClientRect(),rb=b.getBoundingClientRect();" +
    "return JSON.stringify({ok:true,val:ia?ia.value:'',skuT:Math.round(ra.top),pnT:Math.round(rb.top),skuL:Math.round(ra.left),pnL:Math.round(rb.left)});})()"
  $praw = (EvalJs $jsPos).Trim()
  Write-Host ('  sku/name probe = ' + $praw)
  $pd = $null
  try { $pd = $praw | ConvertFrom-Json } catch { }
  if ($null -ne $pd) {
    Ok ($pd.ok -eq $true) 'the product-SKU and product-name fields both exist'
    if ($pd.ok -eq $true) {
      Ok ([string]$pd.val -match '^NS-\d{6}$') 'the product-SKU input is pre-filled with an NS-###### code'
      Ok ([int]$pd.skuT -eq [int]$pd.pnT) 'the product-SKU field is on the SAME ROW as the product name'
      Ok ([int]$pd.skuL -lt [int]$pd.pnL) 'the product-SKU field sits BEFORE (left of) the product name'
    }
  }

  # h) spec = match hides the plan/config blocks; modified brings them back
  $jsPickSpec = "(()=>{" + $jsHelper +
    "var it=item(T('$B_SPEC'));if(!it)return 'nospec';var w=it.querySelector('.el-select__wrapper')||it.querySelector('.el-select');if(!w)return 'nowrapper';w.click();return 'opened';})()"
  $jsPickOptMatch = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$B_OMATCH');" +
    "var items=[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).filter(function(i){return i.getClientRects().length>0;});" +
    "var t=items.filter(function(i){return (i.innerText||'').trim()===L;})[0];if(!t)return 'noopt';t.click();return 'ok';})()"
  $jsPickOptMod = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$B_OMOD');" +
    "var items=[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).filter(function(i){return i.getClientRects().length>0;});" +
    "var t=items.filter(function(i){return (i.innerText||'').trim()===L;})[0];if(!t)return 'noopt';t.click();return 'ok';})()"
  $jsVisible = "(()=>{" + $jsHelper +
    "return JSON.stringify({display:!!item(T('$B_DISPLAY')),touch:!!item(T('$B_TOUCH')),config:!!item(T('$B_GLASS'))});})()"

  EvalJs $jsPickSpec | Out-Null
  Start-Sleep -Milliseconds 600
  $picked = (EvalJs $jsPickOptMatch).Trim()
  Start-Sleep -Milliseconds 900
  $v1 = (EvalJs $jsVisible).Trim()
  Write-Host ('  after picking spec=match (pick=' + $picked + ') = ' + $v1)
  $vd = $null
  try { $vd = $v1 | ConvertFrom-Json } catch { }
  if ($null -ne $vd) {
    Ok ($vd.display -eq $false) 'spec=match hides the display-plan field'
    Ok ($vd.touch -eq $false) 'spec=match hides the touch-plan field'
    Ok ($vd.config -eq $false) 'spec=match hides the whole modified-config block (no glass-size field)'
  }

  # switch back to modified -> the blocks must reappear (also proves the v-if is reactive)
  EvalJs $jsPickSpec | Out-Null
  Start-Sleep -Milliseconds 600
  $picked2 = (EvalJs $jsPickOptMod).Trim()
  Start-Sleep -Milliseconds 900
  $v2 = (EvalJs $jsVisible).Trim()
  Write-Host ('  after picking spec=modified (pick=' + $picked2 + ') = ' + $v2)
  $vd2 = $null
  try { $vd2 = $v2 | ConvertFrom-Json } catch { }
  if ($null -ne $vd2) {
    Ok ($vd2.display -eq $true) 'switching to spec=modified shows the display-plan field again'
    Ok ($vd2.touch -eq $true) 'switching to spec=modified shows the touch-plan field again'
    Ok ($vd2.config -eq $true) 'switching to spec=modified shows the modified-config block again'
  }

  # i) spec=match + original config filled + NO modified-config info -> the form must SUBMIT
  EvalJs $jsPickSpec | Out-Null
  Start-Sleep -Milliseconds 600
  EvalJs $jsPickOptMatch | Out-Null
  Start-Sleep -Milliseconds 900
  $uniq = 'PROBE-UI-' + (Get-Date -Format 'HHmmss')
  $jsFill = "(()=>{" + $jsHelper +
    "var r=[];r.push(setByLabel(T('$B_PROJN'),'$uniq'));r.push(setByLabel(T('$B_PNAME'),'$uniq'));" +
    "r.push(setByLabel(T('$B_OSIZE'),'6.1in'));r.push(setByLabel(T('$B_ORES'),'1080x2400'));" +
    "r.push(setByLabel(T('$B_ODRV'),'IC-ORIG-D'));r.push(setByLabel(T('$B_OTCH'),'IC-ORIG-T'));" +
    "return JSON.stringify(r);})()"
  $filled = (EvalJs $jsFill).Trim()
  Write-Host ('  fill required original-config fields = ' + $filled)
  Start-Sleep -Milliseconds 600
  $jsCreate = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$B_CREATE');" +
    "var bs=[].slice.call(document.querySelectorAll('button')).filter(function(b){return b.getClientRects().length>0 && (b.innerText||'').trim()===L;});" +
    "if(!bs.length)return 'nobtn';bs[0].click();return 'ok';})()"
  $clicked = (EvalJs $jsCreate).Trim()
  Start-Sleep -Milliseconds 3500
  # the duplicate-name dialog must NOT appear (unique probe name), so a jump to the list means success
  $path = (EvalJs "(()=>location.pathname)()").Trim()
  $msgs = (EvalJs "(()=>JSON.stringify([].slice.call(document.querySelectorAll('.el-message')).map(function(m){return m.className.indexOf('error')>=0?'E':(m.className.indexOf('success')>=0?'S':'?');})))()").Trim()
  Write-Host ('  submit(create=' + $clicked + ') -> path=' + $path + ' messages=' + $msgs)
  Ok ($path -eq '/dev/project') 'a spec=match project submits WITHOUT any modified-config info (checks skipped)'
  Ok ($msgs -notmatch 'E') 'no error toast after submitting'

  # the created project must be findable with spec=MATCHED
  $found = Call 'Get' "$apiBase/dev/project/page?pageNum=1&pageSize=50&keyword=$uniq" $null
  $rows = @($found.data.records)
  Write-Host ('  lookup by name -> rows=' + $rows.Count)
  Ok ($rows.Count -ge 1) 'the UI-created project exists in the list'
  if ($rows.Count -ge 1) {
    Ok ([string]$rows[0].specType -eq 'MATCHED') 'the UI-created project stored spec=MATCHED'
    Ok ([string]$rows[0].productName -eq $uniq) 'the UI-created project stored the product name'
  }

  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'no JS/API errors on the add page'
  Summary 'NS product-SKU + spec + match-spec hiding (UI)'
}
