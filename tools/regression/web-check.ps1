# 前端类型检查（单次调用）：默认 vue-tsc --noEmit；-Script build 则跑 npm run build
param([string]$Script = 'typecheck')
$root = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web'
$log = Join-Path $root 'web_check.log'

# ===== 源头守卫（2026-09-14）：禁止用 toISOString() 取业务日期 =====
# 原因：toISOString() 返回 UTC，东八区 08:00 之前会算成**前一天**（每月 1 日还会跨月）。
# 历史上 40 个文件 / 50 余处这样取"今天"，导致新增单据默认日期、报表默认区间、导出文件名在
# 早上 8 点前集体偏一天；"本月"口径还会算成上个月。业务日期唯一入口是 src/utils/date.ts。
$bad = Get-ChildItem -Path (Join-Path $root 'src') -Recurse -Include *.vue, *.ts -File |
  Where-Object { $_.FullName -notlike '*\utils\date.ts' } |
  Select-String -Pattern 'toISOString\(\)\.(slice\(0, ?(7|10)\)|split\(''T''\)\[0\])'
if ($bad) {
  Write-Output "[守卫] FAIL 发现 UTC 业务日期写法 $($bad.Count) 处（应改用 @/utils/date 的 localDate() / localMonth()）："
  $bad | ForEach-Object { Write-Output ("  " + $_.Path + ":" + $_.LineNumber + ": " + $_.Line.Trim()) }
  $hygiene = 1
} else {
  Write-Output '[守卫] PASS 无 UTC 业务日期写法（业务日期统一走 @/utils/date）'
  $hygiene = 0
}

# ===== 源头守卫（2026-09-16）：禁止字号硬编码 =====
# 全站字号阶梯统一在 src/styles/tokens.css（辅助 12 / 正文 14 / 卡片标题 15 / 页标题 18 / 统计 16·22），
# 组件基准走 --el-font-size-*；页面里写死 font-size:NNpx 会绕开阶梯、又回到"同一页字号不齐"。
# 例外：403 页的大插图 96px。
$fontBad = Get-ChildItem -Path (Join-Path $root 'src') -Recurse -Include *.vue, *.css, *.ts -File |
  Where-Object { $_.Name -ne 'tokens.css' -and $_.FullName -notlike '*\views\error\403.vue' } |
  Select-String -Pattern 'font-size:\s*[0-9.]+px'
if ($fontBad) {
  Write-Output "[字号守卫] FAIL 发现字号硬编码 $($fontBad.Count) 处（应改用 src/styles/tokens.css 的 --app-font-* 变量）："
  $fontBad | ForEach-Object { Write-Output ("  " + $_.Path + ":" + $_.LineNumber + ": " + $_.Line.Trim()) }
  $hygiene = 1
} else {
  Write-Output '[字号守卫] PASS 无字号硬编码（统一走 --app-font-* 阶梯）'
}

# ===== 源头守卫（2026-09-18）：ui-e2e-*.ps1 含非 ASCII 必须带 UTF-8 BOM =====
# 原因（T3 实锤）：Windows PowerShell 5.1 对**无 BOM** 的 .ps1 按 ANSI(GBK) 解码 →
# 文件里的中文会变成乱码（实测 ui-e2e-4d 的 Step 'reopen (反结单)' 打印成 `鍙嶇粨鍗?`），
# 更严重的是中文**选择器/文案断言**会静默失效（元素找不到 → 断言恒不成立 → 假绿）。
# 约定：含中文的 .ps1 一律存成 UTF-8 **with BOM**（写盘用 [IO.File]::WriteAllText + UTF8Encoding($true)）。
$wsRoot = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$e2eScripts = @(Get-ChildItem -Path $wsRoot -Filter 'ui-e2e-*.ps1' -File -ErrorAction SilentlyContinue)
$bomBad = @()
foreach ($f in $e2eScripts) {
  $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
  $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
  if ($hasBom) { continue }
  $nonAscii = $false
  foreach ($by in $bytes) { if ($by -ge 0x80) { $nonAscii = $true; break } }
  if ($nonAscii) { $bomBad += $f.Name }
}
if ($bomBad.Count -gt 0) {
  Write-Output "[BOM守卫] FAIL 以下 ui-e2e-*.ps1 含非 ASCII 但缺 UTF-8 BOM（PS 5.1 下中文会乱码、断言会静默失效）："
  $bomBad | ForEach-Object { Write-Output ("  " + $_) }
  $hygiene = 1
} else {
  Write-Output ("[BOM守卫] PASS ui-e2e-*.ps1 编码规范（含非 ASCII 的均已带 UTF-8 BOM；共 " + $e2eScripts.Count + " 个）")
}

Push-Location $root
if ($Script -eq 'build') {
  & cmd /c "npm run build > `"$log`" 2>&1"
} else {
  & cmd /c "npx vue-tsc --noEmit > `"$log`" 2>&1"
}
$code = $LASTEXITCODE
Pop-Location

Write-Output "exitCode=$code"
Get-Content $log -Tail 40 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
if ($code -eq 0 -and $hygiene -eq 0) { Write-Output 'PASS 前端检查通过' } else { Write-Output 'FAIL 前端检查未通过（见上）' }
if ($code -ne 0 -or $hygiene -ne 0) { exit 1 }
