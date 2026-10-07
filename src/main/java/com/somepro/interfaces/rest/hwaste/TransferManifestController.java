package com.somepro.interfaces.rest.hwaste;

import com.somepro.application.hwaste.TransferManifestAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hwaste.converter.TransferManifestVoConverter;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.TransferManifestVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 电子转移联单接口（用户接口层）：提交、审批、退回、启运、签收、详情与多条件翻页。
 *
 * 状态流转类接口的联单定位参数：manifestId 或 manifestNo 传其一即可。
 */
@RestController
@RequestMapping("/api/hwaste/manifest")
public class TransferManifestController {

    private final TransferManifestAppService transferManifestAppService;

    public TransferManifestController(TransferManifestAppService transferManifestAppService) {
        this.transferManifestAppService = transferManifestAppService;
    }

    /**
     * 提交联单：得有一份对得上的已批复年度计划（同单位 + 同类别 + 同年度，planYear 不传默认当年），
     * 额度不超批复总量；处置单位得正常且能接该类别；名录限制跨省的类别不得跨省开单。
     * 新提交的一律落 SUBMITTED，联单编号由系统分配。
     */
    @PostMapping("/submit")
    public Mono<Result<TransferManifestVO>> submit(@RequestParam(required = false) Long sourceId,
                                                   @RequestParam(required = false) String categoryCode,
                                                   @RequestParam(required = false) Long unitId,
                                                   @RequestParam(required = false) String transporter,
                                                   @RequestParam(required = false) BigDecimal transferWeight,
                                                   @RequestParam(required = false) Integer planYear) {
        return transferManifestAppService.submit(sourceId, categoryCode, unitId, transporter, transferWeight, planYear)
                .map(TransferManifestVoConverter::toVo)
                .map(Result::ok);
    }

    /** 审批：已提交 → 已审批；已批过 / 退过 / 作废的不能重复审批。 */
    @PostMapping("/approve")
    public Mono<Result<TransferManifestVO>> approve(@RequestParam(required = false) Long manifestId,
                                                    @RequestParam(required = false) String manifestNo) {
        return transferManifestAppService.approve(manifestId, manifestNo)
                .map(TransferManifestVoConverter::toVo)
                .map(Result::ok);
    }

    /** 退回：已提交 → 已退回，必须写明退回理由。 */
    @PostMapping("/reject")
    public Mono<Result<TransferManifestVO>> reject(@RequestParam(required = false) Long manifestId,
                                                   @RequestParam(required = false) String manifestNo,
                                                   @RequestParam(required = false) String reason) {
        return transferManifestAppService.reject(manifestId, manifestNo, reason)
                .map(TransferManifestVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 启运：已审批 → 运输中，记启运时刻。
     * 还在提交、被退回、已在途 / 已签收 / 已处置的都启不动，重复启运被挡回。
     */
    @PostMapping("/begin-transport")
    public Mono<Result<TransferManifestVO>> beginTransport(@RequestParam(required = false) Long manifestId,
                                                           @RequestParam(required = false) String manifestNo) {
        return transferManifestAppService.beginTransport(manifestId, manifestNo)
                .map(TransferManifestVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 签收：运输中 → 已签收。receivedWeight 传车到厂里地磅过出来的实收重量（千克），
     * 入库账认这个数，不拿申报量顶替；同时记签收时刻。
     * 处置单位累计已接收量 + 本趟实收量若盖过许可上限会被挡下；
     * 同一张联单重复签收幂等空转，第二下原样返回、不重复记账。
     */
    @PostMapping("/receive")
    public Mono<Result<TransferManifestVO>> receive(@RequestParam(required = false) Long manifestId,
                                                    @RequestParam(required = false) String manifestNo,
                                                    @RequestParam(required = false) BigDecimal receivedWeight) {
        return transferManifestAppService.receive(manifestId, manifestNo, receivedWeight)
                .map(TransferManifestVoConverter::toVo)
                .map(Result::ok);
    }

    /** 联单详情：manifestId 或 manifestNo 传其一。 */
    @GetMapping("/detail")
    public Mono<Result<TransferManifestVO>> detail(@RequestParam(required = false) Long manifestId,
                                                   @RequestParam(required = false) String manifestNo) {
        return transferManifestAppService.detail(manifestId, manifestNo)
                .map(TransferManifestVoConverter::toVo)
                .map(Result::ok);
    }

    /** 分页查询：计划 / 单位 / 类别 / 处置单位 / 状态均可选，都不传则分页列全；每行带联单号。 */
    @GetMapping("/page")
    public Mono<Result<PageVO<TransferManifestVO>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         @RequestParam(required = false) Long planId,
                                                         @RequestParam(required = false) Long sourceId,
                                                         @RequestParam(required = false) String categoryCode,
                                                         @RequestParam(required = false) Long unitId,
                                                         @RequestParam(required = false) String status) {
        return transferManifestAppService.page(pageNum, pageSize, planId, sourceId, categoryCode, unitId, status)
                .map(TransferManifestVoConverter::toPageVo)
                .map(Result::ok);
    }
}
