# 造数编排器（原仓库缺失）：按拓扑顺序跑完 seed0..seed7d，并做收尾自检
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\seed-all.ps1            # 增量（保留现有主数据/单据）
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\seed-all.ps1 -Fresh     # 先删 seed_ids.json（空库/重置后从零造数）
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\seed-all.ps1 -SkipCheck # 只造数，不做不变量自检
#
# 设计说明：
#   1) 顺序不能乱：seed0 建主数据并写 seed_ids.json（精简版）→ seed1 覆盖为完整版（后续脚本依赖其中的
#      proSupIds/whFin/whAux 等键）→ seed2 采购（产生库存与应付）→ seed3 销售（需库存 qtyA>30）
#      → seed4 委外（料件送货需 AUX 库存，故紧跟 seed4b 补 AUX）→ …→ seed5 财务（需应收/应付）
#      → seed7c/7d 由库存追加销售。
#   2) 每步**独立子进程**执行：某步异常不会中断整串，也避免脚本内 exit 影响编排器。
#   3) 逐步统计失败行（FAIL/ERR），全部步跑完后打印汇总；任一步有失败则以 exit 1 结束（便于 CI/门禁挂接）。
#   4) 收尾自检直接读库核对三条硬不变量：库存 vs 流水 diff、幽灵库存/流水、自有仓负库存。
#
# 未纳入的脚本（按需手动跑，见《上线运维手册》/审核清单）：
#   seed4c（委外仓→委外仓实验性验证）、seed4f_receive（需 seed0 版 seed_ids.json，即须在 seed1 之前跑）

param(
  [switch]$Fresh,
  [switch]$SkipCheck
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$root = 'c:\Users\75629\CodeBuddy\20260710123705'
Set-Location $root

$mysql = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function Sql([string]$q) { return "$(& $mysql --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null)".Trim() }

$steps = @(
  @{ s = 'seed0_master.ps1';          d = '主数据：品牌/客户/供应商/产品/委外物料+BOM/研发项目/仓库' },
  @{ s = 'seed1_master.ps1';          d = '汇总主数据 ID 到 seed_ids.json（覆盖为完整版）' },
  @{ s = 'seed2_purchase.ps1';        d = '采购：采购单+审核、采购退货、仓间调拨、其他出入库' },
  @{ s = 'seed3_sale.ps1';            d = '销售：销售单+审核、销售退货' },
  # RETIRED 2026-09-24：seed4_outsource / seed4b / seed4d_fix 依赖已下线的「手工物料收发单」
  #   （POST /api/outsource/delivery 发料/调拨），已移入 tools/regression/retired/ 不再参与套件。
  #   替代：物料移仓（/api/inventory/material-move）；若后续发现下游缺"发料形态"的库存数据，
  #   再补一个 seed4-material-move.ps1（TODO）。
  @{ s = 'seed4e_deliveries.ps1';     d = '成品入仓单去重并审核' },
  @{ s = 'seed2b_return.ps1';         d = '采购退货（/items 端点变体）' },
  # RETIRED 2026-10-09：seed3b2_outbound.ps1（销售出库造数）随**销售出库模块整体下线**一并删除（报告 §7.28）。
  # seed6_aftersale.ps1 已失效（2026-09-14 实测）：其调用的 /outsource/after-sale/return-defect 端点后端已不存在
  # （脚本已标注 OBSOLETE，保留作历史参考）。其"造出售后待整理原料"的原意由 seed8 用现行 API 重写实现：
  # 对已审核销售单建「待整理(PENDING)」销售退货并审核 → 售后仓出现 PENDING 库存 + after_sale_pending 批次。
  @{ s = 'seed8_aftersale.ps1';       d = '售后待整理原料：PENDING 销售退货 + 换货（供 seed3c 建退货整理单）' },
  @{ s = 'seed3b3_fix.ps1';           d = '补审销售换货单 + 按待分拣缺陷建退货分拣单' },
  @{ s = 'seed3c.ps1';                d = '退货分拣（带 targetWarehouse* 字段）' },
  @{ s = 'seed5_finance.ps1';         d = '财务：账户/费用/收款/付款/发票 + 盘点' },
  @{ s = 'seed7c_sales.ps1';          d = '由库存追加 30 张小额销售单（冲收入）' },
  @{ s = 'seed7d_sales2.ps1';         d = '由库存追加 18 张大额销售单（平衡收入/成本）' }
)

Write-Output '================ 北辰 ERP 造数开始 ================'
Write-Output ("时间: " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + "  Fresh=" + [bool]$Fresh)

# 前置 1：后端就绪
try {
  $null = Invoke-WebRequest -Uri 'http://localhost:8080/doc.html' -UseBasicParsing -TimeoutSec 5
  Write-Output 'PASS 后端已就绪'
} catch {
  Write-Output 'FAIL 后端不可用：请先运行 .\restart-backend.ps1'
  exit 1
}

# 前置 2：-Fresh 时清掉旧的 ID 清单（空库/重置后必须清，否则会引用不存在的旧 ID）
if ($Fresh) {
  Remove-Item (Join-Path $root 'seed_ids.json') -ErrorAction SilentlyContinue
  Write-Output 'INFO 已清除 seed_ids.json（本次从零生成主数据与 ID 清单）'
}

$results = @()
foreach ($st in $steps) {
  $path = Join-Path $root $st.s
  if (-not (Test-Path $path)) { Write-Output ('SKIP ' + $st.s + '（文件不存在）'); continue }

  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $out = (& powershell -NoProfile -ExecutionPolicy Bypass -File $path 2>&1 | Out-String)
  $sw.Stop()

  $failLines = @($out -split "`r?`n" | Where-Object { $_ -match '^\s*(FAIL|ERR)\b' })
  $results += [pscustomobject]@{ Step = $st.s; Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 1); Fails = $failLines.Count }

  Write-Output ('--- {0,-24} {1,6}s fails={2}  {3}' -f $st.s, [math]::Round($sw.Elapsed.TotalSeconds, 1), $failLines.Count, $st.d)
  if ($failLines.Count -gt 0) {
    $failLines | Select-Object -First 6 | ForEach-Object { Write-Output ('      ' + $_.Trim()) }
    if ($failLines.Count -gt 6) { Write-Output ('      …（另有 ' + ($failLines.Count - 6) + ' 条失败，已被汇总统计）') }
  }
}

