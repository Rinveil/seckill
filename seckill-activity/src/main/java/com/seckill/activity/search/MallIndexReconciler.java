package com.seckill.activity.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.activity.domain.Activity;
import com.seckill.activity.mapper.ActivityMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** 用 MySQL 可见活动覆盖 ES：补漏写、删 DRAFT/已删残留。失败只打日志。 */
@Component
public class MallIndexReconciler {

    private final ActivityMapper activityMapper;
    private final MallActivityIndex mallActivityIndex;

    public MallIndexReconciler(ActivityMapper activityMapper, MallActivityIndex mallActivityIndex) {
        this.activityMapper = activityMapper;
        this.mallActivityIndex = mallActivityIndex;
    }

    public void reconcile() {
        if (!mallActivityIndex.enabled()) {
            return;
        }
        List<Activity> rows = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .in(Activity::getStatus, Activity.STATUS_PREHEATED, Activity.STATUS_OPEN, Activity.STATUS_CLOSED)
        );
        mallActivityIndex.reindex(rows);
    }
}
