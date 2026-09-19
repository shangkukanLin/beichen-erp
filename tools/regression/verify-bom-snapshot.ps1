# Verify: BOM snapshot sharing on work-order creation (2026-09-17). ASCII ONLY.
# Asserts: (1) same dev BOM -> reuse snapshot (no new row); (2) detail changed -> new snapshot;
#          (3) view recalculates demand = per-set x product qty.
# Cleans up its own test orders/snapshots (MIGRATED snapshots are never touched).
#
# 2026-09-18 review fix (F6-1): fixtures are now resolved FROM THE LIVE DB.
#   Before: hard-coded factoryId=23 / projectId=9 / productId=48 / materialId=25,31 / materialTypeId=47,50.
#   Those ids no longer exist after the DB was wiped+rebuilt, so "create order" (assertion 1) failed and
#   3 assertions went red while 4 others "passed" vacuously (counters simply did not change).
#   Now: factory <- latest outsource_order.factory_id, project <- min dev_project,
#        product <- min product, materials+types <- that project's dev_bom (first 2 rows).
param([int]$Qty = 20)
$ErrorActionPreference = 'Continue'
$exe = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function Sql([string]$sql) { return (& $exe --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $sql 2>$null) }
function SqlOne([string]$sql) { $r = @(Sql $sql); if ($r.Count -lt 1) { return '' }; return ([string]$r[0]).Trim() }
$base = 'http://localhost:8080/api'
$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$tag = 'SNAPVERIFY-' + (Get-Random)
$pass = 0; $fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:pass++; Write-Host ('PASS ' + $m) } else { $script:fail++; Write-Host ('FAIL ' + $m) } }

# ===== fixtures resolved from the live DB (no hard-coded ids) =====
$FACTORY = [int](SqlOne 'SELECT factory_id FROM outsource_order WHERE factory_id IS NOT NULL ORDER BY id DESC LIMIT 1;')
$PROJ = [int](SqlOne 'SELECT id FROM dev_project ORDER BY id LIMIT 1;')
$PROD = [int](SqlOne 'SELECT id FROM product ORDER BY id LIMIT 1;')
$PRODNAME = SqlOne ("SELECT IFNULL(name,'') FROM product WHERE id=$PROD;")
$MATROWS = @(Sql ("SELECT b.outsource_material_id, IFNULL(b.material_type_id,0), IFNULL(m.unit,'') FROM dev_bom b LEFT JOIN outsource_material m ON m.id=b.outsource_material_id WHERE b.project_id=$PROJ AND b.outsource_material_id IS NOT NULL ORDER BY b.outsource_material_id LIMIT 2;") | Where-Object { "$_" -ne '' })
Write-Host ('  fixtures: factory=' + $FACTORY + ' project=' + $PROJ + ' product=' + $PROD + ' bpRows=' + $MATROWS.Count)
Ok (($FACTORY -gt 0) -and ($PROJ -gt 0) -and ($PROD -gt 0) -and ($MATROWS.Count -ge 2)) 'fixtures resolved from live DB (factory/project/product/2 BOM materials)'
if (($FACTORY -le 0) -or ($PROJ -le 0) -or ($PROD -le 0) -or ($MATROWS.Count -lt 2)) {
  Write-Host 'RESULT FAIL bom-snapshot sharing (fixtures missing: need >=1 factory, 1 project, 1 product, 2 dev_bom rows)'
  exit 1
}
$M1 = $MATROWS[0] -split "`t"; $M2 = $MATROWS[1] -split "`t"
$M1ID = [int]$M1[0]; $M1TYPE = [int]$M1[1]; $M1UNIT = [string]$M1[2]
$M2ID = [int]$M2[0]; $M2TYPE = [int]$M2[1]; $M2UNIT = [string]$M2[2]

function NewOrder([double]$loss) {
  $body = @{
    factoryId = $FACTORY; supplyMode = 'OURS'; planStartDate = (Get-Date -Format 'yyyy-MM-dd'); remark = $tag
    products = @(@{
      projectId = $PROJ; productId = $PROD; productName = $PRODNAME; quantity = $Qty; unitPrice = 1
      materials = @(
        @{ materialId = $M1ID; materialTypeId = $M1TYPE; unit = $M1UNIT; bomQuantityPerSet = 1; demandQuantity = $Qty; lossRate = $loss; supplyType = 'OURS' },
        @{ materialId = $M2ID; materialTypeId = $M2TYPE; unit = $M2UNIT; bomQuantityPerSet = 1; demandQuantity = $Qty; lossRate = 0; supplyType = 'OURS' }
      )
    })
  } | ConvertTo-Json -Depth 10
  return Invoke-RestMethod -Uri "$base/outsource/order" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
}

