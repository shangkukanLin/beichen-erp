# verify-material-move.ps1 -- material warehouse-move (new 2026-09-24, replaces the retired manual material delivery doc)
# Covers: create validation / audit (from-qty, to+qty) / un-audit restore / cancel / stock+log invariants /
#         the retired manual entry must be rejected (POST /outsource/delivery) / 401 without token.
# NOTE: ASCII ONLY (Chinese in output makes `powershell -File` mis-parse the file).
# Side effects: creates one material-move draft (cancelled at the end) + one audited/un-audited pair (stock restored).
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$script:fail = 0

function Ok([bool]$cond, [string]$msg) {
  if ($cond) { Write-Output ('PASS ' + $msg) } else { $script:fail++; Write-Output ('FAIL ' + $msg) }
}
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o | Where-Object { "$_" -ne '' }); if ($l.Count -lt 1) { return '' }
  return ("$($l[0])").Trim()
}
function SqlAll([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @($o | ForEach-Object { "$_" })
}
function Api([string]$method, [string]$path, $obj, [hashtable]$H) {
  try {
    # GET 不能带 body（PowerShell 会直接抛 "Cannot send a content-body with this verb-type."）
    $a = @{ Uri = ($B + $path); Method = $method; TimeoutSec = 20 }
    if ($H) { $a.Headers = $H }
    if ($method -ne 'Get') {
      $json = if ($obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }
      $a.ContentType = 'application/json; charset=utf-8'
      $a.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @a
  } catch {
    $r = $_.Exception.Response
    if ($r) { return @{ code = [int]$r.StatusCode; msg = 'HTTP' + [int]$r.StatusCode } }
    return @{ code = -1; msg = $_.Exception.Message }
  }
}
function Num([string]$q) { $v = SqlOne $q; if ($v -eq '') { return [decimal]0 } return [decimal]$v }

# ---------------- login / auth ----------------
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json' -Body (@{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
Ok ([int]$login.code -eq 200) ('login code=' + $login.code)
$H = @{ Authorization = [string]$login.data.token }
$noauth = Api 'Get' '/inventory/material-move/page?pageNum=1&pageSize=1' $null $null
Ok (([int]$noauth.code -eq 401) -or ("$($noauth.msg)" -like '*401*')) ('no token -> 401 (got ' + $noauth.code + '/' + $noauth.msg + ')')

# ---------------- fixture: a material with stock in warehouse A, and another warehouse B ----------------
$row = SqlOne "SELECT CONCAT(warehouse_id, ',', material_id, ',', quantity) FROM warehouse_stock WHERE material_id IS NOT NULL AND quantity > 10 ORDER BY quantity DESC LIMIT 1"
$p = $row -split ','
$fromWh = $p[0]; $mat = $p[1]; $qty = 4
$toWh = SqlOne "SELECT id FROM warehouse WHERE id <> $fromWh ORDER BY id LIMIT 1"
Ok (([int]$fromWh -gt 0) -and ([int]$mat -gt 0) -and ([int]$toWh -gt 0)) ("fixture from=$fromWh to=$toWh material=$mat")
$today = (Get-Date -Format 'yyyy-MM-dd')

# ---------------- 1) create validation ----------------
$bad1 = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$fromWh; items = @(@{ materialId = [long]$mat; quantity = 1 }) } $H
Ok ([int]$bad1.code -ne 200) ('same from/to warehouse rejected (code=' + $bad1.code + ')')
$bad2 = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; items = @() } $H
Ok ([int]$bad2.code -ne 200) ('empty items rejected (code=' + $bad2.code + ')')
$bad3 = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; items = @(@{ materialId = [long]$mat; quantity = -3 }) } $H
Ok ([int]$bad3.code -ne 200) ('negative qty rejected (code=' + $bad3.code + ')')

# ---------------- 1b) quality grade (2026-09-24): recorded on the doc, NOT part of stock ----------------
$cq = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; remark = 'verify-material-move-quality'; items = @(@{ materialId = [long]$mat; qualityType = 'DEFECT'; quantity = 2 }) } $H
Ok ([int]$cq.code -eq 200) ('create draft with qualityType=DEFECT (code=' + $cq.code + ')')
$qid = SqlOne "SELECT id FROM inventory_material_move ORDER BY id DESC LIMIT 1"
Ok ((SqlOne "SELECT IFNULL(quality_type,'') FROM inventory_material_move_item WHERE move_id=$qid ORDER BY id LIMIT 1") -eq 'DEFECT') 'qualityType persisted on the item'
$cqBad = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; items = @(@{ materialId = [long]$mat; qualityType = 'X'; quantity = 1 }) } $H
Ok ([int]$cqBad.code -ne 200) ('invalid quality rejected (code=' + $cqBad.code + ')')
$stockRows = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE material_id=$mat"
$aQ = Api 'Put' "/inventory/material-move/$qid/audit" @{} $H
$stockRowsAfter = SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE material_id=$mat"
Ok (([int]$aQ.code -eq 200) -and ([int]$stockRowsAfter -eq [int]$stockRows)) ('quality is doc-level only: material stock rows unchanged (' + $stockRows + ' -> ' + $stockRowsAfter + ')')
Api 'Put' "/inventory/material-move/$qid/un-audit" @{} $H | Out-Null
Api 'Put' "/inventory/material-move/$qid/cancel" @{} $H | Out-Null
Ok ((SqlOne "SELECT status FROM inventory_material_move WHERE id=$qid") -eq 'CANCELLED') 'cleanup: quality draft cancelled'

