# Temp test 3: purchase chain through the UI (purchase order -> audit -> stock/payable -> un-audit -> audit -> purchase return -> audit)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
$S = (ZH 'val_product')   # prefix of products created earlier
$V = (ZH 'val_vendor')    # prefix of vendors created earlier
function Step($n) { Write-Host ('--- STEP ' + $n) }

# ---------- 1. create purchase order ----------
Step 'create purchase order'
Open '/inventory/purchase' 2400
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1600
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('vendor: ' + (SelectLabelContains 'lbl_vendor' $V))
Write-Host ('warehouse: ' + (SelectLabelContains 'lbl_in_warehouse' (ZH 'pfx_wh')))
Write-Host ('add row: ' + (ClickBtn 'btn_add_detail'))
Start-Sleep -Milliseconds 800
Write-Host ('open product sel: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1200
Write-Host ('pick product: ' + (PickOptionContains $S))
Start-Sleep -Milliseconds 600
Write-Host ('qty A: ' + (SetRowInput 0 1 '10'))
Write-Host ('price A: ' + (SetRowInput 0 2 '100'))
Start-Sleep -Milliseconds 400
Write-Host ('total: ' + (Txt '.el-form-item'))
# 2026-09-24: /inventory/purchase/add serves purchase/order/form.vue whose primary action is labelled
# **保存**. The 提交 / 确定 labels tried on 2026-09-23 are both stale (the page never shows them) =>
# try 保存 first, then fall back to the older labels in case the wording changes back.
$subRes = ClickBtn 'btn_save'
if ($subRes -match 'NOBTN') { $subRes = ClickBtn 'btn_ok' }
if ($subRes -match 'NOBTN') { $subRes = ClickBtn 'btn_submit' }
Write-Host ('submit: ' + $subRes)
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/inventory/purchase' 2400
$r = Rows 0
Write-Host ('purchase rows=' + $r.n)
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
$rowIdx = FindRow $S
Write-Host ('found row=' + $rowIdx)
Ok ($rowIdx -ne '-1') 'purchase order created (draft, found in list)'
if ($rowIdx -eq '-1') { Summary 'purchase chain'; exit 1 }

# ---------- 2. audit ----------
Step 'audit'
Write-Host ('click audit: ' + (ClickRowBtnContains ([int]$rowIdx) (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/purchase' 2400
$r = Rows 0
$rowIdx = FindRow $S
Write-Host ('row after audit=' + ($r.rows[[int]$rowIdx] -join ' | '))
Ok (($r.rows[[int]$rowIdx] -join ' ') -match (ZH 'st_audited')) 'purchase order audited'

# ---------- 3. stock + payable ----------
Step 'stock and payable'
Open '/inventory/stock' 2600
$st = Rows 0
Write-Host ('stock head=' + ($st.head -join '|'))
$si = FindRow $S
Write-Host ('stock row=' + $(if ($si -ne '-1') { ($st.rows[[int]$si] -join ' | ') } else { 'NOT FOUND' }))
Ok ($si -ne '-1') 'stock page shows the purchased product'
Open '/finance/payable' 2600
$py = Rows 0
Write-Host ('payable rows=' + $py.n + ' head=' + ($py.head -join '|'))
$found = $false
foreach ($rw in $py.rows) { if (($rw -join ' ') -match '1000') { $found = $true; Write-Host ('payable row=' + ($rw -join ' | ')) } }
Ok $found 'payable ledger generated with amount 1000'
Write-Host ('errs=' + (Errs))

# ---------- 4. un-audit -> rollback ----------
# 2026-09-24（用户口径）：反审核已从列表行内移入详情页 ⇒ 先点「详情」，再在详情页头点「反审核」。
Step 'un-audit rollback'
Open '/inventory/purchase' 2400
$r = Rows 0
$rowIdx = FindRow $S
Write-Host ('open detail: ' + (ClickRowBtnContains ([int]$rowIdx) (ZH 'btn_detail')))
Start-Sleep -Milliseconds 2200
Write-Host ('click un-audit: ' + (ClickBtn 'btn_unaudit'))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/stock' 2400
$st = Rows 0
$si = FindRow $S
Write-Host ('stock row after un-audit=' + $(if ($si -ne '-1') { ($st.rows[[int]$si] -join ' | ') } else { 'NOT FOUND' }))
# 2026-10-05 F7-290: was `Ok $true` -- a permanent PASS for "we clicked something". The real assertions of
# this un-audit step are the payable rows checked right below; this line reported nothing.
Skip 'un-audit executed (the stock/payable rows below are the actual assertions)'
Open '/finance/payable' 2400
$py = Rows 0
$still = $false
foreach ($rw in $py.rows) { $line = ($rw -join ' '); if ($line -match '1000' -and $line -match (ZH 'st_audited')) { $still = $true } }
Ok (-not $still) 'payable of the old row no longer active after un-audit'

# ---------- 5. re-audit ----------
Step 're-audit'
Open '/inventory/purchase' 2400
$r = Rows 0
$rowIdx = FindRow $S
Write-Host ('click audit: ' + (ClickRowBtnContains ([int]$rowIdx) (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message'))
Open '/finance/payable' 2400
$py = Rows 0
$found = $false
foreach ($rw in $py.rows) { if (($rw -join ' ') -match '1000') { $found = $true } }
Ok $found 'payable regenerated after re-audit'

# ---------- 6. purchase return ----------
Step 'purchase return'
Open '/inventory/purchase' 2400
$r = Rows 0
$rowIdx = FindRow $S
Write-Host ('open detail: ' + (ClickRowBtnContains ([int]$rowIdx) (ZH 'btn_detail')))
Start-Sleep -Milliseconds 2200
Write-Host ('path=' + (EvalJs 'String(location.pathname)') + ' btns=' + (EvalJs "JSON.stringify([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0).map(b=>(b.innerText||'').trim()).filter(t=>t!==''))"))
Write-Host ('click launch-return: ' + (ClickText (ZH 'btn_launch_return')))
Start-Sleep -Milliseconds 2600
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('return rows=' + (Rows 0).n)
Write-Host ('in alert: ' + (BodyHas (ZH 'txt_brought')))
Write-Host ('set qty: ' + (SetRowInput 0 1 '2'))
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/inventory/purchase-return' 2400
$pr = Rows 0
Write-Host ('return rows=' + $pr.n + ' head=' + ($pr.head -join '|'))
if ($pr.n -gt 0) { Write-Host ('row0=' + ($pr.rows[0] -join ' | ')) }
Ok ($pr.n -gt 0) 'purchase return created'
Write-Host ('click audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/purchase-return' 2400
$pr = Rows 0
Write-Host ('return rows after audit=' + $(if ($pr.n -gt 0) { ($pr.rows[0] -join ' | ') } else { 'NONE' }))
Open '/inventory/stock' 2400
$st = Rows 0
$si = FindRow $S
Write-Host ('stock after return=' + $(if ($si -ne '-1') { ($st.rows[[int]$si] -join ' | ') } else { 'NOT FOUND' }))
Open '/finance/payable' 2400
$py = Rows 0
foreach ($rw in $py.rows) { Write-Host ('payable row=' + ($rw -join ' | ')) }
Write-Host ('errs-final=' + (Errs))

Summary 'purchase chain'
