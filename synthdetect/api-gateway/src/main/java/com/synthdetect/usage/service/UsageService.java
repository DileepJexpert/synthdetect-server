package com.synthdetect.usage.service;

import com.synthdetect.common.exception.QuotaExceededException;
import com.synthdetect.usage.model.UsageStat;
import com.synthdetect.usage.repository.UsageStatRepository;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        // -1 = unlimited (ENTERPRISE)
        if (quota != -1 && stat.getTotalCalls() >= quota) {
            throw new QuotaExceededException("Monthly quota of " + quota + " calls exceeded");
        }

        stat.setTotalCalls(stat.getTotalCalls() + 1);
        if ("image".equalsIgnoreCase(type)) {
            stat.setImageCalls(stat.getImageCalls() + 1);
        } else if ("text".equalsIgnoreCase(type)) {
            stat.setTextCalls(stat.getTextCalls() + 1);
        } else {
            stat.setBatchCalls(stat.getBatchCalls() + 1);
        }

        usageStatRepository.save(stat);
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
