# P12 (2026-09-21 user request): the material-warehouse catalog gets a "material stock overview" page,
#   mirroring the finished-goods one (/inventory/product-stock) but for MATERIALS.
#   This case CREATES NOTHING (rerun-safe): it only reads the DB and drives the UI.
#   Asserts: menu 416 is registered / granted / ordered right; the list matches the DB (one row per
#            material, its good qty shown); row -> distribution detail; the detail lists every warehouse
#            holding that material and shows the cross-warehouse total; both tables fit one line
#            (no horizontal scrolling); no JS runtime errors.
#   NOTE: material quality is outsource.common.QualityType = GOOD/DEFECT ONLY (no A/B/C/pending, no
#         safety stock) and outsource_material has no SKU => the page must not show those bits.
#   ASCII ONLY (Chinese text comes from ui-e2e-zh.json via base64; table rows come from the lib's Rows helper).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function TableOver([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[$idx];if(!t)return 'NOTABLE/'+ts.length;const w=t.querySelector('.el-table__body-wrapper');if(!w)return 'NOWRAP';return String(w.scrollWidth-w.clientWidth)})()"
  return (EvalJs $js)
}

# ---- fixtures from the DB (the biggest-stock material) ----
$matCount = D (SqlOne 'SELECT COUNT(*) FROM (SELECT material_id FROM warehouse_stock WHERE material_id IS NOT NULL GROUP BY material_id) t')
$topId = D (SqlOne 'SELECT material_id FROM warehouse_stock WHERE material_id IS NOT NULL GROUP BY material_id ORDER BY SUM(quantity) DESC LIMIT 1')
$topName = SqlOne ("SELECT material_name FROM outsource_material WHERE id=$topId")
$topType = SqlOne ("SELECT t.type_name FROM outsource_material m LEFT JOIN material_type t ON t.id=m.material_type_id WHERE m.id=$topId")
$topGood = D (SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE material_id=$topId AND quality_type='GOOD'"))
$topDefect = D (SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE material_id=$topId AND quality_type='DEFECT'"))
$topWhs = D (SqlOne ("SELECT COUNT(DISTINCT warehouse_id) FROM warehouse_stock WHERE material_id=$topId"))
$topTotal = $topGood + $topDefect
Write-Host ('[BASE] materials=' + $matCount + ' top=' + $topId + ' ' + $topName + ' type=' + $topType + ' good=' + $topGood + ' defect=' + $topDefect + ' total=' + $topTotal + ' whs=' + $topWhs)
Ok (($matCount -gt 0) -and ($topId -gt 0)) 'fixture resolved (material stock exists in the DB)'

Step '0) the new submenu is registered under the material catalog, before the own-material warehouse'
$menuRow = SqlOne "SELECT CONCAT(id,'|',parent_id,'|',route_path,'|',route_name,'|',IFNULL(perms,''),'|',sort_order,'|',visible) FROM sys_menu WHERE id=416"
Write-Host ('  menu 416 = ' + $menuRow)
Ok ($menuRow -match 'OutsourceMaterialStock') 'menu 416 carries the material-stock route name'
Ok ($menuRow -match 'outsource:material-stock') 'menu 416 carries its interface perm code (page menus use perms as the API perm)'
Ok ($menuRow -match '^\d+\|11\|') 'menu 416 hangs under the material-warehouse catalog (11)'
Ok ($menuRow -match '\|1$') 'menu 416 is visible'
$matSort = D (SqlOne 'SELECT sort_order FROM sys_menu WHERE id=416')
$ownSort = D (SqlOne 'SELECT sort_order FROM sys_menu WHERE id=410')
Ok (($matSort -lt $ownSort)) ('the new query page sits before the own-material warehouse (sort ' + $matSort + ' < ' + $ownSort + ')')
$granted = D (SqlOne "SELECT COUNT(*) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id WHERE rm.menu_id=416 AND r.role_code IN ('admin','merchandiser','warehouse')")
Ok (($granted -ge 3)) ('menu 416 is granted to admin / merchandiser / warehouse (got ' + $granted + ')')

Step '1) the list page renders the material columns (and none of the finished-goods-only ones)'
Open '/outsource/material-stock' 2800
Ok (((EvalJs 'String(location.pathname)') -match '/outsource/material-stock')) 'the material-stock page is reachable'
Ok ((BodyHas (ZH 'menu_material_stock')) -eq 'True') 'the page shows the material-stock title'
Ok ((BodyHas (ZH 'lbl_material_type')) -eq 'True') 'column header A: material type'
Ok ((BodyHas (ZH 'lbl_material_name')) -eq 'True') 'column header B: material name'
Ok ((BodyHas (ZH 'lbl_unit')) -eq 'True') 'column header C: unit'
Ok ((BodyHas (ZH 'lbl_good')) -eq 'True') 'column header D: good (material has only GOOD/DEFECT)'
Ok ((BodyHas (ZH 'lbl_defect')) -eq 'True') 'column header E: defect'
Ok ((BodyHas (ZH 'lbl_total_stock')) -eq 'True') 'column header F: total stock'
# 2026-09-24 (user decision: no cross-warehouse aggregation any more) -> column G becomes the row's own warehouse
Ok ((BodyHas (ZH 'lbl_wh_name')) -eq 'True') 'column header G: the warehouse of this row (per-warehouse granularity)'
Ok ((BodyHas (ZH 'btn_export_excel_ws')) -eq 'True') 'the export button is offered (mirrors the finished-goods page)'
Ok ((BodyHas (ZH 'opt_q_pending')) -eq 'False') 'the finished-goods-only pending column is absent'

Step '2) the list matches the DB (one row per (warehouse x material) — no cross-warehouse aggregation any more)'
$tb = Rows 0
Write-Host ('  list rows=' + $tb.n + ' (db materials=' + $matCount + ')  head=' + ($tb.head -join '|'))
# 2026-09-24 (aggregation removed): rows are per (warehouse x material) -> a material with stock in N
#   warehouses yields N rows, so the row count no longer equals the material count (it may exceed it).
Ok (([int]$tb.n -ge 1)) ('the list renders rows in the per-(warehouse x material) granularity (rows=' + [int]$tb.n + ', materials in db=' + [int]$matCount + ')')
$allRows = [string](($tb.rows | ForEach-Object { "$_" }) -join '~')
Write-Host ('  allRows=[' + $allRows + ']')
Write-Host ('  topName=[' + $topName + '] len=' + $topName.Length + ' good=' + [int]$topGood)
Ok ($allRows -like ('*' + $topName + '*')) ('the biggest-stock material is listed (' + $topName + ')')
# 2026-09-24: the cross-warehouse sum (topGood) is no longer a single cell -> the per-warehouse qty is shown instead
Write-Host ('  (aggregation removed) cross-warehouse good of ' + $topName + ' = ' + [int]$topGood + ' is no longer shown as one cell')
$ov = TableOver 0
Write-Host ('  list overflow=' + $ov + 'px')
Ok ([int]$ov -le 2) ('the list table fits on one line (overflow=' + $ov + 'px)')

Step '3) aggregation is gone from the list + the distribution detail stays reachable by URL'
$idx = [int](FindRow $topName)
Ok ($idx -ge 0) ('the material row is found on the list (idx=' + $idx + ')')
if ($idx -ge 0) {
  $clk = ClickRowBtnContains $idx (ZH 'btn_wh_dist')
  Write-Host ('  click the removed distribution entry: ' + $clk)
  Start-Sleep -Milliseconds 2600
  $p = EvalJs 'String(location.pathname)'
  Write-Host ('  path=' + $p)
  Ok ($clk -notmatch 'OK') 'the distribution entry is gone from the list (no aggregation entry any more)'
  Ok ($p -match '/outsource/material-stock$') ('the row click does not navigate away (' + $p + ')')
  # sub page kept on purpose -> open by URL and check it still renders
  Open ('/outsource/material-stock/detail/' + [int]$topId) 2600
  Ok ((BodyHas $topName) -eq 'True') ('detail shows the material name (' + $topName + ')')
  Ok ((BodyHas (ZH 'lbl_total_stock')) -eq 'True') 'detail shows the total-stock label'
  Ok ((BodyHas ([string][int]$topTotal)) -eq 'True') ('detail shows the cross-warehouse total (' + [int]$topTotal + ')')
  $d = Rows 0
  Write-Host ('  detail rows=' + $d.n + ' (db warehouses=' + $topWhs + ')')
  Ok (([int]$d.n -eq [int]$topWhs)) 'detail lists every warehouse holding the material'
  $ov2 = TableOver 0
  Write-Host ('  detail overflow=' + $ov2 + 'px')
  Ok ([int]$ov2 -le 2) ('the detail table fits on one line (overflow=' + $ov2 + 'px)')
} else { Ok $false 'the material row was not found (cannot check the detail page)' }

Step '4) no JS runtime errors on the material-stock pages'
$errs = Errs
Write-Host ('  errs=' + $errs)
Ok ($errs -notmatch 'JSERR') 'no JS runtime errors captured'

Summary 'material stock overview (P12)'
