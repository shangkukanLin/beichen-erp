# verify-fix-f7-90-96-100-103.ps1  (regression for the dev-module P3 clean-up batch)
#
# F7-100 dev_bug.code had no UNIQUE key; BugController used @PathVariable projectId in update/delete
#        without ever reading it (so any Bug id could be changed/deleted across projects); bugType /
#        severity / status were taken verbatim from the request body (three fromCode helpers existed but
#        were unused -> the list rendered blank labels); and the code used substring(len-3) + a silent
#        `catch -> seq = 1` while the comment claimed a different format.
# F7-96  the "project cancelled" guard existed in complete/skip/savePhaseRow but was missing in
#        revertPhase / updatePlanned / updatePlannedAndShift; savePhaseRow also did oldStatus.equals(...)
#        (NPE when oldStatus is null) and accepted any status string (a bogus value makes
#        syncProjectStatus treat the phase as unfinished -> the project can never be closed).
# F7-90  syncPaixianComponents wrote setCompanyId(CompanyContext.get()) unconditionally, so under the
#        super-admin context (0/null) it persisted company_id = NULL (invisible to every company).
# F7-103 DevMaterialFlowServiceImpl silently accepted an unknown placeType (empty place name, no error,
#        no log), never checked that materialId exists, and update/delete had no existence check.
# F7-101 (touched in the same area) DevPurchaseItemController.add wrote companyId unconditionally.
#
# Assertions are read-only (SELECT / information_schema / source regex scoped to method bodies).
# NOTE: every source assertion below is scoped to a method body and matches signatures/calls rather
#       than bare keywords -- see skill note #28 (comments and lookalike identifiers make naive
#       regex assertions report false FAILs).
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
# Extract a method body: signature -> first "{ ... \n    }" (4-space indent = end of the member).
# NOTE: [^;{]* lets the signature regex stop anywhere inside the parameter list, so both a full
# signature ("...Long phaseId)") and a partial one ("...@PathVariable Long projectId") resolve to the
# same body. Without it, partial signatures never matched and every scoped assertion reported a false FAIL.
function Body([string]$path, [string]$signatureRegex) {
  if (-not (Test-Path $path)) { return '' }
  $m = [regex]::Match((Get-Content $path -Raw -Encoding UTF8), $signatureRegex + '[^;{]*\{([\s\S]*?)\n    \}')
  if (-not $m.Success) { return '' }
  return $m.Groups[1].Value
}
function IndexExists([string]$table, [string]$index) {
  return [int](SqlOne "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='beichen_erp' AND TABLE_NAME='$table' AND INDEX_NAME='$index' AND NON_UNIQUE=0")
}

$bugCtrl   = "$SERVER\dev\controller\BugController.java"
$phaseSvc  = "$SERVER\dev\service\impl\ProjectPhaseServiceImpl.java"
$projSvc   = "$SERVER\dev\service\impl\ProjectServiceImpl.java"
$flowSvc   = "$SERVER\dev\service\impl\DevMaterialFlowServiceImpl.java"
$itemCtrl  = "$SERVER\dev\controller\DevPurchaseItemController.java"
$itemSvc   = "$SERVER\dev\service\impl\DevPurchaseItemServiceImpl.java"

# ================= F7-100: Bug module =================
Write-Output '=== F7-100: Bug ownership, enum whitelist, code format, unique key ==='

if ((IndexExists 'dev_bug' 'uk_code') -ge 1) { Ok "dev_bug.code carries a UNIQUE key (uk_code)" }
else { Bad "dev_bug.code has no UNIQUE key -- duplicate Bug numbers can still be persisted" }

$dupBug = [int](SqlOne "SELECT COUNT(*) FROM (SELECT code FROM dev_bug GROUP BY code HAVING COUNT(*)>1) t")
if ($dupBug -eq 0) { Ok "no duplicate Bug codes exist right now" } else { Bad "$dupBug duplicate Bug code(s) exist" }

