# ASCII only. Guard for the per-user table column-width memory (2026-10-09).
#
# WHAT IT LOCKS IN
#   ① 拖动列宽 → 服务端存下这条偏好（sys_user_table_pref，按用户、不分公司）；
#   ② 重新进入该页面 → 列宽**仍然是用户拖过的宽度**（这就是需求本身）；
#   ③ 表头右键 → 确认 → 恢复默认列宽，且服务端偏好被删除；
#   ④ 全程不产生 JS/API 报错。
#
# WHY /pages chosen: /finance/payable 是稳定的列表页（7 列、无固定列），既是拖拽对象也让断言可读。
# CLEANUP: 结尾一律 DELETE /mine 清掉本用户的全部偏好 —— 浏览器 profile 是多个守卫共用的，
#          留下偏好会影响后续"表格一行放得下"等布局守卫。
. $PSScriptRoot\ui-e2e-lib.ps1

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$ROUTE = '/finance/payable'
$COL = 1                 # 第二列（第一列常带固定宽度，取第二列更直观）
$DELTA = 60              # 向右拖 60px
$API = 'http://localhost:8080/api'

function SqlOne([string]$q) { return "$(& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null)".Trim() }

function ColWidth([int]$idx) {
  $js = "(()=>{const t=document.querySelector('.el-table');if(!t)return -1;const cs=[...t.querySelectorAll('colgroup col')];const c=cs[$idx];return c?Math.round(c.getBoundingClientRect().width):-1})()"
  return [int]((EvalJs $js).Trim([char]34))
}

# 页面是异步渲染的：等表格与列真正出现（最多 ~6s），避免量到 -1
function WaitTable([int]$idx = 0, [int]$ms = 6000) {
  for ($i = 0; $i -lt [int]($ms / 300); $i++) {
    if ((ColWidth $idx) -gt 0) { return $true }
    Start-Sleep -Milliseconds 300
  }
  return $false
}

# 拖拽：EP 是"鼠标靠近 th 右边界"启动的，故先 mousemove 预位、再 mousedown / 移动 / mouseup
function DragColumn([int]$idx, [int]$dx) {
  $js = "(()=>{const t=document.querySelector('.el-table');if(!t)return 'NOTABLE';const ths=[...t.querySelectorAll('.el-table__header th')];const th=ths[$idx];if(!th)return 'NOTH';const r=th.getBoundingClientRect();const y=Math.round(r.top+r.height/2);const x=Math.round(r.right-3);const fire=(type,target,cx)=>target.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,clientX:cx,clientY:y,button:0,buttons:1,which:1}));fire('mousemove',th,x);fire('mousedown',th,x);fire('mousemove',document.documentElement,x+$dx);fire('mousemove',document.documentElement,x+$dx);fire('mouseup',document.documentElement,x+$dx);return 'DRAGGED'})()"
  return (EvalJs $js).Trim([char]34)
}

EnsureLogin
# 起点干净：先清掉本用户的全部列宽偏好再重载 —— 否则上一轮留下的记忆宽度会让"可拖空间"变小，
# 使"拖 60px ⇒ 宽度 +60"这条断言不可复现。直接拿页面里的 token 调接口。
$tok = (EvalJs "String(localStorage.getItem('beichen_erp_token'))").Trim([char]34)
if ($tok -and $tok -ne 'null') {
  try { Invoke-RestMethod -Uri "$API/system/table-prefs/mine" -Method Delete -Headers @{ Authorization = $tok } | Out-Null } catch { }
}
Open $ROUTE 3200
# 清完偏好必须**整页刷新**：`Open` 走的是 SPA 同路由跳转，组件不会重挂载 ⇒ 页面继续用"上一轮已应用的
# 宽度"，于是"页面默认列宽"会被记成拖后的值（实测"重置"会回到 107 而不是 96）。
EvalJs "location.reload();'x'" | Out-Null
Start-Sleep -Milliseconds 4000
ClearErrs | Out-Null
$null = WaitTable $COL

$base = ColWidth $COL
Write-Host ("  baseline col[$COL] width = $base")
Ok ($base -gt 0) "the list page renders a table with a measurable column (width=$base)"

$drag = DragColumn $COL $DELTA
Write-Host ("  drag => $drag")
Start-Sleep -Milliseconds 500
$after = ColWidth $COL
Write-Host ("  width after drag = $after (baseline $base + $DELTA)")
Ok ($after -ge ($base + $DELTA - 12)) "dragging widened the column by ~$DELTA px (got $($after - $base))"

