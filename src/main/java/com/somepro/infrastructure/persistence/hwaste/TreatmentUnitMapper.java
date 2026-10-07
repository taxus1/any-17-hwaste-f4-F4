package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.TreatmentUnitPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * t_treatment_unit 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 * 自定义 SQL 里的 del_flag = 0 要手写（@TableLogic 只自动拼 MyBatis-Plus 生成的 SQL）。
 */
@Mapper
public interface TreatmentUnitMapper extends BaseMapper<TreatmentUnitPO> {

    /**
     * 签收事务里按主键行锁取处置单位：同一家单位两笔并发签收要在锁上排队，
     * 后到的在锁里重算「累计已接收 + 本趟实收」，额度不够就整体回滚，不会接爆。
     */
    @Select("SELECT * FROM t_treatment_unit WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    TreatmentUnitPO selectByIdForUpdate(@Param("id") Long id);
}
