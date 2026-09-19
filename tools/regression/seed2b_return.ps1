# Seed 2b: purchase returns (ASCII only) - items via /api/inventory/purchase/{id}/items
$ErrorActionPreference = 'Continue'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { return $null }
}
function Put($path) {
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } catch { return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { return $null } }

$all = GetP '/api/inventory/purchase/page?pageSize=200'
$audited = @(@($all.records) | Where-Object { $_.status -eq 'AUDITED' })
Write-Output ('audited PO = ' + $audited.Count)
$ok = 0; $fail = 0; $msgs = @()
foreach ($p in ($audited | Select-Object -First 5)) {
  $base = '/api/inventory/purchase/' + $p.id
  $det = GetP $base
  $detItems = GetP ($base + '/items')
  if (-not $detItems -or $detItems.Count -eq 0) { $fail++; $msgs += ('no items PO ' + $p.id); continue }
  $items = @()
  $n = [math]::Min(2, @($detItems).Count)
  for ($j = 0; $j -lt $n; $j++) {
    $it = $detItems[$j]
    $qty = [math]::Max(1, [math]::Floor([decimal]$it.quantity / 10))
    $items += @{ productId = [int]$it.productId; qualityType = 'A'; quantity = $qty; unitPrice = $it.unitPrice; amount = [math]::Round($qty * [decimal]$it.unitPrice, 2) }
  }
  $pr = @{ supplierId = [int]$det.supplierId; warehouseId = [int]$det.warehouseId; purchaseOrderId = [int]$det.id; purchaseOrderCode = $det.code
           returnDate = '2026-08-20'; status = 'DRAFT'; remark = 'seed return'; items = $items }
  $r = Post '/api/inventory/purchase-return' $pr
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $prAll = GetP '/api/inventory/purchase-return/page?pageSize=200'
    $new = @(@($prAll.records) | Where-Object { $_.purchaseOrderId -eq $det.id -and $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = Put ('/api/inventory/purchase-return/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $ok++ } else { $fail++; $msgs += ('PR audit: ' + $ra.code + ' ' + $ra.msg) }
    } else { $fail++; $msgs += 'PR not found' }
  } else { $fail++; $msgs += ('PR create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('purchase returns ok=' + $ok + ' fail=' + $fail)
$msgs | Select-Object -First 8
