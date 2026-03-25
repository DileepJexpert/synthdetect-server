package com.synthdetect.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class TakedownRequest {

    @NotBlank
    private String requestId;

    private String reporterName;
    private String reporterEmail;
    private String reporterOrg;

    @NotBlank(message = "description is required")
    private String description;

    private String jurisdiction;
}
