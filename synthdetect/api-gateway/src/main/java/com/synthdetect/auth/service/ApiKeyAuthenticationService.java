package com.synthdetect.auth.service;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.auth.service.ApiKeyCacheService.ApiKeyCacheEntry;
import com.synthdetect.common.exception.InvalidApiKeyException;
import com.synthdetect.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyCacheService cacheService;

    public ApiKey authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new InvalidApiKeyException("API key is required.");
        }

        if (!rawApiKey.startsWith("sd_")) {
            throw new InvalidApiKeyException("Invalid API key format.");
        }

        String keyHash = HashUtil.sha256(rawApiKey);

        // 1. Try cache first
        Optional<ApiKeyCacheEntry> cached = cacheService.get(keyHash);
        if (cached.isPresent()) {
            ApiKeyCacheEntry entry = cached.get();
            if (!"ACTIVE".equals(entry.status())) {
                throw new InvalidApiKeyException("API key has been revoked.");
            }
            if (!entry.userActive()) {
                throw new InvalidApiKeyException("Account associated with this API key is not active.");
            }
            log.debug("API key authenticated from cache");
            // Return a minimal stub — filter only needs userId and scopes from the cache entry
            return buildFromCache(entry, keyHash);
        }

        // 2. Cache miss — hit DB
        ApiKey apiKey = apiKeyRepository.findActiveByKeyHash(keyHash)
                .orElseThrow(() -> new InvalidApiKeyException("API key not found or has been revoked."));

        if (!apiKey.isValid()) {
            throw new InvalidApiKeyException("API key has expired or been revoked.");
        }

        if (!apiKey.getUser().isActive()) {
            throw new InvalidApiKeyException("Account associated with this API key is not active.");
        }

        // 3. Populate cache for next request
        cacheService.put(apiKey);

        return apiKey;
    }

    private ApiKey buildFromCache(ApiKeyCacheEntry entry, String keyHash) {
        // Build a lightweight ApiKey from cache — avoids DB round-trip
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash(keyHash);
        apiKey.setScopes(entry.scopes());
        // User and ID not needed by filter beyond userId (extracted via entry.userId())
        return apiKey;
    }
}
