package com.tanalytics.query.model;

/** Real-time snapshot: active users and running counters for the current hour. */
public record RealtimeStats(
        long activeUsers,
        long pageViewsCurrentHour,
        long uniqueVisitorsCurrentHour,
        int concurrentMediaViewers
) {}

