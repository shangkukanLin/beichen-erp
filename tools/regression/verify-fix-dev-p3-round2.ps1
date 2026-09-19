# verify-fix-dev-p3-round2.ps1  (regression for the second dev-module P3 batch)
#
# F7-91  materialTypeIdMap used put() so a duplicate type name silently made "the last one win"; the
#        update path had no duplicate-name check at all (rename A to B and the mapping drifts).
# F7-92  project cancel() did select-then-write (no CAS), did not validate the current status and did
#        not check downstream documents (a project with running outsource orders could be cancelled).
# F7-94  checkProductStatusSync / syncPaixianComponents returned silently when a phase template or a
#        material type name was not found (rename => the linkage dies with no signal).
# F7-95  FileController.upload had no size cap of its own, no extension policy and no
#        sanitize/normalize/startsWith check (download had one -- asymmetric guard).
# F7-97  BomController.saveItem accepted a client-supplied version (rewriting history) and could move
#        another project's BOM row onto the current project via setProjectId.
# F7-98  drawing version generation had no concurrency protection; delete had no existence/ownership
#        check. F7-104: the delete mapping (/drawing/{id}) did not match the frontend call
#        (/{projectId}/drawing/{id}) so deleting a drawing always returned 404.
# F7-101 dev purchase item had no required/non-negative validation, update wrote the whole entity and
#        delete left orphan rows in dev_material_flow.
# F7-102 ScreenModelController talked to the mapper directly for all CRUD, batch had no cap and no
#        transaction, and /list exported via the magic pageSize=100000.
#
# Assertions are read-only. Source assertions are scoped to method bodies and match calls/signatures
# (see skill note #28: comments and lookalike identifiers cause false FAILs otherwise).
# ASCII-only on purpose (PS 5.1 + BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL  = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$SERVER = 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp'
$env:MYSQL_PWD = 'root'
$script:fails = 0

function Sql([string]$sql) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>&1
  return ($out | Out-String).Trim()
}
function SqlOne([string]$sql) { $v = Sql $sql; if ($v -eq '') { return '' }; return ($v -split "`n")[0].Trim() }
function Ok([string]$m)   { Write-Output ("  [OK]   " + $m) }
function Bad([string]$m)  { Write-Output ("  [FAIL] " + $m); $script:fails++ }
function Info([string]$m) { Write-Output ("  [INFO] " + $m) }
function Has([string]$path, [string]$pattern) {
  if (-not (Test-Path $path)) { return $false }
  return ((Get-Content $path -Raw -Encoding UTF8) -match $pattern)
}
function Body([string]$path, [string]$signatureRegex) {
  if (-not (Test-Path $path)) { return '' }
  $m = [regex]::Match((Get-Content $path -Raw -Encoding UTF8), $signatureRegex + '[^;{]*\{([\s\S]*?)\n    \}')
  if (-not $m.Success) { return '' }
  return $m.Groups[1].Value
}
function IndexExists([string]$table, [string]$index) {
  return [int](SqlOne "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='beichen_erp' AND TABLE_NAME='$table' AND INDEX_NAME='$index' AND NON_UNIQUE=0")
}

$projSvc  = "$SERVER\dev\service\impl\ProjectServiceImpl.java"
$mtSvc    = "$SERVER\dev\service\impl\MaterialTypeServiceImpl.java"
$phaseSvc = "$SERVER\dev\service\impl\ProjectPhaseServiceImpl.java"
$fileCtrl = "$SERVER\dev\controller\FileController.java"
$bomCtrl  = "$SERVER\dev\controller\BomController.java"
$dwgCtrl  = "$SERVER\dev\controller\DrawingController.java"
$dwgSvc   = "$SERVER\dev\service\impl\DrawingServiceImpl.java"
$itemCtrl = "$SERVER\dev\controller\DevPurchaseItemController.java"
$itemSvc  = "$SERVER\dev\service\impl\DevPurchaseItemServiceImpl.java"
$smSvc    = "$SERVER\dev\service\ScreenModelService.java"
$smImpl   = "$SERVER\dev\service\impl\ScreenModelServiceImpl.java"
$smCtrl   = "$SERVER\dev\controller\ScreenModelController.java"

# ================= F7-91 =================
Write-Output '=== F7-91: material type name mapping is deterministic + unique ==='
if ((IndexExists 'material_type' 'uk_company_name') -ge 1) { Ok "material_type has UNIQUE KEY uk_company_name(company_id,type_name)" }
else { Bad "material_type has no unique key on (company_id,type_name)" }
$dupMt = [int](SqlOne "SELECT COUNT(*) FROM (SELECT company_id,type_name FROM material_type GROUP BY company_id,type_name HAVING COUNT(*)>1) t")
if ($dupMt -eq 0) { Ok "no duplicate type names inside a company right now" } else { Bad "$dupMt duplicate type name(s) exist" }
$mapBody = Body $projSvc 'private Map<String, Long> materialTypeIdMap\(\)'
if ($mapBody -match 'putIfAbsent\(') { Ok "materialTypeIdMap uses putIfAbsent (no last-one-wins drift)" }
else { Bad "materialTypeIdMap still uses put() -- duplicate names drift" }
$mtUpdate = Body $mtSvc 'public void update\(MaterialType type\)'
if ($mtUpdate -match 'ne\(MaterialType::getId') { Ok "MaterialTypeServiceImpl.update rejects duplicate names (excluding itself)" }
else { Bad "MaterialTypeServiceImpl.update has no duplicate-name check" }

