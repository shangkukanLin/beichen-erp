# Seed 0: master data via API (brand/customer/supplier/product/material+components/project) - ASCII only
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }

function Post($path, $obj) {
    $json = $obj | ConvertTo-Json -Depth 8
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
        if ($r.code -and $r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path + ' -> ' + $_.Exception.Message); return $null }
}
function Put($path, $obj) {
    $json = if ($obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
        if ($r.code -and $r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path + ' -> ' + $_.Exception.Message); return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ($B + $path) -Headers $H).data } catch { Write-Output ('GETERR ' + $path); return $null } }

# 1) brands (5)
$brandIds = @()
for ($i = 1; $i -le 5; $i++) {
    Post '/brand' @{ brandName = 'BRAND-' + $i; status = 1 } | Out-Null
}
$brandIds = @((GetP '/brand/page?pageSize=50').records | ForEach-Object { $_.id })
Write-Output ('brands=' + $brandIds.Count)

# 2) customers (8)
for ($i = 1; $i -le 8; $i++) {
    Post '/inventory/customer' @{ name = 'CUST-' + $i; contact = 'CONTACT-' + $i; phone = '1380000' + ('{0:D4}' -f $i); status = 1; remark = 'seed' } | Out-Null
}
$custIds = @((GetP '/inventory/customer/page?pageSize=50').records | ForEach-Object { $_.id })
Write-Output ('customers=' + $custIds.Count)

# 3) suppliers: 4 factory / 5 product / 3 material / 2 solution (factory auto-creates outsource warehouse)
$supPlan = @(
    @{ prefix = 'FAC-SUP'; type = 'factory';  n = 4 },
    @{ prefix = 'PRO-SUP'; type = 'product';  n = 5 },
    @{ prefix = 'MAT-SUP'; type = 'material'; n = 3 },
    @{ prefix = 'SOL-SUP'; type = 'solution'; n = 2 }
)
foreach ($sp in $supPlan) {
    for ($i = 1; $i -le $sp.n; $i++) {
        Post '/supplier' @{ name = ($sp.prefix + '-' + $i); typeCodes = @($sp.type); contact = 'CT-' + $sp.prefix + '-' + $i; phone = '1390000' + ('{0:D4}' -f $i); status = 1; remark = 'seed' } | Out-Null
    }
}
$supAll = (GetP '/supplier/page?pageSize=200').records
Write-Output ('suppliers=' + $supAll.Count + ' (factory should auto-create outsource warehouses)')

# 4) products (20) with brand
$prodOk = 0
for ($i = 1; $i -le 20; $i++) {
    $bid = $brandIds[($i - 1) % $brandIds.Count]
    $r = Post '/product' @{ name = 'PROD-' + ('{0:D2}' -f $i); unit = 'PCS'; spec = 'SEED-SPEC-' + $i; status = 'NORMAL'; safetyStock = 10; brandId = $bid; remark = 'seed' }
    if ($r) { $prodOk++ }
}
$prodIds = @((GetP '/product/page?pageSize=50').records | ForEach-Object { $_.id })
Write-Output ('products=' + $prodIds.Count + ' created=' + $prodOk)

# 5) outsource materials: 8 child + 6 parent (with components)
$bomTypes = (GetP '/dev/bom-type/enabled')
$btId = if ($bomTypes -and $bomTypes.Count) { [int]$bomTypes[0].id } else { $null }
Write-Output ('bomTypes=' + $bomTypes.Count + ' first=' + $btId)
# 2026-10-10 修复（用户口径「物料必须有类型」）：本段原来往物料接口传的是 **bomTypeId**
#   —— 那是**研发 BOM 的类型**，字段名撞车；而后端 create 只认 `materialTypeId`（`if != null` 才写）
#   ⇒ 静默落 NULL、且不报错 ⇒ 实测一次 seed 造出 14 条无类型物料（SUB-MAT-* / MAT-* 全是这样来的 ✗）。
#   现在按「物料类型」接口取名（默认第一类）并把字段名改对 ✓；后端也已补必填校验（见 OutsourceMaterialServiceImpl.create）。
$matTypes = @((GetP '/dev/material-type/enabled'))
$mtId = if ($matTypes.Count -gt 0) { [int]$matTypes[0].id } else { $null }
Write-Output ('materialTypes=' + $matTypes.Count + ' first=' + $mtId)
if (-not $mtId) { Write-Output 'WARN no enabled material type -> materials will have NO type (backend will now reject them)' }
$matSupIds = @($supAll | Where-Object { $_.name -like 'MAT-SUP-*' } | ForEach-Object { $_.id })
$childIds = @()
for ($i = 1; $i -le 8; $i++) {
    $supStr = ($matSupIds -join ',')
    $r = Post '/outsource/material' @{ materialName = 'SUB-MAT-' + $i; unit = 'PCS'; price = [math]::Round(($i * 7 + 3), 2); status = 1; materialTypeId = $mtId; supplierIds = $supStr; remark = 'seed child' }
    if ($r) { $childIds += [int]$r.data }
}
$parentIds = @()
for ($i = 1; $i -le 6; $i++) {
    $supStr = ($matSupIds -join ',')
    $r = Post '/outsource/material' @{ materialName = 'MAT-' + $i; unit = 'PCS'; price = [math]::Round(($i * 30 + 60), 2); status = 1; materialTypeId = $mtId; supplierIds = $supStr; remark = 'seed parent' }
    if ($r) { $parentIds += [int]$r.data }
}
# components: each parent uses 2 children
$compOk = 0
for ($k = 0; $k -lt $parentIds.Count; $k++) {
    $c1 = $childIds[$k % $childIds.Count]; $c2 = $childIds[($k + 3) % $childIds.Count]
    $items = @(
        @{ childMaterialId = $c1; quantity = 2; lossRate = 0 },
        @{ childMaterialId = $c2; quantity = 1; lossRate = 5 }
    )
    $r = Put ('/outsource/material/' + $parentIds[$k] + '/components') $items
    if ($r) { $compOk++ }
}
$matIds = @($parentIds) + @($childIds)
Write-Output ('materials=' + $matIds.Count + ' parents=' + $parentIds.Count + ' componentsOk=' + $compOk)

