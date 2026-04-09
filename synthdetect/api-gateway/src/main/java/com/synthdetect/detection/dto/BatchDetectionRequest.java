package com.synthdetect.detection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchDetectionRequest {

    @NotEmpty(message = "items cannot be empty")
    @Size(max = 50, message = "batch size cannot exceed 50 items")
    @Valid
    private List<BatchItem> items;

    private String webhookUrl;
    private String jurisdiction;

    @Data
    public static class BatchItem {
        private String type;    // "image" or "text"
        private String imageUrl;
        private String text;
        private String language;
        private String externalId;
    }
}
