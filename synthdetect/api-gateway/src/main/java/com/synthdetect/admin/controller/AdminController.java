package com.synthdetect.admin.controller;

import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.detection.model.DetectionRequest;
import com.synthdetect.detection.repository.DetectionRequestRepository;
import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserPlan;
import com.synthdetect.user.model.UserRole;
import com.synthdetect.user.model.UserStatus;
import com.synthdetect.user.repository.UserRepository;
import com.synthdetect.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only management endpoints")
public class AdminController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final DetectionRequestRepository detectionRequestRepository;

    @GetMapping("/stats")
    @Operation(summary = "Platform-wide statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long totalDetections = detectionRequestRepository.count();
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);

        Map<String, Object> stats = Map.of(
                "totalUsers", totalUsers,
                "activeUsers", activeUsers,
                "totalDetections", totalDetections,
                "detectionsSince24h", detectionRequestRepository.findByStatus(
                        com.synthdetect.detection.model.DetectionStatus.COMPLETED).size()
        );
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public ResponseEntity<ApiResponse<Page<User>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PatchMapping("/users/{id}/plan")
    @Operation(summary = "Change a user's plan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changePlan(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.synthdetect.common.exception.ApiException(
                        "User not found", org.springframework.http.HttpStatus.NOT_FOUND));
        UserPlan plan = UserPlan.valueOf(body.get("plan").toUpperCase());
        user.setPlan(plan);

        // update quota and rate limit based on plan
        switch (plan) {
            case FREE -> { user.setMonthlyQuota(500); user.setRateLimitRpm(30); }
            case STARTER -> { user.setMonthlyQuota(10000); user.setRateLimitRpm(120); }
            case BUSINESS -> { user.setMonthlyQuota(100000); user.setRateLimitRpm(600); }
            case ENTERPRISE -> { user.setMonthlyQuota(-1); user.setRateLimitRpm(3000); }
        }
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("userId", id, "newPlan", plan)));
    }

    @PatchMapping("/users/{id}/suspend")
    @Operation(summary = "Suspend a user account")
    public ResponseEntity<ApiResponse<Map<String, String>>> suspendUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.synthdetect.common.exception.ApiException(
                        "User not found", org.springframework.http.HttpStatus.NOT_FOUND));
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "suspended")));
    }

    @PatchMapping("/users/{id}/activate")
    @Operation(summary = "Reactivate a suspended user account")
    public ResponseEntity<ApiResponse<Map<String, String>>> activateUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.synthdetect.common.exception.ApiException(
                        "User not found", org.springframework.http.HttpStatus.NOT_FOUND));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "active")));
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Assign ADMIN or USER role to a user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignRole(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.synthdetect.common.exception.ApiException(
                        "User not found", org.springframework.http.HttpStatus.NOT_FOUND));
        UserRole role = UserRole.valueOf(body.get("role").toUpperCase());
        user.setRole(role);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of("userId", id, "role", role)));
    }

    @GetMapping("/detections/flagged")
    @Operation(summary = "List all flagged detection requests")
    public ResponseEntity<ApiResponse<Page<DetectionRequest>>> getFlaggedDetections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DetectionRequest> flagged = detectionRequestRepository.findFlagged(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(flagged));
    }
}
