package com.synthdetect.auth.service;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.common.exception.InvalidApiKeyException;
import com.synthdetect.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKey authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new InvalidApiKeyException("API key is required.");
        }

        if (!rawApiKey.startsWith("sd_")) {
            throw new InvalidApiKeyException("Invalid API key format.");
        }

        String keyHash = HashUtil.sha256(rawApiKey);

        ApiKey apiKey = apiKeyRepository.findActiveByKeyHash(keyHash)
                .orElseThrow(() -> new InvalidApiKeyException("API key not found or has been revoked."));

        if (!apiKey.isValid()) {
            throw new InvalidApiKeyException("API key has expired or been revoked.");
        }

        if (!apiKey.getUser().isActive()) {
            throw new InvalidApiKeyException("Account associated with this API key is not active.");
        }

        return apiKey;
    }
}
