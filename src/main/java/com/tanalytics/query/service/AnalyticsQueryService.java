package com.tanalytics.query.service;

import com.tanalytics.query.model.AggregateStats;
import com.tanalytics.query.model.MediaStats;
import com.tanalytics.query.model.PageStats;
import com.tanalytics.query.model.RealtimeStats;
import com.tanalytics.query.model.ReferrerStats;
import com.tanalytics.query.model.TimeRange;
import com.tanalytics.query.model.TimeSeriesPoint;
import com.tanalytics.query.repository.HdfsParquetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Core query service that routes analytics queries to the appropriate backend:
 *  - Redis real-time counters  → last 5 minutes
 *  - HDFS/Parquet via DuckDB   → arbitrary date ranges
 */
@Service
public class AnalyticsQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsQueryService.class);
    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);

    private final HdfsParquetRepository hdfsParquetRepo;
    private final RedisTemplate<String, Object> redis;

    public AnalyticsQueryService(HdfsParquetRepository hdfsParquetRepo,
                                 RedisTemplate<String, Object> redis) {
        this.hdfsParquetRepo = hdfsParquetRepo;
        this.redis = redis;
    }

    // -------------------------------------------------------------------------
    // Realtime stats (always from Redis, never cached)
    // -------------------------------------------------------------------------

    public RealtimeStats getRealtimeStats(String siteId) {
        String hourKey = HOUR_FMT.format(Instant.now());

        Long pageViews = toLong(redis.opsForValue().get("rt:pageviews:" + siteId + ":" + hourKey));
        Long activeUsers = redis.opsForZSet().zCard("rt:active:" + siteId);
        Long mediaViewers = toLong(redis.opsForValue().get("rt:media:playing:" + siteId));

        // HyperLogLog cardinality estimate for unique visitors this hour
        Long uniqueVisitors = redis.execute(
                connection -> connection.hyperLogLogCommands()
                        .pfCount(("rt:visitors:" + siteId + ":" + hourKey).getBytes()),
                true
        );

        return new RealtimeStats(
                activeUsers  != null ? activeUsers  : 0L,
                pageViews    != null ? pageViews    : 0L,
                uniqueVisitors != null ? uniqueVisitors : 0L,
                mediaViewers != null ? mediaViewers.intValue() : 0
        );
    }

    // -------------------------------------------------------------------------
    // Aggregate stats – route based on time range
    // -------------------------------------------------------------------------

    @Cacheable(value = "stats", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end()")
    public AggregateStats getAggregateStats(String siteId, TimeRange timeRange) {
        log.debug("Routing aggregate query for site={} to HDFS/Parquet via DuckDB", siteId);
        return hdfsParquetRepo.queryAggregate(siteId, timeRange.start(), timeRange.end());
    }

    // -------------------------------------------------------------------------
    // Time-series
    // -------------------------------------------------------------------------

    @Cacheable(value = "timeseries", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end()")
    public List<TimeSeriesPoint> getTimeSeries(String siteId, TimeRange timeRange) {
        return hdfsParquetRepo.queryTimeSeries(siteId, timeRange.start(), timeRange.end());
    }

    // -------------------------------------------------------------------------
    // Top pages
    // -------------------------------------------------------------------------

    @Cacheable(value = "top-pages", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<PageStats> getTopPages(String siteId, TimeRange timeRange, int limit) {
        return hdfsParquetRepo.queryTopPages(siteId, timeRange.start(), timeRange.end(), limit);
    }

    // -------------------------------------------------------------------------
    // Referrers
    // -------------------------------------------------------------------------

    @Cacheable(value = "referrers", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<ReferrerStats> getTopReferrers(String siteId, TimeRange timeRange, int limit) {
        return hdfsParquetRepo.queryTopReferrers(siteId, timeRange.start(), timeRange.end(), limit);
    }

    // -------------------------------------------------------------------------
    // Media engagement
    // -------------------------------------------------------------------------

    @Cacheable(value = "media", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<MediaStats> getMediaStats(String siteId, TimeRange timeRange, int limit) {
        return hdfsParquetRepo.queryMediaStats(siteId, timeRange.start(), timeRange.end(), limit);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return null;
    }
}

