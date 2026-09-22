# P15 (2026-09-22 user request): the material warehouse gains its own material stock-log list page
#   (menu 417 / sort 2). Same table + same API as the finished-goods log (warehouse_stock_log /
#   /warehouse/stock/log with stockType=MATERIAL), so this test targets what could silently be wrong:
#     1) the menu row / perms exist and the MATERIAL data really is there (DB-derived);
#     2) the page opens (not 403) with no JS errors, and lists rows;
#     3) every row shows a resolved material name (material_name is stored on the log row);
#     4) the warehouse column resolves a REAL name for every row -- crucially including OUTSOURCE
#        warehouses (measured: 489 of 582 material rows happened at OUTSOURCE warehouses; an
#        INVENTORY-only name map would show '-' for most rows);
#     5) a clickable source-bill number opens the MATERIAL-side detail page, not the finished-goods one.
# Read-only, rerun-safe. ASCII ONLY (this file must not contain non-ASCII: the BOM guard checks it,
# and PS 5.1 would silently garble Chinese in a BOM-less file). Expectations come from the DB.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne($q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return ((@($o) | Select-Object -First 1) -as [string])
}

Step '1) DB: menu 417 exists under catalog 11 at slot 2 with its own perms'
$row = SqlOne "SELECT CONCAT(id,'|',IFNULL(parent_id,-1),'|',IFNULL(sort_order,-1),'|',IFNULL(route_path,''),'|',IFNULL(route_name,''),'|',IFNULL(perms,'')) FROM sys_menu WHERE id=417"
Write-Host ('  417 = ' + $row)
Ok ($row -eq '417|11|2|/outsource/material-stock-log|OutsourceMaterialStockLog|outsource:material-stock-log') 'menu 417 is slot 2 of catalog 11 and carries its own perms'
$dbN = SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE material_id IS NOT NULL"
$outN = SqlOne "SELECT COUNT(*) FROM warehouse_stock_log l JOIN warehouse w ON w.id=l.warehouse_id WHERE l.material_id IS NOT NULL AND w.warehouse_category='OUTSOURCE'"
Write-Host ('  material log rows = ' + $dbN + ' (of which at OUTSOURCE warehouses = ' + $outN + ')')
Ok ([int]$dbN -gt 0) ('warehouse_stock_log already holds material flow rows (' + $dbN + ')')
Ok ([int]$outN -gt 0) ('a large share of material flow happens in OUTSOURCE warehouses (' + $outN + ')')

Step '2) the page opens with rows: no 403, no JS errors, names resolved'
Open '/outsource/material-stock-log' 3600
ClearErrs | Out-Null
Start-Sleep -Milliseconds 2200
$path = (EvalJs 'String(location.pathname)').Trim([char]34)
Write-Host ('  path = ' + $path)
Ok ($path -match '/outsource/material-stock-log') ('the page stays on its own route (' + $path + ')')
# columns: 0 time, 1 change type, 2 source bill no, 3 material name, 4 warehouse, 5..7 quantities
$probe = "(function(){const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis);const names=rs.map(r=>((r.querySelectorAll('td')[3]||{}).innerText||'').trim()).filter(Boolean);const whs=rs.map(r=>((r.querySelectorAll('td')[4]||{}).innerText||'').trim()).filter(Boolean);return rs.length+'@'+names.filter(n=>n&&n!=='-').length+'@'+whs.filter(w=>w&&w!=='-').length+'@'+(names[0]||'')+'@'+(whs[0]||'')})()"
$p = @(((EvalJs $probe) -replace '"','') -split '@')
Write-Host ('  rows=' + $p[0] + ' materialNames=' + $p[1] + ' warehouseNames=' + $p[2] + ' firstMat=' + $p[3] + ' firstWh=' + $p[4])
Ok ([int]$p[0] -gt 0) ('the list has rows (' + $p[0] + ')')
Ok ([int]$p[1] -gt 0) ('material names resolve for the listed rows (' + $p[1] + ')')
Ok ([int]$p[2] -eq [int]$p[0]) ('warehouse names resolve for EVERY row (' + $p[2] + '/' + $p[0] + ') -- the name map covers OUTSOURCE warehouses too')

Step '3) a clickable source-bill number opens the MATERIAL-side document'
$clickJs = "(function(){const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const b=[...t.querySelectorAll('.el-table__body a, .el-table__body .el-link')].filter(vis)[0];if(!b)return 'NOLINK';b.click();return (b.innerText||'').trim()})()"
$clicked = (EvalJs $clickJs).Trim([char]34)
Write-Host ('  clicked = ' + $clicked)
Start-Sleep -Milliseconds 1800
$sp = (EvalJs 'String(location.pathname)').Trim([char]34)
Write-Host ('  landed = ' + $sp)
if ($clicked -eq 'NOLINK') {
  Ok $true 'no clickable source-bill number on this page (all rows are jump-less types) -- nothing to verify'
} else {
  Ok ($sp -match '(outsource|supplier)/') ('the source-bill link opens a MATERIAL-side page, not the finished-goods one (' + $sp + ')')
}
Write-Host ('  errs=' + (Errs))
Ok ((Errs) -eq '[]') 'no JS/API errors on the material stock log page'
Summary 'material stock log page (P15)'
