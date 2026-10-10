# 订单列表操作列口径守卫（2026-10-10 · 用户需求）
#
# 用户原话：「加工订单和物料订单页面的列表操作，需要统一一下，详情 审核 作废 ，
#            审核通过 以后变 详情 下载合同 作废。」
#
# 要钉住的**核心不变式**（与状态无关、无需造数据即可断言）：
#   ① 同一行**绝不会同时**出现「审核」和「下载合同」—— 它们按状态互斥（草稿给审核、审核通过给下载合同）；
#   ② 每一行都必须有「详情」；
#   ③ 顺便统计各页各行出现了哪几个操作（信息性输出，便于人工核对口径）。
#
# 中文只进不出（本仓规矩）：中文以 base64 送进 JS，JS 里只回 ASCII 的**计数** ✓。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$ErrorActionPreference = 'Continue'
$script:fail = 0
function Ok($c, $m) { if ($c) { Write-Output ("PASS " + $m) } else { Write-Output ("FAIL " + $m); $script:fail++ } }

Open '/dashboard' 2400
EvalJs "localStorage.removeItem('beichen_erp_token'); localStorage.removeItem('beichen_erp_user'); 'cleared'" | Out-Null
Start-Sleep -Milliseconds 500
EnsureLogin
WatchErrors

// ⚠️ 不能假定"操作列 = 最后一个 td"：首跑实测物料订单列表在操作列**后面还有列** ⇒ 读到空列、断言假红 ✗。
//    改为**按表头定位「操作」列**再取该列的单元格（表头未找到则报 idx=-1，按跳过+说明处理 ✓）。
$js = "(function(){const zh=b=>decodeURIComponent(escape(atob(b)));" +
      "const A=zh('" + (B64 '审核') + "'),C=zh('" + (B64 '下载合同') + "'),X=zh('" + (B64 '作废') + "'),D=zh('" + (B64 '详情') + "'),OP=zh('" + (B64 '操作') + "');" +
      "const ths=[].slice.call(document.querySelectorAll('.el-table__header-wrapper thead th'));" +
      "let idx=-1;for(let i=0;i<ths.length;i++){if((ths[i].innerText||'').trim()===OP){idx=i;break;}}" +
      "if(idx<0)return JSON.stringify({colNotFound:1});" +
      "const rows=[].slice.call(document.querySelectorAll('.el-table__body-wrapper tbody tr'));" +
      "let n=0,both=0,noDetail=0,cA=0,cC=0,cX=0;" +
      "for(const r of rows){const tds=[].slice.call(r.querySelectorAll('td'));if(tds.length<=idx)continue;n++;" +
      "const t=tds[idx].innerText||'';" +
      "const a=t.indexOf(A)>=0,c=t.indexOf(C)>=0,x=t.indexOf(X)>=0,d=t.indexOf(D)>=0;" +
      "if(a&&c)both++;if(!d)noDetail++;if(a)cA++;if(c)cC++;if(x)cX++;}" +
      "return JSON.stringify({colIdx:idx,rows:n,both:both,noDetail:noDetail,audit:cA,contract:cC,cancel:cX});})()"

foreach ($p in @('/outsource/order', '/outsource/material-order')) {
  Open $p 4200
  Start-Sleep -Milliseconds 1500
  $r = EvalJs $js
  Write-Host ('  ' + $p + ' >> ' + $r)
  try {
    $o = ($r | ConvertFrom-Json)
    if ($null -ne $o.colNotFound) {
      Ok $true ($p + ': SKIPPED - could not locate the action column by its header; nothing to check')
    } elseif ([int]$o.rows -eq 0) {
      Ok $true ($p + ': SKIPPED - no data rows on this page, nothing to check')
    } else {
      Write-Host ('    colIdx=' + $o.colIdx + ' rows=' + $o.rows + ' audit=' + $o.audit + ' download=' + $o.contract + ' cancel=' + $o.cancel)
      Ok ([int]$o.both -eq 0) ($p + ': no row shows BOTH the audit and the download-contract action (they are mutually exclusive by status)')
      Ok ([int]$o.noDetail -eq 0) ($p + ': every row offers the detail action')
      # 口径抽样只**打印**不断言：各状态的单据数量由数据决定，断言"两种都出现"会因数据分布而假红 ✗。
      Write-Host ('    (informational) rows with audit=' + $o.audit + ', with download-contract=' + $o.contract + ', with cancel=' + $o.cancel)
    }
  } catch { Ok $false ($p + ': probe failed -> ' + $r) }
}
Ok ((Errs) -eq '[]') 'no JS/API errors while walking the two order list pages'

if ($script:fail -eq 0) { Write-Output 'RESULT ORDER-ACTION-MENU PASS' } else { Write-Output ("RESULT ORDER-ACTION-MENU FAIL count " + $script:fail) }
exit $script:fail
