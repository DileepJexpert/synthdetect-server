package com.synthdetect.detection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImageDetectionRequest {

    @NotBlank(message = "image_url is required")
    private String imageUrl;

    private String webhookUrl;
    private String jurisdiction;
    private Boolean flagIfSynthetic;
}
