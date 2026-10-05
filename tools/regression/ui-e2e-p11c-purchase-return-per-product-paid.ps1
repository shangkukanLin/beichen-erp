# P11c (2026-09-21 user rule): purchase RETURN must have a "do we pay" flag, the payment is US paying the
#   SUPPLIER, and it must be PER PRODUCT. Frontend only: list -> add page -> supplier/warehouse -> add row
#   -> pick product (by SKU) -> qty/price -> fill the PER-PRODUCT paid amount + type on the row -> save
#   -> audit from the list -> detail page; every step is cross-checked against the DB.
#   Direction assertion: the paid ledger must be POSITIVE (we owe the supplier more),
#   source_bill_type = PURCHASE_RETURN_CHARGE (the return side itself stays NEGATIVE = we owe less).
#   Rerunnable + self-healing fixtures (picks the product/warehouse that actually has grade-A stock).
#   ASCII ONLY (Chinese text comes from ui-e2e-zh.json through base64; runtime Chinese comes from the DB).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function Step($n) { Write-Host ('--- STEP ' + $n) }
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

# pick the row's product through the remote search: type the SKU into the row select, then pick the option
function PickRowProduct([int]$rowIdx, [string]$sku) {
  $v = B64 $sku
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const sel=rs[$rowIdx].querySelector('.el-select');if(!sel)return 'NOSELECT';const w=sel.querySelector('.el-select__wrapper')||sel.querySelector('.el-input');if(w)w.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));const inp=sel.querySelector('input');if(!inp)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(inp,V);inp.dispatchEvent(new Event('input',{bubbles:true}));return 'OK'})()"
  $r = EvalJs $js
  if ($r -notmatch 'OK') { return $r }
  Start-Sleep -Milliseconds 1600
  return (PickOptionContains $sku)
}
function TableOver([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$idx];if(!t)return 'NOTABLE/'+ts.length;const w=t.querySelector('.el-table__body-wrapper');if(!w)return 'NOWRAP';const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,' ').trim());return JSON.stringify({over:w.scrollWidth-w.clientWidth,cols:hs.length,head:hs.join('|')})})()"
  $r = EvalJs $js
  Write-Host ('  [' + $idx + '] ' + $r)
  try { return ($r.Replace('\"', '"') | ConvertFrom-Json) } catch { return $null }
}

