package com.synthdetect.detection.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BatchDetectionResponse {
    private UUID batchId;
    private String status;
    private int totalItems;
    private int processedItems;
    private List<DetectionResponse> results;
    private Instant createdAt;
}
