package com.tanalytics.query.model;

/** Media engagement metrics per media URL. */
public record MediaStats(
        String mediaUrl,
        String mediaType,
        long plays,
        long completions,
        long uniqueViewers,
        double avgCompletionRate
) {}

