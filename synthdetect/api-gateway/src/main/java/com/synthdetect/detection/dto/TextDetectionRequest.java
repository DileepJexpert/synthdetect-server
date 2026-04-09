package com.synthdetect.detection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TextDetectionRequest {

    @NotBlank(message = "text is required")
    @Size(min = 50, max = 100000, message = "text must be between 50 and 100000 characters")
    private String text;

    private String language;
    private String webhookUrl;
    private String jurisdiction;
    private Boolean flagIfSynthetic;
}
