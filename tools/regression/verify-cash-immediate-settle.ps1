# 现金结算 = 立刻到账（2026-09-18 用户口径）验证：
#   ① 现金销售单审核 → 系统自动生成收款单**并立即审核**（不是草稿）
#   ② 该单应收立刻结清（paid = amount、unpaid = 0、status = SETTLED）
#   ③ 立刻写资金流水（收入）并体现到账户
#   ④ 反审核销售单 → 自动冲正：收款单作废 + 应收回退 + 冲正流水（"现金单仍可反审核"）
#   ⑤ 再审核 → 重新自动收款（幂等、可重复）
#   ⑥ 全局不变量：所有已审核的现金销售单，其应收都不应还有未收金额
# UI + DB；只增不删（作废/冲正都是留痕，不物理删除）。ASCII ONLY.
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
function ClickSettleSwitch([bool]$wantCash) {
  $lbl = B64 (ZH 'lbl_settle_type')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$lbl');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);const it=items.find(x=>(((x.querySelector('.el-form-item__label')||{}).innerText)||'').indexOf(L)>=0);if(!it)return 'NOITEM';const sw=it.querySelector('.el-switch');if(!sw)return 'NOSWITCH';const on=sw.classList.contains('is-checked');if(on===$($wantCash.ToString().ToLower()))return 'ALREADY';sw.click();return 'OK'})()"
  $r = EvalJs $js
  Start-Sleep -Milliseconds 500
  return $r
}
function ReceiptOf([string]$orderCode) {
  return (SqlOne ("SELECT code FROM finance_receipt WHERE source_bill_no='" + $orderCode + "' ORDER BY id DESC LIMIT 1"))
}
function ReceiptStatus([string]$rcCode) {
  if (-not $rcCode) { return '' }
  return (SqlOne ("SELECT status FROM finance_receipt WHERE code='" + $rcCode + "'"))
}
function RecvPaid([string]$orderCode) { return (D (SqlOne ("SELECT IFNULL(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no='" + $orderCode + "'"))) }
function RecvUnpaid([string]$orderCode) { return (D (SqlOne ("SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_no='" + $orderCode + "'"))) }
function RecvStatus([string]$orderCode) { return (SqlOne ("SELECT status FROM finance_receivable WHERE source_bill_no='" + $orderCode + "' ORDER BY id LIMIT 1")) }

# ---- seed (same robustness as verify-settle-switch: validate every candidate)
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
Ok (($whName -ne '') -and ($prodName -ne '') -and ($acct -ne '')) 'seed resolved'
if (($whName -eq '') -or ($prodName -eq '') -or ($acct -eq '')) { Summary 'cash immediate settle'; exit 1 }

$qty = 3; $price = 100
$expect = [decimal]($qty * $price)

Step '1) create a CASH sale order through the UI (switch ON) and audit it'
Open '/inventory/sale' 3000
ClearErrs | Out-Null
ClickBtn 'btn_new' | Out-Null
Start-Sleep -Milliseconds 2400
SelectLabelContains 'lbl_customer' ((ZH 'val_customer') + '1') | Out-Null
Start-Sleep -Milliseconds 900
SelectLabelContains 'lbl_out_wh' $whName | Out-Null
Start-Sleep -Milliseconds 900
Write-Host ('  settle switch -> cash: ' + (ClickSettleSwitch $true))
Start-Sleep -Milliseconds 1100
SelectLabelContains 'lbl_settle_account' $acct | Out-Null
Start-Sleep -Milliseconds 800
$rc = Rows 0
if ($rc.n -lt 1) { ClickBtn 'btn_add_product' | Out-Null; Start-Sleep -Milliseconds 900 }
OpenRowSelect 0 0 | Out-Null
Start-Sleep -Milliseconds 1500
Write-Host ('  product: ' + (PickOptionContains $prodName))
Start-Sleep -Milliseconds 800
OpenRowSelect 0 1 | Out-Null
Start-Sleep -Milliseconds 1100
PickOptionContains (ZH 'opt_q_a') | Out-Null
Start-Sleep -Milliseconds 700
SetRowInputT 0 0 2 ('' + $qty) | Out-Null
Start-Sleep -Milliseconds 500
SetRowInputT 0 0 3 ('' + $price) | Out-Null
Start-Sleep -Milliseconds 700
ClickBtn 'btn_save' | Out-Null
Start-Sleep -Milliseconds 2600
$code = SqlOne 'SELECT code FROM sale_order ORDER BY id DESC LIMIT 1'
$st = SqlOne ("SELECT status FROM sale_order WHERE code='" + $code + "'")
Write-Host ('  created ' + $code + ' status=' + $st + ' toast=' + (Txt '.el-message'))
Ok (($st -eq 'DRAFT') -and ($code -ne '')) ('cash sale order created as DRAFT (' + $code + ')')