$updateBody = Body $bugCtrl 'public R<Void> update\(@PathVariable Long projectId'
$deleteBody = Body $bugCtrl 'public R<Void> delete\(@PathVariable Long projectId'
if ($updateBody -match 'requireOwned\(') { Ok "update() validates ownership against the path projectId" }
else { Bad "update() still ignores the path projectId" }
if ($deleteBody -match 'requireOwned\(') { Ok "delete() validates ownership against the path projectId" }
else { Bad "delete() still ignores the path projectId" }
if (Has $bugCtrl 'private Bug requireOwned\(Long projectId, Long id\)') { Ok "requireOwned() helper is defined" }
else { Bad "requireOwned() helper is missing" }

$addBody = Body $bugCtrl 'public R<Bug> add\(@PathVariable Long projectId'
if ($addBody -match 'projectMapper\.selectById\(projectId\)') { Ok "add() verifies the target project exists" }
else { Bad "add() does not verify the target project" }
if ($addBody -match 'validateEnums\(bug\)') { Ok "add() runs the enum whitelist check" }
else { Bad "add() does not run the enum whitelist check" }
if ($updateBody -match 'validateEnums\(bug\)') { Ok "update() runs the enum whitelist check" }
else { Bad "update() does not run the enum whitelist check" }
if ($updateBody -match 'bug\.setProjectId\(exist\.getProjectId\(\)\)') { Ok "update() pins projectId to the stored value" }
else { Bad "update() lets the body move the Bug to another project" }

$genBody = Body $bugCtrl 'private String generateBugCode\(\)'
if ($genBody -eq '') { Bad "could not locate generateBugCode()" }
else {
  if ($genBody -match 'lastIndexOf\(''-''\)|lastIndexOf\("-"\)') { Ok "generateBugCode parses the sequence after the last dash" }
  else { Bad "generateBugCode still slices a fixed 3-char tail" }
  if ($genBody -match 'log\.warn\(') { Ok "generateBugCode logs a warning when parsing fails" }
  else { Bad "generateBugCode falls back to seq=1 silently" }
}

foreach ($pair in @(@('BugStatus', "$SERVER\dev\common\BugStatus.java"),
                    @('BugTypeEnum', "$SERVER\dev\common\BugTypeEnum.java"),
                    @('SeverityType', "$SERVER\dev\common\SeverityType.java"),
                    @('PhaseStatus', "$SERVER\dev\common\PhaseStatus.java"),
                    @('DevMaterialPlaceTypeEnum', "$SERVER\dev\common\DevMaterialPlaceTypeEnum.java"))) {
  if (Has $pair[1] ('public static ' + $pair[0] + ' fromCode\(String code\)')) { Ok ($pair[0] + ".fromCode() is available for whitelist checks") }
  else { Bad ($pair[0] + ".fromCode() is missing") }
}

# ================= F7-96: phase guards / whitelist =================
Write-Output '=== F7-96: cancelled-project guard on every phase entry point ==='

foreach ($entry in @(@('revertPhase', 'public void revertPhase\(Long projectId, Long phaseId\)'),
                      @('updatePlanned', 'public void updatePlanned\(Long projectId, String phaseName, LocalDate plannedEnd\)'),
                      @('updatePlannedAndShift', 'public void updatePlannedAndShift\(Long projectId, String phaseName, LocalDate plannedEnd\)'))) {
  $b = Body $phaseSvc $entry[1]
  if ($b -eq '') { Bad ("could not locate " + $entry[0] + "()") }
  elseif ($b -match 'isProjectCancelled\(projectId\)') { Ok ($entry[0] + "() checks isProjectCancelled like the other entry points") }
  else { Bad ($entry[0] + "() still lacks the cancelled-project guard") }
}
foreach ($entry in @('completePhase', 'skipPhase', 'savePhaseRow')) {
  $b = Body $phaseSvc ('public void ' + $entry + '\(Long projectId')
  if ($b -match 'isProjectCancelled\(projectId\)') { Ok ($entry + "() still checks isProjectCancelled") }
  else { Bad ($entry + "() lost the cancelled-project guard") }
}

