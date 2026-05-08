package com.tanalytics.query.service;

import com.tanalytics.query.model.AggregateStats;
import com.tanalytics.query.model.BreakdownStats;
import com.tanalytics.query.model.BreakdownType;
import com.tanalytics.query.model.MediaStats;
import com.tanalytics.query.model.PageStats;
import com.tanalytics.query.model.RealtimeStats;
import com.tanalytics.query.model.ReferrerStats;
import com.tanalytics.query.model.TimeRange;
import com.tanalytics.query.model.TimeSeriesPoint;
import com.tanalytics.query.repository.ClickHouseRepository;
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
 *  - ClickHouse materialized views → last 24 hours
 *  - ClickHouse raw events         → arbitrary date ranges
 */
@Service
public class AnalyticsQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsQueryService.class);
    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);

    private final ClickHouseRepository clickHouseRepo;
    private final RedisTemplate<String, Object> redis;

    public AnalyticsQueryService(ClickHouseRepository clickHouseRepo,
                                 RedisTemplate<String, Object> redis) {
        this.clickHouseRepo = clickHouseRepo;
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
        if (timeRange.isWithinHours(24)) {
            log.debug("Routing aggregate query for site={} to ClickHouse MVs", siteId);
            return clickHouseRepo.queryAggregateFromMV(siteId, timeRange.start(), timeRange.end());
        }
        log.debug("Routing aggregate query for site={} to raw ClickHouse events", siteId);
        return clickHouseRepo.queryAggregateFromRaw(siteId, timeRange.start(), timeRange.end());
    }

    // -------------------------------------------------------------------------
    // Time-series
    // -------------------------------------------------------------------------

    @Cacheable(value = "timeseries", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end()")
    public List<TimeSeriesPoint> getTimeSeries(String siteId, TimeRange timeRange) {
        return clickHouseRepo.queryTimeSeries(siteId, timeRange.start(), timeRange.end());
    }

    // -------------------------------------------------------------------------
    // Top pages
    // -------------------------------------------------------------------------

    @Cacheable(value = "top-pages", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<PageStats> getTopPages(String siteId, TimeRange timeRange, int limit) {
        return clickHouseRepo.queryTopPagesFromMV(siteId, timeRange.start(), timeRange.end(), limit);
    }

    // -------------------------------------------------------------------------
    // Referrers
    // -------------------------------------------------------------------------

    @Cacheable(value = "referrers", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<ReferrerStats> getTopReferrers(String siteId, TimeRange timeRange, int limit) {
        return clickHouseRepo.queryTopReferrers(siteId, timeRange.start(), timeRange.end(), limit);
    }

    // -------------------------------------------------------------------------
    // Breakdowns
    // -------------------------------------------------------------------------

    @Cacheable(value = "breakdowns", key = "#siteId + '_' + #breakdownType + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<BreakdownStats> getBreakdownStats(String siteId, TimeRange timeRange, String breakdownType, int limit) {
        BreakdownType type = BreakdownType.fromValue(breakdownType);

        if (timeRange.isWithinHours(24)) {
            log.debug("Routing breakdown query for site={} type={} to ClickHouse hourly MVs", siteId, type.value());
            return clickHouseRepo.queryBreakdownFromHourlyMV(siteId, timeRange.start(), timeRange.end(), limit, type);
        }

        log.debug("Routing breakdown query for site={} type={} to ClickHouse daily MVs", siteId, type.value());
        return clickHouseRepo.queryBreakdownFromDailyMV(siteId, timeRange.start(), timeRange.end(), limit, type);
    }

    // -------------------------------------------------------------------------
    // Media engagement
    // -------------------------------------------------------------------------

    @Cacheable(value = "media", key = "#siteId + '_' + #timeRange.start() + '_' + #timeRange.end() + '_' + #limit")
    public List<MediaStats> getMediaStats(String siteId, TimeRange timeRange, int limit) {
        return clickHouseRepo.queryMediaStats(siteId, timeRange.start(), timeRange.end(), limit);
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

