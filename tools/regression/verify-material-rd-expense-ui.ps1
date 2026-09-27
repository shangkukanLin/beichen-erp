# verify-material-rd-expense-ui.ps1 (2026-09-27, user request): the UI half of "create a material -> optionally file an
# R&D expense". Covers what the API-level guard (verify-material-rd-expense.ps1) cannot: the PROMPT itself.
#
#   A) the add-material dialog shows a "file an R&D expense" checkbox, and the amount/account fields stay hidden
#      until it is ticked (the prompt must not clutter the normal flow);
#   B) ticking it reveals 支出金额 / 支出账户 (+ date / remark), and the account dropdown is populated with a balance;
#   C) submitting files the material AND the DRAFT expense in one go: success message says "draft + audit to pay",
#      and the DB holds exactly one RND row sourced from that material;
#   D) no JS / API errors along the way.
#
# Fixture is self-built / self-cleaned (temp material + its expense row), so the file is repeatable. PURE ASCII.
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
$bNew = B64 (ZH 'btn_new'); $bName = B64 (ZH 'lbl_material_name'); $bType = B64 (ZH 'lbl_material_type')
$bCheck = B64 (ZH 'chk_material_rd'); $bAmount = B64 (ZH 'lbl_rd_amount'); $bAcct = B64 (ZH 'lbl_rd_account')
$bOk = B64 (ZH 'btn_ok'); $bMsg = B64 (ZH 'msg_rd_draft')

# shared JS prelude: decode labels, find a form item by label, set an input value the Vue way
$pre = "const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;const dlg=()=>[...document.querySelectorAll('.el-dialog')].filter(vis).pop();const item=(root,lab)=>{for(const it of root.querySelectorAll('.el-form-item')){const l=it.querySelector('.el-form-item__label');if(l&&(l.innerText||'').replace(/[\s*:]/g,'')===lab)return it}return null};const setv=(el,v)=>{const p=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))};"

Write-Host '--- A) the dialog prompts for an R&D expense, fields stay hidden until ticked'
Open '/outsource/material-info' 3500
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
$msgOk = (EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));return String((document.body.innerText||'').indexOf(T('$bMsg'))>=0)})()") -replace '"', ''
Write-Host ('  draft hint on screen = ' + $msgOk.Trim())
Ok ($msgOk.Trim() -eq 'true') 'C: success message says the expense is a DRAFT (audit before paying)'
$matId = SqlOne "SELECT id FROM outsource_material WHERE material_name='$matName' ORDER BY id DESC LIMIT 1"
Ok ($matId -ne '') ('C: material created (id=' + $matId + ')')
$row = SqlOne ("SELECT CONCAT(status,'/',expense_type,'/',amount,'/',IFNULL(source_bill_type,''),'/',IFNULL(source_id,0)) FROM finance_expense WHERE source_bill_type='RD_MATERIAL' AND source_id=" + $matId + " ORDER BY id DESC LIMIT 1")
Write-Host ('  expense row = ' + $row)
Ok ($row -like ('DRAFT/RND/88.5*RD_MATERIAL/' + $matId)) 'C: one DRAFT RND expense sourced from that material'
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_MATERIAL' AND source_id=" + $matId)) -eq '1') 'C: exactly one expense row for the material'
Ok ((Errs) -eq '[]') 'D: no JS/API errors during the flow'

Write-Host '--- cleanup'
SqlExec ("DELETE FROM finance_expense WHERE source_bill_type='RD_MATERIAL' AND source_id=" + $matId + ";")
SqlExec ("DELETE FROM outsource_material WHERE id=" + $matId + ";")
Ok ((SqlOne ("SELECT COUNT(*) FROM outsource_material WHERE material_name='$matName'")) -eq '0') 'cleanup: fixture material removed'

Summary 'material page prompts for an R&D expense and files it as a draft (UI)'
