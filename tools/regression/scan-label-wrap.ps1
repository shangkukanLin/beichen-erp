# Form-label line-wrap guard (established 2026-09-28). ASCII ONLY - Chinese comes from base64.
#
# WHY: user reported that on the "new outsource return" page the label 送修出库仓 wrapped and the
# last character (仓) dropped to a second line. Root cause is a WIDTH budget, not the wording:
#   * tokens.css has two label-width steps - default 90px, lg 104px
#   * any label with 5+ characters (+ required star + colon) needs the lg step
#   * page.css has a global fallback `.page-shell .el-form-item__label { width: 90px }`, so a bare
#     <el-form-item> without an <el-form label-width> wrapper is silently clamped to 90px too
#   * `size="small"` does NOT shrink the label font (.el-form-item__label stays 14px site-wide)
#
# HOW IT DETECTS: number of line boxes of the label TEXT via Range.getClientRects() > 1.
# Do NOT use the element height - el-form-item__label height is pinned to the control height, so a
# wrapped label keeps h == single-line height while the overflow text visually sits on a second line.
#
# COVERAGE: (1) every static route in router/index.ts, (2) 15 main document detail/edit pages
# (ids read from the DB; 2026-10-02 补入 sale/exchange、purchase/exchange、purchase/return 三个详情页 ——
# 用户报的「换入仓(售后)」折行正是发生在 sale/exchange/detail，而它此前不在覆盖内),
# (3) the query-parameter entry pages that a plain route scan cannot reach,
# (4) the first "new/create" dialog on every page (opened, scanned, cancelled - read only, no writes).

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin | Out-Null

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" } | Select-Object -First 1)
  )
}
function FromB64([string]$s) { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) }

# --- targets ---------------------------------------------------------------------------------
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent   # beichen-erp
$router = Get-Content (Join-Path $root 'beichen-erp-web\src\router\index.ts') -Raw
$targets = @()
# ⚠️ 2026-10-10 修：原正则用 `[^\r\n]*?` ⇒ **不能跨行** ✗，而有些路由的 `path:` 与 `component:` 是
#    分两行写的（如 dev/project/add）⇒ 这些页**根本没进扫描目标**，扫描却仍报 PASS（"扫不到=通过"的假绿 ✗，
#    用户报的「玻璃分辨率」折行就在这类页上）。改为允许跨行但**限定跨度**（{0,400} 惰性 ⇒ 不会误吃到下一条路由）。
foreach ($m in [regex]::Matches($router, "path:\s*'([^']+)'[\s\S]{0,400}?component:")) {
  $p = $m.Groups[1].Value
  if ($p -match ':' -or $p -eq '') { continue }
  $targets += ('/' + $p)
}
$oid = SqlOne "SELECT id FROM outsource_order ORDER BY id DESC LIMIT 1"
$moid = SqlOne "SELECT id FROM outsource_material_order ORDER BY id DESC LIMIT 1"
$mrL = SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REFUND' AND material_order_id IS NOT NULL ORDER BY id DESC LIMIT 1"
$mrN = SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REFUND' AND material_order_id IS NULL ORDER BY id DESC LIMIT 1"
$mrR = SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REPAIR' ORDER BY id DESC LIMIT 1"
$roR = SqlOne "SELECT id FROM outsource_return_order WHERE return_type='REPAIR' ORDER BY id DESC LIMIT 1"
$dr = SqlOne "SELECT id FROM outsource_order_delivery WHERE delivery_type='DEFECT_RETURN' ORDER BY id DESC LIMIT 1"
$del = SqlOne "SELECT id FROM outsource_order_delivery ORDER BY id DESC LIMIT 1"
$so = SqlOne "SELECT id FROM sale_order ORDER BY id DESC LIMIT 1"
$sr = SqlOne "SELECT id FROM sale_return ORDER BY id DESC LIMIT 1"
$prj = SqlOne "SELECT id FROM dev_project ORDER BY id DESC LIMIT 1"
# 2026-10-02 补：退换货三个详情页（用户正是在 sale/exchange/detail 上报「换入仓(售后)」标签折行，
# 此前这三个页面不在守卫范围内 ⇒ 漏检）。它们的路由带 :id，静态路由扫描覆盖不到，必须单列。
$sx = SqlOne "SELECT id FROM sale_exchange ORDER BY id DESC LIMIT 1"
$px = SqlOne "SELECT id FROM purchase_exchange ORDER BY id DESC LIMIT 1"
$pr = SqlOne "SELECT id FROM purchase_return ORDER BY id DESC LIMIT 1"
$detail = @(
  ('/outsource/order/detail/' + $oid), ('/outsource/order/delivery/' + $oid), ('/outsource/material-order/detail/' + $moid),
  ('/outsource/return-order/detail/' + $roR), ('/outsource/defect-return/detail/' + $dr),
  ('/outsource/material-return/detail/' + $mrL), ('/outsource/material-return/detail/' + $mrN), ('/outsource/material-return/detail/' + $mrR),
  # 2026-10-02 修正两处**错路径**（原来会跳到兜底 /403 页 ⇒ 被静默当"已扫描且无命中"，等于这两页从未被覆盖）：
  #   ① 销售单详情真路由是 inventory/sale/detail/:id（不是 sale/order/detail/:id —— 那是 API 前缀的形状）
  #   ② 加工收退详情真路由是 outsource/order/delivery/:id（原写法多了一段 /record/）
  ('/outsource/order/delivery/' + $del), ('/inventory/sale/detail/' + $so), ('/sale/return/detail/' + $sr), ('/dev/project/edit/' + $prj),
  ('/sale/exchange/detail/' + $sx), ('/inventory/purchase-exchange/detail/' + $px), ('/inventory/purchase-return/detail/' + $pr)
) | Where-Object { $_ -notmatch '/$' }
$entry = @(
  '/outsource/material-return/add?returnType=REFUND&linked=WITH_ORDER',
  '/outsource/material-return/add?returnType=REFUND&linked=WITHOUT_ORDER',
  '/outsource/material-return/add?returnType=REPAIR',
  ('/outsource/order/delivery/return-defect/' + $oid),
  ('/outsource/order/delivery/return-defect/' + $oid + '?from=return-order'),
  ('/outsource/order/delivery/' + $oid + '?add=1'),
  ('/outsource/material-order/delivery/' + $moid)
)
$targets = @($targets + $detail + $entry | Sort-Object -Unique)
Write-Host ('targets = ' + $targets.Count)

