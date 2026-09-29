package com.beichen.erp.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.mapper.InventoryOtherIoMapper;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.mapper.PurchaseOrderMapper;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final OutsourceOrderMapper orderMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final InventoryOtherIoMapper inventoryOtherIoMapper;
    private final OutsourceOtherIoMapper outsourceOtherIoMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SaleOrderMapper saleOrderMapper;

    @GetMapping("/resolve-code")
    public R<Map<String, Object>> resolveCode(@RequestParam String code) {
        // 参数非法返回 400（原先 R.ok(null) 会让调用方把"参数为空"当成"解析不到单据"）
        if (code == null || code.isBlank()) throw new BusinessException(400, "单号不能为空");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);

        if (code.startsWith(BillPrefix.OUTSOURCE_ORDER)) {
            OutsourceOrder o = orderMapper.selectOne(
                new LambdaQueryWrapper<OutsourceOrder>().eq(OutsourceOrder::getCode, code));
            if (o != null) { result.put("type", "order"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith(BillPrefix.OUTSOURCE_MATERIAL_ORDER) || code.startsWith(BillPrefix.OUTSOURCE_PO)) {
            MaterialOrder o = materialOrderMapper.selectOne(
                new LambdaQueryWrapper<MaterialOrder>().eq(MaterialOrder::getCode, code));
            if (o != null) { result.put("type", "material_order"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith(BillPrefix.OUTSOURCE_DELIVERY) || code.startsWith(BillPrefix.OUTSOURCE_DEFECT)) {
            OutsourceDelivery o = deliveryMapper.selectOne(
                new LambdaQueryWrapper<OutsourceDelivery>().eq(OutsourceDelivery::getCode, code));
            if (o != null) { result.put("type", "delivery"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith(BillPrefix.INVENTORY_OTHER_IO)) {
            InventoryOtherIo o = inventoryOtherIoMapper.selectOne(
                new LambdaQueryWrapper<InventoryOtherIo>().eq(InventoryOtherIo::getCode, code));
            if (o != null) { result.put("type", "other_io"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith(BillPrefix.OUTSOURCE_OTHER_IO)) {
            OutsourceOtherIo o = outsourceOtherIoMapper.selectOne(
                new LambdaQueryWrapper<OutsourceOtherIo>().eq(OutsourceOtherIo::getCode, code));
            if (o != null) { result.put("type", "outsource_other_io"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith(BillPrefix.PURCHASE)) {
            PurchaseOrder o = purchaseOrderMapper.selectOne(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getCode, code));
            if (o != null) { result.put("type", "purchase"); result.put("id", o.getId()); return R.ok(result); }
        }
        // P4（2026-09-30 未上线清理，用户口径「旧数据不需要兼容」）：原为 BillPrefix.SALE_ORDER_LEGACY("SO-")，
        // 而那使得**现行的 XS- 销售单号反而解析不到**。历史前缀分支已删除，改用现行前缀。
        if (code.startsWith(BillPrefix.SALE)) {
            SaleOrder o = saleOrderMapper.selectOne(
                new LambdaQueryWrapper<SaleOrder>().eq(SaleOrder::getCode, code));
            if (o != null) { result.put("type", "sale"); result.put("id", o.getId()); return R.ok(result); }
        }
        return R.ok(result);
    }
}
