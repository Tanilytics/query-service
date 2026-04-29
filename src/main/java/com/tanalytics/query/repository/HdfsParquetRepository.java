package com.tanalytics.query.repository;

import com.tanalytics.query.model.AggregateStats;
import com.tanalytics.query.model.MediaStats;
import com.tanalytics.query.model.PageStats;
import com.tanalytics.query.model.ReferrerStats;
import com.tanalytics.query.model.TimeSeriesPoint;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository that queries Parquet files stored on HDFS via DuckDB SQL.
 * Files are downloaded from HDFS to a temp directory, then queried in-place by DuckDB.
 *
 * Parquet schema matches the processing service output:
 * event_id, site_id, visitor_id, session_id, event_type, event_name, timestamp,
 * url, referrer, utm_source, utm_medium, utm_campaign, country, region,
 * device_type, browser, os, screen_width, properties, ip_hash, consent_given
 */
@Repository
public class HdfsParquetRepository {

    private static final Logger log = LoggerFactory.getLogger(HdfsParquetRepository.class);
    private static final DateTimeFormatter YEAR_FMT = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC);

    private final FileSystem hdfs;
    private final JdbcTemplate duckDb;
    private final String hdfsBasePath;

    public HdfsParquetRepository(FileSystem hdfs,
                                 @Qualifier("duckDbJdbcTemplate") JdbcTemplate duckDb,
                                 @Value("${hdfs.base-path:/analytics/events}") String hdfsBasePath) {
        this.hdfs = hdfs;
        this.duckDb = duckDb;
        this.hdfsBasePath = hdfsBasePath;
    }

    // -------------------------------------------------------------------------
    // Aggregate stats
    // -------------------------------------------------------------------------

    public AggregateStats queryAggregate(String siteId, Instant start, Instant end) {
        List<String> parquetFiles = resolveParquetFiles(siteId, start, end);
        if (parquetFiles.isEmpty()) {
            return new AggregateStats(0, 0, 0, 0.0, 0.0);
        }

        String filesClause = buildFilesClause(parquetFiles);
        long startMs = start.toEpochMilli();
        long endMs = end.toEpochMilli();

        String sql = """
                WITH events AS (
                    SELECT * FROM read_parquet(%s)
                    WHERE site_id = '%s' AND timestamp >= %d AND timestamp <= %d
                ),
                sessions AS (
                    SELECT
                        session_id,
                        visitor_id,
                        COUNT(*) AS event_count,
                        MAX(timestamp) - MIN(timestamp) AS duration_ms
                    FROM events
                    GROUP BY session_id, visitor_id
                )
                SELECT
                    (SELECT COUNT(*) FROM events WHERE event_type = 'page_view') AS page_views,
                    (SELECT COUNT(DISTINCT visitor_id) FROM events) AS unique_visitors,
                    (SELECT COUNT(*) FROM sessions) AS unique_sessions,
                    ROUND(CAST(SUM(CASE WHEN event_count = 1 THEN 1 ELSE 0 END) AS DOUBLE) / NULLIF(COUNT(*), 0) * 100, 2) AS bounce_rate,
                    ROUND(CAST(AVG(duration_ms) AS DOUBLE) / 1000, 2) AS avg_session_duration_seconds
                FROM sessions
                """.formatted(filesClause, siteId, startMs, endMs);

        return duckDb.query(sql, rs -> {
            if (rs.next()) {
                return new AggregateStats(
                        rs.getLong("page_views"),
                        rs.getLong("unique_visitors"),
                        rs.getLong("unique_sessions"),
                        rs.getDouble("bounce_rate"),
                        rs.getDouble("avg_session_duration_seconds")
                );
            }
            return new AggregateStats(0, 0, 0, 0.0, 0.0);
        });
    }

    // -------------------------------------------------------------------------
    // Time series
    // -------------------------------------------------------------------------

    public List<TimeSeriesPoint> queryTimeSeries(String siteId, Instant start, Instant end) {
        List<String> parquetFiles = resolveParquetFiles(siteId, start, end);
        if (parquetFiles.isEmpty()) {
            return List.of();
        }

        String filesClause = buildFilesClause(parquetFiles);
        long startMs = start.toEpochMilli();
        long endMs = end.toEpochMilli();

        String sql = """
                SELECT
                    epoch_ms(CAST((timestamp / 3600000) * 3600000 AS BIGINT)) AS bucket,
                    COUNT(*) AS page_views,
                    COUNT(DISTINCT visitor_id) AS unique_visitors
                FROM read_parquet(%s)
                WHERE site_id = '%s' AND timestamp >= %d AND timestamp <= %d
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(filesClause, siteId, startMs, endMs);

        return duckDb.query(sql, rs -> {
            List<TimeSeriesPoint> points = new ArrayList<>();
            while (rs.next()) {
                points.add(new TimeSeriesPoint(
                        rs.getTimestamp("bucket").toInstant(),
                        rs.getLong("page_views"),
                        rs.getLong("unique_visitors")
                ));
            }
            return points;
        });
    }

    // -------------------------------------------------------------------------
    // Top pages
    // -------------------------------------------------------------------------

    public List<PageStats> queryTopPages(String siteId, Instant start, Instant end, int limit) {
        List<String> parquetFiles = resolveParquetFiles(siteId, start, end);
        if (parquetFiles.isEmpty()) {
            return List.of();
        }

        String filesClause = buildFilesClause(parquetFiles);
        long startMs = start.toEpochMilli();
        long endMs = end.toEpochMilli();

        String sql = """
                SELECT
                    url,
                    SUM(views) AS views,
                    COUNT(DISTINCT visitor_id) AS unique_visitors,
                    COUNT(DISTINCT session_id) AS unique_sessions,
                    ROUND(CAST(SUM(total_duration_ms) AS DOUBLE) / NULLIF(SUM(views), 0) / 1000, 2) AS avg_time_on_page_seconds
                FROM (
                    SELECT
                        url,
                        visitor_id,
                        session_id,
                        COUNT(*) AS views,
                        MAX(timestamp) - MIN(timestamp) AS total_duration_ms
                    FROM read_parquet(%s)
                    WHERE site_id = '%s' AND timestamp >= %d AND timestamp <= %d AND event_type = 'page_view'
                    GROUP BY url, visitor_id, session_id
                )
                GROUP BY url
                ORDER BY views DESC
                LIMIT %d
                """.formatted(filesClause, siteId, startMs, endMs, limit);

        return duckDb.query(sql, rs -> {
            List<PageStats> pages = new ArrayList<>();
            while (rs.next()) {
                pages.add(new PageStats(
                        rs.getString("url"),
                        rs.getLong("views"),
                        rs.getLong("unique_visitors"),
                        rs.getLong("unique_sessions"),
                        rs.getDouble("avg_time_on_page_seconds")
                ));
            }
            return pages;
        });
    }

    // -------------------------------------------------------------------------
    // Top referrers
    // -------------------------------------------------------------------------

    public List<ReferrerStats> queryTopReferrers(String siteId, Instant start, Instant end, int limit) {
        List<String> parquetFiles = resolveParquetFiles(siteId, start, end);
        if (parquetFiles.isEmpty()) {
            return List.of();
        }

        String filesClause = buildFilesClause(parquetFiles);
        long startMs = start.toEpochMilli();
        long endMs = end.toEpochMilli();

        String sql = """
                SELECT
                    COALESCE(referrer, '(direct)') AS referrer,
                    COUNT(DISTINCT session_id) AS visits,
                    COUNT(DISTINCT visitor_id) AS unique_visitors
                FROM read_parquet(%s)
                WHERE site_id = '%s' AND timestamp >= %d AND timestamp <= %d
                GROUP BY referrer
                ORDER BY visits DESC
                LIMIT %d
                """.formatted(filesClause, siteId, startMs, endMs, limit);

        return duckDb.query(sql, rs -> {
            List<ReferrerStats> referrers = new ArrayList<>();
            while (rs.next()) {
                referrers.add(new ReferrerStats(
                        rs.getString("referrer"),
                        rs.getLong("visits"),
                        rs.getLong("unique_visitors")
                ));
            }
            return referrers;
        });
    }

    // -------------------------------------------------------------------------
    // Media stats
    // -------------------------------------------------------------------------

    public List<MediaStats> queryMediaStats(String siteId, Instant start, Instant end, int limit) {
        List<String> parquetFiles = resolveParquetFiles(siteId, start, end);
        if (parquetFiles.isEmpty()) {
            return List.of();
        }

        String filesClause = buildFilesClause(parquetFiles);
        long startMs = start.toEpochMilli();
        long endMs = end.toEpochMilli();

        String sql = """
                SELECT
                    url AS media_url,
                    'video' AS media_type,
                    COUNT(*) FILTER (WHERE event_type = 'media_play') AS plays,
                    COUNT(*) FILTER (WHERE event_type = 'media_complete') AS completions,
                    COUNT(DISTINCT visitor_id) FILTER (WHERE event_type = 'media_play') AS unique_viewers,
                    CASE WHEN COUNT(*) FILTER (WHERE event_type = 'media_play') > 0 THEN
                        ROUND(CAST(COUNT(*) FILTER (WHERE event_type = 'media_complete') AS DOUBLE)
                            / COUNT(*) FILTER (WHERE event_type = 'media_play') * 100, 2)
                    ELSE 0 END AS avg_completion_rate
                FROM read_parquet(%s)
                WHERE site_id = '%s' AND timestamp >= %d AND timestamp <= %d
                  AND event_type IN ('media_play', 'media_pause', 'media_seek', 'media_progress', 'media_buffer', 'media_complete')
                GROUP BY url
                HAVING COUNT(*) FILTER (WHERE event_type = 'media_play') > 0
                ORDER BY plays DESC
                LIMIT %d
                """.formatted(filesClause, siteId, startMs, endMs, limit);

        return duckDb.query(sql, rs -> {
            List<MediaStats> media = new ArrayList<>();
            while (rs.next()) {
                media.add(new MediaStats(
                        rs.getString("media_url"),
                        rs.getString("media_type"),
                        rs.getLong("plays"),
                        rs.getLong("completions"),
                        rs.getLong("unique_viewers"),
                        rs.getDouble("avg_completion_rate")
                ));
            }
            return media;
        });
    }

    // -------------------------------------------------------------------------
    // HDFS file resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves Parquet file paths from HDFS for the given time range.
     * Uses the partition structure: /analytics/events/{yyyy}/{MM}/{dd}/{HH}/*.parquet
     */
    private List<String> resolveParquetFiles(String siteId, Instant start, Instant end) {
        List<String> localPaths = new ArrayList<>();
        org.apache.hadoop.fs.Path basePath = new org.apache.hadoop.fs.Path(hdfsBasePath);

        int startYear = Integer.parseInt(YEAR_FMT.format(start));
        int endYear = Integer.parseInt(YEAR_FMT.format(end));

        for (int year = startYear; year <= endYear; year++) {
            int startMonth = (year == startYear) ? Integer.parseInt(MONTH_FMT.format(start)) : 1;
            int endMonth = (year == endYear) ? Integer.parseInt(MONTH_FMT.format(end)) : 12;

            for (int month = startMonth; month <= endMonth; month++) {
                int startDay = (year == startYear && month == startMonth) ? Integer.parseInt(DAY_FMT.format(start)) : 1;
                int endDay = (year == endYear && month == endMonth) ? Integer.parseInt(DAY_FMT.format(end)) : 28;

                for (int day = startDay; day <= endDay; day++) {
                    int startHour = (year == startYear && month == startMonth && day == startDay)
                            ? Integer.parseInt(HOUR_FMT.format(start)) : 0;
                    int endHour = (year == endYear && month == endMonth && day == endDay)
                            ? Integer.parseInt(HOUR_FMT.format(end)) : 23;

                    for (int hour = startHour; hour <= endHour; hour++) {
                        org.apache.hadoop.fs.Path hourDir = new org.apache.hadoop.fs.Path(basePath,
                                String.format("%04d/%02d/%02d/%02d", year, month, day, hour));
                        try {
                            if (hdfs.exists(hourDir)) {
                                FileStatus[] files = hdfs.listStatus(hourDir,
                                        p -> p.getName().endsWith(".parquet"));
                                for (FileStatus file : files) {
                                    String localPath = downloadToLocalTemp(file.getPath());
                                    if (localPath != null) {
                                        localPaths.add(localPath);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Failed to list HDFS directory {}: {}", hourDir, e.getMessage());
                        }
                    }
                }
            }
        }

        log.debug("Resolved {} Parquet files from HDFS for site={} range=[{},{}]",
                localPaths.size(), siteId, start, end);
        return localPaths;
    }

    private String downloadToLocalTemp(org.apache.hadoop.fs.Path hdfsPath) {
        try {
            Path tempDir = Files.createTempDirectory("duckdb-parquet");
            Path localFile = tempDir.resolve(hdfsPath.getName());
            hdfs.copyToLocalFile(hdfsPath, new org.apache.hadoop.fs.Path(localFile.toString()));
            localFile.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            return localFile.toString().replace("\\", "/");
        } catch (IOException e) {
            log.warn("Failed to download HDFS file {}: {}", hdfsPath, e.getMessage());
            return null;
        }
    }

    private String buildFilesClause(List<String> files) {
        if (files.size() == 1) {
            return "'" + files.get(0) + "'";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(files.get(i)).append("'");
        }
        sb.append("]");
        return sb.toString();
    }
}
