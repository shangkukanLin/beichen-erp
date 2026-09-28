# Detail-page RENDER smoke guard (2026-09-27).
#
# WHY: a setup-time exception in a `.vue` blanks the WHOLE app -- `#app` innerHTML = 0, no vite error overlay,
#   the module still compiles (HTTP 200) and the route still resolves. Compile/HTTP-only guards cannot see it.
#   The real incident: `watch(computedA, cb)` was placed ABOVE the consts that computedA depends on; `watch()`
#   evaluates the source ONCE during setup (even without `immediate`) -> "Cannot access 'x' before initialization"
#   (TDZ) -> blank page on every deep link to the detail page.
#
# WHAT IT CHECKS (representative detail pages, ids resolved from the DB):
#   1. `#app` actually rendered (innerHTML length > 1000; a blank app is 0)
#   2. the console logged no "Unhandled error during execution of setup function"
# PURE ASCII.
$ErrorActionPreference = 'Continue'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = @($o | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '' })
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin | Out-Null

$cases = @(
  @{ note = 'outsource return-order REPAIR detail'; expect = '成品维修退货详情'; url = ('/outsource/return-order/detail/' + (SqlOne "SELECT id FROM outsource_return_order WHERE return_type='REPAIR' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'outsource return-order DEFECT detail'; expect = '委外加工退货详情'; url = ('/outsource/return-order/detail/' + (SqlOne "SELECT id FROM outsource_return_order WHERE return_type='DEFECT' ORDER BY id DESC LIMIT 1")) },
  # 2026-09-28 三态：维修返回详情（术语由「物料维修退货」统一为「维修返回」）+ **新增订单退料详情**
  @{ note = 'material-return REPAIR detail'; expect = '维修返回详情'; card = $true; url = ('/outsource/material-return/detail/' + (SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REPAIR' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'material-return ORDER detail'; expect = '订单退料详情'; card = $true; url = ('/outsource/material-return/detail/' + (SqlOne "SELECT id FROM outsource_material_return WHERE return_type='ORDER' ORDER BY id DESC LIMIT 1")) },
  # 2026-09-28（用户口径「无单退料和物料维修退货的标题也没有对齐」）：REFUND 详情按**是否挂物料订单**三态命名 ——
  #   挂订单(MRH-)=关联退料详情 / 无单(MRW-)=无单退料详情 ⇒ 用例由 1 条拆成 2 条（原先共用旧名「委外物料退货详情」）。
  @{ note = 'material-return REFUND detail (linked to order)'; expect = '关联退料详情'; card = $true; url = ('/outsource/material-return/detail/' + (SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REFUND' AND material_order_id IS NOT NULL ORDER BY id DESC LIMIT 1")) },
  @{ note = 'material-return REFUND detail (no order)'; expect = '无单退料详情'; card = $true; url = ('/outsource/material-return/detail/' + (SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REFUND' AND material_order_id IS NULL ORDER BY id DESC LIMIT 1")) },
  # 2026-09-27：加工退货记录详情页现在承载「登记返回」（加工返回单叶子已下线）⇒ 必须纳入渲染守卫
  @{ note = 'defect-return detail (登记返回 落点)'; expect = '加工退货详情'; url = ('/outsource/defect-return/detail/' + (SqlOne "SELECT id FROM outsource_order_delivery WHERE delivery_type='DEFECT_RETURN' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'outsource order detail (untouched control)'; expect = '委外加工单详情'; url = ('/outsource/order/detail/' + (SqlOne "SELECT id FROM outsource_order ORDER BY id DESC LIMIT 1")) },
  # 2026-09-28（用户口径「页头标题也要跟随类型/入口」）：**新增页**同样受"三处一致"家规约束 ——
  #   物料退货新增页由**两个叶子**共用（关联退料 / 无单退料；2026-09-28 起维修返回不再独占叶子），
  #   加工退货（拆分还料）页被两个入口共用（成品收货 = meta.title，关联退货台账 = ?from=return-order）。
  #   原先页头吃 meta.title ⇒ 只有页签跟了（用户实测报回）。
  @{ note = 'material-return add (linked leaf)'; expect = '新增关联退料'; card = $true; url = '/outsource/material-return/add?returnType=REFUND&linked=WITH_ORDER' },
  @{ note = 'material-return add (unlinked leaf)'; expect = '新增无单退料'; card = $true; url = '/outsource/material-return/add?returnType=REFUND&linked=WITHOUT_ORDER' },
  # 兜底分支（无叶子参数 + 类型=维修返回：老书签/深链）⇒ 标题跟类型 = 新增维修返回
  @{ note = 'material-return add (repair type, no leaf)'; expect = '新增维修返回'; card = $true; url = '/outsource/material-return/add?returnType=REPAIR' },
  @{ note = 'return-defect add (from ledger)'; expect = '新增关联加工退货'; card = $true; url = ('/outsource/order/delivery/return-defect/' + (SqlOne "SELECT id FROM outsource_order ORDER BY id DESC LIMIT 1") + '?from=return-order') },
  # 对照：成品收货入口不带 from ⇒ 四处仍是路由 meta.title（守住"不被顺手改掉"）
  @{ note = 'return-defect add (from receipt, meta.title control)'; expect = '加工退货（拆分还料）'; card = $true; url = ('/outsource/order/delivery/return-defect/' + (SqlOne "SELECT id FROM outsource_order ORDER BY id DESC LIMIT 1")) }
)

foreach ($c in $cases) {
  Step $c.note
  if ($c.url -match '/0$' -or $c.url -match '/$') { Info ('no document id found for ' + $c.url + ' -> skipped (coverage gap)'); continue }
  agent-browser console --clear | Out-Null
  Open $c.url 4200
  $len = [int](EvalJs "String((document.querySelector('#app')||{innerHTML:''}).innerHTML.length)")
  $hdr = EvalJs "(()=>{const e=document.querySelector('.page-header__title');return e?(e.innerText||'').trim():'NONE'})()"
  $tab = EvalJs "(()=>{const a=document.querySelector('.tab-item.active .tab-label');return a?(a.innerText||'').trim():'NONE'})()"
  $doc = EvalJs "String(document.title)"
  # 卡片标题（2026-09-28）：add/detail 两张页面都要求"卡片标题与页面名同名"（用户口径「页面里的标题也要对齐」）
  $card = EvalJs "(()=>{const e=document.querySelector('.el-card__header');return e?(e.innerText||'').trim():'NONE'})()"
  $con = (agent-browser console 2>&1 | ForEach-Object { "$_" }) -join ' '
  Write-Host ('  url=' + $c.url + ' appHtml=' + $len + ' pageHeader=' + $hdr + ' tab=' + $tab + ' card=' + $card + ' docTitle=' + $doc)
  Ok ($len -gt 1000) ($c.note + ': the app rendered (appHtml > 1000)')
  Ok (-not ($con -match 'execution of setup function')) ($c.note + ': no Vue setup error in console')
  # 2026-09-27: 页面名必须**跟随单据类型**（页头 / 顶部页签 / 浏览器标签页 三处一致）
  Ok ($hdr -eq $c.expect) ($c.note + ': page header = ' + $c.expect + ' (got ' + $hdr + ')')
  Ok ($tab -eq $c.expect) ($c.note + ': tab title = ' + $c.expect + ' (got ' + $tab + ')')
  Ok ($doc.StartsWith($c.expect)) ($c.note + ': browser tab title starts with ' + $c.expect + ' (got ' + $doc + ')')
  # 可选卡片断言（2026-09-28）：该页第一张卡片的标题也必须与页面名同名
  if ($c.card) { Ok ($card -eq $c.expect) ($c.note + ': card header = ' + $c.expect + ' (got ' + $card + ')') }
}

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-detail-render (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
