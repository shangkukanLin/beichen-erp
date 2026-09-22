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
Summary 'dashboard quick links follow the current submenu order (P14)'
