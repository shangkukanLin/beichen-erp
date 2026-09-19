# 仓型收敛为 2 种（成品仓 / 辅料仓）验证 —— 2026-09-16 方案 A
# 覆盖：①自有仓缺仓型→拦 ②仓型非法(DEFECT/已取消)→拦 ③建成品仓 ④建辅料仓 ⑤建委外仓(不写仓型)
#       ⑥页面下拉只有 成品仓/辅料仓 ⑦清理测试仓
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0
function Ok($m) { Write-Host ("PASS " + $m) }
function Bad($m) { Write-Host ("FAIL " + $m); $global:fail = $global:fail + 1 }
function EvalJs($js) {
  $out = @(agent-browser eval $js) | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '✓ Done' -and $_ -notmatch '^\s*✗' }
  return ($out -join "`n").Trim()
}
function Api($method, $path, $obj) {
  if ($obj) {
    $json = ConvertTo-Json -InputObject $obj -Compress -Depth 8
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
    $bp = ", body: new TextDecoder().decode(Uint8Array.from(atob('$b64'), c=>c.charCodeAt(0))), headers: { 'Content-Type':'application/json', Authorization: localStorage.getItem('beichen_erp_token') }"
  } else { $bp = ", headers: { Authorization: localStorage.getItem('beichen_erp_token') }" }
  $js = "fetch('$path',{method:'$method'$bp}).then(r=>r.text()).catch(e=>'ERR:'+e)"
  $raw = (EvalJs "(async()=>{return await $js})()") -replace '^"|"$', ''
  $raw = $raw.Replace('\"', '"')
  $m = [regex]::Match($raw, '"code"\s*:\s*(-?\d+)')
  $msg = [regex]::Match($raw, '"(?:message|msg)"\s*:\s*"([^"]*)"')
  return [pscustomobject]@{ Code = $(if ($m.Success) { [int]$m.Groups[1].Value } else { -1 }); Msg = $(if ($msg.Success) { $msg.Groups[1].Value } else { '' }); Raw = $raw }
}
# ---------- 登录 ----------
agent-browser open "$base/dashboard" | Out-Null
agent-browser wait 1200 | Out-Null
if ((EvalJs "String(!!localStorage.getItem('beichen_erp_token'))") -notmatch 'true') {
  EvalJs "localStorage.clear(); 'x'" | Out-Null
  agent-browser open "$base/login" | Out-Null
  agent-browser wait 2500 | Out-Null
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
  agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
  agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
  agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
  agent-browser wait 3500 | Out-Null
}

# ---------- ① 自有仓缺仓型 ----------
$r1 = Api 'POST' '/api/warehouse' @{ warehouseName = 'ZZ验收-缺仓型'; warehouseCategory = 'INVENTORY' }
if ($r1.Code -ne 200 -and $r1.Msg -match '仓型不能为空') { Ok '自有仓缺仓型被拦（仓型不能为空）' } else { Bad ("自有仓缺仓型未被拦：" + $r1.Code + ' ' + $r1.Msg) }

# ---------- ② 仓型非法（DEFECT 已取消） ----------
$r2 = Api 'POST' '/api/warehouse' @{ warehouseName = 'ZZ验收-非法仓型'; warehouseCategory = 'INVENTORY'; warehouseType = 'DEFECT' }
if ($r2.Code -ne 200 -and $r2.Msg -match '仓型非法') { Ok '已取消仓型(DEFECT)被拦（仓型非法）' } else { Bad ("DEFECT 未被拦：" + $r2.Code + ' ' + $r2.Msg) }

