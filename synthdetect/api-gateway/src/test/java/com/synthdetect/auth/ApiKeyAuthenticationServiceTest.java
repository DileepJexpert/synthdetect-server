package com.synthdetect.auth;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.model.ApiKeyStatus;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.auth.service.ApiKeyAuthenticationService;
import com.synthdetect.common.exception.InvalidApiKeyException;
import com.synthdetect.common.util.HashUtil;
import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyAuthenticationService authService;

    @Test
    void authenticate_shouldSucceedWithValidKey() {
        String rawKey = "sd_live_abcdefghijklmnopqrstuvwxyz123456";
        String keyHash = HashUtil.sha256(rawKey);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .status(UserStatus.ACTIVE)
                .build();

        ApiKey apiKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .user(user)
                .keyHash(keyHash)
                .keyPrefix("sd_live_abcd")
                .status(ApiKeyStatus.ACTIVE)
                .scopes("detect")
                .build();

        when(apiKeyRepository.findActiveByKeyHash(keyHash)).thenReturn(Optional.of(apiKey));

        ApiKey result = authService.authenticate(rawKey);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(apiKey.getId());
    }

    @Test
    void authenticate_shouldFailWithNullKey() {
        assertThatThrownBy(() -> authService.authenticate(null))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void authenticate_shouldFailWithInvalidPrefix() {
        assertThatThrownBy(() -> authService.authenticate("invalid_key_format"))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("format");
    }

    @Test
    void authenticate_shouldFailWithNonExistentKey() {
        String rawKey = "sd_live_nonexistent12345678901234567";
        String keyHash = HashUtil.sha256(rawKey);
        when(apiKeyRepository.findActiveByKeyHash(keyHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate(rawKey))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void authenticate_shouldFailWithSuspendedUser() {
        String rawKey = "sd_live_abcdefghijklmnopqrstuvwxyz123456";
        String keyHash = HashUtil.sha256(rawKey);

        User suspendedUser = User.builder()
                .id(UUID.randomUUID())
                .email("suspended@example.com")
                .status(UserStatus.SUSPENDED)
                .build();

        ApiKey apiKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .user(suspendedUser)
                .keyHash(keyHash)
                .keyPrefix("sd_live_abcd")
                .status(ApiKeyStatus.ACTIVE)
                .build();

        when(apiKeyRepository.findActiveByKeyHash(keyHash)).thenReturn(Optional.of(apiKey));

        assertThatThrownBy(() -> authService.authenticate(rawKey))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("not active");
    }
}
