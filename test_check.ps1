$ErrorActionPreference = 'Stop'
$up = $false
for ($i = 0; $i -lt 12; $i++) {
    try {
        $r = Invoke-WebRequest -Uri http://localhost:8080/api/auth/captcha -Method GET -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch {}
    Start-Sleep -Seconds 3
}
Write-Host "backend_up=$up"
if (-not $up) { Write-Host 'BACKEND NOT UP'; exit }

$body = '{"username":"lin","password":"123","companyId":1}'
$resp = Invoke-WebRequest -Uri http://localhost:8080/api/auth/login -Method POST -ContentType 'application/json; charset=utf-8' -Body $body -UseBasicParsing
$login = $resp.Content | ConvertFrom-Json
$token = if ($login.data.token) { $login.data.token } else { $login.token }
Write-Host "TOKEN_LEN=$($token.Length)"

Write-Host '=== GET /check-assembly?name=总成X ==='
try {
    $g = Invoke-RestMethod -Uri 'http://localhost:8080/api/dev/project/check-assembly?name=%E6%80%BB%E6%88%90X' -Method GET -Headers @{Authorization=$token}
    Write-Host "OK: $( $g | ConvertTo-Json -Compress )"
} catch {
    Write-Host "ERR: $( $_.ErrorDetails.Message ) HTTP:$( $_.Exception.Response.StatusCode.value__ )"
}

Write-Host '=== GET /check-assembly?name=不存在的总成 ==='
try {
    $g2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/dev/project/check-assembly?name=%E4%B8%8D%E5%AD%98%E5%9C%A8%E7%9A%84%E6%80%BB%E6%88%90' -Method GET -Headers @{Authorization=$token}
    Write-Host "OK: $( $g2 | ConvertTo-Json -Compress )"
} catch {
    Write-Host "ERR: $( $_.ErrorDetails.Message ) HTTP:$( $_.Exception.Response.StatusCode.value__ )"
}
