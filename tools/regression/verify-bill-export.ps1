# Guard (2026-09-29, permanent) for the user rule "bill detail must offer export", at the PROFESSIONAL level
# (backend POI statement, NOT the old client-side SheetJS data dump).
# ASCII ONLY on purpose (no BOM in this file; PS 5.1 would read Chinese literals as GBK). Chinese text
# comes from ui-e2e-zh.json via ZH (the composite key `text_statement_parts`) or straight from the DB.
#
# PART A -- API + file contract. GET /api/finance/bill/{id}/export is fetched in the browser, the .xlsx
#   is saved, then UNZIPPED (xlsx = zip) and asserted item by item. This is the only way to prove
#   "professional": a community-edition SheetJS export cannot carry fonts/fills/borders/print setup at all.
#     workbook.xml : sheet named <type>statement ; repeated print titles (_xlnm.Print_Titles)
#     sheet1.xml   : landscape + fit-to-width, headerFooter with page numbers, freeze pane, mergeCells,
#                    3 real SUM() formulas over exactly the data rows, row count == items + 12,
#                    money cells ARE numeric (<v>) and NOT text (t="s")
#     styles.xml   : title 16pt, header fill D9E1F2, total fill FFF2CC, red font C00000, numFmtId="4"
#     sharedStrings: bill no / partner / company letterhead / partner contact+phone / DRAFT marker /
#                    capital-amount line (Chinese numerals) / signature line / overdue word when overdue
# PART B -- UI. The export button on the bill detail page must exist by its exact label, trigger a NON-EMPTY
#   .xlsx download and leave the page free of JS/API errors.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
# $env:MYSQL_PWD + -uroot (NOT -proot): the client prints "mysql: [Warning] Using a password ... insecure"
# on STDOUT, so a naive "take the first line" read silently returns the WARNING instead of the value.
$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}
# Fetch the exported workbook in the browser (token from localStorage) and stash it on window.__x.
# Returns only the SMALL meta JSON; the base64 body is pulled back in CHUNKS by ExportBytes below
# (a single EvalJs carrying ~10KB+ of base64 comes back truncated, which silently breaks ConvertFrom-Json).
function ExportPrep([string]$id) {
  $pb = B64 ('/finance/bill/' + $id + '/export')
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const P=T('$pb');const t=localStorage.getItem('beichen_erp_token')||'';return fetch('/api'+P,{headers:{satoken:t}}).then(async r=>{const ab=await r.arrayBuffer();const b=new Uint8Array(ab);let s='';for(let i=0;i<b.length;i+=8192){s+=String.fromCharCode.apply(null,b.subarray(i,i+8192))}window.__x={status:r.status,ct:(r.headers.get('content-type')||''),cd:(r.headers.get('content-disposition')||''),len:b.length,b64:btoa(s)};return JSON.stringify({status:r.status,ct:(r.headers.get('content-type')||''),cd:(r.headers.get('content-disposition')||''),len:b.length})}).catch(e=>'FETCHERR:'+e)})()"
  return (EvalJs $js)
}
# Pull the stashed base64 back in 12KB slices and write the real .xlsx; returns the byte count.
function ExportBytes([string]$xlsx) {
  $len = [int](EvalJs 'String((window.__x&&window.__x.len)||0)')
  $b64 = ''
  for ($i = 0; $i -lt ($len * 2); $i += 12000) {
    $b64 += (EvalJs ('window.__x.b64.substring(' + $i + ',' + ($i + 12000) + ')'))
  }
  [System.IO.File]::WriteAllBytes($xlsx, [Convert]::FromBase64String($b64))
  return $len
}
function UnzipBook([string]$xlsx, [string]$dir) {
  if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
  $zip = "$xlsx.zip"
  Copy-Item $xlsx $zip -Force
  Expand-Archive -Path $zip -DestinationPath $dir -Force
}
function XmlText([string]$p) { if (-not (Test-Path $p)) { return '' }; return (Get-Content -Raw -Encoding UTF8 $p) }

$parts = (ZH 'text_statement_parts') -split '\|'
$typePay = $parts[0]; $typeRecv = $parts[1]; $unpaidWord = $parts[2]; $draftMark = $parts[3]
$capitalLabel = $parts[4]; $signLabel = $parts[5]; $numeral = $parts[6]; $overdueWord = $parts[7]
$btnTxt = ZH 'btn_export_excel_ws'
$doneTxt = ZH 'text_export_bill_done'
$company = SqlOne 'SELECT company_name FROM sys_company ORDER BY id LIMIT 1'

