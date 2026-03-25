package com.synthdetect.compliance.model;

import com.synthdetect.detection.model.DetectionRequest;
import com.synthdetect.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_reports")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ComplianceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private DetectionRequest detectionRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private String jurisdiction = "india_it_rules_2026";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ComplianceAction action = ComplianceAction.FLAGGED;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "reporter_email")
    private String reporterEmail;

    @Column(name = "reporter_org")
    private String reporterOrg;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reported_at")
    @Builder.Default
    private Instant reportedAt = Instant.now();

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
