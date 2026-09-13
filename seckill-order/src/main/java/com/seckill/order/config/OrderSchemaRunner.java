package com.seckill.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class OrderSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderSchemaRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public OrderSchemaRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS t_order (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            order_no VARCHAR(64) NOT NULL UNIQUE,
                            user_id BIGINT NOT NULL,
                            activity_id BIGINT NOT NULL,
                            amount_fen INT NOT NULL,
                            status VARCHAR(32) NOT NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            expire_at DATETIME NULL
                        )
                        """
        );
        Integer hasExpire = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 't_order' AND column_name = 'expire_at'
                        """,
                Integer.class
        );
        if (hasExpire != null && hasExpire == 0) {
            log.warn("adding expire_at column to existing t_order");
            jdbcTemplate.execute("ALTER TABLE t_order ADD COLUMN expire_at DATETIME NULL");
        }
        log.info("t_order schema ready");
    }
}