# fixture A: the PAYABLE bill with the most items (drives the whole professional layout).
# NOTE: ONE VALUE PER QUERY on purpose: a CONCAT-with-separator row + -split is fragile here -- a mangled
# row silently yields a NON-EXISTENT bill id, and the API then (correctly) answers "bill not found",
# which shows up as 30 unrelated failures instead of one honest fixture error.
$billId = SqlOne 'SELECT b.id FROM finance_bill b WHERE b.bill_type=''PAYABLE'' ORDER BY (SELECT COUNT(*) FROM finance_bill_item i WHERE i.bill_id=b.id) DESC, b.id DESC LIMIT 1'
$pNo = SqlOne ('SELECT IFNULL(bill_no,'''') FROM finance_bill WHERE id=' + $billId)
$pName = SqlOne ('SELECT IFNULL(partner_name,'''') FROM finance_bill WHERE id=' + $billId)
$pStatus = SqlOne ('SELECT IFNULL(status,'''') FROM finance_bill WHERE id=' + $billId)
$pPartner = SqlOne ('SELECT IFNULL(partner_id,0) FROM finance_bill WHERE id=' + $billId)
$pItems = [int](SqlOne ('SELECT COUNT(*) FROM finance_bill_item WHERE bill_id=' + $billId))
$pUnpaid = SqlOne ('SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_bill_item WHERE bill_id=' + $billId)
$pOverdue = [int](SqlOne ('SELECT COUNT(*) FROM finance_bill_item WHERE bill_id=' + $billId + ' AND unpaid_amount>0 AND due_date IS NOT NULL AND due_date < CURDATE()'))
Write-Host ('[BASE] payable bill id=' + $billId + ' no=' + $pNo + ' partner=' + $pName + ' status=' + $pStatus + ' items=' + $pItems + ' unpaid=' + $pUnpaid + ' overdueRows=' + $pOverdue)
Ok (($billId -ne '') -and ($pItems -gt 0) -and ($pNo -ne '')) 'fixture: a PAYABLE bill with items resolved (id/no/items all read back)'
Ok ($pStatus -eq 'DRAFT') 'fixture is a DRAFT bill (draft marker must be printed)'

Write-Host '--- PART A1) fetch + headers ---'
$meta1 = ExportPrep $billId
Write-Host ('meta: ' + $meta1)
Ok ($meta1 -match '"status":200') ('export endpoint returned 200 (' + $meta1.Substring(0, [Math]::Min(140, $meta1.Length)) + ')')
# NOTE: fields are read with REGEX, not ConvertFrom-Json -- PS 5.1's JSON parser throws ArgumentException
# on this payload (and a silent $null then turns one honest failure into thirty confusing ones).
$ct1 = ([regex]::Match($meta1, '"ct":"([^"]*)"')).Groups[1].Value
$cd1 = ([regex]::Match($meta1, '"cd":"([^"]*)"')).Groups[1].Value
$len1 = [int](([regex]::Match($meta1, '"len":([0-9]+)')).Groups[1].Value)
Ok ($ct1 -match 'spreadsheetml.sheet') ('content-type is xlsx (' + $ct1 + ')')
Ok ($cd1 -match 'filename\*=UTF-8') 'Content-Disposition uses RFC5987 filename* (Chinese-safe file name)'
$m = [regex]::Match($cd1, "filename\*=UTF-8''([^;]+)")
$decoded = ''
if ($m.Success) { $decoded = [System.Uri]::UnescapeDataString($m.Groups[1].Value) }
Write-Host ('file name -> ' + $decoded)
Ok (($decoded -ne '') -and ($decoded -match [regex]::Escape($pNo))) 'file name carries the bill no'
Ok ($decoded -match [regex]::Escape($typePay)) 'file name carries the statement type (payable statement)'
Ok ($len1 -gt 5000) ('exported workbook is non-trivial (' + $len1 + ' bytes)')

$xlsx = Join-Path $env:TEMP 'bill-statement-payable.xlsx'
$sz1 = ExportBytes $xlsx
Ok ($sz1 -eq $len1) ('binary transferred intact in chunks (' + $sz1 + ' bytes)')
$dir = Join-Path $env:TEMP 'bill-statement-payable'
UnzipBook $xlsx $dir
$wbx = XmlText (Join-Path $dir 'xl\workbook.xml')
$sh = XmlText (Join-Path $dir 'xl\worksheets\sheet1.xml')
$st = XmlText (Join-Path $dir 'xl\styles.xml')
$ss = XmlText (Join-Path $dir 'xl\sharedStrings.xml')
Ok (($wbx -ne '') -and ($sh -ne '') -and ($st -ne '') -and ($ss -ne '')) 'xlsx unzips into workbook/sheet/styles/sharedStrings parts'

Write-Host '--- PART A2) layout contract ---'
Ok ($wbx -match [regex]::Escape($typePay)) 'sheet is named <type>statement'
Ok ($wbx -match '_xlnm.Print_Titles') 'print titles repeated on every page (header row)'
Ok ($sh -match 'orientation="landscape"') 'print setup: landscape'
Ok ($sh -match 'fitToWidth="1"|fitToPage') 'print setup: fit to one page wide'
Ok ($sh -match '&amp;P') 'footer prints page numbers (&P)'
Ok ($sh -match '<pane') 'freeze pane below the table header'
Ok ($sh -match '<mergeCell') 'merged cells (title / info block / totals / signature)'
# 2026-09-29 regression (user report: the counterparty / document-date LABELS showed clipped): the
# info-block LABELS must span >= 2 columns. Column A is only 6 wide (sized for the seq-no column), so a
# 5-CJK-char label sitting in A alone gets clipped by Excel -- the neighbouring cell holds the value.
# Measured from the sheet's own <col> widths, so this fails on the old (clipped) layout and passes now.
$colw = @{}
foreach ($cm in [regex]::Matches($sh, '<col min="(\d+)" max="(\d+)" width="([0-9.]+)"')) {
  for ($ci = [int]$cm.Groups[1].Value; $ci -le [int]$cm.Groups[2].Value; $ci++) { $colw[$ci] = [double]$cm.Groups[3].Value }
}
$spanW = {
  param([string]$ref)
  $mm = [regex]::Match($ref, '^([A-Z]+)\d+:([A-Z]+)\d+$')
  if (-not $mm.Success) { return 0.0 }
  $a = [int][char]$mm.Groups[1].Value - 64; $b = [int][char]$mm.Groups[2].Value - 64
  $w = 0.0
  for ($ci = $a; $ci -le $b; $ci++) { $w += [double]$colw[$ci] }
  return $w
}
foreach ($lr in 5, 6, 7) {                       # 1-based rows: counterparty / date / remark (labels)
  $ref = 'A' + $lr + ':B' + $lr
  $w = & $spanW $ref
  Ok ($sh -match ('ref="' + $ref + '"')) ('info label merged across 2 cols (' + $ref + ')')
  Ok ($w -ge 16) ('label cell wide enough for 5 CJK chars (' + $ref + ' = ' + $w + ' >= 16)')
}
Ok ($st -match 'wrapText="(1|true)"') 'long note line wraps instead of being clipped (POI writes wrapText="true")'
$sums = ([regex]::Matches($sh, 'SUM\(')).Count
Ok ($sums -eq 3) ('total row has 3 real SUM formulas (D/E/F), got ' + $sums)
$rows = ([regex]::Matches($sh, '<row ')).Count
Ok ($rows -eq ($pItems + 12)) ('row count == items + 12 layout rows (' + $rows + ' for ' + $pItems + ' items)')
$firstData = 9; $lastData = 8 + $pItems
Ok ($sh -match [regex]::Escape('SUM(D' + $firstData + ':D' + $lastData + ')')) ('SUM range covers exactly the data rows (D9:D' + $lastData + ')')
# Anchor on the FIRST DATA ROW: the header row's amount cell legitimately IS a shared string (t="s"),
# so a table-wide "no t=s in column D" check fails for the wrong reason.
$dcell = [regex]::Match($sh, 'r="D' + $firstData + '"[^>]*>')
Ok ($dcell.Success) ('money cell of the first data row exists (D' + $firstData + ')')
Ok ($dcell.Value -match 't="n"') 'money cells are NUMERIC (t="n") -- usable for further analysis'
Ok ($dcell.Value -notmatch 't="s"') 'money cells are NOT text (no "left-aligned string" trap)'

Write-Host '--- PART A3) style contract (impossible with community SheetJS) ---'
Ok ($st -match 'val="16\.0"') 'statement title font size 16pt present (POI writes 16.0)'
Ok ($st -match 'D9E1F2') 'table header fill (light blue D9E1F2) present'
Ok ($st -match 'FFF2CC') 'totals fill (light yellow FFF2CC) present'
Ok ($st -match 'C00000') 'red font present (overdue emphasis / draft warning)'
Ok ($st -match 'numFmtId="4"') 'thousands+2dp number format (#,##0.00) applied'

Write-Host '--- PART A4) content contract ---'
Ok (($company -ne '') -and ($ss -match [regex]::Escape($company))) ('company letterhead printed (' + $company + ')')
Ok (($pNo -ne '') -and ($ss -match [regex]::Escape($pNo))) 'bill no printed in the header block'
Ok (($pName -ne '') -and ($ss -match [regex]::Escape($pName))) 'partner (counterparty) printed'
Ok ($ss -match [regex]::Escape($draftMark)) 'DRAFT marker printed in the title (not passed off as final)'
Ok ($ss -match [regex]::Escape($signLabel)) 'signature block (maker/auditor/counterparty confirm) printed'
Ok ($ss -match [regex]::Escape($unpaidWord)) 'unpaid column header printed'
# 2026-09-29 user rule: NO combined wording like 已收付/未收付 -- the wording must follow the statement type
# (payable = 已付/未付, receivable = 已收/未收). The combined term is the forbidden needle here.
$neutralWord = $parts[8]
Ok ($ss -notmatch [regex]::Escape($neutralWord)) ('payable statement does NOT use the combined term (' + $neutralWord + ')')
$contact = SqlOne ('SELECT IFNULL(contact,'''') FROM supplier WHERE id=' + $pPartner)
if ($contact -ne '') { Ok ($ss -match [regex]::Escape($contact)) ('partner contact taken from supplier master (' + $contact + ')') }
else { Write-Host 'INFO supplier has no contact on file -> only assert the label' }
$cap = (([regex]::Matches($ss, '<t>[^<]*</t>') | ForEach-Object { $_.Value }) | Where-Object { $_ -match [regex]::Escape($capitalLabel) } | Select-Object -First 1)
Write-Host ('capital line -> ' + $cap)
Ok ($cap -ne $null) 'capital amount line (unpaid total in words) printed'
if ([decimal]$pUnpaid -ge 1) { Ok ($cap -match [regex]::Escape($numeral)) 'capital amount uses Chinese numerals (not a placeholder)' }
if ($pOverdue -gt 0) { Ok ($ss -match [regex]::Escape($overdueWord)) ('overdue marked on ' + $pOverdue + ' rows') }

