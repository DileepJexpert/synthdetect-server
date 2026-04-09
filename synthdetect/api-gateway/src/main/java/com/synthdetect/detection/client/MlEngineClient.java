package com.synthdetect.detection.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MlEngineClient {

    private final WebClient webClient;
    private final int timeoutMs;
    private final int retryAttempts;

    public MlEngineClient(
            WebClient.Builder builder,
            @Value("${ml-engine.base-url}") String baseUrl,
            @Value("${ml-engine.timeout-ms:30000}") int timeoutMs,
            @Value("${ml-engine.retry-max-attempts:2}") int retryAttempts) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.timeoutMs = timeoutMs;
        this.retryAttempts = retryAttempts;
    }

    public MlDetectionResult detectImage(String imageUrl) {
        log.debug("ML Engine: detecting image {}", imageUrl);
        try {
            return webClient.post()
                    .uri("/v1/detect/image")
                    .bodyValue(Map.of("image_url", imageUrl))
                    .retrieve()
                    .bodyToMono(MlDetectionResult.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .retry(retryAttempts)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ML Engine image detection failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new MlEngineException("ML engine error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("ML Engine image detection error", e);
            throw new MlEngineException("ML engine unavailable: " + e.getMessage(), e);
        }
    }

    public MlDetectionResult detectText(String text, String language) {
        log.debug("ML Engine: detecting text (len={})", text.length());
        try {
            return webClient.post()
                    .uri("/v1/detect/text")
                    .bodyValue(Map.of("text", text, "language", language != null ? language : "en"))
                    .retrieve()
                    .bodyToMono(MlDetectionResult.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .retry(retryAttempts)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ML Engine text detection failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new MlEngineException("ML engine error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("ML Engine text detection error", e);
            throw new MlEngineException("ML engine unavailable: " + e.getMessage(), e);
        }
    }

    // Response structure from Python ML service
    public record MlDetectionResult(
            boolean isSynthetic,
            double confidenceScore,
            String modelVersion,
            int processingMs,
            List<MlSignal> signals
    ) {}

    public record MlSignal(
            String name,
            double value,
            double weight,
            String description
    ) {}

    public static class MlEngineException extends RuntimeException {
        public MlEngineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
