# 采购换货单 —— 无单换货 + 是否付费 前端用例（P11b，2026-09-21 用户口径）
#   用户口径：①「可以不强关联采购单」②「需要有是否付费，注意，这里是我们向供货商付费」。
#   本用例**全部走前端**：列表 → 新增（不选来源采购单）→ 添加明细 → 行内选产品 → 填数量 →
#   打开「是否付费」+ 选类型 + 填金额 → 保存 → 列表审核 → 详情页；每步都比对数据库（单据/库存/台账）。
#   ⚠️ 方向断言：付费台账必须是**正**数（我方欠供货商变多），source_bill_type=PURCHASE_EXCHANGE_CHARGE。
#   ASCII ONLY（中文经 ui-e2e-zh.json + base64 注入）。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$OUT_WH = 71          # 退回出库仓 = 成品二号仓
$IN_WH = 71           # 换入入库仓 = 同仓
$PROD = 60            # 明细产品「测试产品A11」（SKU-000011）
$QTY = 1              # 本次退回/换入数量
$FEE = 50             # 付费金额（我方付给供货商）
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
# SetRowInput counts EVERY input of the row -- in free-form mode the product cell holds an el-select
# (its filter input comes first), so address the numeric fields by their el-input-number order instead:
# 0 = return qty, 1 = return price, 2 = exchange-in qty, 3 = exchange-in price.
function SetRowNumberField([int]$rowIdx, [int]$nth, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ns=[...rs[$rowIdx].querySelectorAll('.el-input-number input')];if(ns.length<=$nth)return 'NONUM:'+ns.length;const el=ns[$nth];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return 'OK'})()"
  return (EvalJs $js)
}

EnsureLogin
WatchErrors
Write-Host '--- 0) open the list and create a NEW document (no purchase order on purpose)'
Open '/inventory/purchase-exchange' 2600
$c0 = ClickBtn 'btn_new_purchase_exchange'
Ok ($c0 -match 'OK') ('clicked the new button (' + $c0 + ')')
Start-Sleep -Milliseconds 2500
Ok (((EvalJs 'String(location.pathname)') -match '/purchase-exchange/add')) 'navigated to the add page'

Write-Host '--- 1) the add page offers a free-form path (add-detail button) and no mandatory purchase order'
$hasAddBtn = BodyHas (ZH 'btn_add_detail')
Ok ($hasAddBtn -eq 'True') 'the add page offers the add-detail button (free-form entry)'
Ok ((BodyHas (ZH 'lbl_src_purchase_order')) -eq 'True') 'the source purchase order field is still offered'
# 2026-09-21（逐产品口径）：单据级「是否付费」开关已移除 —— 付费改为**明细行**填写，
# 单据级只留「付费合计（自动）」+「付费说明（整单）」；是否付费由明细推导。
Ok ((BodyHas (ZH 'lbl_pay_total')) -eq 'True') 'the per-product paid total is rendered (charges live on the rows)'

Write-Host '--- 2) supplier + both warehouses (purchase order intentionally left empty)'
$r1 = SelectLabelContains 'lbl_vendor' (ZH 'val_vendor1') 1500
Ok ($r1 -match 'OK') ('supplier selected (' + $r1 + ')')
$r2 = SelectLabelContains 'lbl_return_out_wh' (ZH 'wh_finished2') 1500
Ok ($r2 -match 'OK') ('return-out warehouse selected (' + $r2 + ')')
$r3 = SelectLabelContains 'lbl_exchange_in_wh' (ZH 'wh_finished2') 1500
Ok ($r3 -match 'OK') ('exchange-in warehouse selected (' + $r3 + ')')

Write-Host '--- 3) add one detail row manually and pick the product inside the row'
$ra = ClickBtn 'btn_add_detail'
Ok ($ra -match 'OK') ('add-detail clicked (' + $ra + ')')
Start-Sleep -Milliseconds 1200
$tb = Rows 0
if ($tb -and $tb.n -ge 1) {
  Write-Host ('  detail header=' + ($tb.head -join '|'))
  Ok ((($tb.head -join '|') -match (ZH 'lbl_can_exchange'))) 'detail table shows the can-exchange column'
  $os = OpenRowSelect 0 0
  Ok ($os -match 'OK') ('product select opened inside row 0 (' + $os + ')')
  Start-Sleep -Milliseconds 1500
  $pk = PickOptionContains (ZH 'val_prod_po')
  Ok ($pk -match 'OK') ('product picked in the row (' + $pk + ')')
  Start-Sleep -Milliseconds 900
  $tb2 = Rows 0
  Ok ((($tb2.rows[0] -join '|') -match (ZH 'val_prod_po'))) 'the row now shows the picked product'
  # numeric fields inside the row: 0 = return qty, 2 = exchange-in qty (1/3 are the unit prices)
  $q1 = SetRowNumberField 0 0 "$QTY"
  $q2 = SetRowNumberField 0 2 "$QTY"
  Ok (($q1 -match 'OK') -and ($q2 -match 'OK')) ('quantities set (' + $q1 + '/' + $q2 + ')')
} else { Ok $false ('the free-form detail row was not added (rows=' + $tb.n + ')') }

