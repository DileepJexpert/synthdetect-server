package com.synthdetect.common.util;

import java.security.SecureRandom;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private IdGenerator() {
    }

    public static String generate(String prefix, int randomLength) {
        StringBuilder sb = new StringBuilder(prefix.length() + randomLength);
        sb.append(prefix);
        for (int i = 0; i < randomLength; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    public static String detectionId() {
        return generate("det_", 6);
    }

    public static String batchId() {
        return generate("bat_", 6);
    }

    public static String requestId() {
        return generate("req_", 6);
    }
}
