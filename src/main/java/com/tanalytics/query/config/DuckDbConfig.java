package com.tanalytics.query.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configures an embedded DuckDB DataSource and JdbcTemplate.
 * DuckDB is used to query Parquet files directly via SQL without loading them into a database.
 */
@Configuration
public class DuckDbConfig {

    @Bean(name = "duckDbDataSource")
    public DataSource duckDbDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:duckdb:");
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Bean(name = "duckDbJdbcTemplate")
    public JdbcTemplate duckDbJdbcTemplate() {
        return new JdbcTemplate(duckDbDataSource());
    }
}
