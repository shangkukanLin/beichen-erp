# P3c (2026-09-18 full-flow E2E): supplier payments through the frontend only.
#   2026-09-29 更新（用户口径「付款管理只显示付款记录 + 可以新增付款记录；汇总搬到应付管理」）：
#     付款单改由**统一的新增付款独立页**创建（`/finance/payment/add?supplierId=`，供应商在页内选）；
#     账户从"单账户 label 下拉"改为**分款明细表格**（多账户：A 账户 50 + B 账户 100）；
#     核销明细**默认关闭**（核销开关）⇒ 本用例显式打开并核销 500（否则全额落预付台账）；
#     审核仍在付款管理页（页签已去掉，现在是单表格页）。
#   5 payments, ALL DATA KEPT. ASCII ONLY.
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
# 2026-09-29：新增付款页的两个新控件不是 button ⇒ 补两个本地助手（保持 lib 不膨胀）
#   ① 核销开关（el-switch，默认关）：直接点 .el-switch（点它的内部 label span 不生效）
function ToggleSwitch() {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-switch')].filter(vis)[0];if(!s)return 'NOSWITCH';s.click();return 'OK'})()")
}
#   ② 分款表格里的账户下拉：优先选中文本包含 $text 的项（BANK-01 余额充足），取不到则退化为第一项
function PickOptionPrefer([string]$text, [int]$wait = 900) {
  Start-Sleep -Milliseconds $wait
  $b = B64 $text
  return (EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const N=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()!=='');if(!li.length)continue;const hit=li.find(e=>(e.innerText||'').indexOf(N)>=0)||li[0];hit.click();return 'OK:'+(hit.innerText||'').trim()}return 'NOOPT'})()")
}
# ③ 分页改成 100 条/页：付款单已有 22 张，老草稿（FK-20260919001…）**不在第 1 页**（默认 10 条/页），
#    不放大页长就会"找不到行"（2026-09-29 实测：这是**数据增长**造成的守卫假设过期，与业务改动无关）。
function PagerSize100() {
  $r = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-pagination .el-select')].filter(vis)[0];if(!s)return 'NOPAGER';const inp=s.querySelector('input');(inp||s).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||s).click();return 'OK'})()"
  if ($r -notmatch 'OK') { return $r }
  return (PickOptionContains '100' 900)
}

$N = 5
$payBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_payment')
$skipCreate = ($payBefore -ge $N)
if ($skipCreate) { Write-Host ('NOTE: ' + $payBefore + ' payments already exist -> creation skipped (audit/verify only)') }
$paidBefore = D (SqlOne 'SELECT COALESCE(SUM(paid_amount),0) FROM finance_payable')
$flowBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$sids = @(SqlList ("SELECT s.id FROM supplier s JOIN supplier_type_ref r ON r.supplier_id=s.id WHERE r.type_code='product' ORDER BY s.id LIMIT " + $N))
Write-Host ("[BASE] payments=$payBefore paidSum=$paidBefore cashflow=$flowBefore suppliers=[" + ($sids -join ',') + "] count=" + $sids.Count)
Ok ($sids.Count -ge 1) 'product suppliers resolved from DB'

Step ("payments x" + $N)
$codes = @()
for ($i = 0; $i -lt $N; $i++) {
  if ($skipCreate) { break }
  $sid = $sids[$i % $sids.Count]
  # 2026-09-29：统一的新增付款独立页（供应商页内选 / `?supplierId=` 预填）
  Open ("/finance/payment/add?supplierId=" + $sid) 4000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1600
  # ① 付款账户 = 分款明细表格第 1 行（此时核销明细表尚未渲染 ⇒ 页面上唯一的表格就是它）
  #    ⚠️ 账户是**表格里的下拉**（原页面是 label 下拉），故不能用 SelectLabelText
  $ra = ''
  for ($t = 1; $t -le 3; $t++) {
    $ra = OpenRowSelect 0 0
    if ($ra -match 'OK') { break }
    Write-Host ('  retry open account select (' + $t + '): ' + $ra + ' path=' + (EvalJs 'String(location.pathname)'))
    Start-Sleep -Milliseconds 1200
  }
  $po = PickOptionPrefer 'BANK-01'
  Write-Host ('pay account: ' + $ra + ' / ' + $po)
  Ok (($ra -match 'OK') -and ($po -match 'OK')) ('payment account row picked (supplier ' + $sid + ')')
  Write-Host ('pay amount: ' + (SetRowInput 0 1 '500'))
  Start-Sleep -Milliseconds 500
  # ② 核销开关（**默认关**）⇒ 显式打开，否则全额落预付台账、下面对 500 的核销断言不成立
  Write-Host ('write-off switch: ' + (ToggleSwitch))
  Start-Sleep -Milliseconds 1000
  Ok ((OpenRowSelect 0 0) -match 'OK') 'write-off switch turned on (核销明细表已渲染)'
  Start-Sleep -Milliseconds 300
  Write-Host ('add writeoff: ' + (ClickBtn 'btn_add_writeoff'))
  Start-Sleep -Milliseconds 1200
  Write-Host ('pick payable: ' + (OpenRowSelect 0 0))
  Start-Sleep -Milliseconds 1500
  Write-Host ('pick first payable option: ' + (PickFirstOption 1200))
  Start-Sleep -Milliseconds 900
  Write-Host ('writeoff amount: ' + (SetRowInput 0 1 '500'))
  Start-Sleep -Milliseconds 600
  Write-Host ('submit: ' + (ClickDialogBtn 'btn_ok' 1200))
  Start-Sleep -Milliseconds 2800
  Write-Host ('msg=' + (Txt '.el-message'))
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_payment')
  Ok ($cnt -eq ($payBefore + $i + 1)) ('payment ' + ($i + 1) + ' created (db=' + $cnt + ')')
  $c = SqlOne "SELECT code FROM finance_payment ORDER BY id DESC LIMIT 1"
  if ($c) { $codes += $c }
}

