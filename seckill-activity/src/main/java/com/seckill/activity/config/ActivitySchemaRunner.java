package com.seckill.activity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ActivitySchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActivitySchemaRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public ActivitySchemaRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureActivityTable();
        log.info("t_activity schema ready");
    }

    private void ensureActivityTable() {
        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS t_activity (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            title VARCHAR(128) NOT NULL,
                            price_fen INT NOT NULL,
                            origin_price_fen INT NOT NULL,
                            stock INT NOT NULL,
                            status TINYINT NOT NULL DEFAULT 0,
                            start_at DATETIME NOT NULL,
                            end_at DATETIME NOT NULL
                        )
                        """
        );
        Integer hasStatus = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 't_activity' AND column_name = 'status'
                        """,
                Integer.class
        );
        if (hasStatus != null && hasStatus == 0) {
            log.warn("adding status column to existing t_activity");
            jdbcTemplate.execute("ALTER TABLE t_activity ADD COLUMN status TINYINT NOT NULL DEFAULT 0");
        }
    }
}
