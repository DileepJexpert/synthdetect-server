package com.synthdetect.user.dto;

import com.synthdetect.user.model.User;
import com.synthdetect.user.model.UserPlan;
import com.synthdetect.user.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String companyName;
    private UserPlan plan;
    private UserStatus status;
    private int monthlyQuota;
    private int rateLimitRpm;
    private boolean emailVerified;
    private Instant createdAt;
    private Instant lastLoginAt;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .companyName(user.getCompanyName())
                .plan(user.getPlan())
                .status(user.getStatus())
                .monthlyQuota(user.getMonthlyQuota())
                .rateLimitRpm(user.getRateLimitRpm())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