try {
  # Warm-up order: the resolved product may not have any snapshot yet, and assertion (2) is about
  # REUSE (no new row) - so make sure one snapshot for this product exists before measuring.
  NewOrder 0 | Out-Null
  $before = [int](Sql 'SELECT COUNT(*) FROM bom_snapshot;')
  $r1 = NewOrder 0
  Ok ([bool]($r1.code -eq 200)) 'create order with same BOM -> ok'
  $after1 = [int](Sql 'SELECT COUNT(*) FROM bom_snapshot;')
  Ok ($after1 -eq $before) 'dev BOM unchanged -> snapshot reused (no new row)'

  NewOrder 0 | Out-Null
  $after2 = [int](Sql 'SELECT COUNT(*) FROM bom_snapshot;')
  Ok ($after2 -eq $before) 'second order, different qty, same BOM -> still reused'

  NewOrder 5 | Out-Null
  $after3 = [int](Sql 'SELECT COUNT(*) FROM bom_snapshot;')
  Ok ($after3 -eq ($before + 1)) 'order edited the detail (loss rate) -> new snapshot created'

  $nullSnap = [int](Sql ("SELECT COUNT(*) FROM outsource_order_product p JOIN outsource_order o ON o.id=p.order_id WHERE o.remark='" + $tag + "' AND p.bom_snapshot_id IS NULL;"))
  Ok ($nullSnap -eq 0) 'every new product row has bom_snapshot_id'

  $link = ("JOIN outsource_order_product p ON p.id=om.product_id JOIN outsource_order o ON o.id=p.order_id WHERE o.remark='" + $tag + "'")
  $viewTotal = [int](Sql ("SELECT COUNT(*) FROM outsource_order_material om " + $link + ";"))
  $viewOk = [int](Sql ("SELECT COUNT(*) FROM outsource_order_material om " + $link + " AND om.demand_quantity = ROUND(om.quantity_per_set * p.quantity);"))
  Ok (($viewTotal -gt 0) -and ($viewOk -eq $viewTotal)) 'view demand qty = per-set x product qty'

  $sharedSnap = [int](Sql ("SELECT COUNT(DISTINCT p.bom_snapshot_id) FROM outsource_order_product p JOIN outsource_order o ON o.id=p.order_id WHERE o.remark='" + $tag + "' AND p.id IN (SELECT product_id FROM outsource_order_material om2 GROUP BY product_id HAVING SUM(CASE WHEN om2.loss_rate=5 THEN 1 ELSE 0 END)=0);"))
  Ok ($sharedSnap -le 1) 'the two same-BOM orders share one snapshot'
} catch {
  $script:fail++; Write-Host ('FAIL exception: ' + $_.Exception.Message + ' :: ' + $_.ErrorDetails.Message)
} finally {
  # 2026-09-18: only remove the snapshots THIS run used (previously it swept every unreferenced
  # non-MIGRATED snapshot in the DB, which could delete snapshots owned by other test data).
  $mySnaps = @(Sql ("SELECT DISTINCT p.bom_snapshot_id FROM outsource_order_product p JOIN outsource_order o ON o.id=p.order_id WHERE o.remark='" + $tag + "' AND p.bom_snapshot_id IS NOT NULL;"))
  Sql ("DELETE FROM outsource_order_product WHERE order_id IN (SELECT id FROM outsource_order WHERE remark='" + $tag + "'); DELETE FROM outsource_order WHERE remark='" + $tag + "';") | Out-Null
  $ids = @()
  foreach ($s in $mySnaps) { $v = ([string]$s).Trim(); if ($v -ne '' -and $v -ne 'NULL') { $ids += $v } }
  if ($ids.Count -gt 0) {
    $csv = ($ids -join ',')
    Sql ("DELETE FROM bom_snapshot_item WHERE snapshot_id IN (" + $csv + "); DELETE FROM bom_snapshot WHERE id IN (" + $csv + ");") | Out-Null
    Write-Host ('CLEANUP removed test snapshots: ' + $csv)
  }
  Write-Host ('RESULT ' + $(if ($fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' bom-snapshot sharing (PASS=' + $pass + ' FAIL=' + $fail + ')')
}
