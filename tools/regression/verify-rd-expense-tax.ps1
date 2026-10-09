# 研发支出「是否含税」-- 2026-10-09 用户需求（V7: finance_expense 加 tax_included/tax_rate/tax_amount）。
#
# 口径（与 V4 的四个单据一致，用户拍板「按你推荐的来」）：
#   * 未含税 = 默认（与 DB 默认 0 同义，存量单语义不变）；
#   * 含税时用户填的金额即「含税总额」；税额 = 金额 x 税率/(100+税率)（HALF_UP、2 位）；
#   * 税额**只做拆分展示/统计**，绝不改写 amount -> 审核扣款、资金流水、利润表口径全部不变。
#
# 断言策略（每条绿色断言都要能报红；金额类一律以 DB 为准，不信前端算的数）：
#   1) UI 端到端：研发物料列表「研发支出」弹窗里打开含税开关（默认税率 13）-> 提交 -> 三列落库正确，
#      且 amount 仍是 113（**没被改写成不含税 100**）;
#   2) 税不变量（DB 自校验）：tax_amount = ROUND(amount*rate/(100+rate), 2);
#   3) 负例：同一单再提交「未含税」-> 三列归 0（证明开关真在控制），金额依然不变;
#   4) 幂等：同一物料重复登记 -> 未作废单仍只有 1 条;
#   5) 资金安全：审核后资金流水的支出额 == **amount(113)**，不是不含税金额(100);
#   6) 可见：费用单详情（只读态）出现含税行，且不含税金额 100.00（= 113 - 13，只有税额算对才会出现）;
#   7) 缺夹具（无可用资金账户 / 建不了研发物料 / 无权限）-> SKIP，绝不伪装通过。
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return ($out | Select-Object -First 1)
}
# Sync XHR. Request body travels as **base64** (PS 5.1 mangles embedded double quotes when passing
# args to a native exe -- the known repo trap that broke the commit-message file and an earlier guard).
function ApiSend([string]$method, [string]$path, [string]$body) {
  $b64 = if ([string]::IsNullOrEmpty($body)) { '' } else { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($body)) }
  $js = "(()=>{const x=new XMLHttpRequest();x.open('" + $method + "','" + $path + "',false);" +
        "x.setRequestHeader('Authorization',String(localStorage.getItem('beichen_erp_token')));" +
        "let b=null;const B='" + $b64 + "';if(B){x.setRequestHeader('Content-Type','application/json');" +
        "b=new TextDecoder().decode(Uint8Array.from(atob(B),c=>c.charCodeAt(0)))}" +
        "x.send(b);return String(x.status)+' '+String(x.responseText).slice(0,400)})()"
  return (EvalJs $js).Trim([char]34)
}
function ExpenseRow([int]$matId) {
  return (SqlOne ("SELECT id, amount, tax_included, tax_rate, tax_amount, status FROM finance_expense " +
                  "WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId +
                  " AND status <> 'CANCELLED' ORDER BY id DESC LIMIT 1"))
}
function ExpenseCount([int]$matId) {
  return [int](SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId + " AND status <> 'CANCELLED'"))
}
function AcctBalance([string]$acc) {
  return [decimal](SqlOne ("SELECT COALESCE(SUM(income),0)-COALESCE(SUM(expense),0) FROM finance_cashflow WHERE account_id=" + $acc))
}

