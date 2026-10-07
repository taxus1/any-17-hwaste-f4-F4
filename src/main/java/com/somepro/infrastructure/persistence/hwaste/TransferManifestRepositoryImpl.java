package com.somepro.infrastructure.persistence.hwaste;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.model.ManifestStatus;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.hwaste.model.TreatmentUnit;
import com.somepro.domain.hwaste.repository.TransferManifestRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.ManifestSignoffPoConverter;
import com.somepro.infrastructure.persistence.hwaste.converter.TransferManifestPoConverter;
import com.somepro.infrastructure.persistence.hwaste.converter.TreatmentUnitPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.ManifestSignoffPO;
import com.somepro.infrastructure.persistence.hwaste.po.TransferManifestPO;
import com.somepro.infrastructure.persistence.hwaste.po.TreatmentUnitPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 电子转移联单仓储适配器（基础设施层）。
 *
 * 提交的「额度复核 + 取号 + 插入」包在同一把 EM 锁里：同一计划下两笔几乎同时提交，
 * 后到的在锁里重算已开出量，额度不够就挡回去，不会一起把额度用穿；
 * 同一个联单号也只成一单，库表 uk_manifest_no 唯一约束是最后兜底。
 * 审批 / 退回 / 启运走条件更新（UPDATE ... WHERE id=? AND status=?），
 * 更新 0 行说明状态已被别人推走，后到的请求直接拒掉。
 *
 * 签收是「处置单位行锁 + 联单行锁 + 条件更新 + 写签收记录 + 累加累计量」一个事务：
 * 锁序固定先锁处置单位再锁联单，同一家单位的并发签收在锁上排队，许可余量在锁内复核；
 * 同一张联单重复签收（手快点两下）在锁里看到已不是 IN_TRANSIT，幂等返回现状，
 * 不另立签收记录、不加第二次账。
 */
@Repository
public class TransferManifestRepositoryImpl extends BaseBlockingRepository implements TransferManifestRepository {

    private final TransferManifestMapper transferManifestMapper;
    private final ManifestSignoffMapper manifestSignoffMapper;
    private final TreatmentUnitMapper treatmentUnitMapper;
    private final BizNoService bizNoService;
    private final TransactionTemplate txTemplate;