# 6) dev projects (3)
$projOk = 0
for ($i = 1; $i -le 3; $i++) {
    $r = Post '/dev/project' @{ name = 'PRJ-' + $i; productName = 'ASSY-' + $i; specType = 'MATCHED'; brandId = $brandIds[($i - 1) % $brandIds.Count]; startDate = '2026-08-01'; expectedEndDate = '2026-10-01'; remark = 'seed project' }
    if ($r) { $projOk++ }
}
Write-Output ('projects created=' + $projOk)

# 7) warehouses: ensure aux / defect / after-sale inventory warehouses exist
$whs = (GetP '/warehouse/page?pageSize=300').records
function Cn([int[]]$codes) { return -join ($codes | ForEach-Object { [char]$_ }) }
# 仓库类型必须写**枚举 code**（DB 存 code；见 beichen-erp-web/src/api/enums.ts 的 WarehouseType 注释
# 与 WarehouseType.java）。旧版这里写的是中文 label（成品仓/不良仓/售后仓/辅料仓），
# 导致 SaleReturnServiceImpl 的 `WarehouseType.AFTER_SALE.equals(wh.getWarehouseType())` 校验恒不成立
# → 销售退货审核必然失败（2026-09-14 定位并修复）。
$W_AUX = 'AUXILIARY'
$W_DEF = 'DEFECT'
$W_AFS = 'AFTER_SALE'
$W_FIN = 'FINISHED'
$hasAux = @($whs | Where-Object { $_.warehouseType -eq $W_AUX }).Count
$hasDef = @($whs | Where-Object { $_.warehouseType -eq $W_DEF }).Count
$hasAfs = @($whs | Where-Object { $_.warehouseType -eq $W_AFS }).Count
$hasFin = @($whs | Where-Object { $_.warehouseType -eq $W_FIN }).Count
if (-not $hasFin) { Post '/warehouse' @{ warehouseName = 'FIN-WH-1'; warehouseCategory = 'INVENTORY'; warehouseType = $W_FIN; status = 1 } | Out-Null }
if (-not $hasDef) { Post '/warehouse' @{ warehouseName = 'DEF-WH-1'; warehouseCategory = 'INVENTORY'; warehouseType = $W_DEF; status = 1 } | Out-Null }
if (-not $hasAfs) { Post '/warehouse' @{ warehouseName = 'AFS-WH-1'; warehouseCategory = 'INVENTORY'; warehouseType = $W_AFS; status = 1 } | Out-Null }
if (-not $hasAux) { Post '/warehouse' @{ warehouseName = 'AUX-WH-1'; warehouseCategory = 'INVENTORY'; warehouseType = $W_AUX; status = 1 } | Out-Null }
$whs = (GetP '/warehouse/page?pageSize=300').records
Write-Output ('warehouses total=' + $whs.Count + ' (inventory + factory outsource)')
$summary = @{ brandIds = $brandIds; custIds = $custIds; prodIds = $prodIds; matIds = $matIds; parentMatIds = $parentIds; childMatIds = $childIds }
$summary | ConvertTo-Json -Depth 5 | Out-File -FilePath 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Encoding utf8
Write-Output 'saved seed_ids.json'
