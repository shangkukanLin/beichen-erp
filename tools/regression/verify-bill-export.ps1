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
#
# 2026-10-02 BUGFIX -- why every CONTENT assertion below used to fail while the fetch itself was fine:
#   this pulled window.__x.b64 back through EvalJs in 12000-char slices, but a single EvalJs return is
#   truncated FAR below that on this host (measured: the button click / fetch answered 200 with 5113 bytes
#   while the file written from the chunks was corrupt, so "unzips into parts" + 30 content checks failed).
#   => keep the in-browser fetch above (it is what proves status / content-type / RFC5987 file name) and
#   download the SAME url straight from PowerShell with the same auth header, which yields the real bytes.
#   $id is required for that; the caller compares the size with the browser's Content-Length ($len1).
function ExportBytes([string]$id, [string]$xlsx) {
  # ⚠️ two traps here, both measured:
  #   1) the snippet MUST contain a space -- a space-free argument is handed to cmd.exe unquoted and a literal
  #      '||' is then parsed as a pipe ("String(localStorage...||'')" returned an EVAL ERROR, not a token).
  #   2) strip whitespace: the EvalJs return path can carry trailing junk, and a header value with a newline
  #      makes Invoke-WebRequest throw "invalid CRLF characters".
  $tok = ((EvalJs "(()=>{const t = localStorage.getItem('beichen_erp_token'); return t ? t : 'none'})()") -replace '\s', '')
  if (($tok -eq '') -or ($tok -eq 'none')) { Write-Host '  (no token -> cannot download the workbook here)'; return 0 }
  $u = 'http://localhost:8080/api/finance/bill/' + $id + '/export'
  try { Invoke-WebRequest -Uri $u -Headers @{ Authorization = $tok } -OutFile $xlsx -TimeoutSec 25 | Out-Null } catch { Write-Host ('  download error: ' + $_.Exception.Message); return 0 }
  if (-not (Test-Path $xlsx)) { return 0 }
  return [int](Get-Item $xlsx).Length
}
function UnzipBook([string]$xlsx, [string]$dir) {
  if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
  $zip = "$xlsx.zip"
  Copy-Item $xlsx $zip -Force
  Expand-Archive -Path $zip -DestinationPath $dir -Force
}
function XmlText([string]$p) { if (-not (Test-Path $p)) { return '' }; return (Get-Content -Raw -Encoding UTF8 $p) }
# page text coming back through EvalJs is decoded as GBK on this host -> probes must return base64 (same
# convention as scan-col-truncation.ps1 and verify-bill-list-filter.ps1)
function Dec([string]$b) {
  if ([string]::IsNullOrEmpty($b)) { return '' }
  try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b)) } catch { return '?' }
}

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
$sz1 = ExportBytes $billId $xlsx
# ⚠️ NOT byte-exact on purpose: the workbook is REBUILT per request and POI stamps docProps/core.xml with the
# generation time, so two separate fetches of the same bill can differ by a byte or two (measured 6562 vs 6563).
# What matters is that both transfers carry the same, non-trivial workbook.
$delta = [Math]::Abs($sz1 - $len1)
Write-Host ('  sizes: direct=' + $sz1 + ' browser=' + $len1 + ' delta=' + $delta)
Ok (($sz1 -gt 5000) -and ($delta -le 64)) ('the direct download matches the browser fetch size (' + $sz1 + ' ~ ' + $len1 + ')')
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
  ExportBytes $recId $x2 | Out-Null
  $d2 = Join-Path $env:TEMP 'bill-statement-receivable'
  UnzipBook $x2 $d2
  $wbx2 = XmlText (Join-Path $d2 'xl\workbook.xml')
  Ok ($wbx2 -match [regex]::Escape($typeRecv)) 'receivable statement is titled receivable-statement (type-driven, not hardcoded)'
  $ss2 = XmlText (Join-Path $d2 'xl\sharedStrings.xml')
  $recvWord = $parts[9]
  Ok ($ss2 -match [regex]::Escape($recvWord)) ('receivable statement uses its own wording (' + $recvWord + ')')
  Ok ($ss2 -notmatch [regex]::Escape($unpaidWord)) 'receivable statement does NOT borrow the payable wording'
} else { Write-Host 'INFO no receivable bill in DB -> skipped' }

Write-Host '--- PART A6) the workbook carries the product-detail sheet (2026-10-02) ---'
# 2026-10-02 用户要求「导出里也带明细」：明细落在**第二个 sheet「产品明细」**，主表版式与行数契约**未动**
# （表头行号是冻结/重复打印/SUM 的共同锚点，插子行会三样全废且金额双计）⇒ A2 的 rows == items + 12 依旧成立。
$detailSheet = ZH 'sheet_bill_detail'
$dp = (ZH 'text_detail_parts') -split '\|'
$detailOk = $dp[0]; $detailBad = $dp[1]; $detailGoods = $dp[2]; $detailCharge = $dp[3]