# ================= F7-92 =================
Write-Output '=== F7-92: project cancel is atomic and respects downstream documents ==='
$cancelBody = Body $projSvc 'public void cancel\(Long projectId\)'
if ($cancelBody -eq '') { Bad "could not locate cancel()" }
else {
  if ($cancelBody -match 'LambdaUpdateWrapper') { Ok "cancel() uses a conditional update (CAS), not select-then-write" }
  else { Bad "cancel() still does select-then-write" }
  if ($cancelBody -match 'countRunningOutsourceOrders\(') { Ok "cancel() checks downstream outsource orders" }
  else { Bad "cancel() does not check downstream documents" }
  if ($cancelBody -match 'updated == 0') { Ok "cancel() detects a lost CAS race" } else { Bad "cancel() ignores the CAS result" }
}
$countBody = Body $projSvc 'private Long countRunningOutsourceOrders\(Long projectId\)'
if ($countBody -match 'outsourceOrderProductMapper\.selectList') { Ok "downstream lookup uses outsource_order_product.project_id (not a remark LIKE)" }
else { Bad "downstream lookup does not use project_id" }
if ($countBody -match 'notIn\(OutsourceOrder::getStatus') { Ok "downstream lookup excludes finished/cancelled orders" }
else { Bad "downstream lookup does not filter order status" }

# ================= F7-94 =================
Write-Output '=== F7-94: silent linkage failures now log ==='
$cpsBody = Body $phaseSvc 'private void checkProductStatusSync\(String phaseName, Long projectId\)'
if ($cpsBody -match 'log\.warn\(') { Ok "checkProductStatusSync logs a warning when the template is missing" }
else { Bad "checkProductStatusSync still fails silently" }
$pxBody = Body $projSvc 'private void syncPaixianComponents\(Project project, int version\)'
if ($pxBody -match 'log\.warn\(') { Ok "syncPaixianComponents logs a warning when the type is missing" }
else { Bad "syncPaixianComponents still returns silently" }

# ================= F7-95 =================
Write-Output '=== F7-95: upload hardening ==='
$upBody = Body $fileCtrl 'public R<String> upload\(@RequestParam\("file"\) MultipartFile file\)'
if ($upBody -eq '') { Bad "could not locate upload()" }
else {
  if ($upBody -match 'MAX_ATTACHMENT_BYTES') { Ok "upload enforces its own size cap" } else { Bad "upload has no size cap of its own" }
  if ($upBody -match 'BLOCKED_EXTENSIONS\.contains') { Ok "upload rejects dangerous extensions" } else { Bad "upload has no extension policy" }
  if ($upBody -match 'sanitizeFileName\(') { Ok "upload sanitises the original filename" } else { Bad "upload does not sanitise the filename" }
  if ($upBody -match 'normalize\(\)') { Ok "upload normalises the resolved path" } else { Bad "upload does not normalise the target path" }
  if ($upBody -match 'target\.startsWith\(uploadDir\)') { Ok "upload verifies the path stays inside the upload dir" }
  else { Bad "upload does not verify the resolved path" }
}
if (Has $fileCtrl 'private static String sanitizeFileName\(String original\)') { Ok "sanitizeFileName helper is defined" }
else { Bad "sanitizeFileName helper is missing" }

# ================= F7-97 =================
Write-Output '=== F7-97: BOM single-row save cannot rewrite history or move rows across projects ==='
$saveItem = Body $bomCtrl 'public R<Void> saveItem\(@PathVariable Long projectId'
if ($saveItem -eq '') { Bad "could not locate saveItem()" }
else {
  if ($saveItem -match 'bomService\.getById\(bom\.getId\(\)\)') { Ok "saveItem checks the row exists" } else { Bad "saveItem does not check the row" }
  if ($saveItem -match '!projectId\.equals\(exist\.getProjectId\(\)\)') { Ok "saveItem rejects rows owned by another project" }
  else { Bad "saveItem can move another project's row" }
  if ($saveItem -match 'exist\.getVersion\(\) != maxVersion') { Ok "saveItem rejects edits of historical versions" }
  else { Bad "saveItem can still rewrite a historical version" }
  if ($saveItem -match 'bom\.setVersion\(maxVersion\)') { Ok "saveItem pins the version to the latest one" }
  else { Bad "saveItem still honours a client-supplied version" }
}

