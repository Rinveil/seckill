package com.seckill.activity.config;

import com.seckill.activity.search.MallActivityIndex;
import com.seckill.activity.search.MallIndexReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class MallIndexSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MallIndexSyncRunner.class);

    private final MallActivityIndex mallActivityIndex;
    private final MallIndexReconciler mallIndexReconciler;

    public MallIndexSyncRunner(MallActivityIndex mallActivityIndex, MallIndexReconciler mallIndexReconciler) {
        this.mallActivityIndex = mallActivityIndex;
        this.mallIndexReconciler = mallIndexReconciler;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!mallActivityIndex.enabled()) {
            log.info("mall elasticsearch disabled, skip reindex");
            return;
        }
        try {
            mallIndexReconciler.reconcile();
        } catch (Exception ex) {
            log.warn("mall elasticsearch reindex failed, search will fall back to MySQL", ex);
        }
    }
}
