package com.synthdetect.auth;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.model.ApiKeyStatus;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.auth.service.ApiKeyAuthenticationService;
import com.synthdetect.auth.service.ApiKeyService;
import com.synthdetect.common.exception.ApiException;
import com.synthdetect.common.exception.InvalidApiKeyException;
import com.synthdetect.common.util.HashUtil;
import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserPlan;
import com.synthdetect.user.model.UserStatus;
import com.synthdetect.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("hashed")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .monthlyQuota(500)
                .rateLimitRpm(30)
                .build();
    }

    @Test
    void createApiKey_shouldReturnKeyWithPrefix() {
        when(userService.findActiveUser(userId)).thenReturn(testUser);
        when(apiKeyRepository.countByUserIdAndStatus(userId, ApiKeyStatus.ACTIVE)).thenReturn(0L);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(Instant.now());
            return key;
        });

        Map<String, Object> result = apiKeyService.createApiKey(userId, "Test Key", null, "live");

        assertThat(result).containsKey("key");
        assertThat(result.get("key").toString()).startsWith("sd_live_");
        assertThat(result.get("key").toString()).hasSize(40); // "sd_live_" (8) + 32 random chars
        assertThat(result.get("name")).isEqualTo("Test Key");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void createApiKey_shouldFailWhenMaxKeysReached() {
        when(userService.findActiveUser(userId)).thenReturn(testUser);
        when(apiKeyRepository.countByUserIdAndStatus(userId, ApiKeyStatus.ACTIVE)).thenReturn(10L);

        assertThatThrownBy(() -> apiKeyService.createApiKey(userId, "Test", null, "live"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Maximum of 10");
    }

    @Test
    void createApiKey_shouldCreateTestKey() {
        when(userService.findActiveUser(userId)).thenReturn(testUser);
        when(apiKeyRepository.countByUserIdAndStatus(userId, ApiKeyStatus.ACTIVE)).thenReturn(0L);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(Instant.now());
            return key;
        });

        Map<String, Object> result = apiKeyService.createApiKey(userId, "Test Key", null, "test");

        assertThat(result.get("key").toString()).startsWith("sd_test_");
    }

    @Test
    void revokeApiKey_shouldSetStatusToRevoked() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .user(testUser)
                .keyHash("hash")
                .keyPrefix("sd_live_xxxx")
                .status(ApiKeyStatus.ACTIVE)
                .build();

        when(apiKeyRepository.findByIdAndUserId(keyId, userId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);

        apiKeyService.revokeApiKey(userId, keyId);

        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(apiKey.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeApiKey_shouldFailWhenNotFound() {
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findByIdAndUserId(keyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(userId, keyId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}
