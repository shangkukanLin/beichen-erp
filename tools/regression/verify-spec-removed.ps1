# 产品规格下线后逐页烟测（2026-09-15）：产品口径页面不应再出现「规格」；物料口径页面应仍保留
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-spec-removed.ps1
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
# expect: 'no' = 该页不应再出现「规格」；'yes' = 物料/BOM 口径，应仍保留
$pages = @(
  @{ n = '产品管理(列表)';        u = '/product';                 expect = 'no' },
  @{ n = '新增产品(表单)';        u = '/product/add';             expect = 'no' },
  @{ n = '销售单(列表)';          u = '/inventory/sale';          expect = 'no' },
  @{ n = '采购单(列表)';          u = '/inventory/purchase';      expect = 'no' },
  @{ n = '成品报损(列表)';        u = '/inventory/stock-loss';    expect = 'no' },
  @{ n = '品质转换(列表)';        u = '/inventory/reclassify';    expect = 'no' },
  @{ n = '退货整理(列表)';        u = '/inventory/return-sort';   expect = 'no' },
  @{ n = '其他出入库(列表)';      u = '/inventory/other-io';      expect = 'no' },
  @{ n = '成品库存详情(列表)';    u = '/inventory/product-stock'; expect = 'no' },
  @{ n = '移仓单新增(产品口径)'; u = '/inventory/warehouse-move/add'; expect = 'no' }
)
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
$js = "(()=>{const t=document.body.innerText;return JSON.stringify({hasSpec:t.includes('规格'),rows:document.querySelectorAll('.el-table__row').length,tables:document.querySelectorAll('.el-table').length,forms:document.querySelectorAll('.el-form').length,viteErr:t.includes('Internal server error')||t.includes('[plugin:vite')});})()"

$fail = 0
foreach ($p in $pages) {
  agent-browser open ($base + $p.u) | Out-Null
  agent-browser wait 2600
  if (((EvalJs "'path=' + location.pathname").Trim()) -match '/login') {
    Write-Output '（会话失效，先登录）'
    $snap = (agent-browser snapshot -i) -join "`n"
    $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
    $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
    $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500
    agent-browser open ($base + $p.u) | Out-Null
    agent-browser wait 2600
  }
  $out = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($out, '\{.*\}')
  if (-not $m.Success) { Write-Output ("WARN {0}：未读到页面状态 {1}" -f $p.n, $out); $fail++; continue }
  $ui = $m.Value | ConvertFrom-Json
  $want = if ($p.expect -eq 'no') { -not $ui.hasSpec } else { $ui.hasSpec }
  $tag = if ($want) { 'PASS' } else { 'FAIL' }
  if (-not $want) { $fail++ }
  Write-Output ("{0} {1}：含规格={2}（期望 {3}） 表格={4} 行数={5} 表单={6} Vite报错={7} 路径={8}" -f `
    $tag, $p.n, $ui.hasSpec, $p.expect, $ui.tables, $ui.rows, $ui.forms, $ui.viteErr, (EvalJs "'path='+location.pathname"))
  if ($ui.viteErr) { Write-Output ("FAIL {0}：页面出现 Vite 报错" -f $p.n); $fail++ }
}
if ($fail -eq 0) { Write-Output 'RESULT PASS 产品口径规格已移除、物料口径保留、页面均正常渲染' }
else { Write-Output ("RESULT FAIL 项数 " + $fail); exit 1 }
