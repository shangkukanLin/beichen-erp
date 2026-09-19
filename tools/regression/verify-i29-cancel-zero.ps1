# I29 修复验证（2026-09-18，(a) 口径：作废台账金额清零）
# 口径：应付/应收被「作废(CANCELLED)」时，amount 与 unpaid_amount 一并清零（原金额写入 remark 留痕），
#       使「amount = paid + unpaid」在**所有行**（含作废留痕行）上恒成立。
# 验证方式：走真实业务路径「采购退货反审核」→ 断言新产生的 CANCELLED 行 amount=0 且备注留痕 →
#           再「重新审核」→ 断言重新生成有效应付 → 最后做全表不变量检查。
# ASCII ONLY. 可重复运行（每次都会把目标单反审核再重审，单状态回到原样）。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$pass = 0; $fail = 0
function Ok($cond, $msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:pass++ } else { Write-Host ('FAIL ' + $msg); $script:fail++ }
}
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

Write-Host '--- 0) baseline invariants (ALL rows incl. CANCELLED)'
$badPay = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
$badRecv = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
$cancelPay = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='CANCELLED'")
$cancelRecv = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE status='CANCELLED'")
Write-Host ("[DB] payables violating amount=paid+unpaid: " + $badPay + " ; receivables: " + $badRecv + " (CANCELLED rows: " + $cancelPay + " pay / " + $cancelRecv + " recv)")
Ok (($badPay -eq 0)) ('every payable row satisfies amount = paid + unpaid (incl. CANCELLED), bad=' + $badPay)
Ok (($badRecv -eq 0)) ('every receivable row satisfies amount = paid + unpaid (incl. CANCELLED), bad=' + $badRecv)

Write-Host '--- 1) pick an AUDITED purchase return whose payable is unpaid (reverse-able)'
$rid = 0; $rcode = ''
foreach ($ln in (SqlLines "SELECT id, code FROM purchase_return WHERE status='AUDITED' ORDER BY id")) {
  $f = $ln -split "`t"
  $id = [int]$f[0].Trim(); $code = $f[1].Trim()
  $active = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN' AND source_bill_no='" + $code + "' AND status<>'CANCELLED' AND IFNULL(paid_amount,0)=0"))
  if ($active -ge 1) { $rid = $id; $rcode = $code; break }
}
Write-Host ('  target purchase return id=' + $rid + ' code=' + $rcode)
Ok (($rid -gt 0)) ('found a reverse-able purchase return (' + $rcode + ')')
if ($rid -eq 0) { Write-Host ('RESULT FAIL I29 cancel-zero (PASS=' + $pass + ' FAIL=' + $fail + ')'); exit 1 }

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }

Write-Host '--- 2) un-audit it -> the payable must be CANCELLED with amount ZEROED + remark trace'
$r1 = Invoke-RestMethod -Uri "$base/inventory/purchase-return/$rid/un-audit" -Method Put -Headers $h
Ok (($r1.code -eq 200) -or ($r1.code -eq 0)) ('un-audit accepted (code=' + $r1.code + ' msg=' + $r1.msg + ')')
Start-Sleep -Milliseconds 800
$st = SqlOne ("SELECT status FROM purchase_return WHERE id=" + $rid)
Ok (($st -eq 'DRAFT')) ('purchase return back to DRAFT (status=' + $st + ')')
$cx = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN' AND source_bill_no='" + $rcode + "' AND status='CANCELLED' AND IFNULL(amount,0)=0 AND IFNULL(unpaid_amount,0)=0"))
Ok (($cx -ge 1)) ('CANCELLED payable created with amount=0 AND unpaid=0 (rows=' + $cx + ')')
$mk = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN' AND source_bill_no='" + $rcode + "' AND status='CANCELLED' AND remark LIKE '%" + [char]0x539F + [char]0x91D1 + [char]0x989D + "%'"))
Write-Host ('  CANCELLED rows carrying the pre-void amount in remark = ' + $mk)
Ok (($mk -ge 1)) ('pre-void amount kept in remark for traceability (rows=' + $mk + ')')

Write-Host '--- 3) re-audit -> a fresh active payable must be created again'
$r2 = Invoke-RestMethod -Uri "$base/inventory/purchase-return/$rid/audit" -Method Put -Headers $h
Ok (($r2.code -eq 200) -or ($r2.code -eq 0)) ('re-audit accepted (code=' + $r2.code + ' msg=' + $r2.msg + ')')
Start-Sleep -Milliseconds 800
$st2 = SqlOne ("SELECT status FROM purchase_return WHERE id=" + $rid)
Ok (($st2 -eq 'AUDITED')) ('purchase return AUDITED again (status=' + $st2 + ')')
$activeAgain = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN' AND source_bill_no='" + $rcode + "' AND status<>'CANCELLED' AND IFNULL(amount,0)<>0"))
Ok (($activeAgain -ge 1)) ('fresh active payable regenerated (rows=' + $activeAgain + ')')

Write-Host '--- 4) final invariants (whole table, incl. CANCELLED)'
$badPay2 = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
$badRecv2 = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
$zeroCancel = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)")
$zeroCancelR = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)")
Write-Host ("[DB] allRowsBad pay=" + $badPay2 + " recv=" + $badRecv2 + " ; CANCELLED rows still carrying money pay=" + $zeroCancel + " recv=" + $zeroCancelR)
Ok (($badPay2 -eq 0)) 'payable invariant holds on ALL rows'
Ok (($badRecv2 -eq 0)) 'receivable invariant holds on ALL rows'
Ok (($zeroCancel -eq 0)) ('no CANCELLED payable carries amount/unpaid (bad=' + $zeroCancel + ')')
Ok (($zeroCancelR -eq 0)) ('no CANCELLED receivable carries amount/unpaid (bad=' + $zeroCancelR + ')')

Write-Host ''
if ($fail -eq 0) { Write-Host ('RESULT PASS I29 cancel-zero (PASS=' + $pass + ' FAIL=0)') } else { Write-Host ('RESULT FAIL I29 cancel-zero (PASS=' + $pass + ' FAIL=' + $fail + ')') }
if ($fail -gt 0) { exit 1 }
