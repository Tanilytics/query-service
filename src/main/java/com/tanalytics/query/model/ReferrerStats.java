package com.tanalytics.query.model;

/** Per-referrer analytics summary. */
public record ReferrerStats(
        String referrer,
        long visits,
        long uniqueVisitors
) {}

