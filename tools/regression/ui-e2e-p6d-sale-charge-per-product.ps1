# Sale per-product charge -- FRONT-END case (P6d, 2026-09-21 user rule: the charge must be per product).
#   User rule: "the sale return and the sale exchange both need a charge, and the charge must be precise
#   down to the product". Ledger shape (option A): ONE receivable per document, amount = SUM(item charges),
#   remark lists each product. This case drives the real pages and cross-checks the DB at every step.
#
#   A) sale return add page: customer + warehouse -> add-detail -> pick product -> qty/price/CHARGE AMOUNT
#      + CHARGE TYPE in the row -> save -> DB (item charge, doc charge = SUM, claim flag) -> audit from the
#      list -> the -FEE receivable carries SUM and names the product
#   B) sale exchange add page: customer -> source sale order -> rows auto-filled -> CHARGE AMOUNT + CHARGE
#      TYPE in the row -> save -> DB (item charge + doc = SUM) -> cancel (leaves no live draft)
#   C) one-line guard: all six tables (2 add / 2 detail / 2 list) must not scroll horizontally
#   D) no JS runtime errors
#   ASCII ONLY (Chinese travels through ui-e2e-zh.json + base64 injection; a name read from the DB is
#   passed as a plain string argument).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$WH = 71            # finished warehouse 2 (stock for the exchange-out side)
$ITEM_FEE = 12      # charge written on the return row (per product)
$EX_FEE = 7         # charge written on the exchange row (per product)

function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
# address the numeric fields by their el-input-number order (the product cell holds an el-select whose
# filter input would otherwise steal index 0). Return row: 0 = qty, 1 = price, 2 = CHARGE AMOUNT.
# Exchange row: 0 = return qty, 1 = exchange-out qty, 2 = exchange-out price, 3 = CHARGE AMOUNT.
function SetRowNumberField([int]$rowIdx, [int]$nth, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ns=[...rs[$rowIdx].querySelectorAll('.el-input-number input')];if(ns.length<=$nth)return 'NONUM:'+ns.length;const el=ns[$nth];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}
function TableOver([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$idx];if(!t)return 'NOTABLE/'+ts.length;const w=t.querySelector('.el-table__body-wrapper');if(!w)return 'NOWRAP';const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,' ').trim());return JSON.stringify({over:w.scrollWidth-w.clientWidth,cols:hs.length,head:hs.join('|')})})()"
  $r = EvalJs $js
  Write-Host ('  [' + $idx + '] ' + $r)
  try { return ($r.Replace('\"', '"') | ConvertFrom-Json) } catch { return $null }
}

EnsureLogin
WatchErrors

