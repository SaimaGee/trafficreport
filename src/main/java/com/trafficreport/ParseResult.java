package com.trafficreport;

public sealed interface ParseResult permits ParseResult.Ok, ParseResult.Malformed {
 
    static ParseResult ok(TrafficRecord record) {
        return new Ok(record);
    }
 
    static ParseResult malformed(MalformedReason reason) {
        return new Malformed(reason);
    }
 
    record Ok(TrafficRecord record) implements ParseResult {

        public Ok(TrafficRecord record) {
            this.record = record;
        }
    }
 
    record Malformed(MalformedReason reason) implements ParseResult {

        public Malformed(MalformedReason reason) {
            this.reason = reason;
        }
    }
}