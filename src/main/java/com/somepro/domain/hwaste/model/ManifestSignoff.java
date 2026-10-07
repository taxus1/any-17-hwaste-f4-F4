package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 联单签收记录（纯领域实体，无框架注解）。
 *
 * 车到处置单位过磅签收后落一条：认的是地磅实收重量（receivedWeight），
 * 不是联单上的申报转移量 —— 两数往往对不齐，最后入库的账以过磅实收为准。
 * 签收单编号形如 SO-2026-0001，由仓储层分配，全局唯一。
 * 同一张联单只该有一条签收记录：签收是幂等动作，手快点两下第二下不另立记录、不动账。
 */
@Getter
@Setter
public class ManifestSignoff extends BaseEntity {

    private Long id;

    /** 签收单编号，全局唯一，形如 SO-2026-0001（由仓储层分配）。 */
    private String signoffNo;

    /** 签收的联单 id（t_transfer_manifest.id）。 */
    private Long manifestId;

    /** 实际过磅签收重量（千克）：地磅过多少记多少，入库账以这个数为准。 */
    private BigDecimal receivedWeight;

    /** 签收时刻（沿用联单落签收的同一时刻，一趟货一个点）。 */
    private LocalDateTime signAt;

    /**
     * 按联单签收结果立签收记录：联单得已走完 {@link TransferManifest#receive}，
     * 过磅重量与签收时刻都从联单上取，保证单子上的点和签收单对得上。
     */
    public static ManifestSignoff of(TransferManifest manifest) {
        if (manifest.getId() == null) {
            throw new BizException("联单未落库，不能签收");
        }
        if (manifest.getReceivedWeight() == null) {
            throw new BizException("实际过磅重量未落上，不能签收");
        }
        if (manifest.getReceiveAt() == null) {
            throw new BizException("签收时刻未落上，不能签收");
        }
        ManifestSignoff signoff = new ManifestSignoff();
        signoff.setManifestId(manifest.getId());
        signoff.setReceivedWeight(manifest.getReceivedWeight());
        signoff.setSignAt(manifest.getReceiveAt());
        return signoff;
    }
}