# expected layout: 1 title + 1 note + 1 header + per-group (lines, or 1 row when the type has no detail) + 1 total
function ApiGet([string]$path) {
  $t = ((EvalJs "(()=>{const x = localStorage.getItem('beichen_erp_token'); return x ? x : 'none'})()") -replace '\s', '')
  if (($t -eq '') -or ($t -eq 'none')) { return $null }
  try { return Invoke-RestMethod -Uri ('http://localhost:8080/api' + $path) -Headers @{ Authorization = $t } -TimeoutSec 25 } catch { return $null }
}
$groups = ApiGet ('/finance/bill/' + $billId + '/product-items')
$expRows = -1
if ($groups -and $groups.data) {
  $inner = 0
  foreach ($g in @($groups.data)) { $n = @($g.lines).Count; if ($n -eq 0) { $inner += 1 } else { $inner += $n } }
  $expRows = 3 + $inner + 1
}
Write-Host ('  bill ' + $billId + ' product groups = ' + (@($groups.data).Count) + ' ; expected detail-sheet rows = ' + $expRows)
$wbAll = XmlText (Join-Path $dir 'xl\workbook.xml')
Ok ($wbAll -match [regex]::Escape($detailSheet)) ('workbook declares a sheet named ' + $detailSheet)
$sh2 = XmlText (Join-Path $dir 'xl\worksheets\sheet2.xml')
$rows2 = ([regex]::Matches($sh2, '<row ')).Count
Write-Host ('  sheet2 rows = ' + $rows2 + ' (' + $sh2.Length + ' chars)')
Ok ($sh2 -ne '') 'the second sheet part exists and is non-empty'
Ok (($expRows -gt 0) -and ($rows2 -eq $expRows)) ('detail sheet row count == title+note+header+lines+total (' + $rows2 + ' == ' + $expRows + ')')
Ok (([regex]::Matches($sh2, '<f>SUM')).Count -ge 1) 'detail sheet totals its amount column with a real SUM formula'
# column contract: 来源类型/来源单号/明细类型/产品名称/SKU/品质/数量/单价/金额/说明/核对 = 11 headers on row 3
$hdr2 = ([regex]::Matches($sh2, '<c r="[A-K]3"')).Count
Write-Host ('  detail header cells = ' + $hdr2)
Ok ($hdr2 -eq 11) ('detail sheet has 11 columns (' + $hdr2 + ')')
if ($groups -and $groups.data) {
  $firstLine = $null
  foreach ($g in @($groups.data)) { if (@($g.lines).Count -gt 0) { $firstLine = $g.lines[0]; break } }
  if ($firstLine) {
    $pn = [string]$firstLine.productName
    Write-Host ('  first detail product = ' + $pn)
    Ok (($pn -ne '') -and ($ss -match [regex]::Escape($pn))) ('the detail product name is printed (' + $pn + ')')
  }
  $anyCharge = @($groups.data | Where-Object { $_.lineKind -eq 'CHARGE' }).Count -gt 0
  $anyGoods = @($groups.data | Where-Object { $_.lineKind -eq 'GOODS' }).Count -gt 0
  if ($anyGoods) { Ok ($ss -match [regex]::Escape($detailGoods)) ('the detail sheet labels goods lines (' + $detailGoods + ')') }
  if ($anyCharge) { Ok ($ss -match [regex]::Escape($detailCharge)) ('the detail sheet labels charge lines (' + $detailCharge + ')') }
  $anyRecon = @($groups.data | Where-Object { $_.reconcilable -eq $true }).Count -gt 0
  if ($anyRecon) { Ok (($ss -match [regex]::Escape($detailOk)) -or ($ss -match [regex]::Escape($detailBad))) 'the detail sheet prints the per-source reconciliation result' }
}

Write-Host '--- PART B) UI: export button triggers a real export request ---'
Open ('/finance/bill/detail/' + $billId) 3600
ClearErrs | Out-Null
Start-Sleep -Milliseconds 1700
# 2026-10-02 BUGFIX: this part used to hook URL.createObjectURL / HTMLAnchorElement.click and assert a
# captured blob+download name -- that hook never fires in this headless setup (measured: both assertions
# failed on an untouched page while the backend answered 200/5113 bytes to the very same URL). What stays
# verifiable from the UI is: the click finds the button by its exact label and fires a REAL request to the
# export URL, the success toast appears, and the page logs no errors. The bytes themselves are verified in
# PART A by downloading the same URL directly.
$bb = B64 $btnTxt
$found = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$bb');const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('button')].filter(vis).find(e=>(e.innerText||'').trim()===N);if(!b)return 'NOBTN';b.click();return 'CLICKED'})()"
Write-Host ('click: ' + $found)
Ok ($found -eq 'CLICKED') ('export button found by EXACT text and clicked (' + $btnTxt + ')')
# The toast lives ~1s (measured), so poll for it immediately instead of reading after a 2.5s sleep; its text
# is Chinese -> base64 both ways (Dec above).
$toast = ''
for ($i = 0; $i -lt 12; $i++) {
  Start-Sleep -Milliseconds 250
  $tb = (EvalJs "(()=>{const e=document.querySelector('.el-message');return e?btoa(unescape(encodeURIComponent((e.innerText||'').trim()))):''})()").Trim()
  if (($tb -ne '') -and ($tb -ne "''")) { $toast = (Dec $tb); break }
}
$perfRaw = EvalJs "(()=>{const B=s=>btoa(unescape(encodeURIComponent(s||'')));const e=performance.getEntriesByType('resource').filter(x=>x.name.indexOf('/finance/bill/')>=0&&x.name.indexOf('/export')>=0);return B(e.map(x=>x.name).join('\u0001'))})()"
$perf = (((Dec $perfRaw) -split ([char]1)) -join ' | ')
Write-Host ('toast: ' + $toast)
Write-Host ('export requests seen by the page: ' + $perf)
Ok ($perf -match '/export') 'the click fired a real export request from the page'
Ok ($toast -match [regex]::Escape($doneTxt)) ('success toast shown (' + $doneTxt + ')')
Ok ((Errs) -eq '[]') 'bill detail page has no JS/API errors'
Summary 'verify bill statement export (professional, backend POI)'
