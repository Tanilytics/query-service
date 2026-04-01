package com.tanalytics.query.controller;

import com.tanalytics.query.model.AggregateStats;
import com.tanalytics.query.model.MediaStats;
import com.tanalytics.query.model.PageStats;
import com.tanalytics.query.model.RealtimeStats;
import com.tanalytics.query.model.ReferrerStats;
import com.tanalytics.query.model.TimeRange;
import com.tanalytics.query.model.TimeSeriesPoint;
import com.tanalytics.query.service.AnalyticsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST controller exposing analytics query endpoints.
 * All paths are prefixed with /api/v1/sites/{siteId}/stats.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/stats")
@Tag(name = "Analytics", description = "Analytics query endpoints")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/realtime")
    @Operation(summary = "Active users and current-hour counters from Redis")
    public ResponseEntity<RealtimeStats> getRealtime(@PathVariable String siteId) {
        return ResponseEntity.ok(queryService.getRealtimeStats(siteId));
    }

    @GetMapping("/aggregate")
    @Operation(summary = "Aggregate stats for a time range")
    public ResponseEntity<AggregateStats> getAggregate(
            @PathVariable String siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(queryService.getAggregateStats(siteId, new TimeRange(from, to)));
    }

    @GetMapping("/timeseries")
    @Operation(summary = "Hourly time-series data for charting")
    public ResponseEntity<List<TimeSeriesPoint>> getTimeSeries(
            @PathVariable String siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(queryService.getTimeSeries(siteId, new TimeRange(from, to)));
    }

    @GetMapping("/pages")
    @Operation(summary = "Top pages ranked by page views")
    public ResponseEntity<List<PageStats>> getTopPages(
            @PathVariable String siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(queryService.getTopPages(siteId, new TimeRange(from, to), limit));
    }

    @GetMapping("/referrers")
    @Operation(summary = "Top referrers")
    public ResponseEntity<List<ReferrerStats>> getReferrers(
            @PathVariable String siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(queryService.getTopReferrers(siteId, new TimeRange(from, to), limit));
    }

    @GetMapping("/media")
    @Operation(summary = "Media engagement metrics")
    public ResponseEntity<List<MediaStats>> getMediaStats(
            @PathVariable String siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(queryService.getMediaStats(siteId, new TimeRange(from, to), limit));
    }

    @GetMapping("/funnels")
    @Operation(summary = "Funnel analysis results (Phase 4 – not implemented in Phase 3)")
    public ResponseEntity<ProblemDetail> getFunnels(@PathVariable String siteId) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_IMPLEMENTED,
                "Funnels endpoint is planned for Phase 4 and is not available in Phase 3."
        );
        detail.setTitle("Feature not implemented");
        detail.setProperty("feature", "funnels");
        detail.setProperty("targetPhase", "4");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(detail);
    }

    @GetMapping("/heatmaps")
    @Operation(summary = "Click heatmap data (Phase 4 – not implemented in Phase 3)")
    public ResponseEntity<ProblemDetail> getHeatmaps(@PathVariable String siteId) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_IMPLEMENTED,
                "Heatmaps endpoint is planned for Phase 4 and is not available in Phase 3."
        );
        detail.setTitle("Feature not implemented");
        detail.setProperty("feature", "heatmaps");
        detail.setProperty("targetPhase", "4");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(detail);
    }
}