# --- js probes ------------------------------------------------------------------------------
$B_NEW = B64 ([char]0x65B0 + [char]0x589E); $B_CRT = B64 ([char]0x65B0 + [char]0x5EFA); $B_ADD = B64 ([char]0x6DFB + [char]0x52A0)
$B_CAN = B64 ([char]0x53D6 + [char]0x6D88); $B_CLS = B64 ([char]0x5173 + [char]0x95ED)
$SCAN = "(()=>{const B=s=>{var u=new TextEncoder().encode(s),x='';u.forEach(c=>x+=String.fromCharCode(c));return btoa(x)};" +
  "const out=[];const vis=e=>e.getClientRects().length>0;" +
  "const cardOf=el=>{let n=el;while(n){if(n.classList&&n.classList.contains('el-card')){const h=n.querySelector('.el-card__header');return h?(h.innerText||'').trim():''}n=n.parentElement}return ''};" +
  "const scan=(root,tag)=>{root.querySelectorAll('.el-form-item').forEach(it=>{if(!vis(it))return;" +
  "const l=it.querySelector('.el-form-item__label');if(!l||!vis(l))return;let lines=0;" +
  "const walk=n=>{if(n.nodeType===3){if(!n.textContent.trim())return;const r=document.createRange();r.selectNodeContents(n);" +
  "lines=Math.max(lines,r.getClientRects().length)}else{n.childNodes.forEach(walk)}};walk(l);" +
  "const w=Math.round(l.getBoundingClientRect().width);const fs=parseFloat(getComputedStyle(l).fontSize);" +
  "const req=it.classList.contains('is-required')?1:0;const form=it.closest('form');const fw=form?Math.round(form.querySelector('.el-form-item__label').getBoundingClientRect().width):0;" +
  "if(lines>1)out.push(B((l.innerText||'').trim())+'|'+lines+'|'+w+'|'+tag+'|fs'+fs+'|req'+req+'|formLabel'+fw+'|'+B(cardOf(it)))})};" +
  "scan(document,'page');const dlg=[...document.querySelectorAll('.el-dialog')].filter(vis);" +
  "if(dlg.length)scan(dlg[dlg.length-1],'dialog');return JSON.stringify(out)})()"
