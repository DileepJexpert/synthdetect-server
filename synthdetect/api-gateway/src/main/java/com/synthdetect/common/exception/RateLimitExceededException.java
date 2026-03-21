package com.synthdetect.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException() {
        super("RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Please slow down.", HttpStatus.TOO_MANY_REQUESTS);
    }

    public RateLimitExceededException(int retryAfterSeconds) {
        super("RATE_LIMIT_EXCEEDED",
                "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.",
                HttpStatus.TOO_MANY_REQUESTS,
                java.util.Map.of("retry_after_seconds", retryAfterSeconds));
    }
}
