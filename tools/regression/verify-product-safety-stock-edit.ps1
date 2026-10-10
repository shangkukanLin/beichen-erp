# 成品库存详情列表 · 安全库存「点一下就能改」守卫（2026-10-09 用户需求，报告 §7.32）。
#
# 用户口径：「成品库存详情列表的安全库存，可以直接点击安全库存，弹框修改该产品的库存。」
# 本守卫钉五件事：
#   ① UI：列表的安全库存列渲染成**可点**元素（带 .safety-edit class，光标手型 + 虚线下划线）；
#   ② 点它 ⇒ 弹框出现，且弹框里写明"**产品级**（所有仓库共用）"这条粒度提醒（浏览器内比对 ✓）；
#   ③ 点它**不会**把整行点击吞掉走详情页 ✗（列上的 @click.stop 只阻止冒泡到 @row-click，页面 URL 不变 ✓）；
#   ④ 改值 + 保存 ⇒ **DB 真的落** `product.safety_stock`，且列表刷新后回显新值；
#   ⑤ 后端护栏：直调 API 传 -1 / 2.5 ⇒ 拒绝且 DB 不变（负例）；传合法值 ⇒ 通过。
# 收尾把安全库存改回原值（夹具原样归还 ✓）。
#
# ⚠️ 坑预防：① 本页整行可点（@row-click 跳详情）⇒ 断言"点安全库存不跳页"；② 安全库存是**产品级**字段
#   （product 表），不是 warehouse_stock；③ 中文不读回 PowerShell（GBK 会解坏）⇒ 标签走 base64、在浏览器内比。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }

# "产品级" 的 UTF-8 base64（ASCII 常量，避免把中文写进 .ps1 —— PS 5.1 会按 ANSI 读，中文源码会烂）
# 生成方式：python -c "import base64;print(base64.b64encode('产品级'.encode()).decode())"
$HINT_B64 = '5Lqn5ZOB57qn'

# 令牌自愈（2026-10-09）：lib 的 EnsureLogin 只在**缺 token** 时才登录 ⇒ 后端重启后遗留的"坏 token"
# 会被跳过，页面全被重定向到 /login（症状：innerText 只剩一百多字符、断言全红 ✗）。先清掉再登录。
Open '/dashboard' 2400
EvalJs "localStorage.removeItem('beichen_erp_token'); localStorage.removeItem('beichen_erp_user'); 'cleared'" | Out-Null
Start-Sleep -Milliseconds 500
EnsureLogin
WatchErrors

Step 'UI: the safety-stock cell is clickable (and shows the product-level hint in a dialog)'
Open '/inventory/product-stock' 3600
# 2026-10-10：首列已按用户口径合并为「SKU | 名称」⇒ 必须**切出分隔符前的 SKU** 再拿去查库 ✗
#   （否则 `product WHERE sku='SKU-000001 | 名称'` 查不到 ⇒ 假红 ✓）。
$listJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis);const cells=[...t.querySelectorAll('.safety-edit')].filter(vis);let idx=-1,sku='';for(let i=0;i<rs.length;i++){const raw=(rs[i].querySelectorAll('td')[0]||{innerText:''}).innerText.trim();const c=raw.split('|')[0].trim();const has=cells.some(x=>rs[i].contains(x));if(has&&c){idx=i;sku=c;break}}return JSON.stringify({rows:rs.length,editCells:cells.length,idx:idx,sku:sku})})()"
$list = EvalJs $listJs
Write-Host ('  list probe >> ' + $list)
$L = $list | ConvertFrom-Json
Ok ($L.editCells -ge 1) 'the safety-stock column renders clickable cells (.safety-edit) - it used to be a plain read-only span'
# 布局不回退（这是本次最容易踩的坑：往列里塞控件会改列宽/撑出横向滚动 ✗；因此实现刻意选了"点击 → 弹框"，
# 列宽与改前逐字一致 ✓）：本页全是 min-width 列（自动铺满）⇒ scrollWidth 必须等于 clientWidth。
# 说明：全站布局扫描（scan-table-overflow / scan-col-truncation）遍历所有列表页，本机跑了 10 分钟没跑完 ✗，
#      故这里对**被改动的那一页**做同口径的定向断言 ✓（证据强度相当，代价小得多 ✓）。
$layJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return 'NOTABLE';const body=t.querySelector('.el-table__body-wrapper')||t;const head=t.querySelector('.el-table__header-wrapper')||t;return JSON.stringify({bodyOverflow:body.scrollWidth-body.clientWidth,headOverflow:head.scrollWidth-head.clientWidth})})()"
$lay = EvalJs $layJs
Write-Host ('  layout probe >> ' + $lay)
$LY = $lay | ConvertFrom-Json
Ok ($LY.bodyOverflow -le 0 -and $LY.headOverflow -le 0) 'the list still fits horizontally (the clickable cells introduced no column-width change)'
$sku = [string]$L.sku
$idx = [int]$L.idx
Ok ($idx -ge 0 -and $sku -ne '') ('found a row to work with (row ' + $idx + ', sku ' + $sku + ')')
if ($idx -lt 0) { Skip 'no row with a SKU and a clickable safety-stock cell -> cannot continue'; Summary 'product safety-stock inline edit'; exit 0 }

