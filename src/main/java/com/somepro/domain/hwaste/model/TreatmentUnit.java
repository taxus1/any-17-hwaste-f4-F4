package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处置利用单位（纯领域对象）。开联单只关心它状态是否正常、能接哪些类别、
 * 落在哪个省；签收时还要盯它的许可上限与累计已接收重量，别让一家厂接爆。
 */
@Getter
@Setter
public class TreatmentUnit {

    private Long id;

    /** 处置单位编号，形如 TU-2026-0001。 */
    private String unitNo;

    private String name;

    /** 所在省份（跨省判定用）。 */
    private String province;

    /** 许可经营重量上限（千克）。 */
    private BigDecimal licensedWeight;

    /** 累计已接收重量（千克，每签收一单按地磅实收量往上加）。 */
    private BigDecimal receivedWeight;

    /** 可处置类别代码，逗号分隔（如 HW08,HW09）。 */
    private String disposes;

    private UnitStatus status;

    /** 只有正常（ACTIVE）的单位才接得了新货；停用、吊销都挡回去。 */
    public boolean isActive() {
        return this.status == UnitStatus.ACTIVE;
    }

    /** 这趟货的类别得落在对方能接的范围里。 */
    public boolean canAccept(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()
                || disposes == null || disposes.isBlank()) {
            return false;
        }
        for (String code : disposes.split(",")) {
            if (categoryCode.trim().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 许可余量核验：累计已接收重量加上这趟地磅实收重量，不该盖过许可上限。
     * 由仓储侧在签收事务的行锁内复核，并发两笔不会一起把额度接穿。
     */
    public void requireWithinLicense(BigDecimal incomingWeight) {
        if (incomingWeight == null || incomingWeight.signum() <= 0) {
            throw new BizException("实际过磅重量必须大于 0");
        }
        BigDecimal licensed = licensedWeight == null ? BigDecimal.ZERO : licensedWeight;
        BigDecimal received = receivedWeight == null ? BigDecimal.ZERO : receivedWeight;
        if (received.add(incomingWeight).compareTo(licensed) > 0) {
            throw new BizException(
                    "累计接收量加上本趟实收量将超过该处置单位的许可上限，先腾额度再签收");
        }
    }
}