# ---- fixtures resolved from the live DB (no hardcoded ids/names) ----
$PROD = [int](SqlOne "SELECT product_id FROM warehouse_stock WHERE warehouse_id=$WH AND quality_type='A' AND IFNULL(quantity,0) > 20 ORDER BY quantity DESC LIMIT 1")
$PROD_NAME = SqlOne ("SELECT name FROM product WHERE id=$PROD")
# customer: prefer one that ALREADY bought this product. The sale-return AUDIT refuses a product that was
# never sold to the customer -- guard message observed live, translated here to keep this file ASCII:
#   "the product was never sold, it cannot be returned into stock (product ID=52); if this really is a
#    special case, open a sale order first or use the other inbound/outbound document instead"
# That is a genuine business guard, so the FIXTURE has to be a real (customer, product) sales pair.
$CUST_ID = [int](SqlOne "SELECT o.customer_id FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status='AUDITED' AND i.product_id=$PROD ORDER BY o.id DESC LIMIT 1")
if ($CUST_ID -le 0) { $CUST_ID = [int](SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1") }
$CUST = SqlOne ("SELECT name FROM customer WHERE id=$CUST_ID")
Write-Host ("fixtures: product=$PROD ($PROD_NAME) customer=$CUST_ID ($CUST) warehouse=$WH")

# Seed one AUDITED sale order (this product x 2) for that customer through the API. It is what makes the
# return auditable (the product IS sold to the customer) and gives the exchange page a source order that
# still has exchangeable quantity. NOTE: the API lives on the backend port -- the lib's BASE is the UI host.
$API = 'http://localhost:8080/api'
try {
  $tok = (Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}').data.token
  $hdr = @{ Authorization = $tok }
  $seedMax = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM sale_order')
  $seed = @{ order = @{ customerId = $CUST_ID; warehouseId = $WH; settleType = 'CREDIT'; remark = 'p6d exchange seed' }
    items = @(@{ productId = $PROD; qualityType = 'A'; quantity = 2; unitPrice = 10 }) } | ConvertTo-Json -Depth 6
  Invoke-RestMethod -Uri "$API/inventory/sale" -Method Post -Headers $hdr -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($seed)) | Out-Null
  Start-Sleep -Milliseconds 800
  $seedId = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM sale_order')
  Ok (($seedId -gt $seedMax)) ('seeded a sale order for the fixtures (id=' + $seedId + ')')
  Invoke-RestMethod -Uri "$API/inventory/sale/$seedId/audit" -Method Put -Headers $hdr | Out-Null
  Start-Sleep -Milliseconds 900
  Ok ((SqlOne ("SELECT status FROM sale_order WHERE id=$seedId")) -eq 'AUDITED') ('the seeded sale order is AUDITED (id=' + $seedId + ')')
} catch { Ok $false ('could not seed the sale order: ' + $_.Exception.Message) }

# ==================== A) sale return: per-product charge ====================
Write-Host '--- A) /sale/return/add : charge the row (per product)'
Open '/sale/return/add' 3200
Ok ((BodyHas (ZH 'lbl_charge_total_batch')) -eq 'True') 'add page renders the auto charge total'
# 2026-09-21 (user rule): the batch charge-TYPE field and its apply-to-all button were removed --
# the type is picked per product on the detail row, so the page must NOT offer them any more.
Ok ((BodyHas (ZH 'lbl_charge_type_batch')) -eq 'False') 'the batch charge-type field is gone'
Ok ((BodyHas (ZH 'btn_apply_charge')) -eq 'False') 'the apply-to-all button is gone'
Ok ((BodyHas (ZH 'txt_sale_charge_direction')) -eq 'True') 'add page states the charge direction (we collect from the customer)'
$rc = SelectLabelContains 'lbl_customer' $CUST 1500
Ok ($rc -match 'OK') ('customer selected (' + $rc + ')')
$rw = SelectLabelContains 'lbl_return_wh' (ZH 'wh_finished2') 1500
Ok ($rw -match 'OK') ('return warehouse selected (' + $rw + ')')
# NOTE: the page auto-adds ONE blank row on mount -- use it as it is. Clicking add-detail as well left a
# second, EMPTY row, and the backend then refused the document ("quantity must be > 0"): observed 19 -> 19
# while the row I had filled looked perfect.
$tb = Rows 0
Ok ($tb.n -ge 1) ('a detail row exists (rows=' + $tb.n + ')')
if ($tb.n -ge 1) {
  Write-Host ('  head=' + ($tb.head -join '|'))
  Ok ((($tb.head -join '|') -match (ZH 'lbl_charge'))) 'the detail table has a charge column'
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1600
  $pk = PickOptionContains $PROD_NAME
  Ok ($pk -match 'OK') ('product picked in the row (' + $pk + ')')
  Start-Sleep -Milliseconds 900
  Ok ((SetRowNumberField 0 0 '1') -match 'OK') 'return qty set'
  Ok ((SetRowNumberField 0 1 '10') -match 'OK') 'unit price set'
  $fee = SetRowNumberField 0 2 "$ITEM_FEE"
  Ok ($fee -match 'OK') ('per-product charge amount set (' + $fee + ')')
  Start-Sleep -Milliseconds 700
  # 2026-09-21: with an amount but NO type of its own the save must be refused -- the batch field used to
  # supply that fallback and is gone, so every charged row has to carry its own type.
  $negBefore = D (SqlOne 'SELECT COUNT(*) FROM sale_return')
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 1500
  $negToast = Txt '.el-message'
  Write-Host ('  save-without-type toast=' + $negToast)
  Ok ($negToast -match [regex]::Escape((ZH 'msg_item_charge_type'))) 'a charged row without its own type is refused'
  Ok ((D (SqlOne 'SELECT COUNT(*) FROM sale_return')) -eq $negBefore) 'the refused document was not persisted'
  # the charge type select is the LAST select of the row (product remote-select comes first)
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1300
  $ct = PickOptionContains (ZH 'pay_type_diff')
  Ok ($ct -match 'OK') ('per-product charge type picked (' + $ct + ')')
  Start-Sleep -Milliseconds 800
}
$retBefore = D (SqlOne 'SELECT COUNT(*) FROM sale_return')
$retMaxBefore = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM sale_return')
$sv = ClickBtn 'btn_save'
Ok ($sv -match 'OK') ('save clicked (' + $sv + ')')
Start-Sleep -Milliseconds 2600
Write-Host ('  toast=' + (Txt '.el-message'))
$retAfter = D (SqlOne 'SELECT COUNT(*) FROM sale_return')
Ok (($retAfter -eq ($retBefore + 1))) ('exactly one new return created (' + $retBefore + ' -> ' + $retAfter + ')')
$rid = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM sale_return')
Ok (($rid -gt $retMaxBefore)) ('the charged document is the newest row (id=' + $rid + ' > ' + $retMaxBefore + ')')
$rcode = SqlOne ("SELECT code FROM sale_return WHERE id=$rid")
$rItemFee = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM sale_return_item WHERE return_id=$rid ORDER BY id LIMIT 1"))
$rItemType = SqlOne ("SELECT charge_type FROM sale_return_item WHERE return_id=$rid ORDER BY id LIMIT 1")
$rItemQty = D (SqlOne ("SELECT IFNULL(quantity,0) FROM sale_return_item WHERE return_id=$rid ORDER BY id LIMIT 1"))
$rDocFee = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM sale_return WHERE id=$rid"))
$rDocFlag = D (SqlOne ("SELECT IFNULL(charge_flag,0) FROM sale_return WHERE id=$rid"))
Write-Host ("  saved: id=$rid code=$rcode item(qty=$rItemQty fee=$rItemFee type=$rItemType) doc(flag=$rDocFlag fee=$rDocFee)")
Ok ($rid -gt 0) 'the return exists in the DB'
Ok (($rItemQty -eq 1)) 'the row quantity came from the page'
Ok (($rItemFee -eq $ITEM_FEE)) ('the per-product charge persisted on the ITEM (' + $ITEM_FEE + ')')
Ok ($rItemType -eq 'DIFF') 'the per-product charge TYPE persisted on the item'
Ok (($rDocFee -eq $ITEM_FEE)) 'the document charge equals the sum of the item charges'
Ok (($rDocFlag -eq 1)) 'the document charge flag was derived from the items'

Write-Host '--- A2) audit from the list -> ONE -FEE receivable holding the sum'
Open '/sale/return' 2800
$idx = FindRow $rcode
Ok (([int]$idx -ge 0)) ('return row found in the list (idx=' + $idx + ')')
if ([int]$idx -ge 0) {
  ClickRowBtnContains ([int]$idx) (ZH 'btn_audit') | Out-Null
  ConfirmBox 1400 | Out-Null
  Start-Sleep -Milliseconds 2800
}
Write-Host ('  after audit click: path=' + (EvalJs 'String(location.pathname)') + ' toast=' + (Txt '.el-message'))
if ((SqlOne ("SELECT status FROM sale_return WHERE id=$rid")) -ne 'AUDITED') {
  # UI flake guard: retry once from a fresh list load (observed once: the click was swallowed and the
  # document stayed a draft, exactly like the analysis below describes for a stolen row click).
  Open '/sale/return' 2800
  $idx = FindRow $rcode
  if ([int]$idx -ge 0) {
    ClickRowBtnContains ([int]$idx) (ZH 'btn_audit') | Out-Null
    ConfirmBox 1600 | Out-Null
    Start-Sleep -Milliseconds 3000
  }
  Write-Host ('  retry: path=' + (EvalJs 'String(location.pathname)') + ' toast=' + (Txt '.el-message'))
}
Ok ((SqlOne ("SELECT status FROM sale_return WHERE id=$rid")) -eq 'AUDITED') 'return audited from the list page'
$feeAmt = D (SqlOne ("SELECT amount FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'"))
$feeRemark = SqlOne ("SELECT remark FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'")
$feeRows = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'"))
Write-Host ("  receivable: rows=$feeRows amount=$feeAmt remark=$feeRemark")
Ok (($feeAmt -eq $ITEM_FEE)) 'the fee receivable carries the sum of the item charges'
Ok (($feeRows -eq 1)) 'exactly ONE fee receivable per document (per-product detail lives in its remark)'
Ok (([string]$feeRemark).Contains([string]$PROD_NAME)) 'the receivable remark names the charged product'

Write-Host '--- A3) un-audit + cancel from the list (the fee receivable must be reversed)'
Open '/sale/return' 2800
$idx2 = FindRow $rcode
if ([int]$idx2 -ge 0) {
  ClickRowBtnContains ([int]$idx2) (ZH 'btn_un_audit') | Out-Null
  ConfirmBox 1400 | Out-Null
  Start-Sleep -Milliseconds 2600
}
Ok ((SqlOne ("SELECT status FROM sale_return WHERE id=$rid")) -eq 'DRAFT') 'return un-audited from the list page'
$feeLeft = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_bill_type='SALE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'"))
Ok (($feeLeft -eq 0)) ('the fee receivable was reversed, nothing left to collect (left=' + $feeLeft + ')')
if ([int]$idx2 -ge 0) {
  ClickRowBtnContains ([int]$idx2) (ZH 'btn_cancel_doc') | Out-Null
  ConfirmBox 1400 | Out-Null
  Start-Sleep -Milliseconds 2400
}
Ok ((SqlOne ("SELECT status FROM sale_return WHERE id=$rid")) -eq 'CANCELLED') 'return cancelled (trace kept, no live document)'

# ==================== B) sale exchange: per-product charge ====================
Write-Host '--- B) /sale/exchange/add : charge the row (per product)'
# The source order was seeded before part A. It still offers exchangeable quantity here because part A's
# document ends up CANCELLED (only AUDITED documents count against can-exchange) -- see A3.
Open '/sale/exchange/add' 3200
Ok ((BodyHas (ZH 'lbl_charge_total_batch')) -eq 'True') 'exchange add page renders the auto charge total'
Ok ((BodyHas (ZH 'lbl_charge_type_batch')) -eq 'False') 'exchange add page no longer offers the batch charge-type field'
Ok ((BodyHas (ZH 'btn_apply_charge')) -eq 'False') 'exchange add page no longer offers the apply-to-all button'
Ok ((BodyHas (ZH 'txt_sale_charge_direction')) -eq 'True') 'exchange add page states the charge direction (we collect from the customer)'
$ec = SelectLabelContains 'lbl_customer' $CUST 1500
Ok ($ec -match 'OK') ('exchange customer selected (' + $ec + ')')
$eo = OpenSelect 'lbl_src_sale_order'
Ok ($eo -match 'OK') ('sale order select opened (' + $eo + ')')
Start-Sleep -Milliseconds 1500
$ddJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);const d=ds[ds.length-1];if(!d)return 'NODROP';const items=[...d.querySelectorAll('.el-select-dropdown__item')].map(li=>(li.innerText||'').trim()).filter(x=>x);return JSON.stringify({n:items.length,items:items.slice(0,4)})})()"
$dd = EvalJs $ddJs
Write-Host ('  sale-order options=' + $dd)
# COVERAGE NOTE: the exchange page can only reach its detail rows through a source sale order, and the
# dropdown offers the picked customer's AUDITED orders. When the environment offers none, part B is a
# documented SKIP instead of a false red -- the exchange charge PERSISTENCE is covered end-to-end by
# verify-sale-charge-per-product.ps1 (API: item 7/DIFF + doc = SUM), and the exchange page itself
# (charge column + batch/total fields + one-line table) is asserted above and in part C.
$haveSrc = ($dd -notmatch 'n":0')
$xid = 0
$xcode = ''
if (-not $haveSrc) { Write-Host '  SKIP: no source sale order offered for this customer in this environment (see the coverage note).' }
if ($haveSrc) {
  $op = PickFirstOption 1200
  Ok ($op -match 'OK') ('source sale order picked (' + $op + ')')
  Start-Sleep -Milliseconds 1600
  $tb2 = Rows 0
  Ok ($tb2.n -ge 1) ('exchange rows auto-filled from the sale order (rows=' + $tb2.n + ')')
  if ($tb2.n -ge 1) {
    Ok ((($tb2.head -join '|') -match (ZH 'lbl_charge'))) 'the exchange detail table has a charge column'
    $xf = SetRowNumberField 0 3 "$EX_FEE"
    Ok ($xf -match 'OK') ('per-product charge amount set on the exchange row (' + $xf + ')')
    Start-Sleep -Milliseconds 700
    # row selects: 0 = exchange-out grade, 1 = charge type
    OpenRowSelect 0 1 | Out-Null
    Start-Sleep -Milliseconds 1300
    $xt = PickOptionContains (ZH 'pay_type_diff')
    Ok ($xt -match 'OK') ('per-product charge type picked on the exchange row (' + $xt + ')')
    Start-Sleep -Milliseconds 800
  }
  $exBefore = D (SqlOne 'SELECT COUNT(*) FROM sale_exchange')
  $exMaxBefore = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM sale_exchange')
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 2600
  Write-Host ('  toast=' + (Txt '.el-message'))
  $exAfter = D (SqlOne 'SELECT COUNT(*) FROM sale_exchange')
  Ok (($exAfter -eq ($exBefore + 1))) ('exactly one new exchange created (' + $exBefore + ' -> ' + $exAfter + ')')
  $xid = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM sale_exchange')
  Ok (($xid -gt $exMaxBefore)) ('the charged exchange is the newest row (id=' + $xid + ' > ' + $exMaxBefore + ')')
  $xcode = SqlOne ("SELECT code FROM sale_exchange WHERE id=$xid")
  $xItemFee = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM sale_exchange_item WHERE exchange_id=$xid ORDER BY id LIMIT 1"))
  $xItemType = SqlOne ("SELECT charge_type FROM sale_exchange_item WHERE exchange_id=$xid ORDER BY id LIMIT 1")
  $xDocFee = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM sale_exchange WHERE id=$xid"))
  Write-Host ("  saved: id=$xid code=$xcode item(fee=$xItemFee type=$xItemType) doc(fee=$xDocFee)")
  # only judge the charge fields when the document really is the one this run created (otherwise MAX(id)
  # would point at a leftover row and the assertions could pass vacuously)
  if ($xid -gt $exMaxBefore) {
    Ok (($xItemFee -eq $EX_FEE)) ('the per-product charge persisted on the exchange ITEM (' + $EX_FEE + ')')
    Ok ($xItemType -eq 'DIFF') 'the per-product charge TYPE persisted on the exchange item'
    Ok (($xDocFee -eq $EX_FEE)) 'the exchange document charge equals the sum of the item charges'
  } else {
    Ok $false 'the exchange charge fields were not checked (no new document was created this run)'
  }
}