# ---------- 0) 身份与夹具 ----------
# lin(公司1) 同时持有 dev:material(菜单304) 与 finance:expense(菜单809) -> 一条链能跑完（实测自 sys_role_menu）。
if (-not (EnsureLoginAs 'lin' '123')) {
  Skip 'cannot log in as lin (the account holding dev:material + finance:expense)'
  Summary 'RD-expense tax switch (V7)'
  exit 0
}
# 账户挑**余额最大**的可用账户：审核要真扣款，余额不够会失败（本库只有 195/100/100，金额取 113）。
$acctId = SqlOne "SELECT a.id FROM finance_account a LEFT JOIN finance_cashflow f ON f.account_id=a.id WHERE IFNULL(a.status,1) <> 0 GROUP BY a.id ORDER BY COALESCE(SUM(f.income),0)-COALESCE(SUM(f.expense),0) DESC LIMIT 1"
if (-not $acctId -or $acctId -eq 'NULL') {
  Skip 'no enabled finance account (fixture missing)'
  Summary 'RD-expense tax switch (V7)'
  exit 0
}
$stamp = (Get-Date).ToString('yyyyMMddHHmmss')
$matName = 'E2E-RDTAX-' + $stamp
$today = (Get-Date).ToString('yyyy-MM-dd')
$mkBody = '{"name":"' + $matName + '","quantity":1,"amount":113,"status":"GOOD","purchaseDate":"' + $today + '"}'
$rMk = ApiSend 'POST' '/api/dev/purchase-item' $mkBody
if ($rMk -notmatch '"code":200') {
  Skip ('cannot create the dev-material fixture (permission/validation): ' + $rMk.Substring(0, [Math]::Min(160, $rMk.Length)))
  Summary 'RD-expense tax switch (V7)'
  exit 0
}
$matId = [int](($rMk.Substring(4) | ConvertFrom-Json).data.id)
$balBefore = AcctBalance $acctId
Write-Host ("  fixture: dev material #$matId ($matName) amount=113 ; account=$acctId balance=$balBefore")