$saveBody = Body $phaseSvc 'public void savePhaseRow\(Long projectId, ProjectPhase row\)'
if ($saveBody -match 'PhaseStatus\.fromCode\(newStatus\)') { Ok "savePhaseRow validates the status against PhaseStatus" }
else { Bad "savePhaseRow still accepts any status string" }
if ($saveBody -match 'Objects\.equals\(oldStatus, newStatus\)') { Ok "savePhaseRow compares status with Objects.equals (null-safe)" }
else { Bad "savePhaseRow still calls oldStatus.equals(...) -- NPE on null" }

# ================= F7-90: company id guard =================
Write-Output '=== F7-90: companyId is only written when a tenant context exists ==='

$paixianBody = Body $projSvc 'private void syncPaixianComponents\(Project project, int version\)'
if ($paixianBody -eq '') { Bad "could not locate syncPaixianComponents()" }
else {
  # NOTE: match an assignment statement, not the explanatory comment that quotes the old code.
  if ($paixianBody -match '(?m)^\s*(mat|n)\.setCompanyId\(CompanyContext\.get\(\)\);') { Bad "syncPaixianComponents still writes companyId unconditionally" }
  else { Ok "syncPaixianComponents no longer writes companyId unconditionally" }
  if ($paixianBody -match 'hasCid') { Ok "syncPaixianComponents guards with hasCid" }
  else { Bad "syncPaixianComponents has no hasCid guard" }
}

# ================= F7-103 / F7-101: flow validation =================
Write-Output '=== F7-103: material-flow placeType whitelist + existence checks ==='

$resolveBody = Body $flowSvc 'private void resolvePlaceName\(DevMaterialFlow flow\)'
if ($resolveBody -eq '') { Bad "could not locate resolvePlaceName()" }
else {
  if ($resolveBody -match 'DevMaterialPlaceTypeEnum\.fromCode\(') { Ok "resolvePlaceName whitelists placeType" }
  else { Bad "resolvePlaceName still accepts unknown placeType silently" }
}

$flowAddBody = Body $flowSvc 'public DevMaterialFlow add\(DevMaterialFlow flow\)'
if ($flowAddBody -eq '') { Bad "could not locate flow add()" }
else {
  if ($flowAddBody -match 'purchaseItemMapper\.selectById\(') { Ok "flow add() verifies the material exists" }
  else { Bad "flow add() does not verify the material" }
  if ($flowAddBody -match 'if \(cid != null && cid > 0\)') { Ok "flow add() guards companyId" } else { Bad "flow add() writes companyId unconditionally" }
}
$flowUpdBody = Body $flowSvc 'public DevMaterialFlow update\(DevMaterialFlow flow\)'
if ($flowUpdBody -match 'getById\(flow\.getId\(\)\)') { Ok "flow update() checks the record exists" }
else { Bad "flow update() has no existence check" }
$flowDelBody = Body $flowSvc 'public void delete\(Long id\)'
if ($flowDelBody -match 'getById\(id\)') { Ok "flow delete() checks the record exists" }
else { Bad "flow delete() has no existence check" }

# NOTE: the guard moved from the controller into the service (addItem) in the P3 batch -- assert the
# end-to-end shape (controller delegates + service guards) instead of pinning the old location.
$itemAddBody    = Body $itemCtrl 'public R<DevPurchaseItem> add\(@RequestBody DevPurchaseItem item\)'
$itemAddSvcBody = Body $itemSvc 'public DevPurchaseItem addItem\(DevPurchaseItem item\)'
if ($itemAddBody -match 'addItem\(' -and $itemAddSvcBody -match 'if \(cid != null && cid > 0\)') {
  Ok "dev purchase item add() guards companyId (delegated to the service)"
} else { Bad "dev purchase item add() writes companyId unconditionally" }

# ================= summary =================
Write-Output ''
if ($script:fails -eq 0) { Write-Output 'ALL CHECKS PASSED' }
else { Write-Output ("FAILED CHECKS: " + $script:fails); exit 1 }
