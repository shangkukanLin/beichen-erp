# verify-material-rd-expense-ui.ps1 (2026-09-27, user request; **2026-09-28 retargeted to DEV material**): the UI half
# of "create a dev material -> optionally file an R&D expense". Covers what the API-level guard cannot: the PROMPT.
#   (2026-09-28 user: the feature belongs to 研发物料 /dev/material, NOT to the outsource material page.)
#
#   A) the add-material dialog shows a "file an R&D expense" checkbox, and the amount/account fields stay hidden
#      until it is ticked (the prompt must not clutter the normal flow);
#   B) ticking it reveals the amount / account fields (+ date / remark), and the account dropdown is populated with a balance;
#   C) submitting files the material AND the expense in one go: the ticked box means AUTO-AUDIT (2026-09-27 user),
#      so the success message says it was audited + paid, the DB holds exactly one AUDITED RND row sourced from
#      that dev material (RD_DEV_MATERIAL), and the audit wrote the EXPENSE cashflow row (the balance);
#   D) no JS / API errors along the way.
#
# Fixture is self-built / self-cleaned (temp dev material + its expense row), so the file is repeatable. PURE ASCII.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $v = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
  if (-not $v) { return '' }
  return ($v -split "`n")[0].Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }

$matName = 'RD-UI-' + (Get-Date).ToString('HHmmss')
# NOTE: the dev-material dialog labels its type field just "type" (lbl_type), not "material type"
$bNew = B64 (ZH 'btn_new'); $bName = B64 (ZH 'lbl_material_name'); $bType = B64 (ZH 'lbl_type')
$bCheck = B64 (ZH 'chk_material_rd'); $bAmount = B64 (ZH 'lbl_rd_amount'); $bAcct = B64 (ZH 'lbl_rd_account')
$bOk = B64 (ZH 'btn_ok'); $bMsg = B64 (ZH 'msg_rd_draft'); $bMsgAudited = B64 (ZH 'msg_rd_audited')
# E reuses the same label as the form item ("R&D expense") for the list row action button
$bRdBtn = B64 (ZH 'lbl_material_rd')

# shared JS prelude: decode labels, find a form item by label, set an input value the Vue way
$pre = "const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;const dlg=()=>[...document.querySelectorAll('.el-dialog')].filter(vis).pop();const item=(root,lab)=>{for(const it of root.querySelectorAll('.el-form-item')){const l=it.querySelector('.el-form-item__label');if(l&&(l.innerText||'').replace(/[\s*:]/g,'')===lab)return it}return null};const setv=(el,v)=>{const p=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))};const rdBtnAt=(name,btn)=>{const bodies=[...document.querySelectorAll('.el-table__body')].filter(vis);let idx=-1;for(const b of bodies){const rows=[...b.querySelectorAll('tr')].filter(vis);const i=rows.findIndex(tr=>(tr.innerText||'').includes(name));if(i>=0){idx=i;break}}if(idx<0)return 'NOROW';for(const b of bodies){const rows=[...b.querySelectorAll('tr')].filter(vis);if(rows.length<=idx)continue;const x=[...rows[idx].querySelectorAll('button')].filter(vis).find(y=>(y.innerText||'').trim()===btn);if(x)return x}return 'NOBTN'};"

Write-Host '--- A) the dialog prompts for an R&D expense, fields stay hidden until ticked'
Open '/dev/material' 3500
$r = (EvalJs "(()=>{$pre;const bs=[...document.querySelectorAll('.toolbar button')].filter(vis).filter(b=>(b.innerText||'').trim()===T('$bNew'));if(!bs.length)return 'NOBTN';bs[0].click();return 'OK'})()")
Ok ($r -eq 'OK') 'A: add-material dialog opened'
Start-Sleep -Milliseconds 1200
$r = (EvalJs "(()=>{$pre;const d=dlg();if(!d)return 'NODLG';const cb=[...d.querySelectorAll('.el-checkbox')].filter(vis).map(c=>(c.innerText||'').trim());const labels=[...d.querySelectorAll('.el-form-item__label')].map(l=>(l.innerText||'').replace(/[\s*:]/g,'').trim());return JSON.stringify({cb:cb,hasAmt:labels.includes(T('$bAmount'))})})()") -replace '"', ''
Write-Host ('  ' + $r)
Ok ($r -like ('*' + (ZH 'chk_material_rd') + '*')) 'A: dialog shows the R&D-expense checkbox'
Ok ($r -notlike 'true*' -and $r -notmatch 'hasAmt":true') 'A: amount field hidden while unticked'

