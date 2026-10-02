# F3-3 interface-level permission (pilot module: purchase exchange, menu 504 -> perm "purchase:exchange")
# Pure API + SQL (no browser). This file must stay pure ASCII.
#
# Asserts:
#   1) data: sys_menu.perms exists; page menus carry codes; catalogs are NULL; code count = 71
#      2026-10-02: 62 -> 71. 两个原因一起修正：
#        a) 本条期望值**长期过期**（种子库自 2026-09-09 起就有 70 个带码菜单，期间新增的菜单一直没同步
#           这个常量）⇒ 本守卫在本次改动前就是红的（"page menus with perms = 70 (expected 62)"）；
#        b) 本次新增「产品分析」1008 / analysis:product ⇒ 70 -> 71。
#   2) admin (owns menu 504) can call the pilot module -> 200 (no over-blocking)
#   3) restricted user (sales role, no 504) -> 403 on the pilot module: list + detail + write endpoint
#   4) same restricted user keeps 200 on its own module (sale order) and on shared base data (product)
#   5) super_admin (role has NO menus at all) -> all codes granted -> 200 on the pilot module
#   6) login payload exposes "perms" (so the frontend can reuse the same source of truth)
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) -join '').Trim()
}
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function LoginFull([string]$u, [string]$p, [int]$cid = 1) {
  try {
    $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":' + $cid + '}'
    return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b))
  } catch { return $null }
}
function Req([string]$method, [string]$url, $body, [string]$tok) {
  try {
    $h = @{}; if ($tok) { $h['Authorization'] = $tok }
    if ($method -eq 'GET') { return Invoke-RestMethod -Uri $url -Method Get -Headers $h }
    $json = if ($null -eq $body) { '{}' } else { ConvertTo-Json -InputObject $body -Depth 8 }
    return Invoke-RestMethod -Uri $url -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch { return [pscustomobject]@{ code = -1; msg = "HTTPEX $($_.Exception.Message)"; data = $null } }
}
function CodeOf($r) { if ($null -eq $r) { return 'null' } else { return "$($r.code)" } }

Write-Output '--- 1) data: sys_menu.perms + code inventory'
$col = SqlOne "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_menu' AND column_name='perms'"
if ($col -eq '1') { Ok 'sys_menu.perms column exists' } else { Bad 'sys_menu.perms column missing' }
$codes = SqlOne "SELECT COUNT(*) FROM sys_menu WHERE menu_type='menu' AND perms IS NOT NULL AND perms<>''"
if ($codes -eq '71') { Ok '71 page menus carry an interface permission code' } else { Bad ("page menus with perms = $codes (expected 71)") }
$dup = SqlOne "SELECT IFNULL(GROUP_CONCAT(p),'-') FROM (SELECT perms p FROM sys_menu WHERE menu_type='menu' AND perms IS NOT NULL AND perms<>'' GROUP BY perms HAVING COUNT(*)>1) t"
if ($dup -eq '-') { Ok 'permission codes are unique' } else { Bad ("duplicated permission codes: $dup") }
$cat = SqlOne "SELECT COUNT(*) FROM sys_menu WHERE menu_type NOT IN ('menu','button') AND perms IS NOT NULL"
if ($cat -eq '0') { Ok 'catalogs carry no permission code (button rows are checked separately below)' } else { Bad ("catalogs with perms = $cat") }
$m504 = SqlOne "SELECT perms FROM sys_menu WHERE id=504"
if ($m504 -eq 'purchase:exchange') { Ok 'menu 504 (purchase exchange) -> purchase:exchange' } else { Bad ("menu 504 perms = $m504") }

Write-Output '--- 1b) button-level codes (plan A: actions follow the page)'
$btn = SqlOne "SELECT COUNT(*) FROM sys_menu WHERE menu_type='button' AND perms IS NOT NULL"
if ($btn -eq '14') { Ok '14 button (action) codes registered' } else { Bad ("button codes = $btn (expected 14)") }
$btnMapped = SqlOne "SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id=b.parent_id AND p.perms IS NOT NULL WHERE b.menu_type='button'"
if ($btnMapped -eq '14') { Ok 'every button row hangs under a page that owns a page code' } else { Bad ("button rows with coded parent = $btnMapped") }
$dupBtn = SqlOne "SELECT IFNULL(GROUP_CONCAT(p),'-') FROM (SELECT perms p FROM sys_menu WHERE menu_type='button' AND perms IS NOT NULL GROUP BY perms HAVING COUNT(*)>1) t"
if ($dupBtn -eq '-') { Ok 'button codes are unique' } else { Bad ("duplicated button codes: $dupBtn") }

Write-Output '--- 2) admin owns the page -> 200 on the pilot module'
$la = LoginFull 'lin' '123'
if ($null -eq $la -or [string]$la.code -ne '200') { Bad 'cannot login as admin (lin)'; Write-Output 'RESULT API-PERM FAIL count 1'; exit 1 }
$tokA = [string]$la.data.token
$permsA = @($la.data.userInfo.perms)
Write-Output ("  admin perms count = " + $permsA.Count)
if ($permsA -contains 'purchase:exchange') { Ok 'admin perms contain purchase:exchange' } else { Bad 'admin perms miss purchase:exchange' }
$r = Req 'GET' "$api/inventory/purchase-exchange/page?pageNum=1&pageSize=5" $null $tokA
if ((CodeOf $r) -eq '200') { Ok 'admin GET purchase-exchange/page -> 200' } else { Bad ('admin GET purchase-exchange/page -> ' + (CodeOf $r) + ' ' + "$($r.msg)") }
if ($permsA -contains 'purchase:exchange:audit') { Ok 'admin (owns the page) automatically holds purchase:exchange:audit' } else { Bad 'admin misses purchase:exchange:audit (action codes did not follow the page)' }
# over-blocking guard: the action endpoint must NOT be 403 for a user who owns the page
$r = Req 'PUT' "$api/inventory/purchase-exchange/1/audit" $null $tokA
if ((CodeOf $r) -eq '403') { Bad 'admin PUT purchase-exchange/1/audit -> 403 (action enforcement over-blocks)' } else { Ok ('admin PUT purchase-exchange/1/audit -> ' + (CodeOf $r) + ' (not blocked)') }

Write-Output '--- 3) restricted user (sales, no 504) -> 403 on the pilot module'
$lu = LoginFull 'perm_test' '123'
if ($null -eq $lu -or [string]$lu.code -ne '200') { Bad 'cannot login as perm_test'; Write-Output 'RESULT API-PERM FAIL count 1'; exit 1 }
$tokU = [string]$lu.data.token
$permsU = @($lu.data.userInfo.perms)
Write-Output ("  perm_test perms = " + ($permsU -join ','))
if ($permsU -contains 'purchase:exchange') { Bad 'restricted user unexpectedly holds purchase:exchange' } else { Ok 'restricted user does NOT hold purchase:exchange' }
if ($permsU -contains 'sale:order') { Ok 'restricted user holds sale:order (own module)' } else { Bad 'restricted user misses sale:order' }
$r = Req 'GET' "$api/inventory/purchase-exchange/page?pageNum=1&pageSize=5" $null $tokU
if ((CodeOf $r) -eq '403') { Ok 'restricted user GET purchase-exchange/page -> 403 (the F3-3 gap is closed)' } else { Bad ('restricted user GET purchase-exchange/page -> ' + (CodeOf $r) + ' (expected 403) ' + "$($r.msg)") }
$r = Req 'GET' "$api/inventory/purchase-exchange/1" $null $tokU
if ((CodeOf $r) -eq '403') { Ok 'restricted user GET purchase-exchange/{id} -> 403' } else { Bad ('restricted user GET purchase-exchange/1 -> ' + (CodeOf $r) + ' (expected 403)') }
$r = Req 'POST' "$api/inventory/purchase-exchange" @{ purchaseOrderId = 1; items = @() } $tokU
if ((CodeOf $r) -eq '403') { Ok 'restricted user POST purchase-exchange -> 403 (write is blocked too)' } else { Bad ('restricted user POST purchase-exchange -> ' + (CodeOf $r) + ' (expected 403)') }
$r = Req 'PUT' "$api/inventory/purchase-exchange/1/audit" $null $tokU
if ((CodeOf $r) -eq '403') { Ok 'restricted user PUT purchase-exchange/1/audit -> 403 (action blocked)' } else { Bad ('restricted user PUT purchase-exchange/1/audit -> ' + (CodeOf $r) + ' (expected 403)') }
if ($permsU -contains 'purchase:exchange:audit') { Bad 'restricted user unexpectedly holds purchase:exchange:audit' } else { Ok 'restricted user does NOT hold purchase:exchange:audit' }

Write-Output '--- 4) no over-blocking: own module + shared base data stay reachable'
$r = Req 'GET' "$api/inventory/sale/page?pageNum=1&pageSize=5" $null $tokU
if ((CodeOf $r) -eq '200') { Ok 'restricted user GET sale/page -> 200 (own module still works)' } else { Bad ('restricted user GET sale/page -> ' + (CodeOf $r)) }
$r = Req 'GET' "$api/product/page?pageNum=1&pageSize=5" $null $tokU
if ((CodeOf $r) -eq '200') { Ok 'restricted user GET product/page -> 200 (shared base data exempt)' } else { Bad ('restricted user GET product/page -> ' + (CodeOf $r)) }
$r = Req 'GET' "$api/warehouse/inventory" $null $tokU
if ((CodeOf $r) -eq '200') { Ok 'restricted user GET warehouse/inventory -> 200 (shared dictionary exempt)' } else { Bad ('restricted user GET warehouse/inventory -> ' + (CodeOf $r)) }
# base-data / master-data write protection (v1.1): reads are shared, writes need the owning page code.
# Expectations are DERIVED from ApiPermGuard.writeRule(...) + the caller's real perms (no hand-written guesses:
# an earlier hand-written version was wrong three times because e.g. the sales role legitimately owns base:product).
# Probe bodies are named '__perm_probe__' so any leak is countable and removable.
$guardJava = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\config\ApiPermGuard.java' -Raw
$wrules = @{}
foreach ($m in [regex]::Matches($guardJava, 'writeRule\("([^"]+)",([^)]*)\)')) {
  $wrules[$m.Groups[1].Value] = @([regex]::Matches($m.Groups[2].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
}
$mdBefore = SqlRaw "SELECT (SELECT COUNT(*) FROM product)+(SELECT COUNT(*) FROM outsource_material)+(SELECT COUNT(*) FROM finance_account)"
$mdProbes = @(
  @{ p = '/finance/account'; b = @{ accountType = 'CASH'; accountName = '__perm_probe__' } },
  @{ p = '/outsource/material'; b = @{ materialName = '__perm_probe__' } },
  @{ p = '/product'; b = @{ name = '__perm_probe__' } }
)
foreach ($md in $mdProbes) {
  $codes = @($wrules['/api' + $md.p])
  if ($codes.Count -eq 0) { Bad ("no writeRule for " + $md.p + " (test out of sync with the guard)"); continue }
  $holds = @($codes | Where-Object { $permsU -contains $_ }).Count -gt 0
  $r = Req 'POST' "$api$($md.p)" $md.b $tokU
  $code = CodeOf $r
  if ($holds) {
    if ($code -eq '403') { Bad ("restricted user holds one of $($codes -join '|') but POST " + $md.p + ' -> 403 (over-blocking)') }
    else { Ok ("restricted user POST " + $md.p + " -> " + $code + " (allowed: holds one of $($codes -join '|'))") }
  } else {
    if ($code -eq '403') { Ok ("restricted user POST " + $md.p + ' -> 403 (master-data write closed)') }
    else { Bad ("restricted user lacks $($codes -join '|') but POST " + $md.p + ' -> ' + $code + ' (write not blocked)') }
  }
}
$mdAfter = SqlRaw "SELECT (SELECT COUNT(*) FROM product)+(SELECT COUNT(*) FROM outsource_material)+(SELECT COUNT(*) FROM finance_account)"
SqlRaw "DELETE FROM product WHERE name='__perm_probe__'; DELETE FROM outsource_material WHERE material_name='__perm_probe__'; DELETE FROM finance_account WHERE account_name='__perm_probe__';" | Out-Null
$leak = SqlRaw "SELECT (SELECT COUNT(*) FROM product WHERE name='__perm_probe__')+(SELECT COUNT(*) FROM outsource_material WHERE material_name='__perm_probe__')+(SELECT COUNT(*) FROM finance_account WHERE account_name='__perm_probe__')"
if ($leak -eq '0') { Ok ("no __perm_probe__ leftovers (count before=$mdBefore after=$mdAfter)") } else { Bad ("leftover probe rows: $leak") }

Write-Output '--- 5) super_admin fallback (role has zero menus -> must still own every code)'
$superRoleId = SqlOne "SELECT id FROM sys_role WHERE role_code='super_admin'"
if ($superRoleId -eq '') { Bad 'role super_admin not found' } else {
  $su = SqlOne "SELECT id FROM sys_user WHERE username='perm_super_test'"
  if ($su -ne '') { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "DELETE FROM sys_user_role WHERE user_id=$su; DELETE FROM sys_user WHERE id=$su;" 2>$null | Out-Null }
  $r = Req 'POST' "$api/system/user" @{ username = 'perm_super_test'; password = '123'; status = 1 } $tokA
  Write-Output ("  create perm_super_test -> code=" + (CodeOf $r))
  $su = SqlOne "SELECT id FROM sys_user WHERE username='perm_super_test'"
  if ($su -eq '') { Bad 'cannot create perm_super_test' } else {
    # NOTE: user management deliberately refuses to grant super_admin (see UserServiceImpl.saveUserRoles),
    # so bind the role the same way DataInitializer.initSuperAdmin does: straight in sys_user_role.
    & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES ($su, $superRoleId);" 2>$null | Out-Null
    Write-Output ("  bound super_admin role via SQL (user=$su role=$superRoleId)")
    $ls = LoginFull 'perm_super_test' '123'
    if ($null -eq $ls -or [string]$ls.code -ne '200') { Bad 'cannot login as perm_super_test' } else {
      $permsS = @($ls.data.userInfo.perms)
      Write-Output ("  perm_super_test perms count = " + $permsS.Count)
      if ($permsS.Count -eq [int]$codes) { Ok 'super_admin gets every configured code (fallback works)' } else { Bad ("super_admin perms = " + $permsS.Count + " (expected $codes)") }
      $r = Req 'GET' "$api/inventory/purchase-exchange/page?pageNum=1&pageSize=5" $null ([string]$ls.data.token)
      if ((CodeOf $r) -eq '200') { Ok 'super_admin GET purchase-exchange/page -> 200' } else { Bad ('super_admin GET purchase-exchange/page -> ' + (CodeOf $r)) }
    }
  }
  & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.username='perm_super_test'; DELETE um FROM sys_user_menu um JOIN sys_user u ON u.id=um.user_id WHERE u.username='perm_super_test'; DELETE dt FROM sys_user_dashboard_tab dt JOIN sys_user u ON u.id=dt.user_id WHERE u.username='perm_super_test'; DELETE FROM sys_user WHERE username='perm_super_test';" 2>$null | Out-Null
  $left = SqlOne "SELECT COUNT(*) FROM sys_user WHERE username='perm_super_test'"
  if ($left -eq '0') { Ok 'test user perm_super_test cleaned up' } else { Bad 'cleanup failed for perm_super_test' }
}

Write-Output '--- 5b) dashboard module-pages aggregate is filtered by perms (read isolation phase 1)'
$rA = Req 'GET' "$api/dashboard/module-pages" $null $tokA
$keysA = if ($rA.data) { @($rA.data.PSObject.Properties.Name) } else { @() }
if ($keysA -contains 'purchaseOrder' -and $keysA -contains 'outsourceOrder') { Ok ('admin aggregate carries purchase/outsource blocks (' + ($keysA -join ',') + ')') } else { Bad ('admin aggregate missing blocks: ' + ($keysA -join ',')) }
# phase 1b: dev (projects+phases), sale total, customer analysis also live in the aggregate
if ($keysA -contains 'dev' -and $keysA -contains 'sale' -and $keysA -contains 'customerAnalysis') { Ok 'admin aggregate carries dev/sale/customerAnalysis blocks (phase 1b)' } else { Bad ('admin aggregate missing 1b blocks: ' + ($keysA -join ',')) }
if ($rA.data.dev -and $rA.data.dev.projectPage -and $rA.data.dev.projectPage.total -ge 0) { Ok ('dev block carries projectPage.total=' + $rA.data.dev.projectPage.total) } else { Bad 'dev block missing projectPage' }
$rU2 = Req 'GET' "$api/dashboard/module-pages" $null $tokU
$keysU = if ($rU2.data) { @($rU2.data.PSObject.Properties.Name) } else { @() }
if ($keysU -contains 'purchaseOrder') { Bad 'restricted user unexpectedly receives the purchase block' } else { Ok ('restricted user gets no purchase block (' + ($keysU -join ',') + ')') }
if ($keysU -contains 'sale') { Ok 'restricted user (sales) does receive the sale block (owns sale:order)' } else { Bad 'restricted user (sales) missing the sale block' }
if ($keysU -contains 'customerAnalysis') { Bad 'restricted user unexpectedly receives customerAnalysis (lacks analysis:customer)' } else { Ok 'restricted user gets no customerAnalysis block' }

Write-Output '--- 5c) phase 2 batch 1: detail pages carry their own related summaries (read isolation)'
$saleId = SqlOne "SELECT id FROM sale_order ORDER BY id DESC LIMIT 1"
if ($saleId -eq '') { Bad 'no sale_order row to probe (seed data missing)' } else {
  $r = Req 'GET' "$api/inventory/sale/$saleId" $null $tokA
  $k = if ($r.data) { @($r.data.PSObject.Properties.Name) } else { @() }
  if ((CodeOf $r) -eq '200' -and $k -contains 'receipts' -and $k -contains 'returns' -and $k -contains 'exchanges') {
    Ok ("admin GET inventory/sale/{id} -> 200 with receipts/returns/exchanges (entity fields kept: id=" + $r.data.id + ")")
  } else { Bad ('sale detail missing merged summaries: ' + (CodeOf $r) + ' keys=' + ($k -join ',')) }
  $r = Req 'GET' "$api/inventory/sale/$saleId" $null $tokU
  if ((CodeOf $r) -eq '200') { Ok 'restricted user (sales) GET sale/{id} -> 200 (no over-blocking)' } else { Bad ('restricted user GET sale/{id} -> ' + (CodeOf $r)) }
}
$poId = SqlOne "SELECT id FROM purchase_order ORDER BY id DESC LIMIT 1"
if ($poId -eq '') { Bad 'no purchase_order row to probe (seed data missing)' } else {
  $r = Req 'GET' "$api/inventory/purchase/$poId" $null $tokA
  $k = if ($r.data) { @($r.data.PSObject.Properties.Name) } else { @() }
  if ((CodeOf $r) -eq '200' -and $k -contains 'returns') { Ok 'admin GET inventory/purchase/{id} -> 200 with returns (purchase detail)' } else { Bad ('purchase detail missing returns: ' + (CodeOf $r) + ' keys=' + ($k -join ',')) }
  $r = Req 'GET' "$api/inventory/purchase/$poId" $null $tokU
  # expectation is DERIVED from ApiPermGuard (same idea as section 4): while the prefix is still in
  # READ_SHARED (today: dashboard todo + purchase-return page read source orders) reading must stay open;
  # once phase 3 shrinks the whitelist this flips to 403 automatically.
  $sharedBlock = [regex]::Match($guardJava, "(?s)READ_SHARED\s*=\s*List\.of\((.*?)\);").Groups[1].Value
  $readShared = @([regex]::Matches($sharedBlock, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
  $purchaseShared = @($readShared | Where-Object { $_ -eq '/api/inventory/purchase' }).Count -gt 0
  if ($purchaseShared) {
    if ((CodeOf $r) -eq '200') { Ok 'restricted user GET purchase/{id} -> 200 (prefix still READ_SHARED; closes in phase 3)' } else { Bad ('restricted user GET purchase/{id} -> ' + (CodeOf $r) + ' (READ_SHARED says it must be open)') }
  } else {
    if ((CodeOf $r) -eq '403') { Ok 'restricted user (sales, no purchase:order) GET purchase/{id} -> 403 (page code enforced)' } else { Bad ('restricted user GET purchase/{id} -> ' + (CodeOf $r) + ' (expected 403)') }
  }
}
$cusId = SqlOne "SELECT id FROM customer ORDER BY id DESC LIMIT 1"
if ($cusId -eq '') { Bad 'no customer row to probe (seed data missing)' } else {
  $r = Req 'GET' "$api/inventory/customer/$cusId/sale-orders?pageNum=1&pageSize=5" $null $tokU
  if ((CodeOf $r) -eq '200' -and $null -ne $r.data.records -and $null -ne $r.data.total) {
    Ok 'restricted user GET customer/{id}/sale-orders -> 200 (customer detail tab, own prefix)'
  } else { Bad ('customer sale-orders -> ' + (CodeOf $r) + ' ' + "$($r.msg)") }
}

Write-Output '--- 5d) phase 3: per-page read endpoints return exactly what the shared endpoint did'
$soId = SqlOne "SELECT id FROM sale_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1"
if ($soId -eq '') { Bad 'no audited sale_order to probe (seed data missing)' } else {
  $rA1 = Req 'GET' "$api/sale/return/source-order?saleOrderId=$soId" $null $tokA
  $rA2 = Req 'GET' "$api/inventory/sale/$soId" $null $tokA
  if ((CodeOf $rA1) -eq '200' -and "$($rA1.data.id)" -eq "$($rA2.data.id)" -and "$($rA1.data.code)" -eq "$($rA2.data.code)") {
    Ok 'sale/return/source-order == inventory/sale/{id} (same entity, code kept)'
  } else { Bad ('sale/return/source-order mismatch: ' + (CodeOf $rA1) + ' vs ' + (CodeOf $rA2)) }
  $rA3 = Req 'GET' "$api/sale/exchange/source-order?saleOrderId=$soId" $null $tokA
  if ((CodeOf $rA3) -eq '200' -and "$($rA3.data.id)" -eq "$soId") { Ok 'sale/exchange/source-order returns the same order' } else { Bad ('sale/exchange/source-order -> ' + (CodeOf $rA3)) }
  $rA4 = Req 'GET' "$api/sale/exchange/sale-orders?customerId=$($rA2.data.customerId)" $null $tokA
  $rA5 = Req 'GET' "$api/sale/return/sale-orders?customerId=$($rA2.data.customerId)" $null $tokA
  if ((CodeOf $rA4) -eq '200' -and (CodeOf $rA5) -eq '200' -and @($rA4.data).Count -eq @($rA5.data).Count) {
    Ok ('sale/exchange/sale-orders == sale/return/sale-orders (count=' + @($rA4.data).Count + ')')
  } else { Bad ('exchange sale-orders mismatch: ' + (CodeOf $rA4) + '/' + (CodeOf $rA5)) }
}
$moA = Req 'GET' "$api/outsource/material-order/page?pageSize=5" $null $tokA
$moB = Req 'GET' "$api/outsource/material-return/material-orders?pageSize=5" $null $tokA
if ((CodeOf $moA) -eq '200' -and (CodeOf $moB) -eq '200' -and "$($moA.data.total)" -eq "$($moB.data.total)") {
  Ok ('material-return/material-orders == material-order/page (total=' + $moB.data.total + ')')
} else { Bad ('material-orders mismatch: ' + (CodeOf $moA) + '#total=' + $moA.data.total + ' vs ' + (CodeOf $moB) + '#total=' + $moB.data.total) }
# exact request shape the material-return UI sends (supplier + statuses -> RemoteSelect dropdown)
$msup = SqlOne "SELECT supplier_id FROM outsource_material_order WHERE supplier_id IS NOT NULL ORDER BY id DESC LIMIT 1"
if ($msup -eq '') { Bad 'no material_order supplier to probe' } else {
  $moU = Req 'GET' "$api/outsource/material-return/material-orders?pageSize=500&supplierId=$msup&statuses=RECEIVING,FINISHED" $null $tokA
  $moV = Req 'GET' "$api/outsource/material-order/page?pageSize=500&supplierId=$msup&statuses=RECEIVING,FINISHED" $null $tokA
  if ((CodeOf $moU) -eq '200' -and @($moU.data.records).Count -eq @($moV.data.records).Count -and @($moU.data.records).Count -gt 0) {
    Ok ('material-return/material-orders UI-shape == material-order/page (rows=' + @($moU.data.records).Count + ')')
  } else { Bad ('material-orders UI-shape mismatch: ' + (CodeOf $moU) + '#' + @($moU.data.records).Count + ' vs ' + (CodeOf $moV) + '#' + @($moV.data.records).Count) }
}
$fid = SqlOne "SELECT factory_id FROM outsource_order WHERE factory_id IS NOT NULL ORDER BY id DESC LIMIT 1"
if ($fid -eq '') { Bad 'no factory supplier to probe' } else {
  $rB1 = Req 'GET' "$api/outsource/return-order/orders?factoryId=$fid&pageSize=5" $null $tokA
  $rB2 = Req 'GET' "$api/outsource/order/page?factoryId=$fid&pageSize=5" $null $tokA
  if ((CodeOf $rB1) -eq '200' -and "$($rB1.data.total)" -eq "$($rB2.data.total)") {
    Ok ('return-order/orders == outsource/order/page (total=' + $rB1.data.total + ')')
  } else { Bad ('return-order/orders mismatch: ' + (CodeOf $rB1) + '#total=' + $rB1.data.total + ' vs ' + (CodeOf $rB2) + '#total=' + $rB2.data.total) }
  $mid = SqlOne "SELECT id FROM outsource_material ORDER BY id LIMIT 1"
  if ($mid -eq '') { Bad 'no outsource_material to probe' } else {
    $rC1 = Req 'GET' "$api/outsource/other-io/material-weighted-price?factoryId=$fid&materialId=$mid" $null $tokA
    $rC2 = Req 'GET' "$api/outsource/delivery/material-weighted-price?factoryId=$fid&materialId=$mid" $null $tokA
    if ((CodeOf $rC1) -eq '200' -and "$($rC1.data)" -eq "$($rC2.data)") {
      Ok ('other-io/material-weighted-price == delivery/material-weighted-price (' + $rC1.data + ')')
    } else { Bad ('weighted price mismatch: ' + (CodeOf $rC1) + '=' + $rC1.data + ' vs ' + (CodeOf $rC2) + '=' + $rC2.data) }
  }
}
$prA = Req 'GET' "$api/inventory/purchase-return/source-order?purchaseOrderId=$poId" $null $tokA
$prB = Req 'GET' "$api/inventory/purchase/$poId" $null $tokA
if ((CodeOf $prA) -eq '200' -and "$($prA.data.id)" -eq "$($prB.data.id)") { Ok 'purchase-return/source-order == inventory/purchase/{id}' } else { Bad ('purchase-return/source-order -> ' + (CodeOf $prA)) }
$peA = Req 'GET' "$api/inventory/purchase-exchange/source-order?purchaseOrderId=$poId" $null $tokA
if ((CodeOf $peA) -eq '200' -and "$($peA.data.id)" -eq "$poId") { Ok 'purchase-exchange/source-order returns the same order' } else { Bad ('purchase-exchange/source-order -> ' + (CodeOf $peA)) }

Write-Output '--- 6) login payload exposes perms (single source of truth for the frontend)'
if ($permsA.Count -gt 0) { Ok 'login response carries userInfo.perms' } else { Bad 'login response has no perms' }

if ($fail -eq 0) { Write-Output 'RESULT API-PERM PASS' } else { Write-Output ("RESULT API-PERM FAIL count " + $fail) }
exit $fail
