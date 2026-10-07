package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.TransferManifestPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * t_transfer_manifest 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 *
 * 编号取数说明：MAX 查询故意不过滤 del_flag —— 已删除联单的编号也不许复用；
 * FOR UPDATE 走当前已提交数据，避免事务快照里读到旧的最大号。
 * 自定义 SQL 里的 del_flag = 0 要手写（@TableLogic 只自动拼 MyBatis-Plus 生成的 SQL）。
 */
@Mapper
public interface TransferManifestMapper extends BaseMapper<TransferManifestPO> {

    @Select("SELECT MAX(manifest_no) FROM t_transfer_manifest WHERE manifest_no LIKE CONCAT(#{prefix}, '%') FOR UPDATE")
    String maxManifestNo(@Param("prefix") String prefix);

    /**
     * 该计划下已开出去的联单量合计（占额度用）：退回（REJECTED）与作废（VOID）的单子
     * 货没走成，不占额度；其余状态（已提交 / 已审批 / 在途 / 已签收 / 已处置）都计入。
     */
    @Select("SELECT IFNULL(SUM(transfer_weight), 0) FROM t_transfer_manifest "
            + "WHERE del_flag = 0 AND plan_id = #{planId} AND status NOT IN ('REJECTED', 'VOID')")
    BigDecimal sumTransferWeight(@Param("planId") Long planId);

    /**
     * 签收事务里按主键行锁取联单：签收推进状态得看到最新状态，
     * 同一张单两笔并发签收时后到的在锁里直接走幂等空转，不会签第二回。
     */
    @Select("SELECT * FROM t_transfer_manifest WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    TransferManifestPO selectByIdForUpdate(@Param("id") Long id);
}
