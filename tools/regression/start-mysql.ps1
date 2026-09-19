# 启动本地 MySQL（E:\dev\mysql，非服务方式）并等待 3306 就绪
$mysqld = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysqld.exe'
$ini = 'E:\dev\mysql\my.ini'
$errLog = 'E:\dev\mysql\mysql.err.log'

function PortUp {
  $l = netstat -ano | Select-String ':3306\s' | Select-String 'LISTENING' | Select-Object -First 1
  return [bool]$l
}

if (PortUp) { Write-Output 'PASS MySQL 3306 已在监听（复用现有进程）'; exit 0 }
if (-not (Test-Path $mysqld)) { Write-Output "FAIL 未找到 mysqld.exe: $mysqld"; exit 1 }
if (-not (Test-Path $ini)) { Write-Output "FAIL 未找到 my.ini: $ini"; exit 1 }

# --defaults-file 必须作为第一个参数；窗口隐藏、后台运行
Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden
Write-Output 'INFO 已启动 mysqld，等待 3306 就绪...'

for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 1
  if (PortUp) { Write-Output "PASS MySQL 已就绪（$($i + 1)s）"; exit 0 }
}
Write-Output 'FAIL 60s 内未监听到 3306，错误日志尾部：'
Get-Content $errLog -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
exit 1
