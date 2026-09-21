package com.beichen.erp.dev.service;

/**
 * 项目关联产品同步服务
 * <p>2026-09-21：「总成名称」更名为「产品名称」（{@code Project.assemblyName → productName}、
 * DB 列 {@code assembly_name → product_name}），本接口的方法名与参数一并统一，
 * 两个方向的同步方法改为 {@code From/To Project} 命名以便区分。</p>
 */
public interface ProjectProductSyncService {

    /**
     * 根据项目「产品名称」创建/关联项目产品
     *
     * @param projectId             项目ID
     * @param linkExistingProductId 关联的已有产品ID（null=新建产品）
     * @param productSku            立项页指定的产品SKU（2026-09-21 新增，默认 {@code NS-} 打头、可改）；
     *                              为 null/空白时交由 {@code ProductService} 自动生成。
     *                              **走 {@code linkExistingProductId} 分支（关联已有产品）时本参数不生效**
     *                              —— 那时产品已存在，SKU 沿用该产品自己的。
     */
    void syncProduct(Long projectId, Long linkExistingProductId, String productSku);

    /**
     * 修改项目「产品名称」后，同步更新关联产品的名称
     *
     * @param projectId      项目ID
     * @param newProductName 新的产品名称
     */
    void syncProductNameFromProject(Long projectId, String newProductName);

    /**
     * 修改产品名称后，同步更新关联项目的「产品名称」
     *
     * @param productId 产品ID
     * @param newName   新的产品名称
     */
    void syncProductNameToProject(Long productId, String newName);

    /**
     * 同步项目的「规格」到关联产品（2026-09-21 新增，需求"规格要和产品的规格联动"）。
     * <p>立项时由 {@link #syncProduct} 一并写入；之后在立项详细页改规格走本方法。</p>
     */
    void syncProductSpecFromProject(Long projectId);

    /**
     * 同步立项详细页填写的「产品SKU」到关联产品（2026-09-21 需求 1）。
     * <p>只在**确有变化**时写；未关联产品 / SKU 为空则不动。公司内唯一性由
     * {@code ProductService.updateById} 校验（重复会明确报错）。
     * 前端在提交前已做二次确认（SKU 是既有编码，历史单据里存的是快照）。</p>
     */
    void syncProductSkuFromProject(Long projectId, String productSku);

    /**
     * 同步项目关联产品的状态（研发中→正常）
     * 当项目阶段推进到"小批量"或"结项"时触发
     */
    void syncProductStatus(Long projectId);
}
