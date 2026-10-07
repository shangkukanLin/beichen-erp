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
  # 2026-10-07：契约变更（用户要求恢复「记住密码」）——原先断言"payload 里绝不能有 password"，
  # 现在改为：**允许有，但必须是混淆形式**（本地存储里不得出现明文口令，也不得出现其朴素 base64），
  # 并且旧格式（无 v1: 前缀）仍必须在下次进入登录页时被清除（见下方 legacy 段）。
  Step 'F7-194 login: remember persists an OBFUSCATED password (contract updated 2026-10-07)'
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
  # 2026-10-07 修复：原先写作 `...getItem('…')||''` —— `||` 里的 `|` 会被 agent-browser 的 cmd 垫片当**管道符**
  # 吞掉，EvalJs 回回一个 "SyntaxError: missing ) after argument list" 字符串 ⇒ 这条断言此前一直在比错误文本
  # （同族坑：JS 片段里不要出现 `|`/`<`/`>`/`&`；缺失时 getItem 返回字面量 null，判断用 -eq 'null'）。
  $rem = EvalJs "String(localStorage.getItem('beichen_erp_remember'))"
  Write-Host ('  remember payload = ' + $rem)
  Ok ($rem -match 'username') 'remember payload keeps the username'
  Ok ($rem -match 'password') 'remember payload now KEEPS a password field (user decision 2026-10-07)'
  Ok ($rem -match 'v1:') 'the stored password is the obfuscated v1: form (not plaintext, not bare base64)'
  Ok ($rem -notmatch 'MTIz') 'remember payload holds neither the plaintext password nor its naive base64'
  Ok ($rem -notmatch '123') 'the raw password text does not appear anywhere in the payload'

  # a legacy payload (password stored by an older build) must be scrubbed on the next load.
  # NOTE: clear the session FIRST - with a live token the router guard bounces /login straight back
  # to the dashboard, so the login component (which owns the scrub) would never mount and this
  # assertion would fail for the wrong reason.
  EvalJs "localStorage.clear();sessionStorage.clear();localStorage.setItem('beichen_erp_remember', JSON.stringify({username:'lin',password:'MTIz'}));'x'" | Out-Null
  Open '/login' 2600
  Write-Host ('  landed on=' + (EvalJs 'String(location.pathname)'))
  # 同上：去掉了 `||''`（`|` 会被 cmd 垫片吞掉 ⇒ 拿到的是语法错误文本而不是存储内容）
  $rem2 = EvalJs "String(localStorage.getItem('beichen_erp_remember'))"
  Write-Host ('  after legacy payload reload = ' + $rem2)
  Ok ($rem2 -match 'username') 'the scrubbed payload is still readable (sanity: not an EvalJs syntax error)'
  Ok ($rem2 -notmatch 'password') 'legacy password payload is scrubbed on load'
  Ok ($rem2 -match 'username') 'scrubbed payload keeps the username'

  # 2026-10-07 新契约补充（用户要求恢复「记住密码」）：
  #   ① 勾选登录后，再进登录页必须把账号 **和口令** 回填、勾选框保持选中；
  #   ② 取消勾选后登录，记忆条目必须被清除。
  EvalJs "localStorage.clear();sessionStorage.clear();'x'" | Out-Null
  Open '/login' 2600
  $chk2 = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const cs=[...document.querySelectorAll('.el-checkbox')].filter(vis);if(!cs.length)return 'NOCHK';cs[0].click();return 'OK'})()"
  Ok ($chk2 -eq 'OK') 'remember checkbox ticked again for the prefill check'
  $lg2 = FillLogin
  Write-Host ('  second login: ' + $lg2)
  Start-Sleep -Milliseconds 4000
  # 保留记忆条目、只清登录态，再进登录页（否则路由守卫会把 /login 直接弹回工作台）
  EvalJs "localStorage.removeItem('beichen_erp_token');'x'" | Out-Null
  Open '/login' 2600
  # 选择器用 autocomplete 属性定位（2026-10-07 给登录表单补上的）：公司下拉的 .el-select 也渲染
  # input[type=text]，按 type 找会误抓它（首版就这么踩了一次）。
  $pf = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const u=document.querySelector('input[autocomplete=username]');const p=document.querySelector('input[autocomplete=current-password]');const c=[...document.querySelectorAll('.el-checkbox')].filter(vis)[0];return JSON.stringify({u:u?u.value:'',p:p?p.value:'',c:!!(c&&c.classList.contains('is-checked'))})})()"
  Write-Host ('  prefill after re-entering /login = ' + $pf)
  Ok ($pf -match '"u":"lin"') 'the username is prefilled from the remembered payload'
  Ok ($pf -match '"p":"123"') 'the password is prefilled from the remembered payload'
  Ok ($pf -match '"c":true') 'the remember checkbox comes back ticked'

  $un = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const c=[...document.querySelectorAll('.el-checkbox')].filter(vis)[0];if(!c)return 'NOCHK';c.click();return 'OK'})()"
  Ok ($un -eq 'OK') 'remember checkbox un-ticked'
  $lg3 = FillLogin
  Write-Host ('  third login (un-ticked): ' + $lg3)
  Start-Sleep -Milliseconds 4000
  $rem3 = EvalJs "String(localStorage.getItem('beichen_erp_remember'))"
  Write-Host ('  remember payload after un-ticked login = [' + $rem3 + ']')
  Ok ($rem3 -eq 'null') 'un-ticking the checkbox clears the remembered payload on the next login'

  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 3 recorded no JS/API errors'
  Summary 'F7-194 login remember'
}
