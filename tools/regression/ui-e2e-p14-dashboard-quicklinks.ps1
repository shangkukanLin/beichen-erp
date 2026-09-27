# P14 (2026-09-22 user request): every dashboard TAB's quick links must LEAD with that catalog's own
#   menus, in the SAME order as the current submenus (left sidebar order), and carry the same labels.
#   Cross-catalog extras (master data surfaced by usage) come after that block -- not asserted here.
# Read-only, rerun-safe. ASCII ONLY (expectations come from the DB, so no Chinese literals are needed).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
# same helper shape as verify-material-warehouse-menu.ps1: catalog children, sidebar order
function MenuNames([int]$parentId) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=$parentId AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}

Step '1) each dashboard TAB quick-links lead with its catalog menus in sidebar order'
Open '/dashboard' 4200
ClearErrs | Out-Null
Start-Sleep -Milliseconds 2200

# pane id == <el-tab-pane name="...">; inactive panes stay in the DOM, so read textContent (innerText is empty when hidden)
$pairs = @(
  @{ pane = 'dev';               cat = 3  },
  @{ pane = 'outsource';         cat = 4  },
  @{ pane = 'materialWarehouse'; cat = 11 },
  @{ pane = 'purchase';          cat = 5  },
  @{ pane = 'sale';              cat = 6  },
  @{ pane = 'stock';             cat = 7  },
  @{ pane = 'finance';           cat = 8  }
)
foreach ($p in $pairs) {
  $js = "(function(){const b=[...document.querySelectorAll('#pane-" + $p.pane + " .quick-links button')].map(x=>(x.textContent||'').trim()).filter(Boolean);return b.join('|')})()"
  $labels = (EvalJs $js) -replace '"', ''
  $labels = $labels.Trim()
  $arr = @($labels -split '\|' | Where-Object { $_ -ne '' })
  $exp = MenuNames $p.cat
  $n = $exp.Count
  $head = @($arr | Select-Object -First $n)
  Write-Host ('  pane=' + $p.pane + ' catalog=' + $p.cat + ' labels=' + ($arr -join '|'))
  Write-Host ('    expected head = ' + ($exp -join '|'))
  # NOTE: ui-e2e-lib's Ok takes (condition, message) -- unlike the older standalone verify-*.ps1 files,
  # which define their own one-arg local Ok. Passing just a message throws and silently yields a false PASS.
  Ok (($n -gt 0) -and (($head -join '|') -eq ($exp -join '|'))) ($p.pane + ': quick links lead with catalog ' + $p.cat + ' in sidebar order (' + $n + ')')
  if ($n -gt 0 -and (($head -join '|') -ne ($exp -join '|'))) {
    Write-Host ('    MISMATCH expected [' + ($exp -join '|') + ']')
  }
  if ($arr.Count -gt $n) { Write-Host ('    (cross-catalog tail: ' + (@($arr | Select-Object -Skip $n) -join '|') + ')') }
}
Write-Host ('  errs=' + (Errs))
Ok ((Errs) -eq '[]') 'no JS/API errors on the dashboard'

Step '2) no TAB may be card-less; material warehouse summary == per-warehouse breakdown'
# 2026-09-27 (user report: the material-warehouse dashboard TAB is blank): that TAB shipped with quick links only and
# zero stat cards, so next to the other TABs (all opening with a stat-card row) it looked like a blank page.
# Three assertions keep it from coming back:
#   a) GENERIC INVARIANT: each of the 7 module TABs has >= 1 .stat-card (memo / overview are not module TABs
#      and deliberately use other shapes);
#   b) the material TAB's 4 card labels are all present (labels via the zh json keys) and parse as numbers;
#   c) SUMMARY == DETAIL: per-warehouse table columns add up to the summary cards (two independent SQL
#      queries -- equality proves the scopes did not drift).
$counts = (EvalJs "(function(){const ids=['dev','outsource','materialWarehouse','purchase','sale','stock','finance'];return ids.map(id=>{const p=document.querySelector('#pane-'+id);return id+'='+(p?p.querySelectorAll('.stat-card').length:-1)}).join('|')})()") -replace '"', ''
$counts = $counts.Trim()
Write-Host ('  stat-card counts: ' + $counts)
foreach ($id in @('dev', 'outsource', 'materialWarehouse', 'purchase', 'sale', 'stock', 'finance')) {
  $hit = @($counts -split '\|' | Where-Object { $_ -like "$id=*" })
  $n = if ($hit.Count) { [int]($hit[0] -replace '^.*=', '') } else { -1 }
  Ok ($n -ge 1) ("pane $id has stat cards (" + $n + ") -- 'blank TAB' regression guard")
}

