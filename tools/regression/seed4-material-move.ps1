# seed4-material-move.ps1 -- replacement seeding for the retired seed4* scripts.
#
# Why: the retired seed4_outsource / seed4b / seed4d_fix seeded "issued material" data through the
# manual material-delivery doc (POST /api/outsource/delivery), which was removed on 2026-09-24.
# The replacement doc is the material warehouse-move (/api/inventory/material-move).
#
# What it does: creates N AUDITED material warehouse-moves, source = an own material warehouse
# (warehouse_type='AUXILIARY') that actually holds the material, target = an outsourcing warehouse
# (warehouse_category='OUTSOURCE') when one exists, otherwise any other warehouse. This reproduces the
# "material moved out of our warehouse" stock + log shapes downstream suites may rely on.
#
# ASCII ONLY (Chinese output makes `powershell -File` mis-parse the file). Rerunnable.
param([int]$Count = 2, [decimal]$QtyEach = 5)

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
function Api([string]$method, [string]$path, $obj, [hashtable]$H) {
  try {
    $a = @{ Uri = ($B + $path); Method = $method; TimeoutSec = 25 }
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

$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json' -Body (@{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
Ok ([int]$login.code -eq 200) ('login code=' + $login.code)
$H = @{ Authorization = [string]$login.data.token }

# source: own material warehouse holding the material; target: outsourcing warehouse when available
$fromWh = SqlOne "SELECT s.warehouse_id FROM warehouse_stock s JOIN warehouse w ON w.id = s.warehouse_id WHERE w.warehouse_type = 'AUXILIARY' AND s.material_id IS NOT NULL AND s.quantity > 30 ORDER BY s.quantity DESC LIMIT 1"
$toWh = SqlOne "SELECT id FROM warehouse WHERE warehouse_category = 'OUTSOURCE' AND id <> $fromWh ORDER BY id LIMIT 1"
if ($toWh -eq '') { $toWh = SqlOne "SELECT id FROM warehouse WHERE id <> $fromWh ORDER BY id LIMIT 1" }
$mat = SqlOne "SELECT material_id FROM warehouse_stock WHERE warehouse_id = $fromWh AND material_id IS NOT NULL ORDER BY quantity DESC LIMIT 1"
Ok (([int]$fromWh -gt 0) -and ([int]$toWh -gt 0) -and ([int]$mat -gt 0)) ("fixture from=$fromWh to=$toWh material=$mat qtyEach=$QtyEach")

$today = (Get-Date -Format 'yyyy-MM-dd')
$made = 0
for ($i = 1; $i -le $Count; $i++) {
  $c = Api 'Post' '/inventory/material-move' @{ moveDate = $today; fromWarehouseId = [long]$fromWh; toWarehouseId = [long]$toWh; remark = 'seed4-material-move'; items = @(@{ materialId = [long]$mat; qualityType = 'A'; quantity = $QtyEach }) } $H
  if ([int]$c.code -ne 200) { Write-Output ('FAIL create #' + $i + ' code=' + $c.code + ' msg=' + $c.msg); $script:fail++; continue }
  $id = SqlOne "SELECT id FROM inventory_material_move ORDER BY id DESC LIMIT 1"
  $a = Api 'Put' "/inventory/material-move/$id/audit" @{} $H
  $code = SqlOne "SELECT code FROM inventory_material_move WHERE id=$id"
  $st = SqlOne "SELECT status FROM inventory_material_move WHERE id=$id"
  if (([int]$a.code -eq 200) -and ($st -eq 'AUDITED')) { $made++; Write-Output ('  seeded ' + $code + ' (id=' + $id + ') AUDITED') }
  else { Write-Output ('FAIL audit #' + $i + ' code=' + $a.code + ' status=' + $st); $script:fail++ }
}
Ok ($made -eq $Count) ("seeded $made/$Count audited material moves")

if ($script:fail -eq 0) { Write-Output 'RESULT PASS seed4-material-move' }
else { Write-Output ('RESULT FAIL items ' + $script:fail); exit 1 }