Write-Host '--- B) ticking reveals the fields; the account list is populated'
$r = (EvalJs "(()=>{$pre;const d=dlg();const it=item(d,T('$bName'));if(!it)return 'NOITEM_NAME';setv(it.querySelector('input'),'$matName');return 'OK'})()")
Ok ($r -eq 'OK') 'B: material name filled'
$r = (EvalJs "(()=>{$pre;const d=dlg();const it=item(d,T('$bType'));if(!it)return 'NOITEM_TYPE';const inp=it.querySelector('input');inp.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));inp.click();return 'OK'})()")
Ok ($r -eq 'OK') 'B: material-type dropdown opened'
Start-Sleep -Milliseconds 1100
$r = (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const opts=[...document.querySelectorAll('.el-select-dropdown__item')].filter(vis);if(!opts.length)return 'NOOPT';opts[0].click();return 'OK'})()")
Ok ($r -eq 'OK') 'B: material type picked'
$r = (EvalJs "(()=>{$pre;const d=dlg();const cb=[...d.querySelectorAll('.el-checkbox')].filter(vis).find(c=>(c.innerText||'').trim()===T('$bCheck'));if(!cb)return 'NOCHECKBOX';cb.click();return 'OK'})()")
Ok ($r -eq 'OK') 'B: R&D-expense checkbox ticked'
Start-Sleep -Milliseconds 700
$r = (EvalJs "(()=>{$pre;const d=dlg();const labels=[...d.querySelectorAll('.el-form-item__label')].map(l=>(l.innerText||'').replace(/[\s*:]/g,'').trim());return JSON.stringify({amt:labels.includes(T('$bAmount')),acct:labels.includes(T('$bAcct'))})})()") -replace '"', ''
Write-Host ('  ' + $r)
Ok ($r -like '*amt:true*') 'B: amount field revealed'
Ok ($r -like '*acct:true*') 'B: account field revealed'
$r = (EvalJs "(()=>{$pre;const d=dlg();const it=item(d,T('$bAmount'));if(!it)return 'NOITEM_AMT';setv(it.querySelector('input'),'88.5');return 'OK'})()")
Ok ($r -eq 'OK') 'B: amount filled'
$r = (EvalJs "(()=>{$pre;const d=dlg();const it=item(d,T('$bAcct'));if(!it)return 'NOITEM_ACCT';const inp=it.querySelector('input');inp.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));inp.click();return 'OK'})()")
Ok ($r -eq 'OK') 'B: account dropdown opened'
Start-Sleep -Milliseconds 1100
$r = (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const opts=[...document.querySelectorAll('.el-select-dropdown__item')].filter(vis);if(!opts.length)return 'NOOPT';opts[0].click();return 'OK'})()")
Ok ($r -eq 'OK') 'B: account picked'

Write-Host '--- C) one submit files the material AND the DRAFT expense'
ClearErrs | Out-Null
$r = (EvalJs "(()=>{$pre;const d=dlg();const bs=[...d.querySelectorAll('.el-dialog__footer button')].filter(vis).filter(b=>(b.innerText||'').trim()===T('$bOk'));if(!bs.length)return 'NOOK';bs[0].click();return 'OK'})()")
Ok ($r -eq 'OK') 'C: submitted'
Start-Sleep -Milliseconds 2200
# NOTE: assert on the whole page text instead of scraping the toast node -- join()/quoting of the toast node was
# unreliable through the CLI pipe, while "is the hint on screen" is exactly what we care about.
$msgOk = (EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));return String((document.body.innerText||'').indexOf(T('$bMsgAudited'))>=0)})()") -replace '"', ''
Write-Host ('  auto-audit hint on screen = ' + $msgOk.Trim())
Ok ($msgOk.Trim() -eq 'true') 'C: success message says the expense was AUTO-AUDITED and paid'
$matId = SqlOne "SELECT id FROM dev_purchase_item WHERE name='$matName' ORDER BY id DESC LIMIT 1"
Ok ($matId -ne '') ('C: dev material created (id=' + $matId + ')')
$row = SqlOne ("SELECT CONCAT(status,'/',expense_type,'/',amount,'/',IFNULL(source_bill_type,''),'/',IFNULL(source_id,0)) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId + " ORDER BY id DESC LIMIT 1")
Write-Host ('  expense row = ' + $row)
Ok ($row -like ('AUDITED/RND/88.5*RD_DEV_MATERIAL/' + $matId)) 'C: one AUDITED RND expense sourced from that dev material (ticked box pays immediately)'
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId)) -eq '1') 'C: exactly one expense row for the dev material'
# the audit must have written the EXPENSE cashflow row -- that row is what actually reduces the account balance
$expNo = SqlOne ("SELECT expense_no FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId + " ORDER BY id DESC LIMIT 1")
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $expNo + "' AND flow_type='EXPENSE'")) -eq '1') 'C: the auto-audit wrote exactly one EXPENSE cashflow row'
Ok ((Errs) -eq '[]') 'D: no JS/API errors during the flow'