Step 'audit the payments created above (row located by code)'
# 2026-09-29 两处修正（都是**守卫自身的假设过期**，与业务改动无关）：
#   ① 付款管理页已去掉页签（只留「付款记录」单表格）⇒ 不再需要切页签；并要求**只开一次页面 + 分页放大到
#      100 条/页**（付款单已 20+ 张，自己刚建的单也不一定在第 1 页；旧写法逐单重开页面同样找不到行）；
#   ② **只审本用例创建的单**（`$codes`）：库里另有历史草稿是 I27「卡住的付款」夹具 ——
#      它们的核销目标指向预付台账（ADVANCE），按后端 I27 口径**设计上就不可审核**
#      （实测报错「是预付台账（多付款待抵扣），不能作为付款核销的目标」）。盲目审核所有草稿是旧版缺陷。
if ($skipCreate) {
  Write-Host ('INFO creation skipped -> nothing of our own to audit (other drafts may be I27 stuck fixtures by design)')
} else {
  Open '/finance/payment' 3200
  Start-Sleep -Milliseconds 1400
  Write-Host ('pager -> 100/page: ' + (PagerSize100))
  Start-Sleep -Milliseconds 2000
  foreach ($c in $codes) {
    $idx = [int](FindRow $c)
    Ok ($idx -ge 0) ('payment row found: ' + $c)
    if ($idx -ge 0) {
      Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
      Start-Sleep -Milliseconds 1400
      ConfirmBox 900 | Out-Null
      Start-Sleep -Milliseconds 2800
      $st = SqlOne ("SELECT status FROM finance_payment WHERE code='" + $c + "'")
      Write-Host ('  -> status=' + $st + ' msg=' + (Txt '.el-message'))
      Ok ($st -eq 'AUDITED') ('payment ' + $c + ' audited')
    }
  }
}

Step 'DB cross-check'
$pay = D (SqlOne 'SELECT COUNT(*) FROM finance_payment')
$payAud = D (SqlOne 'SELECT COUNT(*) FROM finance_payment WHERE status=''AUDITED''')
$items = D (SqlOne 'SELECT COUNT(*) FROM finance_payment_item')
$itemSum = D (SqlOne 'SELECT COALESCE(SUM(this_amount),0) FROM finance_payment_item')
$flowAfter = D (SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$outSum = D (SqlOne "SELECT COALESCE(SUM(expense),0) FROM finance_cashflow")
$payFlows = D (SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE flow_type='PAYMENT'")
$advance = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='ADVANCE'")
# 2026-09-29 多账户改造的不变量（**任何数据状态**下都必须成立，故放在 skipCreate 之外）
$splitRows = D (SqlOne 'SELECT COUNT(*) FROM finance_payment_account')
$splitSum = D (SqlOne 'SELECT COALESCE(SUM(amount),0) FROM finance_payment_account')
$mainSum = D (SqlOne 'SELECT COALESCE(SUM(amount),0) FROM finance_payment')
$noSplit = D (SqlOne 'SELECT COUNT(*) FROM finance_payment p WHERE p.account_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM finance_payment_account a WHERE a.payment_id = p.id)')
Write-Host ("[DB] payments=$pay audited=$payAud items=$items itemSum=$itemSum cashflow=$flowBefore->$flowAfter outSum=$outSum paymentFlows=$payFlows advanceRows=$advance")
Write-Host ("[SPLIT] rows=$splitRows sum=$splitSum mainSum=$mainSum paymentsMissingSplit=$noSplit")
Ok ($noSplit -eq 0) 'every payment that has an account has >= 1 split row (分款表即权威)'
Ok ($splitSum -eq $mainSum) ('split-row amounts sum == payment amounts sum (' + $splitSum + ')')
if ($skipCreate) {
  Write-Host ('INFO creation skipped -> audit-summary assertions (all audited / 500 x payments) do not apply to pre-existing fixtures')
} else {
  Ok (($pay -ge $N)) ('payments >= ' + $N + ' (got ' + $pay + ')')
  Ok (($payAud -eq $pay)) ('all payments audited (' + $payAud + '/' + $pay + ')')
  Ok (($items -ge $N)) ('payment write-off items >= ' + $N + ' (got ' + $items + ')')
  Ok (($itemSum -eq (500 * $pay))) ('write-off amounts sum to 500 x payments (' + $itemSum + ' = 500 x ' + $pay + ')')
  Ok (($payFlows -ge $pay)) ('PAYMENT cashflow rows >= payments (' + $payFlows + ' >= ' + $pay + ')')
  Ok (($outSum -ge (500 * $pay))) ('cash outflow total >= 500 x payments (' + $outSum + ')')
}
Write-Host ('INFO overpaid negative payables turned into ADVANCE rows = ' + $advance + ' (design: excess payment becomes advance/预付)')
Write-Host ('errs=' + (Errs))
Summary 'P3c supplier payments'
