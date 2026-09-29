package com.seckill.activity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.activity.domain.Activity;
import com.seckill.activity.mapper.ActivityMapper;
import com.seckill.activity.search.MallActivityIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(5)
public class MallIndexSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MallIndexSyncRunner.class);

    private final ActivityMapper activityMapper;
    private final MallActivityIndex mallActivityIndex;

    public MallIndexSyncRunner(ActivityMapper activityMapper, MallActivityIndex mallActivityIndex) {
        this.activityMapper = activityMapper;
        this.mallActivityIndex = mallActivityIndex;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!mallActivityIndex.enabled()) {
            log.info("mall elasticsearch disabled, skip reindex");
            return;
        }
        try {
            List<Activity> rows = activityMapper.selectList(
                    new LambdaQueryWrapper<Activity>()
                            .in(Activity::getStatus, Activity.STATUS_PREHEATED, Activity.STATUS_OPEN, Activity.STATUS_CLOSED)
            );
            mallActivityIndex.reindex(rows);
        } catch (Exception ex) {
            log.warn("mall elasticsearch reindex failed, search will fall back to MySQL", ex);
        }
    }
}