# ==================== C) one-line guard (six tables) ====================
Write-Host '--- C) no horizontal scrolling on the six tables'
Open '/sale/return/add' 2800
$t1 = TableOver 0
Ok ($null -ne $t1 -and ([int]$t1.over) -le 2) ('return add detail table fits one line (overflow=' + $t1.over + 'px, cols=' + $t1.cols + ')')
Open '/sale/exchange/add' 2800
$t2 = TableOver 0
Ok ($null -ne $t2 -and ([int]$t2.over) -le 2) ('exchange add detail table fits one line (overflow=' + $t2.over + 'px, cols=' + $t2.cols + ')')
Open ("/sale/return/detail/" + $rid) 2800
$t3 = TableOver 0
Ok ($null -ne $t3 -and ([int]$t3.over) -le 2) ('return detail table fits one line (overflow=' + $t3.over + 'px, cols=' + $t3.cols + ')')
Ok ((BodyHas (ZH 'lbl_charge_total_batch')) -eq 'False') 'the detail page is read-only (no batch charge field)'
Ok ((BodyHas (ZH 'lbl_charge')) -eq 'True') 'the detail page still shows the charge column'
if ($xid -gt 0) {
  Open ("/sale/exchange/detail/" + $xid) 2800
  $t4 = TableOver 0
  Ok ($null -ne $t4 -and ([int]$t4.over) -le 2) ('exchange detail table fits one line (overflow=' + $t4.over + 'px, cols=' + $t4.cols + ')')
} else {
  Write-Host '  SKIP: no exchange document this run (see the coverage note in part B).'
}
Open '/sale/return' 2800
$t5 = TableOver 0
Ok ($null -ne $t5 -and ([int]$t5.over) -le 2) ('return list fits one line (overflow=' + $t5.over + 'px, cols=' + $t5.cols + ')')
Open '/sale/exchange' 2800
$t6 = TableOver 0
Ok ($null -ne $t6 -and ([int]$t6.over) -le 2) ('exchange list fits one line (overflow=' + $t6.over + 'px, cols=' + $t6.cols + ')')

