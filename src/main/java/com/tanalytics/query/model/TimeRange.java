package com.tanalytics.query.model;

import java.time.Instant;

/**
 * Represents a requested time window for analytics queries.
 * The query service uses this to decide whether to hit Redis (realtime),
 * ClickHouse materialized views (last 24 h), or raw events (custom range).
 */
public record TimeRange(Instant start, Instant end) {

    /** True when the range fits within the last 5 minutes (use Redis real-time counters). */
    public boolean isWithinMinutes(int minutes) {
        return start.isAfter(Instant.now().minusSeconds((long) minutes * 60));
    }

    /** True when the range fits within the last 24 hours (use ClickHouse MVs). */
    public boolean isWithinHours(int hours) {
        return start.isAfter(Instant.now().minusSeconds((long) hours * 3600));
    }
}

