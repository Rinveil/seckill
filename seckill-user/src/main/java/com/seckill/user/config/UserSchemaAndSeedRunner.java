package com.seckill.user.config;

import com.seckill.user.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserSchemaAndSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSchemaAndSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final SeedAdminProperties seedAdminProperties;

    public UserSchemaAndSeedRunner(
            JdbcTemplate jdbcTemplate,
            AuthService authService,
            SeedAdminProperties seedAdminProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.seedAdminProperties = seedAdminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUserTable();
        ensureStatusColumn();
        authService.ensureAdmin(
                seedAdminProperties.username(),
                seedAdminProperties.password(),
                seedAdminProperties.nickname()
        );
        log.info("seed admin ready: username={}", seedAdminProperties.username());
    }

    /** 演示环境：若仍是旧版 mobile 表结构则重建 t_user。DDL 用 JdbcTemplate，业务走 MyBatis-Plus。 */
    private void ensureUserTable() {
        Integer cnt = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = 't_user'
                        """,
                Integer.class
        );
        if (cnt != null && cnt > 0) {
            Integer hasUsername = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'username'
                            """,
                    Integer.class
            );
            if (hasUsername != null && hasUsername == 0) {
                log.warn("detected legacy t_user schema, dropping and recreating");
                jdbcTemplate.execute("DROP TABLE t_user");
            }
        }
        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS t_user (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            username VARCHAR(64) NOT NULL UNIQUE,
                            password_hash VARCHAR(100) NOT NULL,
                            role VARCHAR(16) NOT NULL,
                            nickname VARCHAR(64) NOT NULL,
                            status TINYINT NOT NULL DEFAULT 1,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """
        );
    }

    private void ensureStatusColumn() {
        Integer hasStatus = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'status'
                        """,
                Integer.class
        );
        if (hasStatus != null && hasStatus == 0) {
            log.warn("adding status column to existing t_user");
            jdbcTemplate.execute("ALTER TABLE t_user ADD COLUMN status TINYINT NOT NULL DEFAULT 1");
        }
    }
}
