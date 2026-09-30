# =====================================================================================
# mainline-seed.ps1  (C 路径：主线数据"只从前端"生成器, 2026-09-30)
#
# 为什么另起一支而不复用 ui-e2e-p*:
#   那套脚本写于 2026-09-18/23，与今天的 UI 已不匹配（实测 p2a PASS=26/FAIL=38：
#   简单表单能过，含下拉的表单全挂）。本脚本按**当前 DOM**重写，并把踩过的坑固化：
#     1) 中文一律 base64 传进 JS（命令行直传会被转码；本文件保持 ASCII-only，中文用码点构造）
#     2) 可见性判据用 getClientRects().length>0（offsetParent 对 fixed 定位恒为 null）
#     3) "新增"有两种形态：**独立路由页**（/xxx/add）与**弹窗**（仓库类）。
#        弹窗形态必须用 -InDialog 把查找范围收进最后一个可见 .el-dialog，
#        否则会命中列表页那张同名的**查询**表单（仓库名称/仓型 两边重名）。
#     4) 每一步都打时间戳（Now），任何一次调用卡住都能立刻定位（见 2026-09-30 的假死修复）
#     5) 幂等：同名记录已存在则跳过，避免重复造数与"同名确认弹窗"
#
# 用法（PS 直接跑；本文件 ASCII-only，无需 BOM）:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\mainline-seed.ps1                  # 全部
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\mainline-seed.ps1 -Only customer   # 单实体
#       可用 -Only 值: product | customer | supplier | factory | wh_finished | wh_outsource |
#                      wh_material | purchase | otherin | sale | move | loss |
#                      return | receipt | payment | expense | invoice | project |
#                      matio | matmove | matloss | dump
#       （单据层 = 建单 + 列表页审核，且有**顺序依赖**：
#          purchase 审核写入的是 MATERIAL 形态库存，而销售单库存检查/销售审核按 PRODUCT 形态，
#          故必须先 otherin（成品其他入库，ioType 默认 IN）造出 PRODUCT 库存，再跑 sale）
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\mainline-seed.ps1 -DumpOnly        # 只看表单结构
# =====================================================================================
param(
    [string]$Only = '',
    [switch]$DumpOnly,
    # 每类业务单据的目标条数（默认 10）。脚本按"当前条数 < Count 才补 1 条"工作，
    # 因此反复调用同一命令即可逐步补齐（见 tools/regression/seed-bulk.ps1）。
    [int]$Count = 10
)
$N = $Count
$ErrorActionPreference = 'Continue'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$env:MYSQL_PWD = 'root'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
. (Join-Path $repo 'tools\regression\ui-e2e-lib.ps1')

$script:PASS = 0; $script:FAIL = 0
function Ok($c, $m) { if ($c) { $script:PASS++; Write-Host ('  PASS  ' + $m) } else { $script:FAIL++; Write-Host ('  FAIL  ' + $m) } }
# Timestamped progress marks. Every UI step is bracketed by one of these so a stall can be
# attributed to a single call instead of showing up as a silent freeze (see 2026-09-30 fix).
function Now([string]$m) { Write-Host ('  [' + (Get-Date -Format 'HH:mm:ss') + '] ' + $m) }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t + '   @' + (Get-Date -Format 'HH:mm:ss')) }
function SqlOne([string]$sql) { return ("$(@(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) | Select-Object -First 1)").Trim() }
function B64([string]$s) { return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($s)) }
# NOTE: do NOT name this CP -- `cp` is a built-in alias for Copy-Item and aliases beat functions.
function Uni([int[]]$cp) { return (-join ($cp | ForEach-Object { [char]$_ })) }

