# 退货整理页优化 (2026-09-19): browser smoke test for the NEW tabbed layout.
#   TAB1 待整理 (default): cross-warehouse pending overview, tri-state 可整理/实物不足/已整理完,
#         multi-select + 批量生成整理草稿, per-row / per-warehouse 抽屉开单.
#   TAB2 整理单 (?tab=bills): the original list + 新增退货整理.
# The create/audit flow itself stays in ui-e2e-p6c-exchange-returnsort.ps1 (now opens ?tab=bills).
# Creates NO data, rerunnable. ASCII ONLY.
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

# visible pending-pane rows across all warehouse collapse tables
$rowsJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);return String(ts.reduce((n,t)=>n+t.querySelectorAll('.el-table__body tbody tr').length,0))})()"
# cleared-labelled rows + exact-text 整理 buttons inside them (gating: non-sortable rows must have no 整理 action)
$zk = B64 (ZH 'st_cleared')
$zb = B64 (ZH 'btn_sort_row')
$gateJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const K=T('$zk'),B=T('$zb');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);let c=0,s=0;for(const t of ts){for(const tr of t.querySelectorAll('.el-table__body tbody tr')){if((tr.innerText||'').indexOf(K)>=0){c++;s+=[...tr.querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===B).length}}}return c+','+s})()"
# the batch button state (rendered / disabled)
$zbt = B64 (ZH 'btn_batch_draft')
$batchJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const B=T('$zbt');const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===B);if(!bs.length)return '0/-';return bs.length+'/'+(bs[0].disabled?'disabled':'enabled')})()"

Step 'open /inventory/return-sort -> default tab must be 待整理'
Open '/inventory/return-sort' 3400
ClearErrs | Out-Null
$tabs = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-tabs__item')].filter(vis).map(e=>(e.innerText||'').replace(/\s+/g,' ').trim()))})()"
Write-Host ('  tabs = ' + $tabs)
Ok ($tabs -match (ZH 'tab_rs_pending')) 'tab 待整理 exists'
Ok ($tabs -match (ZH 'tab_rs_bills')) 'tab 整理单 exists'
$sum = Txt '.pending-summary'
Write-Host ('  summary = ' + $sum)
Ok (($sum -ne 'NOEL') -and ($sum -match '\d')) 'pending overview summary rendered (warehouses/batches/quantities)'
$first = [int](EvalJs $rowsJs)
Write-Host ('  visible pending rows (default, cleared hidden) = ' + $first)

Step 'toggle 显示已整理完 -> the historical (cleared) batches are listed'
$sw = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ss=[...document.querySelectorAll('.el-switch')].filter(vis);if(!ss.length)return 'NOSW';ss[0].click();return 'OK'})()"
Write-Host ('  toggle first switch = ' + $sw)
Start-Sleep -Milliseconds 1800
$second = [int](EvalJs $rowsJs)
Write-Host ('  visible pending rows (includeCleared) = ' + $second)
Ok ($sw -eq 'OK') 'the 显示已整理完 switch is clickable'
Ok ($second -ge $first) ('includeCleared never hides rows (' + $first + ' -> ' + $second + ')')
$dbBatches = D (SqlOne "SELECT COUNT(*) FROM after_sale_pending p JOIN warehouse w ON w.id=p.warehouse_id WHERE w.warehouse_category='INVENTORY' AND w.warehouse_type='FINISHED'")
Ok (($dbBatches -gt 0) -and ($second -eq $dbBatches)) ('the overview lists every pending batch of the finished warehouses (' + $second + '/' + $dbBatches + ')')

Step 'tri-state gating: 已整理完 rows must not offer the 整理 action'
$g = (EvalJs $gateJs) -split ','
Write-Host ('  clearedRows=' + $g[0] + ' sortButtonsInThose=' + $g[1])
Ok ($g.Count -ge 2) 'tri-state probe returned both counters'
Ok ([int]$g[0] -gt 0) ('cleared rows are labelled 已整理完 (got ' + $g[0] + ')')
Ok ([int]$g[1] -eq 0) ('no 整理 button on non-sortable rows (got ' + $g[1] + ')')

