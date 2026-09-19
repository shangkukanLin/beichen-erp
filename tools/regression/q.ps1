param([Parameter(Mandatory = $true)][string]$Sql)
$exe = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
& $exe --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $Sql 2>$null
