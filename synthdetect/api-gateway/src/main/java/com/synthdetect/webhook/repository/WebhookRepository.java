package com.synthdetect.webhook.repository;

import com.synthdetect.webhook.model.Webhook;
import com.synthdetect.webhook.model.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    List<Webhook> findByUserId(UUID userId);
    List<Webhook> findByUserIdAndStatus(UUID userId, WebhookStatus status);
    Optional<Webhook> findByIdAndUserId(UUID id, UUID userId);
}