Step 'batch button gating: rendered in the pending toolbar'
$b = EvalJs $batchJs
Write-Host ('  batch button = ' + $b)
Ok ($b -notmatch '^0/') '批量生成整理草稿 button is rendered'
Ok ($b -match 'disabled') 'batch button starts disabled (nothing selected yet)'

Step 'switch to 整理单 tab -> the original list + 新增退货整理 are reachable'
$tap = ClickText (ZH 'tab_rs_bills')
Write-Host ('  click tab = ' + $tap)
Start-Sleep -Milliseconds 1600
$bills = Rows 0
Write-Host ('  bills rows=' + $bills.n + ' head=' + ($bills.head -join '|'))
Ok ($null -ne $bills) ('整理单 table rendered (' + $bills.n + ' rows)')
Ok ((ClickBtn 'btn_new_sort') -match 'OK') '新增退货整理 button is reachable on the 整理单 tab'
Start-Sleep -Milliseconds 1400
$path = EvalJs 'String(location.pathname)'
Ok ($path -match '/inventory/return-sort/add') ('新增退货整理 opens its own page (' + $path + ')')

Step 'source-bill column: doc number only (no source-type tag) and the number opens the source document'
Open '/inventory/return-sort' 3200
Start-Sleep -Milliseconds 1200
# show cleared batches too -- every pending row keeps its source bill, so the probe always has rows to inspect
$sw2 = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ss=[...document.querySelectorAll('.el-switch')].filter(vis);if(!ss.length)return 'NOSW';ss[0].click();return 'OK'})()"
Write-Host ('  toggle first switch = ' + $sw2)
Start-Sleep -Milliseconds 1800
# labels of the dropped type tag (AfterSaleSourceTypeLabel) + doc-number shape (XTH-20260921... / HH-20260918...)
$zRet = B64 (ZH 'opt_sale_in')
$zExc = B64 (ZH 'opt_sale_ex')
$codeRe = '[A-Z]{2,4}-\d{5,}'
$probeJs = "(function(){const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;const R=T('$zRet'),X=T('$zExc');const re=/$codeRe/;const ts=[...document.querySelectorAll('.el-table')].filter(vis);let tagged=0;const codes=[];for(const t of ts){for(const tr of t.querySelectorAll('.el-table__body tbody tr')){const txt=(tr.innerText||'');if(txt.indexOf(R)>=0||txt.indexOf(X)>=0)tagged++;for(const b of [...tr.querySelectorAll('button')].filter(vis)){const tx=(b.innerText||'').trim();if(re.test(tx))codes.push(tx)}}}return tagged+'|'+codes.length+'|'+(codes[0]||'')})()"
$p = @((EvalJs $probeJs) -split '\|')
Write-Host ('  typeTags=' + $p[0] + ' codeLinks=' + $p[1] + ' first=' + $p[2])
Ok ($p.Count -ge 3) 'source-bill probe returned (tag count / link count / first number)'
Ok ([int]$p[0] -eq 0) ('no source-type tag in the pending rows any more (got ' + $p[0] + ')')
Ok ([int]$p[1] -gt 0) ('the source doc number is a clickable link (' + $p[1] + ' rows)')
$clickJs = "(function(){const vis=e=>e.getClientRects().length>0;const re=/$codeRe/;const ts=[...document.querySelectorAll('.el-table')].filter(vis);for(const t of ts){for(const b of [...t.querySelectorAll('.el-table__body button')].filter(vis)){const tx=(b.innerText||'').trim();if(re.test(tx)){b.click();return tx}}}return 'NOCODE'})()"
$clicked = EvalJs $clickJs
Write-Host ('  clicked = ' + $clicked)
Start-Sleep -Milliseconds 1800
$sp = EvalJs 'String(location.pathname)'
Write-Host ('  source detail path = ' + $sp)
Ok ($clicked -ne 'NOCODE') 'a source-doc number was clicked'
Ok ($sp -match '/(sale/return|sale/exchange)/detail/[0-9]+') ('clicking the number opens that source document (' + $sp + ')')

Write-Host ('errs=' + (Errs))
Summary 'return-sort overview tab'
