package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 电子转移联单仓储端口：领域层定义，基础设施层实现。
 *
 * 提交的「额度复核 + 编号分配 + 插入」要保证并发安全，由实现侧用锁兜底
 * （同一计划下两笔几乎同时提交，额度不能被一起用穿；同一个联单号只成一单）。
 * 审批 / 退回 / 启运用条件更新落库：只认当前状态，后到的请求更新 0 行即拒。
 * 签收是「处置单位行锁 + 联单行锁 + 条件更新 + 写签收记录 + 累加累计量」一个事务，
 * 许可余量在锁内复核，重复签收幂等空转。
 */
public interface TransferManifestRepository {

    /**
     * 提交联单：先在锁内复核额度（该计划下已开出量 + 本趟量不得盖过 approvedWeight），
     * 再分配联单编号（EM-年份-序号）并落库，状态落 SUBMITTED。
     */
    Mono<TransferManifest> create(TransferManifest manifest, BigDecimal approvedWeight);

    Mono<TransferManifest> findById(Long id);

    Mono<TransferManifest> findByManifestNo(String manifestNo);

    /** 审批：条件更新仅 SUBMITTED → APPROVED；已审过 / 退过 / 作废的更新 0 行。 */
    Mono<TransferManifest> approve(TransferManifest manifest);

    /** 退回：条件更新仅 SUBMITTED → REJECTED；已审过 / 退过 / 作废的更新 0 行。 */
    Mono<TransferManifest> reject(TransferManifest manifest);

    /**
     * 启运：条件更新仅 APPROVED → IN_TRANSIT，同时落启运时刻（transport_begin）；
     * 还在提交、被退回、已在途 / 已签收 / 已处置的更新 0 行，后到的请求直接拒掉。
     */
    Mono<TransferManifest> beginTransport(TransferManifest manifest);

    /**
     * 签收（一个事务）：处置单位行锁 + 联单行锁内复核许可余量（累计已接收 + 本趟实收
     * 不得盖过许可上限），条件更新仅 IN_TRANSIT → RECEIVED（落 receive_at），
     * 写一条签收记录（地磅实收量），再把实收量累加到处置单位累计已接收重量。
     * 联单已签收（或已走完）时幂等返回现状，不另立记录、不加第二次账。
     */
    Mono<TransferManifest> receive(TransferManifest manifest, BigDecimal receivedWeight);

    /** 多条件分页：计划 / 单位 / 类别 / 处置单位 / 状态均可选，一个都不传则分页列全；每行带联单号。 */
    Mono<PageResult<TransferManifest>> page(int pageNum, int pageSize, Long planId, Long sourceId,
                                            String categoryCode, Long unitId, String status);
}
