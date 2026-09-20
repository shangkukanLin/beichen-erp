# Purchase-analysis label rename verification (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per project convention:
# a .ps1 holding raw Chinese without a BOM gets decoded as GBK by PS 5.1 and breaks the parser.
#
# 2026-09-21 (text only, no logic): pie card titles "direct-purchase finished goods" -> "purchased
# finished goods", "outsourced finished-goods receipt" -> "outsourcing"; detail card title drops the
# "(drill down)" suffix.
# Asserts: the three NEW labels render EXACTLY, the three OLD labels are gone from the page body,
# both pie cards still render, no JS/API errors. Pie container ids are unchanged, so the existing
# verify-purchase-pie*.ps1 scripts keep working.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function FromB64([string]$s) { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) }

$B_O1 = B64 (ZH 'old_name_direct_purchase')
$B_O2 = B64 (ZH 'old_name_outsource_in')
$B_O3 = B64 (ZH 'old_name_detail_drill')

$js = "(()=>{" +
  "const D=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));" +
  "const B=s=>{var u=new TextEncoder().encode(s),x='';u.forEach(function(c){x+=String.fromCharCode(c)});return btoa(x);};" +
  "const O1=D('$B_O1'),O2=D('$B_O2'),O3=D('$B_O3');" +
  "var out={};" +
  "out.pieTitlesB64=[].slice.call(document.querySelectorAll('.pie-grid .pie-card')).map(function(c){" +
  "var t=c.querySelector('.pie-title');return B(t?t.innerText.trim():'');});" +
  "var ct=document.querySelector('.card-title');out.cardTitleB64=B(ct?ct.innerText.trim():'');" +
  "out.pieCount=out.pieTitlesB64.length;" +
  "var body=document.body.innerText;" +
  "out.oldHits=[body.indexOf(O1),body.indexOf(O2),body.indexOf(O3)];" +
  "return JSON.stringify(out);})()"

EnsureLogin | Out-Null
Open '/analysis/purchase' 5000
ClearErrs | Out-Null
Start-Sleep -Milliseconds 2500
$raw = (EvalJs $js).Trim()
Write-Host ('  probe = ' + $raw)
$j = $null
try { $j = $raw | ConvertFrom-Json } catch { Ok $false ('probe parse failed: ' + $raw) }

if ($null -ne $j) {
  $titles = @(@($j.pieTitlesB64) | ForEach-Object { FromB64 $_ })
  Write-Host ('  pieCount = ' + $j.pieCount)
  foreach ($t in $titles) { Write-Host ('  card title = ' + $t) }
  Write-Host ('  detail card title = ' + (FromB64 $j.cardTitleB64))

  Ok ([int]$j.pieCount -eq 2) 'both pie cards render'
  Ok ($titles -contains (ZH 'pie_title_purchase')) 'pie card title is exactly the NEW purchase name (not the old one)'
  Ok ($titles -contains (ZH 'pie_title_outsource')) 'pie card title is exactly the NEW outsource name (not the old one)'
  Ok ((FromB64 $j.cardTitleB64) -eq (ZH 'card_title_purchase_detail')) 'detail card title is exactly the NEW name (drill suffix removed)'
  Ok ([int]$j.oldHits[0] -eq -1) 'OLD purchase name no longer appears on the page'
  Ok ([int]$j.oldHits[1] -eq -1) 'OLD outsource name no longer appears on the page'
  Ok ([int]$j.oldHits[2] -eq -1) 'OLD detail-card name no longer appears on the page'
  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'no JS/API errors'
}
Summary 'purchase analysis label rename'