Open '/inventory/sale' 3000
$idx = [int](FindRow $code)
Ok (($idx -ge 0)) ('list row found for ' + $code)
ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
Start-Sleep -Milliseconds 1500
ConfirmBox 1200 | Out-Null
Start-Sleep -Milliseconds 3200
$st = SqlOne ("SELECT status FROM sale_order WHERE code='" + $code + "'")
Write-Host ('  after audit: status=' + $st + ' msg=' + (Txt '.el-message'))
Ok (($st -eq 'AUDITED')) ('cash sale order audited (' + $st + ')')

Step '2) cash rules: the auto receipt must already be AUDITED and the receivable must be SETTLED'
$rcCode = ReceiptOf $code
$rcStatus = ReceiptStatus $rcCode
$paid = RecvPaid $code
$unpaid = RecvUnpaid $code
$recvSt = RecvStatus $code
Write-Host ('  receipt=' + $rcCode + ' status=' + $rcStatus + ' | receivable paid=' + $paid + ' unpaid=' + $unpaid + ' status=' + $recvSt)
Ok (($rcCode -ne '')) ('an auto receipt was generated (' + $rcCode + ')')
Ok (($rcStatus -eq 'AUDITED')) ('auto receipt is AUDITED immediately (got ' + $rcStatus + ') - cash means money already received')
Ok (($paid -eq $expect)) ('receivable paid == order amount (' + $paid + ' = ' + $expect + ')')
Ok (($unpaid -eq 0)) ('receivable unpaid == 0 (' + $unpaid + ')')
Ok (($recvSt -eq 'SETTLED')) ('receivable status == SETTLED (got ' + $recvSt + ')')
$income = D (SqlOne ("SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE related_bill_no='" + $rcCode + "'"))
$rev = D (SqlOne ("SELECT COALESCE(SUM(expense),0) FROM finance_cashflow WHERE related_bill_no='" + $rcCode + "'"))
Write-Host ('  cashflow for the receipt: income=' + $income + ' expense=' + $rev)
Ok (($income -eq $expect)) ('cash inflow written immediately (' + $income + ' = ' + $expect + ')')

Step '3) un-audit the sale order -> the auto receipt must be reversed and voided, receivable restored'
# 2026-09-24 user rule: 反审核/编辑都收进**详情页**（销售列表操作列只剩 详情/审核/作废）⇒ the guard must open the
# detail page and click there. (Until 2026-09-29 this step still looked for a row button, got 'NOBTN', and the 6
# consequent assertions went red -- a stale selector, not a product defect.)
$oid = SqlOne ("SELECT COALESCE(MAX(id),0) FROM sale_order WHERE code='" + $code + "'")
Open ('/inventory/sale/detail/' + $oid) 3000
$clickR = ClickBtn 'btn_un_audit'
Start-Sleep -Milliseconds 1200
$confR = ConfirmBox 1200
Write-Host ('  [ui] orderId=' + $oid + ' click=' + $clickR + ' confirm=' + $confR)
Start-Sleep -Milliseconds 3200
$st2 = SqlOne ("SELECT status FROM sale_order WHERE code='" + $code + "'")
$rcStatus2 = ReceiptStatus $rcCode
$paid2 = RecvPaid $code
$unpaid2 = RecvUnpaid $code
$recvSt2 = RecvStatus $code
$rev2 = D (SqlOne ("SELECT COALESCE(SUM(expense),0) FROM finance_cashflow WHERE related_bill_no='" + $rcCode + "'"))
Write-Host ('  after un-audit: order=' + $st2 + ' receipt=' + $rcStatus2 + ' recv paid=' + $paid2 + ' unpaid=' + $unpaid2 + ' status=' + $recvSt2 + ' reverseExpense=' + $rev2 + ' msg=' + (Txt '.el-message'))
Ok (($st2 -eq 'DRAFT')) ('sale order back to DRAFT - cash orders can still be un-audited (' + $st2 + ')')
Ok (($rcStatus2 -eq 'CANCELLED')) ('auto receipt voided for the audit trail (got ' + $rcStatus2 + ')')
Ok (($paid2 -eq 0)) ('receivable paid rolled back to 0 (' + $paid2 + ')')
# 本系统反审核台账的口径 = **冲销留痕**（CANCELLED + amount/unpaid 清零，见 I29），重审时复用同一行
Ok (($unpaid2 -eq 0)) ('voided receivable no longer carries unpaid amount (' + $unpaid2 + ')')
Ok (($recvSt2 -eq 'CANCELLED')) ('receivable voided as CANCELLED (got ' + $recvSt2 + ')')
Ok (($rev2 -eq $expect)) ('a reverse cash-flow row was written (' + $rev2 + ' = ' + $expect + ')')

