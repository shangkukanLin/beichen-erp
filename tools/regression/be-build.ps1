# 后端编译（单次调用）：mvn -q -DskipTests compile，输出日志尾部与结论
$root = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server'
$log = Join-Path $root 'be_compile.log'

# ===== 源头守卫（2026-09-14）：禁止在 SQL/字符串里写中文字面量做条件 =====
# 原因：枚举字段口径是「DB 存 code + 前端映射中文」，SQL 里比对中文会恒不成立（等于没过滤）。
# 历史缺陷 3 处：SupplierMapper/CustomerMapper 用 '已结清' 比 status（code 列）、DashboardService 用 '售后仓' 比 warehouse_type。
# 例外：DataInitializer 的初始化 INSERT（公司名/角色名等是真实数据，不是枚举）。
$src = Join-Path $root 'src\main\java'
# 只查"真实代码行"：注释/javadoc 里为了说明问题而引用中文（如 原条件为 '已结清'）不算违规
$bad = Get-ChildItem -Path $src -Recurse -Include *.java -File |
  Where-Object { $_.FullName -notlike '*\config\DataInitializer.java' } |
  Select-String -Pattern "'[^']*[\u4e00-\u9fa5][^']*'" |
  Where-Object { $_.Line.TrimStart() -notmatch '^(//|/\*|\*)' }
if ($bad) {
  Write-Output "[守卫] FAIL 发现中文字面量 $($bad.Count) 处（枚举一律按 code 比对，中文只放 DB 数据或前端 label）："
  $bad | ForEach-Object { Write-Output ("  " + $_.Path + ":" + $_.LineNumber + ": " + $_.Line.Trim()) }
  exit 1
} else {
  Write-Output '[守卫] PASS 无中文字面量（枚举一律按 code 比对）'
}

Push-Location $root
# 2026-09-21：**必须带 clean** —— 只写 compile 会被 maven-compiler-plugin 的"新不新增"判断骗到。
# 实测（3.11.0 / Maven 3.9.9）：源码里植入一处真实类型错误（int = "字符串"），本脚本仍
# exit 0 报"编译通过"；手跑 mvn 的输出是 `[INFO] Nothing to compile - all classes are up to date`
# ⇒ 门禁给出**假绿灯**（2 分钟后 spring-boot:run 一编译就立刻暴露）。
# 补 `-Dmaven.compiler.useIncrementalCompilation=false` **仍然无效**，故直接 clean 强制重建。
# 门禁宁可慢十几秒，也绝不能放过编译不过的代码。
& cmd /c "mvn -q -DskipTests clean compile > `"$log`" 2>&1"
$code = $LASTEXITCODE
Pop-Location

Write-Output "exitCode=$code"
Get-Content $log -Tail 30 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
if ($code -eq 0) { Write-Output 'PASS 后端编译通过' } else { Write-Output 'FAIL 后端编译失败（见上）' }
if ($code -ne 0) { exit 1 }
