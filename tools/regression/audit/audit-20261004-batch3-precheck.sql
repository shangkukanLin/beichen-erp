-- audit 2026-10-04 batch 3 (bill product items) - READ ONLY precheck
-- Q1: how bills distribute over ledger source types (which types are reachable in a bill)
SELECT '--- Q1 bill items by ledger source type ---' AS section;
SELECT r.source_bill_type AS ledger_type, COUNT(*) AS c
  FROM finance_bill_item bi JOIN finance_receivable r ON r.id = bi.source_id
 GROUP BY r.source_bill_type
 UNION ALL
SELECT p.source_bill_type, COUNT(*)
  FROM finance_bill_item bi JOIN finance_payable p ON p.id = bi.source_id
 GROUP BY p.source_bill_type
 ORDER BY c DESC;

-- Q2: for OUTSOURCE_DELIVERY / OUTSOURCE_EXCESS_LOSS, is ledger.source_id the delivery-record id (needs resolve-code)
--     or already the outsource order id?
SELECT '--- Q2 outsource sources: ledger.source_id vs order id ---' AS section;
SELECT bi.id AS bill_item_id, bi.source_bill_no, p.source_bill_type AS ledger_type, p.source_id AS ledger_source_id,
       (SELECT COUNT(*) FROM outsource_order o WHERE o.id = p.source_id) AS is_order_id
  FROM finance_bill_item bi JOIN finance_payable p ON p.id = bi.source_id
 WHERE p.source_bill_type IN ('OUTSOURCE_DELIVERY','OUTSOURCE_EXCESS_LOSS')
 LIMIT 8;

-- Q3: return-sourced bill items (for the signed-sum / quantity-sum display question)
SELECT '--- Q3 return-sourced bill items ---' AS section;
SELECT bi.id AS bill_item_id, bi.bill_id, bi.source_bill_no, bi.amount,
       r.source_bill_type AS ledger_type, r.source_id AS biz_id
  FROM finance_bill_item bi JOIN finance_receivable r ON r.id = bi.source_id
 WHERE r.source_bill_type IN ('SALE_RETURN','PURCHASE_RETURN')
 LIMIT 8;

-- Q4: charge-sourced bill items (lineKind=CHARGE path)
SELECT '--- Q4 charge-sourced bill items ---' AS section;
SELECT bi.id AS bill_item_id, bi.bill_id, bi.source_bill_no, bi.amount, r.source_bill_type AS ledger_type, r.source_id AS biz_id
  FROM finance_bill_item bi JOIN finance_receivable r ON r.id = bi.source_id
 WHERE r.source_bill_type LIKE '%CHARGE%'
 LIMIT 8;
