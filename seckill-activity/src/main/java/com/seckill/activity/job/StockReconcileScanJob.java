package com.seckill.activity.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.activity.domain.Activity;
import com.seckill.activity.dto.StockReconcileView;
import com.seckill.activity.mapper.ActivityMapper;
import com.seckill.activity.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时库存对账：扫 PREHEATED/OPEN 活动，不一致只打日志告警（不自动改数）。
 * B 端「对账」按钮仍可手动查看详情。
 */
@Component
public class StockReconcileScanJob {

    private static final Logger log = LoggerFactory.getLogger(StockReconcileScanJob.class);
    private static final String SYSTEM_ADMIN = "ADMIN";

    private final ActivityMapper activityMapper;
    private final ActivityService activityService;

    public StockReconcileScanJob(ActivityMapper activityMapper, ActivityService activityService) {
        this.activityMapper = activityMapper;
        this.activityService = activityService;
    }

    @Scheduled(fixedDelayString = "${seckill.activity.reconcile-scan-ms:60000}")
    public void scan() {
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .in(Activity::getStatus, Activity.STATUS_PREHEATED, Activity.STATUS_OPEN)
                        .orderByDesc(Activity::getId)
                        .last("LIMIT 50")
        );
        int bad = 0;
        for (Activity a : list) {
            StockReconcileView view = activityService.reconcile(SYSTEM_ADMIN, a.getId());
            if (!view.consistent()) {
                bad++;
                log.warn("stock reconcile mismatch id={} status={} msg={}",
                        view.activityId(), view.status(), view.message());
            }
        }
        if (!list.isEmpty()) {
            log.info("stock reconcile scan checked={} mismatch={}", list.size(), bad);
        }
    }
}
