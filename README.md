# API Traffic Report

A Java program that ingests a stream of API request log records, flags clients that exceed reasonable HTTP rate limits, and produces a traffic report covering request volume, per-endpoint distribution, and malformed input handling.

## What it does

- Parses newline-delimited JSON request records
- Detects clients whose request rate exceeds a configurable threshold within a sliding time window
- Discards malformed records without halting processing, and counts them by failure reason
- Produces a human-readable (or JSON) report summarizing traffic, violations, and discards

## Input format

Each line is a single JSON object:

```json
{"request_id":"a1_1","timestamp":"2024-01-15T10:00:00Z","client_id":"acct_1","endpoint":"/v1/widgets","status_code":200}
```

| Field | Type | Meaning |
|---|---|---|
| `request_id` | string | Unique ID for the request. |
| `timestamp` | string | ISO-8601 / RFC 3339 timestamp (may use `Z` or a `+HH:MM` offset). |
| `client_id` | string | Who made the request. |
| `endpoint` | string | Path that was called. |
| `status_code` | integer | HTTP status code (100–599). |

## Assumptions

Since the original brief left some decisions open, these were resolved as follows. Adjust `RateLimitConfig` if a different policy is needed.

- A client is identified by `client_id`, not IP, since IP is unreliable behind shared gateways
- Rate limiting uses a sliding window log (per client) rather than a fixed window, to avoid boundary-burst undercounting
- Default threshold: configurable requests per configurable window (see Configuration below)
- Duplicate `request_id` values are tracked separately from malformed records, since they usually indicate a replay or logging issue rather than a bad client request
- Malformed records are discarded and counted by reason rather than dropped silently

## Project structure

```
src/main/java/traffic/
  TrafficRecord.java          # parsed record model
  ParseResult.java            # sealed Ok / Malformed result type
  MalformedReason.java        # enum of discard reasons
  TrafficRecordParser.java    # JSON parsing and validation
  RateLimitEvaluator.java     # sliding window violation detection
  TrafficReportAggregator.java # accumulates counts as records stream through
  TrafficReport.java          # final report data model
  TrafficReportRenderer.java  # text/JSON output rendering
  Main.java                   # wiring and entry point
```

## Configuration

| Setting | Description | Default |
|---|---|---|
| `window.duration` | Sliding window size | 60s |
| `window.maxRequests` | Requests allowed per client per window | 100 |
| `report.topOffenders` | Number of top violators shown in the report | 10 |

## Running it

```bash
mvn clean package
java -jar target/api-traffic-report.jar path/to/requests.log
```

Output is written to stdout by default. Pass `--format json` to emit a structured report instead of the text summary.

## Sample output

```
=== API Traffic Report ===

Total requests processed: 5000
Valid requests: 4891
Malformed (discarded): 94
  - MISSING_FIELD: 41
  - BAD_TIMESTAMP: 30
  - INVALID_STATUS: 23
Duplicate request IDs: 15

Requests by endpoint:
  /v1/widgets: 2103
  /v1/orders: 1788
  /v1/users: 1000

Rate limit violations:
  acct_42: 6 window(s) over limit
  acct_7: 2 window(s) over limit
```

## Testing

Run the test suite with:

```bash
mvn test
```

Coverage includes malformed input handling (bad JSON, missing fields, invalid status codes, invalid timestamps), duplicate request ID detection, and rate limit boundary behavior (a client sitting exactly at the threshold, and one just over it).

## Rate-limit rule chosen, and why

Sliding window log, per client_id, with a configurable window duration and request ceiling (default: 100 requests per 60-second window).
This was chosen over the alternatives for the following reasons:
- Fixed window was ruled out because it undercounts abuse at window boundaries, a client can send the full limit at 0:59 and again at 1:01 and never trip a fixed-window check, despite sending double the allowed rate in two seconds.
- Token bucket and leaky bucket are well suited to live traffic shaping at request time, but this program evaluates a completed log after the fact, so there's no need for the smoothing behavior they provide.
- Sliding window counter (the approximated, memory-cheaper cousin of sliding window log) was considered, but with request volumes at the scale of a single log file, exact per-timestamp tracking is affordable and gives an exact violation count rather than an estimate.

Rate limiting is keyed on client_id rather than IP, since IP is unreliable behind shared gateways, proxies, or NAT and would misattribute traffic from multiple legitimate clients to one bucket.

## Output specification

The report includes:
- Total requests processed: every line read from input, valid or not
- Valid requests: records that passed parsing and were not duplicates
- Malformed (discarded) count, broken down by reason: invalid JSON, missing field, invalid timestamp, invalid status code, invalid endpoint
- Duplicate request ID count
- Requests by endpoint: total valid requests per endpoint
- Rate limit violations: per client, the number of windows in which that client exceeded the threshold, sorted with the worst offenders first

## Possible extensions

- Track peak requests-in-window per client alongside violation count, so the report distinguishes a borderline client from a genuinely abusive one instead of flattening both into the same number.
- Split rate-limit evaluation by endpoint as well as by client, since a single expensive endpoint can make a normal client look like a rate-limit violator under a client-only rule.
- Add a streaming/live mode in addition to the current batch-over-a-file mode, so the same evaluator could sit behind a real-time ingestion pipeline.
- Expand the test suite to cover boundary conditions more thoroughly: a request landing exactly at the window edge, out-of-order timestamps in the input, and very large files to confirm memory stays bounded.