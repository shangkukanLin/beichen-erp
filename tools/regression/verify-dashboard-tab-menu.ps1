# verify-dashboard-tab-menu.ps1 (2026-09-27, user request): the dashboard TABs, and the quick links inside each TAB,
# must line up with sys_menu -- one-to-one, same names, same order, same target routes.
#
# The three rules (also written at the top of views/dashboard/index.vue):
#  1) TAB <-> sidebar CATALOG: label == catalog menu_name, order == catalog sort_order.
#     Exceptions are listed below in $NO_TAB_CATALOGS / the memo pane (no menu at all).
#  2) The leading block of a TAB's quick links == that catalog's CURRENT child menus (same labels, same order).
#  3) Extra buttons (cross-catalog conveniences, historically all from "base data") must be REAL visible menus --
#     name must exist in sys_menu and the pushed route must equal that menu's route_path (no dead buttons, no renames left behind).
#
# Static only (parses sys_menu + index.vue + api/system.ts), read-only, rerun-safe, ASCII ONLY.
# NOTE: no 'Stop' here -- mysql.exe prints a password warning on stderr and PowerShell 5.1 turns that into a
# NativeCommandError under 'Stop' (other scripts in this folder have the same pattern). The guard self-checks below
# (sys_menu must return rows) keep a silent failure from turning into a vacuous PASS.
$ErrorActionPreference = 'Continue'
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
function Clean([string]$s) { return (("$s" -replace '"', '') -replace '\s+', ' ').Trim() }

# ---------- side A: sys_menu ----------
# catalogs (sidebar level 1) in sidebar order
$catalogs = @()
foreach ($l in (Sql "SELECT id, menu_name, sort_order FROM sys_menu WHERE parent_id=0 AND menu_type='catalog' AND visible=1 AND status=1 ORDER BY sort_order")) {
  $f = "$l" -split "`t"
  $catalogs += [pscustomobject]@{ id = [int]$f[0]; name = $f[1] }
}
# every visible child row (menu or nested catalog) -> used to resolve a TAB's leading block
$children = @()
foreach ($l in (Sql "SELECT parent_id, id, menu_type, menu_name, IFNULL(route_path,''), sort_order FROM sys_menu WHERE parent_id>0 AND visible=1 AND status=1 ORDER BY parent_id, sort_order")) {
  $f = "$l" -split "`t"
  $children += [pscustomobject]@{ parent = [int]$f[0]; id = [int]$f[1]; type = $f[2]; name = $f[3]; route = $f[4]; sort = [int]$f[5] }
}
# every visible leaf page (name -> route), for validating extra buttons
$pages = @{}
foreach ($l in (Sql "SELECT menu_name, route_path FROM sys_menu WHERE parent_id>0 AND menu_type='menu' AND visible=1 AND status=1")) {
  $f = "$l" -split "`t"
  if (-not $pages.ContainsKey($f[0])) { $pages[$f[0]] = @() }
  $pages[$f[0]] += $f[1]
}
function ChildrenOf([int]$catId) { return @($children | Where-Object { $_.parent -eq $catId }) }
function ResolveRoute($row) {
  if ($row.type -eq 'catalog') {
    # a nested catalog (level 3) is not clickable: the shortcut lands on its first visible leaf
    $leaf = @($children | Where-Object { $_.parent -eq $row.id -and $_.type -eq 'menu' } | Sort-Object sort | Select-Object -First 1)
    if ($leaf.Count -eq 0) { return '' }
    return $leaf[0].route
  }
  return $row.route
}

if ($catalogs.Count -lt 5 -or $children.Count -lt 20) {
  Write-Host ("FAIL cannot read sys_menu (catalogs=" + $catalogs.Count + " children=" + $children.Count + ") -- refusing to report a vacuous PASS")
  exit 1
}

# exceptions: catalogs that deliberately have no dashboard TAB (documented in index.vue's header comment)
$NO_TAB_CATALOGS = @(2, 9)   # base data / settings (no TAB by design)
$expectedTabCatalogs = @($catalogs | Where-Object { $NO_TAB_CATALOGS -notcontains $_.id })

