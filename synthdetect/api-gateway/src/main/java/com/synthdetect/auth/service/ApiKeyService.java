package com.synthdetect.auth.service;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.model.ApiKeyStatus;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.common.exception.ApiException;
import com.synthdetect.common.util.HashUtil;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final int MAX_KEYS_PER_USER = 10;
    private static final String KEY_PREFIX_LIVE = "sd_live_";
    private static final String KEY_PREFIX_TEST = "sd_test_";

    private final ApiKeyRepository apiKeyRepository;
    private final UserService userService;

    @Transactional
    public Map<String, Object> createApiKey(UUID userId, String name, String[] scopes, String environment) {
        User user = userService.findActiveUser(userId);

        long activeKeys = apiKeyRepository.countByUserIdAndStatus(userId, ApiKeyStatus.ACTIVE);
        if (activeKeys >= MAX_KEYS_PER_USER) {
            throw new ApiException("MAX_KEYS_REACHED",
                    "Maximum of " + MAX_KEYS_PER_USER + " active API keys allowed.", HttpStatus.BAD_REQUEST);
        }

        String prefix = "live".equals(environment) ? KEY_PREFIX_LIVE : KEY_PREFIX_TEST;
        String randomPart = HashUtil.generateSecureRandomString(32);
        String fullKey = prefix + randomPart;

        String keyHash = HashUtil.sha256(fullKey);
        String keyPrefixDisplay = fullKey.substring(0, Math.min(12, fullKey.length()));

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .keyHash(keyHash)
                .keyPrefix(keyPrefixDisplay)
                .name(name != null ? name : "Default")
                .scopes(scopes != null ? String.join(",", scopes) : "detect")
                .build();

        apiKey = apiKeyRepository.save(apiKey);
        log.info("API key created: id={}, userId={}, prefix={}", apiKey.getId(), userId, keyPrefixDisplay);

        // Return the full key only once — it won't be retrievable again
        return Map.of(
                "id", apiKey.getId(),
                "key", fullKey,
                "key_prefix", keyPrefixDisplay,
                "name", apiKey.getName(),
                "scopes", apiKey.getScopesArray(),
                "created_at", apiKey.getCreatedAt() != null ? apiKey.getCreatedAt() : Instant.now()
        );
    }

    public List<Map<String, Object>> listApiKeys(UUID userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(key -> Map.<String, Object>of(
                        "id", key.getId(),
                        "key_prefix", key.getKeyPrefix(),
                        "name", key.getName(),
                        "status", key.getStatus().name(),
                        "scopes", key.getScopesArray(),
                        "last_used_at", key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : "never",
                        "total_calls", key.getTotalCalls(),
                        "created_at", key.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void revokeApiKey(UUID userId, UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ApiException("KEY_NOT_FOUND", "API key not found.", HttpStatus.NOT_FOUND));

        if (apiKey.getStatus() == ApiKeyStatus.REVOKED) {
            throw new ApiException("KEY_ALREADY_REVOKED", "API key is already revoked.", HttpStatus.BAD_REQUEST);
        }

        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        log.info("API key revoked: id={}, userId={}", keyId, userId);
    }

    @Transactional
    public void recordUsage(UUID keyId) {
        apiKeyRepository.updateUsage(keyId, Instant.now());
    }
}
