package com.synthdetect.scheduler;

import com.synthdetect.user.repository.EmailTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cleans up expired email tokens (verification + reset) from the DB.
 * Runs daily at 03:00.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredTokenCleanupJob {

    private final EmailTokenRepository emailTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")  // 03:00 every day
    @Transactional
    public void cleanupExpiredTokens() {
        List<com.synthdetect.user.model.EmailToken> expired = emailTokenRepository.findAll()
                .stream()
                .filter(t -> t.isExpired() && t.getUsedAt() == null)
                .toList();

        if (!expired.isEmpty()) {
            emailTokenRepository.deleteAll(expired);
            log.info("ExpiredTokenCleanup: deleted {} expired email tokens", expired.size());
        }
    }
}
