package com.somepro.infrastructure.persistence.hwaste;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 业务编号分配器（基础设施层）：CK（盘点单）/ AJ（调账流水）/ WB（入库批次）/ TP（年度转移计划）/
 * EM（电子转移联单）/ SO（联单签收单），形如 CK-2026-0001，按「前缀-年份-四位序号」递增，全局唯一。
 *
 * 并发约定：编号「取号 + 落库」必须包在同一把 JVM 锁里（见各仓储适配器的 inLock 用法），
 * 否则两个线程会拿到同一个号；库表唯一约束（uk_check_no / uk_adjust_no / uk_batch_no / uk_plan_no /
 * uk_manifest_no / uk_signoff_no）是最后兜底。取号 SQL 用 FOR UPDATE 读当前已提交的最大号，避免事务快照读到旧值。
 */
@Component
public class BizNoService {

    /** 按业务前缀分锁：不同前缀互不阻塞，同前缀串行取号。 */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final StockCheckMapper stockCheckMapper;
    private final StockAdjustMapper stockAdjustMapper;
    private final WasteStockMapper wasteStockMapper;
    private final TransferPlanMapper transferPlanMapper;
    private final TransferManifestMapper transferManifestMapper;
    private final ManifestSignoffMapper manifestSignoffMapper;

    public BizNoService(StockCheckMapper stockCheckMapper, StockAdjustMapper stockAdjustMapper,
                        WasteStockMapper wasteStockMapper, TransferPlanMapper transferPlanMapper,
                        TransferManifestMapper transferManifestMapper,
                        ManifestSignoffMapper manifestSignoffMapper) {
        this.stockCheckMapper = stockCheckMapper;
        this.stockAdjustMapper = stockAdjustMapper;
        this.wasteStockMapper = wasteStockMapper;
        this.transferPlanMapper = transferPlanMapper;
        this.transferManifestMapper = transferManifestMapper;
        this.manifestSignoffMapper = manifestSignoffMapper;
    }

    /** 在指定前缀的锁里执行一段「取号 + 落库」临界区。 */
    public <T> T inLock(String bizPrefix, Supplier<T> body) {
        ReentrantLock lock = locks.computeIfAbsent(bizPrefix, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }

    /** 下一个盘点单编号，形如 CK-2026-0001。 */
    public String nextCheckNo() {
        String prefix = yearPrefix("CK");
        return next(prefix, stockCheckMapper.maxCheckNo(prefix));
    }

    /** 下一个调账流水号，形如 AJ-2026-0001。 */
    public String nextAdjustNo() {
        String prefix = yearPrefix("AJ");
        return next(prefix, stockAdjustMapper.maxAdjustNo(prefix));
    }

    /** 下一个入库批次号，形如 WB-2026-0001。 */
    public String nextBatchNo() {
        String prefix = yearPrefix("WB");
        return next(prefix, wasteStockMapper.maxBatchNo(prefix));
    }

    /**
     * 下一个年度转移计划编号，形如 TP-2026-0001。
     * 注意编号里的年份取「计划年度」（year 入参），不是取号当下的自然年 ——
     * 2026 年立的 2027 年度计划仍编号 TP-2027-xxxx。
     */
    public String nextPlanNo(int year) {
        String prefix = "TP-" + year + "-";
        return next(prefix, transferPlanMapper.maxPlanNo(prefix));
    }

    /** 下一个联单编号，形如 EM-2026-0001，年份取提交当下的自然年。 */
    public String nextManifestNo() {
        String prefix = yearPrefix("EM");
        return next(prefix, transferManifestMapper.maxManifestNo(prefix));
    }

    /** 下一个联单签收单编号，形如 SO-2026-0001，年份取签收当下的自然年。 */
    public String nextSignoffNo() {
        String prefix = yearPrefix("SO");
        return next(prefix, manifestSignoffMapper.maxSignoffNo(prefix));
    }

    private String yearPrefix(String bizPrefix) {
        return bizPrefix + "-" + LocalDate.now().getYear() + "-";
    }

    private String next(String prefix, String maxNo) {
        int seq = 0;
        if (maxNo != null && maxNo.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(maxNo.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                // 历史数据里有异形编号时从 1 重新排，唯一约束兜底
                seq = 0;
            }
        }
        return prefix + String.format("%04d", seq + 1);
    }
}
