package com.tanalytics.query.model;

/**
 * Generic breakdown row returned by country/device/campaign materialized views.
 * The dimension fields map to the selected breakdown type.
 */
public record BreakdownStats(
        String dimension1,
        String dimension2,
        String dimension3,
        long pageViews,
        long uniqueVisitors,
        long uniqueSessions
) {}

