package com.tanalytics.query.repository;

import com.tanalytics.query.model.AggregateStats;
import com.tanalytics.query.model.MediaStats;
import com.tanalytics.query.model.PageStats;
import com.tanalytics.query.model.ReferrerStats;
import com.tanalytics.query.model.TimeSeriesPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Raw JDBC queries against ClickHouse.
 * Uses the materialized views (page_views_hourly, media_engagement_hourly)
 * for pre-aggregated queries and falls back to the raw events table for
 * custom date ranges outside the last 24 hours.
 */
@Repository
public class ClickHouseRepository {

    private static final DateTimeFormatter CLICKHOUSE_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    public ClickHouseRepository(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Aggregate stats from materialized views (last 24 h)
    // -------------------------------------------------------------------------

    public AggregateStats queryAggregateFromMV(String siteId, Instant start, Instant end) {
        String sql = """
                SELECT
                    sum(views)             AS page_views,
                    sum(unique_visitors)   AS unique_visitors,
                    sum(unique_sessions)   AS unique_sessions
                FROM page_views_hourly
                WHERE site_id = ?
                  AND hour BETWEEN ? AND ?
                """;
        return jdbc.queryForObject(sql, (rs, rowNum) -> new AggregateStats(
                rs.getLong("page_views"),
                rs.getLong("unique_visitors"),
                rs.getLong("unique_sessions"),
                0.0,   // bounce rate requires session-level data – computed separately
                0.0    // avg session duration – computed separately
        ), siteId, toClickHouseDateTime(start), toClickHouseDateTime(end));
    }

    public AggregateStats queryAggregateFromRaw(String siteId, Instant start, Instant end) {
        String sql = """
                SELECT
                    countIf(event_type = 'page_view')    AS page_views,
                    uniq(visitor_id)                     AS unique_visitors,
                    uniq(session_id)                     AS unique_sessions
                FROM events
                WHERE site_id = ?
                  AND timestamp BETWEEN ? AND ?
                """;
        return jdbc.queryForObject(sql, (rs, rowNum) -> new AggregateStats(
                rs.getLong("page_views"),
                rs.getLong("unique_visitors"),
                rs.getLong("unique_sessions"),
                0.0,
                0.0
        ), siteId, toClickHouseDateTime(start), toClickHouseDateTime(end));
    }

    // -------------------------------------------------------------------------
    // Top pages
    // -------------------------------------------------------------------------

    public List<PageStats> queryTopPagesFromMV(String siteId, Instant start, Instant end, int limit) {
        String sql = """
                SELECT
                    url,
                    sum(views)           AS views,
                    sum(unique_visitors) AS unique_visitors,
                    sum(unique_sessions) AS unique_sessions
                FROM page_views_hourly
                WHERE site_id = ?
                  AND hour BETWEEN ? AND ?
                GROUP BY url
                ORDER BY views DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new PageStats(
                rs.getString("url"),
                rs.getLong("views"),
                rs.getLong("unique_visitors"),
                rs.getLong("unique_sessions"),
                0.0
        ), siteId, toClickHouseDateTime(start), toClickHouseDateTime(end), limit);
    }

    // -------------------------------------------------------------------------
    // Top referrers
    // -------------------------------------------------------------------------

    public List<ReferrerStats> queryTopReferrers(String siteId, Instant start, Instant end, int limit) {
        String sql = """
                SELECT
                    referrer,
                    sum(views)           AS visits,
                    sum(unique_visitors) AS unique_visitors
                FROM page_views_hourly
                WHERE site_id = ?
                  AND hour BETWEEN ? AND ?
                  AND referrer != ''
                GROUP BY referrer
                ORDER BY visits DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ReferrerStats(
                rs.getString("referrer"),
                rs.getLong("visits"),
                rs.getLong("unique_visitors")
        ), siteId, toClickHouseDateTime(start), toClickHouseDateTime(end), limit);
    }

    // -------------------------------------------------------------------------
    // Time-series (hourly buckets)
    // -------------------------------------------------------------------------

    public List<TimeSeriesPoint> queryTimeSeries(String siteId, Instant start, Instant end) {
        String sql = """
                SELECT
                    hour                 AS bucket,
                    sum(views)           AS page_views,
                    sum(unique_visitors) AS unique_visitors
                FROM page_views_hourly
                WHERE site_id = ?
                  AND hour BETWEEN ? AND ?
                GROUP BY hour
                ORDER BY hour ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new TimeSeriesPoint(
                rs.getTimestamp("bucket").toInstant(),
                rs.getLong("page_views"),
                rs.getLong("unique_visitors")
        ), siteId, toClickHouseDateTime(start), toClickHouseDateTime(end));
    }

    // -------------------------------------------------------------------------
    // Media engagement
    // -------------------------------------------------------------------------

    public List<MediaStats> queryMediaStats(String siteId, Instant start, Instant end, int limit) {
        String sql = """
                SELECT
                    media_url,
                    media_type,
                    sum(plays)                AS plays,
                    sum(completions)          AS completions,
                    sum(unique_viewers)       AS unique_viewers,
                    avg(avg_completion_rate)  AS avg_completion_rate
                FROM media_engagement_hourly
                WHERE site_id = ?
                  AND hour BETWEEN ? AND ?
                GROUP BY media_url, media_type
                ORDER BY plays DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new MediaStats(
                rs.getString("media_url"),
                rs.getString("media_type"),
                rs.getLong("plays"),
                rs.getLong("completions"),
                rs.getLong("unique_viewers"),
                rs.getDouble("avg_completion_rate")
        ), siteId, toClickHouseDateTime(start), toClickHouseDateTime(end), limit);
    }

    private String toClickHouseDateTime(Instant value) {
        return CLICKHOUSE_DT_FMT.format(value);
    }
}