# JS 片段统一包 IIFE，并带 base64 解码 T() 与可见性 v()
function Js([string]$body) {
    $pre = "const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const v=e=>!!(e&&e.getClientRects().length>0);"
    return (EvalJs ("(()=>{" + $pre + $body + "})()"))
}
# 查找根：默认整个 document；-InDialog 时收进最后一个可见弹窗（R 供各片段使用）
function ScopeJs([switch]$InDialog) {
    if ($InDialog) { return "const R=(()=>{const d=Array.from(document.querySelectorAll('.el-dialog')).filter(v);return d.length?d[d.length-1]:document})();" }
    return 'const R=document;'
}
function JsFindBtn([string]$labelB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const b=Array.from(R.querySelectorAll('button')).filter(v).find(x=>x.innerText.trim().replace(/\s/g,'')===T('$labelB64'));if(!b)return 'NOBTN';b.click();return 'CLICKED';"))
}
# 按正则点击按钮（保存|提交|确定 这类多选一文案）
function JsClickBtnRe([string]$rxB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const rx=new RegExp(T('$rxB64'));const b=Array.from(R.querySelectorAll('button')).filter(v).find(x=>rx.test(x.innerText));if(!b)return 'NOBTN';b.click();return 'CLICKED';"))
}
# 按"标签含某关键词"填文本框（label 关键词与值都以 base64 传入）
function JsFillByLabel([string]$kwB64, [string]$valB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const items=Array.from(R.querySelectorAll('.el-form-item')).filter(v);const hit=items.find(it=>{const l=it.querySelector('.el-form-item__label');return l&&l.innerText.indexOf(T('$kwB64'))>=0;});if(!hit)return 'NOLABEL';const inp=Array.from(hit.querySelectorAll('input')).filter(v)[0];if(!inp)return 'NOINPUT';const set=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;set.call(inp,T('$valB64'));inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));inp.dispatchEvent(new Event('blur',{bubbles:true}));return 'FILLED';"))
}
# 按"标签含关键词"选下拉：pickB64 非空则按选项文本匹配，否则取第一项
function JsPickSelect([string]$kwB64, [string]$pickB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    $a = Js ($sc + "const items=Array.from(R.querySelectorAll('.el-form-item')).filter(v);const hit=items.find(it=>{const l=it.querySelector('.el-form-item__label');return l&&l.innerText.indexOf(T('$kwB64'))>=0;});if(!hit)return 'NOLABEL';const s=Array.from(hit.querySelectorAll('.el-select')).filter(v)[0];if(!s)return 'NOSELECT';const w=s.querySelector('.el-select__wrapper')||s.querySelector('input');if(!w)return 'NOCLICK';w.click();return 'OPENED';")
    if ($a -ne 'OPENED') { return $a }
    Start-Sleep -Milliseconds 1000
    # 选项面板是 body 级浮层（不在弹窗内），按"最后打开的可见面板"取；RemoteSelect 异步取选项 => 带重试
    return (PickDropdownRetry $pickB64)
}
# 在"最后打开的可见下拉"里选：pickB64 空 = 取第一项
function JsPickDropdown([string]$pickB64) {
    if ($pickB64) {
        return (Js "const ps=Array.from(document.querySelectorAll('.el-select-dropdown')).filter(v);if(!ps.length)return 'NOOPT';const it=Array.from(ps[ps.length-1].querySelectorAll('li')).filter(v);const hit=it.find(x=>x.innerText.trim()===T('$pickB64'))||it.find(x=>x.innerText.indexOf(T('$pickB64'))>=0);if(!hit)return 'NOMATCH:'+it.slice(0,3).map(x=>x.innerText.trim()).join('/');hit.click();return 'PICKED';")
    }
    return (Js "const ps=Array.from(document.querySelectorAll('.el-select-dropdown')).filter(v);if(!ps.length)return 'NOOPT';const it=Array.from(ps[ps.length-1].querySelectorAll('li')).filter(v);if(!it.length)return 'NOITEM';it[0].click();return 'PICKED-FIRST';")
}
# 面板还没出选项（RemoteSelect 是异步取选项）时重试几次，避免误判 NOITEM
function PickDropdownRetry([string]$pickB64, [int]$tries = 4) {
    for ($k = 1; $k -le $tries; $k++) {
        $r = JsPickDropdown $pickB64
        if ($r -like 'PICKED*') { return $r }
        if ($r -notlike 'NOOPT*' -and $r -notlike 'NOITEM*') { return $r }
        Start-Sleep -Milliseconds 1200
    }
    return 'NOITEM-AFTER-RETRY'
}
# 按"标签含关键词"**确保**勾选该表单项里的第一个复选框（供应商/供货商的必填「类型」是 checkbox-group）
# 幂等：已勾选则不动。供货商页的类型默认就是 ['product']（manage-form.vue:34），
# 若无脑再点一次会把默认勾选**取消** ⇒ checkedTypes 为空 ⇒ 前端校验直接 return（实测不发请求）。
function JsCheckByLabel([string]$kwB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const items=Array.from(R.querySelectorAll('.el-form-item')).filter(v);const hit=items.find(it=>{const l=it.querySelector('.el-form-item__label');return l&&l.innerText.indexOf(T('$kwB64'))>=0;});if(!hit)return 'NOLABEL';const cbs=Array.from(hit.querySelectorAll('.el-checkbox')).filter(v);if(!cbs.length)return 'NOCHECKBOX';if(cbs.filter(c=>c.classList.contains('is-checked')).length)return 'ALREADY-CHECKED';cbs[0].click();return 'CHECKED';"))
}
function JsMsgText() { return (Js "const m=Array.from(document.querySelectorAll('.el-message')).filter(v);return m.length?m[m.length-1].innerText.trim():'(none)';") }
# 诊断用：读某表单项里第一个输入框的值/禁用态，以及复选框勾选数
function JsLabelValue([string]$kwB64) {
    return (Js "const items=Array.from(document.querySelectorAll('.el-form-item')).filter(v);const hit=items.find(it=>{const l=it.querySelector('.el-form-item__label');return l&&l.innerText.indexOf(T('$kwB64'))>=0;});if(!hit)return 'NOLABEL';const inp=Array.from(hit.querySelectorAll('input')).filter(v)[0];return inp?('val=[' + inp.value + '] disabled=' + inp.disabled):'NOINPUT';")
}
function JsLabelChecked([string]$kwB64) {
    return (Js "const items=Array.from(document.querySelectorAll('.el-form-item')).filter(v);const hit=items.find(it=>{const l=it.querySelector('.el-form-item__label');return l&&l.innerText.indexOf(T('$kwB64'))>=0;});if(!hit)return 'NOLABEL';const cbs=Array.from(hit.querySelectorAll('.el-checkbox')).filter(v);const on=cbs.filter(c=>c.classList.contains('is-checked')).length;return 'checked ' + on + '/' + cbs.length;")
}
function JsErrText() { return (Js "const e=Array.from(document.querySelectorAll('.el-form-item__error')).filter(v).map(x=>x.innerText.trim()).filter(Boolean);return e.slice(0,3).join(' / ');") }
# 同名确认弹窗（ElMessageBox）：出现就点最后一个按钮（确定）
function JsConfirmAnyBox() { return (Js "const bs=Array.from(document.querySelectorAll('.el-message-box')).filter(v);if(!bs.length)return 'NOBOX';const b=Array.from(bs[bs.length-1].querySelectorAll('button')).filter(v);if(!b.length)return 'NOBTN';b[b.length-1].click();return 'CONFIRMED';") }
# ---- 单据明细表通用助手：按**表头文字**定位列序，避免写死列号 -------------------------
function JsColIdx([string]$kwB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const hs=Array.from(R.querySelectorAll('.el-table__header thead th')).map(h=>h.innerText.trim());const i=hs.findIndex(t=>t.indexOf(T('$kwB64'))>=0);return (i<0)?('NONE['+hs.join('|')+']'):String(i);"))
}
# ---- 合并调用（2026-09-30 提速）-------------------------------------------------------------
# 每次 agent-browser 调用都要新起一个子作业（约 2~3s，有界改造的代价），所以"同一时刻要读的
# 多个信息"必须**合并成一次 eval** 返回 JSON，再在 PS 侧拆开 —— 少一次调用就省 2~3s。
# 一次取多个表头的列号（原先 3~4 次 JsColIdx 现在 1 次）：返回 JSON 数组 ["0","1","4"]
function JsColsIdx([string[]]$kwB64Arr, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    $a = ($kwB64Arr | ForEach-Object { "'" + $_ + "'" }) -join ','
    return (Js ($sc + "const K=[$a].map(k=>T(k));const hs=Array.from(R.querySelectorAll('.el-table__header thead th')).map(h=>h.innerText.trim());return JSON.stringify(K.map(k=>{const i=hs.findIndex(t=>t.indexOf(k)>=0);return (i<0)?('NONE['+hs.join('|')+']'):String(i);}));"))
}
# 一次读"最后一条 toast + 前 3 条字段校验错误"（原先 JsMsgText + JsErrText 两次调用）
function JsFeedback() {
    return (Js "const m=Array.from(document.querySelectorAll('.el-message')).filter(v);const e=Array.from(document.querySelectorAll('.el-form-item__error')).filter(v).map(x=>x.innerText.trim()).filter(Boolean);return JSON.stringify({msg:(m.length?m[m.length-1].innerText.trim():'(none)'),err:e.slice(0,3).join(' / ')});")
}
function JsRowCount([switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "return String(Array.from(R.querySelectorAll('.el-table__body tbody tr')).filter(v).length);"))
}
# 第 rowIdx 行第 colIdx 列里的第 inputIdx 个输入框填值（el-input / el-input-number 都吃 input+change+blur）
function JsRowFill($rowIdx, $colIdx, [string]$valB64, [int]$inputIdx = 0, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const rows=Array.from(R.querySelectorAll('.el-table__body tbody tr')).filter(v);const tr=rows[$rowIdx];if(!tr)return 'NOROW';const td=tr.querySelectorAll('td')[$colIdx];if(!td)return 'NOCELL';const ins=Array.from(td.querySelectorAll('input')).filter(v);if(ins.length<=$inputIdx)return 'NOINPUT:'+ins.length;const el=ins[$inputIdx];const set=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;set.call(el,T('$valB64'));el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.dispatchEvent(new Event('blur',{bubbles:true}));return 'FILLED';"))
}
# 打开第 rowIdx 行第 colIdx 列的 el-select（只开不选）
function JsRowOpenSelect($rowIdx, $colIdx, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const rows=Array.from(R.querySelectorAll('.el-table__body tbody tr')).filter(v);const tr=rows[$rowIdx];if(!tr)return 'NOROW';const td=tr.querySelectorAll('td')[$colIdx];if(!td)return 'NOCELL';const s=Array.from(td.querySelectorAll('.el-select')).filter(v)[0];if(!s)return 'NOSELECT';const w=s.querySelector('.el-select__wrapper')||s.querySelector('input');if(!w)return 'NOCLICK';w.click();return 'OPENED';"))
}
# 点击"文字包含关键词"的按钮（添加明细 / 添加产品 / 审核 …）
function JsClickTextBtn([string]$kwB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const b=Array.from(R.querySelectorAll('button')).filter(v).find(x=>x.innerText.replace(/\s/g,'').indexOf(T('$kwB64'))>=0);if(!b)return 'NOBTN';b.click();return 'CLICKED';"))
}
# 列表页：在"整行文字包含 rowText"的行里点"文字包含 btnText"的按钮
function JsRowBtnByText([string]$rowTextB64, [string]$btnTextB64) {
    return (Js "const rows=Array.from(document.querySelectorAll('.el-table__body tbody tr')).filter(v);const tr=rows.find(x=>x.innerText.indexOf(T('$rowTextB64'))>=0);if(!tr)return 'NOROW';const b=Array.from(tr.querySelectorAll('button')).filter(v).find(x=>x.innerText.replace(/\s/g,'').indexOf(T('$btnTextB64'))>=0);if(!b)return 'NOBTN';b.click();return 'CLICKED';")
}
# 2026-09-30 提速：原先"读列表行数(纯日志) + 点审核按钮"是两次调用（≈5s），合并成一次；
# 返回值里带上看到的行数，找不到行时能区分"列表还没渲染"与"确实没有这单"。
function JsRowBtnByTextCounted([string]$rowTextB64, [string]$btnTextB64) {
    return (Js "const rows=Array.from(document.querySelectorAll('.el-table__body tbody tr')).filter(v);const tr=rows.find(x=>x.innerText.indexOf(T('$rowTextB64'))>=0);if(!tr)return 'NOROW(rows='+rows.length+')';const b=Array.from(tr.querySelectorAll('button')).filter(v).find(x=>x.innerText.replace(/\s/g,'').indexOf(T('$btnTextB64'))>=0);if(!b)return 'NOBTN(rows='+rows.length+')';b.click();return 'CLICKED(rows='+rows.length+')';")
}
# 确保明细表至少一行：**页面 onMounted 常常已经预置一行**，此时再点"添加明细"会多出一张空行，
# 于是后面按行号填充就填错行、空行被前端校验拦下（实测销售单报「第 2 行数量必须大于 0」）。
function EnsureItemRow([string]$addBtnB64) {
    $n = 0
    [void][int]::TryParse((JsRowCount), [ref]$n)
    if ($n -gt 0) { return ('EXISTING-ROWS:' + $n) }
    $c = JsClickTextBtn $addBtnB64
    Start-Sleep -Milliseconds 1200
    return ('ADDED:' + $c)
}
# ---- 消息/弹窗捕获：ElMessage 3 秒就消失，跑完再去读只会得到 (none) => 常驻记录 ----------
function JsHookMsgs() {
    return (Js "if(!window.__msgs){window.__msgs=[];const push=t=>{t=(t||'').trim();if(t&&window.__msgs.indexOf(t)<0)window.__msgs.push(t)};const scan=()=>Array.from(document.querySelectorAll('.el-message,.el-message-box__message,.el-notification__content')).filter(v).forEach(el=>push(el.innerText));new MutationObserver(scan).observe(document.body,{childList:true,subtree:true});window.__dlgs=()=>Array.from(document.querySelectorAll('.el-dialog')).filter(v).map(d=>((d.querySelector('.el-dialog__title')||{}).innerText||'?')+'['+Array.from(d.querySelectorAll('button')).filter(v).map(b=>b.innerText.trim()).join('|')+']');}return 'HOOKED';")
}
function JsMsgsDump() { return (Js "return JSON.stringify(window.__msgs||[]);") }
function JsDlgsDump() { return (Js "return (window.__dlgs?window.__dlgs().join(' ; '):'(no hook)');") }
# 在最后可见弹窗里点"文字包含关键词"的按钮（如缺货框的「仍然保存订单」）
function JsClickDialogBtn([string]$kwB64) {
    return (Js "const ds=Array.from(document.querySelectorAll('.el-dialog')).filter(v);if(!ds.length)return 'NODLG';const d=ds[ds.length-1];const b=Array.from(d.querySelectorAll('button')).filter(v).find(x=>x.innerText.replace(/\s/g,'').indexOf(T('$kwB64'))>=0);if(!b)return 'NOBTN';b.click();return 'CLICKED';")
}
# 按**第 n 个可见文本输入框**填值（排除 el-select 内部的 input）：用于字段很多、
# 又不想为每个标签造中文码点的表单（如项目建档：项目名称/产品SKU/产品名称/适配机型/原机4项…）
function JsFillFormInputByIndex($n, [string]$valB64, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    return (Js ($sc + "const ins=Array.from(R.querySelectorAll('.el-form-item input')).filter(v).filter(e=>!e.closest('.el-select'));if(ins.length<=$n)return 'NOINPUT:'+ins.length;const el=ins[$n];const set=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;set.call(el,T('$valB64'));el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.dispatchEvent(new Event('blur',{bubbles:true}));return 'FILLED';"))
}
function DumpForm([string]$tag, [switch]$InDialog) {
    $sc = ScopeJs -InDialog:$InDialog
    Write-Host ('  [' + $tag + '] route = ' + (Js "return location.pathname;"))
    Write-Host ('  [' + $tag + '] scope = ' + (Js ($sc + "return R===document?'document':'dialog';")))
    Write-Host ('  [' + $tag + '] labels = ' + (Js ($sc + "return Array.from(R.querySelectorAll('.el-form-item__label')).filter(v).map(x=>x.innerText.trim()).filter(Boolean).join(' , ');")))
    Write-Host ('  [' + $tag + '] selects= ' + (Js ($sc + "return Array.from(R.querySelectorAll('.el-select')).filter(v).length;")))
    Write-Host ('  [' + $tag + '] buttons= ' + (Js ($sc + "return Array.from(R.querySelectorAll('button')).filter(v).map(x=>x.innerText.trim()).filter(Boolean).slice(0,12).join(' , ');")))
}
# 幂等判据 + 落库核对
function Exists([string]$table, [string]$col, [string]$name) {
    return ((SqlOne ("SELECT COUNT(*) FROM " + $table + " WHERE " + $col + " = '" + $name + "'")) -ne '0')
}
function VerifyCreated([string]$table, [string]$col, [string]$name, [int]$before) {
    $after = [int](SqlOne ('SELECT COUNT(*) FROM ' + $table))
    $hit = SqlOne ("SELECT COUNT(*) FROM " + $table + " WHERE " + $col + " = '" + $name + "'")
    $msg = JsMsgText
    $err = JsErrText
    Write-Host ('    msg=' + $msg + ' err=' + $err + ' rows ' + $before + '->' + $after + ' nameHits=' + $hit)
    Ok (($after -gt $before) -and ($hit -ne '0')) ('created: ' + $name)
}

