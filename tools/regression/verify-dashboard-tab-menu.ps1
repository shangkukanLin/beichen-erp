# verify-dashboard-tab-menu.ps1 (2026-09-27, user request): the dashboard TABs, and the quick links inside each TAB,
# line up with sys_menu -- one-to-one, same names, same order, same target routes, and STRICTLY nothing else.
#
# The rules (also written at the top of views/dashboard/index.vue):
#  1) TAB <-> sidebar CATALOG: label == catalog menu_name, order == catalog sort_order.
#     Exceptions: the memo pane (no menu) and $NO_TAB_CATALOGS (catalogs that deliberately have no TAB).
#  2) A TAB's quick links == that catalog's CURRENT child menus, exactly -- same labels, same order, same routes,
#     no extras (2026-09-27 user: "strictly only this catalog's submenus"; the 13 cross-catalog buttons were removed).
#  3) A TAB's visibility gate (hasModule.*) may only name submenus of that same catalog -- otherwise a user granted
#     only master-data pages would see a TAB with zero buttons (an empty TAB).
#  4) menuNames (the hasMenu whitelist) is exactly the set of keys the template references -- two-way check:
#     every referenced key is registered (or the button never renders) and there are no dead entries.
#
# Static only (parses sys_menu + index.vue + api/system.ts), read-only, rerun-safe, ASCII ONLY.
$ErrorActionPreference = 'Continue'   # mysql.exe warns on stderr; 'Stop' would turn that into a terminating error
$ROOT  = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$VUE   = Join-Path $ROOT 'beichen-erp-web\src\views\dashboard\index.vue'
$API   = Join-Path $ROOT 'beichen-erp-web\src\api\system.ts'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'

$script:pass = 0; $script:fail = 0
function Ok([bool]$cond, [string]$msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:pass++ } else { Write-Host ('FAIL ' + $msg); $script:fail++ }
}
function Sql([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @(@($o) | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '' })
}
function PathOf([string]$p) { return (($p -split '\?')[0]).TrimEnd('/') }

# ---------- side A: sys_menu ----------
$catalogs = @()
foreach ($l in (Sql "SELECT id, menu_name, sort_order FROM sys_menu WHERE parent_id=0 AND menu_type='catalog' AND visible=1 AND status=1 ORDER BY sort_order")) {
  $f = "$l" -split "`t"
  $catalogs += [pscustomobject]@{ id = [int]$f[0]; name = $f[1] }
}
$children = @()
foreach ($l in (Sql "SELECT parent_id, id, menu_type, menu_name, IFNULL(route_name,''), IFNULL(route_path,''), sort_order FROM sys_menu WHERE parent_id>0 AND visible=1 AND status=1 ORDER BY parent_id, sort_order")) {
  $f = "$l" -split "`t"
  $children += [pscustomobject]@{ parent = [int]$f[0]; id = [int]$f[1]; type = $f[2]; name = $f[3]; key = $f[4]; route = $f[5]; sort = [int]$f[6] }
}
if ($catalogs.Count -lt 5 -or $children.Count -lt 20) {
  Write-Host ("FAIL cannot read sys_menu (catalogs=" + $catalogs.Count + " children=" + $children.Count + ") -- refusing to report a vacuous PASS")
  exit 1
}
function ChildrenOf([int]$catId) { return @($children | Where-Object { $_.parent -eq $catId }) }
function ResolveRoute($row) {
  if ($row.type -eq 'catalog') {
    # a nested catalog (level 3) is not clickable: its shortcut lands on the first visible leaf
    $leaf = @($children | Where-Object { $_.parent -eq $row.id -and $_.type -eq 'menu' } | Sort-Object sort | Select-Object -First 1)
    if ($leaf.Count -eq 0) { return '' }
    return $leaf[0].route
  }
  return $row.route
}
# a TAB's gate may name any page reachable under that catalog (nested catalogs flattened to their leaves)
function CatalogKeys([int]$catId) {
  $keys = @()
  foreach ($c in (ChildrenOf $catId)) {
    if ($c.type -eq 'catalog' -and $c.key -eq '') { $keys += @(ChildrenOf $c.id | Where-Object { $_.type -eq 'menu' } | ForEach-Object { $_.key }) }
    elseif ($c.key -ne '') { $keys += $c.key }
  }
  return @($keys | Where-Object { $_ -ne '' })
}

