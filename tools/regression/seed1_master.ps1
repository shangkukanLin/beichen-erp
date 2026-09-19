# Seed 1b: collect master IDs (ASCII only; Chinese built from char codes)
$ErrorActionPreference = 'Continue'
function Cn([int[]]$codes) { return -join ($codes | ForEach-Object { [char]$_ }) }
$W_FIN = Cn @(0x6210,0x54C1,0x4ED3)
$W_DEF = Cn @(0x4E0D,0x826F,0x4ED3)
$W_AFS = Cn @(0x552E,0x540E,0x4ED3)
$W_AUX = Cn @(0x8F85,0x6599,0x4ED3)

$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 6
  try { $r = Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)); if ($r.code -and $r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg) }; return $r } catch { Write-Output ('ERR ' + $path + ' -> ' + $_.Exception.Message); return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { Write-Output ('GETERR ' + $path); return $null } }

# ensure 2 finished warehouses
$wh = (GetP '/api/warehouse/page?pageSize=300').records
$fin = @($wh | Where-Object { $_.warehouseType -eq $W_FIN })
if ($fin.Count -lt 2) { Post '/api/warehouse' @{ warehouseName = 'FIN-WH-2'; warehouseCategory = 'INVENTORY'; warehouseType = $W_FIN; status = 1 } | Out-Null }
$wh = (GetP '/api/warehouse/page?pageSize=300').records

$brandIds = @((GetP '/api/brand/page?pageSize=100').records | ForEach-Object { $_.id })
$custIds  = @((GetP '/api/inventory/customer/page?pageSize=100').records | ForEach-Object { $_.id })
$prodIds  = @((GetP '/api/product/page?pageSize=100').records | ForEach-Object { $_.id })
$matIds   = @((GetP '/api/outsource/material/page?pageSize=100').records | ForEach-Object { $_.id })
$supAll   = @((GetP '/api/supplier/page?pageSize=200').records)

$whFin = @(); $whDef = @(); $whAfs = @(); $whAux = @(); $whOut = @()
foreach ($w in $wh) {
  if ($w.warehouseCategory -eq 'OUTSOURCE') { $whOut += $w.id; continue }
  if ($w.warehouseType -eq $W_FIN) { $whFin += $w.id }
  elseif ($w.warehouseType -eq $W_DEF) { $whDef += $w.id }
  elseif ($w.warehouseType -eq $W_AFS) { $whAfs += $w.id }
  elseif ($w.warehouseType -eq $W_AUX) { $whAux += $w.id }
}
# supplier ids by name prefix (type stored in ref table, name is our marker)
$facIds = @($supAll | Where-Object { $_.name -like 'FAC-SUP-*' } | ForEach-Object { $_.id })
$proIds = @($supAll | Where-Object { $_.name -like 'PRO-SUP-*' } | ForEach-Object { $_.id })
$matSupIds = @($supAll | Where-Object { $_.name -like 'MAT-SUP-*' } | ForEach-Object { $_.id })
$solIds = @($supAll | Where-Object { $_.name -like 'SOL-SUP-*' } | ForEach-Object { $_.id })

Write-Output ('brand=' + $brandIds.Count + ' cust=' + $custIds.Count + ' prod=' + $prodIds.Count + ' mat=' + $matIds.Count)
Write-Output ('sup fac=' + $facIds.Count + ' pro=' + $proIds.Count + ' mat=' + $matSupIds.Count + ' sol=' + $solIds.Count)
Write-Output ('wh fin=' + $whFin.Count + ' def=' + $whDef.Count + ' afs=' + $whAfs.Count + ' aux=' + $whAux.Count + ' out=' + $whOut.Count)

$out = @{ brandIds = $brandIds; custIds = $custIds; prodIds = $prodIds; matIds = $matIds
          facIds = $facIds; proSupIds = $proIds; matSupIds = $matSupIds; solIds = $solIds
          whFin = $whFin; whDef = $whDef; whAfs = $whAfs; whAux = $whAux; whOut = $whOut }
$out | ConvertTo-Json -Depth 5 | Out-File -FilePath 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Encoding utf8
Write-Output 'saved seed_ids.json'
