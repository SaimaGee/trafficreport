package com.trafficreport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window log rate limiter, keyed per client_id.
 * A client is flagged as violating once the number of requests within
 * the trailing `window` duration exceeds `maxRequests`.
 */
public class RateLimitEvaluator {

    private final Duration window;
    private final int maxRequests;
    private final Map<String, Deque<Instant>> clientTimestamps = new ConcurrentHashMap<>();

    public RateLimitEvaluator(Duration window, int maxRequests) {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        this.window = window;
        this.maxRequests = maxRequests;
    }

    /**
     * Records this request's timestamp for the given client and returns
     * true if the client is currently over the limit within the window.
     */
    public boolean recordAndCheck(String clientId, Instant timestamp) {
        Deque<Instant> times = clientTimestamps.computeIfAbsent(clientId, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(timestamp);

            Instant windowStart = timestamp.minus(window);
            while (!times.isEmpty() && times.peekFirst().isBefore(windowStart)) {
                times.pollFirst();
            }

            return times.size() > maxRequests;
        }
    }
}