$detail = (EvalJs "(function(){const p=document.querySelector('#pane-materialWarehouse');if(!p)return 'NOPANE';const T=e=>e?(e.textContent||'').trim():'';const cards=[...p.querySelectorAll('.stat-card')].map(c=>T(c.querySelector('.stat-label'))+'='+T(c.querySelector('.stat-value')));const head=T(p.querySelector('.el-card .section-title'));const tb=p.querySelector('.el-table');const rows=tb?[...tb.querySelectorAll('.el-table__body tr')].map(tr=>[...tr.querySelectorAll('td')].map(T).join('~')):[];return 'CARDS:'+cards.join('|')+'#HEAD:'+head+'#N:'+rows.length+'#'+rows.join('|')})()") -replace '"', ''
$detail = $detail.Trim()
Write-Host ('  material pane: ' + $detail)
$cards = @()
if ($detail -match 'CARDS:(.*?)#HEAD:') { $cards = @($Matches[1] -split '\|' | Where-Object { $_ -ne '' }) }
$rowsCount = -1
if ($detail -match '#N:(\d+)#') { $rowsCount = [int]$Matches[1] }
$rows = @()
if ($detail -match '#N:\d+#(.*)$' -and $Matches[1].Trim() -ne '') { $rows = @($Matches[1] -split '\|') }
function CardVal([string]$label) {
  $hit = @($cards | Where-Object { $_ -like "$label=*" })
  if (-not $hit.Count) { return $null }
  return [double](($hit[0] -replace '^.*=', '') -replace ',', '')
}
$exp = @{ items = (ZH 'card_mw_items'); good = (ZH 'card_mw_good'); onsite = (ZH 'card_mw_onsite'); pending = (ZH 'card_mw_pending') }
foreach ($k in @('items', 'good', 'onsite', 'pending')) {
  $v = CardVal $exp[$k]
  Ok ($null -ne $v) ('material card present and numeric: ' + $k)
}
Ok ($detail -match ('#HEAD:' + [regex]::Escape((ZH 'section_mw_wh')))) 'per-warehouse table section rendered'
Ok ($rowsCount -ge 1) ('per-warehouse table has rows (' + $rowsCount + ')')
$sumGood = 0.0; $sumOnsite = 0.0
foreach ($r in $rows) {
  $c = @($r -split '~')
  if ($c.Count -ge 4) { $sumGood += [double](($c[2] -replace ',', '')); $sumOnsite += [double](($c[3] -replace ',', '')) }
}
Write-Host ('  sum(qty)=' + $sumGood + ' card=' + (CardVal $exp.good) + ' | sum(onsite)=' + $sumOnsite + ' card=' + (CardVal $exp.onsite))
Ok ($sumGood -eq (CardVal $exp.good)) 'material stock card == sum of per-warehouse table (same scope)'
Ok ($sumOnsite -eq (CardVal $exp.onsite)) 'on-site repair card == sum of per-warehouse table (same scope)'
Ok ((Errs) -eq '[]') 'still no JS/API errors after reading the material TAB'

Summary 'dashboard quick links follow the current submenu order (P14)'
