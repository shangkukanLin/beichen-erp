# Sales-analysis "product profit ranking" verification (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per the project convention:
# a .ps1 with raw Chinese but no BOM gets decoded as GBK by PS 5.1 and blows up the parser.
#
# The page's old "warehouse sales distribution" card was replaced by a "product profit ranking" table.
# Asserts:
#   1) the warehouse card is GONE and the profit-ranking card EXISTS
#   2) rows are sorted by profit DESC (strictly non-increasing)
#   3) table 合计 profit == the product-profit pie card total
#      (same response: sum(byProduct.profit) vs metrics.productProfit -> proves the front end did not
#       recompute profit and that the two views stay reconciled)
#   4) no horizontal scrollbar on the table (columns fit the card)
#   5) clicking the first row's button drills into /analysis/sale/detail with productId
#   6) no JS/API errors
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

$B_RANK = B64 (ZH 'card_sale_profit_rank')
$B_WH = B64 (ZH 'card_sale_wh_dist')
$B_PIE = B64 (ZH 'pie_product_profit')
$B_DET = B64 (ZH 'btn_detail_line_row')

$jsProbe = "(()=>{" +
  "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
  "const RANK=T('$B_RANK'),WH=T('$B_WH'),PIE=T('$B_PIE');" +
  "const nz=s=>String(s||'').replace(/[^0-9.\-]/g,'');" +
  "function hdr(c){var h=c.querySelector('.el-card__header');return h?h.innerText.replace(/\s+/g,''):'';}" +
  "var cards=[].slice.call(document.querySelectorAll('.el-card'));" +
  "var rankCard=null,whCard=null;" +
  "cards.forEach(function(c){var t=hdr(c);if(t.indexOf(RANK)>=0)rankCard=c;if(t.indexOf(WH)>=0)whCard=c;});" +
  "var out={hasRankCard:!!rankCard,hasWhCard:!!whCard,bodyHasWh:document.body.innerText.indexOf(WH)>=0?1:0};" +
  "if(rankCard){" +
  "var body=rankCard.querySelector('.el-table__body');" +
  "var rows=body?[].slice.call(body.querySelectorAll('tr')):[];" +
  "out.rowCount=rows.length;" +
  "out.names=rows.map(function(tr){var td=tr.querySelectorAll('td');return td.length>0?String(td[0].innerText).trim():'';});" +
  "out.profits=rows.map(function(tr){var td=tr.querySelectorAll('td');return td.length>1?nz(td[1].innerText):'';});" +
  "out.rates=rows.map(function(tr){var td=tr.querySelectorAll('td');return td.length>2?String(td[2].innerText).trim():'';});" +
  "var foot=rankCard.querySelector('.el-table__footer');var ftd=foot?foot.querySelectorAll('td'):[];" +
  "out.footTotal=ftd.length>1?nz(ftd[1].innerText):'';" +
  "var w=rankCard.querySelector('.el-scrollbar__wrap');out.scrollW=w?w.scrollWidth:-1;out.clientW=w?w.clientWidth:-1;" +
  "}" +
  "var pieTotal='';" +
  "[].slice.call(document.querySelectorAll('.pie-card')).forEach(function(c){" +
  "var t=c.querySelector('.pie-title');" +
  "if(t&&t.innerText.replace(/\s+/g,'').indexOf(PIE)>=0){var v=c.querySelector('.pie-total');if(v)pieTotal=v.innerText;}" +
  "});" +
  "out.pieTotal=nz(pieTotal);" +
  "return JSON.stringify(out);})()"

if ($Part -eq 0 -or $Part -eq 1) {
  Step 'profit ranking card replaces the warehouse card'
  EnsureLogin | Out-Null
  Open '/analysis/sale' 5000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 2500
  $raw = (EvalJs $jsProbe).Trim()
  Write-Host ('  probe = ' + $raw)
  $j = $null
  try { $j = $raw | ConvertFrom-Json } catch { Ok $false ('probe parse failed: ' + $raw) }

  if ($null -ne $j) {
    Ok ($j.hasRankCard -eq $true) 'product profit ranking card EXISTS'
    Ok ($j.hasWhCard -eq $false) 'warehouse distribution card is GONE'
    Ok ([int]$j.bodyHasWh -eq 0) 'page no longer contains the warehouse-distribution text'
    Ok ([int]$j.rowCount -gt 0) ('profit ranking has rows (rowCount=' + $j.rowCount + ')')
    Write-Host ('  names   = ' + (@($j.names) -join ' | '))
    Write-Host ('  profits = ' + (@($j.profits) -join ' | '))
    Write-Host ('  rates   = ' + (@($j.rates) -join ' | '))
    Write-Host ('  footTotal=' + $j.footTotal + '  pieTotal=' + $j.pieTotal)

    $vals = @()
    foreach ($p in @($j.profits)) { $vals += [double]$p }
    $sorted = $true
    for ($i = 1; $i -lt $vals.Count; $i++) { if ($vals[$i] -gt $vals[$i - 1] + 0.001) { $sorted = $false } }
    Ok $sorted 'rows are sorted by profit DESC'

    $diff = [Math]::Abs([double]$j.footTotal - [double]$j.pieTotal)
    Write-Host ('  abs(footTotal - pieTotal) = ' + $diff)
    Ok ($diff -lt 0.02) 'sum(byProduct.profit) == metrics.productProfit (product-profit pie total)'

    if ([int]$j.scrollW -gt 0) {
      Write-Host ('  scrollW=' + $j.scrollW + ' clientW=' + $j.clientW)
      Ok ([int]$j.scrollW -le [int]$j.clientW) 'profit table fits the card (no horizontal scroll)'
    }

    Write-Host ('  errs=' + (Errs))
    Ok ((Errs) -eq '[]') 'no JS/API errors'
  }
  Summary 'sale profit ranking'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Step 'drill down from the profit ranking first row'
  EnsureLogin | Out-Null
  Open '/analysis/sale' 5000
  Start-Sleep -Milliseconds 2000
  $jsDrill = "(()=>{" +
    "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
    "const RANK=T('$B_RANK'),DET=T('$B_DET');" +
    "var cards=[].slice.call(document.querySelectorAll('.el-card'));var c=null;" +
    "cards.forEach(function(x){var h=x.querySelector('.el-card__header');if(h&&h.innerText.replace(/\s+/g,'').indexOf(RANK)>=0)c=x;});" +
    "if(!c)return 'nocard';" +
    "var bs=[].slice.call(c.querySelectorAll('.el-table__body tr td:last-child button'));" +
    "var b=bs.filter(function(x){return (x.innerText||'').trim()===DET;})[0];" +
    "if(!b)return 'nobtn:'+bs.length;b.click();return 'ok';})()"
  $r = (EvalJs $jsDrill).Trim()
  Write-Host ('  click = ' + $r)
  Ok ($r -eq 'ok') 'first row has an inline detail button'
  if ($r -eq 'ok') {
    Start-Sleep -Milliseconds 2500
    $url = (EvalJs "String(location.pathname+location.search)").Trim()
    Write-Host ('  url = ' + $url)
    Ok ($url -match '^/analysis/sale/detail') ('drilled into the detail page (' + $url + ')')
    Ok ($url -match 'productId=') 'detail url carries productId'
  }
  Summary 'sale profit ranking drill'
}