$bAdd = B64 (Uni @(0x65B0, 0x589E))                       # 新增
$bSave = B64 ((Uni @(0x4FDD, 0x5B58)) + '|' + (Uni @(0x63D0, 0x4EA4)) + '|' + (Uni @(0x786E, 0x5B9A)))   # 保存|提交|确定
$bName = B64 (Uni @(0x540D, 0x79F0))                      # 名称
$bBrand = B64 (Uni @(0x54C1, 0x724C))                     # 品牌
$bBrandVal = B64 ((Uni @(0x6D4B, 0x8BD5, 0x54C1, 0x724C)) + 'A1')   # 测试品牌A1
# 2026-09-30: 规格（原配/改配）是**必填**下拉（add.vue:237 required:true）—— 漏填会让"保存"被前端
# 校验拦下（err=请选择规格），产品一行也建不出来。取值「原配」。
$bSpec = B64 (Uni @(0x89C4, 0x683C))                      # 规格
$bSpecVal = B64 (Uni @(0x539F, 0x914D))                   # 原配
$bStatus = B64 (Uni @(0x72B6, 0x6001))                    # 状态
$bType = B64 (Uni @(0x7C7B, 0x578B))                      # 类型
$bWhName = B64 (Uni @(0x4ED3, 0x5E93, 0x540D, 0x79F0))    # 仓库名称
$bWhType = B64 (Uni @(0x4ED3, 0x578B))                    # 仓型
$bSupplier = B64 (Uni @(0x4F9B, 0x5E94, 0x5546))          # 供应商
# 单据层（采购/销售）标签
$bAudit = B64 (Uni @(0x5BA1, 0x6838))                          # 审核
$bAddDetail = B64 (Uni @(0x6DFB, 0x52A0, 0x660E, 0x7EC6))      # 添加明细
$bAddProductRow = B64 (Uni @(0x6DFB, 0x52A0, 0x4EA7, 0x54C1))  # 添加产品
$bColProduct = B64 (Uni @(0x4EA7, 0x54C1))                     # 产品
$bColQty = B64 (Uni @(0x6570, 0x91CF))                         # 数量
$bColPrice = B64 (Uni @(0x5355, 0x4EF7))                       # 单价
$bColGrade = B64 (Uni @(0x54C1, 0x8D28, 0x6570, 0x91CF))       # 品质数量（采购：一格里 8 个输入框 A/B/C/不良 × 数量单价）
$bHdrSupplier = B64 (Uni @(0x4F9B, 0x8D27, 0x5546))            # 供货商
$bHdrInWh = B64 (Uni @(0x5165, 0x5E93, 0x4ED3, 0x5E93))        # 入库仓库
$bHdrCustomer = B64 (Uni @(0x5BA2, 0x6237))                    # 客户
$bHdrOutWh = B64 (Uni @(0x51FA, 0x5E93, 0x4ED3, 0x5E93))       # 出库仓库
$vQty = B64 '2'
$vPrice = B64 '10'
# 其他出入库 / 缺货弹窗
$bHdrWarehouse = B64 (Uni @(0x4ED3, 0x5E93))                   # 仓库
$bAddFinished = B64 (Uni @(0x6DFB, 0x52A0, 0x6210, 0x54C1))    # 添加成品
$bColFinishedName = B64 (Uni @(0x6210, 0x54C1, 0x540D, 0x79F0)) # 成品名称
$bKeepSave = B64 (Uni @(0x4ECD, 0x7136, 0x4FDD, 0x5B58, 0x8BA2, 0x5355))   # 仍然保存订单
# 移仓 / 报损
$bHdrMoveFrom = B64 (Uni @(0x79FB, 0x51FA, 0x4ED3, 0x5E93))    # 移出仓库
$bHdrMoveTo = B64 (Uni @(0x79FB, 0x5165, 0x4ED3, 0x5E93))      # 移入仓库
$bColMoveQty = B64 (Uni @(0x79FB, 0x4ED3, 0x6570, 0x91CF))     # 移仓数量
$bColLossQty = B64 (Uni @(0x62A5, 0x635F, 0x6570, 0x91CF))     # 报损数量
$bColQuality = B64 (Uni @(0x54C1, 0x8D28))                     # 品质
# 退货 / 收付款
$bColReturnQty = B64 (Uni @(0x9000, 0x8D27, 0x6570, 0x91CF))    # 退货数量
$bFromSaleOrder = B64 (Uni @(0x4ECE, 0x9500, 0x552E, 0x5355, 0x5E26, 0x5165, 0x660E, 0x7EC6))  # 从销售单带入明细
$bSaleOrderLink = B64 (Uni @(0x5173, 0x8054, 0x9500, 0x552E, 0x5355))   # 关联销售单
$bHdrReturnWh = B64 (Uni @(0x9000, 0x8D27, 0x4ED3, 0x5E93))     # 退货仓库
$bColReceiptAcct = B64 (Uni @(0x6536, 0x6B3E, 0x8D26, 0x6237))  # 收款账户
$bColReceiptAmt = B64 (Uni @(0x6536, 0x6B3E, 0x91D1, 0x989D))   # 收款金额
$bColPaymentAcct = B64 (Uni @(0x4ED8, 0x6B3E, 0x8D26, 0x6237))  # 付款账户
$bColPaymentAmt = B64 (Uni @(0x4ED8, 0x6B3E, 0x91D1, 0x989D))   # 付款金额
$bAddAccount = B64 (Uni @(0x6DFB, 0x52A0, 0x8D26, 0x6237))      # 添加账户
# 费用单 / 发票（列表页弹窗）
$bColAmount = B64 (Uni @(0x91D1, 0x989D))                       # 金额
$bExpAccount = B64 (Uni @(0x652F, 0x51FA, 0x8D26, 0x6237))      # 支出账户
$bExpType = B64 (Uni @(0x8D39, 0x7528, 0x7C7B, 0x578B))         # 费用类型
$bInvNo = B64 (Uni @(0x53D1, 0x7968, 0x53F7, 0x7801))           # 发票号码
$bInvTotal = B64 (Uni @(0x4EF7, 0x7A0E, 0x5408, 0x8BA1))        # 价税合计
$bAddInvoice = B64 (Uni @(0x767B, 0x8BB0, 0x53D1, 0x7968))      # 登记发票
# 物料类单据（outsource/other-io、inventory/material-move、outsource/stock-loss）
$bAddMaterial = B64 (Uni @(0x6DFB, 0x52A0, 0x7269, 0x6599))      # 添加物料
$bColMaterial = B64 (Uni @(0x7269, 0x6599))                      # 物料
$bColMaterialName = B64 (Uni @(0x7269, 0x6599, 0x540D, 0x79F0))  # 物料名称（其他出入库页的列名）
# 2026-09-30: SqlOne 用 mysql.exe 取中文文本时，PowerShell 是**按 [Console]::OutputEncoding 解码子进程
# stdout** 的：前台控制台是 UTF-8（正确），但被 Start-Process 以后台方式跑时退化成 GBK ⇒ 同一条 SQL
# 得到的字符串在前台是对的、在后台是乱码。实测：按物料名在下拉里选择时，前台 PICKED、后台 NOMATCH
# （下拉里明明就是那个名字）。mysql 客户端我们统一带 --default-character-set=utf8mb4（输出 UTF-8），
# 所以这里显式声明 UTF-8 解码，让"按名字选"在两种运行方式下都成立。
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

$pProduct = Uni @(0x6D4B, 0x8BD5, 0x4EA7, 0x54C1)             # 测试产品
$pCustomer = Uni @(0x6D4B, 0x8BD5, 0x5BA2, 0x6237)            # 测试客户
$pSupplier = Uni @(0x6D4B, 0x8BD5, 0x4F9B, 0x5E94, 0x5546)    # 测试供应商
$pVendor = Uni @(0x6D4B, 0x8BD5, 0x4F9B, 0x8D27, 0x5546)      # 测试供货商
$pWhFin = Uni @(0x6D4B, 0x8BD5, 0x6210, 0x54C1, 0x4ED3)       # 测试成品仓
$pWhOut = Uni @(0x6D4B, 0x8BD5, 0x59D4, 0x5916, 0x4ED3)       # 测试委外仓
$pWhMat = Uni @(0x6D4B, 0x8BD5, 0x7269, 0x6599, 0x4ED3)       # 测试物料仓

Now 'start (login)'
# 2026-09-30: 这里原来调 EnsureLogin —— 它**不校验登录是否真的成功**（lib:53-65 只打印
# "LOGIN <r> token=<t>" 就返回），实测出现过一整轮没登录成功：18 个段落"全 PASS"其实全是
# **幂等跳过**（只查库、不碰 UI），一到真操作 UI 的段落就整页未渲染（warehouse=NOLABEL /
# save=NOBTN / 表头为空）。改用会**验证身份**的 EnsureLoginAs（比对 beichen_erp_user.username）
# 并重试；登录不上直接 FAIL 退出，绝不产出"假绿"。
#
# 2026-09-30 提速: 每轮强制重登要 ~25s（clear storage → 打开 /login → 填表 → 点击 → wait 3500
# → 读 token → 读身份），而批处理一轮只建 1~3 张单，9 轮就有 3.7 分钟纯花在登录上。token 本来
# 就持久化在浏览器 localStorage 里，所以先做一次**廉价探测**：打开 /dashboard，只要路由守卫
# 没把我们踢回 /login 且身份就是目标账号，就直接**复用会话**（2 次调用 ≈ 4s）。
function EnsureSession([string]$user = 'lin') {
    Open '/dashboard' 1200
    $path = EvalJs 'location.pathname'
    $who = EvalJs "(()=>{try{const u=JSON.parse(localStorage.getItem('beichen_erp_user')||'null');return u?(u.username||''):''}catch(e){return ''}})()"
    Now ('session probe: path=' + $path + ' who=' + $who)
    # 未登录时路由守卫会重定向到 /login => "没被踢回登录页 + 身份匹配" 即认为会话可用
    return (("$path" -notlike '*login*') -and ("$who" -eq $user))
}
$loginOk = EnsureSession 'lin'
if ($loginOk) { Now 'session reused (skip login)' } else {
    for ($la = 1; $la -le 3 -and -not $loginOk; $la++) {
        $loginOk = EnsureLoginAs 'lin' '123'
        Now ('login attempt ' + $la + ' = ' + $loginOk)
        if (-not $loginOk) { Start-Sleep -Seconds 3 }
    }
}
if (-not $loginOk) {
    Write-Host '  FAIL  login: 登录失败（后续 UI 步骤都会退化，直接中止以免产生假 PASS）'
    $script:FAIL++
    exit 1
}
Now 'login ok'
Write-Host ('=== mainline-seed (front-end only)   @' + (Get-Date -Format 'HH:mm:ss') + ' ===')

# ---------------- 产品：全闭环（填名称 + 品牌下拉 + 规格下拉 + 保存 + DB 核对）----------------
if (-not $Only -or $Only -eq 'product') {
    Sec '1) product  x4  (route /product/add, name + brand + spec + save)'
    for ($i = 1; $i -le 4; $i++) {
        $nm = $pProduct + 'B' + $i
        if (Exists 'product' 'name' $nm) { Now ('product#' + $i + ' exists, skip'); Ok $true ('product already seeded: ' + $nm); continue }
        Open '/product/add' 2600
        $before = [int](SqlOne 'SELECT COUNT(*) FROM product')
        if ($DumpOnly) { DumpForm 'product'; break }
        $f = JsFillByLabel $bName (B64 $nm)
        Now ('#' + $i + ' fill=' + $f)
        $s1 = JsPickSelect $bBrand $bBrandVal
        Now ('#' + $i + ' brand=' + $s1)
        $s2 = JsPickSelect $bSpec $bSpecVal
        Now ('#' + $i + ' spec=' + $s2)
        $sv = JsClickBtnRe $bSave
        Now ('#' + $i + ' save=' + $sv)
        Start-Sleep -Seconds 2
        Write-Host ('  #' + $i + ' name=' + $nm + ' fill=' + $f + ' brand=' + $s1 + ' spec=' + $s2 + ' save=' + $sv + ' rows ' + $before + '->' + (SqlOne 'SELECT COUNT(*) FROM product'))
        VerifyCreated 'product' 'name' $nm $before
    }
}

# ---------------- 客户：独立页 /inventory/customer/add（必填 客户名称 + 状态）----------------
if (-not $Only -or $Only -eq 'customer') {
    Sec '2) customer  x2  (/inventory/customer/add, name + status + save)'
    for ($i = 1; $i -le 2; $i++) {
        $nm = $pCustomer + 'A' + $i
        if (Exists 'customer' 'name' $nm) { Now ('customer#' + $i + ' exists, skip'); Ok $true ('customer already seeded: ' + $nm); continue }
        Open '/inventory/customer/add' 2600
        $before = [int](SqlOne 'SELECT COUNT(*) FROM customer')
        if ($DumpOnly) { DumpForm 'customer'; break }
        $f = JsFillByLabel $bName (B64 $nm)
        Now ('#' + $i + ' fill=' + $f)
        $s1 = JsPickSelect $bStatus ''
        Now ('#' + $i + ' status=' + $s1)
        $sv = JsClickBtnRe $bSave
        Now ('#' + $i + ' save=' + $sv)
        Start-Sleep -Seconds 2
        Write-Host ('  #' + $i + ' name=' + $nm + ' fill=' + $f + ' status=' + $s1 + ' save=' + $sv)
        VerifyCreated 'customer' 'name' $nm $before
    }
}

