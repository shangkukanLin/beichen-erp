# 首页「销售业务」TAB —— 当日 4 卡 校验：接口值 vs SQL 直查（2026-09-15）
# 口径（2026-09-15 全站统一）：当日 = **建单日 create_time = CURDATE()** 且 status <> 'CANCELLED'
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-today.ps1
$ErrorActionPreference = 'Continue'

# 1) 接口值
$base = 'http://localhost:8080/api'
$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$t = (Invoke-RestMethod -Uri "$base/dashboard/sale-workbench" -Headers $h).data.todos
Write-Output ('接口 /dashboard/sale-workbench todos = ' + ($t | ConvertTo-Json -Compress))

# 2) SQL 直查（口径：建单日 create_time = CURDATE() 且 status <> 'CANCELLED'）
$sql = "SELECT 'sale_order' AS t, COUNT(*) AS c FROM sale_order WHERE DATE(create_time) = CURDATE() AND status <> 'CANCELLED' UNION ALL SELECT 'sale_return', COUNT(*) FROM sale_return WHERE DATE(create_time) = CURDATE() AND status <> 'CANCELLED' UNION ALL SELECT 'sale_exchange', COUNT(*) FROM sale_exchange WHERE DATE(create_time) = CURDATE() AND status <> 'CANCELLED' UNION ALL SELECT 'return_sort', COUNT(*) FROM return_sort WHERE DATE(create_time) = CURDATE() AND status <> 'CANCELLED';"
$out = (& powershell -NoProfile -ExecutionPolicy Bypass -File .\q.ps1 -Sql $sql) -join "`n"
$map = @{}
foreach ($ln in ($out -split "`r?`n")) {
  if ($ln -match '^\s*(\S+)\s*\t\s*(-?\d+)\s*$') { $map[$Matches[1]] = [int]$Matches[2] }
}
Write-Output ('SQL  当日计数 = ' + (($map.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '))

# 3) 逐项比对
$pairs = @(
  @{ api = 'saleOrderToday';    db = 'sale_order' },
  @{ api = 'saleReturnToday';   db = 'sale_return' },
  @{ api = 'saleExchangeToday'; db = 'sale_exchange' },
  @{ api = 'returnSortToday';   db = 'return_sort' }
)
$fail = 0
foreach ($p in $pairs) {
  $a = [int]($t.($p.api))
  $b = $map[$p.db]
  if ($a -eq $b) { Write-Output ("PASS {0}：接口 {1} = SQL {2}" -f $p.api, $a, $b) }
  else { Write-Output ("FAIL {0}：接口 {1} ≠ SQL {2}" -f $p.api, $a, $b); $fail++ }
}
if ($t.PSObject.Properties.Name -contains 'outboundDraft') { Write-Output 'FAIL todos 不应再有 outboundDraft'; $fail++ }
if ($t.PSObject.Properties.Name -contains 'auditedNotOutbound') { Write-Output 'FAIL todos 不应再有 auditedNotOutbound'; $fail++ }
Write-Output ('FIELD COUNT = ' + $t.PSObject.Properties.Name.Count + '（应为 4）')
if ($fail -eq 0) { Write-Output 'RESULT PASS 当日 4 卡与 SQL 完全一致' } else { Write-Output "RESULT FAIL 不一致项 $fail" ; exit 1 }