Write-Host '--- PART A5) receivable bill uses its own title ---'
$recId = SqlOne 'SELECT b.id FROM finance_bill b WHERE b.bill_type=''RECEIVABLE'' ORDER BY (SELECT COUNT(*) FROM finance_bill_item i WHERE i.bill_id=b.id) DESC, b.id DESC LIMIT 1'
if ($recId -ne '') {
  $meta2 = ExportPrep $recId
  Ok ($meta2 -match '"status":200') 'receivable bill exported (200)'
  $x2 = Join-Path $env:TEMP 'bill-statement-receivable.xlsx'
  ExportBytes $x2 | Out-Null
  $d2 = Join-Path $env:TEMP 'bill-statement-receivable'
  UnzipBook $x2 $d2
  $wbx2 = XmlText (Join-Path $d2 'xl\workbook.xml')
  Ok ($wbx2 -match [regex]::Escape($typeRecv)) 'receivable statement is titled receivable-statement (type-driven, not hardcoded)'
  $ss2 = XmlText (Join-Path $d2 'xl\sharedStrings.xml')
  $recvWord = $parts[9]
  Ok ($ss2 -match [regex]::Escape($recvWord)) ('receivable statement uses its own wording (' + $recvWord + ')')
  Ok ($ss2 -notmatch [regex]::Escape($unpaidWord)) 'receivable statement does NOT borrow the payable wording'
} else { Write-Host 'INFO no receivable bill in DB -> skipped' }

