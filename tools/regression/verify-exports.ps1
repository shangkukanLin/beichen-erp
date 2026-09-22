# 批量验证各页面「导出 Excel」：逐个打开页面 → 点击导出按钮 → 解析下载文件（单次调用跑完整轮）
$dl = 'c:\Users\75629\CodeBuddy\20260710123705\dl'
$self = 'c:\Users\75629\CodeBuddy\20260710123705'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

$pages = @(
  @{ n = '成品库存查询';        u = 'http://localhost:5173/inventory/product-stock' },
  @{ n = '成品库存流水';        u = 'http://localhost:5173/inventory/stock-log' },
  @{ n = '成品库存详情';        u = 'http://localhost:5173/inventory/product-stock' },
  @{ n = '产品库存分布';        u = 'http://localhost:5173/inventory/product-stock/detail/2' },
  @{ n = '自有仓库明细';        u = 'http://localhost:5173/inventory/warehouse/detail/1' },
  @{ n = '委外仓库明细';        u = 'http://localhost:5173/outsource/warehouse/detail/2' },
  @{ n = '产品库存流水(仓+品)'; u = 'http://localhost:5173/inventory/warehouse/product-history/1/2' },
  @{ n = '物料库存流水(仓+料)'; u = 'http://localhost:5173/inventory/warehouse/material-history/2/1' },
  @{ n = '利润表';              u = 'http://localhost:5173/analysis/profit' },
  @{ n = '税务分析';            u = 'http://localhost:5173/analysis/tax' },
  @{ n = '屏幕资料知识库';      u = 'http://localhost:5173/dev/screen-model' }
)

$pass = 0; $fail = 0
foreach ($p in $pages) {
  Write-Output ("=== " + $p.n + " ===")
  Get-ChildItem $dl -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

  cmd /c "agent-browser open $($p.u)" | Out-Null
  cmd /c "agent-browser wait 2500" | Out-Null
  $snap = (cmd /c "agent-browser snapshot -i") -join "`n"

  $m = [regex]::Match($snap, 'button "(导出[^"]*)"[^\n]*ref=(e\d+)')
  if (-not $m.Success) { Write-Output 'FAIL 未找到「导出」按钮'; $fail++; continue }
  $label = $m.Groups[1].Value
  $ref = $m.Groups[2].Value

  cmd /c "agent-browser click @$ref" | Out-Null

  # 等待下载落地（最多 10s）
  $file = $null
  for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Milliseconds 500
    $file = Get-ChildItem $dl -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($file) { break }
  }
  if (-not $file) { Write-Output ("FAIL 点击「" + $label + "」后 10s 内未产生下载文件"); $fail++; continue }

  Write-Output ("OK 按钮=「" + $label + "」 文件=" + $file.Name + " (" + $file.Length + "B)")
  & "$self\verify-xlsx.ps1" -Path $file.FullName | Select-Object -First 7 | ForEach-Object { Write-Output $_ }
  $pass++
}
Write-Output ("RESULT pass=" + $pass + " fail=" + $fail)
