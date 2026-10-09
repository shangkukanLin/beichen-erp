# 研发立项详情 · BOM「损耗率%」可就地修改 —— UI + DB 守卫（2026-10-09 用户需求，报告 §7.31）。
#
# 用户口径：「研发立项详情的BOM信息的损耗率%需要可以修改。」改前该列是只读 `<span>`（数据链路本就通：
# saveBom 一直提交 lossRate），本次只把 UI 放开 + 加后端护栏。本守卫钉四件事：
#   ① UI：损耗率% 列是**输入框**（父行）；「用量」列**仍然没有输入框**（负例：没顺手把用量也改了 ✗）；
#   ② 改值 + 点「保存」⇒ **DB 真的落新值**（dev_bom.loss_rate，最新版本），且该版本**行数不变**；
#   ③ 刷新后**回显**新值（回填链路没断）；
#   ④ 后端护栏：直调 API 传 150 ⇒ 拒绝，且**该版本一行都没被改动**（校验在"删旧版"之前 ⇒ 不破坏既有 BOM）；
#      传 30 ⇒ 落库成功。
# 收尾把损耗率改回原值并核对（不留痕）。
#
# ⚠️ 两处踩坑预防：
#   1) 不能用 lib 的 SetRowInput（它是"行内第 N 个 input"，而本表的类型/物料/供应商都是 select，内部也有 input，
#      下标会错位）⇒ 本脚本按**列**定位（第 7 列）✓。
#   2) 页面里可能有不止一个「保存」按钮（页头保存项目 + BOM 工具条保存）⇒ 必须点**与 BOM 表格同一个 el-card** 里的那个。
#
# 跑法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-dev-bom-lossrate-edit.ps1
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }

EnsureLogin
WatchErrors

