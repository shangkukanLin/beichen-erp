# Seed 3c: return sort with targetWarehouse* fields (ASCII only)
$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { return $null }
}
function PutOp($path) { try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } catch { return $null } }
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { return $null } }

$afs = [int]$ids.whAfs[0]
$ds = GetP ('/api/inventory/return-sort/defect-stock?warehouseId=' + $afs)
$rows = @($ds)
$whFin = @($ids.whFin); $whDef = @($ids.whDef)
Write-Output ('pending rows=' + $rows.Count)
$ok = 0; $fail = 0; $msgs = @()
foreach ($row in $rows) {
  $total = [math]::Max(1, [math]::Floor([decimal]$row.quantity))
  $rs = @{ warehouseId = $afs; sortDate = '2026-08-26'; status = 'DRAFT'; remark = 'seed return sort'
           targetWarehouseA = [int]$whFin[0]; targetWarehouseB = [int]$whFin[0]; targetWarehouseC = [int]$whFin[0]; targetWarehouseDefect = [int]$whDef[0]
           items = @(@{ pendingId = [int]$row.pendingId; productId = [int]$row.productId
                        totalQuantity = $total; qtyA = $total; qtyB = 0; qtyC = 0; qtyDefect = 0 }) }
  $r = Post '/api/inventory/return-sort' $rs
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 300
    $allRs = GetP '/api/inventory/return-sort/page?pageSize=200'
    $new = @(@($allRs.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/inventory/return-sort/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $ok++ } else { $fail++; $msgs += ('RS audit ' + $new[0].id + ': ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $fail++; $msgs += ('RS create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('return sort ok=' + $ok + ' fail=' + $fail)
$msgs | Select-Object -First 6
