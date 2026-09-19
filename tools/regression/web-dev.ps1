# 启动前端 dev server（vite，5173）并等待就绪；同时检查后端 8080
# 单次调用：内部完成重定向与等待
$root = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web'
$log = Join-Path $root 'web_dev.log'

function PortUp([string]$port) {
  $l = netstat -ano | Select-String ":$port\s" | Select-String 'LISTENING' | Select-Object -First 1
  return [bool]$l
}

if (-not (PortUp '8080')) { Write-Output 'FAIL 后端 8080 未监听，请先启动后端' ; exit 1 }
Write-Output 'INFO 后端 8080 已就绪'

if (PortUp '5173') { Write-Output 'PASS 前端 5173 已在监听（复用现有 dev server）'; exit 0 }

Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "npm run dev > `"$log`" 2>&1" `
  -WorkingDirectory $root -WindowStyle Hidden
Write-Output 'INFO 已启动 vite dev，等待端口就绪...'

for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 1
  if (PortUp '5173') { Write-Output "PASS 前端已就绪（$($i + 1)s，http://localhost:5173）"; exit 0 }
}
Write-Output 'FAIL 60s 内未监听到 5173，日志尾部：'
Get-Content $log -Tail 20 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
exit 1