# 服务端偏好（节流 700ms 后才上报）
Start-Sleep -Milliseconds 1200
$keys = SqlOne "SELECT GROUP_CONCAT(pref_key) FROM sys_user_table_pref"
Write-Host ("  pref keys in DB = [" + $keys + "]")
Ok ($keys -like ("*" + $ROUTE + "*")) "the drag was persisted server-side under the route key ($ROUTE)"
$prefsVal = SqlOne ("SELECT prefs FROM sys_user_table_pref WHERE pref_key LIKE '" + $ROUTE + "%' LIMIT 1")
Write-Host ("  stored prefs = " + $prefsVal)
Ok ([string]::IsNullOrWhiteSpace($prefsVal) -eq $false) 'the stored prefs payload is not empty'

# 重新进入页面 → 宽度必须保持。
# 用**整页刷新**模拟"下次进来"（等价于用户重新打开；也避开 SPA 同路由跳转在无参数时不重渲染的问题
# —— 实测该跳转下 `.el-table` 会量不到，导致断言报 -1 这种与功能无关的红）。
EvalJs "location.reload();'x'" | Out-Null
Start-Sleep -Milliseconds 4000
$null = WaitTable $COL
$reload = ColWidth $COL
Write-Host ("  width after re-entering the page = $reload")
Ok ([Math]::Abs($reload - $after) -le 3) "RE-ENTERING keeps the user's width (got $reload, expected ~$after)"

# 记忆生效后**再拖一次**必须仍然能改 —— 纯 CSS 钉死（`!important`）方案就是在这条上失败的：
# 渲染被压住、拖拽只动几像素。这条断言把"记忆宽度不得妨碍再次拖拽"钉住。
$before2 = ColWidth $COL
$drag2 = DragColumn $COL 40
Start-Sleep -Milliseconds 500
$after2 = ColWidth $COL
Write-Host ("  second drag => $drag2 ; width $before2 -> $after2")
Ok ($after2 -ge ($before2 + 40 - 12)) "a SECOND drag still resizes the column (memory must not pin it)"

# 表头右键 → 确认 → 恢复默认 + 删偏好
$ctx = EvalJs "(()=>{const w=document.querySelector('.el-table__header-wrapper');if(!w)return 'NOWRAP';w.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return 'MENU'})()"
Write-Host ("  right click => " + $ctx.Trim([char]34))
Start-Sleep -Milliseconds 900
# 刻意不传中文字面量（PS 5.1 会把无 BOM 脚本里的中文按 GBK 解，ClickDialogBtn 会抛 "no zh key"）：
# 直接点 Element Plus 确认框的主按钮。
$confirm = EvalJs "(()=>{const b=document.querySelector('.el-message-box__btns .el-button--primary');if(!b)return 'NOBTN';b.click();return 'CONFIRMED'})()"
Write-Host ("  confirm dialog => " + ($confirm.Trim([char]34)))
Start-Sleep -Milliseconds 900
$reset = ColWidth $COL
Write-Host ("  width right after reset = $reset (baseline $base)")
Ok ([Math]::Abs($reset - $base) -le 15) "right-click reset brings the column back to the default order of magnitude (got $reset, baseline $base)"
# 更强也更有意义的断言：重置后再**整页刷新**一次，必须回到页面默认布局。
# （不在"重置当刻"断言像素级相等：Element Plus 在没有偏好时会重新分配弹性列的空间，与我们写入前的
#   渲染值可能差十几像素 —— 那是 EP 的正常行为，不是本功能的问题。）
EvalJs "location.reload();'x'" | Out-Null
Start-Sleep -Milliseconds 4000
$null = WaitTable $COL
$afterResetReload = ColWidth $COL
Write-Host ("  width after reset + reload = $afterResetReload (baseline $base)")
Ok ([Math]::Abs($afterResetReload - $base) -le 6) "after the reset the page falls back to its DEFAULT layout on a fresh load (got $afterResetReload, baseline $base)"
Start-Sleep -Milliseconds 900
$left = @(SqlOne "SELECT COUNT(*) FROM sys_user_table_pref")
Write-Host ("  pref rows after reset = " + $left)
Ok ("$left" -eq '0') 'reset removed the server-side preference'

Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'

# 收尾：无论如何清掉本用户的偏好（共享浏览器 profile，别影响其它守卫）
try {
  $lg = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
  $lr = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' -Body $lg
  Invoke-RestMethod -Uri "$API/system/table-prefs/mine" -Method Delete -Headers @{ Authorization = $lr.data.token } | Out-Null
} catch { Write-Host '  (cleanup call failed, continuing)' }
Write-Host ("  final pref rows = " + (SqlOne "SELECT COUNT(*) FROM sys_user_table_pref"))

Summary 'table column width memory (per user)'
