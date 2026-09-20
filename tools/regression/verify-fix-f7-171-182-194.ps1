# R1 verification (2026-09-20): F7-171 / F7-182 / F7-194 driven through the real UI.
#
# Run in parts so no single invocation stays silent long enough to be killed:
#   -Part 1  -> F7-171 (/inventory/stock-loss/add available-stock cell)
#   -Part 2  -> F7-182 (/system/clear-data confirm word gate; nothing is submitted)
#   -Part 3  -> F7-194 (/login remember must not persist a password)
#   -Part 0  -> all three (default)
#
#   F7-171: after picking a warehouse + a product the "available stock" cell must render a NUMBER.
#     Before the fix the RemoteSelect was bound with @select while the component only emits `pick`,
#     so the lookup never ran and the cell stayed "-" forever. Asserted on the CELL TEXT, so a 0
#     stock still passes (0 proves the query ran).
#   F7-182: the danger button must stay disabled until the exact confirm word is typed. The word is
#     deliberately NOT submitted - company data must be kept.
#   F7-194: after ticking "remember" and logging in, localStorage must not hold a password; a legacy
#     payload that still contains one must be scrubbed on the next page load.
#
# ASCII ONLY - every Chinese literal comes from ui-e2e-zh.json via ZH / B64.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

# p5c-style option picker: click the visible dropdown item whose text ENDS WITH the given text
# (remote selects render labels like "SKU | name", so endsWith is the reliable match).
function PickOptionEndsWith([string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim().endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  return (EvalJs $js)
}
# type into the first visible input that carries a placeholder (the clear-data confirm box has no label)
function SetFirstPlaceholderInput([string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$b');const vis=e=>e.getClientRects().length>0;const ins=[...document.querySelectorAll('input:not([type=hidden])')].filter(vis).filter(e=>(e.placeholder||'').length>0);if(!ins.length)return 'NOINPUT';const el=ins[0];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}
# disabled state of the first visible danger button (the "clear company data" one)
function DangerBtnDisabled() {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('button')].filter(vis).filter(b=>(b.className||'').indexOf('el-button--danger')>=0);if(!bs.length)return 'NOBTN';return String(bs[0].disabled)})()"
  return (EvalJs $js)
}

# ---------------------------------------------------------------- F7-171
if ($Part -eq 0 -or $Part -eq 1) {
  EnsureLogin | Out-Null
  ClearErrs | Out-Null
  Step 'F7-171 available-stock cell on the finished-goods loss add form'
  Open '/inventory/stock-loss/add' 3200
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  $wh = SelectLabelContains 'lbl_warehouse' (ZH 'wh_fin1')
  Write-Host ('  warehouse pick: ' + $wh)
  Ok ($wh -match 'OK') ('warehouse selected (' + (ZH 'wh_fin1') + ')')
  Start-Sleep -Milliseconds 1600
  # the add form starts with one empty detail row, so open its remote product select directly
  $op = OpenRowSelect 0 0
  Write-Host ('  open row product select: ' + $op)
  Start-Sleep -Milliseconds 1800
  $rp = PickOptionEndsWith (ZH 'val_product_a1')
  Write-Host ('  product pick: ' + $rp)
  Ok ($rp -match 'OK') ('product picked (' + (ZH 'val_product_a1') + ')')
  Start-Sleep -Milliseconds 2200

  $rows = Rows 0
  if ($null -eq $rows) {
    Ok $false 'could not read the detail table'
  } else {
    $head = @($rows.head)
    $ai = -1
    for ($i = 0; $i -lt $head.Count; $i++) { if ("$($head[$i])" -eq (ZH 'col_avail_stock')) { $ai = $i } }
    Write-Host ('  head=' + ($head -join ' | '))
    Ok ($ai -ge 0) ('header contains the available-stock column (idx=' + $ai + ')')
    $bodyRows = @($rows.rows)
    Ok ($bodyRows.Count -ge 1) ('detail table has a row (n=' + $bodyRows.Count + ')')
    if ($ai -ge 0 -and $bodyRows.Count -ge 1) {
      $cell = "$($bodyRows[0][$ai])"
      Write-Host ('  available-stock cell = "' + $cell + '"')
      Ok ($cell -match '^\d+$') ('cell renders a number, not "-" (got "' + $cell + '")')
    }
  }
  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 1 recorded no JS/API errors'
  Summary 'F7-171 available-stock cell'
}

