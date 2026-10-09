# 仓库「负责人」保存不生效 —— 2026-10-09 用户报告：「编辑仓库，填写负责人，点击保存后不生效」。
#
# 根因（已定位）：`warehouse` 表与实体的列叫 **contact**（注释"联系人"），而前端这两个页面的
# 「负责人」绑的却是 **manager** —— 实体里没有这个属性 ⇒ Jackson 静默丢弃
# （Spring Boot 默认 FAIL_ON_UNKNOWN_PROPERTIES=false）⇒ contact 永远不落库；回显同理恒空。
# 受影响页面：`views/inventory/warehouse.vue`（成品仓库管理）、
# `views/outsource/material-warehouse.vue`（自有物料仓管理）。「委外仓」页一直是对的（用 contact）。
#
# 断言策略（本仓纪律：断言落到**库事实**、不看 toast 自报成功；每条都要能报红）：
#   ① 编辑某仓（按**仓库编码**定位，纯 ASCII 免掉编码坑）→ 填负责人 → 确定
#      ⇒ DB `warehouse.contact` == 写入值（**修前这一列恒为空 ⇒ 这条必然红** = 天然负例）；
#   ② 重新进入页面（整页刷新）→ 再开编辑弹窗 ⇒ 负责人输入框**回显**该值；
#   ③ 列表「负责人」列也显示该值（证明列 prop 也对齐了）；
#   ④ 自有物料仓页同流程再证一遍（同一缺陷的另一处）；
#   ⑤ 全程无 JS/API 报错；
#   ⑥ 收尾把 contact 还原成改动前的值（不留测试数据）。
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (($out | Select-Object -First 1) | ForEach-Object { "$_" })
}
function SqlCol([string]$q) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

if (-not (EnsureLoginAs 'lin' '123')) {
  Skip 'cannot log in as lin (the account holding stock:warehouse / outsource:material-warehouse)'
  Summary 'warehouse person-in-charge (contact) persists'
  exit 0
}
$stamp = (Get-Date).ToString('HHmmss')

# 在页面里按 label 精确读"负责人"输入框的值（中文标签 base64 进 JS、浏览器内比较，只回传 ASCII 值）
function ContactFieldValue() {
  $b = B64 (ZH 'lbl_contact_incharge')
  $js = "(()=>{const T=q=>new TextDecoder().decode(Uint8Array.from(atob(q),c=>c.charCodeAt(0)));const L=T('$b');const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';for(const it of d.querySelectorAll('.el-form-item')){const l=it.querySelector('.el-form-item__label');if(!l)continue;if((l.innerText||'').replace(/[\s*:]/g,'')!==L)continue;const inp=it.querySelector('input');return inp?String(inp.value):'NOINPUT'}return 'NOITEM'})()"
  return (EvalJs $js).Trim([char]34)
}

function CheckPage([string]$route, [string]$category, [string]$type, [int]$contactCol, [string]$tag) {
  # ⚠️ 不能只取"第一条"：列表排序方向不确定，取到的行可能不在第 1 页 ⇒ 遍历候选，谁在列表里就用谁。
  $codes = SqlCol ("SELECT code FROM warehouse WHERE warehouse_category='" + $category + "' AND warehouse_type='" + $type + "' AND IFNULL(status,1)=1 ORDER BY id DESC LIMIT 8")
  if ($codes.Count -eq 0) { Skip ($tag + ': no enabled ' + $category + '/' + $type + ' warehouse (fixture missing)'); return }

  Open $route 3000
  $rowIdx = '-1'; $code = ''
  foreach ($c in $codes) {
    $i = FindRow $c
    if ($i -ne '-1') { $rowIdx = $i; $code = $c; break }
  }
  Write-Host ('  [' + $tag + '] candidates=' + $codes.Count + ' -> visible row=' + $rowIdx + ' code=' + $code)
  if ($rowIdx -eq '-1') { Bad ($tag + ': none of the candidate warehouses is visible in the list'); return }

  $id = SqlOne ("SELECT id FROM warehouse WHERE code='" + $code + "'")
  $before = SqlOne ("SELECT IFNULL(contact,'') FROM warehouse WHERE id=" + $id)
  $marker = 'E2E-CONTACT-' + $tag + '-' + $stamp
  Write-Host ('  [' + $tag + '] warehouse #' + $id + ' contact_before="' + $before + '" -> "' + $marker + '"')

  $r = ClickRowBtn ([int]$rowIdx) 'btn_edit'
  Start-Sleep -Milliseconds 1200
  Write-Host ('  [' + $tag + '] edit dialog >> ' + $r)
  Ok ($r -match 'OK') ($tag + ': the row action opens the edit dialog')

  $r = FillLabel 'lbl_contact_incharge' $marker
  Ok ($r -match 'OK') ($tag + ': the person-in-charge field can be filled (' + $r + ')')
  ClickDialogBtn 'btn_ok' | Out-Null
  Start-Sleep -Milliseconds 1600

  # ① 落库（修前恒空 ⇒ 这条是天然负例）
  $after = SqlOne ("SELECT IFNULL(contact,'') FROM warehouse WHERE id=" + $id)
  Write-Host ('  [' + $tag + '] contact in DB after save = "' + $after + '"')
  Ok ($after -eq $marker) ($tag + ': saving the edit PERSISTS the person-in-charge into warehouse.contact')

  # ②③ 重新进入页面 -> 列表列 + 编辑弹窗回显
  Open $route 3000
  $rowIdx2 = FindRow $code
  if ($rowIdx2 -eq '-1') {
    Bad ($tag + ': the row is not visible after the reload (cannot check the echo)')
  } else {
    Ok ((CellText ([int]$rowIdx2) $contactCol).Trim() -eq $marker) ($tag + ': the list column shows the person-in-charge')
    ClickRowBtn ([int]$rowIdx2) 'btn_edit' | Out-Null
    Start-Sleep -Milliseconds 1200
    $echo = ContactFieldValue
    Write-Host ('  [' + $tag + '] echo in the dialog = "' + $echo + '"')
    Ok ($echo -eq $marker) ($tag + ': re-opening the edit dialog ECHOES the saved value')
    ClickDialogBtn 'btn_cancel' | Out-Null
    Start-Sleep -Milliseconds 600
  }

  # ⑥ 还原成改动前的值（空串 = 显式清空，后端会更新该列）
  Open $route 3000
  $rowIdx3 = FindRow $code
  if ($rowIdx3 -ne '-1') {
    ClickRowBtn ([int]$rowIdx3) 'btn_edit' | Out-Null
    Start-Sleep -Milliseconds 1200
    FillLabel 'lbl_contact_incharge' $before | Out-Null
    ClickDialogBtn 'btn_ok' | Out-Null
    Start-Sleep -Milliseconds 1400
    $restored = SqlOne ("SELECT IFNULL(contact,'') FROM warehouse WHERE id=" + $id)
    Write-Host ('  [' + $tag + '] restored contact = "' + $restored + '" (want "' + $before + '")')
    Ok ($restored -eq $before) ($tag + ': the fixture was restored to its original value')
  }
}

ClearErrs | Out-Null
CheckPage '/inventory/warehouse' 'INVENTORY' 'FINISHED' 6 'FIN'
CheckPage '/outsource/material-warehouse' 'INVENTORY' 'AUXILIARY' 4 'AUX'
Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'
Summary 'warehouse person-in-charge (contact) persists'
