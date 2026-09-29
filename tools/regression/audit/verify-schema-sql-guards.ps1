# Static guard for schema.sql (2026-09-30, settings batch C).
# Why: the same defect twice - a line-level comment was inserted BEFORE the column separator, so the
# trailing comma ended up inside the comment and MySQL reported a syntax error, which made the whole
# app fail to start (Spring runs schema.sql on boot). Occurrences: F7-258 (finance unique keys) and
# S-9② (sys_role.uk_role_code). Both were "0 lines removed / 1 line changed" edits.
# Checks:
#   1. no line ends with "..." + comma inside a line comment  ->  "... -- comment ,"  (comma swallowed)
#   2. no BOM at the start of the file (Spring would see it as part of the first token)
#   3. every "UNIQUE KEY"/"INDEX" line that carries a comment keeps the comma BEFORE "--"
# Read-only. ASCII-only by design.
$ErrorActionPreference = 'Continue'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$schema = Join-Path $repo 'beichen-erp-server\src\main\resources\schema.sql'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }

Write-Host '=== schema.sql static guards ==='
$lines = [System.IO.File]::ReadAllLines($schema)

Sec '1) comma must never be swallowed by a line comment (the F7-258 / S-9 repeat defect)'
$bad = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
  $l = $lines[$i]
  $idx = $l.IndexOf('--')
  if ($idx -lt 0) { continue }
  $afterComment = $l.Substring($idx)
  # a separator comma appearing at the end of a comment is always the defect
  if ($afterComment -match ',\s*$') { $bad += ('line ' + ($i + 1) + ': ' + $l.Trim()) }
}
foreach ($b in $bad) { Write-Host ('    offender -> ' + $b) }
Ok ($bad.Count -eq 0) 'no line has a trailing comma inside a comment'

Sec '2) key/index lines with comments keep the comma before "--"'
$keyBad = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
  $l = $lines[$i]
  if ($l -notmatch '(UNIQUE KEY|INDEX |KEY )') { continue }
  if ($l -notmatch '--') { continue }
  $idx = $l.IndexOf('--')
  # full-line comments ("-- ...") are not inline separators -> skip them
  if ($l.Substring(0, $idx).Trim().Length -eq 0) { continue }
  $before = $l.Substring(0, $idx)
  if ($before -notmatch ',\s*$') { $keyBad += ('line ' + ($i + 1) + ': ' + $l.Trim()) }
}
foreach ($b in $keyBad) { Write-Host ('    suspect -> ' + $b) }
Ok ($keyBad.Count -eq 0) 'key/index comments are preceded by the separator comma (or the key is last in the table)'

Sec '3) no BOM (Spring schema runner treats it as part of the first token)'
$b = [System.IO.File]::ReadAllBytes($schema)[0..2]
Ok (-not ($b[0] -eq 239 -and $b[1] -eq 187 -and $b[2] -eq 191)) ('schema.sql first bytes = ' + ($b -join ','))

Sec '4) live DB matches the intended role key (company_id, role_code)'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$k = (@(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name='sys_role' AND index_name='uk_role_code'" 2>$null) | Where-Object { "$_" -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1)
Write-Host ('    uk_role_code -> ' + "$k".Trim())
Ok ("$k".Trim() -eq 'company_id,role_code') 'live sys_role.uk_role_code = (company_id, role_code)'

Write-Host ''
Write-Host ('RESULT  PASS=' + $pass + '  FAIL=' + $fail)
if ($fail -gt 0) { exit 1 } else { exit 0 }
