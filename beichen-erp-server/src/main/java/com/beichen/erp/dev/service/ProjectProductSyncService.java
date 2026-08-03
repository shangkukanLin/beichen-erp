package com.beichen.erp.dev.service;

/**
 * 项目关联产品同步服务
 */
public interface ProjectProductSyncService {

    /**
     * 同步项目关联的产品信息（产品名、供应商等）
     */
    void syncProduct(Long projectId);

    /**
     * 同步项目关联产品的状态（研发中→正常）
     * 当项目阶段推进到"小批量"或"结项"时触发
     */
    void syncProductStatus(Long projectId);
}
