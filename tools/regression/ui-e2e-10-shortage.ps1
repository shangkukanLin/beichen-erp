# P5 - 加工订单「是否缺料」列 断言版（2026-09-18 重写：原版零断言 + 每次无脑补料）
# 场景（自造前置，可重跑、数据保留）：
#   1) 在「加工厂3」建一张加工单（该厂委外仓无料）-> 审核 -> 断言「是否缺料」= 是
#   2) 通过「物料其他出入库(入库)」给该厂委外仓补足 BOM 三种料 -> 审核
#   3) 复查：该单「是否缺料」变为 否；并断言委外仓库存增加
# 说明：BOM 三种料 = 玻璃(测试物料A1) / 排线(排线-MFTESTE2E1) / 驱动IC(测试物料A3)，均从库解析。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function PickOptionEndsWith([string]$text, [int]$wait = 0) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim().endsWith(O));if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
  return (EvalJs $js)
}
# 读「是否缺料」列：返回 [{t: 整行文本, s: 缺料值}]（按行文本匹配单号，避免列序假设）
$rowsJs = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return '[]';const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').trim());const ci=hs.findIndex(x=>x.indexOf('\u662f\u5426\u7f3a\u6599')>=0);const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>{const tds=[...r.querySelectorAll('td')];return {t:(r.innerText||'').replace(/\s+/g,' '),s:(ci>=0&&tds[ci]?(tds[ci].innerText||'').trim():'-')}}))})()"
function ShortageList() { Open '/outsource/order' 3000; return (EvalJs $rowsJs) }

# 挑一个「委外仓还没有任何物料」的加工厂（3/4/5），使 before/after 翻转每次都可复现
$facN = 0
foreach ($n in @(3, 4, 5)) {
  $stock = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE factory_id=(SELECT id FROM supplier WHERE name='" + (ZH 'val_factory') + $n + "') LIMIT 1)"))
  if ($stock -eq 0) { $facN = $n; break }
}
Write-Host ('[BASE] chosen factory = ' + $facN + ' (empty outsource warehouse)')
if ($facN -eq 0) { Write-Host 'NOTE: all candidate factories already stocked -> only column readability is verified'; $facN = 5 }

$wo = SqlOne ("SELECT code FROM outsource_order WHERE status='PRODUCING' AND factory_id=(SELECT id FROM supplier WHERE name='" + (ZH 'val_factory') + $facN + "') ORDER BY id DESC LIMIT 1")
if (-not $wo) {
  Step ('create work order on factory ' + $facN + ' (no material issued there)')
  Open '/outsource/order' 2600
  ClickBtn 'btn_new_order' | Out-Null
  Start-Sleep -Milliseconds 2200
  SelectLabelContains 'lbl_factory' ((ZH 'val_factory') + $facN) | Out-Null
  Start-Sleep -Milliseconds 900
  SelectLabelContains 'lbl_mfg_product' ((ZH 'val_proj') + 'E2E1') | Out-Null
  Start-Sleep -Milliseconds 900
  FillLabel 'lbl_qty' '100' | Out-Null
  FillLabel 'lbl_mfg_price' '20' | Out-Null
  Start-Sleep -Milliseconds 500
  ClickBtn 'btn_submit_confirm' | Out-Null
  Start-Sleep -Milliseconds 3000
  $wo = SqlOne ("SELECT code FROM outsource_order WHERE factory_id=(SELECT id FROM supplier WHERE name='" + (ZH 'val_factory') + $facN + "') ORDER BY id DESC LIMIT 1")
  Ok ([bool]$wo) ('factory ' + $facN + ' work order created (' + $wo + ')')
  if ($wo) {
    Open '/outsource/order' 2400
    $idx = [int](FindRow $wo)
    if ($idx -ge 0) {
      ClickRowBtnContains $idx (ZH 'btn_detail') | Out-Null
      Start-Sleep -Milliseconds 2600
      ClickBtn 'btn_audit' | Out-Null
      Start-Sleep -Milliseconds 1200
      ConfirmBox 1300 | Out-Null
      Start-Sleep -Milliseconds 2800
    }
    $st = SqlOne ("SELECT status FROM outsource_order WHERE code='" + $wo + "'")
    Ok ($st -eq 'PRODUCING') ('factory ' + $facN + ' work order PRODUCING (' + $st + ')')
  }
}

