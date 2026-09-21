# Temp UI test library (ASCII ONLY - do not put non-ASCII chars in this file).
# Chinese labels live in ui-e2e-zh.json and are passed to JS as base64.
$ErrorActionPreference = 'Continue'
$script:BASE = 'http://localhost:5173'
# becomes $true once WatchErrors() ran; Open() then re-installs the hook after every (full) page load
$script:WatchOn = $false
$script:PASS = 0
$script:FAIL = 0
$script:ZHPATH = Join-Path $PSScriptRoot 'ui-e2e-zh.json'
$script:ZH = (Get-Content $script:ZHPATH -Raw -Encoding UTF8 | ConvertFrom-Json)

function ZH([string]$key) {
  $v = $script:ZH.$key
  if (-not $v) { throw "no zh key: $key" }
  return [string]$v
}
function B64([string]$s) { return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($s)) }

function EvalRaw([string]$js) {
  $out = @(agent-browser eval $js) | ForEach-Object { "$_" }
  $out = $out | Where-Object { $_.Trim() -ne '' -and $_.Trim() -ne 'OK Done' -and $_.Trim() -ne 'Done' -and $_.Trim() -notmatch '^\s*[xX]' }
  $s = ($out -join "`n").Trim()
  $q = [char]34
  if ($s.StartsWith($q) -and $s.EndsWith($q) -and $s.Length -ge 2) { $s = $s.Substring(1, $s.Length - 2) }
  $s = $s.Replace([string][char]92 + $q, [string]$q)
  return $s
}
function EvalJs([string]$js) { return (EvalRaw $js) }