$totalFail = ($results | Measure-Object -Property Fails -Sum).Sum
if ($null -eq $totalFail) { $totalFail = 0 }

Write-Output ''
Write-Output '================ 造数据汇总 ================'
$results | ForEach-Object { Write-Output ('{0,-24} {1,6}s  fails={2}' -f $_.Step, $_.Seconds, $_.Fails) }
Write-Output ('步骤数=' + $results.Count + '  失败行合计=' + $totalFail)

if (-not $SkipCheck) {
  Write-Output ''
  Write-Output '================ 不变量自检 ================'
  $checks = @(
    @{ n = '库存 vs 流水 diff';          q = "SELECT COUNT(*) FROM (SELECT warehouse_id,product_id,material_id,quality_type,SUM(quantity) sq FROM warehouse_stock GROUP BY 1,2,3,4) s JOIN (SELECT warehouse_id,product_id,material_id,quality_type,SUM(change_quantity) lq FROM warehouse_stock_log GROUP BY 1,2,3,4) l ON s.warehouse_id=l.warehouse_id AND IFNULL(s.product_id,0)=IFNULL(l.product_id,0) AND IFNULL(s.material_id,0)=IFNULL(l.material_id,0) AND IFNULL(s.quality_type,'-')=IFNULL(l.quality_type,'-') WHERE s.sq<>l.lq"; exp = 0 },
    @{ n = '幽灵库存(产品不存在)';       q = "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL AND product_id NOT IN (SELECT id FROM product)"; exp = 0 },
    @{ n = '幽灵流水(产品不存在)';       q = "SELECT COUNT(*) FROM warehouse_stock_log WHERE product_id IS NOT NULL AND product_id NOT IN (SELECT id FROM product)"; exp = 0 },
    @{ n = '自有仓成品负库存';           q = "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity < 0"; exp = 0 }
  )
  $checkFail = 0
  foreach ($c in $checks) {
    $v = Sql $c.q
    if ("$v" -eq "$($c.exp)") { Write-Output ('PASS ' + $c.n + ' = ' + $v) }
    else { Write-Output ('FAIL ' + $c.n + ' = ' + $v + '（期望 ' + $c.exp + '）'); $checkFail++ }
  }
  Write-Output ''
  Write-Output ('数据规模：product=' + (Sql 'SELECT COUNT(*) FROM product') +
                ' stock_rows=' + (Sql 'SELECT COUNT(*) FROM warehouse_stock') +
                ' stock_log=' + (Sql 'SELECT COUNT(*) FROM warehouse_stock_log') +
                ' 采购单=' + (Sql 'SELECT COUNT(*) FROM purchase_order') +
                ' 销售单=' + (Sql 'SELECT COUNT(*) FROM sale_order') +
                ' 应收=' + (Sql 'SELECT COUNT(*) FROM finance_receivable') +
                ' 应付=' + (Sql 'SELECT COUNT(*) FROM finance_payable'))
} else { $checkFail = 0 }

Write-Output ''
if ($totalFail -eq 0 -and $checkFail -eq 0) {
  Write-Output 'RESULT 造数完成（无失败行，不变量自检通过）'
  exit 0
} else {
  Write-Output ('RESULT 造数完成但有失败：脚本失败行=' + $totalFail + '  不变量失败=' + $checkFail + '（请查看上方明细）')
  exit 1
}
