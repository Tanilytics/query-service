package com.tanalytics.query.model;

/**
 * Aggregate analytics stats for a site over a given time range.
 */
public record AggregateStats(
        long pageViews,
        long uniqueVisitors,
        long uniqueSessions,
        double bounceRate,
        double avgSessionDurationSeconds
) {}

