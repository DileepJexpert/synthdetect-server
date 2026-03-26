package com.synthdetect.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Blacklists JWT tokens in Redis so they cannot be reused after logout.
 * Key: "jwt:blacklist:<jti>", TTL = remaining token lifetime.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void blacklist(String jti, Instant expiresAt) {
        try {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (ttl.isNegative() || ttl.isZero()) return; // already expired
            redisTemplate.opsForValue().set(PREFIX + jti, Boolean.TRUE, ttl);
            log.debug("Token blacklisted jti={} ttl={}s", jti, ttl.getSeconds());
        } catch (Exception e) {
            log.warn("Failed to blacklist token jti={}: {}", jti, e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().get(PREFIX + jti));
        } catch (Exception e) {
            log.warn("Failed to check token blacklist jti={}: {}", jti, e.getMessage());
            return false; // fail open — prefer availability
        }
    }
}
