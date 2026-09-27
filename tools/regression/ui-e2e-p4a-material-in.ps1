# P4a (2026-09-18 full-flow E2E): material orders + material receipt through the frontend only.
#   4 material orders (2 lines each: material TYPE + material + qty + price) -> audit (PENDING->RECEIVING)
#   -> receive into our own material warehouse (自有物料一号仓) so that the material stock exists for issuing.
# ALL DATA KEPT; rerunnable. ASCII ONLY.
#
# 2026-09-28 重写（记忆 §5.27 存量失败）：**每个周期自建"目标单"，并且只处理自己建的这几张**。
# 旧实现有两处硬伤 ⇒ 只能跑一次、第二次必红：
#   ① `need = max(0, 8 - 已有数)` ⇒ 库里够 8 张就一张不建，随后对**所有 RECEIVING 单**逐张收货
#      —— 里面混着早已收满的老单，再收被"超交"校验拒；
#   ② 上一步的 0 行不是靠状态判断，而是靠**分页首页**找行（列表 10 行/页、按 id 倒序）
#      —— 老单被挤到第 2 页 ⇒ 行找不到（现象恰是 "receipt row found for MWO-2026xxxx" 假红）。
# 现在：`need = max(1, 8 - 已有数)`（库空时照旧建到 8 张，之后每周期至少 1 张），
#   用 `id > 启动前最大 id` 精确圈定本周期目标单 ⇒ 状态判断 + 行必在首页（id 最大）⇒ 可重复跑。
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
function PickOptionEndsWith([string]$text, [int]$wait = 0) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim().endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
  return (EvalJs $js)
}

$N = 8
$moBefore = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
# 启动前的最大 id：本周期"目标单" = id 大于它的那些（自建自用，不受存量单状态/分页影响）
$maxIdBefore = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_material_order')
$need = [int]([Math]::Max(1, $N - $moBefore))
$whAux = ZH 'wh_auxA'
$matBase = ZH 'val_material'
$factoryBase = ZH 'val_factory'
$matType = @{ '1' = 'opt_mt_glass'; '2' = 'opt_mt_line'; '3' = 'opt_drv' }
# NOTE (found by probe): the add page ALREADY has 1 empty row, and the "add line" button text is
# "+ 添加物料" (zh key btn_add_material_plus) - btn_add_material ("添加物料") does not exist here.
$plan = @(
  @{ factory = 1; lines = @(@{ m = 3; q = 300; p = 12 }, @{ m = 1; q = 200; p = 10 }) },
  @{ factory = 2; lines = @(@{ m = 3; q = 200; p = 12 }, @{ m = 2; q = 200; p = 8 }) },
  @{ factory = 3; lines = @(@{ m = 1; q = 200; p = 10 }, @{ m = 2; q = 200; p = 8 }) },
  @{ factory = 4; lines = @(@{ m = 2; q = 200; p = 8 }, @{ m = 3; q = 200; p = 12 }) }
)
Write-Host ("[BASE] material_orders=$moBefore maxIdBefore=$maxIdBefore target=$N needCreate=$need auxWh=$whAux")

for ($i = 1; $i -le $need; $i++) {
  $pl = $plan[($i - 1) % $plan.Count]
  Step ('material order #' + $i + ' (factory ' + $pl.factory + ')')
  Open '/outsource/material-order' 2600
  ClearErrs | Out-Null
  ClickBtn 'btn_new' | Out-Null
  Start-Sleep -Milliseconds 2000
  SelectLabelContains 'lbl_supplier' ($factoryBase + $pl.factory) | Out-Null
  Start-Sleep -Milliseconds 1000
  $r = 0
  foreach ($ln in $pl.lines) {
    if ($r -gt 0) {
      Write-Host ('  add line: ' + (ClickBtn 'btn_add_material_plus'))
      Start-Sleep -Milliseconds 1000
    }
    $rows = (Rows 0).n
    OpenRowSelect $r 0 | Out-Null
    Start-Sleep -Milliseconds 1300
    $rt = PickOptionContains (ZH $matType['' + $ln.m])
    Start-Sleep -Milliseconds 900
    OpenRowSelect $r 1 | Out-Null
    Start-Sleep -Milliseconds 1500
    $rm = PickOptionEndsWith ($matBase + $ln.m)
    Write-Host ('  row' + $r + ' (tableRows=' + $rows + ') type=' + $rt + ' material=' + $ln.m + ' -> ' + $rm)
    Ok ((($rt -match 'OK') -and ($rm -match 'OK'))) ('material-order line filled (row ' + $r + ')')
    Start-Sleep -Milliseconds 600
    SetRowInput $r 2 ('' + $ln.q) | Out-Null
    SetRowInput $r 3 ('' + $ln.p) | Out-Null
    Start-Sleep -Milliseconds 400
    $r++
  }
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 2800
  Write-Host ('  save path=' + (EvalJs 'String(location.pathname)'))
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
  Ok (($cnt -eq ($moBefore + $i))) ('material order #' + $i + ' created (db=' + $cnt + ')')
}

