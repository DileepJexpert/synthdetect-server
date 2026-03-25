package com.synthdetect.detection.repository;

import com.synthdetect.detection.model.DetectionSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DetectionSignalRepository extends JpaRepository<DetectionSignal, UUID> {
    List<DetectionSignal> findByRequestId(UUID requestId);
}
