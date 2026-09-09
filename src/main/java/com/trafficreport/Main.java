package com.trafficreport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Entry point. Reads a newline-delimited JSON request log, runs it through
 * the parser/aggregator/rate-limit pipeline, and prints the resulting report.
 *
 * Usage:
 *   java -jar api-traffic-report.jar <path-to-log-file> [--format json]
 *        [--window-seconds N] [--max-requests N]
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar api-traffic-report.jar <log-file> [--format text|json] [--window-seconds N] [--max-requests N]");
            System.exit(1);
        }

        Path logFile = Path.of(args[0]);
        String format = "text";
        int windowSeconds = 60;
        int maxRequests = 100;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--format" -> format = args[++i];
                case "--window-seconds" -> windowSeconds = Integer.parseInt(args[++i]);
                case "--max-requests" -> maxRequests = Integer.parseInt(args[++i]);
                default -> {
                    System.err.println("Unrecognized argument: " + args[i]);
                    System.exit(1);
                }
            }
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(logFile);
        } catch (IOException e) {
            System.err.println("Could not read file: " + logFile + " (" + e.getMessage() + ")");
            System.exit(1);
            return;
        }

        TrafficRecordParser parser = new TrafficRecordParser();
        RateLimitEvaluator rateLimitEvaluator =
            new RateLimitEvaluator(Duration.ofSeconds(windowSeconds), maxRequests);
        TrafficReportAggregator aggregator = new TrafficReportAggregator(parser, rateLimitEvaluator);

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            aggregator.process(line);
        }

        TrafficReport report = aggregator.buildReport();
        TrafficReportRenderer renderer = new TrafficReportRenderer();

        String output = "json".equalsIgnoreCase(format)
        ? renderer.renderJson(report)
        : renderer.render(report);

        System.out.println(output);
    }
}