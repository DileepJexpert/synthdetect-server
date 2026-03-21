package com.synthdetect.auth.model;

import com.synthdetect.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 12)
    private String keyPrefix;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String name = "Default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    // Stored as comma-separated string for cross-DB compatibility
    // PostgreSQL migration uses text[] but JPA maps it as a string
    @Column(name = "scopes", length = 500)
    @Builder.Default
    private String scopes = "detect";

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "total_calls", nullable = false)
    @Builder.Default
    private long totalCalls = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isValid() {
        if (status != ApiKeyStatus.ACTIVE) return false;
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return false;
        return true;
    }

    public String[] getScopesArray() {
        if (scopes == null || scopes.isBlank()) return new String[0];
        return scopes.split(",");
    }

    public void setScopesArray(String[] scopesArr) {
        this.scopes = scopesArr != null ? String.join(",", scopesArr) : "detect";
    }

    public boolean hasScope(String scope) {
        if (scopes == null) return false;
        return Arrays.stream(getScopesArray())
                .anyMatch(s -> s.trim().equalsIgnoreCase(scope) || s.trim().equalsIgnoreCase("admin"));
    }
}
