package com.synthdetect.config;

import com.synthdetect.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Sliding window rate limiter using Redis.
     * Returns true if the request is allowed, throws RateLimitExceededException otherwise.
     */
    public void checkRateLimit(UUID userId, int maxRequestsPerMinute) {
        String key = "rate_limit:" + userId.toString();

        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        if (currentCount != null && currentCount > maxRequestsPerMinute) {
            Long ttl = redisTemplate.getExpire(key);
            int retryAfter = ttl != null && ttl > 0 ? ttl.intValue() : 60;
            throw new RateLimitExceededException(retryAfter);
        }
    }
}
