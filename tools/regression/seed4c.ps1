# Seed 4c: verify delivery OUTSOURCE->OUTSOURCE works (workaround) (ASCII only)
$ErrorActionPreference = 'Continue'
$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 10
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { return $null }
}
function PutOp($path) { try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } catch { return $null } }
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { return $null } }

$matIds = @($ids.matIds); $whOut = @($ids.whOut)
Write-Output ('outsource warehouses=' + ($whOut -join ','))
$allOo = GetP '/api/outsource/order/page?pageSize=200'
$orders = @(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' } | Select-Object -First 3)
$ok = 0; $fail = 0; $msgs = @()
foreach ($o in $orders) {
  $whs = GetP ('/api/outsource/delivery/warehouses/by-factory/' + $o.factoryId)
  $target = if ($whs -and $whs.Count) { [int]$whs[0].id } else { $null }
  if (-not $target) { $fail++; continue }
  $src = $null
  foreach ($w in $whOut) { if ([int]$w -ne $target) { $src = [int]$w; break } }
  if (-not $src) { $fail++; continue }
  $dl = @{ deliveryType = 'DELIVERY'; factoryId = [int]$o.factoryId; fromWarehouseId = $src; toWarehouseId = $target
           deliveryDate = '2026-08-10'; status = 'DRAFT'; remark = 'outsource->outsource delivery'
           items = @(@{ materialId = [int]$matIds[0]; unit = 'PCS'; quantity = 20; qualityType = 'GOOD'; unit_price = 30 }) }
  $r = Post '/api/outsource/delivery' $dl
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 300
    $all = GetP '/api/outsource/delivery/page?pageSize=200'
    $new = @(@($all.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/outsource/delivery/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $ok++ } else { $fail++; $msgs += ('audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $fail++; $msgs += ('create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('OUTSOURCE->OUTSOURCE delivery ok=' + $ok + ' fail=' + $fail)
$msgs | Select-Object -First 5
