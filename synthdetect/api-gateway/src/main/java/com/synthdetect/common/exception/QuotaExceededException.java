package com.synthdetect.common.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public class QuotaExceededException extends ApiException {

    public QuotaExceededException(String message) {
        super("QUOTA_EXCEEDED", message, HttpStatus.PAYMENT_REQUIRED, null);
    }

    public QuotaExceededException(int currentUsage, int monthlyLimit, Instant resetsAt) {
        super("QUOTA_EXCEEDED",
                "Monthly API call quota exceeded. Upgrade plan or wait for reset.",
                HttpStatus.PAYMENT_REQUIRED,
                Map.of(
                        "current_usage", currentUsage,
                        "monthly_limit", monthlyLimit,
                        "resets_at", resetsAt.toString()
                ));
    }
}
