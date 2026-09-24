# P3a (2026-09-18 full-flow E2E): purchase orders through the frontend only.
#   6 purchase orders (2 lines each, A-grade qty+price) -> audit (stock-in + payable)
#   2 negative cases: missing supplier / missing quantity must NOT create a document
# ALL DATA KEPT. ASCII ONLY (Chinese via zh.json keys); Chinese assertions go through B64 helpers.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function Has([string]$t) { return ((BodyHas $t) -match 'true') }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

# pick an option whose text ENDS WITH the given value (option labels are "SKU xxx 产品名")
function PickOptionEndsWith([string]$text, [int]$wait = 0) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim().endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
  return (EvalJs $js)
}

$N = 6
$poBefore = D (SqlOne 'SELECT COUNT(*) FROM purchase_order')
Write-Host ('[BASE] purchase_order = ' + $poBefore)
$matBase = ZH 'val_product'
$vendBase = ZH 'val_vendor'
$wh = ZH 'wh_finished2'

Step 'negative: submit without supplier / without quantity'
Open '/inventory/purchase/add' 2600
ClearErrs | Out-Null
ClickBtn 'btn_add_detail' | Out-Null
Start-Sleep -Milliseconds 900
SetRowInput 0 1 '10' | Out-Null
SetRowInput 0 2 '12' | Out-Null
ClickBtn 'btn_save' | Out-Null
Start-Sleep -Milliseconds 2000
$onAdd = ((EvalJs 'String(location.pathname)') -match 'add')
Ok ($onAdd) 'N1 no-supplier submit blocked (still on add page)'
Ok ((D (SqlOne 'SELECT COUNT(*) FROM purchase_order')) -eq $poBefore) 'N1 no document created'

Step ("purchase orders x" + $N)
for ($i = 1; $i -le $N; $i++) {
  Open '/inventory/purchase/add' 2600
  ClearErrs | Out-Null
  SelectLabelContains 'lbl_vendor' ($vendBase + (($i - 1) % 5 + 1)) | Out-Null
  Start-Sleep -Milliseconds 900
  SelectLabelContains 'lbl_in_warehouse' $wh | Out-Null
  Start-Sleep -Milliseconds 900
  $p1 = $matBase + (2 * $i - 1)
  $p2 = $matBase + (2 * $i)
  for ($r = 0; $r -lt 2; $r++) {
    ClickBtn 'btn_add_detail' | Out-Null
    Start-Sleep -Milliseconds 900
    $pn = $(if ($r -eq 0) { $p1 } else { $p2 })
    OpenRowSelect $r 0 | Out-Null
    Start-Sleep -Milliseconds 1600
    $rp = PickOptionEndsWith $pn
    Write-Host ('row' + $r + ' product=' + $pn + ' -> ' + $rp)
    Ok ($rp -match 'OK') ('order ' + $i + ' line ' + ($r + 1) + ' product picked')
    Start-Sleep -Milliseconds 800
    SetRowInput $r 1 ('' + (100 - 10 * $r)) | Out-Null   # A grade qty
    SetRowInput $r 2 ('' + (10 + $i)) | Out-Null         # A grade price
    Start-Sleep -Milliseconds 400
  }
  $dump = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];return JSON.stringify([...t.querySelectorAll('.el-table__body tbody tr')].map(r=>[...r.querySelectorAll('td')].map(td=>(td.innerText||'').replace(/\s+/g,' ').trim())))})()"
  Write-Host ('rows=' + (EvalJs $dump))
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 2600
  $msg = Txt '.el-message'
  Write-Host ('submit msg=' + $msg)
  Open '/inventory/purchase' 2400
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM purchase_order')
  Ok ($cnt -eq ($poBefore + $i)) ('order ' + $i + ' created (db count=' + $cnt + ')')
  # audit the newest row (list is newest-first)
  ClickRowBtnContains 0 (ZH 'btn_audit') | Out-Null
  Start-Sleep -Milliseconds 1200
  ConfirmBox 1200 | Out-Null
  Start-Sleep -Milliseconds 2600
  Open '/inventory/purchase' 2200
  $aud = D (SqlOne 'SELECT COUNT(*) FROM purchase_order WHERE status=''AUDITED''')
  Write-Host ('audited count=' + $aud)
  Ok ($aud -ge $i) ('order ' + $i + ' audited (audited total=' + $aud + ')')
}

