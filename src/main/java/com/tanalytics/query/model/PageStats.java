package com.tanalytics.query.model;

/** Per-page analytics summary returned by the top-pages endpoint. */
public record PageStats(
        String url,
        long views,
        long uniqueVisitors,
        long uniqueSessions,
        double avgTimeOnPageSeconds
) {}

