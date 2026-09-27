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

# ===== 源头守卫（2026-09-19 · F7-54）：后端枚举常量必须出现在前端 Label 映射里 =====
# 原因（F7-54 实锤）：前端映射表**手工维护**，后端新增枚举值（如 2026-09-18 的 PURCHASE_EXCHANGE_*、
# PAYABLE_TRANSFER）不会自动进前端 ⇒ sourceBillTypeLabel() 走 `|| code` 兜底 ⇒ 清单页"来源"列
# **直接显示英文 code**（DB 里 PURCHASE_EXCHANGE_IN/RETURN 各 60 行）。
# 做法：解析后端枚举的常量名集合，断言每个常量都出现在前端对应 Label 映射块内；缺失即 FAIL 列出。
$srvRoot = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp'
$enumPairs = @(
  @{ Name = 'SourceBillType';   Java = "$srvRoot\finance\common\SourceBillType.java";   TsMap = 'SourceBillTypeLabel' },
  @{ Name = 'SettlementStatus'; Java = "$srvRoot\finance\common\SettlementStatus.java"; TsMap = 'SettlementStatusLabel' },
  # 2026-09-27：费用类型也收成后端枚举（finance/common/ExpenseType，写入侧 validate 校验+归一化）
  # ⇒ 纳入同一守卫：前端 ExpenseTypeLabel 漏加值即 FAIL（原先费用类型只有前端映射、后端无枚举可守）
  @{ Name = 'ExpenseType';      Java = "$srvRoot\finance\common\ExpenseType.java";      TsMap = 'ExpenseTypeLabel' }
)
$enumMissing = @()
$tsSrc = Get-Content (Join-Path $root 'src\api\enums.ts') -Raw -Encoding UTF8
foreach ($p in $enumPairs) {
  if (-not (Test-Path $p.Java)) { $enumMissing += ($p.Name + ' (java 枚举文件不存在)'); continue }
  $javaSrc = Get-Content $p.Java -Raw -Encoding UTF8
  # 枚举常量形如：   NAME("标签"),   末项 NAME("标签");   （缩进 4 空格；javadoc/字段/方法均为小写开头故不误匹配）
  $codes = @([regex]::Matches($javaSrc, '(?m)^\s{4}([A-Z][A-Z0-9_]*)\s*[(",;]') |
    ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne $p.Name } | Select-Object -Unique)
  $block = [regex]::Match($tsSrc, ($p.TsMap + '[^=]*=\s*\{([^}]*)\}'))
  if (-not $block.Success) { $enumMissing += ($p.Name + ' (前端映射块 ' + $p.TsMap + ' 未找到)'); continue }
  $keys = @([regex]::Matches($block.Groups[1].Value, '[A-Z][A-Z0-9_]{2,}') |
    ForEach-Object { $_.Value } | Select-Object -Unique)
  foreach ($c in $codes) { if ($keys -notcontains $c) { $enumMissing += ($p.Name + '.' + $c) } }
}
if ($enumMissing.Count -gt 0) {
  Write-Output ("[枚举守卫] FAIL 后端枚举常量在前端 Label 映射里缺失 " + $enumMissing.Count + " 个（页面会显示英文 code）：")
  $enumMissing | ForEach-Object { Write-Output ("  " + $_) }
  $hygiene = 1
} else {
  Write-Output '[枚举守卫] PASS 后端枚举常量与前端 Label 映射一致（SourceBillType / SettlementStatus / ExpenseType）'
}

# ===== 源头守卫（2026-09-27）：RemoteSelect 的**函数型** label-key 必须用 : 绑定 =====
# 原因（用户实测，维修退货详情「送修出库仓」显示成 73）：写成 label-key="(row:any)=>row.warehouseName"
# （漏了 :）时 Vue 把它当**静态字符串**传进去 ⇒ RemoteSelect.getLabel 取 row["(row:any)=>row.warehouseName"]
# ⇒ undefined ⇒ el-option 没有 label ⇒ Element Plus 回退显示 **value** ⇒ 下拉与回显全变成 ID。
# 正确写法：:label-key="(row:any)=>row.warehouseName"；静态字符串形式只允许真实字段名（如 label-key="name"）。
$lkBad = @(Get-ChildItem -Path (Join-Path $root 'src') -Recurse -Filter *.vue -File |
  Select-String -Pattern '\slabel-key="\(')
if ($lkBad.Count -gt 0) {
  Write-Output "[label-key守卫] FAIL 发现静态 label-key 里写函数 $($lkBad.Count) 处（函数必须写成 :label-key=\"...\"）："
  $lkBad | ForEach-Object { Write-Output ("  " + $_.Path + ":" + $_.LineNumber + ": " + $_.Line.Trim()) }
  $hygiene = 1
} else {
  Write-Output '[label-key守卫] PASS RemoteSelect 函数型 label-key 均已用 : 绑定（静态只允许真实字段名）'
}

# ===== 源头守卫（2026-09-28）：ui-e2e-zh.json 不得有**重复键** =====
# 原因（当天实锤）：新增文案键时撞了既有键名（lbl_wh_name）⇒ 出现重复键，而 JSON 解析**取"后出现"的那条**
# ⇒ 脚本拿到完全无关的中文（实测报 NOLABEL:所在仓库），现场提示离真因十万八千里、极易误判成"页面又改了"。
# 约定：键名唯一；两个页面文案确实不同时，用**两个不同的键名**（如 lbl_wh_name="所在仓库" 与
# lbl_warehouse_name="仓库名称"）。
$zhPath = Join-Path $wsRoot 'ui-e2e-zh.json'
$allZhKeys = @()
$dupZhKeys = @()
if (Test-Path $zhPath) {
  $zhText = Get-Content $zhPath -Raw -Encoding UTF8
  $allZhKeys = @([regex]::Matches($zhText, '(?m)^\s*"([^"]+)"\s*:') | ForEach-Object { $_.Groups[1].Value })
  $dupZhKeys = @($allZhKeys | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name + ' x' + $_.Count })
}
if ($dupZhKeys.Count -gt 0) {
  Write-Output ("[文案键守卫] FAIL ui-e2e-zh.json 存在重复键 " + $dupZhKeys.Count + " 个（JSON 解析取后者，脚本会拿到意外的中文）：")
  $dupZhKeys | ForEach-Object { Write-Output ("  " + $_) }
  $hygiene = 1
} else {
  Write-Output ('[文案键守卫] PASS ui-e2e-zh.json 无重复键（共 ' + $allZhKeys.Count + ' 键）')
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
