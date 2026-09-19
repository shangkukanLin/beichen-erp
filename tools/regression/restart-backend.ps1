# 一键重启后端：停 8080 → 后台启动 → 轮询等待就绪（单次调用完成）
# 说明：内部完成重定向与等待，避免在命令里出现 `;` 复合命令与 `>` 重定向
$root = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$log = Join-Path $root 'backend_diag.log'

function PortPid {
  $line = (netstat -ano | Select-String ':8080\s' | Select-String 'LISTENING' | Select-Object -First 1)
  if ($line) { return ($line.Line -split '\s+')[-1] }
  return $null
}

$pid0 = PortPid
if ($pid0) {
  Stop-Process -Id $pid0 -Force -ErrorAction SilentlyContinue
  Write-Output "INFO 已停止旧进程 pid=$pid0"
  Start-Sleep -Seconds 2
}

Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "start-backend.bat > `"$log`" 2>&1" `
  -WorkingDirectory $root -WindowStyle Hidden
Write-Output 'INFO 已后台启动后端，等待端口就绪...'

for ($i = 0; $i -lt 120; $i++) {
  Start-Sleep -Seconds 1
  $p = PortPid
  if ($p) {
    Write-Output "PASS 后端已就绪（$($i + 1)s，pid=$p）"
    exit 0
  }
}
Write-Output 'FAIL 120s 内未监听到 8080，请查看 backend_diag.log'
exit 1
