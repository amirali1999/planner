package com.lifeplanner.db;

import com.lifeplanner.config.PlannerProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.SQLExceptionTranslator;

/** Replaces Boot's auto-configured DataSource; Flyway and JdbcClient auto-configuration use this bean. */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    HikariDataSource dataSource(PlannerProperties properties) {
        PlannerProperties.Datasource ds = properties.datasource();
        log.info("SQLite database: {}", ds.file().toAbsolutePath().normalize());
        return SqliteDataSources.create(ds.file(), ds.busyTimeout(), ds.maxPoolSize());
    }

    /** Picked up by Boot's JdbcTemplate (and therefore JdbcClient) auto-configuration. */
    @Bean
    SQLExceptionTranslator sqliteExceptionTranslator() {
        return new SqliteExceptionTranslator();
    }
}