# ================= F7-98 / F7-104 =================
Write-Output '=== F7-98 / F7-104: drawing delete path + ownership + version concurrency ==='
if ((IndexExists 'dev_drawing' 'uk_proj_doc_ver') -ge 1) { Ok "dev_drawing has UNIQUE KEY uk_proj_doc_ver(project_id,doc_name,doc_type,version_code)" }
else { Bad "dev_drawing has no version unique key" }
if (Has $dwgCtrl '@DeleteMapping\("/\{projectId\}/drawing/\{id\}"\)') { Ok "delete mapping matches the frontend URL (F7-104)" }
else { Bad "delete mapping still differs from the frontend call -> 404" }
$dwgDel = Body $dwgCtrl 'public R<Void> delete\(@PathVariable Long projectId'
if ($dwgDel -match 'getById\(id\)') { Ok "drawing delete checks existence" } else { Bad "drawing delete has no existence check" }
if ($dwgDel -match '!projectId\.equals\(d\.getProjectId\(\)\)') { Ok "drawing delete checks ownership" } else { Bad "drawing delete has no ownership check" }
$dwgUp = Body $dwgSvc 'public Drawing upload\(Drawing drawing\)'
if ($dwgUp -eq '') { Bad "could not locate drawing upload()" }
else {
  if ($dwgUp -match 'DuplicateKeyException') { Ok "drawing upload retries on a version-number race" } else { Bad "drawing upload does not handle the race" }
  if ($dwgUp -match 'getDocName\(\) == null') { Ok "drawing upload validates the required fields" } else { Bad "drawing upload does not validate required fields" }
}

# ================= F7-101 =================
Write-Output '=== F7-101: dev purchase item validation / whitelist / cascade ==='
$itemAdd = Body $itemSvc 'public DevPurchaseItem addItem\(DevPurchaseItem item\)'
if ($itemAdd -match 'validateItem\(item\)') { Ok "addItem validates the payload" } else { Bad "addItem does not validate" }
$itemUpd = Body $itemSvc 'public void updateItem\(DevPurchaseItem item\)'
if ($itemUpd -match 'DevPurchaseItem patch = new DevPurchaseItem\(\)') { Ok "updateItem uses a whitelist patch" } else { Bad "updateItem writes the whole entity" }
if ($itemUpd -match 'setCompanyId\(') { Bad "updateItem still writes companyId" } else { Ok "updateItem does not write companyId" }
$itemDel = Body $itemSvc 'public void deleteItem\(Long id\)'
if ($itemDel -match 'materialFlowMapper\.delete\(') { Ok "deleteItem cascades to dev_material_flow" } else { Bad "deleteItem leaves orphan flow rows" }
$vBody = Body $itemSvc 'private void validateItem\(DevPurchaseItem item\)'
if ($vBody -match 'getQuantity\(\) < 0') { Ok "validateItem rejects negative quantity" } else { Bad "validateItem does not reject negative quantity" }
if ($vBody -match 'compareTo\(java\.math\.BigDecimal\.ZERO\) < 0') { Ok "validateItem rejects negative amount" } else { Bad "validateItem does not reject negative amount" }
$itemCtrlAdd = Body $itemCtrl 'public R<DevPurchaseItem> add\(@RequestBody DevPurchaseItem item\)'
if ($itemCtrlAdd -match 'devPurchaseItemService\.addItem\(') { Ok "controller delegates add() to the service" } else { Bad "controller still writes directly" }

# ================= F7-102 =================
Write-Output '=== F7-102: screen knowledge base moved behind a service ==='
if (Test-Path $smSvc) { Ok "ScreenModelService interface exists" } else { Bad "ScreenModelService interface is missing" }
if (Test-Path $smImpl) { Ok "ScreenModelServiceImpl exists" } else { Bad "ScreenModelServiceImpl is missing" }
if (Has $smImpl 'MAX_BATCH_SIZE') { Ok "batch import has an upper bound" } else { Bad "batch import has no upper bound" }
$smListAll = Body $smImpl 'public List<ScreenModel> listAll\('
if ($smListAll -match 'mapper\.selectList\(') { Ok "listAll performs a real full query (no magic pageSize)" }
else { Bad "listAll still paginates with a magic size" }
if (Has $smImpl '@Transactional') { Ok "screen model writes are transactional" } else { Bad "screen model writes have no transaction" }
if (Has $smImpl 'isDuplicate\(') { Ok "create/update reject duplicates" } else { Bad "create/update do not reject duplicates" }
# NOTE: match the import/field, not the javadoc sentence that mentions the mapper name.
if (Has $smCtrl 'import com\.beichen\.erp\.dev\.mapper\.ScreenModelMapper|private final ScreenModelMapper') {
  Bad "controller still depends on the mapper directly"
} else { Ok "controller no longer depends on the mapper" }
if (Has $smCtrl 'screenModelService') { Ok "controller delegates to the service" } else { Bad "controller does not use the service" }

# ================= summary =================
Write-Output ''
if ($script:fails -eq 0) { Write-Output 'ALL CHECKS PASSED' }
else { Write-Output ("FAILED CHECKS: " + $script:fails); exit 1 }
