package com.synthdetect.user.service;

import com.synthdetect.common.exception.ApiException;
import com.synthdetect.common.util.HashUtil;
import com.synthdetect.config.JwtService;
import com.synthdetect.user.dto.*;
import com.synthdetect.user.model.EmailToken;
import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserPlan;
import com.synthdetect.user.repository.EmailTokenRepository;
import com.synthdetect.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailTokenRepository emailTokenRepository;
    private final EmailService emailService;
    private final com.synthdetect.auth.service.TokenBlacklistService tokenBlacklistService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new ApiException("EMAIL_EXISTS", "An account with this email already exists.", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .companyName(request.getCompanyName())
                .plan(UserPlan.FREE)
                .monthlyQuota(UserPlan.FREE.getMonthlyQuota())
                .rateLimitRpm(UserPlan.FREE.getRateLimitRpm())
                .build();

        user = userRepository.save(user);
        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());

        // Send verification email async
        String rawToken = generateSecureToken();
        saveEmailToken(user, rawToken, EmailToken.Type.EMAIL_VERIFICATION, 24);
        emailService.sendVerificationEmail(user.getEmail(), rawToken);

        return buildAuthResponse(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailToken token = findValidToken(rawToken, EmailToken.Type.EMAIL_VERIFICATION);
        token.getUser().setEmailVerified(true);
        token.setUsedAt(Instant.now());
        userRepository.save(token.getUser());
        emailTokenRepository.save(token);
        log.info("Email verified for user={}", token.getUser().getId());
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailAndDeletedAtIsNull(email.toLowerCase().trim())
                .ifPresent(user -> {
                    // Invalidate any existing reset tokens
                    emailTokenRepository.deleteUnusedByUserAndType(user.getId(), EmailToken.Type.PASSWORD_RESET);
                    String rawToken = generateSecureToken();
                    saveEmailToken(user, rawToken, EmailToken.Type.PASSWORD_RESET, 1);
                    emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
                    log.info("Password reset email sent for user={}", user.getId());
                });
        // Always return success to prevent email enumeration
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException("WEAK_PASSWORD", "Password must be at least 8 characters.", HttpStatus.BAD_REQUEST);
        }
        EmailToken token = findValidToken(rawToken, EmailToken.Type.PASSWORD_RESET);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(Instant.now());
        userRepository.save(user);
        emailTokenRepository.save(token);
        log.info("Password reset completed for user={}", user.getId());
    }

    @Transactional
    public void resendVerification(UUID userId) {
        User user = findActiveUser(userId);
        if (user.isEmailVerified()) {
            throw new ApiException("ALREADY_VERIFIED", "Email is already verified.", HttpStatus.BAD_REQUEST);
        }
        emailTokenRepository.deleteUnusedByUserAndType(userId, EmailToken.Type.EMAIL_VERIFICATION);
        String rawToken = generateSecureToken();
        saveEmailToken(user, rawToken, EmailToken.Type.EMAIL_VERIFICATION, 24);
        emailService.sendVerificationEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException("INVALID_CREDENTIALS",
                        "Invalid email or password.", HttpStatus.UNAUTHORIZED));

        if (!user.isActive()) {
            throw new ApiException("ACCOUNT_SUSPENDED",
                    "Your account has been suspended.", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException("INVALID_CREDENTIALS",
                    "Invalid email or password.", HttpStatus.UNAUTHORIZED);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: id={}", user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new ApiException("INVALID_TOKEN", "Invalid or expired refresh token.", HttpStatus.UNAUTHORIZED);
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new ApiException("INVALID_TOKEN", "Token is not a refresh token.", HttpStatus.UNAUTHORIZED);
        }

        String jti = jwtService.extractJti(refreshToken);
        if (tokenBlacklistService.isBlacklisted(jti)) {
            throw new ApiException("TOKEN_REVOKED", "Refresh token has already been used.", HttpStatus.UNAUTHORIZED);
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));

        if (!user.isActive()) {
            throw new ApiException("ACCOUNT_SUSPENDED", "Your account has been suspended.", HttpStatus.FORBIDDEN);
        }

        // Rotate: blacklist the used refresh token
        tokenBlacklistService.blacklist(jti, jwtService.extractExpiration(refreshToken));

        return buildAuthResponse(user);
    }

    public void logout(String accessToken) {
        if (accessToken != null && jwtService.isTokenValid(accessToken)) {
            String jti = jwtService.extractJti(accessToken);
            tokenBlacklistService.blacklist(jti, jwtService.extractExpiration(accessToken));
            log.info("User logged out — token blacklisted jti={}", jti);
        }
    }

    public UserResponse getProfile(UUID userId) {
        User user = findActiveUser(userId);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, String companyName) {
        User user = findActiveUser(userId);
        if (companyName != null) {
            user.setCompanyName(companyName);
        }
        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    public User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        if (!user.isActive()) {
            throw new ApiException("ACCOUNT_SUSPENDED",
                    "Your account has been suspended.", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void saveEmailToken(User user, String rawToken, EmailToken.Type type, int expiryHours) {
        String hash = HashUtil.sha256(rawToken);
        EmailToken token = EmailToken.builder()
                .user(user)
                .tokenHash(hash)
                .type(type)
                .expiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS))
                .build();
        emailTokenRepository.save(token);
    }

    private EmailToken findValidToken(String rawToken, EmailToken.Type expectedType) {
        String hash = HashUtil.sha256(rawToken);
        EmailToken token = emailTokenRepository.findByTokenHashAndUsedAtIsNull(hash)
                .orElseThrow(() -> new ApiException("INVALID_TOKEN", "Token is invalid or has already been used.", HttpStatus.BAD_REQUEST));
        if (token.isExpired()) {
            throw new ApiException("TOKEN_EXPIRED", "Token has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }
        if (token.getType() != expectedType) {
            throw new ApiException("INVALID_TOKEN", "Invalid token type.", HttpStatus.BAD_REQUEST);
        }
        return token;
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .user(UserResponse.fromEntity(user))
                .build();
    }
}
