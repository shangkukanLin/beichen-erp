$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
  Stop-Process -Id $conn.OwningProcess -Force
  Write-Output ("killed " + $conn.OwningProcess)
} else {
  Write-Output "8080 not listening"
}
