package com.seckill.user.config;

import com.seckill.user.support.UserDisabledStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动时把 DB 禁用账号同步到 Redis，避免网关仍放行旧 Token。 */
@Component
@Order(3)
public class UserDisabledFlagSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserDisabledFlagSyncRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserDisabledStore userDisabledStore;

    public UserDisabledFlagSyncRunner(JdbcTemplate jdbcTemplate, UserDisabledStore userDisabledStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDisabledStore = userDisabledStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Long> ids = jdbcTemplate.query(
                    "SELECT id FROM t_user WHERE status = 0",
                    (rs, rowNum) -> rs.getLong("id")
            );
            userDisabledStore.replaceAll(ids);
        } catch (RuntimeException ex) {
            log.warn("sync disabled users to Redis failed, gateway will fail-open", ex);
        }
    }
}
