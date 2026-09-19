# Seed 8: 售后链路补数（seed6 的正确实现 / 替代已失效的 seed6）
#
# 背景（2026-09-14 定位）：
#   1) seed6_aftersale.ps1 调用的 /outsource/after-sale/return-defect 端点**后端已不存在** → 恒定 404 失败；
#   2) 于是「售后待整理批次(after_sale_pending)」「退货整理单(return_sort)」两个模块始终为空；
#   3) 更深一层原因：售后仓待整理库存只认 `quality_type='PENDING'（待分类）` 的正库存
#      （见 ReturnSortServiceImpl.defectStock），而 seed3 的销售退货写的是 qualityType='A'（良品）→ 清单恒空。
#
# 本脚本做两件事（都用现行 API）：
#   ① 对若干**已审核销售单**创建「待分类(PENDING)」销售退货并审核 → 售后仓出现 PENDING 库存 + after_sale_pending 批次；
#   ② 尝试创建销售换货单并审核（换货同样会写 after_sale_pending，源类型 SALE_EXCHANGE）——失败不致命，仅打印原因。
#   随后由既有的 seed3c.ps1 读取 defect-stock → 建退货整理单 → 审核（职责分离，故本脚本只造"原料"）。
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\seed8_aftersale.ps1

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080'           # 注意：基址**不含** /api，路径里才带（与其它 seed 脚本一致）
$idsFile = 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json'

$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }

function GetP($path) {
  try {
    $r = Invoke-RestMethod -Uri ($B + $path) -Headers $H
    # 本系统业务错误走 HTTP 200 + body.code，必须显式校验，否则 null 会被误当"空列表"
    if ($r.code -and $r.code -ne 200) { Write-Output ('ERR GET ' + $path + ' -> code=' + $r.code + ' msg=' + $r.msg); return $null }
    return $r.data
  } catch { Write-Output ('ERR GET ' + $path + ' -> ' + $_.Exception.Message); return $null }
}
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8
  try { return Invoke-RestMethod -Uri ($B + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) }
  catch { Write-Output ('ERR POST ' + $path + ' -> ' + $_.Exception.Message); return $null }
}
function PutOp($path) {
  try { return Invoke-RestMethod -Uri ($B + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) }
  catch { Write-Output ('ERR PUT ' + $path + ' -> ' + $_.Exception.Message); return $null }
}
# 取某分页接口最新一张 DRAFT 单据的 id（创建后回调审核用）
function LatestDraftId($pagePath, $key) {
  $pg = GetP $pagePath
  if ($null -eq $pg) { return 0 }
  $rows = @($pg.records | Where-Object { $_.status -eq 'DRAFT' })
  if ($rows.Count -eq 0) { return 0 }
  return [int](($rows | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1).id)
}

$ids = Get-Content $idsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$whAfs = [int]@($ids.whAfs)[0]
Write-Output ('whAfs=' + $whAfs)

# 已产生退货的销售单（避免对同一单重复退货导致超量）
$exists = @{}
$srPage = GetP '/api/sale/return/page?pageSize=300'
if ($srPage) { foreach ($r in @($srPage.records)) { if ($r.saleOrderId) { $exists[[int]$r.saleOrderId] = $true } } }

# 0) 先补审历史遗留的 DRAFT 退货单（此前因仓库类型口径错误而审核失败，修数据后应能通过；幂等）
$draftFixed = 0
if ($srPage) {
  foreach ($d in @($srPage.records | Where-Object { $_.status -eq 'DRAFT' })) {
    $ra = PutOp ('/api/sale/return/' + [int]$d.id + '/audit')
    if ($ra -and $ra.code -eq 200) { $draftFixed++ } else { Write-Output ('ERR 补审退货单 ' + $d.id + ' -> ' + $ra.code + ' ' + $ra.msg) }
  }
}
Write-Output ('补审历史 DRAFT 退货单=' + $draftFixed)

$soPage = GetP '/api/inventory/sale/page?pageSize=200'
$allSo = @()
if ($soPage) { $allSo = @($soPage.records) }
$audited = @($allSo | Where-Object { $_.status -eq 'AUDITED' })
Write-Output ('sale orders total=' + $allSo.Count + ' audited=' + $audited.Count + ' 已有退货单=' + $exists.Count)
$cand = @($audited | Where-Object { -not $exists[[int]$_.id] } | Select-Object -First 5)
Write-Output ('候选销售单=' + $cand.Count)

