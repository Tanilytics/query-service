package com.tanalytics.query.controller;

import com.tanalytics.query.service.RealtimeStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint for real-time dashboard updates.
 * Clients open a persistent connection; the server pushes JSON events
 * whenever processing-service publishes to the Redis "rt:{siteId}" channel.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/stats")
@Tag(name = "Real-time", description = "Server-Sent Events for live dashboard updates")
public class RealtimeController {

    private final RealtimeStreamService streamService;

    public RealtimeController(RealtimeStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream of real-time counter updates for a site")
    public SseEmitter stream(@PathVariable String siteId) {
        return streamService.subscribe(siteId);
    }
}

