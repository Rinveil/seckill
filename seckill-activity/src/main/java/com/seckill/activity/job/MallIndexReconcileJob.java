package com.seckill.activity.job;

import com.seckill.activity.search.MallIndexReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 双写失败兜底：周期用 MySQL 覆盖 mall_activity 索引。 */
@Component
@ConditionalOnProperty(prefix = "seckill.schedule", name = "enabled", havingValue = "true")
public class MallIndexReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(MallIndexReconcileJob.class);

    private final MallIndexReconciler mallIndexReconciler;

    public MallIndexReconcileJob(MallIndexReconciler mallIndexReconciler) {
        this.mallIndexReconciler = mallIndexReconciler;
    }

    @Scheduled(
            initialDelayString = "${seckill.search.elasticsearch.reconcile-ms:120000}",
            fixedDelayString = "${seckill.search.elasticsearch.reconcile-ms:120000}"
    )
    public void scan() {
        try {
            mallIndexReconciler.reconcile();
        } catch (Exception ex) {
            log.warn("mall elasticsearch reconcile failed", ex);
        }
    }
}
