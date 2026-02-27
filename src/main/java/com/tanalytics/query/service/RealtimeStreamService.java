package com.tanalytics.query.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Server-Sent Event (SSE) connections for real-time dashboard updates.
 *
 * Flow:
 *   Dashboard opens SSE → subscribe to Redis Pub/Sub channel "rt:{siteId}"
 *   processing-service publishes counter updates → pushed to all SSE clients for that site
 */
@Service
public class RealtimeStreamService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeStreamService.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private final RedisMessageListenerContainer listenerContainer;

    /** siteId → list of active SSE emitters */
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    /** siteId → Redis Pub/Sub listener (registered once per site) */
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

    public RealtimeStreamService(RedisMessageListenerContainer listenerContainer) {
        this.listenerContainer = listenerContainer;
    }

    /**
     * Creates an SSE emitter for the given site and registers a Redis listener
     * if one doesn't already exist for that site.
     */
    public SseEmitter subscribe(String siteId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(siteId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        // Ensure exactly one Redis listener per siteId
        listeners.computeIfAbsent(siteId, id -> {
            MessageListener listener = (message, pattern) -> broadcast(siteId, message);
            listenerContainer.addMessageListener(listener,
                    new PatternTopic("rt:" + siteId));
            log.info("Subscribed to Redis channel rt:{}", siteId);
            return listener;
        });

        emitter.onCompletion(() -> removeEmitter(siteId, emitter));
        emitter.onTimeout(() -> removeEmitter(siteId, emitter));
        emitter.onError(e -> removeEmitter(siteId, emitter));

        return emitter;
    }

    private void broadcast(String siteId, Message message) {
        String payload = new String(message.getBody());
        CopyOnWriteArrayList<SseEmitter> siteEmitters = emitters.get(siteId);
        if (siteEmitters == null) return;

        siteEmitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("update").data(payload));
                return false;
            } catch (IOException e) {
                log.debug("Removing dead SSE emitter for site={}", siteId);
                return true;
            }
        });
    }

    private void removeEmitter(String siteId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> siteEmitters = emitters.get(siteId);
        if (siteEmitters != null) {
            siteEmitters.remove(emitter);
            if (siteEmitters.isEmpty()) {
                // Remove Redis listener when no clients remain
                MessageListener listener = listeners.remove(siteId);
                if (listener != null) {
                    listenerContainer.removeMessageListener(listener);
                    log.info("Unsubscribed from Redis channel rt:{}", siteId);
                }
                emitters.remove(siteId);
            }
        }
    }
}

