package com.trafficreport;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a single flat JSON log line without any external JSON library.
 * Only supports the fixed, known schema:
 * {"request_id":"...", "timestamp":"...", "client_id":"...", "endpoint":"...", "status_code":123}
 *
 * This is intentionally not a general-purpose JSON parser. It does not handle
 * nested objects/arrays or escaped quotes inside string values. If the input
 * format grows beyond this flat shape, switch back to a real JSON library
 * (Jackson or org.json) instead of extending this regex.
 */
public class TrafficRecordParser {

    private static final String[] REQUIRED_FIELDS = {
        "request_id", "timestamp", "client_id", "endpoint", "status_code"
    };

    // Matches "key":"string value"  OR  "key":numericValue
    private static final Pattern FIELD_PATTERN =
        Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))");

    public ParseResult parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return ParseResult.malformed(MalformedReason.INVALID_JSON);
        }

        Map<String, String> fields = extractFields(rawLine);
        if (fields == null) {
            return ParseResult.malformed(MalformedReason.INVALID_JSON);
        }

        if (hasMissingOrBlank(fields, REQUIRED_FIELDS)) {
            return ParseResult.malformed(MalformedReason.MISSING_FIELD);
        }

        String requestId = fields.get("request_id");
        String clientId = fields.get("client_id");
        String endpoint = fields.get("endpoint");

        if (endpoint.isBlank()) {
            return ParseResult.malformed(MalformedReason.INVALID_ENDPOINT);
        }

        int status;
        try {
            status = Integer.parseInt(fields.get("status_code"));
        } catch (NumberFormatException e) {
            return ParseResult.malformed(MalformedReason.INVALID_STATUS);
        }
        if (status < 100 || status > 599) {
            return ParseResult.malformed(MalformedReason.INVALID_STATUS);
        }

        Instant ts;
        try {
            // OffsetDateTime handles both "Z" and "+HH:MM" style offsets;
            // Instant.parse() alone only accepts "Z".
            ts = OffsetDateTime.parse(fields.get("timestamp")).toInstant();
        } catch (DateTimeParseException e) {
            return ParseResult.malformed(MalformedReason.BAD_TIMESTAMP);
        }

        return ParseResult.ok(new TrafficRecord(requestId, ts, clientId, endpoint, status));
    }

    /**
     * Extracts top-level "key": value pairs from a flat JSON object line.
     * Returns null if the line doesn't look like a JSON object at all
     * (missing braces), which is treated as INVALID_JSON.
     */
    private Map<String, String> extractFields(String rawLine) {
        String trimmed = rawLine.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            String key = matcher.group(1);
            // group(2) = quoted string value, group(3) = bare numeric value
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            fields.put(key, value);
        }
        return fields;
    }

    private boolean hasMissingOrBlank(Map<String, String> fields, String... requiredKeys) {
        for (String key : requiredKeys) {
            String value = fields.get(key);
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}