Step '4) re-audit -> auto collection happens again (repeatable, no double posting)'
Open '/inventory/sale' 3000
$idx = [int](FindRow $code)
if ($idx -ge 0) {
  ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1500
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 3200
}
$st3 = SqlOne ("SELECT status FROM sale_order WHERE code='" + $code + "'")
$rcCode3 = ReceiptOf $code
$rcStatus3 = ReceiptStatus $rcCode3
$paid3 = RecvPaid $code
$unpaid3 = RecvUnpaid $code
$activeRc = D (SqlOne ("SELECT COUNT(*) FROM finance_receipt WHERE source_bill_no='" + $code + "' AND status IN ('DRAFT','AUDITED')"))
Write-Host ('  after re-audit: order=' + $st3 + ' newReceipt=' + $rcCode3 + '(' + $rcStatus3 + ') paid=' + $paid3 + ' unpaid=' + $unpaid3 + ' activeReceipts=' + $activeRc)
Ok (($st3 -eq 'AUDITED')) ('sale order audited again (' + $st3 + ')')
Ok (($rcStatus3 -eq 'AUDITED')) ('the regenerated auto receipt is AUDITED again (' + $rcStatus3 + ')')
Ok (($paid3 -eq $expect) -and ($unpaid3 -eq 0)) ('receivable settled again without double posting (paid=' + $paid3 + ', unpaid=' + $unpaid3 + ')')
Ok (($activeRc -eq 1)) ('exactly ONE active receipt per cash order (got ' + $activeRc + ')')
# 重审复用同一条应收台账（不重复挂账）
$recvRows = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_bill_no='" + $code + "'"))
Ok (($recvRows -eq 1)) ('the sale order still has exactly ONE receivable row (got ' + $recvRows + ')')

Step '5) global invariant: every AUDITED cash sale order must have a settled receivable'
# 2026-09-29 fix: the old check summed `unpaid_amount` by source_bill_no, which also picks up the
# ADVANCE (over-collection, negative) rows => two legacy 9.19 fixtures showed unpaid=-100 / -10 even though
# the order's own receivable was SETTLED. Assert the precise invariant instead: an AUDITED cash order must own
# a SETTLED SALE_ORDER receivable (looked up by source_id, ignoring ADVANCE/CANCELLED rows).
$n = 0; $bad = 0
foreach ($c in (SqlLines "SELECT code FROM sale_order WHERE status='AUDITED' AND settle_type='CASH' ORDER BY id")) { $n++ }
foreach ($c in (SqlLines "SELECT o.code FROM sale_order o WHERE o.status='AUDITED' AND o.settle_type='CASH' AND NOT EXISTS (SELECT 1 FROM finance_receivable r WHERE r.source_bill_type='SALE_ORDER' AND r.source_id=o.id AND r.status='SETTLED') ORDER BY o.id")) {
  $bad++
  $u = RecvUnpaid $c
  Write-Host ('  NOT SETTLED: ' + $c + ' (sum unpaid incl. advance rows=' + $u + ')')
}
Write-Host ('[DB] audited cash orders=' + $n + ' without a SETTLED own receivable: ' + $bad)
Ok (($bad -eq 0)) ('all AUDITED cash orders have a SETTLED receivable (checked ' + $n + ')')

Write-Host ('errs=' + (Errs))
Summary 'cash immediate settle'
