package com.trafficreport;

import java.util.List;
import java.util.Map;

public record TrafficReport(
    int totalRequestsProcessed,
    int totalMalformed,
    Map<String, Integer> malformedByReason,
    int duplicateRequestIds,
    Map<String, Integer> requestsByClient,
    Map<String, Integer> requestsByEndpoint,
    Map<String, Integer> violationsByClient
) {

    public List<Map.Entry<String, Integer>> topOffenders(int limit) {
        return violationsByClient.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .toList();
    }
}