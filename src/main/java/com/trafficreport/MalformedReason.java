package com.trafficreport;
 
/**
 * Reasons a raw log line can be rejected by {@link TrafficRecordParser}.
 * Kept as a dedicated enum, rather than a plain String, so the discard
 * reasons are fixed and typo-proof, and so the report can group and
 * count them reliably.
 */
public enum MalformedReason {
    INVALID_JSON,
    MISSING_FIELD,
    BAD_TIMESTAMP,
    INVALID_STATUS,
    INVALID_ENDPOINT
}
 