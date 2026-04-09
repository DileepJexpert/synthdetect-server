package com.synthdetect.detection.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "detection_signals")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DetectionSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private DetectionRequest request;

    @Column(name = "signal_name", nullable = false)
    private String signalName;

    @Column(name = "signal_value", precision = 5, scale = 4)
    private BigDecimal signalValue;

    @Column(name = "signal_weight", precision = 5, scale = 4)
    private BigDecimal signalWeight;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