function Open([string]$path, [int]$wait = 2200) {
  agent-browser open "$($script:BASE)$path" | Out-Null
  agent-browser wait $wait | Out-Null
  # 2026-09-19 backfill: the hook is installed from PowerShell, and every Open is a FULL page load that wipes
  # both window.__errs and the fetch hook => without this, only the first page of a script is ever observed
  # (ui-e2e-1-nav.ps1 used to look clean for that reason). Re-install whenever watching is on.
  if ($script:WatchOn) { EvalJs (ErrHookJs) | Out-Null }
}
function FillLogin([string]$user = 'lin', [string]$pwd = '123') {
  $b = B64 (ZH 'btn_login'); $pu = B64 (ZH 'ph_user'); $pp = B64 (ZH 'ph_pwd')
  $ub = B64 $user; $pb = B64 $pwd
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const LB=T('$b').replace(/\s/g,''),PU=T('$pu'),PP=T('$pp'),VU=T('$ub'),VP=T('$pb');const vis=e=>e.getClientRects().length>0;const ins=[...document.querySelectorAll('input')].filter(e=>vis(e)&&e.type!=='hidden');if(!ins.length)return 'NOINPUTS';const u=ins.find(e=>(e.placeholder||'')===PU)||ins.find(e=>e.type==='text'&&(e.placeholder||'')!=='');const p=ins.find(e=>(e.placeholder||'')===PP)||ins.find(e=>e.type==='password');if(!u||!p)return 'NOFIELDS:'+ins.length;const set=(el,v)=>{el.focus();const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))};set(u,VU);set(p,VP);const bs=[...document.querySelectorAll('button')].filter(vis).filter(x=>(x.innerText||'').replace(/\s/g,'')===LB);if(bs.length){bs[0].click();return 'CLICKED'}p.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',keyCode:13,bubbles:true}));p.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',keyCode:13,bubbles:true}));return 'ENTER'})()"
  return (EvalJs $js)
}
function EnsureLogin([string]$user = 'lin', [string]$pwd = '123') {
  Open '/dashboard' 1500
  if ((EvalJs "String(!!localStorage.getItem('beichen_erp_token'))") -notmatch 'true') {
    EvalJs "localStorage.clear();sessionStorage.clear();'x'" | Out-Null
    Open '/login' 2500
    $r = FillLogin $user $pwd
    agent-browser wait 3500 | Out-Null
    $t = EvalJs "String(!!localStorage.getItem('beichen_erp_token'))"
    Write-Host ("LOGIN " + $r + " token=" + $t)
  }
  # drop cached menu tree so newly added menus are visible
  EvalJs "localStorage.removeItem('beichen_erp_menus');'ok'" | Out-Null
}
# Always switch account: wipe the session, log in as $user, then VERIFY the identity via
# localStorage['beichen_erp_user'].username (a silent failure would leave the admin token in place
# and make every later page look fine => false PASS). Returns $true when the identity matches.
function EnsureLoginAs([string]$user, [string]$pwd = '123') {
  EvalJs "localStorage.clear();sessionStorage.clear();'x'" | Out-Null
  Open '/login' 2500
  $r = FillLogin $user $pwd
  agent-browser wait 3500 | Out-Null
  $t = EvalJs "String(!!localStorage.getItem('beichen_erp_token'))"
  $who = EvalJs "(()=>{try{const u=JSON.parse(localStorage.getItem('beichen_erp_user')||'null');return u?(u.username||''):''}catch(e){return ''}})()"
  Write-Host ("LOGIN-AS " + $user + " " + $r + " token=" + $t + " who=" + $who)
  EvalJs "localStorage.removeItem('beichen_erp_menus');'ok'" | Out-Null
  return ("$who" -eq $user)
}
# idempotent: keeps the existing array/listeners when called again on the same document
# NOTE (2026-09-19): this backend answers MOST business errors - including "no permission" - with HTTP 200
# plus a JSON envelope {"code":403,...} (that is why verify-api-perm-enforcement.ps1 asserts the body code).
# A status-only hook therefore never saw a permission failure. The hook now inspects the body too and records
# APICODE<code>:<url>, so "0 errors" finally means "0 failures" for this app.
function ErrHookJs() {
  return "(()=>{if(!window.__errs){window.__errs=[];window.addEventListener('error',e=>window.__errs.push('JSERR:'+(e.message||'')));const of=window.fetch;window.fetch=function(){const a=[].slice.call(arguments);const u=String(a[0]);const ck=r=>{try{return r.clone().json().then(b=>{if(b&&typeof b.code==='number'&&b.code>=400){window.__errs.push('APICODE'+b.code+':'+u)}}).catch(()=>{})}catch(e){return null}};return of.apply(this,a).then(r=>{if(r.status>=400){window.__errs.push('HTTP'+r.status+':'+u)}else{ck(r)}return r})};}return 'watch'})()"
}
function WatchErrors() {
  $script:WatchOn = $true
  EvalJs (ErrHookJs) | Out-Null
}
# negative control: force a request that MUST fail and report whether the hook recorded it. Proves the
# detector is live on the CURRENT page (without it a freshly loaded page would silently report "no errors").
# NOTE: unknown /api/** paths are NOT usable (measured: they answer 200 here), so send an unsupported method
# to a read-only endpoint instead - Spring answers 405/400 and nothing is written.
function ErrHookSelfTest() {
  # read-only request to a row that does not exist => the module answers with R.fail(...) => body code >= 400
  # (envelope style). Falls back to an unsupported HTTP method in case that endpoint answers 200.
  $cands = @(
    @{ p = '/api/sale/exchange/999999999'; m = 'GET' },
    @{ p = '/api/inventory/sale/page'; m = 'DELETE' },
    @{ p = '/api/product/page'; m = 'DELETE' }
  )
  foreach ($c in $cands) {
    $pb = B64 $c.p; $mb = B64 $c.m
    EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));window.fetch(T('$pb'),{method:T('$mb')}).catch(()=>{});return 'fired'})()" | Out-Null
    Start-Sleep -Milliseconds 1100
    $e = Errs
    if ($e -match 'HTTP[45]|APICODE[45]') { return $e }
  }
  return (Errs)
}
function Errs() { return (EvalJs "JSON.stringify(window.__errs||[])") }
function ClearErrs() { EvalJs "window.__errs=[];'ok'" | Out-Null }

