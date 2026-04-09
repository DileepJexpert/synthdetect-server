package com.synthdetect.detection.service;

import com.synthdetect.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Handles multipart image uploads, validates format/size, stores temporarily,
 * and returns a local URL that the ML engine can fetch.
 */
@Slf4j
@Service
public class ImageUploadService {

    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif", "image/bmp"
    );
    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20 MB

    @Value("${app.upload.dir:/tmp/synthdetect-uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String storeAndGetUrl(MultipartFile file) {
        validateFile(file);

        String filename = UUID.randomUUID() + getExtension(file.getOriginalFilename());
        Path uploadPath = Paths.get(uploadDir);

        try {
            Files.createDirectories(uploadPath);
            Path dest = uploadPath.resolve(filename);
            file.transferTo(dest);
            log.debug("Image upload stored: {}", dest);
            return baseUrl + "/internal/uploads/" + filename;
        } catch (IOException e) {
            log.error("Failed to store upload: {}", e.getMessage());
            throw new ApiException("UPLOAD_FAILED", "Failed to store uploaded file.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("EMPTY_FILE", "Uploaded file is empty.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException("FILE_TOO_LARGE",
                    "File size exceeds 20 MB limit. Actual: " + (file.getSize() / 1024 / 1024) + " MB",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException("UNSUPPORTED_FORMAT",
                    "Unsupported image format. Allowed: jpeg, png, webp, gif, bmp",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
