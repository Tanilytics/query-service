package com.tanalytics.query.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Configures the ClickHouse JDBC DataSource and JdbcTemplate.
 * Uses the lightweight HTTP transport (classifier=http) for compatibility
 * with self-hosted ClickHouse on port 8123.
 */
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String url;

    @Value("${clickhouse.username}")
    private String username;

    @Value("${clickhouse.password}")
    private String password;

    @Bean(name = "clickHouseDataSource")
    public DataSource clickHouseDataSource() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        // Use async inserts for write paths; reads are synchronous
        props.setProperty("async_insert", "0");
        return new ClickHouseDataSource(url, props);
    }

    /**
     * Dedicated JdbcTemplate for ClickHouse queries.
     * Named "clickHouseJdbcTemplate" to avoid clashing with a PostgreSQL bean
     * if both are present in the same context.
     */
    @Bean(name = "clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate() throws SQLException {
        return new JdbcTemplate(clickHouseDataSource());
    }
}

