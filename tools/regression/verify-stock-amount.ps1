# 库存金额（物料仓 / 成品仓）—— 2026-10-09 用户需求。
# 口径 = 数量 × 单价（现行移动加权成本价 → 最近进价 → 物料手填单价；三者全空记 0 并标记「成本未维护」），
# 唯一实现见后端 StockCosts / WarehouseStockController#amountSummary。
#
# 断言策略（沿用本项目纪律）：
#   ① **结构不变量**：不写快照数字 —— 金额由「SQL 重算」对照（成品/物料各一条），并校验 合计 = 成品 + 物料、
#      按仓库明细之和 = 合计、口径文案非空；
#   ② **逐行自洽**：物料列表每行 行金额 = (良品+不良) × 该行单价，且 costMissing 与单价是否为空一致；
#   ③ **负例（有牙）**：把某个已维护成本的产品成本清零 ⇒ 成品金额必须下降、且还原后复原（finally 保证还原）；
#   ④ 缺夹具（库里没有任何库存行）⇒ SKIP，不判红。
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return ($out | Select-Object -First 1)
}
function ApiGet([string]$path) {
  # 带 `_t` 抖动参数：同步 XHR 打同一个 URL 会被浏览器缓存命中（实测负例因此读到改动前的旧值 ✗）
  $sep = if ($path.Contains('?')) { '&' } else { '?' }
  $js = "(()=>{const x=new XMLHttpRequest();x.open('GET','" + $path + $sep + "_t='+Date.now(),false);x.setRequestHeader('Authorization',String(localStorage.getItem('beichen_erp_token')));x.send();return String(x.status)+' '+String(x.responseText).slice(0,9000)})()"
  $raw = (EvalJs $js).Trim([char]34)
  if ($raw -notmatch '^200 ') { return $null }
  return $raw.Substring(4)
}

EnsureLogin

