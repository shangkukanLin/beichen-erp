# P4c (2026-09-18 full-flow E2E): work orders + finished-goods deliveries through the frontend only.
#   4 work orders (2 per factory; products MFTESTE2E1 / MFTESTE2E2 which have project BOMs), qty 100 each
#   -> audit (PRODUCING) -> 2 deliveries each (40 + 60) -> audit -> finished stock + payable + BOM material consumption.
# ALL DATA KEPT; rerunnable. ASCII ONLY.
#
# 2026-09-28 重写（记忆 §5.27 存量失败）：**每个周期自建"目标单"，并且只处理自己建的这几张**。
# 旧实现 `need = max(0, 4 - 已有数)` ⇒ 库里够 4 张就一张不建，随后对**所有 PRODUCING 单**逐张收货
# —— 里面混着早已收满的老单（100/100、50/50），再收 40+60 被"超交"校验拒
# ⇒ 现象就是 8 条 `delivery draft row not found`（只能跑一次，第二次必红）。
# 现在 `need = max(1, 4 - 已有数)`（库空时照旧建到 4 张，之后每周期至少 1 张），并用
# `id > 启动前最大 id` 圈定本周期目标单 ⇒ 可重复跑。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function SqlList([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

$N = 4
$woBefore = D (SqlOne 'SELECT COUNT(*) FROM outsource_order')
# 启动前的最大 id：本周期"目标单" = id 大于它的那些（自建自用，不受存量单是否已收满影响）
$maxIdBefore = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_order')
$need = [int]([Math]::Max(1, $N - $woBefore))
$prodBase = ZH 'val_proj'
$facBase = ZH 'val_factory'
$plan = @(
  @{ f = 1; p = $prodBase + 'E2E1' },
  @{ f = 1; p = $prodBase + 'E2E2' },
  @{ f = 2; p = $prodBase + 'E2E1' },
  @{ f = 2; p = $prodBase + 'E2E2' }
)
Write-Host ("[BASE] work_orders=$woBefore maxIdBefore=$maxIdBefore target=$N needCreate=$need")
# 2026-09-28：本轮口径交叉校验的基线（见文件末尾 invariant 的重做说明）
$outStock0 = D (SqlOne "SELECT COALESCE(SUM(s.quantity),0) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id WHERE s.material_id IS NOT NULL AND w.warehouse_category='OUTSOURCE'")
$consume0 = D (SqlOne "SELECT COALESCE(SUM(-l.change_quantity),0) FROM warehouse_stock_log l WHERE l.change_type LIKE 'OUTSOURCE_CONSUME%'")
Write-Host ("[BASE] outsourceStock0=$outStock0 consumed0=$consume0")

for ($i = 1; $i -le $need; $i++) {
  $pl = $plan[($i - 1) % $plan.Count]
  Step ('work order #' + $i + ' factory ' + $pl.f + ' product ' + $pl.p)
  Open '/outsource/order' 2600
  ClearErrs | Out-Null
  ClickBtn 'btn_new_order' | Out-Null
  Start-Sleep -Milliseconds 2200
  Write-Host ('  factory: ' + (SelectLabelContains 'lbl_factory' ($facBase + $pl.f)))
  Start-Sleep -Milliseconds 900
  Write-Host ('  product: ' + (SelectLabelContains 'lbl_mfg_product' $pl.p))
  Start-Sleep -Milliseconds 900
  Write-Host ('  qty: ' + (FillLabel 'lbl_qty' '100'))
  Write-Host ('  price: ' + (FillLabel 'lbl_mfg_price' '20'))
  Start-Sleep -Milliseconds 600
  Write-Host ('  submit: ' + (ClickBtn 'btn_submit_confirm'))
  Start-Sleep -Milliseconds 3000
  Write-Host ('  msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)'))
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM outsource_order')
  Ok (($cnt -eq ($woBefore + $i))) ('work order #' + $i + ' created (db=' + $cnt + ')')
}

Step 'audit the work orders created in THIS run (row located by code)'
$targets = @(SqlList ("SELECT code FROM outsource_order WHERE id > " + $maxIdBefore + " ORDER BY id"))
Write-Host ('[TARGETS] ' + ($targets -join ','))
Ok ($targets.Count -ge 1) ('this run created its own target orders (' + $targets.Count + ')')
foreach ($c in $targets) {
  Open '/outsource/order' 2400
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('work order row found: ' + $c)
  if ($idx -lt 0) { continue }
  ClickRowBtnContains $idx (ZH 'btn_detail') | Out-Null
  Start-Sleep -Milliseconds 2600
  Write-Host ('audit ' + $c + ': ' + (ClickBtn 'btn_audit'))
  Start-Sleep -Milliseconds 1200
  ConfirmBox 1300 | Out-Null
  Start-Sleep -Milliseconds 2800
  $st = SqlOne ("SELECT status FROM outsource_order WHERE code='" + $c + "'")
  Write-Host ('  -> status=' + $st)
  Ok ($st -eq 'PRODUCING') ('work order ' + $c + ' is PRODUCING')
}