    public TransferManifestRepositoryImpl(TransferManifestMapper transferManifestMapper,
                                          ManifestSignoffMapper manifestSignoffMapper,
                                          TreatmentUnitMapper treatmentUnitMapper,
                                          BizNoService bizNoService,
                                          PlatformTransactionManager transactionManager) {
        this.transferManifestMapper = transferManifestMapper;
        this.manifestSignoffMapper = manifestSignoffMapper;
        this.treatmentUnitMapper = treatmentUnitMapper;
        this.bizNoService = bizNoService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Mono<TransferManifest> create(TransferManifest manifest, BigDecimal approvedWeight) {
        return blocking(() -> bizNoService.inLock("EM", () -> {
            // 锁内复核额度：已开出量 + 本趟量不得盖过计划批复总量
            BigDecimal used = transferManifestMapper.sumTransferWeight(manifest.getPlanId());
            manifest.requireWithinQuota(approvedWeight, used);
            TransferManifestPO po = TransferManifestPoConverter.toPo(manifest);
            po.setId(IdUtil.getSnowflakeNextId());
            po.setManifestNo(bizNoService.nextManifestNo());
            transferManifestMapper.insert(po);
            return TransferManifestPoConverter.toDomain(po);
        }));
    }

    @Override
    public Mono<TransferManifest> findById(Long id) {
        return blocking(() -> loadEnriched(id));
    }

    @Override
    public Mono<TransferManifest> findByManifestNo(String manifestNo) {
        return blocking(() -> {
            TransferManifestPO po = transferManifestMapper.selectOne(Wrappers.<TransferManifestPO>lambdaQuery()
                    .eq(TransferManifestPO::getManifestNo, manifestNo));
            return po == null ? null : fillReceivedWeight(TransferManifestPoConverter.toDomain(po));
        });
    }

    @Override
    public Mono<TransferManifest> approve(TransferManifest manifest) {
        return blocking(() -> transition(manifest, ManifestStatus.APPROVED.name(),
                "联单已审批或已退回，不能重复审批"));
    }

    @Override
    public Mono<TransferManifest> reject(TransferManifest manifest) {
        return blocking(() -> transition(manifest, ManifestStatus.REJECTED.name(),
                "联单已审批或已退回，不能重复退回"));
    }

    @Override
    public Mono<TransferManifest> beginTransport(TransferManifest manifest) {
        return blocking(() -> {
            // 条件更新：只有 APPROVED 能推到 IN_TRANSIT，启运时刻一并落库；
            // 还在提交、被退回、已在途 / 已签收 / 已处置的更新 0 行，后到的直接拒
            TransferManifestPO patch = new TransferManifestPO();
            patch.setStatus(ManifestStatus.IN_TRANSIT.name());
            patch.setTransportBegin(manifest.getTransportBegin());
            int rows = transferManifestMapper.update(patch, Wrappers.<TransferManifestPO>lambdaUpdate()
                    .eq(TransferManifestPO::getId, manifest.getId())
                    .eq(TransferManifestPO::getStatus, ManifestStatus.APPROVED.name()));
            if (rows == 0) {
                throw new BizException("只有已审批的联单才能启运，不能重复启运");
            }
            return loadEnriched(manifest.getId());
        });
    }

    @Override
    public Mono<TransferManifest> receive(TransferManifest requested, BigDecimal receivedWeight) {
        return blocking(() -> txTemplate.execute(tx -> {
            // 锁序固定先处置单位后联单，并发签收按同一顺序取锁，避免互相死锁
            TreatmentUnitPO unitPo = treatmentUnitMapper.selectByIdForUpdate(requested.getUnitId());
            if (unitPo == null) {
                throw new BizException("处置单位不存在");
            }
            TransferManifestPO currentPo = transferManifestMapper.selectByIdForUpdate(requested.getId());
            if (currentPo == null) {
                throw new BizException("转移联单不存在");
            }
            TransferManifest current = TransferManifestPoConverter.toDomain(currentPo);

            // 幂等：已经签收（或已走完）的单子再点签收，不另立记录、不加第二次账，原样返回
            if (current.getStatus() == ManifestStatus.RECEIVED
                    || current.getStatus() == ManifestStatus.DISPOSED) {
                return fillReceivedWeight(current);
            }
            // 货还没出门（SUBMITTED / APPROVED / REJECTED / VOID）不许直接签收；状态不符直接挡
            current.receive(receivedWeight);

            // 锁内复核许可余量：累计已接收 + 本趟地磅实收，不得盖过许可上限
            TreatmentUnit unit = TreatmentUnitPoConverter.toDomain(unitPo);
            unit.requireWithinLicense(receivedWeight);

            // 条件更新联单：只有 IN_TRANSIT 能推到 RECEIVED，落签收时刻
            TransferManifestPO patch = new TransferManifestPO();
            patch.setStatus(ManifestStatus.RECEIVED.name());
            patch.setReceiveAt(current.getReceiveAt());
            int rows = transferManifestMapper.update(patch, Wrappers.<TransferManifestPO>lambdaUpdate()
                    .eq(TransferManifestPO::getId, current.getId())
                    .eq(TransferManifestPO::getStatus, ManifestStatus.IN_TRANSIT.name()));
            if (rows == 0) {
                throw new BizException("联单状态已变化，签收失败");
            }

            // 写签收记录：认地磅实收重量，一单一条（idx_manifest 上的业务约定 + 状态闸双保险）
            ManifestSignoff signoff = ManifestSignoff.of(current);
            ManifestSignoffPO signoffPo = ManifestSignoffPoConverter.toPo(signoff);
            signoffPo.setId(IdUtil.getSnowflakeNextId());
            bizNoService.inLock("SO", () -> {
                signoffPo.setSignoffNo(bizNoService.nextSignoffNo());
                manifestSignoffMapper.insert(signoffPo);
                return null;
            });

            // 累计已接收重量按地磅实收往上加，SQL 里再带一道许可上限条件兜底（0 行说明被并发顶穿）
            int unitRows = treatmentUnitMapper.update(null, Wrappers.<TreatmentUnitPO>lambdaUpdate()
                    .setSql("received_weight = received_weight + {0}", receivedWeight)
                    .eq(TreatmentUnitPO::getId, unit.getId())
                    .apply("received_weight + {0} <= licensed_weight", receivedWeight));
            if (unitRows == 0) {
                throw new BizException("处置单位许可余量不足，签收被拦下，先腾额度");
            }

            current.setReceivedWeight(receivedWeight);
            return current;
        }));
    }

    @Override
    public Mono<PageResult<TransferManifest>> page(int pageNum, int pageSize, Long planId, Long sourceId,
                                                   String categoryCode, Long unitId, String status) {
        return this.<PageResult<TransferManifest>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<TransferManifestPO> wrapper = Wrappers.<TransferManifestPO>lambdaQuery()
                        .eq(planId != null, TransferManifestPO::getPlanId, planId)
                        .eq(sourceId != null, TransferManifestPO::getSourceId, sourceId)
                        .eq(categoryCode != null && !categoryCode.isBlank(),
                                TransferManifestPO::getCategoryCode, categoryCode)
                        .eq(unitId != null, TransferManifestPO::getUnitId, unitId)
                        .eq(status != null && !status.isBlank(), TransferManifestPO::getStatus, status)
                        .orderByDesc(TransferManifestPO::getId);
                List<TransferManifestPO> rows = transferManifestMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<TransferManifest> content = fillReceivedWeight(rows.stream()
                        .map(TransferManifestPoConverter::toDomain)
                        .collect(Collectors.toList()));
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    /** 通用状态条件更新：只有 SUBMITTED 能推到目标状态，更新 0 行即已被别人审过。 */
    private TransferManifest transition(TransferManifest manifest, String targetStatus, String conflictMsg) {
        TransferManifestPO patch = new TransferManifestPO();
        patch.setStatus(targetStatus);
        int rows = transferManifestMapper.update(patch, Wrappers.<TransferManifestPO>lambdaUpdate()
                .eq(TransferManifestPO::getId, manifest.getId())
                .eq(TransferManifestPO::getStatus, ManifestStatus.SUBMITTED.name()));
        if (rows == 0) {
            throw new BizException(conflictMsg);
        }
        return loadEnriched(manifest.getId());
    }

    /** 按主键取联单并回填地磅实收重量；查不到返回 null。 */
    private TransferManifest loadEnriched(Long id) {
        TransferManifestPO po = transferManifestMapper.selectById(id);
        return po == null ? null : fillReceivedWeight(TransferManifestPoConverter.toDomain(po));
    }

    /** 单条回填：把签收记录上的地磅实收量补到领域对象。 */
    private TransferManifest fillReceivedWeight(TransferManifest domain) {
        ManifestSignoffPO signoffPo = manifestSignoffMapper.selectOne(
                Wrappers.<ManifestSignoffPO>lambdaQuery()
                        .eq(ManifestSignoffPO::getManifestId, domain.getId())
                        .orderByDesc(ManifestSignoffPO::getId)
                        .last("LIMIT 1"));
        if (signoffPo != null) {
            domain.setReceivedWeight(signoffPo.getReceivedWeight());
        }
        return domain;
    }

    /** 批量回填：一次查回这批联单的签收记录，按 manifestId 对上实收量，避免逐行查库。 */
    private List<TransferManifest> fillReceivedWeight(List<TransferManifest> domains) {
        if (domains.isEmpty()) {
            return domains;
        }
        List<Long> ids = domains.stream().map(TransferManifest::getId).collect(Collectors.toList());
        List<ManifestSignoffPO> signoffs = manifestSignoffMapper.selectList(
                Wrappers.<ManifestSignoffPO>lambdaQuery()
                        .in(ManifestSignoffPO::getManifestId, ids)
                        .orderByDesc(ManifestSignoffPO::getId));
        Map<Long, BigDecimal> weightByManifest = signoffs.stream()
                .collect(Collectors.toMap(ManifestSignoffPO::getManifestId,
                        ManifestSignoffPO::getReceivedWeight, (first, second) -> first));
        domains.forEach(d -> d.setReceivedWeight(weightByManifest.get(d.getId())));
        return domains;
    }
}