# ---------- 夹具：一个有 BOM 的研发立项（取"最新版本"里第一行） ----------
$projId = [int]$((SqlOne 'SELECT b.project_id FROM dev_bom b WHERE b.version=(SELECT MAX(x.version) FROM dev_bom x WHERE x.project_id=b.project_id) ORDER BY b.project_id LIMIT 1') -replace '^$', '0')
if ($projId -le 0) { Skip 'no dev project with BOM rows -> nothing to check'; Summary 'dev BOM loss-rate editable'; exit 0 }
$ver = [int]$((SqlOne ("SELECT MAX(version) FROM dev_bom WHERE project_id=" + $projId)) -replace '^$', '0')
$rowId = SqlOne ("SELECT id FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver + " ORDER BY id LIMIT 1")
$origLoss = SqlOne ("SELECT loss_rate FROM dev_bom WHERE id=" + $rowId)
$cntBefore = [int]$((SqlOne ("SELECT COUNT(*) FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver)) -replace '^$', '0')
Write-Host ('  fixture: project=' + $projId + ' version=V' + $ver + ' row#' + $rowId + ' loss_rate=' + $origLoss + ' rows=' + $cntBefore)
Ok ($cntBefore -ge 1) ('found a project BOM to edit (project=' + $projId + ', V' + $ver + ', ' + $cntBefore + ' rows)')

# ---------- ① UI 形态：损耗率可编辑、用量仍只读 ----------
Step 'UI: the loss-rate column is an input, the quantity column is NOT'
Open ('/dev/project/edit/' + $projId) 3400
Write-Host ('  open BOM tab: ' + (ClickText (ZH 'tab_bom')))
Start-Sleep -Milliseconds 1800
$probeJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis);let loss=0,qty=0;for(const r of rs){const c=r.querySelectorAll('td');if(c[6])loss+=c[6].querySelectorAll('input').length;if(c[5])qty+=c[5].querySelectorAll('input').length}return JSON.stringify({rows:rs.length,lossInputs:loss,qtyInputs:qty})})()"
$probe = EvalJs $probeJs
Write-Host ('  column probe >> ' + $probe)
$p = $probe | ConvertFrom-Json
Ok ($p.rows -ge 1) 'the BOM table rendered rows'
Ok ($p.lossInputs -ge 1) 'the loss-rate column now has editable inputs (was a read-only span)'
Ok ($p.qtyInputs -eq 0) 'the quantity column still has NO input (negative control: only the loss rate was opened up)'

# 提示文案：口径已从"损耗率只读"改为"损耗率可改"——这里**在浏览器内**比对（标签 base64 进、ASCII 布尔出，
# 避免把中文读回 PowerShell 被 GBK 解坏 ✗）。注意 verify-i4-i5-hints.ps1 也断言这条文案（键 hint_bom_qty）。
$hintB64 = B64 (ZH 'hint_bom_qty')
$hintJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$hintB64');const vis=e=>e.getClientRects().length>0;const ps=[...document.querySelectorAll('.el-tab-pane')].filter(vis);const t=ps.map(x=>(x.innerText||'')).join(' ');return JSON.stringify({hasHint:t.indexOf(L)>=0,len:t.length})})()"
$hintJson = EvalJs $hintJs
Write-Host ('  hint probe >> ' + $hintJson)
$h = $hintJson | ConvertFrom-Json
Ok ($h.hasHint -eq $true) 'the BOM tab shows the updated hint (loss rate editable here, quantity still read-only)'

# ---------- ② 改值 + 保存 ⇒ DB 真的落库 ----------
Step 'EDIT: set the first row to 7.5 and press 保存 -> the DB must change'
$v = B64 '7.5'
$setJs = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis);if(!rs.length)return 'NOROW';const el=rs[0].querySelectorAll('td')[6]?.querySelector('input');if(!el)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
Write-Host ('  set row0 loss-rate: ' + (EvalJs $setJs))
Start-Sleep -Milliseconds 500
# 点「与 BOM 表格同一个 card」里的保存按钮（页头另有保存项目按钮，不能点错）。
# ⚠️ 不按文案匹配（el-button 的 innerText 受图标/嵌套影响，2026-10-09 实测报过 NOBTN:0 ✗）：
#    改为按**位置** —— 该工具条依次是「+ 添加物料」「保存」「历史快照」⇒ 取 bs[1] ✓
$saveJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tbl=ts[ts.length-1];if(!tbl)return 'NOTABLE';const card=tbl.closest('.el-card');if(!card)return 'NOCARD';const bs=[...card.querySelectorAll('button')].filter(vis);if(bs.length<2)return 'NOBTN:'+bs.length;bs[1].click();return 'OK('+bs.length+' buttons)'})()"
Write-Host ('  click BOM save: ' + (EvalJs $saveJs))
Start-Sleep -Milliseconds 2600
$hit = SqlOne ("SELECT COUNT(*) FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver + " AND loss_rate=7.5")
$cntAfter = [int]$((SqlOne ("SELECT COUNT(*) FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver)) -replace '^$', '0')
Write-Host ('  rows with loss_rate=7.5 -> ' + $hit + ' ; version rows now = ' + $cntAfter)
Ok ("$hit" -ne '0') 'the edited loss rate really reached the DB (dev_bom.loss_rate)'
Ok ($cntAfter -eq $cntBefore) 'and the version still has the same number of rows (save = full replace, not append)'

# ---------- ③ 回显 ----------
Step 'RELOAD: the new value comes back into the input'
Open ('/dev/project/edit/' + $projId) 3200
ClickText (ZH 'tab_bom') | Out-Null
Start-Sleep -Milliseconds 1800
$backJs = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis);if(!rs.length)return 'NOROW';const el=rs[0].querySelectorAll('td')[6]?.querySelector('input');return el?String(el.value):'NOINPUT'})()"
$back = EvalJs $backJs
Write-Host ('  value after reload = ' + $back)
Ok ($back -ne 'NOINPUT' -and [decimal]$back -eq 7.5) 'the saved loss rate is shown again after a reload'

# ---------- ④ 后端护栏 ----------
Step 'API GUARD: loss_rate=150 must be refused, and the version must stay intact'
$tok = (EvalJs "localStorage.getItem('beichen_erp_token')") -replace '"', ''
function Api([string]$method, [string]$path, [string]$json) {
  $h = @{ Authorization = $tok }
  try {
    if ($json -eq '') { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method $method -Headers $h -TimeoutSec 30 }
    return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 30
  } catch { return $null }
}
$curId = SqlOne ("SELECT id FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver + " ORDER BY id LIMIT 1")
# ⚠️ 这里刻意用**单行**接口打越界值（而不是 /bom/batch）：整表接口是"先删旧版再插" ⇒ 万一护栏失效，
#   本次调用会把这份真实 BOM 换成我构造的那一行（破坏数据 ✗）。单行接口是 UPDATE，最坏也只动一行 ⇒ 可恢复 ✓。
$bad = Api 'POST' ('/api/dev/project/' + $projId + '/bom') ('{"id":' + $curId + ',"lossRate":150}')
Write-Host ('  single-row save lossRate=150 -> ' + $(if ($null -eq $bad) { 'transport-error' } else { 'code=' + $bad.code + ' msg=' + $bad.msg }))
Ok ($null -eq $bad -or $bad.code -ne 200) 'a loss rate of 150 is refused (backend guard, not just the UI :max)'
$cntGuard = [int]$((SqlOne ("SELECT COUNT(*) FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver)) -replace '^$', '0')
$still75 = SqlOne ("SELECT COUNT(*) FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver + " AND loss_rate=7.5")
$badPersisted = SqlOne ("SELECT COUNT(*) FROM dev_bom WHERE project_id=" + $projId + " AND version=" + $ver + " AND loss_rate=150")
Write-Host ('  after the refusal: rows=' + $cntGuard + ' rows@7.5=' + $still75 + ' rows@150=' + $badPersisted)
Ok ($cntGuard -eq $cntBefore -and "$still75" -ne '0' -and "$badPersisted" -eq '0') 'the refused value reached neither the row nor the version (nothing changed)'

Step 'API GUARD: a legal value (30) is accepted, then restored'
$ok = Api 'POST' ('/api/dev/project/' + $projId + '/bom') ('{"id":' + $curId + ',"lossRate":30}')
$v30 = SqlOne ("SELECT loss_rate FROM dev_bom WHERE id=" + $curId)
Write-Host ('  single-row save lossRate=30 -> code=' + $ok.code + ' ; db=' + $v30)
Ok ($ok.code -eq 200 -and [decimal]$v30 -eq 30) 'a legal loss rate passes the single-row endpoint too (shared validation)'

# ---------- 收尾：改回原值 ----------
Step 'CLEANUP: restore the original loss rate'
$restore = [decimal]$origLoss
$rb = Api 'POST' ('/api/dev/project/' + $projId + '/bom') ('{"id":' + $curId + ',"lossRate":' + $restore + '}')
$backVal = SqlOne ("SELECT loss_rate FROM dev_bom WHERE id=" + $curId)
Write-Host ('  restore -> code=' + $rb.code + ' ; db=' + $backVal)
Ok ($rb.code -eq 200 -and [decimal]$backVal -eq $restore) 'the original loss rate is back (the fixture is left as found)'

Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'

Summary 'dev project BOM: loss-rate editable inline (UI + DB + backend guard)'
