package com.synthdetect.common.util;

import com.synthdetect.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Base64;
import java.util.Set;

public final class ContentValidator {

    private static final Set<String> SUPPORTED_IMAGE_FORMATS = Set.of(
            "jpeg", "jpg", "png", "webp", "gif", "bmp"
    );

    private static final int MAX_IMAGE_SIZE_BYTES = 20 * 1024 * 1024; // 20MB
    private static final int MIN_TEXT_CHARS = 50;
    private static final int MAX_TEXT_CHARS = 100_000;

    private ContentValidator() {
    }

    public static byte[] validateAndDecodeBase64Image(String base64Image) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Image);
            if (decoded.length > MAX_IMAGE_SIZE_BYTES) {
                throw new ApiException("IMAGE_TOO_LARGE",
                        "Image size exceeds maximum of 20MB.", HttpStatus.BAD_REQUEST);
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_BASE64",
                    "Invalid base64 encoded image data.", HttpStatus.BAD_REQUEST);
        }
    }

    public static void validateImageFormat(String format) {
        if (format == null || !SUPPORTED_IMAGE_FORMATS.contains(format.toLowerCase())) {
            throw new ApiException("UNSUPPORTED_FORMAT",
                    "Unsupported image format. Supported: " + SUPPORTED_IMAGE_FORMATS,
                    HttpStatus.BAD_REQUEST);
        }
    }

    public static void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new ApiException("EMPTY_TEXT", "Text content is required.", HttpStatus.BAD_REQUEST);
        }
        if (text.length() < MIN_TEXT_CHARS) {
            throw new ApiException("TEXT_TOO_SHORT",
                    "Text must be at least " + MIN_TEXT_CHARS + " characters.", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new ApiException("TEXT_TOO_LONG",
                    "Text must not exceed " + MAX_TEXT_CHARS + " characters.", HttpStatus.BAD_REQUEST);
        }
    }
}
