package com.trafficreport;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a TrafficReport as either a human-readable text summary
 * or a JSON document. No external JSON library is used — output is
 * built directly, since the report's shape is fixed and known.
 */
public class TrafficReportRenderer {

    public String render(TrafficReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== API Traffic Report ===\n\n");

        int valid = report.totalRequestsProcessed() - report.totalMalformed() - report.duplicateRequestIds();
        sb.append("Total requests processed: ").append(report.totalRequestsProcessed()).append("\n");
        sb.append("Valid requests: ").append(valid).append("\n");
        sb.append("Malformed (discarded): ").append(report.totalMalformed()).append("\n");
        report.malformedByReason().forEach((reason, count) ->
            sb.append("  - ").append(reason).append(": ").append(count).append("\n"));
        sb.append("Duplicate request IDs: ").append(report.duplicateRequestIds()).append("\n\n");

        sb.append("Requests by endpoint:\n");
        report.requestsByEndpoint().forEach((endpoint, count) ->
            sb.append("  ").append(endpoint).append(": ").append(count).append("\n"));

        sb.append("\nRate limit violations:\n");
        if (report.violationsByClient().isEmpty()) {
            sb.append("  None detected.\n");
        } else {
            report.topOffenders(10).forEach(entry ->
                sb.append("  ").append(entry.getKey()).append(": ")
                  .append(entry.getValue()).append(" violation(s)\n"));
        }

        return sb.toString();
    }

    /**
     * Produces a single JSON object summarizing traffic, discards, and
     * rate-limit violations. Map entries are emitted in sorted key order
     * so output is deterministic across runs, which makes it easier to
     * diff or test against.
     */
    public String renderJson(TrafficReport report) {
        int valid = report.totalRequestsProcessed() - report.totalMalformed() - report.duplicateRequestIds();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"totalRequestsProcessed\": ").append(report.totalRequestsProcessed()).append(",\n");
        json.append("  \"validRequests\": ").append(valid).append(",\n");
        json.append("  \"duplicateRequestIds\": ").append(report.duplicateRequestIds()).append(",\n");

        json.append("  \"malformed\": {\n");
        json.append("    \"total\": ").append(report.totalMalformed()).append(",\n");
        json.append("    \"byReason\": ").append(intMapToJson(report.malformedByReason(), 4)).append("\n");
        json.append("  },\n");

        json.append("  \"requestsByEndpoint\": ").append(intMapToJson(report.requestsByEndpoint(), 2)).append(",\n");

        json.append("  \"violations\": {\n");
        json.append("    \"byClient\": ").append(intMapToJson(report.violationsByClient(), 4)).append(",\n");
        json.append("    \"topOffenders\": ").append(topOffendersToJson(report.topOffenders(10))).append("\n");
        json.append("  }\n");

        json.append("}");
        return json.toString();
    }

    private String intMapToJson(Map<?, Integer> map, int indentSpaces) {
        if (map.isEmpty()) {
            return "{}";
        }
        String indent = " ".repeat(indentSpaces);
        String innerIndent = " ".repeat(indentSpaces + 2);

        StringBuilder sb = new StringBuilder("{\n");
        Map<String, Integer> sorted = new TreeMap<>();
        map.forEach((key, value) -> sorted.put(String.valueOf(key), value));
        int i = 0;
        int size = sorted.size();
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            sb.append(innerIndent)
              .append("\"").append(escape(entry.getKey())).append("\": ")
              .append(entry.getValue());
            if (++i < size) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("}");
        return sb.toString();
    }

    private String topOffendersToJson(List<Map.Entry<String, Integer>> topOffenders) {
        if (topOffenders.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < topOffenders.size(); i++) {
            Map.Entry<String, Integer> entry = topOffenders.get(i);
            sb.append("      { \"clientId\": \"").append(escape(entry.getKey()))
              .append("\", \"violations\": ").append(entry.getValue()).append(" }");
            if (i < topOffenders.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("    ]");
        return sb.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}