# ---------- ③④⑤ 建成品仓 / 辅料仓 / 委外仓 ----------
$r3 = Api 'POST' '/api/warehouse' @{ warehouseName = 'ZZ验收-成品仓'; warehouseCategory = 'INVENTORY'; warehouseType = 'FINISHED' }
if ($r3.Code -eq 200) { Ok '新建自有成品仓成功' } else { Bad ("新建成品仓失败：" + $r3.Msg) }
$r4 = Api 'POST' '/api/warehouse' @{ warehouseName = 'ZZ验收-辅料仓'; warehouseCategory = 'INVENTORY'; warehouseType = 'AUXILIARY' }
if ($r4.Code -eq 200) { Ok '新建自有辅料仓成功' } else { Bad ("新建辅料仓失败：" + $r4.Msg) }
$r5 = Api 'POST' '/api/warehouse' @{ warehouseName = 'ZZ验收-委外仓'; warehouseCategory = 'OUTSOURCE' }
if ($r5.Code -eq 200) { Ok '新建委外仓成功（未传仓型）' } else { Bad ("新建委外仓失败：" + $r5.Msg) }

# ---------- 查这 3 个仓，核对仓型取值 ----------
$pageRaw = (Api 'GET' "/api/warehouse/page?pageSize=500&warehouseName=ZZ验收").Raw
$types = [regex]::Matches($pageRaw, '"warehouseName"\s*:\s*"([^"]+)"[^}]*?"warehouseType"\s*:\s*(null|"([^"]*)")') | ForEach-Object {
  $_.Groups[1].Value + '=>' + $(if ($_.Groups[2].Value -eq 'null') { 'NULL' } else { $_.Groups[3].Value })
}
Write-Host ("仓型实际取值 = " + ($types -join ' ; '))
if ($types -match 'ZZ验收-成品仓=>FINISHED' -and $types -match 'ZZ验收-辅料仓=>AUXILIARY') { Ok '自有仓仓型落库正确（FINISHED/AUXILIARY）' } else { Bad '自有仓仓型落库不正确' }
if ($types -match 'ZZ验收-委外仓=>NULL') { Ok '委外仓不再写仓型（warehouse_type = NULL）' } else { Bad '委外仓仍带仓型' }

# ---------- ⑥ 页面下拉（浏览器） ----------
function DialogText($url, $name) {
  agent-browser open $url | Out-Null
  agent-browser wait 3000 | Out-Null
  EvalJs "(()=>{const b=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='新增');if(b)b.click();return 'ok';})()" | Out-Null
  agent-browser wait 1200 | Out-Null
  $t = (EvalJs "(()=>{const d=document.querySelector('.el-dialog');return d?d.innerText.split('\n').join('|'):'no-dialog';})()") -replace '"', ''
  Write-Host ("[" + $name + "] 新增弹窗 = " + $t)
  EvalJs "(()=>{const b=[...document.querySelectorAll('.el-dialog__footer button')].find(x=>x.innerText.trim()==='取消');if(b)b.click();return 'closed';})()" | Out-Null
  return $t
}
$t1 = DialogText "$base/inventory/warehouse" '仓库管理'
if ($t1 -match '成品仓' -and $t1 -notmatch '不良仓' -and $t1 -notmatch '售后仓') { Ok '仓库管理页仓型下拉只有「成品仓」' } else { Bad ('仓库管理页仓型文案异常：' + $t1) }
$t2 = DialogText "$base/outsource/material-warehouse" '自有物料仓'
if ($t2 -match '辅料仓') { Ok '自有物料仓页仓型显示「辅料仓」' } else { Bad ('自有物料仓页仓型异常：' + $t2) }

# ---------- ⑦ 清理测试仓 ----------
$ids = [regex]::Matches($pageRaw, '"id"\s*:\s*(\d+)[^}]*?"warehouseName"\s*:\s*"ZZ验收') | ForEach-Object { $_.Groups[1].Value }
foreach ($id in $ids) { $d = Api 'DELETE' "/api/warehouse/$id"; Write-Host ("清理测试仓 id=$id -> " + $d.Code) }
if ($ids.Count -eq 0) { Write-Host 'WARN 未取到测试仓 id，请手工清理名字以 ZZ验收 开头的仓库' }

if ($global:fail -eq 0) { Write-Host 'RESULT PASS 仓型已收敛为 成品仓/辅料仓（含接口护栏与页面下拉）' } else { Write-Host ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
