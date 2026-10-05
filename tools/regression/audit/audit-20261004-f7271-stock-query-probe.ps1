# audit 2026-10-04 batch2 - F7-271 verification: does the new frontend util's stock口径 match the DB?
# It reproduces exactly what utils/stock.ts does (GET /warehouse/stock/page + keep stockForm=MATERIAL rows
# + sum quantity) and compares it with a direct SQL sum of the same rows. READ ONLY. ASCII ONLY.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return "$($l[0])".Trim()
}
function Login([string]$u, [string]$p) {
  $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
  return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20
}
$la = Login 'lin' '123'
if ($null -eq $la -or [string]$la.code -ne '200') { Write-Output 'FAIL cannot login'; Write-Output 'RESULT F7-271-STOCK FAIL count 1'; exit 1 }
$tok = [string]$la.data.token

foreach ($c in @(@(131, 147, 'A'), @(133, 149, 'A'))) {
  $wh = $c[0]; $prod = $c[1]; $qt = $c[2]
  # (1) the util's口径 over HTTP
  $res = Invoke-RestMethod -Uri ("$api/warehouse/stock/page?warehouseId=$wh&productId=$prod&qualityType=$qt&pageSize=500") -Headers @{ Authorization = $tok } -TimeoutSec 20
  $rows = @($res.data.records)
  $apiSum = 0.0
  $apiMat = 0
  foreach ($r in $rows) {
    if ((-not $r.stockForm) -or ($r.stockForm -eq 'MATERIAL')) { $apiSum += [double]$r.quantity; $apiMat++ }
  }
  # (2) direct SQL over the same rows
  $sqlSum = [double](SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='$qt' AND (stock_form IS NULL OR stock_form='' OR stock_form='MATERIAL')"))
  $sqlAll = [double](SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='$qt'"))
  $forms = (SqlOne ("SELECT GROUP_CONCAT(DISTINCT IFNULL(stock_form,'(null)')) FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='$qt'"))
  Write-Output ("wh=$wh prod=$prod qt=$qt : api_rows=$($rows.Count) material_rows=$apiMat api_sum=$apiSum sql_material_sum=$sqlSum sql_all_forms=$sqlAll forms=[$forms]")
  if ([Math]::Abs($apiSum - $sqlSum) -lt 0.0001) { Ok ("util semantics == DB: MATERIAL sum $apiSum (wh=$wh prod=$prod qt=$qt)") }
  else { Bad ("semantics mismatch: api=$apiSum sql=$sqlSum (wh=$wh prod=$prod qt=$qt)") }
  if ($sqlAll -ne $sqlSum) { Write-Output ("  (note: other stock_form rows exist here => summing every form would over-count by " + ($sqlAll - $sqlSum) + ", which is why the MATERIAL filter matters)") }
}
if ($fail -eq 0) { Write-Output 'RESULT F7-271-STOCK PASS' } else { Write-Output ("RESULT F7-271-STOCK FAIL count " + $fail) }
exit $fail
