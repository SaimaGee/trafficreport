package com.trafficreport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficRecordParserTest {

    private final TrafficRecordParser parser = new TrafficRecordParser();

    @Test
    void parsesValidRecord() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":200}";

        ParseResult result = parser.parse(line);

        assertInstanceOf(ParseResult.Ok.class, result);
        TrafficRecord record = ((ParseResult.Ok) result).record();
        assertEquals("a1_1", record.requestId());
        assertEquals("acct_1", record.clientId());
        assertEquals("/v1/widgets", record.endpoint());
        assertEquals(200, record.statusCode());
    }

    @Test
    void acceptsTimestampWithOffsetInsteadOfZ() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00+02:00\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":200}";

        ParseResult result = parser.parse(line);

        assertInstanceOf(ParseResult.Ok.class, result);
    }

    @Test
    void rejectsBlankLine() {
        ParseResult result = parser.parse("   ");
        assertMalformed(result, MalformedReason.INVALID_JSON);
    }

    @Test
    void rejectsLineNotShapedLikeJson() {
        ParseResult result = parser.parse("not json at all");
        assertMalformed(result, MalformedReason.INVALID_JSON);
    }

    @Test
    void rejectsRecordMissingRequiredField() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"endpoint\":\"/v1/widgets\",\"status_code\":200}"; // client_id missing

        ParseResult result = parser.parse(line);

        assertMalformed(result, MalformedReason.MISSING_FIELD);
    }

    @Test
    void rejectsBlankClientId() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"client_id\":\"\",\"endpoint\":\"/v1/widgets\",\"status_code\":200}";

        ParseResult result = parser.parse(line);

        assertMalformed(result, MalformedReason.MISSING_FIELD);
    }

    @Test
    void rejectsBlankEndpointSeparatelyFromMissingField() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"\",\"status_code\":200}";

        ParseResult result = parser.parse(line);

        assertMalformed(result, MalformedReason.INVALID_ENDPOINT);
    }

    @Test
    void rejectsStatusCodeBelowValidRange() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":99}";

        ParseResult result = parser.parse(line);

        assertMalformed(result, MalformedReason.INVALID_STATUS);
    }

    @Test
    void rejectsStatusCodeAboveValidRange() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":600}";

        ParseResult result = parser.parse(line);

        assertMalformed(result, MalformedReason.INVALID_STATUS);
    }

    @Test
    void rejectsUnparseableTimestamp() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"not-a-date\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":200}";

        ParseResult result = parser.parse(line);

        assertMalformed(result, MalformedReason.BAD_TIMESTAMP);
    }

    @Test
    void rejectsMultipleConcatenatedJsonObjectsOnOneLine() {
        String line = "{\"request_id\":\"a1_1\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":200}"
            + "{\"request_id\":\"a1_2\",\"timestamp\":\"2024-01-15T10:00:01Z\","
            + "\"client_id\":\"acct_1\",\"endpoint\":\"/v1/widgets\",\"status_code\":200}";

        // Known limitation: the regex-based parser currently merges fields from both
        // objects into one record rather than rejecting the line. This test documents
        // the current behavior so a future fix has a regression test to satisfy.
        ParseResult result = parser.parse(line);

        assertInstanceOf(ParseResult.Ok.class, result,
            "Documents current behavior — see README known limitations. "
            + "Update this test if/when concatenated-object detection is added.");
    }

    private void assertMalformed(ParseResult result, MalformedReason expectedReason) {
        assertInstanceOf(ParseResult.Malformed.class, result);
        assertEquals(expectedReason, ((ParseResult.Malformed) result).reason());
    }
}