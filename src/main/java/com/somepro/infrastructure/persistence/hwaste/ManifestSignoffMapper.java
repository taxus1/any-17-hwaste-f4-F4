package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.ManifestSignoffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * t_manifest_signoff 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 *
 * 编号取数说明同联单：MAX 查询故意不过滤 del_flag —— 已删除签收单的编号也不许复用；
 * FOR UPDATE 走当前已提交数据，避免事务快照里读到旧的最大号。
 * 自定义 SQL 里的 del_flag = 0 要手写（@TableLogic 只自动拼 MyBatis-Plus 生成的 SQL）。
 */
@Mapper
public interface ManifestSignoffMapper extends BaseMapper<ManifestSignoffPO> {

    @Select("SELECT MAX(signoff_no) FROM t_manifest_signoff WHERE signoff_no LIKE CONCAT(#{prefix}, '%') FOR UPDATE")
    String maxSignoffNo(@Param("prefix") String prefix);
}