Write-Host '--- 4) fill the PER-PRODUCT charge on the detail row (WE pay the supplier)'
# 2026-09-21（逐产品口径）：付费已下沉到明细行 —— 该行最后一个数字输入框 = 「付费」金额，
# 最后一个下拉 = 「付费」类型（前面的下拉是 产品 / 退回品质 / 换入品质）。金额 > 0 即该产品付费。
$feeAmtJs = "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const ts=[...root.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(!rs.length)return 'NOROW';const ns=[...rs[0].querySelectorAll('.el-input-number input')];if(!ns.length)return 'NONUM';const el=ns[ns.length-1];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,'$FEE');el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK:n='+ns.length})()"
$fa = EvalJs $feeAmtJs
Ok ($fa -match 'OK') ('paid amount filled on the detail row (' + $fa + ')')
Start-Sleep -Milliseconds 700
$ro = OpenRowSelect 0 3
Ok ($ro -match 'OK') ('paid type select opened on the row (' + $ro + ')')
Start-Sleep -Milliseconds 900
$pk = PickOptionB64 (B64 (ZH 'pay_type_diff'))
Ok ($pk -match 'OK') ('paid type picked (' + $pk + ')')
Start-Sleep -Milliseconds 800
Ok ((BodyHas (ZH 'lbl_pay_total')) -eq 'True') 'the per-product paid total is rendered'
Ok ((BodyHas (ZH 'txt_pay_direction')) -eq 'True') 'the form states the direction: WE pay the supplier'

Write-Host '--- 5) save -> a DRAFT with NO purchase order and charge_flag=1'
$before = D (SqlOne 'SELECT COUNT(*) FROM purchase_exchange')
$sv = ClickBtn 'btn_save'
Ok ($sv -match 'OK') ('save clicked (' + $sv + ')')
Start-Sleep -Milliseconds 2600
$after = D (SqlOne 'SELECT COUNT(*) FROM purchase_exchange')
Ok (($after -eq ($before + 1))) ('exactly one new document created (' + $before + ' -> ' + $after + ')')
$xid = D (SqlOne 'SELECT IFNULL(MAX(id),0) FROM purchase_exchange')
$xcode = SqlOne ("SELECT code FROM purchase_exchange WHERE id=$xid")
$poId = D (SqlOne ("SELECT IFNULL(purchase_order_id,0) FROM purchase_exchange WHERE id=$xid"))
$cf = D (SqlOne ("SELECT IFNULL(charge_flag,0) FROM purchase_exchange WHERE id=$xid"))
$ca = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_exchange WHERE id=$xid"))
Write-Host ('  saved: id=' + $xid + ' code=' + $xcode + ' po_id=' + $poId + ' charge_flag=' + $cf + ' charge_amount=' + $ca)
Ok (($xid -gt 0)) 'the document exists in the DB'
Ok (($poId -eq 0)) 'saved WITHOUT a purchase order (free-form exchange)'
Ok (($cf -eq 1)) 'paid flag persisted from the page'
Ok (($ca -eq $FEE)) ('paid amount persisted from the page (' + $FEE + ')')
# 逐产品口径（2026-09-21）：金额必须落在**明细行**上，单据级只是 Σ 的派生值
$caItem = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_exchange_item WHERE exchange_id=$xid LIMIT 1"))
$ctItem = SqlOne ("SELECT IFNULL(charge_type,'') FROM purchase_exchange_item WHERE exchange_id=$xid LIMIT 1")
Write-Host ('  item level: charge_amount=' + $caItem + ' charge_type=' + $ctItem)
Ok (($caItem -eq $FEE)) 'the charge is stored on the ITEM (per product, page filled)'
Ok (($ctItem -eq 'DIFF')) 'the item charge type persisted from the page'

