# 物料类型重命名后的跨模块冒烟（2026-09-15）：逐页打开，断言可渲染 + 无「BOM类型」残留
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }

$pages = @(
  '/outsource/order/add',
  '/outsource/material-order/add',
  '/outsource/delivery/add',
  '/outsource/stock-loss/add',
  '/outsource/other-io/add',
  '/outsource/return-order/add',
  '/outsource/material-return/add',
  '/dev/project',
  '/supplier/manage',
  '/outsource/warehouse'
)
foreach ($p in $pages) {
  agent-browser open ($base + $p) | Out-Null
  agent-browser wait 2600
  $js = "JSON.stringify({path:location.pathname,rows:document.querySelectorAll('.el-table__body tr').length,cards:document.querySelectorAll('.el-card').length,len:document.body.innerText.length,hasNew:document.body.innerText.includes('物料类型'),hasOld:document.body.innerText.includes('BOM' + '类型')})"
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Write-Output ('FAIL ' + $p + ' 未能读取页面'); $fail++; continue }
  $d = $m.Value | ConvertFrom-Json
  $ok = ($d.cards -gt 0 -and $d.len -gt 200)
  $line = ($p.PadRight(34) + ' 卡=' + $d.cards + ' 行=' + $d.rows + ' 物料类型=' + $d.hasNew + ' BOM类型=' + $d.hasOld)
  if ($ok -and -not $d.hasOld) { Write-Output ('PASS ' + $line) } else { Write-Output ('FAIL ' + $line); $fail++ }
}
if ($fail -eq 0) { Write-Output 'RESULT PASS 跨模块冒烟全部通过（无 BOM类型 残留、页面正常渲染）' } else { Write-Output ('RESULT FAIL 项数 ' + $fail); exit 1 }