# ---------------- 2) draft does not touch stock; cancel works ----------------
$before = Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$fromWh AND material_id=$mat"
$toBefore = Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$toWh AND material_id=$mat"
$c = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; remark = 'verify-material-move'; items = @(@{ materialId = [long]$mat; quantity = $qty }) } $H
Ok ([int]$c.code -eq 200) ('create draft (code=' + $c.code + ')')
$id = SqlOne "SELECT id FROM inventory_material_move ORDER BY id DESC LIMIT 1"
$code = SqlOne "SELECT code FROM inventory_material_move WHERE id=$id"
Ok ($code -like 'MYC-*') ('code prefix MYC- (' + $code + ')')
Ok ((SqlOne "SELECT status FROM inventory_material_move WHERE id=$id") -eq 'DRAFT') 'new doc is DRAFT'
Ok ((Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$fromWh AND material_id=$mat") -eq $before) 'draft does not move stock'
Ok ([int](Api 'Put' "/inventory/material-move/$id/cancel" @{} $H).code -eq 200) 'draft can be cancelled'

# ---------------- 3) audit -> from- / to+ ----------------
$c2 = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; remark = 'verify-material-move'; items = @(@{ materialId = [long]$mat; quantity = $qty }) } $H
$id = SqlOne "SELECT id FROM inventory_material_move ORDER BY id DESC LIMIT 1"
$code = SqlOne "SELECT code FROM inventory_material_move WHERE id=$id"
$a = Api 'Put' "/inventory/material-move/$id/audit" @{} $H
Ok ([int]$a.code -eq 200) ('audit (code=' + $a.code + ')')
$after = Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$fromWh AND material_id=$mat"
$toAfter = Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$toWh AND material_id=$mat"
Ok (($before - $after) -eq $qty) ("from warehouse -$qty ($before -> $after)")
Ok (($toAfter - $toBefore) -eq $qty) ("to warehouse +$qty ($toBefore -> $toAfter)")
Ok ((SqlOne "SELECT status FROM inventory_material_move WHERE id=$id") -eq 'AUDITED') 'status AUDITED'
Ok ("$(SqlOne "SELECT IFNULL(auditor_name,'') FROM inventory_material_move WHERE id=$id")" -ne '') 'auditor stamped'
# 注意：warehouse_stock_log.related_bill_id **不是全局唯一**（其它单据类型可能复用同一 id）
# ⇒ 判读本单据的流水必须同时带 related_bill_type 过滤。
$logs = @(SqlAll "SELECT CONCAT(change_type,':',related_bill_type) FROM warehouse_stock_log WHERE related_bill_id=$id AND related_bill_type IN ('MATERIAL_MOVE','MATERIAL_MOVE_UN_AUDIT') ORDER BY id")
$logStr = ($logs -join ' ; ')
Ok (($logs.Count -eq 2) -and ($logStr -like '*MATERIAL_MOVE_OUT*') -and ($logStr -like '*MATERIAL_MOVE_IN*')) ('2 audit log rows (' + $logStr + ')')
Ok ([int](Api 'Put' "/inventory/material-move/$id/audit" @{} $H).code -ne 200) 're-audit rejected'
Ok ([int](Api 'Put' "/inventory/material-move/$id/cancel" @{} $H).code -ne 200) 'audited doc cannot be cancelled'

# ---------------- 4) un-audit restores ----------------
$u = Api 'Put' "/inventory/material-move/$id/un-audit" @{} $H
Ok ([int]$u.code -eq 200) ('un-audit (code=' + $u.code + ')')
Ok ((Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$fromWh AND material_id=$mat") -eq $before) 'un-audit restores from warehouse'
Ok ((Num "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$toWh AND material_id=$mat") -eq $toBefore) 'un-audit restores to warehouse'
$logs2 = ((@(SqlAll "SELECT related_bill_type FROM warehouse_stock_log WHERE related_bill_id=$id AND related_bill_type IN ('MATERIAL_MOVE','MATERIAL_MOVE_UN_AUDIT') ORDER BY id")) -join ' ')
Ok ($logs2 -like '*MATERIAL_MOVE_UN_AUDIT*') 'un-audit writes MATERIAL_MOVE_UN_AUDIT log'
Api 'Put' "/inventory/material-move/$id/cancel" @{} $H | Out-Null
Ok ((SqlOne "SELECT status FROM inventory_material_move WHERE id=$id") -eq 'CANCELLED') 'cleanup: temp doc cancelled'

# ---------------- 5) retired manual entry must be blocked (the core invariant of this change) ----------------
$old = Api 'Post' '/outsource/delivery' @{ deliveryType = 'TRANSFER'; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; items = @() } $H
Ok ([int]$old.code -ne 200) ('legacy manual material-delivery create rejected (code=' + $old.code + ')')
$oldMsg = "$($old.msg)"
Ok (($oldMsg -like '*material-move*') -or ($oldMsg -like '*MYC*') -or ($oldMsg -like '*2026-09-24*')) ('rejection points to the replacement (' + $oldMsg.Substring(0, [Math]::Min(60, $oldMsg.Length)) + ')')
Ok ([int](Api 'Get' '/outsource/delivery/page?pageNum=1&pageSize=1' $null $H).code -eq 200) 'legacy docs still readable (history)'
Ok ([int](Api 'Get' '/outsource/material-order/page?pageNum=1&pageSize=1' $null $H).code -eq 200) 'material receiving flow unaffected'

if ($script:fail -eq 0) { Write-Output 'RESULT PASS material move (validation + stock + legacy entry blocked)' }
else { Write-Output ('RESULT FAIL items ' + $script:fail); exit 1 }
