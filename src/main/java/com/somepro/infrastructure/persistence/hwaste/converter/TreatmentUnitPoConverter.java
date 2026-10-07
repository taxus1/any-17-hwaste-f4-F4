package com.somepro.infrastructure.persistence.hwaste.converter;

import com.somepro.domain.hwaste.model.TreatmentUnit;
import com.somepro.domain.hwaste.model.UnitStatus;
import com.somepro.infrastructure.persistence.hwaste.po.TreatmentUnitPO;

/**
 * TreatmentUnitPO（表）→ TreatmentUnit（领域）转换器（基础设施层）。
 * 只做表 → 领域读取：开联单校验状态与接货范围，签收时还要带许可上限与累计已接收重量；
 * 累计重量的累加在签收事务里走 SQL，不需要反向转换。
 */
public final class TreatmentUnitPoConverter {

    private TreatmentUnitPoConverter() {
    }

    public static TreatmentUnit toDomain(TreatmentUnitPO po) {
        TreatmentUnit domain = new TreatmentUnit();
        domain.setId(po.getId());
        domain.setUnitNo(po.getUnitNo());
        domain.setName(po.getName());
        domain.setProvince(po.getProvince());
        domain.setLicensedWeight(po.getLicensedWeight());
        domain.setReceivedWeight(po.getReceivedWeight());
        domain.setDisposes(po.getDisposes());
        domain.setStatus(po.getStatus() == null ? null : UnitStatus.valueOf(po.getStatus()));
        return domain;
    }
}
