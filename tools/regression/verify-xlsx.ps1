# 解析 xlsx（读取 sharedStrings + sheet1 单元格），用于校验导出内容
param([Parameter(Mandatory = $true)][string]$Path)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
Write-Output ("file=" + (Split-Path $Path -Leaf))

# 共享字符串表
$shared = @()
$e = $zip.GetEntry('xl/sharedStrings.xml')
if ($e) {
  $sr = New-Object System.IO.StreamReader($e.Open())
  $xml = $sr.ReadToEnd(); $sr.Close()
  foreach ($m in [regex]::Matches($xml, '<si>(.*?)</si>', 'Singleline')) {
    $texts = [regex]::Matches($m.Groups[1].Value, '<t[^>]*>(.*?)</t>', 'Singleline') | ForEach-Object { $_.Groups[1].Value }
    $shared += ($texts -join '')
  }
}
Write-Output ("sharedStrings=" + $shared.Count)

# 第一张工作表
$e = $zip.GetEntry('xl/worksheets/sheet1.xml')
$sr = New-Object System.IO.StreamReader($e.Open()); $sx = $sr.ReadToEnd(); $sr.Close()

# 列宽与合并（确认标题行合并、列宽已设）
$cols = [regex]::Matches($sx, '<col[^>]*min="(\d+)"[^>]*max="(\d+)"[^>]*width="([\d.]+)"') | ForEach-Object { $_.Groups[1].Value + '-' + $_.Groups[2].Value + ':' + $_.Groups[3].Value }
$merges = [regex]::Matches($sx, '<mergeCell ref="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
Write-Output ("cols=" + ($cols -join ' ') + "  merges=" + ($merges -join ' '))
$zip.Dispose()

$rows = [regex]::Matches($sx, '<row[^>]*>(.*?)</row>', 'Singleline')
Write-Output ("rows=" + $rows.Count)
foreach ($r in $rows) {
  $cells = [regex]::Matches($r.Groups[1].Value, '<c r="([A-Z]+)(\d+)"(?: s="(\d+)")?(?: t="(\w+)")?\s*(?:/>|>(.*?)</c>)', 'Singleline')
  $parts = @()
  foreach ($c in $cells) {
    $col = $c.Groups[1].Value
    $t = $c.Groups[4].Value
    $inner = $c.Groups[5].Value
    $v = ''
    if ($inner -match '<v>(.*?)</v>') { $v = $Matches[1] }
    if ($t -eq 's' -and $v -ne '') { $v = $shared[[int]$v] }
    if ($inner -match '<f>(.*?)</f>') { $v = '=' + $Matches[1] + ' -> ' + $v }
    $parts += ($col + '=' + $v)
  }
  if ($parts.Count -gt 0) { Write-Output ($parts -join ' | ') }
}
