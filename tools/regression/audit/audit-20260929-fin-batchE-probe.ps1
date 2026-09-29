# Batch E read-only probe (2026-09-29): cross-module accounting (sale / purchase -> finance ledgers).
# READ ONLY: every statement is a SELECT.
# ASCII ONLY (no BOM -> PS 5.1 would read non-ASCII as GBK).
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Q([string]$sql) {
  foreach ($l in @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null)) {
    if ("$l" -notmatch '^(mysql:|ERROR)') { Write-Host ('    ' + $l) }
  }
}
Write-Host '=== batch E probe: sale/purchase -> finance (read only) ==='

Sec '1) ledger provenance: who wrote the receivables / payables (source_bill_type)'
Q "SELECT source_bill_type, status, COUNT(*) FROM finance_receivable GROUP BY source_bill_type, status ORDER BY 3 DESC"
Q "SELECT source_bill_type, status, COUNT(*) FROM finance_payable GROUP BY source_bill_type, status ORDER BY 3 DESC"

Sec '2) F7-24x candidate: re-audit accumulation (CANCELLED ledgers left behind per source doc)'
Q "SELECT source_bill_type, COUNT(*) cancelled FROM finance_payable WHERE status='CANCELLED' GROUP BY source_bill_type ORDER BY 2 DESC"
Q "SELECT source_bill_type, COUNT(*) cancelled FROM finance_receivable WHERE status='CANCELLED' GROUP BY source_bill_type ORDER BY 2 DESC"
Q "SELECT COUNT(*) AS payables_without_source FROM finance_payable WHERE source_bill_type IS NULL OR source_bill_type=''"
Q "SELECT COUNT(*) AS receivables_without_source FROM finance_receivable WHERE source_bill_type IS NULL OR source_bill_type=''"

Sec '3) sale exchange vs sale return: does the EXCHANGE document write any ledger amount?'
Q "SELECT source_bill_type, COUNT(*) rows_, IFNULL(SUM(amount),0) sum_amount FROM finance_receivable WHERE source_bill_type IN ('SALE_RETURN','SALE_EXCHANGE','SALE_RETURN_LOSS','SALE_EXCHANGE_FEE') GROUP BY source_bill_type"
Q "SELECT COUNT(*) AS exchange_docs, (SELECT COUNT(*) FROM finance_receivable r WHERE r.source_bill_type='SALE_EXCHANGE') AS exchange_ledgers FROM sale_exchange"
Q "SELECT COUNT(*) AS return_docs, (SELECT COUNT(*) FROM finance_receivable r WHERE r.source_bill_type='SALE_RETURN') AS return_ledgers FROM sale_return"

Sec '4) audit-state symmetry: AUDITED docs whose ledger is missing / DRAFT docs that still carry an ACTIVE ledger'
Q "SELECT COUNT(*) AS audited_sale_orders_without_receivable FROM sale_order o WHERE o.status='AUDITED' AND NOT EXISTS (SELECT 1 FROM finance_receivable r WHERE r.source_bill_type='SALE_ORDER' AND r.source_id=o.id AND r.status<>'CANCELLED')"
Q "SELECT COUNT(*) AS draft_sale_orders_with_active_receivable FROM sale_order o WHERE o.status='DRAFT' AND EXISTS (SELECT 1 FROM finance_receivable r WHERE r.source_bill_type='SALE_ORDER' AND r.source_id=o.id AND r.status<>'CANCELLED')"
Q "SELECT COUNT(*) AS audited_purchase_orders_without_payable FROM purchase_order o WHERE o.status='AUDITED' AND NOT EXISTS (SELECT 1 FROM finance_payable p WHERE p.source_bill_type='PURCHASE_ORDER' AND p.source_id=o.id AND p.status<>'CANCELLED')"
Q "SELECT COUNT(*) AS draft_purchase_orders_with_active_payable FROM purchase_order o WHERE o.status='DRAFT' AND EXISTS (SELECT 1 FROM finance_payable p WHERE p.source_bill_type='PURCHASE_ORDER' AND p.source_id=o.id AND p.status<>'CANCELLED')"

Sec '5) cash-sale auto receipt (createCashReceipt): provenance + symmetry'
Q "SELECT r.status, COUNT(*) FROM finance_receipt r WHERE r.source_bill_type='SALE_ORDER' GROUP BY r.status"
Q "SELECT COUNT(*) AS auto_receipts_without_linked_order FROM finance_receipt r WHERE r.source_bill_type='SALE_ORDER' AND NOT EXISTS (SELECT 1 FROM sale_order o WHERE o.id=r.source_id)"
Q "SELECT COUNT(*) AS cash_orders_audited_without_receipt FROM sale_order o WHERE o.status='AUDITED' AND o.settle_type IN ('CASH') AND NOT EXISTS (SELECT 1 FROM finance_receipt r WHERE r.source_bill_type='SALE_ORDER' AND r.source_id=o.id AND r.status<>'CANCELLED')"

Sec '6) orphan ledgers (source document no longer exists)'
Q "SELECT COUNT(*) AS orphan_receivables FROM finance_receivable r WHERE r.source_id IS NOT NULL AND r.source_bill_type IS NOT NULL AND r.source_bill_type NOT IN ('ADVANCE_LEDGER','MANUAL') AND NOT EXISTS (SELECT 1 FROM sale_order o WHERE o.id=r.source_id AND r.source_bill_type='SALE_ORDER') AND NOT EXISTS (SELECT 1 FROM sale_return s WHERE s.id=r.source_id AND r.source_bill_type='SALE_RETURN')"
Q "SELECT COUNT(*) AS orphan_payables FROM finance_payable p WHERE p.source_id IS NOT NULL AND p.source_bill_type IS NOT NULL AND p.source_bill_type NOT IN ('ADVANCE_LEDGER','MANUAL') AND NOT EXISTS (SELECT 1 FROM purchase_order o WHERE o.id=p.source_id AND p.source_bill_type='PURCHASE_ORDER') AND NOT EXISTS (SELECT 1 FROM purchase_return s WHERE s.id=p.source_id AND p.source_bill_type='PURCHASE_RETURN')"

Sec '7) scale behind the cross-module legs'
Q "SELECT (SELECT COUNT(*) FROM sale_order) so,(SELECT COUNT(*) FROM sale_return) sr,(SELECT COUNT(*) FROM sale_exchange) sx,(SELECT COUNT(*) FROM sale_outbound) so2,(SELECT COUNT(*) FROM purchase_order) po,(SELECT COUNT(*) FROM purchase_return) pr,(SELECT COUNT(*) FROM purchase_exchange) px,(SELECT COUNT(*) FROM finance_receivable) recv,(SELECT COUNT(*) FROM finance_payable) pay"
Q "SELECT status, COUNT(*) FROM sale_order GROUP BY status"
Q "SELECT status, COUNT(*) FROM purchase_order GROUP BY status"
Write-Host ''
Write-Host 'DONE (no writes performed).'
