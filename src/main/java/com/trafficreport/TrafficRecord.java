package com.trafficreport;

import java.time.Instant;

public record TrafficRecord(
    String requestId,
    Instant timestamp,
    String clientId,
    String endpoint,
    int statusCode
) {}