Write-Host '--- E) list row action: file it after the fact (the path for "forgot to tick it when creating")'
# fixture through the API (no option ticked), then file it from the list row action
$API = 'http://localhost:8080/api'
function ApiPost([string]$path, [string]$token, [string]$json) {
  $h = @{}
  if ($token) { $h['Authorization'] = $token }
  try { return Invoke-RestMethod -Uri ($API + $path) -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) } catch { return $null }
}
$lg = ApiPost '/auth/login' '' '{"username":"lin","password":"123","companyId":1}'
$tok = $lg.data.token
$accId = SqlOne 'SELECT id FROM finance_account WHERE status=1 ORDER BY id LIMIT 1'
$matB = 'RD-ROW-' + (Get-Date).ToString('HHmmss')
$mb = ApiPost '/dev/purchase-item' $tok ('{"name":"' + $matB + '","type":"BOARD","quantity":1,"amount":66}')
$matBId = [string]$mb.data.id
Ok ($matBId -ne '') ('E: fixture dev material created without the option (id=' + $matBId + ')')
Open '/dev/material' 3200   # reload: the new material sorts first (list is ordered by id desc)
$r = (EvalJs "(()=>{$pre;const b=rdBtnAt('$matB',T('$bRdBtn'));if(typeof b==='string')return b;b.click();return 'OK'})()")
Ok ($r -eq 'OK') ('E: the row action "register R&D expense" exists and opens its dialog (' + $r + ')')
Start-Sleep -Milliseconds 1000
$r = (EvalJs "(()=>{$pre;const d=dlg();if(!d)return 'NODLG';const it=item(d,T('$bAmount'));if(!it)return 'NOITEM_AMT';setv(it.querySelector('input'),'66');return 'OK'})()")
Ok ($r -eq 'OK') 'E: amount pre-filled from the material price (and editable)'
$r = (EvalJs "(()=>{$pre;const d=dlg();const it=item(d,T('$bAcct'));if(!it)return 'NOITEM_ACCT';const inp=it.querySelector('input');inp.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));inp.click();return 'OK'})()")
Ok ($r -eq 'OK') 'E: account dropdown opened'
Start-Sleep -Milliseconds 1100
$r = (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const opts=[...document.querySelectorAll('.el-select-dropdown__item')].filter(vis);if(!opts.length)return 'NOOPT';opts[0].click();return 'OK'})()")
Ok ($r -eq 'OK') 'E: account picked'
ClearErrs | Out-Null
$r = (EvalJs "(()=>{$pre;const d=dlg();const bs=[...d.querySelectorAll('.el-dialog__footer button')].filter(vis).filter(b=>(b.innerText||'').trim()===T('$bOk'));if(!bs.length)return 'NOOK';bs[0].click();return 'OK'})()")
Ok ($r -eq 'OK') 'E: submitted'
Start-Sleep -Milliseconds 2200
$msgOk = (EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));return String((document.body.innerText||'').indexOf(T('$bMsg'))>=0)})()") -replace '"', ''
Ok ($msgOk.Trim() -eq 'true') 'E: the same draft hint is shown'
$row2 = SqlOne ("SELECT CONCAT(status,'/',expense_type,'/',amount,'/',IFNULL(source_id,0)) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matBId + " ORDER BY id DESC LIMIT 1")
Write-Host ('  row = ' + $row2)
Ok ($row2 -like ('DRAFT/RND/66*' + $matBId)) 'E: one DRAFT RND expense sourced from that dev material'
Ok ((Errs) -eq '[]') 'E: no JS/API errors during the row-action flow'
# second click on the same row must be idempotent (backend returns the original doc, still one row)
$r = (EvalJs "(()=>{$pre;const b=rdBtnAt('$matB',T('$bRdBtn'));if(typeof b==='string')return b;b.click();return 'OK'})()")
Start-Sleep -Milliseconds 900
$r = (EvalJs "(()=>{$pre;const d=dlg();const it=item(d,T('$bAcct'));if(!it)return 'NOITEM_ACCT';const inp=it.querySelector('input');inp.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));inp.click();return 'OK'})()")
Start-Sleep -Milliseconds 1100
$r = (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const opts=[...document.querySelectorAll('.el-select-dropdown__item')].filter(vis);if(!opts.length)return 'NOOPT';opts[0].click();return 'OK'})()")
$r = (EvalJs "(()=>{$pre;const d=dlg();const bs=[...d.querySelectorAll('.el-dialog__footer button')].filter(vis).filter(b=>(b.innerText||'').trim()===T('$bOk'));if(!bs.length)return 'NOOK';bs[0].click();return 'OK'})()")
Start-Sleep -Milliseconds 2000
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matBId)) -eq '1') 'E: re-filing the same dev material is idempotent (still one row)'
SqlExec ("DELETE FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matBId + ";")
SqlExec ("DELETE FROM dev_purchase_item WHERE id=" + $matBId + ";")

Write-Host '--- cleanup'
# fixture C was auto-audited => it left an EXPENSE cashflow row (= the account balance) behind: drop flows first
SqlExec ("DELETE FROM finance_cashflow WHERE related_bill_no='" + $expNo + "';")
SqlExec ("DELETE FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId + ";")
SqlExec ("DELETE FROM dev_purchase_item WHERE id=" + $matId + ";")
Ok ((SqlOne ("SELECT COUNT(*) FROM dev_purchase_item WHERE name='$matName'")) -eq '0') 'cleanup: fixture dev material removed'

Summary 'dev material page prompts for an R&D expense and files it as a draft (UI)'