$OPENBOX = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;" +
  "if([...document.querySelectorAll('.el-dialog')].filter(vis).length)return 'ALREADY';" +
  "const names=[T('$B_NEW'),T('$B_CRT'),T('$B_ADD')];" +
  "const b=[...document.querySelectorAll('button')].filter(vis).find(x=>names.indexOf((x.innerText||'').trim())>=0);" +
  "if(!b)return 'NOBTN';b.click();return 'CLICKED'})()"
$CLOSEBOX = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;" +
  "const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODLG';" +
  "const names=[T('$B_CAN'),T('$B_CLS')];" +
  "const b=[...d.querySelectorAll('.el-dialog__footer button')].filter(vis).find(x=>names.indexOf((x.innerText||'').trim())>=0);" +
  "if(b){b.click();return 'CANCEL'}return 'NOCANCEL'})()"

# --- scan -----------------------------------------------------------------------------------
$script:hits = 0
$script:pages = 0
$script:dialogs = 0
$probeFail = @()
$script:done = @{}
$queue = @($targets)
while ($queue.Count -gt 0) {
  $u = $queue[0]
  $queue = @($queue | Select-Object -Skip 1)
  if ($script:done.ContainsKey($u)) { continue }
  $script:done[$u] = 1
  Open $u 2600
  $script:pages++
  $seen = @{}
  foreach ($raw in @((EvalJs $SCAN))) {
    # 2026-10-05 F7-293: a failed scan probe used to be an invisible `continue` (same class as the sibling
    # scans) -- with the browser down the sweep asserted "0 wrapped labels" and reported PASS.
    if ("$raw" -notmatch '^\[') { $probeFail += $u; continue }
    foreach ($e in @($raw | ConvertFrom-Json)) {
      if ("$e" -notmatch '\|') { continue }
      $p = "$e" -split '\|'
      $key = $p[0] + '|' + $p[3]
      if ($seen.ContainsKey($key)) { continue }
      $seen[$key] = 1
      $script:hits++
      $card = ''
      if ($p.Count -gt 7) { try { $card = FromB64 $p[7] } catch { $card = '' } }
      Ok $false ('wrapped label :: ' + $u + ' :: ' + (FromB64 $p[0]) + ' (lines=' + $p[1] + ', label=' + $p[2] + 'px, ' + $p[3] + ', ' + $p[4] + ', ' + $p[5] + ', ' + $p[6] + ', card=' + $card + ')')
    }
  }
  $clicked = EvalJs $OPENBOX
  if ("$clicked" -eq 'CLICKED') {
    $script:dialogs++
    Start-Sleep -Milliseconds 1600
    $now = EvalJs "location.pathname+location.search"
    if ("$now" -ne $u -and "$now" -notmatch '^/login') {
      # the button navigated to a page (NOT a dialog) - scan that page as its own target
      if (-not $script:done.ContainsKey("$now")) { $queue += "$now" }
      continue
    }
    $raw2 = EvalJs $SCAN
    if ("$raw2" -match '^\[') {
      foreach ($e in @($raw2 | ConvertFrom-Json)) {
        if ("$e" -notmatch '\|') { continue }
        $p = "$e" -split '\|'
        $key = $p[0] + '|' + $p[3]
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = 1
        $script:hits++
        $card = ''
        if ($p.Count -gt 7) { try { $card = FromB64 $p[7] } catch { $card = '' } }
        Ok $false ('wrapped label :: ' + $u + ' :: ' + (FromB64 $p[0]) + ' (lines=' + $p[1] + ', label=' + $p[2] + 'px, ' + $p[3] + ', ' + $p[4] + ', ' + $p[5] + ', ' + $p[6] + ', card=' + $card + ')')
      }
    }
    EvalJs $CLOSEBOX | Out-Null
    Start-Sleep -Milliseconds 800
  }
}
Write-Host ('pages scanned = ' + $script:pages + ' ; wrapped sites = ' + $script:hits + ' ; probeFailures = ' + $probeFail.Count + ' ; dialogs opened = ' + $script:dialogs)
Ok ($probeFail.Count -eq 0) ('every page could be scanned (failures: ' + $probeFail.Count + (if ($probeFail.Count -gt 0) { ' -> ' + ($probeFail -join ', ') } else { '' }) + ')')
Ok ($script:pages -gt 0) ('the sweep actually visited at least one page (visited: ' + $script:pages + ')')
Ok ($script:hits -eq 0) ('no wrapped form label on any scanned page/dialog (pages=' + $script:pages + ')')
Summary 'scan-label-wrap'
