package com.synthdetect.webhook.model;

import com.synthdetect.detection.model.DetectionRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private DetectionRequest detectionRequest;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(nullable = false)
    @Builder.Default
    private boolean success = false;

    @CreationTimestamp
    @Column(name = "delivered_at", updatable = false)
    private Instant deliveredAt;
}
