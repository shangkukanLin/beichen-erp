# Final verify: counts per module + finance summary (ASCII only)
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function GetRaw($path) {
    try {
        $resp = Invoke-RestMethod -Uri ($B + $path) -Headers $H
        if ($resp -is [string]) { return (($resp | ConvertFrom-Json).data) }
        return $resp.data
    } catch { Write-Output ('ERR ' + $path); return $null }
}
function Count($path) {
    $d = GetRaw $path
    if ($null -eq $d) { return 'ERR' }
    return @($d.records).Count
}

Write-Output ('brands        = ' + (Count '/brand/page?pageSize=200'))
Write-Output ('customers     = ' + (Count '/inventory/customer/page?pageSize=200'))
Write-Output ('suppliers     = ' + (Count '/supplier/page?pageSize=200'))
Write-Output ('products      = ' + (Count '/product/page?pageSize=200'))
Write-Output ('materials     = ' + (Count '/outsource/material/page?pageSize=200'))
Write-Output ('projects      = ' + (Count '/dev/project/page?pageSize=200'))
Write-Output ('warehouses    = ' + (Count '/warehouse/page?pageSize=200'))
Write-Output ('purchase      = ' + (Count '/inventory/purchase/page?pageSize=200'))
Write-Output ('purchase-ret  = ' + (Count '/inventory/purchase-return/page?pageSize=200'))
Write-Output ('sale orders   = ' + (Count '/inventory/sale/page?pageSize=200'))
Write-Output ('sale outbound = ' + (Count '/inventory/outbound/page?pageSize=200'))
Write-Output ('sale return   = ' + (Count '/sale/return/page?pageSize=200'))
Write-Output ('exchange      = ' + (Count '/sale/exchange/page?pageSize=200'))
Write-Output ('return-sort   = ' + (Count '/inventory/return-sort/page?pageSize=200'))
Write-Output ('outsource ord = ' + (Count '/outsource/order/page?pageSize=200'))
Write-Output ('mat orders    = ' + (Count '/outsource/material-order/page?pageSize=200'))
Write-Output ('deliveries    = ' + (Count '/outsource/delivery/page?pageSize=200'))
Write-Output ('other-io      = ' + (Count '/outsource/other-io/page?pageSize=200'))
Write-Output ('stock take    = ' + (Count '/inventory/stock-take/page?pageSize=200'))
Write-Output ('accounts      = ' + (Count '/finance/account/page?pageSize=200'))
Write-Output ('expenses      = ' + (Count '/finance/expense/page?pageSize=200'))
Write-Output ('invoices      = ' + (Count '/finance/invoice/page?pageSize=200'))
Write-Output ('receipts      = ' + (Count '/finance/receipt/page?pageSize=200'))
Write-Output ('payments      = ' + (Count '/finance/payment/page?pageSize=200'))

$s = GetRaw '/finance/analysis/summary'
if ($s) {
    Write-Output ('--- finance summary (this month / last month) ---')
    Write-Output ('revenue    = ' + $s.cur.revenue + ' / ' + $s.prev.revenue)
    Write-Output ('cost       = ' + $s.cur.cost + ' / ' + $s.prev.cost)
    Write-Output ('expense    = ' + $s.cur.expense + ' / ' + $s.prev.expense)
    Write-Output ('netProfit  = ' + $s.cur.netProfit + ' / ' + $s.prev.netProfit)
    Write-Output ('ytd: revenue=' + $s.ytd.revenue + ' expense=' + $s.ytd.expense + ' netProfit=' + $s.ytd.netProfit)
} else { Write-Output 'summary ERR' }