# ---------- 1) UI 端到端：列表行「研发支出」弹窗里打开含税开关 ----------
WatchErrors
Open '/dev/material' 3000
FillLabel 'lbl_material_name' $matName | Out-Null
ClickBtn 'btn_query' | Out-Null
Start-Sleep -Milliseconds 1500
$rowIdx = FindRow $matName
Write-Host ("  list row index for the fixture = " + $rowIdx)
if ($rowIdx -eq '-1') {
  Bad 'the fixture dev material is not visible on /dev/material (search failed) - UI path cannot run'
} else {
  $click = ClickRowBtn ([int]$rowIdx) 'lbl_material_rd'
  Start-Sleep -Milliseconds 1200
  Write-Host ("  row button >> " + $click)
  Ok ($click -match 'OK') 'the row action opens the RD-expense dialog'

  # 金额字段显式置 113（默认即物料金额，这里显式写一遍，避免"默认值恰好对"造成假绿）
  DialogSetInput 0 '113' | Out-Null
  # 打开含税开关（点击 .el-switch 根元素）
  EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';const sw=d.querySelector('.el-switch');if(!sw)return 'NOSW';if(sw.classList.contains('is-checked'))return 'ALREADY';sw.click();return 'CLICKED'})()" | Out-Null
  Start-Sleep -Milliseconds 600
  # 开关的可观测后果：税率输入框**从禁用变可用且默认 13**（"打开时税率默认 13"这条口径的证据）。
  # ⚠️ 必须排除 el-switch 内部的 checkbox input（它也有 rect、初版把它当成税率框 -> rateValue="on"）。
  $swJson = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';const sw=d.querySelector('.el-switch');const ins=[...d.querySelectorAll('input:not([type=hidden]):not([type=checkbox])')].filter(vis);return JSON.stringify({checked:sw?sw.classList.contains('is-checked'):null,rateDisabled:ins[1]?ins[1].disabled:null,rateValue:ins[1]?ins[1].value:null,amount:ins[0]?ins[0].value:null})})()"
  Write-Host ('  switch state >> ' + $swJson)
  $sw = $swJson | ConvertFrom-Json
  Ok ($sw.checked -eq $true) 'the tax switch turned on'
  Ok ($sw.rateDisabled -eq $false) 'turning it on enables the rate field'
  Ok ([decimal]$sw.rateValue -eq 13) 'the rate defaults to 13 when switched on'
  Ok ([decimal]$sw.amount -eq 113) 'the amount field holds 113 (the material amount)'

  # 支出账户必须选（初版漏了这步 -> 前端校验拦下、请求根本没发出，库里没行且没有任何 API 报错）
  $acctName = SqlOne ("SELECT account_name FROM finance_account WHERE id=" + $acctId)
  $selOpen = OpenSelect 'lbl_rd_account'
  Start-Sleep -Milliseconds 900
  $selPick = PickOptionContains $acctName
  Write-Host ('  account select >> ' + $selOpen + ' / ' + $selPick + ' (want ' + $acctName + ')')
  Ok ($selPick -match 'OK') 'the expense account can be selected in the dialog'

  ClickDialogBtn 'btn_ok' | Out-Null
  Start-Sleep -Milliseconds 1000

  # 落库可能有网络/渲染延迟 -> 轮询等行出现（最多 ~5s），避免把时延误判成功能失败
  $row = ''
  for ($i = 0; $i -lt 8; $i++) {
    $row = ExpenseRow $matId
    if ($row) { break }
    Start-Sleep -Milliseconds 700
  }
  Write-Host ('  expense row (id/amount/included/rate/tax/status) = ' + $row)
  $c = @($row -split "`t")
  if ($c.Count -lt 6) {
    Bad ('no RD-expense row was created by the UI flow: ' + $row)
  } else {
    $eid = $c[0]
    Ok ($c[2] -eq '1') 'the UI switch reaches the DB (tax_included = 1)'
    Ok ([decimal]$c[3] -eq 13) 'the rate reaches the DB (tax_rate = 13)'
    Ok ([decimal]$c[4] -eq 13) 'the tax is computed on the server (tax_amount = 13.00)'
    Ok ([decimal]$c[1] -eq 113) 'the amount is NOT rewritten (still 113 = the tax-INCLUSIVE total)'

    # ---------- 3) 税不变量（DB 自校验，不写快照数字） ----------
    $inv = SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE id=" + $eid + " AND ABS(tax_amount - ROUND(amount*tax_rate/(100+tax_rate),2)) < 0.005")
    Ok ($inv -eq '1') 'invariant: tax_amount == amount*rate/(100+rate) rounded to 2dp'

    # ---------- 4) 负例：同一单再提交「未含税」 ----------
    $rNeg = ApiSend 'POST' ("/api/dev/purchase-item/" + $matId + "/rd-expense") ('{"amount":113,"accountId":' + $acctId + ',"taxIncluded":0,"taxRate":13}')
    $row2 = ExpenseRow $matId
    $c2 = @($row2 -split "`t")
    Write-Host ('  after a tax-EXCLUSIVE resubmit = ' + $row2)
    Ok ($rNeg -match '"code":200') 'resubmitting the same material is accepted (idempotent path)'
    Ok ($c2[2] -eq '0' -and [decimal]$c2[3] -eq 0 -and [decimal]$c2[4] -eq 0) 'switching the toggle OFF zeroes the rate and the tax (negative control for the switch)'
    Ok ([decimal]$c2[1] -eq 113) 'and the amount still is not rewritten'

    # ---------- 5) 幂等 + 重新开启 ----------
    $rOn = ApiSend 'POST' ("/api/dev/purchase-item/" + $matId + "/rd-expense") ('{"amount":113,"accountId":' + $acctId + ',"taxIncluded":1,"taxRate":13}')
    $row3 = ExpenseRow $matId
    $c3 = @($row3 -split "`t")
    Write-Host ('  after re-enabling tax = ' + $row3)
    Ok ((ExpenseCount $matId) -eq 1) 'no duplicate expense was created (still exactly one active row)'
    Ok ([decimal]$c3[4] -eq 13) 'the tax is back to 13.00 on the existing (draft) row'

    # ---------- 6) 资金安全：审核后扣款额 == amount（含税总额），不是不含税 ----------
    $rAudit = ApiSend 'PUT' ("/api/finance/expense/" + $eid + "/audit") ''
    Write-Host ('  audit >> ' + $rAudit.Substring(0, [Math]::Min(120, $rAudit.Length)))
    Ok ($rAudit -match '"code":200') 'the expense audits successfully'
    $flow = SqlOne ("SELECT expense FROM finance_cashflow WHERE account_id=" + $acctId + " ORDER BY id DESC LIMIT 1")
    $balAfter = AcctBalance $acctId
    Write-Host ('  cashflow expense = ' + $flow + ' ; balance ' + $balBefore + ' -> ' + $balAfter)
    Ok ([decimal]$flow -eq [decimal]$c3[1]) 'the cash outflow equals the amount (113), i.e. adding tax did NOT shrink the payment'
    Ok (([decimal]$balBefore - [decimal]$balAfter) -eq 113) 'the account was debited by 113 (not by the net 100)'

    # ---------- 7) 结果可见：费用单详情（已审核 -> 只读描述） ----------
    Open ('/finance/expense/detail/' + $eid) 2800
    # ⚠️ 中文**绝不能从浏览器读回 PowerShell** 再比对：agent-browser 的 stdout 会被控制台按 GBK 解码
    # （实测把「含税情况」读成「鍚◣鎯呭喌」⇒ 断言假红）。改法：把标签 base64 传进 JS，在**浏览器内**比较，
    # 只回传 ASCII 的 true/false（本仓既有守卫同款做法：中文只进不出）。
    $lblB64 = B64 (ZH 'lbl_tax_incl_info')
    $infoJson = EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$lblB64');const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-descriptions')].filter(vis)[0];if(!d)return 'NODESC';const t=(d.innerText||'');return JSON.stringify({hasLabel:t.indexOf(L)>=0,hasNet:t.indexOf('100.00')>=0,hasRate:t.indexOf('13%')>=0,len:t.length})})()"
    Write-Host ('  detail assertions >> ' + $infoJson)
    $di = $infoJson | ConvertFrom-Json
    Ok ($di.hasLabel -eq $true) 'the expense detail shows the tax row (checked in-page, no encoding round-trip)'
    Ok ($di.hasNet -eq $true) 'it shows the tax-exclusive amount 100.00 (only correct when the tax is 13.00)'
    Ok ($di.hasRate -eq $true) 'it shows the 13% rate'

    # ---------- 8) 收尾：反审核 + 作废，把钱冲回（不留下已审核的测试费用单） ----------
    $rUn = ApiSend 'PUT' ("/api/finance/expense/" + $eid + "/un-audit") ''
    $rCn = ApiSend 'POST' ("/api/finance/expense/" + $eid + "/cancel") ''
    $balFinal = AcctBalance $acctId
    Write-Host ('  cleanup: un-audit=' + ($rUn -match '"code":200') + ' cancel=' + ($rCn -match '"code":200') + ' ; balance back to ' + $balFinal)
    Ok ($balFinal -eq $balBefore) 'un-auditing reverses the money (the test leaves the account as it found it)'
  }
}