Write-Host '--- PART B) UI: export button triggers a real download ---'
Open ('/finance/bill/detail/' + $billId) 3600
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1700
$hook = EvalJs "(()=>{window.__dl=[];const co=URL.createObjectURL.bind(URL);URL.createObjectURL=b=>{window.__dl.push('blob:'+b.size+':'+b.type);return co(b)};const ck=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){window.__dl.push('name:'+(this.download||''));return ck.apply(this,arguments)};return 'HOOKS'})()"
Ok ($hook -eq 'HOOKS') 'download capture hooks installed'
$bb = B64 $btnTxt
$found = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$bb');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(e=>(e.innerText||'').trim()===N);if(!b)return 'NOBTN';b.click();return 'CLICKED'})()"
Write-Host ('click: ' + $found)
Ok ($found -eq 'CLICKED') ('export button found by EXACT text and clicked (' + $btnTxt + ')')
Start-Sleep -Milliseconds 2500
$toast = Txt '.el-message'
$dl = EvalJs 'JSON.stringify(window.__dl||[])'
Write-Host ('toast: ' + $toast)
Write-Host ('captured: ' + $dl)
Ok ($dl -match '\.xlsx') 'a .xlsx download was triggered from the UI'
Ok ($dl -match 'blob:[1-9][0-9]*:') 'downloaded workbook is non-empty'
Ok ($toast -match [regex]::Escape($doneTxt)) ('success toast shown (' + $doneTxt + ')')
Ok ((Errs) -eq '[]') 'bill detail page has no JS/API errors'
Summary 'verify bill statement export (professional, backend POI)'
