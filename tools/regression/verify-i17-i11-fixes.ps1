# I17 + I11 修复验证（2026-09-18，用户选定口径：I17 选①允许负数 / I11 按建议只列正数未付）
#   I17：加工不良退货「带料退回工厂」改为**允许负数**口径 —— 证明：把工厂委外仓某料**压成负数**后，
#        该退货单仍能**审核成功**（旧代码在严格校验下必失败 `物料[X]库存不足，无法出库`），
#        且物料库存保持"负数 + BOM 量"的负值；随后用「其他出入库(入库)」把库存**还原**到测试前值。
#   I11：付款核销下拉 `/finance/payable/unpaid` **不得**再返回负数应付。
# 纯 API + DB，只增不删（不删任何数据；库存变化全部通过真实单据流水完成）。ASCII ONLY.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$pass = 0; $fail = 0
function Ok($cond, $msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:pass++ } else { Write-Host ('FAIL ' + $msg); $script:fail++ }
}
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockOf([int]$whId, [int]$matId) {
  return (D (SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=" + $whId + " AND material_id=" + $matId)))
}

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200) 'login ok'
if ($lg.code -ne 200) { Write-Host 'RESULT FAIL login'; exit 1 }

# ============================================================ I11
Write-Host ''
Write-Host '--- I11: receivable-writeoff dropdown must not offer NEGATIVE payables'
$supId = SqlOne "SELECT supplier_id FROM finance_payable WHERE amount < 0 AND IFNULL(paid_amount,0)=0 ORDER BY id LIMIT 1"
$negDb = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE supplier_id=" + $supId + " AND status NOT IN ('SETTLED','CANCELLED') AND IFNULL(transferred_to_receivable,0)<>1 AND amount<0"))
Write-Host ('  supplier with negative payables = ' + $supId + ' ; such rows = ' + $negDb)
Ok (($supId -ne '') -and ($negDb -ge 1)) 'precondition: a supplier really owns negative payables'
$r11 = Invoke-RestMethod -Uri "$base/finance/payable/unpaid?supplierId=$supId" -Headers $h
$list11 = @($r11.data)
$negRet = @($list11 | Where-Object { [decimal]$_.amount -lt 0 }).Count
$posRet = @($list11 | Where-Object { [decimal]$_.amount -gt 0 }).Count
Write-Host ('  /payable/unpaid returned rows = ' + $list11.Count + ' (positive=' + $posRet + ', negative=' + $negRet + ')')
Ok (($negRet -eq 0)) ('I11 fixed: no negative payable is offered for write-off (negative returned=' + $negRet + ')')
Ok (($list11.Count -eq $posRet)) 'every row offered for write-off is a positive payable'

# ============================================================ I17
Write-Host ''
Write-Host '--- I17: defect return must be auditable even when the outsource warehouse stock is negative'
$target = 0; $facId = 0
foreach ($ln in (SqlLines "SELECT id, factory_id, return_type FROM outsource_return_order WHERE status='AUDITED' AND IFNULL(closed_flag,0)=0 ORDER BY id DESC")) {
  $f = $ln -split "`t"
  $rid = [int]$f[0].Trim(); $fac = [int]$f[1].Trim(); $rt = $f[2].Trim()
  if ($rt -eq 'REPAIR') { continue }
  $it = D (SqlOne ("SELECT COUNT(*) FROM outsource_return_order_item WHERE return_order_id=" + $rid + " AND quantity>0 AND outsource_material_id IS NOT NULL"))
  if ($it -lt 1) { continue }
  $paid = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_id=" + $rid + " AND IFNULL(paid_amount,0)<>0"))
  $tr = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_id=" + $rid + " AND IFNULL(transferred_to_receivable,0)=1"))
  if (($paid -eq 0) -and ($tr -eq 0)) { $target = $rid; $facId = $fac; break }
}
Write-Host ('  target defect return id=' + $target + ' factory=' + $facId)
Ok (($target -gt 0)) ('found a re-auditable defect return with material lines (id=' + $target + ')')
if ($target -eq 0) { Write-Host ('RESULT FAIL I17/I11 (PASS=' + $pass + ' FAIL=' + $fail + ')'); exit 1 }

$matId = [int](SqlOne ("SELECT outsource_material_id FROM outsource_return_order_item WHERE return_order_id=" + $target + " AND quantity>0 AND outsource_material_id IS NOT NULL ORDER BY id LIMIT 1"))
$bomQty = [int](D (SqlOne ("SELECT quantity FROM outsource_return_order_item WHERE return_order_id=" + $target + " AND outsource_material_id=" + $matId + " ORDER BY id LIMIT 1")))
$matTypeId = [int](D (SqlOne ("SELECT material_type_id FROM outsource_material WHERE id=" + $matId)))
$whId = [int](D (SqlOne ("SELECT id FROM warehouse WHERE factory_id=" + $facId + " ORDER BY id LIMIT 1")))
$stk0 = StockOf $whId $matId
Write-Host ('  material=' + $matId + ' bomQty=' + $bomQty + ' factoryWh=' + $whId + ' stockBefore=' + $stk0)
Ok (($bomQty -gt 0) -and ($whId -gt 0)) 'resolved material/bom qty/factory warehouse'

# 1) back to DRAFT while the stock is still normal
$r = Invoke-RestMethod -Uri "$base/outsource/return-order/$target/un-audit" -Method Put -Headers $h
Ok (($r.code -eq 200)) ('un-audit accepted (code=' + $r.code + ' msg=' + $r.msg + ')')
$st = SqlOne ("SELECT status FROM outsource_return_order WHERE id=" + $target)
Ok (($st -eq 'DRAFT')) ('return order back to DRAFT (status=' + $st + ')')

function OtherIo([string]$direction, [int]$qty, [string]$remark) {
  $body = @{ warehouseId = $whId; ioType = $direction; ioDate = '2026-09-18'; remark = $remark;
             items = @(@{ materialId = $matId; materialTypeId = $matTypeId; unit = 'PCS'; quantity = $qty }) } | ConvertTo-Json -Depth 6
  Invoke-RestMethod -Uri "$base/outsource/other-io" -Method Post -Headers $h -ContentType 'application/json' -Body $body | Out-Null
  $nid = [int](D (SqlOne "SELECT id FROM outsource_other_io ORDER BY id DESC LIMIT 1"))
  $ra = Invoke-RestMethod -Uri "$base/outsource/other-io/$nid/audit" -Method Put -Headers $h
  return @{ Id = $nid; Msg = $ra.msg }
}

# 2) force the warehouse stock NEGATIVE.
#    NOTE: un-audit has ALREADY removed the BOM qty from this warehouse (stock = stk0 - bomQty),
#    so out (stk0 + 10) leaves exactly -(bomQty + 10) -> adding the BOM qty back still stays negative,
#    which is the condition the old strict guard rejected (the real I17 dead-lock).
$outQty = [int]($stk0 + 10)
$resOut = OtherIo 'OUT' $outQty 'I17 verify: force negative stock'
$stkNeg = StockOf $whId $matId
Write-Host ('  other-io OUT doc #' + $resOut.Id + ' qty=' + $outQty + ' -> stock=' + $stkNeg + ' (' + $resOut.Msg + ')')
Ok (($stkNeg -lt 0)) ('stock forced negative via the allow-negative out-io path (stock=' + $stkNeg + ')')
Ok (($stkNeg -eq (0 - $bomQty - 10))) ('stock == -(bomQty+10) i.e. adding the BOM qty back will NOT reach 0 (needed to prove I17)')

# 3) THE PROOF: audit the defect return while the stock is negative and stays negative after the add
$auditBlocked = $false; $msg = ''
try {
  $ra2 = Invoke-RestMethod -Uri "$base/outsource/return-order/$target/audit" -Method Put -Headers $h
  $msg = $ra2.msg
} catch { $auditBlocked = $true; $msg = $_.Exception.Message }
Ok ((-not $auditBlocked)) ('I17 fixed: defect-return audit SUCCEEDED with negative outsource stock (msg=' + $msg + ')')
$stkAfter = StockOf $whId $matId
Write-Host ('  stock after audit = ' + $stkAfter + ' (expected ' + $stkNeg + ' + ' + $bomQty + ' = ' + ($stkNeg + $bomQty) + ')')
Ok (($stkAfter -eq ($stkNeg + $bomQty))) 'material stock advanced by the BOM qty (still negative -> the old strict guard would have blocked it)'
$st2 = SqlOne ("SELECT status FROM outsource_return_order WHERE id=" + $target)
Ok (($st2 -eq 'AUDITED')) ('return order AUDITED again (status=' + $st2 + ')')
$log = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='" + (SqlOne ("SELECT code FROM outsource_return_order WHERE id=" + $target)) + "' AND material_id=" + $matId))
Write-Host ('  stock-log rows for this return order + material = ' + $log)
Ok (($log -ge 2)) ('material movements were logged with the return bill no (rows=' + $log + ')')

# 4) restore the stock to the pre-test value via a real in-io doc
$inQty = [int](10 + $stk0)
$resIn = OtherIo 'IN' $inQty 'I17 verify: restore stock'
$stkFin = StockOf $whId $matId
Write-Host ('  other-io IN doc #' + $resIn.Id + ' qty=' + $inQty + ' -> stock=' + $stkFin)
Ok (($stkFin -eq $stk0)) ('stock restored to the pre-test value (' + $stkFin + ' == ' + $stk0 + ')')

# 5) global sanity: no negative outsource-material stock left behind
$negLeft = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock s JOIN warehouse w ON w.id=s.warehouse_id WHERE w.warehouse_category='OUTSOURCE' AND s.quantity < 0")
Write-Host ('  negative outsource stock rows now = ' + $negLeft)
Ok (($negLeft -eq 0)) ('no negative outsource-material stock left behind (rows=' + $negLeft + ')')

Write-Host ''
if ($fail -eq 0) { Write-Host ('RESULT PASS I17+I11 fixes (PASS=' + $pass + ' FAIL=0)') } else { Write-Host ('RESULT FAIL I17+I11 fixes (PASS=' + $pass + ' FAIL=' + $fail + ')') }
if ($fail -gt 0) { exit 1 }
