package com.tanalytics.query.model;

import java.time.Instant;

/** A single data-point in a time-series chart (e.g. hourly page-view buckets). */
public record TimeSeriesPoint(
        Instant bucket,
        long pageViews,
        long uniqueVisitors
) {}

