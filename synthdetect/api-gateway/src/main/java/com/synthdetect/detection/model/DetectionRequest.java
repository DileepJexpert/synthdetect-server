package com.synthdetect.detection.model;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "detection_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DetectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id")
    private ApiKey apiKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DetectionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DetectionStatus status = DetectionStatus.PENDING;

    @Column(name = "content_url")
    private String contentUrl;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "language")
    private String language;

    @Column(name = "is_synthetic")
    private Boolean isSynthetic;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "jurisdiction")
    @Builder.Default
    private String jurisdiction = "india_it_rules_2026";

    @Column(name = "flagged_for_review")
    @Builder.Default
    private Boolean flaggedForReview = false;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "webhook_delivered")
    @Builder.Default
    private Boolean webhookDelivered = false;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