# ---- UI primitives ----
function ClickBtn([string]$key, [string]$scope = '') {
  $b = B64 (ZH $key); $sc = B64 $scope
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const sc=T('$sc');let root=document;if(sc){const r=document.querySelector(sc);if(!r)return 'NOSCOPE';root=r}const vis=e=>e.getClientRects().length>0;const bs=[...root.querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(!bs.length)return 'NOBTN:'+t;bs[0].click();return 'OK'})()"
  return (EvalJs $js)
}
function ClickBtnIdx([string]$key, [int]$idx) {
  $b = B64 (ZH $key)
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(bs.length<=$idx)return 'NOIDX:'+t+'/'+bs.length;bs[$idx].click();return 'OK'})()"
  return (EvalJs $js)
}
# 2026-09-21: click a TAB by ELEMENT (.el-tabs__item), not by raw text search. A tab label can be IDENTICAL
# to a left-menu item (e.g. the "加工退货" tab vs the "加工退货" menu), and ClickText walks the document in
# DOM order, so it hits the sidebar menu first and the tab never switches. Use this helper for page tabs.
function ClickTab([string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.el-tabs__item')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(!it.length)return 'NOTAB:'+t;it[0].click();return 'OK'})()"
  return (EvalJs $js)
}
function ClickTabIdx([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.el-tabs__item')].filter(vis);if(it.length<=$idx)return 'NOTAB have='+it.length;it[$idx].click();return 'OK'})()"
  return (EvalJs $js)
}
function ClickText([string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const es=[...document.querySelectorAll('a,span,div,li,td,button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(!es.length)return 'NOTEXT:'+t;es[0].click();return 'OK'})()"
  return (EvalJs $js)
}
function FillLabel([string]$key, [string]$value) {
  $b = B64 (ZH $key); $v = B64 $value
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$b'),V=T('$v');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const items=[...root.querySelectorAll('.el-form-item')].filter(vis);for(const it of items){const lab=it.querySelector('.el-form-item__label');if(!lab)continue;const t=(lab.innerText||'').replace(/[\s*:]/g,'');if(t!==L)continue;const inp=it.querySelector('input:not([type=hidden]):not([readonly]),textarea');if(!inp)return 'NOINPUT';const proto=inp.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;const set=Object.getOwnPropertyDescriptor(proto,'value').set;set.call(inp,V);inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'}return 'NOLABEL:'+L})()"
  return (EvalJs $js)
}
function OpenSelect([string]$key) {
  $b = B64 (ZH $key)
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$b');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const items=[...root.querySelectorAll('.el-form-item')].filter(vis);for(const it of items){const lab=it.querySelector('.el-form-item__label');if(!lab)continue;if((lab.innerText||'').replace(/[\s*:]/g,'')!==L)continue;const sc=it.querySelector('.el-select');if(!sc)return 'NOSELECT';const inp=sc.querySelector('input');(inp||sc).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||sc).click();return 'OK'}return 'NOLABEL:'+L})()"
  return (EvalJs $js)
}
function OpenSelectLabelIdx([string]$key, [int]$nth = 0) {
  $b = B64 (ZH $key)
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$b');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis).filter(it=>{const lab=it.querySelector('.el-form-item__label');return lab&&(lab.innerText||'').replace(/[\s*:]/g,'')===L});if(items.length<=$nth)return 'NOITEM:'+items.length;const sc=items[$nth].querySelector('.el-select');if(!sc)return 'NOSELECT';const inp=sc.querySelector('input');(inp||sc).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||sc).click();return 'OK'})()"
  return (EvalJs $js)
}
function PickFirstOption([int]$wait = 1200) {
  Start-Sleep -Milliseconds $wait
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()!=='');if(li.length){li[0].click();return 'OK:'+(li[0].innerText||'').trim()}}return 'NOOPT'})()"
  return (EvalJs $js)
}
function OpenSelectIdx([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const sels=[...document.querySelectorAll('.el-select')].filter(vis);if(sels.length<=$idx)return 'NOSELECT:'+sels.length;const inp=sels[$idx].querySelector('input');(inp||sels[$idx]).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||sels[$idx]).click();return 'OK'})()"
  return (EvalJs $js)
}
function DialogOpenSelect([int]$idx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const dl=d.length?d[d.length-1]:document;const sels=[...dl.querySelectorAll('.el-select')].filter(vis);if(sels.length<=$idx)return 'NOSELECT:'+sels.length;const inp=sels[$idx].querySelector('input');(inp||sels[$idx]).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||sels[$idx]).click();return 'OK'})()"
  return (EvalJs $js)
}
function DialogSetInput([int]$idx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const dl=d.length?d[d.length-1]:document;const ins=[...dl.querySelectorAll('input:not([type=hidden])')].filter(vis);if(ins.length<=$idx)return 'NOINPUT:'+ins.length;const el=ins[$idx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}
# find row index where the Nth cell contains `text` (and optionally the row contains `alsoKey`)
function FindRowByCol([int]$colIdx, [string]$text, [string]$alsoKey = '') {
  $a = B64 $text
  $b = B64 $(if ($alsoKey -ne '') { ZH $alsoKey } else { '' })
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const A=T('$a'),B=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){const tds=[...rs[i].querySelectorAll('td')];const cell=tds[$colIdx]?(tds[$colIdx].innerText||''):'';if(cell.indexOf(A)<0)continue;if(B!==''&&(rs[i].innerText||'').indexOf(B)<0)continue;return String(i)}return '-1'})()"
  return (EvalJs $js)
}
# numeric text of the Nth cell of row `rowIdx`
function CellText([int]$rowIdx, [int]$colIdx) {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return '-';const tds=[...rs[$rowIdx].querySelectorAll('td')];return tds[$colIdx]?(tds[$colIdx].innerText||'').trim():'-'})()")
}
# 物料信息管理页里某个物料的「库存总量」数值（列索引 6）
function MaterialTotal([string]$matNameRe) {
  Open '/outsource/material-info' 2600
  $b = B64 $matNameRe
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const N=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(const r of rs){if((r.innerText||'').indexOf(N)>=0){const tds=[...r.querySelectorAll('td')];return tds[6]?(tds[6].innerText||'').trim():'NOCOL'}}return 'NOTFOUND'})()"
  return (EvalJs $js)
}
function ClickDialogText([string]$key) {
  $b = B64 (ZH $key)
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const es=[...root.querySelectorAll('a,span,div,li,label,p')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(!es.length)return 'NOTEXT:'+t;es[0].click();return 'OK'})()"
  return (EvalJs $js)
}
function CheckedBoxes() {
  return (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;return JSON.stringify([...root.querySelectorAll('.el-checkbox')].filter(vis).map(c=>((c.innerText||'').trim())+'='+c.classList.contains('is-checked')))})()")
}
# item-table helpers: operate on the last visible table (dialog scoped if a dialog is open)
function SetRowInput([int]$rowIdx, [int]$inputIdx, [string]$value) {
  $v = B64 $value
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const ts=[...root.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const ins=[...rs[$rowIdx].querySelectorAll('input:not([type=hidden])')];if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK'})()"
  return (EvalJs $js)
}
function OpenRowSelect([int]$rowIdx, [int]$selIdx) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const ts=[...root.querySelectorAll('.el-table')].filter(vis);const t=ts[ts.length-1];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const scs=[...rs[$rowIdx].querySelectorAll('.el-select')];if(scs.length<=$selIdx)return 'NOSELECT:'+scs.length;const inp=scs[$selIdx].querySelector('input');(inp||scs[$selIdx]).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||scs[$selIdx]).click();return 'OK'})()"
  return (EvalJs $js)
}
function ClickRowBtn([int]$rowIdx, [string]$key) {
  $b = B64 (ZH $key)
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog,.el-drawer')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const ts=[...root.querySelectorAll('.el-table')].filter(vis);const tb=ts[ts.length-1];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const bs=[...rs[$rowIdx].querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim()===t);if(!bs.length)return 'NOBTN';bs[0].click();return 'OK'})()"
  return (EvalJs $js)
}
function PickOptionB64([string]$b64) {
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const O=T('$b64');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').trim()===O);if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  return (EvalJs $js)
}
function SelectLabel([string]$key, [string]$optKey, [int]$wait = 900) {
  $r1 = OpenSelect $key
  if ($r1 -notmatch 'OK') { return "OPENFAIL:$r1" }
  Start-Sleep -Milliseconds $wait
  $r2 = PickOptionB64 (B64 (ZH $optKey))
  if ($r2 -notmatch 'OK') { return "PICKFAIL:$r2" }
  return 'OK'
}
function SelectLabelText([string]$key, [string]$optionText, [int]$wait = 900) {
  $r1 = OpenSelect $key
  if ($r1 -notmatch 'OK') { return "OPENFAIL:$r1" }
  Start-Sleep -Milliseconds $wait
  return (PickOptionB64 (B64 $optionText))
}
function TypeInSelect([string]$key, [string]$keyword, [int]$wait = 1500) {
  $r1 = OpenSelect $key
  if ($r1 -notmatch 'OK') { return "OPENFAIL:$r1" }
  Start-Sleep -Milliseconds 400
  $v = B64 $keyword
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const V=T('$v');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);if(!dds.length)return 'NODD';const inp=dds[0].parentElement?dds[0].querySelector('input'):null;const host=[...document.querySelectorAll('.el-select')].filter(e=>vis(e)).pop();const el=(inp||(host?host.querySelector('input'):null));if(!el)return 'NOINPUT';const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(el,V);el.dispatchEvent(new Event('input',{bubbles:true}));return 'OK'})()"
  $r = EvalJs $js
  Start-Sleep -Milliseconds $wait
  return $r
}
function PickOptionContains([string]$text, [int]$wait = 0) {
  $b = B64 $text
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const O=T('$b');const vis=e=>e.getClientRects().length>0;const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);for(const d of dds){const li=[...d.querySelectorAll('li')].filter(e=>vis(e)&&(e.innerText||'').indexOf(O)>=0);if(li.length){li[0].click();return 'OK'}}return 'NOOPT:'+O+'/dd='+dds.length})()"
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
  return (EvalJs $js)
}
function SelectLabelContains([string]$labelKey, [string]$containsText, [int]$wait = 1200) {
  $r1 = OpenSelect $labelKey
  if ($r1 -notmatch 'OK') { return "OPENFAIL:$r1" }
  Start-Sleep -Milliseconds $wait
  $r2 = PickOptionContains $containsText
  if ($r2 -notmatch 'OK') { return "PICKFAIL:$r2" }
  return 'OK'
}
function ConfirmBox([int]$wait = 900) {
  Start-Sleep -Milliseconds $wait
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('.el-message-box button')].filter(vis);if(!bs.length)return 'NOBOX';bs[bs.length-1].click();return 'OK'})()"
  return (EvalJs $js)
}
function ClickRowBtnContains([int]$rowIdx, [string]$containsText) {
  $b = B64 $containsText
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const bs=[...rs[$rowIdx].querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim().indexOf(t)>=0);if(!bs.length)return 'NOBTN';bs[0].click();return 'OK'})()"
  return (EvalJs $js)
}
function FindRow([string]$containsText) {
  $b = B64 $containsText
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){if((rs[i].innerText||'').indexOf(t)>=0)return String(i)}return '-1'})()"
  return (EvalJs $js)
}
function ClickDialogBtn([string]$key, [int]$wait = 1800) {
  Start-Sleep -Milliseconds $wait
  $r = ClickBtn $key '.el-dialog:not([style*="display: none"])'
  if ($r -notmatch 'OK') { $r = ClickBtn $key '.el-drawer:not([style*="display: none"])' }
  if ($r -notmatch 'OK') { $r = ClickBtn $key }
  return $r
}
function Rows([int]$idx = 0) {
  $js = "(()=>{const ts=[...document.querySelectorAll('.el-table')].filter(e=>e.getClientRects().length>0);const t=ts[$idx];if(!t)return JSON.stringify({err:'NOTABLE/'+ts.length});const hs=[...t.querySelectorAll('.el-table__header th')].map(th=>(th.innerText||'').replace(/\s+/g,' ').trim());const rs=[...t.querySelectorAll('.el-table__body tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>(td.innerText||'').replace(/\s+/g,' ').trim()));return JSON.stringify({n:rs.length,head:hs,rows:rs})})()"
  $raw = EvalJs $js
  try { return $raw | ConvertFrom-Json } catch { return $null }
}
function Txt([string]$selector) {
  $s = $selector.Replace("'", "")
  return (EvalJs "(()=>{const e=document.querySelector('$s');return e?(e.innerText||'').replace(/\s+/g,' ').trim():'NOEL'})()")
}
function BodyHas([string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));return String((document.body.innerText||'').indexOf(T('$b'))>=0)})()"
  return (EvalJs $js)
}
function Ok([bool]$cond, [string]$msg) {
  if ($cond) { $script:PASS++; Write-Host ("PASS " + $msg) } else { $script:FAIL++; Write-Host ("FAIL " + $msg) }
}
function Summary([string]$title) {
  # 2026-09-21: 防"哑用例假绿" —— 一条断言都没跑时绝不算 PASS。
  # 触发实例：ui-e2e-8-finance 原为纯人工 dump 脚本（没有任何 Ok 断言），却输出 RESULT PASS (PASS=0 FAIL=0)，
  # 被当成"财务链已验证"。这类脚本要么补真断言（见 verify-finance-kpi.ps1），要么别调用 Summary。
  $total = [int]$script:PASS + [int]$script:FAIL
  if ($total -eq 0) {
    Write-Host "RESULT FAIL $title  (PASS=0 FAIL=0 -- NO assertions were executed: this script asserts nothing, so it must not report PASS)"
    return
  }
  Write-Host ("RESULT " + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + " $title  (PASS=$($script:PASS) FAIL=$($script:FAIL))")
}
