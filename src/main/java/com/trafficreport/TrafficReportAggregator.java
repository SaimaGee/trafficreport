package com.trafficreport;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TrafficReportAggregator {

    private final TrafficRecordParser parser;
    private final RateLimitEvaluator rateLimitEvaluator;

    private final Map<String, Integer> malformedByReason = new ConcurrentHashMap<>();
    private final Map<String, Integer> requestsByClient = new ConcurrentHashMap<>();
    private final Map<String, Integer> requestsByEndpoint = new ConcurrentHashMap<>();
    private final Map<String, Integer> violationsByClient = new ConcurrentHashMap<>();
    private final Set<String> seenRequestIds = ConcurrentHashMap.newKeySet();
    private int totalProcessed = 0;
    private int duplicateCount = 0;

    public TrafficReportAggregator(TrafficRecordParser parser, RateLimitEvaluator rateLimitEvaluator) {
        this.parser = parser;
        this.rateLimitEvaluator = rateLimitEvaluator;
    }

    public void process(String rawLine) {
        totalProcessed++;
        ParseResult result = parser.parse(rawLine);

        if (result instanceof ParseResult.Malformed m) {
            malformedByReason.merge(m.reason().name(), 1, Integer::sum);
            return;
        }

        TrafficRecord record = ((ParseResult.Ok) result).record();

        if (!seenRequestIds.add(record.requestId())) {
            duplicateCount++;
            return;
        }

        requestsByClient.merge(record.clientId(), 1, Integer::sum);
        requestsByEndpoint.merge(record.endpoint(), 1, Integer::sum);

        if (rateLimitEvaluator.recordAndCheck(record.clientId(), record.timestamp())) {
            violationsByClient.merge(record.clientId(), 1, Integer::sum);
        }
    }

    public TrafficReport buildReport() {
        return new TrafficReport(
            totalProcessed,
            malformedByReason.values().stream().mapToInt(Integer::intValue).sum(),
            malformedByReason,
            duplicateCount,
            requestsByClient,
            requestsByEndpoint,
            violationsByClient
        );
    }
}