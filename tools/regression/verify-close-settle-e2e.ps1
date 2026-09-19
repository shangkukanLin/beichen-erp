# 结单/反结单 + 清算一键退料 端到端验证（2026-09-16，全程可回滚；每次运行会新增"作废"单据作为痕迹）
#
# 用法（分阶段跑，便于用 SQL 观察中间态）：
#   powershell -File verify-close-settle-e2e.ps1 -Phase io-in              # ① 其他出入库给工厂仓补正库存（否则结单退料会因库存不足失败）
#   powershell -File verify-close-settle-e2e.ps1 -Phase close              # ② 保存结单报表 + 确认结单（良品退料=1 → 应生成 TRANSFER 调拨单）
#   powershell -File verify-close-settle-e2e.ps1 -Phase reopen             # ④ 反结单（订单回 PRODUCING、单据 CANCELLED、库存/流水对称回滚）
#   powershell -File verify-close-settle-e2e.ps1 -Phase io-rollback -IoId <其他出入库id>   # ⑥ 撤销补的库存
#   powershell -File verify-close-settle-e2e.ps1 -Phase settle -SupplierId <供应商id>      # 清算一键退料（应生成 TRANSFER）
#   powershell -File verify-close-settle-e2e.ps1 -Phase settle-rollback -DocId <收发单id>  # 清算单据反审核 + 作废
#   powershell -File verify-close-settle-e2e.ps1 -Phase settle-empty -SupplierId <无库存供应商id>  # 负向：应报"该供应商委外仓无可退物料"
#
# 前置（本脚本按开发库现状写死）：订单 16 处于 PRODUCING、其工厂仓 = 14、我方物料仓 = 29；清算用供应商 18（仓 24）。
param([string]$Phase, [int]$IoId = 0, [int]$DocId = 0, [int]$SupplierId = 0, [switch]$Force)
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
# 以库为准（原写死 16/14/29/12）：清库或换环境后写死的 id 必然失效
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
$ORD = [int](SqlOne "SELECT id FROM outsource_order WHERE status='PRODUCING' ORDER BY id LIMIT 1")
$WH_FACTORY = [int](SqlOne ("SELECT id FROM warehouse WHERE warehouse_category='OUTSOURCE' AND factory_id=(SELECT factory_id FROM outsource_order WHERE id=$ORD) ORDER BY id LIMIT 1"))
$WH_OURS = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY' ORDER BY id LIMIT 1")
$MAT = [int](SqlOne ("SELECT COALESCE(outsource_material_id,0) FROM dev_bom WHERE project_id=(SELECT id FROM dev_project ORDER BY id LIMIT 1) ORDER BY id LIMIT 1"))
Write-Host ('[SEED] order=' + $ORD + ' factoryWh=' + $WH_FACTORY + ' ourWh=' + $WH_OURS + ' material=' + $MAT + ' (resolved from DB; pass -Phase to act)')
if ($ORD -le 0 -or $WH_FACTORY -le 0 -or $WH_OURS -le 0) { Write-Host 'FAIL 无法从库解析必要 id（请检查是否有 PRODUCING 加工单/委外仓/自有物料仓）'; exit 1 }

function EvalJs($js) {
  $out = @(agent-browser eval $js) | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '✓ Done' -and $_ -notmatch '^\s*✗' }
  return ($out -join "`n").Trim()
}
function EnsureLogin() {
  agent-browser open "$base/dashboard" | Out-Null
  agent-browser wait 1200 | Out-Null
  if ((EvalJs "String(!!localStorage.getItem('beichen_erp_token'))") -notmatch 'true') {
    EvalJs "localStorage.clear(); 'x'" | Out-Null
    agent-browser open "$base/login" | Out-Null
    agent-browser wait 2500 | Out-Null
    $snap = (agent-browser snapshot -i) -join "`n"
    $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
    $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
    $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500 | Out-Null
  }
}
function Api($method, $path, $obj) {
  $bp = ''
  if ($obj) {
    $json = ConvertTo-Json -InputObject $obj -Compress -Depth 8
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
    $bp = ", body: new TextDecoder().decode(Uint8Array.from(atob('$b64'), c=>c.charCodeAt(0))), headers: { 'Content-Type':'application/json', Authorization: localStorage.getItem('beichen_erp_token') }"
  } else {
    $bp = ", headers: { Authorization: localStorage.getItem('beichen_erp_token') }"
  }
  $js = "fetch('$path',{method:'$method'$bp}).then(r=>r.text()).catch(e=>'ERR:'+e)"
  $raw = (EvalJs "(async()=>{return await $js})()") -replace '^"|"$', ''
  $raw = $raw.Replace('\"', '"')
  $m = [regex]::Match($raw, '"code"\s*:\s*(\d+)')
  $msg = [regex]::Match($raw, '"(?:message|msg)"\s*:\s*"([^"]*)"')
  return [pscustomobject]@{
    Code = $(if ($m.Success) { [int]$m.Groups[1].Value } else { -1 })
    Msg  = $(if ($msg.Success) { $msg.Groups[1].Value } else { '' })
    Raw  = $raw
  }
}