# ---------- 0) 范围内有没有库存行（没有就 SKIP，不判红） ----------
$rowCount = [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NOT NULL OR material_id IS NOT NULL")
if ($rowCount -le 0) { Skip 'no stock rows in this database (fixture missing)'; Summary 'stock amount (material + finished goods)'; exit 0 }

# ---------- 1) SQL 独立重算（成品 / 物料），用于对照接口 ----------
$sqlProduct = "SELECT ROUND(IFNULL(SUM(ws.quantity * (CASE WHEN IFNULL(p.cost_price,0)>0 THEN p.cost_price WHEN IFNULL(p.last_in_price,0)>0 THEN p.last_in_price ELSE 0 END)),0),4) FROM warehouse_stock ws JOIN product p ON p.id=ws.product_id WHERE ws.product_id IS NOT NULL AND ws.quality_type IN ('A','B','C','DEFECT','PENDING')"
$sqlMaterial = "SELECT ROUND(IFNULL(SUM(ws.quantity * (CASE WHEN IFNULL(m.cost_price,0)>0 THEN m.cost_price WHEN IFNULL(m.last_in_price,0)>0 THEN m.last_in_price WHEN IFNULL(m.price,0)>0 THEN m.price ELSE 0 END)),0),4) FROM warehouse_stock ws JOIN outsource_material m ON m.id=ws.material_id WHERE ws.material_id IS NOT NULL AND ws.stock_form='MATERIAL' AND ws.quality_type IN ('GOOD','DEFECT')"
$expProduct = [decimal](SqlOne $sqlProduct)
$expMaterial = [decimal](SqlOne $sqlMaterial)

# ---------- 2) 接口口径 ----------
$body = ApiGet '/api/warehouse/stock/amount-summary'
if (-not $body) { Ok $false 'GET /warehouse/stock/amount-summary did not return 200'; Summary 'stock amount (material + finished goods)'; exit 1 }
$d = ($body | ConvertFrom-Json).data
if ($null -eq $d) { Ok $false 'amount-summary returned no data block'; Summary 'stock amount (material + finished goods)'; exit 1 }

$apiProduct = [decimal]$d.productAmount
$apiMaterial = [decimal]$d.materialAmount
$apiTotal = [decimal]$d.totalAmount
Write-Host ("  api: product=$apiProduct material=$apiMaterial total=$apiTotal   sql: product=$expProduct material=$expMaterial")
Ok ([Math]::Abs($apiProduct - $expProduct) -le 0.01) "finished-goods amount equals the SQL recomputation (api=$apiProduct sql=$expProduct)"
Ok ([Math]::Abs($apiMaterial - $expMaterial) -le 0.01) "material amount equals the SQL recomputation (api=$apiMaterial sql=$expMaterial)"
Ok ([Math]::Abs($apiTotal - ($apiProduct + $apiMaterial)) -le 0.01) "total = finished goods + material (total=$apiTotal)"
$whSum = [double]0
foreach ($w in @($d.byWarehouse)) { $whSum += [double]$w.totalAmount }
Ok ([Math]::Abs($whSum - [double]$apiTotal) -le 0.05) ("per-warehouse breakdown sums to the total (wh=$([Math]::Round($whSum,4)) total=$apiTotal)")
Ok (-not [string]::IsNullOrWhiteSpace([string]$d.caliber)) 'the response carries the caliber text (single source of truth for wording)'
Write-Host ("  cost missing: productQty=$($d.productCostMissingQty) materialQty=$($d.materialCostMissingQty) productSkus=$($d.productCostMissingSkuCount) materialSkus=$($d.materialCostMissingSkuCount)")

# ---------- 3) 逐行自洽（物料库存列表） ----------
$mBody = ApiGet '/api/warehouse/stock/material-stock/page?pageNum=1&pageSize=50'
if ($mBody) {
  # 注意：这里直接读**原始接口**响应 ⇒ 结构与页面里的 request 封装不同，记录在 data.records 下
  # （写成顶层 .records 会得到 $null，@($null) 又会被当成"1 行" ⇒ 断言看似在跑其实在空转）
  $mRows = @(($mBody | ConvertFrom-Json).data.records)
  if ($mRows.Count -eq 0) { Skip 'material-stock/page returned no rows (nothing to check row-by-row)'; }
  $bad = 0
  $missing = 0
  $detail = ''
  foreach ($r in $mRows) {
    $qty = [decimal]([double]$r.qtyGood + [double]$r.qtyDefect)
    $unit = $r.unitCost
    $isMissing = [bool]$r.costMissing
    if ($isMissing) { $missing++ }
    else {
      $expect = [Math]::Round([double]$qty * [double]$unit, 4)
      if ([Math]::Abs([double]$r.stockAmount - $expect) -gt 0.01) {
        $bad++
        if (-not $detail) { $detail = "amount mismatch: mat=$($r.materialId) wh=$($r.warehouseId) good=$($r.qtyGood) defect=$($r.qtyDefect) unit=$unit amount=$($r.stockAmount) expect=$expect" }
      }
    }
    if ((($null -eq $unit) -or ([double]$unit -le 0)) -ne $isMissing) {
      $bad++
      if (-not $detail) { $detail = "costMissing mismatch: mat=$($r.materialId) unit=$unit costMissing=$isMissing" }
    }
  }
  Write-Host ("  material rows = " + $mRows.Count + " ; costMissing rows = $missing ; row math mismatches = $bad")
  if ($detail) { Write-Host ('  first mismatch: ' + $detail) }
  Ok ($bad -eq 0) ('every material row: amount = (good+defect) x unitCost, and costMissing matches a missing unit price' + $(if ($detail) { '  [' + $detail + ']' } else { '' }))
} else {
  Skip 'material-stock/page returned no body (endpoint unavailable)'
}

# ---------- 4) 负例：把某个"有库存且有成本"的产品成本清零 ⇒ 成品金额必须下降 ----------
# 注意：变量名不能叫 $pId —— PowerShell 变量名不区分大小写，会撞上只读的 $PID（报 VariableNotWritable）。
$prodId = SqlOne "SELECT p.id FROM product p JOIN warehouse_stock ws ON ws.product_id=p.id WHERE IFNULL(p.cost_price,0)>0 AND ws.quality_type IN ('A','B','C','DEFECT','PENDING') GROUP BY p.id ORDER BY SUM(ws.quantity) DESC LIMIT 1"
if (-not $prodId -or $prodId -eq 'NULL') {
  Skip 'no product with both stock and a maintained cost price (negative control not applicable)'
} else {
  $oldCost = SqlOne ("SELECT cost_price FROM product WHERE id=" + $prodId)
  $oldLast = SqlOne ("SELECT IFNULL(last_in_price,0) FROM product WHERE id=" + $prodId)
  try {
    SqlOne ("UPDATE product SET cost_price=0, last_in_price=0 WHERE id=" + $prodId) | Out-Null
    $body2 = ApiGet '/api/warehouse/stock/amount-summary'
    $afterProduct = [decimal](($body2 | ConvertFrom-Json).data.productAmount)
    Write-Host ("  negative control on product #$prodId : $apiProduct -> $afterProduct")
    Ok ($afterProduct -lt $apiProduct) "zeroing one product's cost lowers the finished-goods amount (negative control)"
  } finally {
    SqlOne ("UPDATE product SET cost_price=" + $oldCost + ", last_in_price=" + $oldLast + " WHERE id=" + $prodId) | Out-Null
    $restored = [decimal](SqlOne ("SELECT cost_price FROM product WHERE id=" + $prodId))
    Ok ($restored -eq [decimal]$oldCost) 'the cost price was restored after the negative control'
  }
}

# ---------- 5) 前端呈现：两个页面都要有金额（含「成本未维护」明示） ----------
Open '/outsource/material-stock' 3500
$ui = (EvalJs "(()=>{const t=document.querySelector('.el-table');if(!t)return 'NOTABLE';const n=t.querySelectorAll('.el-table__header th').length;const bar=document.querySelector('.stock-amount-bar');const miss=document.querySelectorAll('.cost-missing').length;const w=document.querySelector('.el-table__body-wrapper');const noH=w?(w.scrollWidth<=w.clientWidth+2):null;const cnt=bar?(String(bar.innerText).match(/\d[\d,]*\.\d\d/g)||[]).length:0;return 'cols=' + n + ' ; bar=' + !!bar + ' ; barHasDigit=' + (bar?/\d/.test(String(bar.innerText)):false) + ' ; amtCount=' + cnt + ' ; missingCells=' + miss + ' ; noHScroll=' + noH})()").Trim([char]34)
Write-Host ('  material-stock UI: ' + $ui)
# 范围已限定（物料页）⇒ 只该出现一个金额；重复显示会被当成"两份金额"（2026-10-09 用户反馈）
Ok ($ui -match 'amtCount=1') 'the scoped (material) amount bar shows exactly ONE figure (no duplicated line)'
Ok ($ui -notmatch 'NOTABLE') 'the material stock page renders its table'
# 2026-10-10 基线更新（附依据，不是粉饰）：本页原为 **8** 列（7 + 金额列）。
#   同日按用户口径「独立「物料类型」列与名称前缀内容重复 ⇒ 删列、只留前缀」删掉 1 列 ⇒ 现为 **7** 列。
#   金额列**仍在** ✓，且本页「不横向滚动」与「合计条有数字」两条断言不变 ✓（那才是本守卫的主旨 ✓）。
Ok ($ui -match 'cols=7') 'the material stock list: 7 columns (the duplicated type column was merged into the name prefix; the amount column is still present)'
# 加列必须不破坏本项目"表格一行放得下"的约定（成品主列表正是因此没加列）
Ok ($ui -match 'noHScroll=true') 'adding the amount column did not introduce horizontal scrolling'
Ok ($ui -match 'bar=true' -and $ui -match 'barHasDigit=true') 'the material stock page shows the amount bar with a figure'
if ([int](SqlOne "SELECT COUNT(*) FROM warehouse_stock ws JOIN outsource_material m ON m.id=ws.material_id WHERE ws.material_id IS NOT NULL AND ws.stock_form='MATERIAL' AND IFNULL(m.cost_price,0)=0 AND IFNULL(m.last_in_price,0)=0 AND IFNULL(m.price,0)=0") -gt 0) {
  Ok ($ui -match 'missingCells=[1-9]') 'rows whose cost is not maintained show the explicit marker instead of a silent 0'
}

Open '/inventory/product-stock' 3500
$ui2 = (EvalJs "(()=>{const bar=document.querySelector('.stock-amount-bar');const cnt=bar?(String(bar.innerText).match(/\d[\d,]*\.\d\d/g)||[]).length:0;return 'bar=' + !!bar + ' ; barHasDigit=' + (bar?/\d/.test(String(bar.innerText)):false) + ' ; amtCount=' + cnt + ' ; tableCount=' + document.querySelectorAll('.el-table').length})()").Trim([char]34)
Write-Host ('  product-stock UI: ' + $ui2)
Ok ($ui2 -match 'bar=true' -and $ui2 -match 'barHasDigit=true') 'the finished-goods stock page shows the amount bar with a figure'
Ok ($ui2 -match 'amtCount=1') 'the scoped (finished-goods) amount bar shows exactly ONE figure (no duplicated line)'

Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'

# ---------- 6) 成品库存详情页：金额**只算成品**（用户 2026-10-09 口径） ----------
# 该页行全部是该产品在成品仓的库存行 ⇒ 金额 = 各行 stockAmount 之和，天然不含物料。
# DOM 里的中文经 EvalJs 回传会被按 GBK 解 ⇒ 必须走 base64 双向（本仓既定的读法）。
$prodForDetail = SqlOne "SELECT p.id FROM product p JOIN warehouse_stock ws ON ws.product_id=p.id WHERE IFNULL(p.cost_price,0)>0 GROUP BY p.id ORDER BY SUM(ws.quantity) DESC LIMIT 1"
if ($prodForDetail -and $prodForDetail -ne 'NULL') {
  Open ('/inventory/product-stock/detail/' + $prodForDetail) 3500
  $b64 = (EvalJs "(()=>{const c=document.querySelector('.el-descriptions');const t=c?String(c.innerText):'';return btoa(unescape(encodeURIComponent(t)))})()").Trim([char]34)
  $info = ''
  try { $info = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) } catch { $info = '' }
  $flat = ($info -replace '\s+', '')
  Write-Host ('  product detail info = ' + $flat.Substring(0, [Math]::Min(140, $flat.Length)))
  Ok ($flat -match '库存金额') 'the finished-goods stock DETAIL page shows a 库存金额 field'
  Ok ($flat -match '库存金额[\d,]+') 'the detail page shows a figure for it (ascribed to finished goods only)'
} else {
  Skip 'no priced product with stock (detail-page check not applicable)'
}
Summary 'stock amount (material + finished goods)'
