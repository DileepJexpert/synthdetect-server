package com.synthdetect.user.controller;

import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.user.dto.*;
import com.synthdetect.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh_token");
        AuthResponse response = userService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/v1/user/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal UUID userId) {
        UserResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/v1/user/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody Map<String, String> body) {
        UserResponse response = userService.updateProfile(userId, body.get("company_name"));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Logged out successfully.")));
    }

    @GetMapping("/auth/verify-email")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Email verified successfully.")));
    }

    @PostMapping("/auth/resend-verification")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendVerification(
            @AuthenticationPrincipal UUID userId) {
        userService.resendVerification(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Verification email sent.")));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @RequestBody Map<String, String> body) {
        userService.forgotPassword(body.get("email"));
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "If that email exists, a reset link has been sent.")));
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @RequestBody Map<String, String> body) {
        userService.resetPassword(body.get("token"), body.get("password"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Password reset successfully.")));
    }
}
