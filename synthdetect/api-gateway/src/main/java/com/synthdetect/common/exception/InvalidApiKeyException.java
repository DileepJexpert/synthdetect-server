package com.synthdetect.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidApiKeyException extends ApiException {

    public InvalidApiKeyException() {
        super("INVALID_API_KEY", "Invalid or missing API key.", HttpStatus.UNAUTHORIZED);
    }

    public InvalidApiKeyException(String message) {
        super("INVALID_API_KEY", message, HttpStatus.UNAUTHORIZED);
    }
}
