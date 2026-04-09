package com.synthdetect.detection.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetectionResponse {

    private UUID requestId;
    private String status;
    private String type;

    // Result fields
    private Boolean isSynthetic;
    private BigDecimal confidenceScore;
    private String verdict;          // "synthetic" | "authentic" | "inconclusive"
    private String modelVersion;
    private Integer processingMs;

    // Signals breakdown
    private List<SignalDto> signals;

    // Compliance
    private Boolean flaggedForReview;
    private String jurisdiction;

    private Instant createdAt;
    private Instant completedAt;

    @Data
    @Builder
    public static class SignalDto {
        private String name;
        private BigDecimal value;
        private BigDecimal weight;
        private String description;
    }
}
