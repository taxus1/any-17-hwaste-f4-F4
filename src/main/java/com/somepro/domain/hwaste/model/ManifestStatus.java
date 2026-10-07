package com.somepro.domain.hwaste.model;

/**
 * 电子转移联单状态机（纯领域枚举，落库时存 name() 字符串）。
 *
 * 审批环节：新提交落 SUBMITTED；管事的审批要么批（→ APPROVED）要么退（→ REJECTED，
 * 退回必须写明理由）。已批过、退过、已作废的联单不再来回审。
 * 运输环节：已审批才能启运（→ IN_TRANSIT，记启运时刻）；运输中才能签收（→ RECEIVED，
 * 记地磅实收重量与签收时刻）。DISPOSED 由后续处置确认环节推进，这里先占位。
 */
public enum ManifestStatus {

    /** 已提交：联单新开出来，等审批。 */
    SUBMITTED,
    /** 已审批：管事的已批，可以启运。 */
    APPROVED,
    /** 已退回：审批没通过，带退回理由。 */
    REJECTED,
    /** 运输中。 */
    IN_TRANSIT,
    /** 已签收。 */
    RECEIVED,
    /** 已处置。 */
    DISPOSED,
    /** 已作废。 */
    VOID
}