$ok = 0; $fail = 0; $msgs = @()
foreach ($so in $cand) {
  $det = GetP ('/api/inventory/sale/' + $so.id)
  $items = GetP ('/api/inventory/sale/' + $so.id + '/items')
  $it0 = @($items)[0]
  if (-not $det -or -not $it0) { continue }
  $qty = [math]::Max(1, [math]::Floor([decimal]$it0.quantity / 4))
  # 关键：qualityType = PENDING（待分类）—— 售后仓待整理库存只认这个品质
  $body = @{
    saleOrderId = [int]$so.id; saleOrderCode = $so.code; customerId = [int]$det.customerId
    warehouseId = $whAfs; returnDate = (Get-Date -Format 'yyyy-MM-dd'); status = 'DRAFT'
    remark = 'seed after-sale pending return'; lossAmount = 0
    items = @(@{ saleOrderItemId = [int]$it0.id; productId = [int]$it0.productId
                 qualityType = 'PENDING'; quantity = $qty; unitPrice = $it0.unitPrice
                 amount = [math]::Round($qty * [decimal]$it0.unitPrice, 2) })
  }
  $r = Post '/api/sale/return' $body
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 300
    $id = LatestDraftId '/api/sale/return/page?pageSize=300' 'records'
    if ($id -gt 0) {
      $ra = PutOp ('/api/sale/return/' + $id + '/audit')
      if ($ra -and $ra.code -eq 200) { $ok++ } else { $fail++; $msgs += ('SR audit ' + $id + ': ' + $ra.code + ' ' + $ra.msg) }
    } else { $fail++; $msgs += 'SR: 未找到待审核退货单' }
  } else { $fail++; $msgs += ('SR create so=' + $so.id + ': ' + $r.code + ' ' + $r.msg) }
}

# 换货单（可选）：换入仓=售后仓，换出仓=成品仓；换出为良品
$exOk = 0; $exFail = 0
$whFin = [int]@($ids.whFin)[0]
foreach ($so in @($audited | Select-Object -First 2)) {
  $det = GetP ('/api/inventory/sale/' + $so.id)
  $items = GetP ('/api/inventory/sale/' + $so.id + '/items')
  $it0 = @($items)[0]
  if (-not $det -or -not $it0) { continue }
  $body = @{
    saleOrderId = [int]$so.id; saleOrderCode = $so.code; customerId = [int]$det.customerId
    warehouseInId = $whAfs; warehouseOutId = $whFin; exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
    chargeFlag = 0; chargeType = 'DIFF'; chargeAmount = 0; chargeReason = ''
    remark = 'seed exchange'
    items = @(@{ saleOrderItemId = [int]$it0.id; productId = [int]$it0.productId
                 quantity = 1; unitPrice = $it0.unitPrice
                 outQuantity = 1; outUnitPrice = $it0.unitPrice; outQualityType = 'A'; remark = 'seed exchange' })
  }
  $r = Post '/api/sale/exchange' $body
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 300
    $id = LatestDraftId '/api/sale/exchange/page?pageSize=100' 'records'
    if ($id -gt 0) {
      $ra = PutOp ('/api/sale/exchange/' + $id + '/audit')
      if ($ra -and $ra.code -eq 200) { $exOk++ } else { $exFail++; $msgs += ('EX audit ' + $id + ': ' + $ra.code + ' ' + $ra.msg) }
    } else { $exFail++; $msgs += 'EX: 未找到待审核换货单' }
  } else { $exFail++; $msgs += ('EX create so=' + $so.id + ': ' + $r.code + ' ' + $r.msg) }
}

# 复核：售后仓待整理清单（$null 时不要用 @() 包，否则会得到 1 个空元素而误报）
$ds = GetP ('/api/inventory/return-sort/defect-stock?warehouseId=' + $whAfs)
$rows = @()
if ($null -ne $ds) { $rows = @($ds) }
Write-Output ('sale-return(PENDING) ok=' + $ok + ' fail=' + $fail)
Write-Output ('exchange ok=' + $exOk + ' fail=' + $exFail)
Write-Output ('defect-stock rows=' + $rows.Count + ' (供 seed3c 建退货整理单)')
if ($msgs.Count) { Write-Output '--- messages ---'; $msgs | Select-Object -First 8 | ForEach-Object { Write-Output $_ } }
