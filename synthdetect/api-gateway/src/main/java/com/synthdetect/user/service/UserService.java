package com.synthdetect.user.service;

import com.synthdetect.common.exception.ApiException;
import com.synthdetect.config.JwtService;
import com.synthdetect.user.dto.*;
import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserPlan;
import com.synthdetect.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

        return buildAuthResponse(user);
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

        UUID userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));

        if (!user.isActive()) {
            throw new ApiException("ACCOUNT_SUSPENDED",
                    "Your account has been suspended.", HttpStatus.FORBIDDEN);
        }

        return buildAuthResponse(user);
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
