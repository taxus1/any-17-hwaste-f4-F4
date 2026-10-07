package com.somepro.infrastructure.persistence.hwaste.converter;

import com.somepro.domain.hwaste.model.ManifestStatus;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.infrastructure.persistence.hwaste.po.TransferManifestPO;

/**
 * TransferManifestPO（表）↔ TransferManifest（领域）转换器（基础设施层）。
 * 状态在库里存字符串，在领域里是枚举，互转在这里收口。
 *
 * 注意：地磅实收重量不落在联单表上（在 t_manifest_signoff），领域 receivedWeight
 * 不参与这里的映射，由仓储适配器读取时另行回填。
 */
public final class TransferManifestPoConverter {

    private TransferManifestPoConverter() {
    }

    public static TransferManifestPO toPo(TransferManifest domain) {
        TransferManifestPO po = new TransferManifestPO();
        po.setId(domain.getId());
        po.setManifestNo(domain.getManifestNo());
        po.setPlanId(domain.getPlanId());
        po.setSourceId(domain.getSourceId());
        po.setUnitId(domain.getUnitId());
        po.setCategoryCode(domain.getCategoryCode());
        po.setTransporter(domain.getTransporter());
        po.setTransferWeight(domain.getTransferWeight());
        po.setCrossProvince(domain.getCrossProvince());
        po.setTransportBegin(domain.getTransportBegin());
        po.setReceiveAt(domain.getReceiveAt());
        po.setStatus(domain.getStatus() == null ? null : domain.getStatus().name());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static TransferManifest toDomain(TransferManifestPO po) {
        TransferManifest domain = new TransferManifest();
        domain.setId(po.getId());
        domain.setManifestNo(po.getManifestNo());
        domain.setPlanId(po.getPlanId());
        domain.setSourceId(po.getSourceId());
        domain.setUnitId(po.getUnitId());
        domain.setCategoryCode(po.getCategoryCode());
        domain.setTransporter(po.getTransporter());
        domain.setTransferWeight(po.getTransferWeight());
        domain.setCrossProvince(po.getCrossProvince());
        domain.setTransportBegin(po.getTransportBegin());
        domain.setReceiveAt(po.getReceiveAt());
        domain.setStatus(po.getStatus() == null ? null : ManifestStatus.valueOf(po.getStatus()));
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