# ---------- 9) 费用管理手工新增（同表同口径，用户选了「一起做」） ----------
# 它与研发支出共用 validate -> normalizeTax，但**控制器绑定/字段透传是另一条路径** ⇒ 必须单独证一次
# （本仓有过"字段漏映射被静默丢弃"的先例）。手工单落草稿、不动钱。
$rMan = ApiSend 'POST' '/api/finance/expense' ('{"expenseType":"OFFICE","amount":113,"accountId":' + $acctId + ',"expenseDate":"' + $today + '","taxIncluded":1,"taxRate":13,"remark":"E2E-RDTAX-MANUAL"}')
if ($rMan -match '"code":200') {
  $manId = SqlOne "SELECT id FROM finance_expense WHERE remark='E2E-RDTAX-MANUAL' ORDER BY id DESC LIMIT 1"
  $mrow = SqlOne ("SELECT amount, tax_included, tax_rate, tax_amount FROM finance_expense WHERE id=" + $manId)
  $mc = @($mrow -split "`t")
  Write-Host ('  manual expense row (amount/included/rate/tax) = ' + $mrow)
  Ok ($mc[1] -eq '1' -and [decimal]$mc[2] -eq 13 -and [decimal]$mc[3] -eq 13) 'a MANUALLY registered expense gets the same tax figures (shared normalizeTax)'
  Ok ([decimal]$mc[0] -eq 113) 'and the manual entry amount is untouched as well'
  $rManCancel = ApiSend 'POST' ("/api/finance/expense/" + $manId + "/cancel") ''
  Write-Host ('  manual draft cleanup: cancel -> ' + ($rManCancel -match '"code":200'))
} else {
  Ok $false ('manual expense create rejected: ' + $rMan)
}

# 删掉夹具（无断言：清理失败不影响本功能的结论，只打印）
$rDel = ApiSend 'DELETE' ("/api/dev/purchase-item/" + $matId) ''
Write-Host ('  fixture cleanup: delete dev material -> ' + ($rDel -match '"code":200'))

Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'
Summary 'RD-expense tax switch (V7)'