# ---------------------------------------------------------------- F7-182
if ($Part -eq 0 -or $Part -eq 2) {
  Step 'F7-182 clear-data: danger button gated by the confirm word (nothing is submitted)'
  Open '/system/clear-data' 2400
  $d0 = DangerBtnDisabled
  Write-Host ('  disabled before typing = ' + $d0)
  Ok ($d0 -eq 'true') 'button disabled before anything is typed'

  Write-Host ('  type wrong word: ' + (SetFirstPlaceholderInput 'WRONG-WORD'))
  Start-Sleep -Milliseconds 500
  $d1 = DangerBtnDisabled
  Write-Host ('  disabled with a wrong word = ' + $d1)
  Ok ($d1 -eq 'true') 'button still disabled with a wrong word'

  Write-Host ('  type exact word: ' + (SetFirstPlaceholderInput (ZH 'val_clear_word')))
  Start-Sleep -Milliseconds 500
  $d2 = DangerBtnDisabled
  Write-Host ('  disabled with the exact word = ' + $d2)
  Ok ($d2 -eq 'false') 'button enabled only with the exact confirm word'
  Ok ((BodyHas (ZH 'val_clear_word')) -eq 'true') 'page keeps the confirm-word input (data not cleared)'
  Summary 'F7-182 clear-data gate'
}

# ---------------------------------------------------------------- F7-194
if ($Part -eq 0 -or $Part -eq 3) {
  Step 'F7-194 login: remember must not persist a password'
  ClearErrs | Out-Null
  EvalJs "localStorage.clear();sessionStorage.clear();'x'" | Out-Null
  Open '/login' 2600
  $chk = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const cs=[...document.querySelectorAll('.el-checkbox')].filter(vis);if(!cs.length)return 'NOCHK';cs[0].click();return 'OK'})()"
  Write-Host ('  tick remember: ' + $chk)
  Ok ($chk -eq 'OK') 'remember checkbox ticked'
  Start-Sleep -Milliseconds 400
  $lg = FillLogin
  Write-Host ('  login: ' + $lg)
  Start-Sleep -Milliseconds 4000
  $rem = EvalJs "String(localStorage.getItem('beichen_erp_remember')||'')"
  Write-Host ('  remember payload = ' + $rem)
  Ok ($rem -match 'username') 'remember payload keeps the username'
  Ok ($rem -notmatch 'password') 'remember payload holds NO password field'
  Ok ($rem -notmatch 'MTIz') 'remember payload holds no base64 blob of the password'

  # a legacy payload (password stored by an older build) must be scrubbed on the next load.
  # NOTE: clear the session FIRST - with a live token the router guard bounces /login straight back
  # to the dashboard, so the login component (which owns the scrub) would never mount and this
  # assertion would fail for the wrong reason.
  EvalJs "localStorage.clear();sessionStorage.clear();localStorage.setItem('beichen_erp_remember', JSON.stringify({username:'lin',password:'MTIz'}));'x'" | Out-Null
  Open '/login' 2600
  Write-Host ('  landed on=' + (EvalJs 'String(location.pathname)'))
  $rem2 = EvalJs "String(localStorage.getItem('beichen_erp_remember')||'')"
  Write-Host ('  after legacy payload reload = ' + $rem2)
  Ok ($rem2 -notmatch 'password') 'legacy password payload is scrubbed on load'
  Ok ($rem2 -match 'username') 'scrubbed payload keeps the username'

  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 3 recorded no JS/API errors'
  Summary 'F7-194 login remember'
}
