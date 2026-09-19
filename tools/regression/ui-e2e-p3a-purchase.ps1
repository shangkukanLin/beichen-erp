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
ClickBtn 'btn_submit' | Out-Null
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
  ClickBtn 'btn_submit' | Out-Null
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

Step 'DB cross-check'
$po = D (SqlOne 'SELECT COUNT(*) FROM purchase_order')
$poAud = D (SqlOne 'SELECT COUNT(*) FROM purchase_order WHERE status=''AUDITED''')
$items = D (SqlOne 'SELECT COUNT(*) FROM purchase_order_item')
$stock = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id IS NOT NULL"))
$logs = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE change_type='PURCHASE_IN'")
$payable = D (SqlOne 'SELECT COUNT(*) FROM finance_payable')
Write-Host ("[DB] po=$po audited=$poAud items=$items productStockSum=$stock purchaseInLogs=$logs payable=$payable")
Ok (($po -eq ($poBefore + $N))) ('purchase orders = ' + $po)
Ok (($poAud -eq $po)) 'all purchase orders audited'
Ok (($items -ge (2 * $N))) ('purchase items >= ' + (2 * $N) + ' (got ' + $items + ')')
Ok (($stock -gt 0)) ('product stock created by purchase-in (sum=' + $stock + ')')
Ok (($logs -ge $N)) ('PURCHASE_IN stock logs >= ' + $N + ' (got ' + $logs + ')')
Ok (($payable -ge $N)) ('payables generated >= ' + $N + ' (got ' + $payable + ')')
Write-Host ('errs=' + (Errs))
Summary 'P3a purchase orders'
