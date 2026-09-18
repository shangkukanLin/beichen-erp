package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * BOM 快照版本表（2026-09-17 重构）。
 * <p>按「产品 + 研发BOM版本 + 明细内容指纹」共享：下单时若研发 BOM 版本与内容与上一次快照一致，
 * 新加工单直接复用既有快照（<code>outsource_order_product.bom_snapshot_id</code> 指向它），
 * 不再每次下单都复制一份明细；只有"有变化"时才新建快照。</p>
 * <p>对外读取统一走视图 <code>outsource_order_material</code>（按加工单产品行展开）。</p>
 */
@Data
@TableName("bom_snapshot")
public class BomSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 快照归属键：有产品主数据=P:{product.id}；无则=N:{产品名称}（物料直挂模式） */
    private String productKey;

    /** 产品主数据ID(product.id) */
    private Long productMasterId;

    /** 研发项目ID(dev_project.id)，用于取研发BOM版本 */
    private Long projectId;

    /** 研发BOM版本号(dev_bom.version)；无项目时为空 */
    private Integer bomVersion;

    /** 明细内容指纹(MD5)：物料+单套用量+损耗率+供料方 规范化后计算；历史迁移快照为空（按版本号复用） */
    private String fingerprint;

    /** 明细行数（冗余便于展示） */
    private Integer itemCount;

    /** 来源：BOM=与研发BOM一致 ORDER=订单内手工调整 MIGRATED=历史订单迁移 */
    private String kind;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
