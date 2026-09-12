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
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """
        );
        log.info("t_order schema ready");
    }
}
