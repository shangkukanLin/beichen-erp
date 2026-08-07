package com.beichen.erp.dev.service;

/**
 * 项目关联产品同步服务
 */
public interface ProjectProductSyncService {

    /**
     * 根据总成名称创建/关联项目产品
     *
     * @param projectId            项目ID
     * @param linkExistingProductId 关联的已有产品ID（null=新建产品）
     */
    void syncProduct(Long projectId, Long linkExistingProductId);

    /**
     * 修改项目总成名称后，同步更新关联产品的产品名称
     *
     * @param projectId        项目ID
     * @param newAssemblyName  新的总成名称
     */
    void syncProductNameFromProject(Long projectId, String newAssemblyName);

    /**
     * 修改产品名称后，同步更新关联项目的总成名称
     *
     * @param productId  产品ID
     * @param newName    新的产品名称
     */
    void syncAssemblyNameFromProduct(Long productId, String newName);

    /**
     * 同步项目关联产品的状态（研发中→正常）
     * 当项目阶段推进到"小批量"或"结项"时触发
     */
    void syncProductStatus(Long projectId);
}