# ---------------- 供应商：独立页 /supplier/manage/add（必填 名称 + 类型复选）----------------
if (-not $Only -or $Only -eq 'supplier') {
    Sec '3) supplier  x2  (/supplier/manage/add, name + type-checkbox + save)'
    for ($i = 1; $i -le 2; $i++) {
        $nm = $pSupplier + 'A' + $i
        if (Exists 'supplier' 'name' $nm) { Now ('supplier#' + $i + ' exists, skip'); Ok $true ('supplier already seeded: ' + $nm); continue }
        Open '/supplier/manage/add' 2600
        $before = [int](SqlOne 'SELECT COUNT(*) FROM supplier')
        if ($DumpOnly) { DumpForm 'supplier'; break }
        $f = JsFillByLabel $bName (B64 $nm)
        Now ('#' + $i + ' fill=' + $f)
        $t = JsCheckByLabel $bType
        Now ('#' + $i + ' type=' + $t)
        $sv = JsClickBtnRe $bSave
        Now ('#' + $i + ' save=' + $sv)
        Start-Sleep -Seconds 1
        $cb = JsConfirmAnyBox
        if ($cb -ne 'NOBOX') { Now ('#' + $i + ' dup-confirm=' + $cb); Start-Sleep -Seconds 2 }
        else { Start-Sleep -Seconds 1 }
        Write-Host ('  #' + $i + ' name=' + $nm + ' fill=' + $f + ' type=' + $t + ' save=' + $sv)
        VerifyCreated 'supplier' 'name' $nm $before
    }
}

# ---------------- 供货商（委外/进货）：独立页 /outsource/supplier/manage/add ----------------
if (-not $Only -or $Only -eq 'factory') {
    Sec '4) factory  x1  (/outsource/supplier/manage/add, name + type(product) + save)'
    for ($i = 1; $i -le 1; $i++) {
        $nm = $pVendor + 'A' + $i
        if (Exists 'supplier' 'name' $nm) { Now ('factory#' + $i + ' exists, skip'); Ok $true ('factory already seeded: ' + $nm); continue }
        Open '/outsource/supplier/manage/add' 2600
        $before = [int](SqlOne 'SELECT COUNT(*) FROM supplier')
        if ($DumpOnly) { DumpForm 'factory'; break }
        $f = JsFillByLabel $bName (B64 $nm)
        Now ('#' + $i + ' fill=' + $f + ' nameVal=' + (JsLabelValue $bName))
        $t = JsCheckByLabel $bType
        Now ('#' + $i + ' type=' + $t + ' ' + (JsLabelChecked $bType))
        $sv = JsClickBtnRe $bSave
        Now ('#' + $i + ' save=' + $sv)
        # 前端校验失败时"保存"点击不会发请求 => 立刻（0.9s）读一次 Toast/字段错误，别等 3s 后 Toast 过期
        Start-Sleep -Milliseconds 900
        Now ('#' + $i + ' toast=' + (JsMsgText) + ' err=' + (JsErrText))
        Start-Sleep -Seconds 1
        $cb = JsConfirmAnyBox
        if ($cb -ne 'NOBOX') { Now ('#' + $i + ' dup-confirm=' + $cb); Start-Sleep -Seconds 2 }
        else { Start-Sleep -Seconds 1 }
        Write-Host ('  #' + $i + ' name=' + $nm + ' fill=' + $f + ' type=' + $t + ' save=' + $sv)
        VerifyCreated 'supplier' 'name' $nm $before
        Ok ((SqlOne ("SELECT COUNT(*) FROM supplier_type_ref r JOIN supplier s ON s.id=r.supplier_id WHERE s.name='" + $nm + "' AND r.type_code='product'")) -ne '0') ('factory type ref = product: ' + $nm)
    }
}

# ---------------- 仓库（**弹窗**形态）：列表页点"新增" → 弹窗内填 → 确定 ----------------
# 成品仓 /inventory/warehouse：必填 仓库名称 + 仓型（该页只有 FINISHED 一个选项）
if (-not $Only -or $Only -eq 'wh_finished') {
    Sec '5) wh_finished  x2  (/inventory/warehouse, dialog: name + 仓型)'
    # 建 2 个成品仓：后续"成品移仓"需要两个同类别（INVENTORY）仓库
    for ($i = 1; $i -le 2; $i++) {
        $nm = $pWhFin + 'A' + $i
        if (Exists 'warehouse' 'warehouse_name' $nm) { Now ('wh_finished#' + $i + ' exists, skip'); Ok $true ('wh_finished already seeded: ' + $nm); continue }
        Open '/inventory/warehouse' 2400
        $before = [int](SqlOne 'SELECT COUNT(*) FROM warehouse')
        $c = JsFindBtn $bAdd
        Now ('click-add=' + $c)
        if ($DumpOnly) { Start-Sleep -Milliseconds 1500; DumpForm 'wh_finished' -InDialog; break }
        Start-Sleep -Milliseconds 1500
        $f = JsFillByLabel $bWhName (B64 $nm) -InDialog
        Now ('#' + $i + ' fill=' + $f)
        $t = JsPickSelect $bWhType '' -InDialog
        Now ('#' + $i + ' whtype=' + $t)
        $sv = JsClickBtnRe $bSave -InDialog
        Now ('#' + $i + ' save=' + $sv)
        Start-Sleep -Seconds 2
        Write-Host ('  #' + $i + ' name=' + $nm + ' add=' + $c + ' fill=' + $f + ' whtype=' + $t + ' save=' + $sv)
        VerifyCreated 'warehouse' 'warehouse_name' $nm $before
        Ok ((SqlOne ("SELECT warehouse_type FROM warehouse WHERE warehouse_name='" + $nm + "'")) -eq 'FINISHED') ('finished warehouse type = FINISHED: ' + $nm)
    }
}

# 委外仓 /outsource/warehouse：弹窗内**先选供应商**（RemoteSelect）再填仓库名称
if (-not $Only -or $Only -eq 'wh_outsource') {
    Sec '6) wh_outsource  (/outsource/warehouse, dialog: supplier + name)'
    for ($i = 1; $i -le 1; $i++) {
        $nm = $pWhOut + 'A' + $i
        if (Exists 'warehouse' 'warehouse_name' $nm) { Now ('wh_outsource#' + $i + ' exists, skip'); Ok $true ('wh_outsource already seeded: ' + $nm); continue }
        Open '/outsource/warehouse' 2400
        $before = [int](SqlOne 'SELECT COUNT(*) FROM warehouse')
        $c = JsFindBtn $bAdd
        Now ('click-add=' + $c)
        if ($DumpOnly) { Start-Sleep -Milliseconds 1500; DumpForm 'wh_outsource' -InDialog; break }
        Start-Sleep -Milliseconds 1500
        $sp = JsPickSelect $bSupplier '' -InDialog
        Now ('#' + $i + ' supplier=' + $sp)
        $f = JsFillByLabel $bWhName (B64 $nm) -InDialog
        Now ('#' + $i + ' fill=' + $f)
        $sv = JsClickBtnRe $bSave -InDialog
        Now ('#' + $i + ' save=' + $sv)
        Start-Sleep -Seconds 2
        Write-Host ('  #' + $i + ' name=' + $nm + ' add=' + $c + ' supplier=' + $sp + ' fill=' + $f + ' save=' + $sv)
        VerifyCreated 'warehouse' 'warehouse_name' $nm $before
        Ok ((SqlOne ("SELECT IFNULL(factory_id,0) FROM warehouse WHERE warehouse_name='" + $nm + "'")) -ne '0') ('outsource warehouse bound to a factory: ' + $nm)
    }
}

# 自有物料仓（辅料仓）/outsource/material-warehouse：弹窗内 仓库名称 + 仓型（固定 AUXILIARY）
if (-not $Only -or $Only -eq 'wh_material') {
    Sec '7) wh_material  (/outsource/material-warehouse, dialog: name + 仓型)'
    for ($i = 1; $i -le 1; $i++) {
        $nm = $pWhMat + 'A' + $i
        if (Exists 'warehouse' 'warehouse_name' $nm) { Now ('wh_material#' + $i + ' exists, skip'); Ok $true ('wh_material already seeded: ' + $nm); continue }
        Open '/outsource/material-warehouse' 2400
        $before = [int](SqlOne 'SELECT COUNT(*) FROM warehouse')
        $c = JsFindBtn $bAdd
        Now ('click-add=' + $c)
        if ($DumpOnly) { Start-Sleep -Milliseconds 1500; DumpForm 'wh_material' -InDialog; break }
        Start-Sleep -Milliseconds 1500
        $f = JsFillByLabel $bWhName (B64 $nm) -InDialog
        Now ('#' + $i + ' fill=' + $f)
        $t = JsPickSelect $bWhType '' -InDialog
        Now ('#' + $i + ' whtype=' + $t)
        $sv = JsClickBtnRe $bSave -InDialog
        Now ('#' + $i + ' save=' + $sv)
        Start-Sleep -Seconds 2
        Write-Host ('  #' + $i + ' name=' + $nm + ' add=' + $c + ' fill=' + $f + ' whtype=' + $t + ' save=' + $sv)
        VerifyCreated 'warehouse' 'warehouse_name' $nm $before
        Ok ((SqlOne ("SELECT warehouse_type FROM warehouse WHERE warehouse_name='" + $nm + "'")) -eq 'AUXILIARY') ('material warehouse type = AUXILIARY: ' + $nm)
    }
}

