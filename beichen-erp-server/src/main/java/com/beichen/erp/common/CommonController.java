package com.beichen.erp.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.mapper.InventoryOtherIoMapper;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
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

    @GetMapping("/resolve-code")
    public R<Map<String, Object>> resolveCode(@RequestParam String code) {
        if (code == null || code.isBlank()) return R.ok(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);

        if (code.startsWith("WO-")) {
            OutsourceOrder o = orderMapper.selectOne(
                new LambdaQueryWrapper<OutsourceOrder>().eq(OutsourceOrder::getCode, code));
            if (o != null) { result.put("type", "order"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith("MWO-") || code.startsWith("PO-")) {
            MaterialOrder o = materialOrderMapper.selectOne(
                new LambdaQueryWrapper<MaterialOrder>().eq(MaterialOrder::getCode, code));
            if (o != null) { result.put("type", "material_order"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith("DEL-") || code.startsWith("DEF-")) {
            OutsourceDelivery o = deliveryMapper.selectOne(
                new LambdaQueryWrapper<OutsourceDelivery>().eq(OutsourceDelivery::getCode, code));
            if (o != null) { result.put("type", "delivery"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith("QT-")) {
            InventoryOtherIo o = inventoryOtherIoMapper.selectOne(
                new LambdaQueryWrapper<InventoryOtherIo>().eq(InventoryOtherIo::getCode, code));
            if (o != null) { result.put("type", "other_io"); result.put("id", o.getId()); return R.ok(result); }
        }
        if (code.startsWith("OWO-")) {
            OutsourceOtherIo o = outsourceOtherIoMapper.selectOne(
                new LambdaQueryWrapper<OutsourceOtherIo>().eq(OutsourceOtherIo::getCode, code));
            if (o != null) { result.put("type", "outsource_other_io"); result.put("id", o.getId()); return R.ok(result); }
        }
        return R.ok(result);
    }
}
