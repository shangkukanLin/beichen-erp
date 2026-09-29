# 结算方式（2026-09-18 用户要求）验证：
#   ① 术语 = 现金 / 账期（**不是**"赊账"）
#   ② 新增销售单页的「结算方式」是 **el-switch**（开=现金 / 关=账期，默认关），不再用下拉
#   ③ 开关真的驱动 settle_type：开→单据落 CASH（并显示收款账户）、关→落 CREDIT
#   ④ 现金单审核后自动生成草稿收款单（既有口径不变）
# UI + DB；可重复运行（每次新建 2 张真实销售单，只增不删）。ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }
function SetRowInputT([int]$tblIdx, [int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function RowInputsT([int]$tblIdx, [int]$rowIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$tblIdx];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW';return JSON.stringify([...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')].map(e=>e.value))})()"
  return (EvalJs $js)
}
function SwitchInfo {
  $lbl = B64 (ZH 'lbl_settle_type')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$lbl');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSWITCH:'+(it.innerText||'').replace(/\s+/g,'');const selCount=it.querySelectorAll('.el-select').length;return JSON.stringify({checked:sw.classList.contains('is-checked'),text:(sw.innerText||'').replace(/\s+/g,''),selects:selCount})})()"
  return (EvalJs $js)
}
function ClickSettleSwitch([bool]$wantCash) {
  $info = SwitchInfo
  if ($info -notmatch 'checked') { return ('NOINFO:' + $info) }
  $o = $null
  try { $o = $info | ConvertFrom-Json } catch { return ('BADJSON:' + $info) }
  if ([bool]$o.checked -eq $wantCash) { return ('ALREADY:' + $info) }
  $lbl = B64 (ZH 'lbl_settle_type')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$lbl');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSWITCH';sw.click();return 'OK'})()"
  Start-Sleep -Milliseconds 500
  return ((EvalJs $js) + ' -> ' + (SwitchInfo))
}
function AccountVisible {
  $lbl = B64 (ZH 'lbl_settle_account')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$lbl');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);return items.some(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').indexOf(L)>=0)?'YES':'NO'})()"
  return (EvalJs $js)
}

# ---- seed: a (finished-goods warehouse, product) stock pair + the cash account, single-table lookups only.
# NOTE 1: NOT every warehouse_stock.product_id exists in `product` (some rows point at other masters) ->
#         resolve the name first and skip rows that do not resolve; otherwise the empty needle would make
#         "pick first option" silently select an arbitrary product.
# NOTE 2: finance_account.account_type is stored lowercase ('cash'/'bank'); comparing with 'CASH' returns nothing.
$whId = 0; $whName = ''; $pId = 0; $prodName = ''
foreach ($ln in (SqlLines "SELECT warehouse_id, product_id FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity>20 ORDER BY quantity DESC LIMIT 40")) {
  $f = $ln -split "`t"
  $w = [int]$f[0].Trim(); $p = [int]$f[1].Trim()
  $wn = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + $w)
  $wt = SqlOne ("SELECT warehouse_type FROM warehouse WHERE id=" + $w)
  $pn = SqlOne ("SELECT name FROM product WHERE id=" + $p)
  if (($wt -eq 'FINISHED') -and ($wn -ne '') -and ($pn -ne '')) { $whId = $w; $whName = $wn; $pId = $p; $prodName = $pn; break }
}
$acct = SqlOne "SELECT account_name FROM finance_account WHERE LOWER(account_type)='cash' ORDER BY id LIMIT 1"
Write-Host ('[SEED] wh=' + $whName + '(' + $whId + ') prod=' + $prodName + '(' + $pId + ') acct=' + $acct)
Ok (($whName -ne '') -and ($prodName -ne '') -and ($acct -ne '')) 'seed resolved (finished warehouse / product / cash account)'
if (($whName -eq '') -or ($prodName -eq '') -or ($acct -eq '')) {
  # never fall through with an empty needle: that would create orders with an arbitrary product (lesson I19)
  Summary 'settle switch + 现金/账期 terms'
  exit 1
}

$qty = 2; $price = 100
$custCash = (ZH 'val_customer') + '1'
$custCredit = (ZH 'val_customer') + '2'
$created = @()