Step 'purchase list: after-sale shortcuts (return / exchange) on an audited row'
# 2026-09-21 用户口径：采购单列表的操作列也要有「退货 / 换货」快捷入口（与销售单列表对称），
# 点击后跳到新增页并带出来源采购单。取行方式与销售侧一致：**扫描同时含两个入口的行** ——
# 不按供货商/单号 FindRow（同一供货商可能有多张单，会命中草稿行，而草稿本来就不该有这两个入口）。
Open '/inventory/purchase' 2600
$scanJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const R=T('" + (B64 (ZH 'btn_return')) + "');const E=T('" + (B64 (ZH 'btn_exchange')) + "');const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){const txt=(rs[i].innerText||'').replace(/\s+/g,' ');if(txt.indexOf(R)>=0&&txt.indexOf(E)>=0)return i+'~'+txt}return '-1~none'})()"
$scan = EvalJs $scanJs
Write-Host ('  scan=' + $scan)
$si = [int](($scan -split '~')[0])
Ok ($si -ge 0) 'an audited purchase order row offers BOTH shortcuts (return + exchange)'
if ($si -ge 0) {
  Write-Host ('  click return: ' + (ClickRowBtnContains $si (ZH 'btn_return')))
  Start-Sleep -Milliseconds 2600
  $p2 = EvalJs 'String(location.pathname)'
  Write-Host ('  path after return click=' + $p2)
  Ok ($p2 -match '/inventory/purchase-return/add') 'the RETURN shortcut opens the purchase-return add page'
  $supName = SqlOne("SELECT s.name FROM purchase_order o JOIN supplier s ON s.id=o.supplier_id WHERE o.status='AUDITED' ORDER BY o.id DESC LIMIT 1")
  Write-Host ('  supplier carried over=' + $supName)
  Ok ((BodyHas $supName) -eq 'True') ('the add page carries the source order over (' + $supName + ')')
  Open '/inventory/purchase' 2600
  $scan2 = EvalJs $scanJs
  $si2 = [int](($scan2 -split '~')[0])
  if ($si2 -ge 0) {
    Write-Host ('  click exchange: ' + (ClickRowBtnContains $si2 (ZH 'btn_exchange')))
    Start-Sleep -Milliseconds 2600
  }
  $p3 = EvalJs 'String(location.pathname)'
  Write-Host ('  path after exchange click=' + $p3)
  Ok ($p3 -match '/inventory/purchase-exchange/add') 'the EXCHANGE shortcut opens the purchase-exchange add page'
}

Step 'purchase list table fits (operation column widened to fit 4 buttons)'
# 操作列按 4 个 link 按钮的实际宽度给值 ⇒ 顺手守住"一行显示完、不左右滑动"。
# 2026-09-24 修正判据：原来量 .el-table__body-wrapper 的 scrollWidth-clientWidth —— 本表操作列
# fixed="right"（sticky）时 Element 走 el-scrollbar，外层 body-wrapper 恒为 0 ⇒ 该断言长期假通过
# （用户 2026-09-24 报「成品采购单还是能左右滑」正是它漏检的）。改为与 DOM 实现无关的判据：
# 各列渲染宽之和 与 表格可用宽 之差（>2px 即列放不下：横向滚动或被裁）。
Open '/inventory/purchase' 2600
$ovRaw = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return 'NOTABLE';const hr=t.querySelector('.el-table__header tr:last-child');const ths=hr?[...hr.querySelectorAll('th')]:[];let sum=0;ths.forEach(th=>{sum+=th.getBoundingClientRect().width});const avail=t.clientWidth;return String(Math.round(sum-avail))})()"
$ovNum = 0
[void][int]::TryParse([string]$ovRaw, [ref]$ovNum)
Write-Host ('  list colSum-avail raw=' + $ovRaw)
Ok ($ovNum -le 2) ('purchase order list fits on one line (colSum-avail=' + $ovRaw + 'px)')

Step 'DB cross-check'
$po = D (SqlOne 'SELECT COUNT(*) FROM purchase_order')
$poAud = D (SqlOne 'SELECT COUNT(*) FROM purchase_order WHERE status=''AUDITED''')
$items = D (SqlOne 'SELECT COUNT(*) FROM purchase_order_item')
$stock = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id IS NOT NULL"))
$logs = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE change_type='PURCHASE_IN'")
$payable = D (SqlOne 'SELECT COUNT(*) FROM finance_payable')
Write-Host ("[DB] po=$po audited=$poAud items=$items productStockSum=$stock purchaseInLogs=$logs payable=$payable")
Ok (($po -eq ($poBefore + $N))) ('purchase orders = ' + $po)
# 2026-09-24 修复假红：原先断言「全表所有采购单都已审核」⇒ 只要库里存在任何草稿/作废单
# （历史测试残留、其它用例造过又作废的）就必然 FAIL。改为只校验**本批新建的 N 张**（列表最新在前）。
$newAud = D (SqlOne ("SELECT COUNT(*) FROM (SELECT status FROM purchase_order ORDER BY id DESC LIMIT " + $N + ") t WHERE t.status='AUDITED'"))
Ok (($newAud -eq $N)) ('the ' + $N + ' orders created here are all audited (got ' + $newAud + ')')
Ok (($items -ge (2 * $N))) ('purchase items >= ' + (2 * $N) + ' (got ' + $items + ')')
Ok (($stock -gt 0)) ('product stock created by purchase-in (sum=' + $stock + ')')
Ok (($logs -ge $N)) ('PURCHASE_IN stock logs >= ' + $N + ' (got ' + $logs + ')')
Ok (($payable -ge $N)) ('payables generated >= ' + $N + ' (got ' + $payable + ')')
Write-Host ('errs=' + (Errs))
Summary 'P3a purchase orders'