Step 'before: 是否缺料 should be 是 (no material in that factory warehouse)'
$before = ShortageList
Write-Host ('SHORTAGE before = ' + $before)
$rowB = (($before | ConvertFrom-Json) | Where-Object { $_.t -like ('*' + $wo + '*') })
Write-Host ('row = ' + $rowB.t)
$valBefore = ('' + $rowB.s).Trim()
Ok ($valBefore -eq (ZH 'txt_yes')) ('before: 缺料 = 是 (value=' + $valBefore + ')')

function Replenish([string]$matName, [string]$qtyText, [string]$whName, [string]$typeKey) {
  Open '/outsource/other-io/add' 3000
  ClearErrs | Out-Null
  Write-Host ('  wh: ' + (SelectLabelContains 'lbl_warehouse' $whName))
  Start-Sleep -Milliseconds 1000
  OpenRowSelect 0 0 | Out-Null
  Start-Sleep -Milliseconds 1200
  PickOptionContains (ZH $typeKey) | Out-Null
  Start-Sleep -Milliseconds 900
  OpenRowSelect 0 1 | Out-Null
  Start-Sleep -Milliseconds 1400
  $rp = PickOptionEndsWith $matName
  Write-Host ('  material ' + $matName + ' -> ' + $rp)
  Ok ($rp -match 'OK') ('replenish material picked (' + $matName + ')')
  Start-Sleep -Milliseconds 600
  SetRowInput 0 3 $qtyText | Out-Null
  Start-Sleep -Milliseconds 500
  Write-Host ('  save: ' + (ClickBtn 'btn_save'))
  Start-Sleep -Milliseconds 2800
  $c = SqlOne 'SELECT code FROM outsource_other_io ORDER BY id DESC LIMIT 1'
  Open '/outsource/other-io' 2600
  $idx = [int](FindRow $c)
  if ($idx -ge 0) {
    Write-Host ('  audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1300
    ConfirmBox 1100 | Out-Null
    Start-Sleep -Milliseconds 2600
  }
  return (SqlOne ("SELECT status FROM outsource_other_io WHERE code='" + $c + "'"))
}

Step ('replenish factory ' + $facN + ' warehouse with the 3 BOM materials')
$facWh = SqlOne ("SELECT warehouse_name FROM warehouse WHERE factory_id=(SELECT id FROM supplier WHERE name='" + (ZH 'val_factory') + $facN + "') LIMIT 1")
$auxStockBefore = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $facWh + "' LIMIT 1)"))
Write-Host ('factory ' + $facN + ' warehouse = ' + $facWh + ' stockBefore=' + $auxStockBefore)
Ok ([bool]$facWh) ('factory ' + $facN + ' warehouse resolved')
$m1 = (ZH 'val_material') + '1'
$m2 = SqlOne "SELECT material_name FROM outsource_material WHERE material_name LIKE '%MFTESTE2E1' LIMIT 1"
$m3 = (ZH 'val_material') + '3'
$s1 = Replenish $m1 '500' $facWh 'opt_mt_glass'
$s2 = Replenish $m2 '500' $facWh 'opt_mt_line'
$s3 = Replenish $m3 '500' $facWh 'opt_drv'
Write-Host ('replenish statuses: ' + $s1 + ' / ' + $s2 + ' / ' + $s3)
Ok ((($s1 -eq 'AUDITED') -and ($s2 -eq 'AUDITED') -and ($s3 -eq 'AUDITED'))) '3 replenish docs audited'

Step 'after: 是否缺料 should flip to 否'
$after = ShortageList
Write-Host ('SHORTAGE after = ' + $after)
$rowA = (($after | ConvertFrom-Json) | Where-Object { $_.t -like ('*' + $wo + '*') })
$valAfter = ('' + $rowA.s).Trim()
Write-Host ('row = ' + $rowA.t + ' ; value = ' + $valAfter)
$auxStockAfter = D (SqlOne ("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL AND warehouse_id=(SELECT id FROM warehouse WHERE warehouse_name='" + $facWh + "' LIMIT 1)"))
Write-Host ('factory ' + $facN + ' warehouse stock ' + $auxStockBefore + ' -> ' + $auxStockAfter)
Ok (($auxStockAfter -gt $auxStockBefore)) ('factory ' + $facN + ' warehouse stock grew (' + $auxStockBefore + ' -> ' + $auxStockAfter + ')')
Ok ($valAfter -eq (ZH 'txt_no')) ('after: 缺料 flipped to 否 (' + $valBefore + ' -> ' + $valAfter + ')')
Write-Host ('errs=' + (Errs))
Summary 'P5 shortage column (asserted)'
