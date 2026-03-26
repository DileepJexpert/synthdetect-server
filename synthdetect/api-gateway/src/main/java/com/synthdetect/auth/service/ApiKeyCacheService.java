package com.synthdetect.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.model.ApiKeyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis cache layer for API key lookups.
 * Caches a lightweight ApiKeyCacheEntry (not the full JPA entity) keyed by keyHash.
 * TTL: 5 minutes. On revoke/change, cache entry is evicted immediately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyCacheService {

    private static final String PREFIX = "apikey:";
    private static final String REVOKED_PREFIX = "apikey:revoked:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration REVOKED_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public record ApiKeyCacheEntry(
            String keyHash,
            String keyId,
            String userId,
            String status,
            String scopes,
            boolean userActive
    ) {}

    public Optional<ApiKeyCacheEntry> get(String keyHash) {
        try {
            // Fast check: is this key in the revocation set?
            Boolean revoked = (Boolean) redisTemplate.opsForValue().get(REVOKED_PREFIX + keyHash);
            if (Boolean.TRUE.equals(revoked)) {
                return Optional.empty();
            }

            Object value = redisTemplate.opsForValue().get(PREFIX + keyHash);
            if (value == null) return Optional.empty();

            ApiKeyCacheEntry entry = objectMapper.convertValue(value, ApiKeyCacheEntry.class);
            return Optional.of(entry);
        } catch (Exception e) {
            log.warn("Redis cache get failed for key, falling through to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(ApiKey apiKey) {
        try {
            ApiKeyCacheEntry entry = new ApiKeyCacheEntry(
                    apiKey.getKeyHash(),
                    apiKey.getId().toString(),
                    apiKey.getUser().getId().toString(),
                    apiKey.getStatus().name(),
                    apiKey.getScopes(),
                    apiKey.getUser().isActive()
            );
            redisTemplate.opsForValue().set(PREFIX + apiKey.getKeyHash(), entry, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis cache put failed, continuing without cache: {}", e.getMessage());
        }
    }

    public void evict(String keyHash) {
        try {
            redisTemplate.delete(PREFIX + keyHash);
            // Mark as explicitly revoked to avoid stale cache hits during TTL window
            redisTemplate.opsForValue().set(REVOKED_PREFIX + keyHash, Boolean.TRUE, REVOKED_TTL);
        } catch (Exception e) {
            log.warn("Redis cache evict failed: {}", e.getMessage());
        }
    }

    public void evictByKeyId(UUID keyId) {
        // We don't store by keyId, eviction is by hash — caller should pass hash
        log.debug("evictByKeyId called for {}, use evict(keyHash) for direct eviction", keyId);
    }
}
