# audit-20260920-irreversible-stress.ps1
#
# 不可逆路径专项压测（2026-09-20）。靶点来自"状态流转原子性"静态交叉分析（清单 A：无原子保护者）。
#
# ★ 重要（F7-138 已修复后的语义）：本脚本 §1 在**修复前**能复现的是"两个并发都进入方法体"，
#   当时之所以没双倍搬移，只是因为两个事务算出**相同单号**、被 outsource_delivery.uk_code 撞回滚
#   （拒因 = "Duplicate entry 'DEL-...' for key 'uk_code'"）——属"偶然正确"。
#   修复（SupplierSettlementServiceImpl.returnMaterials 加供应商行锁）之后，**拒因已变为
#   "该供应商委外仓无可退物料"**（第二个请求等锁后读到已清零）⇒ 本脚本的断言（恰好 1 次成功、
#   委外仓恰好清零、目标仓精确一次）**仍然全部成立**，但**拒因本身已成为"锁生效"的判据**。
#   ⇒ 验证 F7-138 修复的专用脚本是 `verify-fix-f7-138.ps1`（它显式断言拒因不再是单号冲突）。
#
# 被测缺陷假设（E0 → 待实证）：
#   ① SupplierSettlementServiceImpl.returnMaterials(:167) —— 批量退料。
#      它遍历该供应商所有委外仓的正库存行，逐行：委外仓扣减（走 changeMaterialStockAllowNegative，
#      **允许负数** ⇒ 不会因库存不足被拦）+ 我方仓回补 + 生成一张 AUDITED 调拨单。
#      全程**无 claim / 无行锁 / 无幂等键**，且"读正库存"与"扣减"不在同一条 SQL 里 ⇒
#      两个并发事务都能读到同一批正库存 ⇒ 预期：**双倍搬移**（委外仓变负、我方仓双倍入账）+ 两张单。
#      顺序对照：第二次执行时委外仓已被清零 ⇒ 应抛"无可退物料"（两者形成正例/负例对照）。
#   ② 对照组（已保护路径）：并发重复 audit 一张已审核单 ⇒ 预期全部被拒且零落账（DocStatusGuard.claim 有效）。
#
# 安全设计：
#   - 先**全量快照**（warehouse_stock 该两仓逐行 + 4 张表的计数与 MAX(id)），并**同时落盘**到
#     _stress-snapshot.json，便于脚本中途崩溃后手工恢复；
#   - 压测后按"新增 id 区间删除 + 逐行 UPDATE 回原值 + 删除新插入的库存行"精确回滚；
#   - 结尾**逐表计数 + 逐行数值双自检**，并再次写入快照比对。
#   - 并发用 Start-Job，并让所有 job **等到同一个起跑时刻**再发请求（否则进程启动耗时会自然错开）。
#
# Needs a UTF-8 BOM (Chinese expectations).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$script:fail = 0
$script:skip = 0

function Ok($m)   { Write-Output ("PASS " + $m) }
function Bad($m)  { Write-Output ("FAIL " + $m); $script:fail++ }
function Skip($m) { Write-Output ("SKIP " + $m); $script:skip++ }
function Info($m) { Write-Output ("  [INFO] " + $m) }

