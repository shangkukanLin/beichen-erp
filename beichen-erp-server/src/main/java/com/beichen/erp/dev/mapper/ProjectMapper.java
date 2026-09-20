package com.beichen.erp.dev.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.dev.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * F7-139（2026-09-20）：**行锁**读取项目（`FOR UPDATE`）。
     *
     * <p>用于"项目维度的先查后写"串行化 —— 目前是 `ProjectProductSyncServiceImpl.syncProduct`：
     * 它按 `projectId` 查是否已有产品，查不到就新建；并发（双击/重试）时两个请求都查不到 ⇒
     * **同一项目挂出两条产品**，且 `project.product_id` 后写者胜。在项目行上串行即可闭合。</p>
     *
     * <p>**带租户条件**，理由同 `ReturnOrderMapper.selectForUpdate`：裸 `FOR UPDATE` 会绕过
     * mybatis-plus 的租户过滤。</p>
     */
    @Select("<script>SELECT * FROM dev_project WHERE id = #{id}"
            + "<if test='companyId != null'> AND company_id = #{companyId}</if> FOR UPDATE</script>")
    Project selectForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);
}
