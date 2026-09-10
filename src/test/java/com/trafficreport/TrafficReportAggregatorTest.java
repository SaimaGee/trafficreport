package com.trafficreport;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TrafficReportAggregatorTest {

    private TrafficReportAggregator newAggregator(int maxRequests) {
        return new TrafficReportAggregator(
            new TrafficRecordParser(),
            new RateLimitEvaluator(Duration.ofSeconds(60), maxRequests)
        );
    }

    @Test
    void countsValidRecordsAndLeavesMalformedAtZeroWhenAllValid() {
        TrafficReportAggregator aggregator = newAggregator(100);

        aggregator.process(validLine("a1_1", "acct_1", "/v1/widgets", "2024-01-15T10:00:00Z"));
        aggregator.process(validLine("a1_2", "acct_1", "/v1/widgets", "2024-01-15T10:00:01Z"));

        TrafficReport report = aggregator.buildReport();

        assertEquals(2, report.totalRequestsProcessed());
        assertEquals(0, report.totalMalformed());
        assertEquals(0, report.duplicateRequestIds());
        assertEquals(2, report.requestsByClient().get("acct_1"));
        assertEquals(2, report.requestsByEndpoint().get("/v1/widgets"));
    }

    @Test
    void countsMalformedRecordsByReason() {
        TrafficReportAggregator aggregator = newAggregator(100);

        aggregator.process("not valid json");
        aggregator.process(validLine("a1_1", "acct_1", "/v1/widgets", "2024-01-15T10:00:00Z"));

        TrafficReport report = aggregator.buildReport();

        assertEquals(2, report.totalRequestsProcessed());
        assertEquals(1, report.totalMalformed());
        assertEquals(1, report.malformedByReason().get(MalformedReason.INVALID_JSON.name()));
    }

    @Test
    void tracksDuplicateRequestIdsSeparatelyFromMalformed() {
        TrafficReportAggregator aggregator = newAggregator(100);
        String line = validLine("a1_1", "acct_1", "/v1/widgets", "2024-01-15T10:00:00Z");

        aggregator.process(line);
        aggregator.process(line); // same request_id again

        TrafficReport report = aggregator.buildReport();

        assertEquals(2, report.totalRequestsProcessed());
        assertEquals(0, report.totalMalformed(), "a duplicate is a well-formed record, not malformed input");
        assertEquals(1, report.duplicateRequestIds());
        assertEquals(1, report.requestsByClient().get("acct_1"),
            "the duplicate should not be double-counted toward client traffic");
    }

    @Test
    void recordsViolationOnceClientExceedsLimit() {
        TrafficReportAggregator aggregator = newAggregator(1); // 1 request per 60s window

        aggregator.process(validLine("a1_1", "acct_1", "/v1/widgets", "2024-01-15T10:00:00Z"));
        aggregator.process(validLine("a1_2", "acct_1", "/v1/widgets", "2024-01-15T10:00:01Z"));

        TrafficReport report = aggregator.buildReport();

        assertEquals(1, report.violationsByClient().get("acct_1"));
    }

    @Test
    void reportsNoViolationsWhenNoneOccur() {
        TrafficReportAggregator aggregator = newAggregator(100);

        aggregator.process(validLine("a1_1", "acct_1", "/v1/widgets", "2024-01-15T10:00:00Z"));

        TrafficReport report = aggregator.buildReport();

        assertTrue(report.violationsByClient().isEmpty());
        assertTrue(report.topOffenders(10).isEmpty());
    }

    private String validLine(String requestId, String clientId, String endpoint, String timestamp) {
        return "{\"request_id\":\"" + requestId + "\",\"timestamp\":\"" + timestamp + "\","
            + "\"client_id\":\"" + clientId + "\",\"endpoint\":\"" + endpoint + "\",\"status_code\":200}";
    }
}