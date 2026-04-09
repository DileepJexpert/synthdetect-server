package com.synthdetect.usage.service;

import com.synthdetect.common.exception.QuotaExceededException;
import com.synthdetect.usage.model.UsageStat;
import com.synthdetect.usage.repository.UsageStatRepository;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import com.synthdetect.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageStatRepository usageStatRepository;
    private final UserService userService;
    @Lazy
    private final WebhookService webhookService;

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional
    public void checkAndIncrementQuota(UUID userId, String type) {
        User user = userService.findActiveUser(userId);
        String yearMonth = LocalDate.now().format(YEAR_MONTH_FMT);

        UsageStat stat = usageStatRepository.findByUserIdAndYearMonth(userId, yearMonth)
                .orElseGet(() -> UsageStat.builder()
                        .user(user)
                        .yearMonth(yearMonth)
                        .quotaLimit(user.getMonthlyQuota())
                        .build());

        int quota = stat.getQuotaLimit();
        int used = stat.getTotalCalls();

        // -1 = unlimited (ENTERPRISE)
        if (quota != -1 && used >= quota) {
            webhookService.deliverQuotaEvent(userId, "quota.exceeded",
                    Map.of("used", used, "limit", quota, "yearMonth", yearMonth));
            throw new QuotaExceededException("Monthly quota of " + quota + " calls exceeded");
        }

        stat.setTotalCalls(used + 1);
        if ("image".equalsIgnoreCase(type)) {
            stat.setImageCalls(stat.getImageCalls() + 1);
        } else if ("text".equalsIgnoreCase(type)) {
            stat.setTextCalls(stat.getTextCalls() + 1);
        } else {
            stat.setBatchCalls(stat.getBatchCalls() + 1);
        }

        usageStatRepository.save(stat);

        // Fire quota.warning at 80% usage
        if (quota != -1) {
            int newUsed = used + 1;
            double pct = newUsed * 100.0 / quota;
            if (pct >= 80 && (used * 100.0 / quota) < 80) {
                webhookService.deliverQuotaEvent(userId, "quota.warning",
                        Map.of("used", newUsed, "limit", quota, "percentUsed", Math.round(pct),
                                "yearMonth", yearMonth));
                log.info("Quota warning fired userId={} used={}/{}", userId, newUsed, quota);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentUsage(UUID userId) {
        User user = userService.findActiveUser(userId);
        String yearMonth = LocalDate.now().format(YEAR_MONTH_FMT);

        UsageStat stat = usageStatRepository.findByUserIdAndYearMonth(userId, yearMonth)
                .orElse(UsageStat.builder()
                        .user(user)
                        .yearMonth(yearMonth)
                        .quotaLimit(user.getMonthlyQuota())
                        .build());

        int quota = stat.getQuotaLimit();
        int used = stat.getTotalCalls();
        double percentUsed = quota == -1 ? 0 : (used * 100.0 / quota);

        return Map.of(
                "yearMonth", yearMonth,
                "totalCalls", used,
                "imageCalls", stat.getImageCalls(),
                "textCalls", stat.getTextCalls(),
                "batchCalls", stat.getBatchCalls(),
                "quotaLimit", quota == -1 ? "unlimited" : quota,
                "percentUsed", Math.round(percentUsed * 10.0) / 10.0,
                "remaining", quota == -1 ? "unlimited" : Math.max(0, quota - used)
        );
    }

    @Transactional(readOnly = true)
    public List<UsageStat> getUsageHistory(UUID userId) {
        return usageStatRepository.findByUserIdOrderByYearMonthDesc(userId);
    }
}
