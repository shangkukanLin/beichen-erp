# audit 2026-10-05 batch 6 - F7-284 verification (READ ONLY, no writes).
# Every finance/ledger LIST page that was just wired to the invalidation bus must still render and be
# free of JS errors. (The bus itself is code-verified: useDomainRefresh(domain, loader) replaces the old
# onMounted loader; the version-key / invalidation mechanics are exercised by the pages themselves.)
# ASCII ONLY in every printed string.
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'ui-e2e-lib.ps1')
Write-Host 'STEP 0: lib loaded; ensuring login...'
EnsureLogin

$pages = @(
  @('/finance/receivable', 'receivable'),
  @('/finance/payable', 'payable'),
  @('/finance/expense', 'expense'),
  @('/finance/cashflow', 'cashflow'),
  @('/finance/invoice', 'invoice'),
  @('/finance/account', 'account'),
  @('/finance/receipt', 'receipt'),
  @('/finance/payment', 'payment'),
  @('/finance/payable/supplier/1', 'payable-supplier'),
  @('/finance/receivable/customer/1', 'receivable-customer'),
  @('/finance/supplier-settlement/1', 'supplier-settlement'),
  # F7-287: the three pages whose duplicate onMounted was removed (they must still render fine)
  @('/finance/bill', 'bill'),
  @('/finance/payable-transfer', 'payable-transfer'),
  @('/finance/bill/detail/198', 'bill-detail')
)
$bad = 0
foreach ($p in $pages) {
  $path = $p[0]; $name = $p[1]
  ClearErrs | Out-Null
  Open $path 2400
  Start-Sleep -Milliseconds 1100
  $cur = (EvalJs 'String(location.pathname)').Trim()
  $tables = (EvalJs "(()=>{return document.querySelectorAll('.el-table').length})()").Trim()
  $errs = (Errs)
  $okPath = $cur.StartsWith(($path -replace '/1$', ''))
  $okRender = ([int]$tables -ge 1)
  $okErr = ($errs -eq '[]')
  Write-Host ('  ' + $name.PadRight(20) + ' path=' + $cur + ' tables=' + $tables + ' errors=' + $errs)
  Ok ($okPath -and $okRender -and $okErr) ($name + ' renders with a table and no JS errors')
  if (-not ($okPath -and $okRender -and $okErr)) { $bad++ }
}
Summary ('audit F7-284 finance list pages wired to the bus (' + $pages.Count + ' pages, ' + $bad + ' bad)')