# ⚠️ 必须 @( ) 强制成数组：SqlLines 只回一行时，PowerShell 会把**单元素数组解包成字符串** ✗
#    ⇒ $prow[0] 会变成"第一个字符"（'147\t0' 的首字符 = '1' ✗），于是后面拿着 id=1 去查/去调接口，
#    表现成"接口全 500、产品不存在"（2026-10-09 本守卫实测踩到，本仓此前已记过这条教训 ✗）。
$prow = @(SqlLines ("SELECT id, IFNULL(safety_stock,0) FROM product WHERE sku='" + $sku + "' LIMIT 1"))
# ⚠️ 变量绝不能叫 $pid —— 它是 PowerShell 的**只读自动变量**（赋值被静默拒绝，后续读到的是进程号 ⇒ 断言全乱）
$prodId = ''
$orig = [decimal]0
if ($prow.Count -ge 1) {
  $parts = @($prow[0] -split "`t")
  $prodId = $parts[0].Trim()
  if ($parts.Count -ge 2 -and $parts[1].Trim() -ne '') { $orig = [decimal]$parts[1].Trim() }
}
Write-Host ('  fixture: product id=' + $prodId + ' sku=' + $sku + ' safety_stock=' + $orig)
Ok ($prodId -ne '') ('resolved the product row in the DB (id=' + $prodId + ')')

$urlBefore = EvalJs "location.pathname"
$clickJs = "(()=>{const vis=e=>e.getClientRects().length>0;const cells=[...document.querySelectorAll('.safety-edit')].filter(vis);if(cells.length<=$idx)return 'NOCELL:'+cells.length;cells[$idx].click();return 'OK'})()"
Write-Host ('  click the safety-stock cell: ' + (EvalJs $clickJs))
Start-Sleep -Milliseconds 1200
$dlgJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$HINT_B64');const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODIALOG';const t=d.innerText||'';const inp=d.querySelector('.el-input-number input');return JSON.stringify({hasHint:t.indexOf(L)>=0,val:inp?String(inp.value):'NOINPUT'})})()"
$dlg = EvalJs $dlgJs
Write-Host ('  dialog probe >> ' + $dlg)
$D = $dlg | ConvertFrom-Json
Ok ($D.val -ne 'NOINPUT') 'the click opened a dialog with an editable number input'
Ok ($D.hasHint -eq $true) 'the dialog tells the user this value is PRODUCT-level (shared by all warehouses)'
Ok ((EvalJs "location.pathname") -eq $urlBefore) 'clicking the cell did NOT navigate to the row detail page (@click.stop works)'