Step 'audit the material orders created in THIS run (row located by code)'
$targets = @(SqlList ("SELECT code FROM outsource_material_order WHERE id > " + $maxIdBefore + " ORDER BY id"))
Write-Host ('[TARGETS] ' + ($targets -join ','))
Ok ($targets.Count -ge 1) ('this run created its own target orders (' + $targets.Count + ')')
foreach ($c in $targets) {
  Open '/outsource/material-order' 2400
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('material order row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 1000 | Out-Null
    Start-Sleep -Milliseconds 2600
    $st = SqlOne ("SELECT status FROM outsource_material_order WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'RECEIVING') ('material order ' + $c + ' is RECEIVING')
  }
}

Step 'material receipt (full qty into our own material warehouse)'
foreach ($c in $targets) {
  Open '/outsource/material-order/delivery' 3000
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('receipt row found for ' + $c)
  if ($idx -lt 0) { continue }
  $qty = D (SqlOne ("SELECT COALESCE(SUM(i.order_quantity),0) FROM outsource_material_order_item i JOIN outsource_material_order o ON o.id=i.order_id WHERE o.code='" + $c + "'"))
  Write-Host ('receive ' + $c + ' qty=' + $qty + ' -> ' + (ClickRowBtnContains $idx (ZH 'btn_receive')))
  Start-Sleep -Milliseconds 3000
  Write-Host ('  wh select: ' + (DialogOpenSelect 0))
  Start-Sleep -Milliseconds 1400
  Write-Host ('  pick wh: ' + (PickOptionContains $whAux))
  Start-Sleep -Milliseconds 700
  # 2026-09-28 修复：收货弹窗是**每行物料一个「本次收货」输入**（并带「剩余可收」上限），
  # 原实现 `DialogSetInput 1 $qty` 只填了**第 1 行**（并把"订单总量"塞给它）⇒ 多行订单只收到第一行的量
  # （实测 300/500）。现按行把「剩余可收」（该行最后一列）填进本行输入 —— 与上限一致、多行都收满。
  $fillJs = "(()=>{const vis=e=>e.getClientRects().length>0;const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!dlg)return 'NODLG';const rows=[...dlg.querySelectorAll('.el-table__body tbody tr')].filter(vis);const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;let n=0;for(const tr of rows){const tds=[...tr.querySelectorAll('td')];const rem=(tds.length?tds[tds.length-1].innerText:'').replace(/[^0-9.-]/g,'');const inp=tr.querySelector('input');if(!inp||rem==='')continue;set.call(inp,rem);inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));inp.dispatchEvent(new Event('blur',{bubbles:true}));n++}return 'FILLED:'+n})()"
  Write-Host ('  qty input (per row = 剩余可收): ' + (EvalJs $fillJs))
  Start-Sleep -Milliseconds 900
  Write-Host ('  confirm: ' + (ClickDialogBtn 'btn_confirm_deliver'))
  Start-Sleep -Milliseconds 3000
  Write-Host ('  msg=' + (Txt '.el-message') + ' errs=' + (Errs))
  $recv = D (SqlOne ("SELECT COALESCE(SUM(di.quantity),0) FROM outsource_delivery_item di JOIN outsource_delivery d ON d.id=di.delivery_id JOIN outsource_material_order o ON o.id=d.source_order_id WHERE o.code='" + $c + "'"))
  Write-Host ('  received qty in db=' + $recv)
  Ok ($recv -ge $qty) ('receipt bookkeeping for ' + $c + ' (' + $recv + ' >= ' + $qty + ')')
}

Step 'DB cross-check'
$mo = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$moAud = D (SqlOne "SELECT COUNT(*) FROM outsource_material_order WHERE status<>'PENDING'")
$items = D (SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$auxStock = D (SqlOne ("SELECT COALESCE(SUM(s.quantity),0) FROM warehouse_stock s WHERE s.material_id IS NOT NULL AND s.warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
$auxRows = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock s WHERE s.material_id IS NOT NULL AND s.warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
$payable = D (SqlOne 'SELECT COUNT(*) FROM finance_payable')
$recvLogs = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $whAux + "' LIMIT 1)"))
$recvQty = D (SqlOne ("SELECT COALESCE(SUM(received_quantity),0) FROM outsource_material_order_item"))
Write-Host ("[DB] orders=$mo notPending=$moAud items=$items auxStock=$auxStock auxRows=$auxRows payable=$payable auxLogs=$recvLogs receivedQty=$recvQty")
Ok (($mo -ge $N)) ('material orders >= ' + $N + ' (got ' + $mo + ')')
# 2026-09-28：原断言是**全局**"没有一张 PENDING"—— 自建目标单后，别的周期遗留的 PENDING 不该算到本次头上
# ⇒ 改为只看本周期目标单（它们必须都已审核）。
$tgtPending = D (SqlOne ("SELECT COUNT(*) FROM outsource_material_order WHERE id > " + $maxIdBefore + " AND status='PENDING'"))
Ok (($tgtPending -eq 0)) 'material orders created in this run are all audited (none left PENDING)'
# 4 legacy one-line orders + 4 new two-line orders = 12 lines expected
Ok (($items -ge 12)) ('material order items >= 12 (got ' + $items + ')')
Ok (($auxRows -ge 3)) ('own material warehouse holds >= 3 material rows (got ' + $auxRows + ')')
Ok (($auxStock -gt 0)) ('own material warehouse stock > 0 (got ' + $auxStock + ')')
Ok (($recvQty -ge $auxStock)) ('order received qty >= warehouse stock (' + $recvQty + ' >= ' + $auxStock + ')')
Ok (($recvLogs -ge 3)) ('own-warehouse material stock logs >= 3 (got ' + $recvLogs + ')')
Write-Host ('errs=' + (Errs))
Summary 'P4a material order + receipt'
