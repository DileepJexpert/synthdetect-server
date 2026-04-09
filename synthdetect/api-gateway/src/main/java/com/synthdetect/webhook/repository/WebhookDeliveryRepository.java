package com.synthdetect.webhook.repository;

import com.synthdetect.webhook.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    Page<WebhookDelivery> findByWebhookIdOrderByDeliveredAtDesc(UUID webhookId, Pageable pageable);
    List<WebhookDelivery> findBySuccessFalseAndWebhookId(UUID webhookId);
}