EnsureLogin
switch ($Phase) {
  'io-in' {
    $r = Api 'POST' '/api/outsource/other-io' @{
      warehouseId = $WH_FACTORY; ioType = 'IN'; ioDate = (Get-Date -Format 'yyyy-MM-dd')
      remark = '验收测试-补库存（可回滚）'
      items = @(@{ materialId = $MAT; unit = 'PCS'; quantity = 5; unit_price = 45 })
    }
    Write-Host ("① 建其他出入库(IN) -> " + $r.Code + " " + $r.Msg)
    $lst = Api 'GET' "/api/outsource/other-io/page?warehouseId=$WH_FACTORY&ioType=IN&pageNum=1&pageSize=1"
    $idM = [regex]::Match($lst.Raw, '"id"\s*:\s*(\d+)')
    if (-not $idM.Success) { Write-Host 'FAIL 取单据 id 失败'; exit 1 }
    $ioId = [int]$idM.Groups[1].Value
    $a = Api 'PUT' "/api/outsource/other-io/$ioId/audit"
    Write-Host ("① 审核其他出入库 id=$ioId -> " + $a.Code + " " + $a.Msg)
    Write-Host ("IO_ID=" + $ioId)
  }
  'close' {
    $rep = Api 'GET' "/api/outsource/order/$ORD/close-report"
    # 构造 items：本单 BOM 物料（12/17/20），给物料 12 填 良品退料=1
    $items = @(
      @{ materialId = 12; materialTypeId = $null; unit = 'PCS'; returnedQuantity = 0; goodReturnQty = 1; defectReturnQty = 0; shippedQuantity = 0; targetYieldRate = 100; actualYieldRate = 0; yieldLoss = 0; excessLossQty = 0; factoryRetainQty = 0; missingQty = 0; unitPrice = 45; remark = 'QA-端到端测试' },
      @{ materialId = 20; materialTypeId = $null; unit = 'PCS'; returnedQuantity = 0; goodReturnQty = 0; defectReturnQty = 0; shippedQuantity = 0; targetYieldRate = 100; actualYieldRate = 0; yieldLoss = 0; excessLossQty = 0; factoryRetainQty = 0; missingQty = 0; unitPrice = 63.2196; remark = '' },
      @{ materialId = 17; materialTypeId = $null; unit = 'PCS'; returnedQuantity = 0; goodReturnQty = 0; defectReturnQty = 0; shippedQuantity = 0; targetYieldRate = 100; actualYieldRate = 0; yieldLoss = 0; excessLossQty = 0; factoryRetainQty = 0; missingQty = 0; unitPrice = 150; remark = '' }
    )
    $sd = Api 'PUT' "/api/outsource/order/$ORD/close-report" @{ items = $items; remark = 'QA-端到端测试' }
    Write-Host ("② 保存结单报表 -> " + $sd.Code + " " + $sd.Msg)
    # -Force：强制退料（账面负库存时按"强制出库"口径扣减）；不加则走严格口径（应报"库存不足"）
    $cf = Api 'POST' "/api/outsource/order/$ORD/close-report/confirm" @{ returnWarehouseId = $WH_OURS; force = [bool]$Force }
    Write-Host ("② 确认结单" + $(if ($Force) { "(强制退料)" } else { "(严格口径)" }) + " -> " + $cf.Code + " " + $cf.Msg)
  }
  'reopen' {
    $rp = Api 'POST' "/api/outsource/order/$ORD/close-report/reopen" $null
    Write-Host ("④ 反结单 -> " + $rp.Code + " " + $rp.Msg)
  }
  'io-rollback' {
    if ($IoId -le 0) { Write-Host 'FAIL 需要 -IoId'; exit 1 }
    $u = Api 'PUT' "/api/outsource/other-io/$IoId/un-audit"
    Write-Host ("⑥ 其他出入库反审核 id=$IoId -> " + $u.Code + " " + $u.Msg)
  }
  'settle' {
    # 清算一键退料：某供应商所有委外仓正库存 → 退回我方仓（应生成 TRANSFER 调拨单）
    $r = Api 'POST' "/api/supplier-settlement/$SupplierId/return-materials" @{ toWarehouseId = $WH_OURS }
    Write-Host ("A 清算一键退料 supplier=$SupplierId -> " + $r.Code + " " + $r.Msg)
    $lst = Api 'GET' "/api/outsource/delivery/page?deliveryType=TRANSFER&pageNum=1&pageSize=1"
    $idM = [regex]::Match($lst.Raw, '"id"\s*:\s*(\d+)')
    if ($idM.Success) { Write-Host ("SETTLE_DOC_ID=" + $idM.Groups[1].Value) } else { Write-Host 'FAIL 取清算单据 id 失败'; exit 1 }
  }
  'settle-rollback' {
    if ($DocId -le 0) { Write-Host 'FAIL 需要 -DocId'; exit 1 }
    $u = Api 'PUT' "/api/outsource/delivery/$DocId/un-audit"
    Write-Host ("B 清算单据反审核 id=$DocId -> " + $u.Code + " " + $u.Msg)
    $c = Api 'PUT' "/api/outsource/delivery/$DocId/cancel"
    Write-Host ("B 清算单据作废 id=$DocId -> " + $c.Code + " " + $c.Msg)
  }
  'settle-empty' {
    # 负向：无库存的供应商应报"无可退物料"
    $r = Api 'POST' "/api/supplier-settlement/$SupplierId/return-materials" @{ toWarehouseId = $WH_OURS }
    Write-Host ("C 无库存供应商退料 supplier=$SupplierId -> " + $r.Code + " " + $r.Msg)
  }
  'ui' {
    # 前端（只读，不提交）：结单页「工厂仓账面」列 + 「账面不足」标识 + 强制退料弹窗
    agent-browser open "$base/outsource/order/close/$ORD" | Out-Null
    agent-browser wait 3500 | Out-Null
    Write-Host ("① 含「工厂仓账面」列 -> " + (EvalJs "String(document.body.innerText.indexOf('工厂仓账面') >= 0)"))
    Write-Host ("② 含「账面不足」标识 -> " + (EvalJs "String(document.body.innerText.indexOf('账面不足') >= 0)"))
    Write-Host ("③ 首行 = " + (EvalJs "document.querySelectorAll('.el-table__body tbody tr')[0].innerText.split('\t').join(' | ')"))
    Write-Host "---- SNAPSHOT（供取 ref）----"
    (agent-browser snapshot -i) | ForEach-Object { "$_" } | Where-Object { $_ -match 'ref=|button|textbox' } | Select-Object -First 25 | Write-Host
  }
  'ui-confirm' {
    # EnsureLogin 会停在首页，这里必须显式回到结单页
    agent-browser open "$base/outsource/order/close/$ORD" | Out-Null
    agent-browser wait 3500 | Out-Null
    # 先选中「退回仓库」（RemoteSelect = el-select，懒加载），否则前端会先提示"请选择退回仓库"
    $r1 = EvalJs "(()=>{const w=document.querySelector('.el-card .el-select');if(!w)return 'no-select';const t=w.querySelector('.el-select__wrapper')||w.querySelector('input');t.dispatchEvent(new MouseEvent('click',{bubbles:true}));t.click();return 'opened';})()"
    Write-Host ("打开退回仓库下拉 -> " + $r1)
    agent-browser wait 2500 | Out-Null
    $r2 = EvalJs "(()=>{const it=document.querySelector('.el-select-dropdown__item');if(!it)return 'no-option';const label=it.innerText.trim();it.click();return label;})()"
    Write-Host ("选中仓库 -> " + $r2)
    agent-browser wait 1200 | Out-Null
    # 点「确认结单」：应弹出强制退料弹窗（不提交，点取消）
    $hit = EvalJs "(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='确认结单');if(b){b.click();return 'clicked';}return 'not-found';})()"
    Write-Host ("点确认结单 -> " + $hit)
    agent-browser wait 1500 | Out-Null
    Write-Host ("弹窗标题存在 -> " + (EvalJs "String(document.body.innerText.indexOf('存在退料超出工厂仓账面库存的物料') >= 0)"))
    Write-Host ("勾选项存在 -> " + (EvalJs "String(document.body.innerText.indexOf('强制退料') >= 0)"))
    Write-Host ("弹窗明细 = " + (EvalJs "(document.querySelector('.el-dialog__body .el-table__body tbody tr')||{innerText:''}).innerText.split('\t').join(' | ')"))
    $ok = EvalJs "(()=>{const b=[...document.querySelectorAll('.el-dialog__footer button')].map(x=>x.innerText.trim());return b.join(',');})()"
    Write-Host ("弹窗按钮 = " + $ok)
    EvalJs "(()=>{const b=[...document.querySelectorAll('.el-dialog__footer button')].find(x=>x.innerText.trim()==='取消');if(b){b.click();return 'cancelled';}return 'no-cancel';})()" | Write-Host
  }
  'ui-qty' {
    # 数量整数化（2026-09-16）：库存/委外仓库页面数量应无小数、"工厂仓账面"与结单页数量均为整数
    function ChkQty($url, $name) {
      agent-browser open $url | Out-Null
      agent-browser wait 3200 | Out-Null
      $txt = EvalJs "(()=>{const t=[...document.querySelectorAll('.el-table__body tbody tr')].map(r=>r.innerText.split('\t').join('|')).join(' ; ');return t;})()"
      # 只判"3 位以上小数"：数量列改整数前是 4 位小数（如 -0.9916）；良率/单价是 2 位小数，属正常
      $bad = [regex]::Matches($txt, '\d+\.\d{3,}') | ForEach-Object { $_.Value } | Select-Object -Unique
      Write-Host ("[" + $name + "] 首行样本 = " + $(if ($txt.Length -gt 160) { $txt.Substring(0, 160) } else { $txt }))
      if ($bad.Count -eq 0) { Write-Host ("PASS " + $name + " 数量列无小数") } else { Write-Host ("FAIL " + $name + " 仍出现小数：" + ($bad -join ',')) ; $global:fail = $global:fail + 1 }
    }
    $global:fail = 0
    ChkQty "$base/inventory/product-stock" '库存'
    ChkQty "$base/outsource/warehouse" '委外仓库'
    ChkQty "$base/outsource/order/close/$ORD" '结单报表'
    if ($global:fail -eq 0) { Write-Host 'RESULT PASS 数量已统一为整数' } else { Write-Host ('RESULT FAIL ' + $global:fail); exit 1 }
  }
  'ui-wh' {
    # 自有物料仓：点「新增」后仓型下拉必须显示中文 label（不得显示枚举 code）
    agent-browser open "$base/outsource/material-warehouse" | Out-Null
    agent-browser wait 3200 | Out-Null
    Write-Host ("列表仓型 = " + (EvalJs "(()=>{const c=document.querySelectorAll('.el-table__body tbody tr')[0];return c?c.innerText.split('\t').join('|'):'';})()"))
    Write-Host ("页面文本 = " + ((EvalJs "document.body.innerText.split('\n').filter(x=>x.trim()).slice(0,14).join(' | ')") -replace '"', ''))
    $opened = EvalJs "(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='新增');if(b){b.click();return 'clicked';}return 'not-found';})()"
    Write-Host ("点新增 -> " + $opened)
    agent-browser wait 1200 | Out-Null
    $opt = (EvalJs "(()=>{const d=document.querySelector('.el-dialog');if(!d)return 'no-dialog';const t=d.innerText;return t.indexOf('AUXILIARY') >= 0 ? ('CODE-SHOWN:' + t.split('\n').join('|')) : ('CN-OK:' + t.split('\n').join('|'));})()") -replace '"', ''
    Write-Host ("新增弹窗 = " + $opt)
    if ($opt -like 'CN-OK*') { Write-Host 'PASS 仓型显示中文（无枚举 code）' } else { Write-Host 'FAIL 仓型仍显示枚举 code'; $global:fail = 1 }
    EvalJs "(()=>{const b=[...document.querySelectorAll('.el-dialog__footer button')].find(x=>x.innerText.trim()==='取消');if(b)b.click();return 'closed';})()" | Write-Host
    if ($global:fail -eq 0) { Write-Host 'RESULT PASS 自有物料仓新增弹窗仓型为中文' }
  }
  default { Write-Host 'usage: -Phase io-in|close|reopen|io-rollback|settle|settle-rollback|settle-empty|ui|ui-confirm|ui-qty|ui-wh'; exit 1 }
}
