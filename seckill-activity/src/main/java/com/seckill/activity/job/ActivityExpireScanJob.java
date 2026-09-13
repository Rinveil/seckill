package com.seckill.activity.job;

import com.seckill.activity.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 延迟队列兜底：扫已过 end_at 仍 OPEN 的活动。 */
@Component
public class ActivityExpireScanJob {

    private static final Logger log = LoggerFactory.getLogger(ActivityExpireScanJob.class);

    private final ActivityService activityService;

    public ActivityExpireScanJob(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Scheduled(fixedDelayString = "${seckill.activity.expire-scan-ms:30000}")
    public void scan() {
        int n = activityService.closeOverdueBatch(50);
        if (n > 0) {
            log.info("activity expire scan closed {} activities", n);
        }
    }
}
