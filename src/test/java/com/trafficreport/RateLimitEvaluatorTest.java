package com.trafficreport;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RateLimitEvaluatorTest {

    @Test
    void allowsRequestsUnderTheLimit() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(Duration.ofSeconds(60), 3);
        Instant base = Instant.parse("2024-01-15T10:00:00Z");

        assertFalse(evaluator.recordAndCheck("acct_1", base));
        assertFalse(evaluator.recordAndCheck("acct_1", base.plusSeconds(1)));
        assertFalse(evaluator.recordAndCheck("acct_1", base.plusSeconds(2)));
    }

    @Test
    void flagsClientExactlyAtTheThresholdAsNotYetViolating() {
        // maxRequests = 3 means the 4th request within the window is the violation,
        // not the 3rd — "at" the limit is allowed, "over" it is not.
        RateLimitEvaluator evaluator = new RateLimitEvaluator(Duration.ofSeconds(60), 3);
        Instant base = Instant.parse("2024-01-15T10:00:00Z");

        evaluator.recordAndCheck("acct_1", base);
        evaluator.recordAndCheck("acct_1", base.plusSeconds(1));
        boolean thirdRequest = evaluator.recordAndCheck("acct_1", base.plusSeconds(2));

        assertFalse(thirdRequest, "the 3rd request should not itself be a violation when the limit is 3");
    }

    @Test
    void flagsClientOverTheThreshold() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(Duration.ofSeconds(60), 3);
        Instant base = Instant.parse("2024-01-15T10:00:00Z");

        evaluator.recordAndCheck("acct_1", base);
        evaluator.recordAndCheck("acct_1", base.plusSeconds(1));
        evaluator.recordAndCheck("acct_1", base.plusSeconds(2));
        boolean fourthRequest = evaluator.recordAndCheck("acct_1", base.plusSeconds(3));

        assertTrue(fourthRequest, "the 4th request within the window should trip the limit");
    }

    @Test
    void evictsTimestampsOutsideTheWindow() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(Duration.ofSeconds(10), 2);
        Instant base = Instant.parse("2024-01-15T10:00:00Z");

        evaluator.recordAndCheck("acct_1", base);
        evaluator.recordAndCheck("acct_1", base.plusSeconds(1));
        // A third request 30 seconds later is outside the 10-second window,
        // so the first two timestamps should have aged out and this should not violate.
        boolean laterRequest = evaluator.recordAndCheck("acct_1", base.plusSeconds(30));

        assertFalse(laterRequest);
    }

    @Test
    void tracksEachClientIndependently() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(Duration.ofSeconds(60), 1);
        Instant base = Instant.parse("2024-01-15T10:00:00Z");

        evaluator.recordAndCheck("acct_1", base);
        boolean acct1SecondRequest = evaluator.recordAndCheck("acct_1", base.plusSeconds(1));
        boolean acct2FirstRequest = evaluator.recordAndCheck("acct_2", base.plusSeconds(1));

        assertTrue(acct1SecondRequest, "acct_1's 2nd request should violate a limit of 1");
        assertFalse(acct2FirstRequest, "acct_2's 1st request should not be affected by acct_1's history");
    }

    @Test
    void constructorRejectsNullWindow() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitEvaluator(null, 10));
    }

    @Test
    void constructorRejectsNonPositiveMaxRequests() {
        assertThrows(IllegalArgumentException.class,
            () -> new RateLimitEvaluator(Duration.ofSeconds(60), 0));
    }
}