$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function SqlAll([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  return @($o | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -e $q 2>$null | Out-Null }

function Login([string]$u, [string]$p) {
  try {
    $r = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json' `
         -Body (@{ username = $u; password = $p; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
    if ($r -and $r.data -and $r.data.token) { return $r.data.token }
    return $null
  } catch { return $null }
}
function Req([string]$method, [string]$path, [string]$tok, $body) {
  $h = @{}; if ($tok) { $h['Authorization'] = $tok }
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h -TimeoutSec 40 }
    $j = ConvertTo-Json -InputObject $body -Depth 8
    return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 40
  } catch {
    $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
    $msg = 'transport-error'
    try {
      $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      $txt = $sr.ReadToEnd()
      if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
    } catch { }
    return [pscustomobject]@{ code = $sc; msg = $msg }
  }
}
function BCode($r) { if ($null -eq $r) { return -1 }; return [int]$r.code }
function BMsg($r)  { if ($null -eq $r) { return '' }; if ($null -eq $r.msg) { return '' }; return [string]$r.msg }

$SUP = 36        # 测试加工厂A3（委外仓 68 有 3 行正库存）
$OUT_WH = 68     # 该供应商的委外仓
$TO_WH = 74      # 退回目标仓 = 自有物料一号仓
$SNAP_FILE = Join-Path $PSScriptRoot '_stress-snapshot.json'

Write-Output '=== 0) login + full snapshot ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

# sanity: the fixture must exist and be non-trivial
$chk = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND quantity>0"
if ([int]$chk -lt 1) { Skip "supplier $SUP out-warehouse $OUT_WH has no positive stock (fixture unavailable)"; Write-Output 'RESULT SKIP'; exit 0 }
$negChk = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND quantity<0"
if ([int]$negChk -gt 0) { Skip "out-warehouse already has negative rows - state is not clean, refusing to stress"; Write-Output 'RESULT SKIP'; exit 0 }

$snap = [ordered]@{
  dCount   = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
  diCount  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
  slCount  = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log')
  wsCount  = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock')
  dMax     = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_delivery')
  diMax    = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_delivery_item')
  slMax    = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
  wsMax    = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock')
  rows68   = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$OUT_WH ORDER BY id")
  rows74   = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$TO_WH ORDER BY id")
}
# the log table may be huge; only snapshot the ids we might delete (none pre-exist above slMax)
$snap | ConvertTo-Json -Depth 6 | Set-Content -Path $SNAP_FILE -Encoding UTF8
Info ("snapshot: delivery=$($snap.dCount)/item=$($snap.diCount)/stockLog=$($snap.slCount)/stock=$($snap.wsCount) outWhRows=$($snap.rows68.Count) toWhRows=$($snap.rows74.Count)")
Info "snapshot saved to _stress-snapshot.json (for manual recovery if this run dies)"

$sum68Before = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH"
$sum74Before = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$TO_WH"
Info "before: SUM(stock) wh$OUT_WH=$sum68Before  wh$TO_WH=$sum74Before"

# ---------- 1) concurrent return-materials (the un-guarded path) ----------
Write-Output ''
Write-Output "=== 1) CONCURRENT return-materials x3 (target: SupplierSettlement.returnMaterials, expected to double-move) ==="
$startAt = (Get-Date).AddSeconds(4).ToString('o')
$jobs = @()
foreach ($i in 1..3) {
  $jobs += Start-Job -ScriptBlock {
    param($api, $tok, $sid, $toWh, $runAt)
    $h = @{ Authorization = $tok }
    $b = @{ toWarehouseId = [long]$toWh } | ConvertTo-Json
    # wait for the shared start instant so all three really overlap
    while ((Get-Date) -lt [datetime]::Parse($runAt)) { Start-Sleep -Milliseconds 20 }
    try {
      $r = Invoke-RestMethod -Uri "$api/supplier-settlement/$sid/return-materials" -Method Post -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 60
      return @{ code = [int]$r.code; msg = [string]$r.msg }
    } catch {
      $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
      $msg = 'transport-error'
      try {
        $sr2 = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $txt = $sr2.ReadToEnd()
        if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
      } catch { }
      return @{ code = $sc; msg = $msg }
    }
  } -ArgumentList $B, $admin, $SUP, $TO_WH, $startAt
}
$jobs | Wait-Job -Timeout 180 | Out-Null
$res = @($jobs | Receive-Job)
$jobs | Remove-Job -Force -ErrorAction SilentlyContinue

$okCount = @($res | Where-Object { $_.code -eq 200 }).Count
$messages = @($res | Where-Object { $_.code -ne 200 } | ForEach-Object { $_.msg })
Info ("concurrent results: 200 x$okCount, other x" + ($res.Count - $okCount) + " -> " + (($messages | Select-Object -First 3) -join ' | '))
if ($okCount -ge 2) {
  Bad ("returnMaterials was accepted $okCount times CONCURRENTLY => the same stock has been moved $okCount times (no atomic guard)")
} elseif ($okCount -eq 1) {
  Ok 'only one of the three concurrent calls was accepted (no double-move observed in this window)'
} else {
  Bad ("none of the three concurrent calls succeeded - unexpected: " + (($messages | Select-Object -First 2) -join ' | '))
}

$sum68After = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH"
$sum74After = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$TO_WH"
$neg68 = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND quantity<0"
$newDel = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE id > $($snap.dMax)")
$newTransfers = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE id > $($snap.dMax) AND delivery_type='TRANSFER'")
$newAudited = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE id > $($snap.dMax) AND status='AUDITED'")
Info "after: SUM wh$OUT_WH=$sum68After (was $sum68Before)  wh$TO_WH=$sum74After (was $sum74Before)  negativeRows=$neg68  newDeliveries=$newDel (TRANSFER=$newTransfers, AUDITED=$newAudited)"

# The decisive assertion: a correctly guarded implementation can move stock at most once,
# so the out-warehouse total must land exactly on 0. A negative total is proof of a double move
# (the out leg deliberately allows negatives, so nothing else would have blocked it).
if ([double]$sum68After -lt 0) {
  Bad ("out-warehouse $OUT_WH sum went NEGATIVE ($sum68Before -> $sum68After) => stock was moved more than once (irreversible)")
} elseif ([double]$sum68After -eq 0) {
  Ok ("out-warehouse $OUT_WH drained exactly once ($sum68Before -> 0, no negative rows)")
} else {
  Bad ("out-warehouse $OUT_WH sum = $sum68After (expected 0 after a full drain)")
}
$expected74 = [double]$sum74Before + [double]$sum68Before
if ([math]::Abs([double]$sum74After - $expected74) -lt 0.0001) {
  Ok ("target warehouse $TO_WH received exactly the drained amount ($sum74Before -> $sum74After, expected $expected74)")
} else {
  Bad ("target warehouse $TO_WH = $sum74After but expected $expected74 (drained $sum68Before once) => amount moved more than once or lost")
}

# ---------- 2) sequential control: a second call after the drain must be refused ----------
Write-Output ''
Write-Output '=== 2) SEQUENTIAL control - a second call after the drain must be refused ==='
$rSeq = Req 'POST' "/supplier-settlement/$SUP/return-materials" $admin @{ toWarehouseId = [long]$TO_WH }
if ((BCode $rSeq) -eq 200) { Bad 'a sequential repeat was accepted (the drain is not idempotent / stock moved twice)' }
elseif ((BMsg $rSeq) -like '*无可退物料*') { Ok ('sequential repeat refused as expected: ' + (BMsg $rSeq)) }
else { Bad ('sequential repeat refused with an unexpected message: ' + (BMsg $rSeq)) }

# ---------- 3) control group: concurrent re-audit of an already-audited bill must write nothing ----------
Write-Output ''
Write-Output '=== 3) control group - concurrent re-audit of an AUDITED bill (guarded path, must write nothing) ==='
$auditedId = [int](SqlOne "SELECT id FROM outsource_delivery WHERE status='AUDITED' AND delivery_type='TRANSFER' ORDER BY id DESC LIMIT 1")
if ($auditedId -lt 1) { Skip 'no AUDITED transfer bill to probe' }
else {
  $slBefore = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log')
  $startAt2 = (Get-Date).AddSeconds(3).ToString('o')
  $jobs2 = @()
  foreach ($i in 1..3) {
    $jobs2 += Start-Job -ScriptBlock {
      param($api, $tok, $id, $runAt)
      $h = @{ Authorization = $tok }
      while ((Get-Date) -lt [datetime]::Parse($runAt)) { Start-Sleep -Milliseconds 20 }
      try {
        $r = Invoke-RestMethod -Uri "$api/outsource/delivery/$id/audit" -Method Put -Headers $h -TimeoutSec 40
        return @{ code = [int]$r.code; msg = [string]$r.msg }
      } catch {
        $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
        $msg = 'transport-error'
        try {
          $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
          $txt = $sr.ReadToEnd()
          if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
        } catch { }
        return @{ code = $sc; msg = $msg }
      }
    } -ArgumentList $B, $admin, $auditedId, $startAt2
  }
  $jobs2 | Wait-Job -Timeout 120 | Out-Null
  $res2 = @($jobs2 | Receive-Job)
  $jobs2 | Remove-Job -Force -ErrorAction SilentlyContinue
  $ok2 = @($res2 | Where-Object { $_.code -eq 200 }).Count
  $slAfter = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log')
  $st = SqlOne "SELECT status FROM outsource_delivery WHERE id=$auditedId"
  if ($ok2 -eq 0) { Ok ("all three concurrent re-audit calls were refused (claim holds), bill $auditedId still $st") }
  else { Bad ("re-audit was accepted $ok2 times => the CAS does NOT hold") }
  if ($slAfter -eq $slBefore) { Ok ("no stock log rows were written by the refusals ($slBefore -> $slAfter)") }
  else { Bad ("stock log grew during refused audits: $slBefore -> $slAfter") }
}

# ---------- 4) precise rollback ----------
Write-Output ''
Write-Output '=== 4) rollback ==='
$snapObj = Get-Content $SNAP_FILE -Raw | ConvertFrom-Json
# 4a) delete new delivery items + deliveries (they are all above the snapshot MAX(id))
SqlExec ("DELETE FROM outsource_delivery_item WHERE id > $($snapObj.diMax)")
SqlExec ("DELETE FROM outsource_delivery WHERE id > $($snapObj.dMax)")
# 4b) delete stock log rows created by the stress run
SqlExec ("DELETE FROM warehouse_stock_log WHERE id > $($snapObj.slMax)")
# 4c) restore the two warehouses row by row, and drop rows that did not exist before
foreach ($row in @($snapObj.rows68)) {
  $p = $row -split '\|'
  SqlExec ("UPDATE warehouse_stock SET quantity=$($p[3]) WHERE id=$($p[0])")
}
foreach ($row in @($snapObj.rows74)) {
  $p = $row -split '\|'
  SqlExec ("UPDATE warehouse_stock SET quantity=$($p[3]) WHERE id=$($p[0])")
}
SqlExec ("DELETE FROM warehouse_stock WHERE id > $($snapObj.wsMax) AND warehouse_id IN ($OUT_WH,$TO_WH)")

# ---------- 5) self-check ----------
Write-Output ''
Write-Output '=== 5) self-check (per-table counts + per-row values) ==='
$dNow = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
$diNow = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
$slNow = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock_log')
$wsNow = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock')
if ($dNow -eq $snapObj.dCount -and $diNow -eq $snapObj.diCount -and $slNow -eq $snapObj.slCount -and $wsNow -eq $snapObj.wsCount) {
  Ok ("counts restored: delivery=$dNow item=$diNow stockLog=$slNow stock=$wsNow")
} else {
  Bad ("count mismatch: delivery=$dNow/$($snapObj.dCount) item=$diNow/$($snapObj.diCount) stockLog=$slNow/$($snapObj.slCount) stock=$wsNow/$($snapObj.wsCount)")
}
$sum68Now = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH"
$sum74Now = SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$TO_WH"
if ("$sum68Now" -eq "$sum68Before" -and "$sum74Now" -eq "$sum74Before") {
  Ok ("stock amounts restored: wh$OUT_WH=$sum68Now wh$TO_WH=$sum74Now")
} else {
  Bad ("stock amounts NOT restored: wh$OUT_WH=$sum68Now (was $sum68Before) wh$TO_WH=$sum74Now (was $sum74Before)")
}
$rows68Now = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$OUT_WH ORDER BY id")
$rows74Now = @(SqlAll "SELECT CONCAT(id,'|',IFNULL(material_id,0),'|',IFNULL(quality_type,'-'),'|',quantity) FROM warehouse_stock WHERE warehouse_id=$TO_WH ORDER BY id")
if (($rows68Now -join ';') -eq (@($snapObj.rows68) -join ';') -and ($rows74Now -join ';') -eq (@($snapObj.rows74) -join ';')) {
  Ok 'both warehouses restored row-by-row (identical id/material/quality/quantity sets)'
} else {
  Bad 'warehouse rows differ from the snapshot after rollback'
}

Write-Output ''
# Only drop the on-disk snapshot when the run ended cleanly; on FAIL keep it so the state can be
# restored by hand (it is the authoritative record of what the two warehouses looked like).
if ($script:fail -eq 0) { Remove-Item $SNAP_FILE -ErrorAction SilentlyContinue }
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=" + $script:skip + ")"); exit 0 }
Write-Output ("RESULT FAIL count=" + $script:fail + " skip=" + $script:skip + " (snapshot kept at _stress-snapshot.json)")
exit 1