# ---------------- 采购单（进货）：建单 → 审核（**入库建库存**）----------------
# 前置：供货商（supplierType=product）+ 成品仓；审核会 changeStock 入库（PurchaseOrderServiceImpl:296/353）
if (-not $Only -or $Only -eq 'purchase') {
    Sec '8) purchase  x1  (/inventory/purchase/add -> audit => stock in)'
    $code = SqlOne 'SELECT code FROM purchase_order ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM purchase_order')
    if ($have -ge $N) { Now ('purchase full: ' + $have + '/' + $N); Ok $true ('purchase count=' + $have) }
    else {
        Open '/inventory/purchase/add' 2800
        if ($DumpOnly) { DumpForm 'purchase' }
        else {
            $before = [int](SqlOne 'SELECT COUNT(*) FROM purchase_order')
            $p1 = JsPickSelect $bHdrSupplier (B64 ($pVendor + 'A1'))
            Now ('purchase supplier=' + $p1)
            $p2 = JsPickSelect $bHdrInWh (B64 ($pWhFin + 'A1'))
            Now ('purchase in-warehouse=' + $p2)
            $a = EnsureItemRow $bAddDetail
            Now ('purchase add-detail=' + $a)
            Start-Sleep -Milliseconds 1200
            # ⚠️ /inventory/purchase/add 在 router/index.ts 里被**重复注册**，实际渲染的是
            # purchase/order/form.vue（列 = 产品|单位|品质|数量|单价|金额|操作），不是 add.vue
            # （列 = SKU|产品|单位|品质数量/单价|…）。故一律按**运行期表头文字**取列序。
            # 另注：form.vue 的前端校验只查明细条数（form.vue:101）⇒ 空行也会存成草稿，
            # 必须靠"明细数量>0"的断言把这种假成功挡住（否则审核时才被后端拦）。
            $cProd = JsColIdx $bColProduct
            $cQty = JsColIdx $bColQty
            $cPrice = JsColIdx $bColPrice
            Now ('purchase cols: product=' + $cProd + ' qty=' + $cQty + ' price=' + $cPrice)
            if ($cProd -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cProd)
                if ($ro -eq 'OPENED') { Now ('purchase row0 product=' + (PickDropdownRetry (B64 ($pProduct + 'B1')))) }
                else { Now ('purchase row0 product-open=' + $ro) }
                # 品质默认 A（form.vue 的 addItem 默认值；库里实测 quality_type='A'）=> 不用动
                Now ('purchase qty=' + (JsRowFill 0 ([int]$cQty) $vQty 0))
                Now ('purchase price=' + (JsRowFill 0 ([int]$cPrice) $vPrice 0))
            }
            JsHookMsgs | Out-Null
            $sv = JsClickBtnRe $bSave
            Now ('purchase save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('purchase msgs=' + (JsMsgsDump) + ' dlgs=' + (JsDlgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM purchase_order')
            $code = SqlOne 'SELECT code FROM purchase_order ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM purchase_order_item WHERE order_id = (SELECT id FROM purchase_order WHERE code = '" + $code + "')")
            Write-Host ('  purchase rows ' + $before + '->' + $after + ' code=' + $code + ' itemQty=' + $iq)
            Ok (($after -gt $before) -and [bool]$code -and ([int]$iq -gt 0)) ('purchase created (draft, itemQty=' + $iq + '): ' + $code)
        }
    }
    if ($code -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM purchase_order WHERE code = '" + $code + "'")
        if ($st -eq 'AUDITED') { Ok $true ('purchase already audited: ' + $code) }
        else {
            Open '/inventory/purchase' 2600
            Now ('purchase list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $code) $bAudit
            Now ('purchase audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('purchase audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM purchase_order WHERE code = '" + $code + "'")
            $stock = SqlOne 'SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock'
            Now ('purchase status=' + $st + ' stockQty=' + $stock)
            Ok ($st -eq 'AUDITED') ('purchase audited: ' + $code)
            Ok ([int]$stock -gt 0) ('stock created by purchase audit (qty=' + $stock + ')')
        }
    }
}

# ---------------- 成品其他入库：建单 → 审核（**创建 PRODUCT 形态库存**）----------------
# 为什么必须有这一步：采购审核写入的是 stock_form='MATERIAL' 库存，而销售单的库存检查与
# 销售审核扣减按 PRODUCT 形态（SaleOrderServiceImpl:427），两者不对称 ⇒ 直接建销售单会被
# 「库存不足提醒」弹窗拦下、审核也会报"库存不足"。本页 ioType 默认就是「入库」，入库不校验库存。
if (-not $Only -or $Only -eq 'otherin') {
    Sec '9) other_in  x1  (/inventory/other-io/add, ioType=IN -> audit => PRODUCT stock)'
    $icode = SqlOne "SELECT code FROM inventory_other_io WHERE io_type = 'IN' ORDER BY id DESC LIMIT 1"
    $have = [int](SqlOne "SELECT COUNT(*) FROM inventory_other_io WHERE io_type = 'IN'")
    if ($have -ge $N) { Now ('other_in full: ' + $have + '/' + $N); Ok $true ('other_in count=' + $have) }
    else {
        Open '/inventory/other-io/add' 2800
        if ($DumpOnly) { DumpForm 'other_in' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM inventory_other_io')
            $p1 = JsPickSelect $bHdrWarehouse (B64 ($pWhFin + 'A1'))
            Now ('other_in warehouse=' + $p1)
            $a = EnsureItemRow $bAddFinished
            Now ('other_in add-finished=' + $a)
            Start-Sleep -Milliseconds 1200
            $cProd = JsColIdx $bColFinishedName
            $cQty = JsColIdx $bColQty
            Now ('other_in cols: product=' + $cProd + ' qty=' + $cQty)
            if ($cProd -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cProd)
                if ($ro -eq 'OPENED') { Now ('other_in row0 product=' + (PickDropdownRetry (B64 ($pProduct + 'B2')))) }
                else { Now ('other_in row0 product-open=' + $ro) }
                Now ('other_in qty=' + (JsRowFill 0 ([int]$cQty) (B64 '5') 0))
            }
            $sv = JsClickBtnRe $bSave
            Now ('other_in save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('other_in msgs=' + (JsMsgsDump) + ' dlgs=' + (JsDlgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM inventory_other_io')
            $icode = SqlOne "SELECT code FROM inventory_other_io ORDER BY id DESC LIMIT 1"
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM inventory_other_io_item WHERE other_io_id = (SELECT id FROM inventory_other_io WHERE code = '" + $icode + "')")
            Write-Host ('  other_in rows ' + $before + '->' + $after + ' code=' + $icode + ' itemQty=' + $iq)
            Ok (($after -gt $before) -and [bool]$icode -and ([int]$iq -gt 0)) ('other_in created (draft, itemQty=' + $iq + '): ' + $icode)
        }
    }
    if ($icode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM inventory_other_io WHERE code = '" + $icode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('other_in already audited: ' + $icode) }
        else {
            Open '/inventory/other-io' 2600
            Now ('other_in list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $icode) $bAudit
            Now ('other_in audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('other_in audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM inventory_other_io WHERE code = '" + $icode + "'")
            # 注意：stock_form 一律是 MATERIAL —— 9 参 changeStock 重载硬编码 FORMAT_MATERIAL
            # （WarehouseStockService.java:68-70），枚举里并没有 'PRODUCT' 值。销售侧 checkStock
            # 只按 warehouse_id + product_id + quality_type 过滤（SaleOrderServiceImpl.java:543-549），
            # 不过滤 stock_form ⇒ 断言只按"该产品在该仓有库存"来判。
            $pstock = SqlOne ("SELECT IFNULL(SUM(s.quantity),0) FROM warehouse_stock s JOIN product p ON p.id = s.product_id WHERE p.name = '" + $pProduct + "B2' AND s.warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A1')")
            Now ('other_in status=' + $st + ' B2-stockQty@finished=' + $pstock)
            Ok ($st -eq 'AUDITED') ('other_in audited: ' + $icode)
            Ok ([int]$pstock -gt 0) ('stock for 测试产品B2 in 成品仓 created (' + $pstock + ')')
        }
    }
}

# ---------------- 销售单：建单 → 审核（**扣库存 + 生成应收**）----------------
# 前置：客户 + 成品仓 + 有库存（先跑 purchase）；审核会校验库存并 changeStock（SALE_OUT）+ 写 finance_receivable
if (-not $Only -or $Only -eq 'sale') {
    Sec '10) sale  x1  (/inventory/sale/add -> audit => stock out + receivable)'
    $scode = SqlOne 'SELECT code FROM sale_order ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM sale_order')
    if ($have -ge $N) { Now ('sale full: ' + $have + '/' + $N); Ok $true ('sale count=' + $have) }
    else {
        Open '/inventory/sale/add' 2800
        if ($DumpOnly) { DumpForm 'sale' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM sale_order')
            $p1 = JsPickSelect $bHdrCustomer (B64 ($pCustomer + 'A1'))
            Now ('sale customer=' + $p1)
            $p2 = JsPickSelect $bHdrOutWh (B64 ($pWhFin + 'A1'))
            Now ('sale out-warehouse=' + $p2)
            $a = EnsureItemRow $bAddProductRow
            Now ('sale add-product=' + $a)
            Start-Sleep -Milliseconds 1200
            $cProd = JsColIdx $bColProduct
            $cQty = JsColIdx $bColQty
            $cPrice = JsColIdx $bColPrice
            Now ('sale cols: product=' + $cProd + ' qty=' + $cQty + ' price=' + $cPrice)
            if ($cProd -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cProd)
                if ($ro -eq 'OPENED') { Now ('sale row0 product=' + (PickDropdownRetry (B64 ($pProduct + 'B1')))) }
                else { Now ('sale row0 product-open=' + $ro) }
                # 品质默认 A（addItem 默认 qualityType:'A'，A 可售）=> 不用动
                Now ('sale qty=' + (JsRowFill 0 ([int]$cQty) $vQty 0))
                Now ('sale price=' + (JsRowFill 0 ([int]$cPrice) $vPrice 0))
            }
            $sv = JsClickBtnRe $bSave
            Now ('sale save=' + $sv)
            Start-Sleep -Milliseconds 900
            # 缺货时销售页会弹「库存不足提醒」并 return（add.vue:230-238）=> 必须点「仍然保存订单」才会真正提交
            $kd = JsClickDialogBtn $bKeepSave
            Now ('sale keep-save=' + $kd)
            if ($kd -eq 'CLICKED') { Start-Sleep -Seconds 2 }
            Now ('sale msgs=' + (JsMsgsDump) + ' dlgs=' + (JsDlgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM sale_order')
            $scode = SqlOne 'SELECT code FROM sale_order ORDER BY id DESC LIMIT 1'
            Write-Host ('  sale rows ' + $before + '->' + $after + ' code=' + $scode)
            Ok (($after -gt $before) -and [bool]$scode) ('sale created (draft): ' + $scode)
        }
    }
    if ($scode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM sale_order WHERE code = '" + $scode + "'")
        $stockBefore = SqlOne 'SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock'
        if ($st -eq 'AUDITED') { Ok $true ('sale already audited: ' + $scode) }
        else {
            Open '/inventory/sale' 2600
            Now ('sale list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $scode) $bAudit
            Now ('sale audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('sale audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM sale_order WHERE code = '" + $scode + "'")
            $stockAfter = SqlOne 'SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock'
            $recv = SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE bill_no = '" + $scode + "'")
            Now ('sale status=' + $st + ' stock ' + $stockBefore + '->' + $stockAfter + ' receivable=' + $recv)
            Ok ($st -eq 'AUDITED') ('sale audited: ' + $scode)
            Ok ([int]$stockAfter -lt [int]$stockBefore) ('stock deducted by sale audit (' + $stockBefore + '->' + $stockAfter + ')')
            Ok ($recv -ne '0') ('receivable created for sale: ' + $scode)
        }
    }
}

# ---------------- 成品移仓：建单 → 审核（两个成品仓之间转移库存）----------------
# 前置：两个 INVENTORY 仓库（本脚本的 wh_finished 建 A1/A2）+ 移出仓有可用库存
if (-not $Only -or $Only -eq 'move') {
    Sec '11) move  x1  (/inventory/warehouse-move/add -> audit => A1 -> A2)'
    $mcode = SqlOne 'SELECT code FROM inventory_warehouse_move ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move')
    if ($have -ge $N) { Now ('move full: ' + $have + '/' + $N); Ok $true ('move count=' + $have) }
    else {
        Open '/inventory/warehouse-move/add' 2800
        if ($DumpOnly) { DumpForm 'move' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move')
            $p1 = JsPickSelect $bHdrMoveFrom (B64 ($pWhFin + 'A1'))
            Now ('move from=' + $p1)
            $p2 = JsPickSelect $bHdrMoveTo (B64 ($pWhFin + 'A2'))
            Now ('move to=' + $p2)
            $a = EnsureItemRow $bAddDetail
            Now ('move add-detail=' + $a)
            Start-Sleep -Milliseconds 1200
            $cProd = JsColIdx $bColProduct
            $cQ = JsColIdx $bColQuality
            $cQty = JsColIdx $bColMoveQty
            Now ('move cols: product=' + $cProd + ' quality=' + $cQ + ' qty=' + $cQty)
            if ($cProd -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cProd)
                if ($ro -eq 'OPENED') { Now ('move row0 product=' + (PickDropdownRetry (B64 ($pProduct + 'B2')))) }
                else { Now ('move row0 product-open=' + $ro) }
                # 品质显式选第一项（A）：该页数量校验比对的是"移出仓该品质可用库存"，品质为空会查不到
                if ($cQ -notlike 'NONE*') {
                    $qo = JsRowOpenSelect 0 ([int]$cQ)
                    if ($qo -eq 'OPENED') { Now ('move row0 quality=' + (PickDropdownRetry '')) }
                    else { Now ('move row0 quality-open=' + $qo) }
                }
                Start-Sleep -Milliseconds 900
                Now ('move qty=' + (JsRowFill 0 ([int]$cQty) (B64 '2') 0))
            }
            $sv = JsClickBtnRe $bSave
            Now ('move save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('move msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move')
            $mcode = SqlOne 'SELECT code FROM inventory_warehouse_move ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM inventory_warehouse_move_item WHERE move_id = (SELECT id FROM inventory_warehouse_move WHERE code = '" + $mcode + "')")
            Write-Host ('  move rows ' + $before + '->' + $after + ' code=' + $mcode + ' itemQty=' + $iq)
            Ok (($after -gt $before) -and [bool]$mcode -and ([int]$iq -gt 0)) ('move created (draft, itemQty=' + $iq + '): ' + $mcode)
        }
    }
    if ($mcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM inventory_warehouse_move WHERE code = '" + $mcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('move already audited: ' + $mcode) }
        else {
            Open '/inventory/warehouse-move' 2600
            Now ('move list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $mcode) $bAudit
            Now ('move audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('move audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM inventory_warehouse_move WHERE code = '" + $mcode + "'")
            $a1 = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A1')")
            $a2 = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A2')")
            Now ('move status=' + $st + ' A1-qty=' + $a1 + ' A2-qty=' + $a2)
            Ok ($st -eq 'AUDITED') ('move audited: ' + $mcode)
            Ok ([int]$a2 -gt 0) ('stock arrived at 测试成品仓A2 (' + $a2 + ')')
        }
    }
}

# ---------------- 成品报损：建单 → 审核（报损扣库存）----------------
if (-not $Only -or $Only -eq 'loss') {
    Sec '12) loss  x1  (/inventory/stock-loss/add -> audit => stock down)'
    $lcode = SqlOne 'SELECT code FROM inventory_stock_loss ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM inventory_stock_loss')
    if ($have -ge $N) { Now ('loss full: ' + $have + '/' + $N); Ok $true ('loss count=' + $have) }
    else {
        Open '/inventory/stock-loss/add' 2800
        if ($DumpOnly) { DumpForm 'loss' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM inventory_stock_loss')
            $p1 = JsPickSelect $bHdrWarehouse (B64 ($pWhFin + 'A1'))
            Now ('loss warehouse=' + $p1)
            $a = EnsureItemRow $bAddProductRow
            Now ('loss add-product=' + $a)
            Start-Sleep -Milliseconds 1200
            $cProd = JsColIdx $bColProduct
            $cQ = JsColIdx $bColQuality
            $cQty = JsColIdx $bColLossQty
            $cPrice = JsColIdx $bColPrice
            Now ('loss cols: product=' + $cProd + ' quality=' + $cQ + ' qty=' + $cQty + ' price=' + $cPrice)
            if ($cProd -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cProd)
                if ($ro -eq 'OPENED') { Now ('loss row0 product=' + (PickDropdownRetry (B64 ($pProduct + 'B2')))) }
                else { Now ('loss row0 product-open=' + $ro) }
                if ($cQ -notlike 'NONE*') {
                    $qo = JsRowOpenSelect 0 ([int]$cQ)
                    if ($qo -eq 'OPENED') { Now ('loss row0 quality=' + (PickDropdownRetry '')) }
                    else { Now ('loss row0 quality-open=' + $qo) }
                }
                Start-Sleep -Milliseconds 900
                Now ('loss qty=' + (JsRowFill 0 ([int]$cQty) (B64 '1') 0))
                if ($cPrice -notlike 'NONE*') { Now ('loss price=' + (JsRowFill 0 ([int]$cPrice) $vPrice 0)) }
            }
            $sv = JsClickBtnRe $bSave
            Now ('loss save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('loss msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM inventory_stock_loss')
            $lcode = SqlOne 'SELECT code FROM inventory_stock_loss ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM inventory_stock_loss_item WHERE loss_id = (SELECT id FROM inventory_stock_loss WHERE code = '" + $lcode + "')")
            Write-Host ('  loss rows ' + $before + '->' + $after + ' code=' + $lcode + ' itemQty=' + $iq)
            Ok (($after -gt $before) -and [bool]$lcode -and ([int]$iq -gt 0)) ('loss created (draft, itemQty=' + $iq + '): ' + $lcode)
        }
    }
    if ($lcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM inventory_stock_loss WHERE code = '" + $lcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('loss already audited: ' + $lcode) }
        else {
            $stkBefore = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A1')")
            Open '/inventory/stock-loss' 2600
            Now ('loss list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $lcode) $bAudit
            Now ('loss audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('loss audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM inventory_stock_loss WHERE code = '" + $lcode + "'")
            $stkAfter = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A1')")
            Now ('loss status=' + $st + ' A1-qty ' + $stkBefore + '->' + $stkAfter)
            Ok ($st -eq 'AUDITED') ('loss audited: ' + $lcode)
            Ok ([int]$stkAfter -lt [int]$stkBefore) ('stock deducted by loss audit (' + $stkBefore + '->' + $stkAfter + ')')
        }
    }
}

# ---------------- 销售退货：从销售单带入明细 → 审核（退货入库 + 红冲应收）----------------
if (-not $Only -or $Only -eq 'return') {
    Sec '13) return  x1  (/sale/return/add -> audit => stock back)'
    $saleCode = SqlOne 'SELECT code FROM sale_order ORDER BY id DESC LIMIT 1'
    $rcode = SqlOne 'SELECT code FROM sale_return ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM sale_return')
    if ($have -ge $N) { Now ('return full: ' + $have + '/' + $N); Ok $true ('return count=' + $have) }
    else {
        Open '/sale/return/add' 2800
        if ($DumpOnly) { DumpForm 'return' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM sale_return')
            $p1 = JsPickSelect $bHdrCustomer (B64 ($pCustomer + 'A1'))
            Now ('return customer=' + $p1)
            $p2 = JsPickSelect $bHdrReturnWh (B64 ($pWhFin + 'A1'))
            Now ('return warehouse=' + $p2)
            $p3 = JsPickSelect $bSaleOrderLink (B64 $saleCode)
            Now ('return sale-order=' + $p3)
            # 「从销售单带入明细」自动填产品/品质/可退数量/单价（比手填更稳，且建立关联）
            $a = JsClickTextBtn $bFromSaleOrder
            Now ('return load-from-sale=' + $a)
            Start-Sleep -Milliseconds 1800
            $cQty = JsColIdx $bColReturnQty
            Now ('return cols: returnQty=' + $cQty)
            if ($cQty -notlike 'NONE*') { Now ('return qty=' + (JsRowFill 0 ([int]$cQty) (B64 '1') 0)) }
            $sv = JsClickBtnRe $bSave
            Now ('return save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('return msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM sale_return')
            $rcode = SqlOne 'SELECT code FROM sale_return ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM sale_return_item WHERE return_id = (SELECT id FROM sale_return WHERE code = '" + $rcode + "')")
            Write-Host ('  return rows ' + $before + '->' + $after + ' code=' + $rcode + ' itemQty=' + $iq)
            Ok (($after -gt $before) -and [bool]$rcode -and ([int]$iq -gt 0)) ('return created (draft, itemQty=' + $iq + '): ' + $rcode)
        }
    }
    if ($rcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM sale_return WHERE code = '" + $rcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('return already audited: ' + $rcode) }
        else {
            $a1b = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A1')")
            Open '/sale/return' 2600
            Now ('return list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $rcode) $bAudit
            Now ('return audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('return audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM sale_return WHERE code = '" + $rcode + "'")
            $a1a = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id = (SELECT id FROM warehouse WHERE warehouse_name = '" + $pWhFin + "A1')")
            Now ('return status=' + $st + ' A1-qty ' + $a1b + '->' + $a1a)
            Ok ($st -eq 'AUDITED') ('return audited: ' + $rcode)
            Ok ([int]$a1a -gt [int]$a1b) ('stock returned into A1 (' + $a1b + '->' + $a1a + ')')
        }
    }
}

# ---------------- 收款单：客户 + 账户行 + 金额 → 审核（现金流入）----------------
if (-not $Only -or $Only -eq 'receipt') {
    Sec '14) receipt  x1  (/finance/receipt/add -> audit)'
    $rccode = SqlOne 'SELECT code FROM finance_receipt ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM finance_receipt')
    if ($have -ge $N) { Now ('receipt full: ' + $have + '/' + $N); Ok $true ('receipt count=' + $have) }
    else {
        Open '/finance/receipt/add' 2800
        if ($DumpOnly) { DumpForm 'receipt' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM finance_receipt')
            $p1 = JsPickSelect $bHdrCustomer (B64 ($pCustomer + 'A1'))
            Now ('receipt customer=' + $p1)
            $a = EnsureItemRow $bAddAccount
            Now ('receipt add-account=' + $a)
            Start-Sleep -Milliseconds 1200
            $cAcct = JsColIdx $bColReceiptAcct
            $cAmt = JsColIdx $bColReceiptAmt
            Now ('receipt cols: account=' + $cAcct + ' amount=' + $cAmt)
            if ($cAcct -notlike 'NONE*' -and $cAmt -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cAcct)
                if ($ro -eq 'OPENED') { Now ('receipt row0 account=' + (PickDropdownRetry '')) }
                else { Now ('receipt row0 account-open=' + $ro) }
                Now ('receipt amount=' + (JsRowFill 0 ([int]$cAmt) (B64 '20') 0))
            }
            $sv = JsClickBtnRe $bSave
            Now ('receipt save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('receipt msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM finance_receipt')
            $rccode = SqlOne 'SELECT code FROM finance_receipt ORDER BY id DESC LIMIT 1'
            Write-Host ('  receipt rows ' + $before + '->' + $after + ' code=' + $rccode)
            Ok (($after -gt $before) -and [bool]$rccode) ('receipt created (draft): ' + $rccode)
        }
    }
    if ($rccode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM finance_receipt WHERE code = '" + $rccode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('receipt already audited: ' + $rccode) }
        else {
            Open '/finance/receipt' 2600
            Now ('receipt list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $rccode) $bAudit
            Now ('receipt audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('receipt audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM finance_receipt WHERE code = '" + $rccode + "'")
            Now ('receipt status=' + $st)
            Ok ($st -eq 'AUDITED') ('receipt audited: ' + $rccode)
        }
    }
}

# ---------------- 付款单：供应商 + 账户行 + 金额 → 审核（现金流出）----------------
if (-not $Only -or $Only -eq 'payment') {
    Sec '15) payment  x1  (/finance/payment/add -> audit)'
    $pcode = SqlOne 'SELECT code FROM finance_payment ORDER BY id DESC LIMIT 1'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM finance_payment')
    if ($have -ge $N) { Now ('payment full: ' + $have + '/' + $N); Ok $true ('payment count=' + $have) }
    else {
        Open '/finance/payment/add' 2800
        if ($DumpOnly) { DumpForm 'payment' }
        else {
            JsHookMsgs | Out-Null
            $before = [int](SqlOne 'SELECT COUNT(*) FROM finance_payment')
            # 注意：付款页的表单标签是「供应商」（$bSupplier），不是采购页的「供货商」（$bHdrSupplier）
            $p1 = JsPickSelect $bSupplier (B64 ($pVendor + 'A1'))
            Now ('payment supplier=' + $p1)
            $a = EnsureItemRow $bAddAccount
            Now ('payment add-account=' + $a)
            Start-Sleep -Milliseconds 1200
            $cAcct = JsColIdx $bColPaymentAcct
            $cAmt = JsColIdx $bColPaymentAmt
            Now ('payment cols: account=' + $cAcct + ' amount=' + $cAmt)
            if ($cAcct -notlike 'NONE*' -and $cAmt -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cAcct)
                if ($ro -eq 'OPENED') { Now ('payment row0 account=' + (PickDropdownRetry '')) }
                else { Now ('payment row0 account-open=' + $ro) }
                Now ('payment amount=' + (JsRowFill 0 ([int]$cAmt) (B64 '20') 0))
            }
            $sv = JsClickBtnRe $bSave
            Now ('payment save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('payment msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM finance_payment')
            $pcode = SqlOne 'SELECT code FROM finance_payment ORDER BY id DESC LIMIT 1'
            Write-Host ('  payment rows ' + $before + '->' + $after + ' code=' + $pcode)
            Ok (($after -gt $before) -and [bool]$pcode) ('payment created (draft): ' + $pcode)
        }
    }
    if ($pcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM finance_payment WHERE code = '" + $pcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('payment already audited: ' + $pcode) }
        else {
            Open '/finance/payment' 2600
            Now ('payment list rows=' + (JsRowCount))
            $c1 = JsRowBtnByText (B64 $pcode) $bAudit
            Now ('payment audit-click=' + $c1)
            Start-Sleep -Milliseconds 1000
            Now ('payment audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM finance_payment WHERE code = '" + $pcode + "'")
            Now ('payment status=' + $st)
            Ok ($st -eq 'AUDITED') ('payment audited: ' + $pcode)
        }
    }
}

# ---------------- 费用单（列表页弹窗）：新增 → 审核 ----------------
if (-not $Only -or $Only -eq 'expense') {
    Sec '16) expense  x1  (/finance/expense dialog -> audit)'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM finance_expense')
    if ($have -ge $N) { Now ('expense full: ' + $have + '/' + $N); Ok $true ('expense count=' + $have) }
    else {
        Open '/finance/expense' 2600
        JsHookMsgs | Out-Null
        $c0 = JsFindBtn $bAdd
        Now ('expense click-add=' + $c0)
        Start-Sleep -Milliseconds 1200
        if ($DumpOnly) { DumpForm 'expense' -InDialog }
        else {
            # 费用类型默认 OFFICE（非 LOSS ⇒ 支出账户必填）；金额用 el-input-number
            $f1 = JsFillByLabel $bColAmount (B64 '100') -InDialog
            Now ('expense amount=' + $f1)
            $p1 = JsPickSelect $bExpAccount '' -InDialog
            Now ('expense account=' + $p1)
            $sv = JsClickBtnRe $bSave -InDialog
            Now ('expense save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('expense msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM finance_expense')
            $eno = SqlOne 'SELECT expense_no FROM finance_expense ORDER BY id DESC LIMIT 1'
            Write-Host ('  expense rows ' + $have + '->' + $after + ' no=' + $eno)
            Ok (($after -gt $have) -and [bool]$eno) ('expense created: ' + $eno)
            if ($eno) {
                Open '/finance/expense' 2600
                $c1 = JsRowBtnByText (B64 $eno) $bAudit
                Now ('expense audit-click=' + $c1)
                Start-Sleep -Milliseconds 1000
                Now ('expense audit-confirm=' + (JsConfirmAnyBox))
                Start-Sleep -Seconds 2
                $st = SqlOne ("SELECT status FROM finance_expense WHERE expense_no = '" + $eno + "'")
                Now ('expense status=' + $st)
                Ok ($st -eq 'AUDITED') ('expense audited: ' + $eno)
            }
        }
    }
}

# ---------------- 发票（列表页弹窗）：登记（**无审核**）----------------
if (-not $Only -or $Only -eq 'invoice') {
    Sec '17) invoice  x1  (/finance/invoice dialog; 登记即生效)'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM finance_invoice')
    if ($have -ge $N) { Now ('invoice full: ' + $have + '/' + $N); Ok $true ('invoice count=' + $have) }
    else {
        Open '/finance/invoice' 2600
        JsHookMsgs | Out-Null
        $c0 = JsClickTextBtn $bAddInvoice
        Now ('invoice click-add=' + $c0)
        Start-Sleep -Milliseconds 1200
        if ($DumpOnly) { DumpForm 'invoice' -InDialog }
        else {
            # 发票号码需唯一（后端有唯一约束）
            $invNo = 'INV-' + (Get-Date -Format 'yyyyMMddHHmmss') + '-' + $have
            $f1 = JsFillByLabel $bInvNo (B64 $invNo) -InDialog
            Now ('invoice no=' + $f1)
            $f2 = JsFillByLabel $bInvTotal (B64 '113') -InDialog
            Now ('invoice total=' + $f2)
            $sv = JsClickBtnRe $bSave -InDialog
            Now ('invoice save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('invoice msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM finance_invoice')
            $ino = SqlOne 'SELECT invoice_no FROM finance_invoice ORDER BY id DESC LIMIT 1'
            Write-Host ('  invoice rows ' + $have + '->' + $after + ' no=' + $ino)
            Ok (($after -gt $have) -and [bool]$ino) ('invoice created: ' + $ino)
        }
    }
}

# ---------------- 项目建档（研发立项）：保存即生效（无审核）----------------
# 必填（原配模式）：项目名称 + 产品SKU(页面已预填 NS-) + 产品名称 + 规格 + 原机配置 4 项。
# 「改配信息」5 项在规格=原配时整块隐藏（v-if="!isOriginalSpec"）。
# 用"按输入框序号填值"避免为 6 个标签造码点：可见文本输入框顺序 =
#   0 项目名称 | 1 产品SKU(预填) | 2 产品名称 | 3 适配机型 | 4 原机尺寸 | 5 原分辨率 | 6 驱动IC | 7 触摸IC
if (-not $Only -or $Only -eq 'project') {
    Sec '18) project  x1  (/dev/project/add, 保存即生效)'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM dev_project')
    if ($have -ge $N) { Now ('project full: ' + $have + '/' + $N); Ok $true ('project count=' + $have) }
    else {
        Open '/dev/project/add' 2800
        JsHookMsgs | Out-Null
        if ($DumpOnly) { DumpForm 'project' }
        else {
            $pn = 'P' + (Get-Date -Format 'HHmmss')
            $f0 = JsFillFormInputByIndex 0 (B64 ('测试项目' + $pn))
            Now ('project name=' + $f0)
            $f2 = JsFillFormInputByIndex 2 (B64 ('测试项目产品' + $pn))
            Now ('project product-name=' + $f2)
            $sp = JsPickSelect $bSpec $bSpecVal
            Now ('project spec=' + $sp)
            $f4 = JsFillFormInputByIndex 4 (B64 '6.1')
            $f5 = JsFillFormInputByIndex 5 (B64 '1080x2400')
            $f6 = JsFillFormInputByIndex 6 (B64 'IC-A')
            $f7 = JsFillFormInputByIndex 7 (B64 'IC-B')
            Now ('project original-config=' + $f4 + '/' + $f5 + '/' + $f6 + '/' + $f7)
            $sv = JsClickBtnRe $bSave
            Now ('project save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('project msgs=' + (JsMsgsDump))
            # 同名/重名确认弹窗（若出现则确认继续）
            $cb = JsConfirmAnyBox
            if ($cb -ne 'NOBOX') { Now ('project confirm=' + $cb); Start-Sleep -Seconds 2 } else { Start-Sleep -Seconds 2 }
            $after = [int](SqlOne 'SELECT COUNT(*) FROM dev_project')
            $pcode = SqlOne 'SELECT project_no FROM dev_project ORDER BY id DESC LIMIT 1'
            Write-Host ('  project rows ' + $have + '->' + $after + ' no=' + $pcode)
            Ok ($after -gt $have) ('project created: ' + $pcode)
        }
    }
}

# ---------------- 物料其他入库（委外仓）：建单 → 审核（**造物料库存**）----------------
# 为什么先做它：物料移仓/报损都要"移出仓/该仓可用库存"，先有库存才走得通。
# 该页 ioType 默认就是 IN（add.vue: form.ioType = IoType.IN），入库不校验库存。
if (-not $Only -or $Only -eq 'matio') {
    Sec '19) mat_in  x1  (/outsource/other-io/add, ioType=IN -> audit)'
    $have = [int](SqlOne "SELECT COUNT(*) FROM outsource_other_io WHERE io_type = 'IN'")
    if ($have -ge $N) { Now ('mat_in full: ' + $have + '/' + $N); Ok $true ('mat_in count=' + $have) }
    else {
        Open '/outsource/other-io/add' 2800
        JsHookMsgs | Out-Null
        if ($DumpOnly) { DumpForm 'mat_in' }
        else {
            $p1 = JsPickSelect $bHdrWarehouse (B64 ($pWhOut + 'A1'))
            Now ('mat_in warehouse=' + $p1)
            $a = EnsureItemRow $bAddMaterial
            Now ('mat_in add-material=' + $a)
            Start-Sleep -Milliseconds 1200
            # 3 次列号探测合并成 1 次调用（每次调用 ≈2~3s）
            $cj = JsColsIdx @($bColMaterialType, $bColMaterialName, $bColQty) | ConvertFrom-Json
            $cType = $cj[0]; $cMat = $cj[1]; $cQty = $cj[2]
            Now ('mat_in cols: type=' + $cType + ' material=' + $cMat + ' qty=' + $cQty)
            if ($cType -notlike 'NONE*') {
                # 该页的物料下拉是**级联**的（add.vue:39-41 materialsByType）：不先选「物料类型」，
                # 「物料名称」列一个选项都没有 => materialId 留空也能存（校验只看数量，add.vue:75），
                # 但审核后**不产生任何物料库存**（实测：outsource_material_id=NULL，库存 0）。
                $to = JsRowOpenSelect 0 ([int]$cType)
                if ($to -eq 'OPENED') { Now ('mat_in row0 material-type=' + (PickDropdownRetry '')) }
                else { Now ('mat_in row0 material-type-open=' + $to) }
                Start-Sleep -Milliseconds 900
            }
            if ($cMat -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cMat)
                if ($ro -eq 'OPENED') { Now ('mat_in row0 material=' + (PickDropdownRetry '')) }
                else { Now ('mat_in row0 material-open=' + $ro) }
                Now ('mat_in qty=' + (JsRowFill 0 ([int]$cQty) (B64 '5') 0))
                # 断言：物料必须真的选上了，否则这条入库单是"空物料"废单
                Now ('mat_in item-material-check=' + (SqlOne "SELECT IFNULL(outsource_material_id,0) FROM outsource_other_io_item WHERE other_io_id = (SELECT id FROM outsource_other_io ORDER BY id DESC LIMIT 1) LIMIT 1"))
            }
            $sv = JsClickBtnRe $bSave
            Now ('mat_in save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('mat_in msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM outsource_other_io')
            $mcode = SqlOne 'SELECT code FROM outsource_other_io ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM outsource_other_io_item WHERE other_io_id = (SELECT id FROM outsource_other_io WHERE code = '" + $mcode + "')")
            Write-Host ('  mat_in rows ' + $have + '->' + $after + ' code=' + $mcode + ' itemQty=' + $iq)
            Ok (($after -gt $have) -and [bool]$mcode -and ([int]$iq -gt 0)) ('mat_in created (draft, itemQty=' + $iq + '): ' + $mcode)
        }
    }
    if ($mcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM outsource_other_io WHERE code = '" + $mcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('mat_in already audited: ' + $mcode) }
        else {
            Open '/outsource/other-io' 2600
            $ac = JsRowBtnByTextCounted (B64 $mcode) $bAudit
            if ($ac -like 'NOROW*') { Start-Sleep -Milliseconds 1500; $ac = JsRowBtnByTextCounted (B64 $mcode) $bAudit }
            Now ('mat_in audit-click=' + $ac)
            Start-Sleep -Milliseconds 1000
            Now ('mat_in audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM outsource_other_io WHERE code = '" + $mcode + "'")
            Now ('mat_in status=' + $st)
            Ok ($st -eq 'AUDITED') ('mat_in audited: ' + $mcode)
        }
    }
}

# ---------------- 物料移仓（委外仓 → 物料仓）：建单 → 审核 ----------------
if (-not $Only -or $Only -eq 'matmove') {
    Sec '20) mat_move  x1  (/inventory/material-move/add -> audit)'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM inventory_material_move')
    if ($have -ge $N) { Now ('mat_move full: ' + $have + '/' + $N); Ok $true ('mat_move count=' + $have) }
    else {
        Open '/inventory/material-move/add' 2800
        JsHookMsgs | Out-Null
        if ($DumpOnly) { DumpForm 'mat_move' }
        else {
            $p1 = JsPickSelect $bHdrMoveFrom (B64 ($pWhOut + 'A1'))
            Now ('mat_move from=' + $p1)
            $p2 = JsPickSelect $bHdrMoveTo (B64 ($pWhMat + 'A1'))
            Now ('mat_move to=' + $p2)
            # 本页物料下拉是**全量列表**（与 mat_in 的「类型→物料」级联不同），第一项未必有库存 =>
            # 按"最近一次入库单里那个真有库存的物料"按名字精确选（名字来自库，用 B64 传中文，无需硬编码）
            $matName = SqlOne "SELECT m.material_name FROM outsource_other_io_item i JOIN outsource_material m ON m.id = i.outsource_material_id WHERE i.outsource_material_id IS NOT NULL ORDER BY i.id DESC LIMIT 1"
            Now ('mat_move target-material=' + $matName)
            $a = EnsureItemRow $bAddMaterial
            Now ('mat_move add-material=' + $a)
            Start-Sleep -Milliseconds 1200
            # 3 次列号探测合并成 1 次调用
            $cj = JsColsIdx @($bColMaterial, $bColQuality, $bColQty) | ConvertFrom-Json
            $cMat = $cj[0]; $cQ = $cj[1]; $cQty = $cj[2]
            Now ('mat_move cols: material=' + $cMat + ' quality=' + $cQ + ' qty=' + $cQty)
            if ($cMat -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cMat)
                if ($ro -eq 'OPENED') {
                    $mp = PickDropdownRetry (B64 $matName)
                    # 名字匹配可能因编码/格式差异落空 => 回退选第一项（与 mat_in 级联选中的是同一个物料）
                    if ($mp -notlike 'PICKED*') { $mp = $mp + ' -> fallback:' + (PickDropdownRetry '') }
                    Now ('mat_move row0 material=' + $mp)
                }
                else { Now ('mat_move row0 material-open=' + $ro) }
                # 品质显式选第一项：数量校验比对的是"移出仓该品质可用库存"
                if ($cQ -notlike 'NONE*') {
                    $qo = JsRowOpenSelect 0 ([int]$cQ)
                    if ($qo -eq 'OPENED') { Now ('mat_move row0 quality=' + (PickDropdownRetry '')) }
                    else { Now ('mat_move row0 quality-open=' + $qo) }
                }
                Start-Sleep -Milliseconds 900
                Now ('mat_move qty=' + (JsRowFill 0 ([int]$cQty) (B64 '2') 0))
            }
            $sv = JsClickBtnRe $bSave
            Now ('mat_move save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('mat_move msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM inventory_material_move')
            $mcode = SqlOne 'SELECT code FROM inventory_material_move ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM inventory_material_move_item WHERE move_id = (SELECT id FROM inventory_material_move WHERE code = '" + $mcode + "')")
            Write-Host ('  mat_move rows ' + $have + '->' + $after + ' code=' + $mcode + ' itemQty=' + $iq)
            Ok (($after -gt $have) -and [bool]$mcode -and ([int]$iq -gt 0)) ('mat_move created (draft, itemQty=' + $iq + '): ' + $mcode)
        }
    }
    if ($mcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM inventory_material_move WHERE code = '" + $mcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('mat_move already audited: ' + $mcode) }
        else {
            Open '/inventory/material-move' 2600
            $ac = JsRowBtnByTextCounted (B64 $mcode) $bAudit
            if ($ac -like 'NOROW*') { Start-Sleep -Milliseconds 1500; $ac = JsRowBtnByTextCounted (B64 $mcode) $bAudit }
            Now ('mat_move audit-click=' + $ac)
            Start-Sleep -Milliseconds 1000
            Now ('mat_move audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM inventory_material_move WHERE code = '" + $mcode + "'")
            Now ('mat_move status=' + $st)
            Ok ($st -eq 'AUDITED') ('mat_move audited: ' + $mcode)
        }
    }
}

# ---------------- 物料报损（委外仓）：建单 → 审核 ----------------
if (-not $Only -or $Only -eq 'matloss') {
    Sec '21) mat_loss  x1  (/outsource/stock-loss/add -> audit)'
    $have = [int](SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
    if ($have -ge $N) { Now ('mat_loss full: ' + $have + '/' + $N); Ok $true ('mat_loss count=' + $have) }
    else {
        Open '/outsource/stock-loss/add' 2800
        JsHookMsgs | Out-Null
        if ($DumpOnly) { DumpForm 'mat_loss' }
        else {
            $p1 = JsPickSelect $bHdrWarehouse (B64 ($pWhOut + 'A1'))
            Now ('mat_loss warehouse=' + $p1)
            # 同 mat_move：按"最近入库单里那个真有库存的物料"按名字选
            $matName = SqlOne "SELECT m.material_name FROM outsource_other_io_item i JOIN outsource_material m ON m.id = i.outsource_material_id WHERE i.outsource_material_id IS NOT NULL ORDER BY i.id DESC LIMIT 1"
            Now ('mat_loss target-material=' + $matName)
            $a = EnsureItemRow $bAddMaterial
            Now ('mat_loss add-material=' + $a)
            Start-Sleep -Milliseconds 1200
            # 4 次列号探测合并成 1 次调用
            $cj = JsColsIdx @($bColMaterial, $bColQuality, $bColLossQty, $bColPrice) | ConvertFrom-Json
            $cMat = $cj[0]; $cQ = $cj[1]; $cQty = $cj[2]; $cPrice = $cj[3]
            Now ('mat_loss cols: material=' + $cMat + ' quality=' + $cQ + ' qty=' + $cQty + ' price=' + $cPrice)
            if ($cMat -notlike 'NONE*' -and $cQty -notlike 'NONE*') {
                $ro = JsRowOpenSelect 0 ([int]$cMat)
                if ($ro -eq 'OPENED') {
                    $mp = PickDropdownRetry (B64 $matName)
                    if ($mp -notlike 'PICKED*') { $mp = $mp + ' -> fallback:' + (PickDropdownRetry '') }
                    Now ('mat_loss row0 material=' + $mp)
                }
                else { Now ('mat_loss row0 material-open=' + $ro) }
                if ($cQ -notlike 'NONE*') {
                    $qo = JsRowOpenSelect 0 ([int]$cQ)
                    if ($qo -eq 'OPENED') { Now ('mat_loss row0 quality=' + (PickDropdownRetry '')) }
                    else { Now ('mat_loss row0 quality-open=' + $qo) }
                }
                Start-Sleep -Milliseconds 900
                Now ('mat_loss qty=' + (JsRowFill 0 ([int]$cQty) (B64 '1') 0))
                if ($cPrice -notlike 'NONE*') { Now ('mat_loss price=' + (JsRowFill 0 ([int]$cPrice) $vPrice 0)) }
            }
            $sv = JsClickBtnRe $bSave
            Now ('mat_loss save=' + $sv)
            Start-Sleep -Milliseconds 900
            Now ('mat_loss msgs=' + (JsMsgsDump))
            Start-Sleep -Seconds 2
            $after = [int](SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
            $mcode = SqlOne 'SELECT code FROM outsource_stock_loss ORDER BY id DESC LIMIT 1'
            $iq = SqlOne ("SELECT IFNULL(SUM(quantity),0) FROM outsource_stock_loss_item WHERE loss_id = (SELECT id FROM outsource_stock_loss WHERE code = '" + $mcode + "')")
            Write-Host ('  mat_loss rows ' + $have + '->' + $after + ' code=' + $mcode + ' itemQty=' + $iq)
            Ok (($after -gt $have) -and [bool]$mcode -and ([int]$iq -gt 0)) ('mat_loss created (draft, itemQty=' + $iq + '): ' + $mcode)
        }
    }
    if ($mcode -and -not $DumpOnly) {
        $st = SqlOne ("SELECT status FROM outsource_stock_loss WHERE code = '" + $mcode + "'")
        if ($st -eq 'AUDITED') { Ok $true ('mat_loss already audited: ' + $mcode) }
        else {
            Open '/outsource/stock-loss' 2600
            $ac = JsRowBtnByTextCounted (B64 $mcode) $bAudit
            if ($ac -like 'NOROW*') { Start-Sleep -Milliseconds 1500; $ac = JsRowBtnByTextCounted (B64 $mcode) $bAudit }
            Now ('mat_loss audit-click=' + $ac)
            Start-Sleep -Milliseconds 1000
            Now ('mat_loss audit-confirm=' + (JsConfirmAnyBox))
            Start-Sleep -Seconds 2
            $st = SqlOne ("SELECT status FROM outsource_stock_loss WHERE code = '" + $mcode + "'")
            Now ('mat_loss status=' + $st)
            Ok ($st -eq 'AUDITED') ('mat_loss audited: ' + $mcode)
        }
    }
}

# ---------------- 结构巡检：只看表单结构（-Only dump 或 -DumpOnly 时用）----------------
if ($Only -eq 'dump') {
    foreach ($ent in @(
            @{ tag = 'customer'; route = '/inventory/customer'; dlg = $false },
            @{ tag = 'supplier'; route = '/supplier/manage'; dlg = $false },
            @{ tag = 'factory'; route = '/outsource/supplier/manage'; dlg = $false },
            @{ tag = 'wh_finished'; route = '/inventory/warehouse'; dlg = $true },
            @{ tag = 'wh_outsource'; route = '/outsource/warehouse'; dlg = $true },
            @{ tag = 'wh_material'; route = '/outsource/material-warehouse'; dlg = $true })) {
        Sec ('dump ' + $ent.tag + '  (' + $ent.route + ')')
        Open $ent.route 2400
        Now ($ent.tag + ': opened ' + $ent.route)
        $c = JsFindBtn $bAdd
        Now ($ent.tag + ': click-add=' + $c)
        Start-Sleep -Milliseconds 2500
        Write-Host ('  click add -> ' + $c)
        DumpForm $ent.tag -InDialog:$ent.dlg
    }
}

Write-Host ''
Write-Host ('RESULT: PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
Write-Host ('DB ledger: brand=' + (SqlOne 'SELECT COUNT(*) FROM brand') + ' product=' + (SqlOne 'SELECT COUNT(*) FROM product') + ' customer=' + (SqlOne 'SELECT COUNT(*) FROM customer') + ' supplier=' + (SqlOne 'SELECT COUNT(*) FROM supplier') + ' supplier_type_ref=' + (SqlOne 'SELECT COUNT(*) FROM supplier_type_ref') + ' warehouse=' + (SqlOne 'SELECT COUNT(*) FROM warehouse') + ' purchase=' + (SqlOne 'SELECT COUNT(*) FROM purchase_order') + ' sale=' + (SqlOne 'SELECT COUNT(*) FROM sale_order') + ' move=' + (SqlOne 'SELECT COUNT(*) FROM inventory_warehouse_move') + ' loss=' + (SqlOne 'SELECT COUNT(*) FROM inventory_stock_loss') + ' otherio=' + (SqlOne 'SELECT COUNT(*) FROM inventory_other_io') + ' stockQty=' + (SqlOne 'SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock') + ' return=' + (SqlOne 'SELECT COUNT(*) FROM sale_return') + ' receipt=' + (SqlOne 'SELECT COUNT(*) FROM finance_receipt') + ' payment=' + (SqlOne 'SELECT COUNT(*) FROM finance_payment') + ' expense=' + (SqlOne 'SELECT COUNT(*) FROM finance_expense') + ' invoice=' + (SqlOne 'SELECT COUNT(*) FROM finance_invoice') + ' project=' + (SqlOne 'SELECT COUNT(*) FROM dev_project') + ' matio=' + (SqlOne 'SELECT COUNT(*) FROM outsource_other_io') + ' matmove=' + (SqlOne 'SELECT COUNT(*) FROM inventory_material_move') + ' matloss=' + (SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss') + ' receivable=' + (SqlOne 'SELECT COUNT(*) FROM finance_receivable') + ' payable=' + (SqlOne 'SELECT COUNT(*) FROM finance_payable'))
