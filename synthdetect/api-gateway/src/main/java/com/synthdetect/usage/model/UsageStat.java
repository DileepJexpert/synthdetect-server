package com.synthdetect.usage.model;

import com.synthdetect.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_stats")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UsageStat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "image_calls")
    @Builder.Default
    private Integer imageCalls = 0;

    @Column(name = "text_calls")
    @Builder.Default
    private Integer textCalls = 0;

    @Column(name = "batch_calls")
    @Builder.Default
    private Integer batchCalls = 0;

    @Column(name = "total_calls")
    @Builder.Default
    private Integer totalCalls = 0;

    @Column(name = "quota_limit")
    @Builder.Default
    private Integer quotaLimit = 500;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
