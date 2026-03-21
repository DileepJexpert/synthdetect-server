package com.synthdetect.auth;

import com.synthdetect.common.exception.ApiException;
import com.synthdetect.config.JwtService;
import com.synthdetect.user.dto.AuthResponse;
import com.synthdetect.user.dto.LoginRequest;
import com.synthdetect.user.dto.RegisterRequest;
import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserPlan;
import com.synthdetect.user.model.UserStatus;
import com.synthdetect.user.repository.UserRepository;
import com.synthdetect.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .monthlyQuota(500)
                .rateLimitRpm(30)
                .build();
    }

    @Test
    void register_shouldCreateUserAndReturnTokens() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("securePassword123")
                .companyName("Test Corp")
                .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("securePassword123")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jwtService.generateAccessToken(any(), eq("new@example.com"))).thenReturn("access_token");
        when(jwtService.generateRefreshToken(any(), eq("new@example.com"))).thenReturn("refresh_token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getEmail()).isEqualTo("new@example.com");
        assertThat(response.getUser().getPlan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    void register_shouldFailWithDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void login_shouldSucceedWithValidCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("correctPassword")
                .build();

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", testUser.getPasswordHash())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(userId, "test@example.com")).thenReturn("access_token");
        when(jwtService.generateRefreshToken(userId, "test@example.com")).thenReturn("refresh_token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access_token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void login_shouldFailWithInvalidPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldFailWithNonExistentEmail() {
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("password")
                .build();

        when(userRepository.findByEmailAndDeletedAtIsNull("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldFailWithSuspendedAccount() {
        testUser.setStatus(UserStatus.SUSPENDED);
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password")
                .build();

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void getProfile_shouldReturnUserResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        var response = userService.getProfile(userId);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getPlan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    void findActiveUser_shouldThrowWhenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findActiveUser(unknownId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}
