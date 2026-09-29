# F7-255 verification probe (2026-09-30, audit batch G fix): analysis endpoints is now code-gated.
# READ ONLY (logins + GETs + SELECTs). Prints the before/after matrix for two accounts.
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$base = 'http://localhost:8080/api'
$db = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'

function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Q([string]$sql) {
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Write-Host ('    ' + $l) }
  }
}
function Lg($u) {
  try { return (Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' `
      -Body ('{"username":"' + $u + '","password":"123","companyId":1}')).data.token } catch { return $null }
}
function G($tok, $path) {
  try { $r = Invoke-RestMethod -Uri ($base + $path) -Headers @{ Authorization = $tok }; return ('code=' + $r.code) }
  catch { return ('HTTP ' + $_.Exception.Response.StatusCode.value__) }
}

Write-Host '=== F7-255 verification: analysis endpoints permission gate (read only) ==='

Sec '1) menus that should carry the analysis:* codes (live DB truth)'
Q "SELECT id, IFNULL(menu_name,'-'), IFNULL(route_path,'-'), IFNULL(route_name,'-'), IFNULL(perms,'<NULL>') FROM sys_menu WHERE route_path LIKE '%analysis%' OR route_name LIKE '%Analysis%' OR id BETWEEN 1000 AND 1010 ORDER BY id"
Q "SELECT perms, COUNT(*) FROM sys_menu WHERE perms LIKE '%analysis%' GROUP BY perms"

Sec '2) who calls /finance/analysis/profit (the endpoint the self-check flagged as unregistered)'
& powershell -NoProfile -Command "Select-String -Path '$db\beichen-erp-web\src\views\**\*.vue' -Pattern 'analysis/profit' | ForEach-Object { Write-Host ('    ' + (\$_.Path -replace '.*views\\\\','') + ':' + \$_.LineNumber) }"

Sec '3) API matrix (admin vs low-privilege account)'
$admin = Lg 'lin'
$low = Lg 'perm_test'
Write-Host ('    admin token=' + [bool]$admin + '  low token=' + [bool]$low)
foreach ($path in @('/finance/analysis/summary', '/finance/analysis/profit', '/finance/analysis/aging', '/finance/analysis/tax', '/sale/analysis', '/customer/analysis', '/finance/bill/page?pageNum=1&pageSize=1')) {
  Write-Host ('    admin ' + $path + ' => ' + (G $admin $path))
  if ($low) { Write-Host ('    low   ' + $path + ' => ' + (G $low $path)) }
}

Sec '4) F7-256 cross-check: aging buckets are now positive-row only (expect d30 = 2506)'
$r = Invoke-RestMethod -Uri ($base + '/finance/analysis/aging') -Headers @{ Authorization = $admin }
Write-Host ('    API  aging.receivable = ' + ($r.data.receivable | ConvertTo-Json -Compress))
Q "SELECT SUM(CASE WHEN IFNULL(due_date,CURDATE())>=CURDATE() THEN unpaid_amount ELSE 0 END) not_due_pos_only, SUM(CASE WHEN IFNULL(due_date,CURDATE())<CURDATE() THEN unpaid_amount ELSE 0 END) overdue_pos_only FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(amount,0)>0"

Write-Host ''
Write-Host 'DONE (no writes performed).'