# exceptions: catalogs that deliberately have no dashboard TAB (documented in index.vue's header comment)
$NO_TAB_CATALOGS = @(2, 9)   # 2 = base data (spread into the sidebar), 9 = settings
$expectedTabCatalogs = @($catalogs | Where-Object { $NO_TAB_CATALOGS -notcontains $_.id })

# ---------- side B: the vue file ----------
$lines = [IO.File]::ReadAllLines($VUE, [Text.Encoding]::UTF8)
$text  = ($lines -join "`n")
$paneStart = @{}
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match '<el-tab-pane[^>]*name="([^"]+)"') { $paneStart[[int]$i] = $Matches[1] }
}
$paneIdx = @($paneStart.Keys | Sort-Object)
$panes = @()
for ($k = 0; $k -lt $paneIdx.Count; $k++) {
  $from = $paneIdx[$k]
  $to = if ($k + 1 -lt $paneIdx.Count) { $paneIdx[$k + 1] - 1 } else { $lines.Count - 1 }
  $btns = @()
  for ($i = $from; $i -le $to; $i++) {
    if ($lines[$i] -match '<el-button v-if="hasMenu\[''([^'']+)''\]"[^>]*@click="\$router\.push\(''([^'']+)''\)"[^>]*>([^<]+)</el-button>') {
      $btns += [pscustomobject]@{ label = $Matches[3].Trim(); path = $Matches[2]; key = $Matches[1]; line = $i }
    }
  }
  $panes += [pscustomobject]@{ name = $paneStart[$from]; line = $from + 1; buttons = $btns; hasVif = ($lines[$from] -match 'v-if="hasModule') }
}
Write-Host ('panes in file: ' + (($panes | ForEach-Object { $_.name }) -join ' | '))

$paneToCatalog = @{ 'overview' = 10; 'dev' = 3; 'outsource' = 4; 'purchase' = 5; 'sale' = 6; 'materialWarehouse' = 11; 'stock' = 7; 'finance' = 8 }
$memoPane = @($panes | Where-Object { $_.name -eq 'memo' })
Ok ($memoPane.Count -eq 1 -and $memoPane[0].buttons.Count -eq 0) 'memo TAB has no menu and therefore no quick links'

# rule 1: pane order == catalog order (memo first, then the catalogs that own a TAB)
$paneCatalogs = @($panes | Where-Object { $paneToCatalog.ContainsKey($_.name) } | ForEach-Object { $paneToCatalog[$_.name] })
$expCatalogs = @($expectedTabCatalogs | ForEach-Object { $_.id })
Ok (($paneCatalogs -join ',') -eq ($expCatalogs -join ',')) ("pane order == sidebar catalog order [" + ($paneCatalogs -join ',') + "] vs expected [" + ($expCatalogs -join ',') + "]")
Ok ($panes[0].name -eq 'memo') 'memo TAB is first (not a catalog)'

# rule 2: a TAB's quick links == that catalog's children, exactly (labels + order + routes, no extras)
foreach ($p in $panes) {
  if (-not $paneToCatalog.ContainsKey($p.name)) { continue }
  $catId = $paneToCatalog[$p.name]
  $kids = ChildrenOf $catId
  $n = $kids.Count
  $got = $p.buttons
  Write-Host ("--- pane " + $p.name + " (catalog " + $catId + ", children=" + $n + ", buttons=" + $got.Count + ")")
  Write-Host ("    expected: " + (($kids | ForEach-Object { $_.name }) -join ' | '))
  Write-Host ("    actual:   " + (($got | ForEach-Object { $_.label }) -join ' | '))
  Ok ($got.Count -eq $n) ($p.name + ': button count == child menu count (' + $n + ') -- no cross-catalog extras')
  Ok ((($got | ForEach-Object { $_.label }) -join '|') -eq (($kids | ForEach-Object { $_.name }) -join '|')) ($p.name + ': labels == child menu names, same order')
  $routeBad = @()
  for ($i = 0; $i -lt [Math]::Min($n, $got.Count); $i++) {
    $exp = ResolveRoute $kids[$i]
    if ((PathOf $got[$i].path) -ne (PathOf $exp)) { $routeBad += ($got[$i].label + ' -> ' + $got[$i].path + ' (expected ' + $exp + ')') }
  }
  Ok ($routeBad.Count -eq 0) ($p.name + ': every button targets its own menu route' + $(if ($routeBad.Count) { ' [' + ($routeBad -join '; ') + ']' } else { '' }))

  # rule 3: the TAB's visibility gate may only name submenus of this catalog
  # (the always-visible panes -- memo / analysis -- carry no gate at all: they are not role-toggleable)
  $gateKeys = @()
  $gateSeen = $false
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match ('hasModule\.' + $p.name + '\s*=')) { $gateSeen = $true; $gateKeys = @(); }
    if ($gateSeen) {
      foreach ($m in [regex]::Matches($lines[$i], "names\.has\('([^']+)'\)")) { $gateKeys += $m.Groups[1].Value }
      if ($lines[$i] -match 'tabAllowed\(') { break }
    }
  }
  if (-not $gateSeen) {
    Ok (-not $p.hasVif) ($p.name + ': always-visible TAB carries no hasModule gate (not role-toggleable)')
    continue
  }
  $ownKeys = CatalogKeys $catId
  $foreign = @($gateKeys | Where-Object { $ownKeys -notcontains $_ })
  Write-Host ("    gate keys: " + ($gateKeys -join ',') + " | catalog keys: " + ($ownKeys -join ','))
  Ok ($gateKeys.Count -gt 0) ($p.name + ': visibility gate is readable')
  Ok ($foreign.Count -eq 0) ($p.name + ': gate only names this catalog''s submenus' + $(if ($foreign.Count) { ' [foreign: ' + ($foreign -join ',') + ']' } else { '' }))
}

