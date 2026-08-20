$ErrorActionPreference = 'Stop'
$body = '{"username":"lin","password":"123","companyId":1}'
$resp = Invoke-WebRequest -Uri http://localhost:8080/api/auth/login -Method POST -ContentType 'application/json; charset=utf-8' -Body $body -UseBasicParsing
$login = $resp.Content | ConvertFrom-Json
$token = if ($login.data.token) { $login.data.token } else { $login.token }

# zcX 是之前 curl 创建项目时生成的产品名（ASCII，无编码问题）
Write-Host '=== check-assembly zcX (expect exists:true) ==='
try {
    $g = Invoke-RestMethod -Uri 'http://localhost:8080/api/dev/project/check-assembly?name=zcX' -Method GET -Headers @{Authorization=$token}
    Write-Host "OK: $( $g | ConvertTo-Json -Compress )"
} catch {
    Write-Host "ERR: $( $_.ErrorDetails.Message ) HTTP:$( $_.Exception.Response.StatusCode.value__ )"
}
