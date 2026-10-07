package com.somepro.interfaces.rest.hwaste.converter;

import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.TransferManifestVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TransferManifest（领域）→ 对外 VO 转换器（用户接口层）。
 */
public final class TransferManifestVoConverter {

    private TransferManifestVoConverter() {
    }

    public static TransferManifestVO toVo(TransferManifest domain) {
        return new TransferManifestVO(
                domain.getId(),
                domain.getManifestNo(),
                domain.getPlanId(),
                domain.getSourceId(),
                domain.getUnitId(),
                domain.getCategoryCode(),
                domain.getTransporter(),
                domain.getTransferWeight(),
                domain.getReceivedWeight(),
                domain.getCrossProvince(),
                domain.getStatus() == null ? null : domain.getStatus().name(),
                domain.getTransportBegin(),
                domain.getReceiveAt(),
                domain.getCreateTime());
    }

    public static PageVO<TransferManifestVO> toPageVo(PageResult<TransferManifest> page) {
        List<TransferManifestVO> content = page.content().stream()
                .map(TransferManifestVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
