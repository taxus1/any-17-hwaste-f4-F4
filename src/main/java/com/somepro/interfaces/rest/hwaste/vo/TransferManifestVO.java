package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子转移联单对外返回对象（VO，用户接口层）—— 不可变 record。
 * 列表与详情共用；delFlag / createBy / updateBy / updateTime 不进 API 契约。
 *
 * 申报量（transferWeight）与地磅实收量（receivedWeight）分列：申报量是开单时报的数，
 * 实收量是货到对方过磅的数，两数往往对不齐，入库账以实收量为准。
 */
public record TransferManifestVO(
        Long id,
        String manifestNo,
        Long planId,
        Long sourceId,
        Long unitId,
        String categoryCode,
        String transporter,
        BigDecimal transferWeight,
        BigDecimal receivedWeight,
        Integer crossProvince,
        String status,
        LocalDateTime transportBegin,
        LocalDateTime receiveAt,
        LocalDateTime createTime) implements Serializable {
}