# ---- fixtures (self-healing: the product/warehouse pair that really holds grade-A stock) ----
$fx = [string](SqlOne "SELECT CONCAT(ws.warehouse_id,'|',ws.product_id,'|',p.sku,'|',p.name) FROM warehouse_stock ws JOIN product p ON p.id=ws.product_id WHERE ws.quality_type='A' AND ws.product_id IS NOT NULL AND IFNULL(ws.quantity,0) > 10 ORDER BY ws.quantity DESC LIMIT 1")
$f = @($fx -split '\|')
# 2026-10-05 F7-294: fixture-missing => SKIP + exit 0 (was a red).
if ($f.Count -lt 4) { Write-Host ('RESULT SKIP p11c (fixture missing: no grade-A stock found)'); exit 0 }
$WH = [int]$f[0]; $PROD = [int]$f[1]; $SKU = [string]$f[2]; $PNAME = [string]$f[3]
$WH_NAME = [string](SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=$WH"))
$QTY = 2; $FEE = 30
$stockBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ('[BASE] wh=' + $WH + ' (' + $WH_NAME + ') product=' + $PROD + ' sku=' + $SKU + ' name=' + $PNAME + ' stock=' + $stockBefore)
Ok (($WH -gt 0) -and ($PROD -gt 0) -and ($SKU.Length -gt 0)) 'fixture resolved (product with grade-A stock)'
Ok (($stockBefore -ge $QTY)) 'enough stock for the return (>= qty)'

Step '1) the add page renders the per-product paid total (charges live on the rows)'
Open '/inventory/purchase-return/add' 2800
Ok ((BodyHas (ZH 'lbl_pay_total')) -eq 'True') 'the per-product paid total is rendered on the add page'
Ok ((BodyHas (ZH 'lbl_vendor')) -eq 'True') 'the supplier field is offered'

Step '2) supplier + return warehouse'
$r1 = SelectLabelContains 'lbl_vendor' (ZH 'val_vendor') 1400
Ok ($r1 -match 'OK') ('supplier selected (' + $r1 + ')')
$r2 = SelectLabelContains 'lbl_return_warehouse' $WH_NAME 1400
Ok ($r2 -match 'OK') ('return warehouse selected (' + $r2 + ')')

Step '3) add a detail row and pick the product inside the row (remote search by SKU)'
$ra = ClickBtn 'btn_add_detail'
Ok ($ra -match 'OK') ('add-detail clicked (' + $ra + ')')
Start-Sleep -Milliseconds 1300
$tb = Rows 0
Ok (($null -ne $tb) -and ($tb.n -ge 1)) ('one detail row exists (rows=' + $tb.n + ')')
if (($null -ne $tb) -and ($tb.n -ge 1)) {
  $pk = PickRowProduct 0 $SKU
  Ok ($pk -match 'OK') ('product picked by SKU (' + $pk + ')')
  Start-Sleep -Milliseconds 800
  # numeric fields inside the row: 0 = return qty, 1 = unit price, 2 = the PER-PRODUCT paid amount
  $n1 = SetRowInput 0 2 "$QTY"
  $n2 = SetRowInput 0 3 '10'
  Ok (($n1 -match 'OK') -and ($n2 -match 'OK')) ('qty + price set (' + $n1 + '/' + $n2 + ')')
} else { Ok $false 'the detail row was not added' }

Step '4) fill the PER-PRODUCT paid amount + type on the row (WE pay the supplier)'
$fa = SetRowInput 0 4 "$FEE"      # 5th input of the row = the paid amount (after qty and price)
Ok ($fa -match 'OK') ('paid amount filled on the row (' + $fa + ')')
Start-Sleep -Milliseconds 700
$ro = OpenRowSelect 0 2           # 3rd select of the row = the paid type (product / quality / paid type)
Ok ($ro -match 'OK') ('paid type select opened on the row (' + $ro + ')')
Start-Sleep -Milliseconds 900
$pt = PickOptionB64 (B64 (ZH 'pay_type_diff'))
Ok ($pt -match 'OK') ('paid type picked (' + $pt + ')')
Start-Sleep -Milliseconds 800
Ok ((BodyHas (ZH 'lbl_pay_total')) -eq 'True') 'the per-product paid total is still rendered'
Ok ((BodyHas (ZH 'txt_pay_direction')) -eq 'True') 'the form states the direction: WE pay the supplier'

Step '5) save -> a DRAFT whose charge lives on the ITEM (doc = SUM, derived by the backend)'
$before = D (SqlOne 'SELECT COUNT(*) FROM purchase_return')
$sv = ClickBtn 'btn_save'
Ok ($sv -match 'OK') ('save clicked (' + $sv + ')')
Start-Sleep -Milliseconds 2800
$after = D (SqlOne 'SELECT COUNT(*) FROM purchase_return')
Ok (($after -eq ($before + 1))) ('exactly one new document created (' + $before + ' -> ' + $after + ')')
$rid = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM purchase_return')
$rcode = SqlOne ("SELECT code FROM purchase_return WHERE id=$rid")
$rFlag = D (SqlOne ("SELECT IFNULL(charge_flag,0) FROM purchase_return WHERE id=$rid"))
$rAmt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return WHERE id=$rid"))
$iAmt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return_item WHERE return_id=$rid LIMIT 1"))
$iType = SqlOne ("SELECT IFNULL(charge_type,'') FROM purchase_return_item WHERE return_id=$rid LIMIT 1")
$iProd = D (SqlOne ("SELECT IFNULL(product_id,0) FROM purchase_return_item WHERE return_id=$rid LIMIT 1"))
Write-Host ('  saved: id=' + $rid + ' code=' + $rcode + ' flag=' + $rFlag + ' doc=' + $rAmt + ' item=' + $iAmt + '/' + $iType + ' itemProduct=' + $iProd)
Ok ($rid -gt 0) 'the document exists in the DB'
Ok (($iAmt -eq $FEE)) ('the paid amount is stored on the ITEM (' + $FEE + ')')
Ok ($iType -eq 'DIFF') 'the per-product paid type persisted from the page'
Ok (($rAmt -eq $FEE)) 'the document amount equals the SUM of item charges (derived)'
Ok (($rFlag -eq 1)) 'the document paid flag was derived from the items'

Step '6) audit it from the list -> stock out + NEGATIVE return payable + POSITIVE paid payable'
Open '/inventory/purchase-return' 2600
$idx = FindRow $rcode
Ok (([int]$idx -ge 0)) ('document row found in the list (idx=' + $idx + ')')
if ([int]$idx -ge 0) {
  ClickRowBtnContains ([int]$idx) (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1400
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 2800
}
Ok ((SqlOne ("SELECT status FROM purchase_return WHERE id=$rid")) -eq 'AUDITED') 'document audited from the list page'
$stockAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$WH AND product_id=$PROD AND quality_type='A'"))
$feePay = D (SqlOne ("SELECT IFNULL(amount,0) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'"))
$feeRows = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'"))
$feeRemark = SqlOne ("SELECT IFNULL(remark,'') FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status<>'CANCELLED'")
$retPay = D (SqlOne ("SELECT IFNULL(amount,0) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN' AND source_bill_no='$rcode' AND status<>'CANCELLED'"))
Write-Host ('  stock ' + $stockBefore + ' -> ' + $stockAfter + ' ; ledgers paid=+' + $feePay + ' rows=' + $feeRows + ' return=' + $retPay)
Write-Host ('  paid remark=' + $feeRemark)
Ok (($stockAfter -eq ($stockBefore - $QTY))) 'the return left our warehouse (stock -qty)'
Ok (($feePay -eq $FEE)) 'the page-created charge produced a POSITIVE payable (WE pay the supplier)'
Ok (($feeRows -eq 1)) 'exactly ONE paid ledger per document'
Ok ($feeRemark.Contains($PNAME)) 'the paid remark names the charged product'
Ok ($retPay -lt 0) 'the return side keeps its own NEGATIVE payable (we owe less)'

Step '7) detail page shows the per-product paid total + the whole-document paid note'
Open ("/inventory/purchase-return/detail/" + $rid) 2800
Ok ((BodyHas (ZH 'lbl_pay_total_detail')) -eq 'True') 'detail shows the per-product paid total'
Ok ((BodyHas (ZH 'lbl_pay_reason')) -eq 'True') 'detail shows the whole-document paid note'

Step '8) 2026-09-21 UI guard: the detail/edit/table must fit one line (no horizontal scrolling)'
Open ("/inventory/purchase-return/detail/" + $rid) 2800
$t1 = TableOver 0
Ok ($null -ne $t1 -and ([int]$t1.over) -le 2) ('detail table fits (overflow=' + $t1.over + 'px, cols=' + $t1.cols + ')')
Open '/inventory/purchase-return/add' 2800
$t2 = TableOver 0
Ok ($null -ne $t2 -and ([int]$t2.over) -le 2) ('add table fits (overflow=' + $t2.over + 'px, cols=' + $t2.cols + ')')
Open '/inventory/purchase-return' 2800
$t3 = TableOver 0
Ok ($null -ne $t3 -and ([int]$t3.over) -le 2) ('list table fits (overflow=' + $t3.over + 'px, cols=' + $t3.cols + ')')

Step '9) no JS runtime errors on the purchase-return pages'
$errs = Errs
Write-Host ('  errs=' + $errs)
Ok ($errs -notmatch 'JSERR') 'no JS runtime errors captured'

Write-Host ('  final: id=' + $rid + ' code=' + $rcode + ' status=' + (SqlOne ("SELECT status FROM purchase_return WHERE id=$rid")))
Summary 'purchase-return per-product paid (P11c)'
