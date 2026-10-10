# 守卫：MyBatis 的 Mapper 接口**不支持同名方法重载**（报告 §7.34）。
#
# 原理：MyBatis 用 `<接口全名>.<方法名>` 作 statement id ⇒ 两个同名方法**抢同一个 id**，
# 只有一个被注册，另一个被**静默忽略**，启动日志打一行：
#   ERROR ... MybatisConfiguration : mapper[...XxxMapper.foo] is ignored, because it exists, maybe from xml file
#
# 实测（2026-10-10）：SupplierMapper 的 selectForUpdate 有两个重载（单参裸锁 / 双参带 company_id），
# 带租户条件的那个被忽略 ⇒ **F7-139（2026-09-20）的租户条件形同虚设**。之所以 20 天没被发现：
# ① 那只是启动日志里一行 ERROR（不中断启动）；② verify-fix-f7-139.ps1 只断言"调用后返回业务错误
#    （供应商不存在）"，**没断言租户条件真的生效** ⇒ 照绿。
#
# 本守卫**静态**扫描所有 `*Mapper.java`，断言接口内没有同名方法 —— 不依赖运行中的后端、不依赖启动日志，
# 可独立跑。附带：若 backend_diag.log 存在，顺便打印其中 `is ignored` 的条数当旁证（**只打印、不断言**：
# 日志可能是旧的，拿它断言会引入假红/假绿）。
#
# ⚠️ 本文件第一次写出来时**自己是假绿的**（2026-10-10 实测）：辅助函数 `Ok` 定义成单参（把参数当"消息"），
#    却按仓里习惯用双参调用 `Ok ($cond) 'msg'` ⇒ 条件被当成消息打印，永远输出 PASS False ✗。
#    ⇒ 本守卫的 `Ok` 固定为双参（条件 + 消息），**调用一律 `Ok ($cond) 'msg'`** ✓（仓内 ui-e2e-lib.ps1 同款）。
# ⚠️ 打印文案一律 ASCII（PS 5.1 + 控制台 GBK 会把中文打成乱码；中文只留在注释里）。
$ErrorActionPreference = 'Continue'
$ROOT = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SRC = Join-Path $ROOT 'beichen-erp-server\src\main\java'
$LOG = Join-Path $ROOT 'backend_diag.log'
$script:fail = 0
function Ok($c, $m) { if ($c) { Write-Output ("PASS " + $m) } else { Write-Output ("FAIL " + $m); $script:fail++ } }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }

Write-Host '=== static scan: duplicate method names inside mapper interfaces (MyBatis has no overloads) ==='
$mappers = @(Get-ChildItem $SRC -Recurse -Filter '*Mapper.java' -ErrorAction SilentlyContinue)
Write-Host ("  mapper interfaces found = " + $mappers.Count)
Ok ($mappers.Count -gt 20) 'the mapper scan really looked at the sources (guard is not vacuously green)'

$dupFiles = 0
$dupCases = 0
foreach ($f in $mappers) {
  $txt = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($f.FullName))
  if ($txt -notmatch 'public\s+interface') { continue }
  $rel = $f.FullName.Substring($SRC.Length + 1)
  $names = @{}
  foreach ($ln in ($txt -split "`r?`n")) {
    # 接口成员签名：4 空格缩进 + 返回类型 + 方法名( —— 跳过注解行(@)/注释行(*,//)/字符串续行(+)
    if ($ln -match '^\s{4}(?!//|\*|/\*|@|\+)([A-Za-z_][\w<>\[\],\.\s]*?)\s+([A-Za-z_]\w*)\s*\(') {
      $n = $Matches[2]
      if (-not $names.ContainsKey($n)) { $names[$n] = @() }
      $names[$n] = @($names[$n]) + $ln.Trim()
    }
  }
  $bad = @()
  foreach ($k in @($names.Keys)) {
    if (@($names[$k]).Count -gt 1) { $bad += ($k + ' x' + @($names[$k]).Count + ' :: ' + ((@($names[$k]) -join ' || '))) }
  }
  if ($bad.Count -gt 0) {
    $dupFiles++
    $dupCases += $bad.Count
    Write-Host ("  !! " + $rel)
    $bad | ForEach-Object { Write-Host ("       " + $_) }
  }
}
Write-Host ("  scanned files with duplicates = $dupFiles ; duplicate cases = $dupCases")
Ok ($dupFiles -eq 0) 'no overloaded method inside any mapper interface (MyBatis would silently ignore one of them)'

Write-Host '=== corroborating evidence: mapper collisions in the last startup log (printed only, never asserted) ==='
if (Test-Path $LOG) {
  $lines = @(Get-Content $LOG -Encoding UTF8 -ErrorAction SilentlyContinue)
  $started = @($lines | Select-String -Pattern 'Started ErpApplication')
  $ignored = @($lines | Select-String -Pattern 'is ignored')
  Write-Host ("  log lines = " + $lines.Count + " ; 'Started ErpApplication' = " + $started.Count + " ; 'is ignored' = " + $ignored.Count)
  Write-Host ("  log mtime = " + (Get-Item $LOG).LastWriteTime)
  if ($ignored.Count -gt 0) { $ignored | ForEach-Object { Write-Host ("    " + $_) } }
  Write-Host '  NOTE evidence only: the log may predate the latest change - restart the backend, then re-run this guard.'
} else {
  Write-Host ("  no log at " + $LOG)
}

if ($script:fail -eq 0) { Write-Output 'RESULT MAPPER-OVERLOAD PASS' } else { Write-Output ("RESULT MAPPER-OVERLOAD FAIL count " + $script:fail) }
exit $script:fail