# Extra buttons that are DEEP LINKS into an internal tab of a menu page: their label is that tab's name, not a menu name,
# so it cannot be found in sys_menu. Those buttons are marked in index.vue with a `@deep-link` comment right above them;
# the marker only relaxes the LABEL check -- the route still has to resolve to a real visible menu (see the loop below).
$DEEP_LINK_MARKER = '@deep-link'

# ---------- side B: the vue file ----------
$lines = [IO.File]::ReadAllLines($VUE, [Text.Encoding]::UTF8)
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
    if ($lines[$i] -match '<el-button v-if="has(Menu|Path)\[''([^'']+)''\]"[^>]*@click="\$router\.push\(''([^'']+)''\)"[^>]*>([^<]+)</el-button>') {
      $btns += [pscustomobject]@{ label = $Matches[4].Trim(); path = $Matches[3]; key = $Matches[2]; line = $i }
    }
  }
  # a pane is role-toggleable only when it carries v-if="hasModule[...]" (memo / analysis are always visible)
  $panes += [pscustomobject]@{ name = $paneStart[$from]; line = $from + 1; buttons = $btns; hasVif = ($lines[$from] -match 'v-if="hasModule') }
}
Write-Host ('panes in file: ' + (($panes | ForEach-Object { $_.name }) -join ' | '))

# pane name -> catalog id (the one-to-one mapping the user asked for)
$paneToCatalog = @{ 'overview' = 10; 'dev' = 3; 'outsource' = 4; 'purchase' = 5; 'sale' = 6; 'materialWarehouse' = 11; 'stock' = 7; 'finance' = 8 }
$memoPane = @($panes | Where-Object { $_.name -eq 'memo' })
Ok ($memoPane.Count -eq 1 -and $memoPane[0].buttons.Count -eq 0) 'memo TAB has no menu and therefore no quick links'

# rule 1: pane order == catalog order (memo first, then the catalogs that own a TAB)
$paneCatalogs = @($panes | Where-Object { $paneToCatalog.ContainsKey($_.name) } | ForEach-Object { $paneToCatalog[$_.name] })
$expCatalogs = @($expectedTabCatalogs | ForEach-Object { $_.id })
Ok (($paneCatalogs -join ',') -eq ($expCatalogs -join ',')) ("pane order == sidebar catalog order [" + ($paneCatalogs -join ',') + "] vs expected [" + ($expCatalogs -join ',') + "]")
Ok ($panes[0].name -eq 'memo') 'memo TAB is first (not a catalog)'

# rule 2 + 3: per TAB, the leading block matches the catalog's children; extras must be real menus
foreach ($p in $panes) {
  if (-not $paneToCatalog.ContainsKey($p.name)) { continue }
  $catId = $paneToCatalog[$p.name]
  $kids = ChildrenOf $catId
  $n = $kids.Count
  $got = $p.buttons
  $head = @($got | Select-Object -First $n)
  $extra = @($got | Select-Object -Skip $n)
  Write-Host ("--- pane " + $p.name + " (catalog " + $catId + ", " + $n + " children, " + $got.Count + " buttons)")
  Write-Host ("    expected head: " + (($kids | ForEach-Object { $_.name }) -join ' | '))
  Write-Host ("    actual   head: " + (($head | ForEach-Object { $_.label }) -join ' | '))
  if ($extra.Count -gt 0) { Write-Host ("    extras:        " + (($extra | ForEach-Object { $_.label }) -join ' | ')) }

  Ok ($got.Count -ge $n) ($p.name + ': has at least the catalog block (' + $n + ')')
  Ok ((($head | ForEach-Object { $_.label }) -join '|') -eq (($kids | ForEach-Object { $_.name }) -join '|')) ($p.name + ': quick-link block == catalog children, same labels and order')
  # each leading button must point at its menu's route (nested catalog -> its first leaf)
  $routeBad = @()
  for ($i = 0; $i -lt [Math]::Min($n, $head.Count); $i++) {
    $exp = ResolveRoute $kids[$i]
    if ((PathOf $head[$i].path) -ne (PathOf $exp)) { $routeBad += ($head[$i].label + ' -> ' + $head[$i].path + ' (expected ' + $exp + ')') }
  }
  Ok ($routeBad.Count -eq 0) ($p.name + ': every leading button targets its own menu route' + $(if ($routeBad.Count) { ' [' + ($routeBad -join '; ') + ']' } else { '' }))

  # extras: must be a real visible menu somewhere, and the route must match that menu's route_path
  $extraBad = @()
  foreach ($e in $extra) {
    $marked = $false
    for ($j = [Math]::Max(0, $e.line - 3); $j -lt $e.line; $j++) { if ($lines[$j] -match [regex]::Escape($DEEP_LINK_MARKER)) { $marked = $true } }
    if ($pages.ContainsKey($e.label)) {
      $hit = @($pages[$e.label] | Where-Object { (PathOf $_) -eq (PathOf $e.path) })
      if ($hit.Count -eq 0) { $extraBad += ($e.label + ' -> ' + $e.path + ' (no visible menu with that label/route)') }
    } else {
      # not a menu name: allowed only when marked as a deep link AND the route still hits a real visible menu
      $anyMenu = @($pages.Values | ForEach-Object { $_ } | Where-Object { (PathOf $_) -eq (PathOf $e.path) })
      if (-not $marked) { $extraBad += ($e.label + ' is not a visible menu (and not marked ' + $DEEP_LINK_MARKER + ')') }
      elseif ($anyMenu.Count -eq 0) { $extraBad += ($e.label + ' -> ' + $e.path + ' (deep link route is not a visible menu)') }
    }
  }
  Ok ($extraBad.Count -eq 0) ($p.name + ': extra buttons are real visible menus with matching routes' + $(if ($extraBad.Count) { ' [' + ($extraBad -join '; ') + ']' } else { '' }))
}