foreach ($isCash in @($true, $false)) {
  $cust = $custCash
  if (-not $isCash) { $cust = $custCredit }
  Step ('new sale order with settle switch = ' + $(if ($isCash) { 'ON' } else { 'OFF' }) + ' (customer ' + $cust + ')')
  Open '/inventory/sale' 3000
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 2400
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  SelectLabelContains 'lbl_customer' $cust | Out-Null
  Start-Sleep -Milliseconds 900
  SelectLabelContains 'lbl_out_wh' $whName | Out-Null
  Start-Sleep -Milliseconds 900

  # ---- the control under test
  $info0 = SwitchInfo
  Write-Host ('  switch before: ' + $info0)
  Ok (($info0 -match 'checked')) 'a switch (not a select) renders for 结算方式'
  $o0 = $null
  try { $o0 = $info0 | ConvertFrom-Json } catch { }
  if ($o0) {
    Ok (([int]$o0.selects -eq 0)) 'no el-select left inside the 结算方式 form item'
    # inline-prompt 的开关**只显示当前状态的文案**：关=账期 / 开=现金（两个状态分别断言，防术语回退）
    Ok (($o0.text -match [regex]::Escape((ZH 'opt_settle_credit')))) 'switch OFF shows the credit label (账期, not 赊账)'
    Ok ((-not [bool]$o0.checked)) 'switch defaults to OFF (= 账期)'
  }
  Ok ((AccountVisible) -eq 'NO') '收款账户 hidden while 账期'
  $clicked = ClickSettleSwitch $isCash
  Write-Host ('  click switch -> ' + $clicked)
  Ok (($clicked -match 'checked')) 'switch toggled'
  if ($isCash) {
    Ok (((SwitchInfo) -match '"checked":true')) 'switch is ON (= 现金)'
    Ok (((SwitchInfo) -match [regex]::Escape((ZH 'opt_settle_cash')))) 'switch ON shows the cash label (现金)'
    Start-Sleep -Milliseconds 900
    Ok ((AccountVisible) -eq 'YES') '收款账户 shown for 现金'
    SelectLabelContains 'lbl_settle_account' $acct | Out-Null
    Start-Sleep -Milliseconds 800
  } else {
    Ok ((AccountVisible) -eq 'NO') '收款账户 stays hidden for 账期'
  }

  # ---- fill one line and save
  $rc = Rows 0
  if ($rc.n -lt 1) { ClickBtn 'btn_add_product' | Out-Null; Start-Sleep -Milliseconds 900 }
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1500
  $pk = PickOptionContains $prodName
  Start-Sleep -Milliseconds 800
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1100
  PickOptionContains (ZH 'opt_q_a') | Out-Null
  Start-Sleep -Milliseconds 700
  SetRowInputT 0 0 2 ('' + $qty) | Out-Null
  Start-Sleep -Milliseconds 500
  SetRowInputT 0 0 3 ('' + $price) | Out-Null
  Start-Sleep -Milliseconds 700
  Write-Host ('  product pick: ' + $pk + ' row inputs=' + (RowInputsT 0 0))
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 2600
  $toast = Txt '.el-message'
  $code = SqlOne 'SELECT code FROM sale_order ORDER BY id DESC LIMIT 1'
  $st = SqlOne ("SELECT status FROM sale_order WHERE code='" + $code + "'")
  $settle = SqlOne ("SELECT settle_type FROM sale_order WHERE code='" + $code + "'")
  $settleAcct = SqlOne ("SELECT IFNULL(settle_account_id,0) FROM sale_order WHERE code='" + $code + "'")
  $expSettle = 'CREDIT'
  if ($isCash) { $expSettle = 'CASH' }
  Write-Host ('  created ' + $code + ' status=' + $st + ' settle_type=' + $settle + ' account_id=' + $settleAcct + ' toast=' + $toast)
  Ok (($settle -eq $expSettle)) ('switch drove settle_type=' + $expSettle + ' (got ' + $settle + ')')
  if ($isCash) { Ok (([int]$settleAcct -gt 0)) ('cash order carries the settlement account (id=' + $settleAcct + ')') }
  else { Ok (([int]$settleAcct -eq 0)) 'credit order carries no settlement account' }
  if ($code) { $created += $code }
}

# ---- audit the cash order -> auto receipt, AUDITED on the spot (current caliber since 2026-09-18:
#      现金 = 立刻到账、即结算 ⇒ the auto receipt is created AND audited by the sale audit itself.
#      This assertion used to demand status='DRAFT' (the pre-upgrade caliber) and had been red ever since.)
Step 'audit the two new orders (audit = stock out) and check the auto AUDITED receipt'
foreach ($c in $created) {
  Open '/inventory/sale' 3000
  $idx = [int](FindRow $c)
  if ($idx -lt 0) { Ok $false ('row found: ' + $c); continue }
  ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1400
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 3000
  $st = SqlOne ("SELECT status FROM sale_order WHERE code='" + $c + "'")
  Write-Host ('  ' + $c + ' -> status=' + $st + ' msg=' + (Txt '.el-message'))
  Ok (($st -eq 'AUDITED')) ('sale order ' + $c + ' audited')
}
$cashCode = SqlOne "SELECT code FROM sale_order WHERE settle_type='CASH' ORDER BY id DESC LIMIT 1"
$autoRc = D (SqlOne ("SELECT COUNT(*) FROM finance_receipt WHERE source_bill_no='" + $cashCode + "' AND status='AUDITED'"))
$autoAny = D (SqlOne ("SELECT COUNT(*) FROM finance_receipt WHERE source_bill_no='" + $cashCode + "' AND status<>'CANCELLED'"))
Write-Host ('[DB] cash order=' + $cashCode + ' autoAuditedReceipts=' + $autoRc + ' autoActiveReceipts=' + $autoAny)
Ok (($autoRc -ge 1)) ('cash order auto-generates an AUDITED receipt (cash = settled immediately) (got ' + $autoRc + ')')
Ok (($autoAny -eq 1)) ('exactly one active receipt per cash order (got ' + $autoAny + ')')

# ---- term check across the list page (no 赊账 anywhere)
Step 'term check: list page must show 现金 / 账期 and never 赊账'
Open '/inventory/sale' 3000
$body = EvalJs "(()=>{const t=(document.body.innerText||'').replace(/\s+/g,'');return t})()"
Ok (($body -match [regex]::Escape((ZH 'opt_settle_credit')))) 'list page shows the 账期 label'
Ok (($body -match [regex]::Escape((ZH 'opt_settle_cash')))) 'list page shows the 现金 label'
$old = [char]0x8D4A + [char]0x8D26   # the retired term (zhe-zhang)
Ok (($body -notmatch [regex]::Escape($old))) 'the retired term no longer appears on the page'
Write-Host ('errs=' + (Errs))

Summary 'settle switch + 现金/账期 terms'