Write-Host '--- 6) audit it from the list -> stock moves + THREE ledgers (return / in / PAID)'
$defBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$IN_WH AND product_id=$PROD AND quality_type='A'"))
Open '/inventory/purchase-exchange' 2600
$idx = FindRow $xcode
Ok (([int]$idx -ge 0)) ('document row found in the list (idx=' + $idx + ')')
if ([int]$idx -ge 0) {
  ClickRowBtnContains ([int]$idx) (ZH 'btn_audit') | Out-Null
  ConfirmBox 1400 | Out-Null
  Start-Sleep -Milliseconds 2600
}
Ok ((SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")) -eq 'AUDITED') 'document audited from the list page'
# 回到列表页再看"付费"标签（2026-09-21 修了操作列按钮缺 .stop 导致点按钮被行点击抢去详情页的问题，
# 这里仍显式回列表页，避免用例依赖该行为）
Open '/inventory/purchase-exchange' 2600
$defAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$IN_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ('  stock: DEFECT ' + $defBefore + ' -> ' + $defAfter + ' ; A ' + $aBefore + ' -> ' + $aAfter)
Ok (($defAfter -eq ($defBefore - $QTY))) 'free-form return side left our warehouse'
Ok (($aAfter -eq ($aBefore + $QTY))) 'free-form exchange-in landed in our warehouse'
$feePay = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
$active = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status<>'CANCELLED'"))
Write-Host ('  ledgers: paid=' + $feePay + ' active=' + $active)
Ok (($feePay -eq $FEE)) 'the page-created paid flag produced a POSITIVE payable (WE pay the supplier)'
Ok (($active -eq 3)) 'three active ledgers (return + exchange-in + paid)'
$rowJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const code=T('" + (B64 $xcode) + "');const paid=T('" + (B64 (ZH 'tag_paid')) + "');const ts=[...document.querySelectorAll('.el-table')].filter(e=>e.getClientRects().length>0);const t=ts[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];for(const tr of rs){if((tr.innerText||'').indexOf(code)>=0)return ((tr.innerText||'').indexOf(paid)>=0)?'PAID':'NOPAID'}return 'NOROW'})()"
$mark = EvalJs $rowJs
Write-Host ('  list row paid marker: ' + $mark)
Ok ($mark -eq 'PAID') 'the list marks the row as paid'

Write-Host '--- 7) detail page: free-form + paid are both visible'
Open "/inventory/purchase-exchange/detail/$xid" 2600
Ok ((BodyHas (ZH 'lbl_pay_total_detail')) -eq 'True') 'detail shows the per-product paid total (we pay the supplier)'
Ok ((BodyHas (ZH 'lbl_pay_reason')) -eq 'True') 'detail shows the whole-document paid note'
Ok ((BodyHas (ZH 'txt_no_po')) -eq 'True') 'detail marks the document as free-form (no purchase order)'
Ok ((BodyHas (ZH 'val_prod_po')) -eq 'True') 'detail lists the manually entered product'

Write-Host '--- 8) no JS runtime errors on the exchange pages'
$errs = Errs
Write-Host ('  errs=' + $errs)
Ok ($errs -notmatch 'JSERR') 'no JS runtime errors captured'

Write-Host '--- 9) 2026-09-21 UI 优化守卫：三张表都必须一行显示完（无横向滚动）'
# 量的是 .el-table__body-wrapper —— Element Plus 2.x 里真正滚动的容器；
# 量 .el-table__body 会得到"scrollWidth == clientWidth"的**假通过**（它本身不滚动）。
function TableOver([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$idx];if(!t)return 'NOTABLE/'+ts.length;const w=t.querySelector('.el-table__body-wrapper');if(!w)return 'NOWRAP';const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,' ').trim());return JSON.stringify({over:w.scrollWidth-w.clientWidth,cols:hs.length,head:hs.join('|')})})()"
  $r = EvalJs $js
  Write-Host ('  [' + $idx + '] ' + $r)
  try { return ($r.Replace('\"', '"') | ConvertFrom-Json) } catch { return $null }
}
# (a) 新增页：明细表（12 列压到 920px；原 1440px 会横向滚动 492px）
Open '/inventory/purchase-exchange/add' 2800
$t1 = TableOver 0
Ok ($null -ne $t1 -and ([int]$t1.over) -le 2) ('新增页明细表一行显示完（overflow=' + $t1.over + 'px, cols=' + $t1.cols + '）')
# (b) 详情页：明细表（888px；原 1420px）
Open ("/inventory/purchase-exchange/detail/" + $xid) 2800
$t2 = TableOver 0
Ok ($null -ne $t2 -and ([int]$t2.over) -le 2) ('详情页明细表一行显示完（overflow=' + $t2.over + 'px, cols=' + $t2.cols + '）')
# (c) 列表页：min-width 合计收到 859px（原 915 ⇒ 窗口略窄就滚）
Open '/inventory/purchase-exchange' 2800
$t3 = TableOver 0
Ok ($null -ne $t3 -and ([int]$t3.over) -le 2) ('列表页一行显示完（overflow=' + $t3.over + 'px, cols=' + $t3.cols + '）')

Write-Host ('  final: id=' + $xid + ' code=' + $xcode + ' status=' + (SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")))
Summary 'purchase-exchange free-form + paid (P11b)'