# every catalog child must have its button (no silent omission): implied by the label equality above, but report explicitly
foreach ($cat in $expectedTabCatalogs) {
  $pane = @($panes | Where-Object { $paneToCatalog.ContainsKey($_.name) -and $paneToCatalog[$_.name] -eq $cat.id })
  if ($pane.Count -ne 1) { Ok $false ('catalog ' + $cat.id + ' has exactly one pane'); continue }
  $kids = ChildrenOf $cat.id
  $missing = @()
  $head = @($pane[0].buttons | Select-Object -First $kids.Count)
  for ($i = 0; $i -lt $kids.Count; $i++) {
    if ($i -ge $head.Count -or $head[$i].label -ne $kids[$i].name) { $missing += $kids[$i].name }
  }
  Ok ($missing.Count -eq 0) ('catalog ' + $cat.name + ': no child menu is missing from its TAB' + $(if ($missing.Count) { ' [' + ($missing -join ', ') + ']' } else { '' }))
}

# the role-config TAB list (api/system.ts) must carry the same labels in the same order
$ts = [IO.File]::ReadAllText($API, [Text.Encoding]::UTF8)
$m = [regex]::Match($ts, "DASHBOARD_TABS[^=]*=\s*\[(.*?)\]", 'Singleline')
$tsLabels = @()
if ($m.Success) {
  foreach ($im in [regex]::Matches($m.Groups[1].Value, "key:\s*'([^']+)'\s*,\s*label:\s*'([^']+)'")) { $tsLabels += $im.Groups[2].Value }
}
Write-Host ('DASHBOARD_TABS labels: ' + ($tsLabels -join ' | '))
# DASHBOARD_TABS lists only the ROLE-TOGGLEABLE tabs (panes with v-if) -- memo / analysis are always visible, not listed.
$toggleCatalogs = @($panes | Where-Object { $_.hasVif -and $paneToCatalog.ContainsKey($_.name) } | ForEach-Object { $paneToCatalog[$_.name] })
$expTsLabels = @($toggleCatalogs | ForEach-Object { $id = $_; (@($catalogs | Where-Object { $_.id -eq $id })[0]).name })
Write-Host ('expected DASHBOARD_TABS: ' + ($expTsLabels -join ' | '))
Ok (($tsLabels -join '|') -eq ($expTsLabels -join '|')) 'api/system.ts DASHBOARD_TABS labels == catalog names, same order'

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' DASHBOARD-TAB-MENU-ALIGNMENT  (PASS=' + $script:pass + ' FAIL=' + $script:fail + ')')
if ($script:fail -gt 0) { exit 1 }