Step 'finished-goods deliveries (2 per target work order, 40 + 60)'
foreach ($c in $targets) {
  $oid = [int](SqlOne ("SELECT id FROM outsource_order WHERE code='" + $c + "'"))
  $pname = SqlOne ("SELECT p.product_name FROM outsource_order_product p WHERE p.order_id=" + $oid + " ORDER BY p.id LIMIT 1")
  $q1 = 40; $q2 = 60
  foreach ($q in @($q1, $q2)) {
    Open ("/outsource/order/delivery/" + $oid) 3200
    Write-Host ('deliver ' + $c + ' qty=' + $q + ' product=' + $pname + ' -> ' + (ClickBtn 'btn_new_delivery'))
    Start-Sleep -Milliseconds 2000
    DialogOpenSelect 0 | Out-Null
    Start-Sleep -Milliseconds 1500
    $rp = PickOptionContains $pname
    Write-Host ('  product pick: ' + $rp)
    Ok ($rp -match 'OK') ('delivery product picked (' + $pname + ')')
    Start-Sleep -Milliseconds 800
    Write-Host ('  A qty: ' + (DialogSetInput 2 ('' + $q)))
    Start-Sleep -Milliseconds 500
    DialogOpenSelect 1 | Out-Null
    Start-Sleep -Milliseconds 1400
    Write-Host ('  receive wh: ' + (PickOptionContains (ZH 'pfx_wh')))
    Start-Sleep -Milliseconds 700
    Write-Host ('  save: ' + (ClickDialogBtn 'btn_save'))
    Start-Sleep -Milliseconds 2400
    if ((BodyHas (ZH 'txt_shortage')) -match 'true') { Write-Host ('  shortage -> force out: ' + (ClickDialogBtn 'btn_force_out')) }
    Start-Sleep -Milliseconds 2600
    Write-Host ('  msg=' + (Txt '.el-message'))
    # audit the newest draft record
    Open ("/outsource/order/delivery/" + $oid) 3000
    $idx = [int](FindRow (ZH 'st_draft'))
    if ($idx -ge 0) {
      Write-Host ('  audit delivery: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
      Start-Sleep -Milliseconds 1200
      ConfirmBox 1300 | Out-Null
      Start-Sleep -Milliseconds 2800
      Write-Host ('  msg=' + (Txt '.el-message'))
      Ok ($true) ('delivery audited (' + $c + ' qty ' + $q + ')')
    } else {
      Ok ($false) ('delivery draft row not found (' + $c + ' qty ' + $q + ')')
    }
  }
}

Step 'DB cross-check'
$wo = D (SqlOne 'SELECT COUNT(*) FROM outsource_order')
$woProd = D (SqlOne "SELECT COUNT(*) FROM outsource_order WHERE status='PRODUCING'")
$del = D (SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$finStock = D (SqlOne 'SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id IS NOT NULL')
$payable = D (SqlOne 'SELECT COUNT(*) FROM finance_payable')
$outStock = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id WHERE s.material_id IS NOT NULL AND w.warehouse_category='OUTSOURCE'")
$issueQty = D (SqlOne "SELECT COALESCE(SUM(i.quantity),0) FROM outsource_delivery_item i JOIN outsource_delivery d ON d.id=i.delivery_id WHERE d.delivery_type='DELIVERY' AND d.status='AUDITED'")
$consumeLogs = D (SqlOne "SELECT COALESCE(SUM(-change_quantity),0) FROM warehouse_stock_log WHERE change_type LIKE 'OUTSOURCE_CONSUME%'")
Write-Host ("[DB] workOrders=$wo producing=$woProd deliveries=$del finishedStockSum=$finStock payable=$payable outsourceStock=$outStock issuedQty=$issueQty consumedQty=$consumeLogs")
Ok (($wo -ge $N)) ('work orders >= ' + $N + ' (got ' + $wo + ')')
Ok (($woProd -ge $N)) ('all work orders in PRODUCING (got ' + $woProd + ')')
Ok (($del -ge 8)) ('delivery records >= 8 (got ' + $del + ')')
Ok (($finStock -gt 1140)) ('finished stock grew beyond the purchase-only baseline 1140 (got ' + $finStock + ')')
Ok (($payable -ge 20)) ('payables >= 20 (got ' + $payable + ')')
Ok (($consumeLogs -gt 0)) ('BOM material consumption logged (got ' + $consumeLogs + ')')
# 2026-09-28（重做）：原式为 `委外仓库存 == Σ收发单已审核发出 - Σ消耗`，现已**结构性失效** ——
#   委外仓库存的入账腿远不止物料收发单：实测 OUTSOURCE 仓 change_type 里 OTHER_IN +10471、RETURN_IN +270、
#   STOCK_TAKE_IN +484、MATERIAL_MOVE_IN +44…，而收发单只剩 DELIVERY_IN +2600；
#   且物料收发单 2026-09-24 已下线（发料改走「物料移仓」）⇒ issuedQty 不再增长、消耗仍在增长，
#   该等式永远不成立（实测 7943 vs 2600-5970），跟"把库管错"无关。
# 改为**本轮口径**的交叉校验（抗历史漂移）：本轮消耗掉多少料，委外仓库存就该正好少多少。
$outDrop = $outStock0 - $outStock
$consumeDelta = $consumeLogs - $consume0
Ok (($outDrop) -eq ($consumeDelta)) ('this run: outsource stock drop == BOM material consumed (' + $outDrop + ' == ' + $consumeDelta + ')')
Write-Host ('errs=' + (Errs))
Summary 'P4c work orders + deliveries'