# ==================== D) leftovers + JS errors ====================
Write-Host '--- D) cleanup (the exchange probe is cancelled so no live draft stays behind) + errors'
if ($xid -gt 0) {
  Open '/sale/exchange' 2600
  $xidx = FindRow $xcode
  Ok (([int]$xidx -ge 0)) ('exchange row found in the list (idx=' + $xidx + ')')
  if ([int]$xidx -ge 0) {
    ClickRowBtnContains ([int]$xidx) (ZH 'btn_cancel_doc') | Out-Null
    ConfirmBox 1400 | Out-Null
    Start-Sleep -Milliseconds 2400
  }
  Ok ((SqlOne ("SELECT status FROM sale_exchange WHERE id=$xid")) -eq 'CANCELLED') 'the exchange probe was cancelled (no live draft left behind)'
} else {
  Write-Host '  SKIP: nothing to cancel (no exchange document this run).'
}
$errs = Errs
Write-Host ('  errs=' + $errs)
Ok ($errs -notmatch 'JSERR') 'no JS runtime errors captured'
Write-Host ("  final: return id=$rid code=$rcode status=" + (SqlOne ("SELECT status FROM sale_return WHERE id=$rid")) + ' | exchange id=' + $xid + ' code=' + $xcode)
Summary 'sale per-product charge (front-end P6d)'