Step 'EDIT: set it to 7 and save -> the DB must change and the list must reflect it'
$v7 = B64 '7'
$setJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v7');const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODIALOG';const el=d.querySelector('.el-input-number input');if(!el)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
Write-Host ('  type 7: ' + (EvalJs $setJs))
Start-Sleep -Milliseconds 400
$saveJs = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODIALOG';const b=[...d.querySelectorAll('.el-dialog__footer button')].filter(vis);if(!b.length)return 'NOBTN';b[b.length-1].click();return 'OK'})()"
Write-Host ('  click dialog save: ' + (EvalJs $saveJs))
Start-Sleep -Milliseconds 2600
# ⚠️ 这里也必须用 $prodId：写成 $pid 会拿去 PowerShell 的**进程号**当产品 id（只读自动变量，
#    赋值被静默拒绝），查询恒空 ⇒ 假红（2026-10-09 本守卫实测踩到过一次 ✓ 已记进 reference_local_env）
$dbVal = SqlOne ("SELECT IFNULL(safety_stock,0) FROM product WHERE id=" + $prodId)
Write-Host ('  product.safety_stock now = [' + $dbVal + ']')
Ok ([decimal]$dbVal -eq 7) 'the new safety stock really reached the DB (product.safety_stock)'
$backJs = "(()=>{const vis=e=>e.getClientRects().length>0;const cells=[...document.querySelectorAll('.safety-edit')].filter(vis);if(cells.length<=$idx)return 'NOCELL';return String((cells[$idx].innerText||'').trim())})()"
$shown = EvalJs $backJs
Write-Host ('  value shown in the list after save = ' + $shown)
Ok ($shown -eq '7') 'and the list shows the new value (the page reloaded itself)'

Step 'API GUARD: -1 / 2.5 must be refused, a legal value must pass'
$tok = (EvalJs "localStorage.getItem('beichen_erp_token')") -replace '"', ''
function Api([string]$method, [string]$path, [string]$json) {
  $h = @{ Authorization = $tok }
  try {
    if ($json -eq '') { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method $method -Headers $h -TimeoutSec 30 }
    return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 30
  } catch { return $null }
}
$neg = Api 'PUT' ('/api/product/' + $prodId + '/safety-stock') '{"safetyStock":-1}'
Write-Host ('  -1 -> ' + $(if ($null -eq $neg) { 'transport-error' } else { 'code=' + $neg.code + ' msg=' + $neg.msg }))
Ok ($null -eq $neg -or $neg.code -ne 200) 'a negative safety stock is refused'
$frac = Api 'PUT' ('/api/product/' + $prodId + '/safety-stock') '{"safetyStock":2.5}'
Write-Host ('  2.5 -> ' + $(if ($null -eq $frac) { 'transport-error' } else { 'code=' + $frac.code + ' msg=' + $frac.msg }))
Ok ($null -eq $frac -or $frac.code -ne 200) 'a fractional safety stock is refused (the column and the product form are both integer)'
Ok ([decimal](SqlOne ("SELECT IFNULL(safety_stock,0) FROM product WHERE id=" + $prodId)) -eq 7) 'neither refused value reached the DB'
$okv = Api 'PUT' ('/api/product/' + $prodId + '/safety-stock') '{"safetyStock":3}'
Ok ($okv.code -eq 200 -and [decimal](SqlOne ("SELECT IFNULL(safety_stock,0) FROM product WHERE id=" + $prodId)) -eq 3) 'a legal value passes the minimal endpoint'

Step 'CLEANUP: restore the original safety stock'
$rb = Api 'PUT' ('/api/product/' + $prodId + '/safety-stock') ('{"safetyStock":' + $orig + '}')
$back = SqlOne ("SELECT IFNULL(safety_stock,0) FROM product WHERE id=" + $prodId)
Write-Host ('  restore -> code=' + $rb.code + ' ; db=' + $back)
Ok ($rb.code -eq 200 -and [decimal]$back -eq $orig) 'the original safety stock is back (fixture left as found)'

Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'

Summary 'product stock list: safety stock editable in place (dialog) + backend guard'
