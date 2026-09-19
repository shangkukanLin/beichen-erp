# 发版前门禁（单次调用跑完整轮）：B0–B8 主链路断言套件 + 全局不变量对账
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\gate.ps1
# 说明：只组合既有断言套件（PASS/FAIL 约定一致），不改任何数据清理逻辑；失败时打印明细并 exit 1
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$root = 'c:\Users\75629\CodeBuddy\20260710123705'
$mysql = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$base = 'http://localhost:8080/api'

# ---------- 0) 前置：后端在跑 ----------
$up = $false
try {
  $r = Invoke-WebRequest -Uri "$base/auth/login" -Method Post -ContentType 'application/json' `
    -Body '{"username":"lin","password":"123","companyId":1}' -UseBasicParsing -TimeoutSec 5
  if ($r.StatusCode -eq 200) { $up = $true }
} catch { $up = $false }
if (-not $up) { Write-Output 'FAIL 后端未就绪（先跑 .\restart-backend.ps1）'; exit 2 }

# ---------- 1) 断言套件（按 B 链路顺序） ----------
$suites = @(
  @{ name = 'B8-2 垂直越权(403)';        file = 't1.ps1';         action = 'rbac' },
  @{ name = 'B0-1 角色菜单矩阵';         file = 't1.ps1';         action = 'rbac-roles' },
  @{ name = '边界矩阵-非法输入/状态机';  file = 'edge.ps1';       action = '' },
  @{ name = '边界矩阵-审核阶段';         file = 'edge2.ps1';      action = '' },
  @{ name = 'B8-1 多租户隔离';           file = 't1.ps1';         action = 'tenant' },
  @{ name = 'B8-4 导出完整性';           file = 't2.ps1';         action = 'io' },
  @{ name = 'B1 采购三态+流水code';      file = 'u1.ps1';         action = 'codex' },
  @{ name = 'D1/E3 台账号与状态口径';    file = 'u1.ps1';         action = 'd1e3' },
  @{ name = 'A3/C2/C3/C5/D5/E1/E2/E4';   file = 'u1.ps1';         action = 'debt8' },
  @{ name = 'B3-2/C7 出库绑定校验';      file = 'u1.ps1';         action = 'c7' },
  @{ name = 'B8-3 并发(采购/收款/结单)'; file = 'u1.ps1';         action = 'concurrency' },
  @{ name = 'B5-4 账单口径';             file = 'u1.ps1';         action = 'bill' },
  @{ name = 'B7 报表口径跨页一致';       file = 's1.ps1';         action = 'b7' },
  @{ name = 'B6-3 移动加权成本';         file = 'cost_check.ps1'; action = '' }
)

$rows = @()
$failDetail = @()

foreach ($s in $suites) {
  $p = Join-Path $root $s.file
  if (-not (Test-Path $p)) {
    $rows += [pscustomobject]@{ Suite = $s.name; Pass = 0; Fail = 0; Verdict = 'SKIP(缺脚本)' }
    continue
  }
  if ($s.action -ne '') { $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $p -Action $s.action 2>$null }
  else { $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $p 2>$null }

  # 子进程输出可能整块返回（不按行切分），统一转成文本后用多行正则统计，避免漏断
  $raw = ($out | Out-String)
  $rawLines = 0
  if ($raw.Trim().Length -gt 0) { $rawLines = ($raw -split "`r?`n").Count }
  $passLines = @(); $failLines = @()
  foreach ($m in [regex]::Matches($raw, '(?m)^PASS.*$')) { $passLines += $m.Value.Trim() }
  foreach ($m in [regex]::Matches($raw, '(?m)^FAIL.*$')) { $failLines += $m.Value.Trim() }

  $verdict = 'PASS'
  if ($failLines.Count -gt 0) { $verdict = 'FAIL' }
  elseif ($passLines.Count -eq 0) { $verdict = 'NOASSERT' }
  $rows += [pscustomobject]@{ Suite = $s.name; Pass = $passLines.Count; Fail = $failLines.Count; Verdict = $verdict }
  foreach ($f in $failLines) { $failDetail += ('[' + $s.name + '] ' + $f) }
  Write-Output ('--- ' + $verdict + ' ' + $s.name + ' (pass=' + $passLines.Count + ' fail=' + $failLines.Count + ' outLines=' + $rawLines + ')')
}

# ---------- 2) 全局不变量（DB 直查，全部应为 0） ----------
function Sql([string]$q) { return "$(& $mysql --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null)".Trim() }
$inv = @(
  @{ n = '库存 vs 流水 diff'; q = "SELECT COUNT(*) FROM (SELECT warehouse_id,product_id,material_id,quality_type,SUM(quantity) sq FROM warehouse_stock GROUP BY 1,2,3,4) s JOIN (SELECT warehouse_id,product_id,material_id,quality_type,SUM(change_quantity) lq FROM warehouse_stock_log GROUP BY 1,2,3,4) l ON s.warehouse_id=l.warehouse_id AND IFNULL(s.product_id,0)=IFNULL(l.product_id,0) AND IFNULL(s.material_id,0)=IFNULL(l.material_id,0) AND IFNULL(s.quality_type,'-')=IFNULL(l.quality_type,'-') WHERE s.sq<>l.lq" },
  @{ n = '台账号非 YF- 行数'; q = "SELECT COUNT(*) FROM finance_payable WHERE bill_no NOT LIKE 'YF-%'" },
  @{ n = '-ADVANCE 拼接台账号'; q = "SELECT COUNT(*) FROM finance_payable WHERE bill_no LIKE '%-ADVANCE'" },
  @{ n = 'other_io 状态越界'; q = "SELECT COUNT(*) FROM outsource_other_io WHERE status NOT IN ('DRAFT','AUDITED','CANCELLED')" },
  @{ n = '流水 changeType 含中文'; q = "SELECT COUNT(*) FROM warehouse_stock_log WHERE change_type REGEXP '[^ -~]'" },
  @{ n = '幽灵库存(产品不存在)'; q = "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL AND product_id NOT IN (SELECT id FROM product)" },
  @{ n = '幽灵流水(产品不存在)'; q = "SELECT COUNT(*) FROM warehouse_stock_log WHERE product_id IS NOT NULL AND product_id NOT IN (SELECT id FROM product)" },
  @{ n = '成品负库存行'; q = "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity < 0" },
  @{ n = '丢失状态列的单据(销售/采购)'; q = "SELECT (SELECT COUNT(*) FROM sale_order WHERE status IS NULL OR status='') + (SELECT COUNT(*) FROM purchase_order WHERE status IS NULL OR status='')" },
  # P2-34/P0（2026-09-14）：super_admin 是**平台级能力角色**，只允许唯一平台账号 lin 持有。
  # 守护的是一条真实存在过的越权通道：CompanyServiceImpl.create 原为"优先 super_admin、回退 admin"，
  # 当 super_admin 角色被建出后，新建公司的管理员会直接拿到平台级权限（可整库导出/导入/清空）。
  @{ n = 'super_admin 仅 lin 持有(非 lin 越权)'; q = "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='super_admin' AND u.username<>'lin'" },
  # 仓库类型必须存**枚举 code**（AUXILIARY/FINISHED/DEFECT/AFTER_SALE）。存中文 label 会让
  # SaleReturnServiceImpl 的售后仓校验、换货的 AFTER_SALE 校验静默失败（2026-09-14 造数时发现，
  # 起因是 seed0_master.ps1 把 label 当 code 写入）。
  @{ n = '仓库类型越界(须为枚举code)'; q = "SELECT COUNT(*) FROM warehouse WHERE warehouse_type NOT IN ('AUXILIARY','FINISHED','DEFECT','AFTER_SALE')" }
)

foreach ($i in $inv) {
  $v = Sql $i.q
  if ("$v" -eq '0') {
    $rows += [pscustomobject]@{ Suite = '不变量: ' + $i.n; Pass = 1; Fail = 0; Verdict = 'PASS' }
  } else {
    $rows += [pscustomobject]@{ Suite = '不变量: ' + $i.n; Pass = 0; Fail = 1; Verdict = 'FAIL' }
    $failDetail += ('[不变量] ' + $i.n + ' = ' + $v + '（应 0）')
  }
}

# ---------- 3) 汇总 ----------
Write-Output ''
Write-Output '================ 发版前门禁汇总 ================'
$tp = 0; $tf = 0; $bad = 0
foreach ($row in $rows) {
  $tp += $row.Pass; $tf += $row.Fail
  if ($row.Verdict -eq 'FAIL') { $bad++ }
  Write-Output ($row.Verdict.PadRight(9) + ' pass=' + ([string]$row.Pass).PadRight(4) + ' fail=' + ([string]$row.Fail).PadRight(4) + ' ' + $row.Suite)
}
Write-Output '------------------------------------------------'
Write-Output ('TOTAL pass=' + $tp + ' fail=' + $tf + ' 失败套件=' + $bad)

if ($failDetail.Count -gt 0) {
  Write-Output '--- 失败明细 ---'
  foreach ($d in $failDetail) { Write-Output $d }
  Write-Output 'RESULT 门禁未通过'
  exit 1
}
Write-Output 'RESULT 门禁通过'
exit 0