# rule 4: menuNames <-> referenced keys (two-way)
$refKeys = @([regex]::Matches($text, "hasMenu(?:\.value)?\['([^']+)'\]") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$mn = [regex]::Match($text, 'const menuNames = \[(.*?)\]', 'Singleline')
$listed = @([regex]::Matches($mn.Groups[1].Value, "'([^']+)'") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$dead = @($listed | Where-Object { $refKeys -notcontains $_ })
$unreg = @($refKeys | Where-Object { $listed -notcontains $_ })
Write-Host ('menuNames=' + $listed.Count + ' referenced=' + $refKeys.Count)
Ok ($mn.Success -and $listed.Count -gt 0) 'menuNames whitelist is readable'
# a key only mentioned inside comments must not count as a reference -- drop such lines before comparing
$unregReal = @()
foreach ($k in $unreg) {
  $usedInCode = $false
  foreach ($line in $lines) {
    if ($line -match "hasMenu(?:\.value)?\['$([regex]::Escape($k))'\]" -and $line -notmatch '^\s*(//|\*|/\*)') { $usedInCode = $true }
  }
  if ($usedInCode) { $unregReal += $k }
}
Ok ($unregReal.Count -eq 0) ('every referenced hasMenu key is registered' + $(if ($unregReal.Count) { ' [missing: ' + ($unregReal -join ',') + ']' } else { '' }))
Ok ($dead.Count -eq 0) ('no dead menuNames entries' + $(if ($dead.Count) { ' [dead: ' + ($dead -join ',') + ']' } else { '' }))

# the role-config TAB list (api/system.ts) must carry the same labels in the same order (toggleable TABS only)
$ts = [IO.File]::ReadAllText($API, [Text.Encoding]::UTF8)
$m = [regex]::Match($ts, "DASHBOARD_TABS[^=]*=\s*\[(.*?)\]", 'Singleline')
$tsLabels = @()
if ($m.Success) {
  foreach ($im in [regex]::Matches($m.Groups[1].Value, "key:\s*'([^']+)'\s*,\s*label:\s*'([^']+)'")) { $tsLabels += $im.Groups[2].Value }
}
$toggleCatalogs = @($panes | Where-Object { $_.hasVif -and $paneToCatalog.ContainsKey($_.name) } | ForEach-Object { $paneToCatalog[$_.name] })
$expTsLabels = @($toggleCatalogs | ForEach-Object { $id = $_; (@($catalogs | Where-Object { $_.id -eq $id })[0]).name })
Write-Host ('DASHBOARD_TABS labels: ' + ($tsLabels -join ' | '))
Write-Host ('expected DASHBOARD_TABS: ' + ($expTsLabels -join ' | '))
Ok (($tsLabels -join '|') -eq ($expTsLabels -join '|')) 'api/system.ts DASHBOARD_TABS labels == catalog names, same order'

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' DASHBOARD-TAB-MENU-ALIGNMENT  (PASS=' + $script:pass + ' FAIL=' + $script:fail + ')')
if ($script:fail -gt 0) { exit 1 }
