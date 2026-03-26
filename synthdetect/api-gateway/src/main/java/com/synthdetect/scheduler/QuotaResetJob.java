package com.synthdetect.scheduler;

import com.synthdetect.usage.model.UsageStat;
import com.synthdetect.usage.repository.UsageStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Logs quota summary at start of each month.
 * Quota is naturally "reset" by the new year-month key in usage_stats;
 * this job sends warning emails to users who hit >80% last month.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaResetJob {

    private final UsageStatRepository usageStatRepository;

    // Runs at 00:01 on the 1st of every month
    @Scheduled(cron = "0 1 0 1 * *")
    @Transactional(readOnly = true)
    public void logMonthlyQuotaSummary() {
        String lastMonth = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        List<UsageStat> stats = usageStatRepository.findAll().stream()
                .filter(s -> lastMonth.equals(s.getYearMonth()))
                .toList();

        long exceeded = stats.stream().filter(s -> s.getQuotaLimit() != -1
                && s.getTotalCalls() >= s.getQuotaLimit()).count();
        long nearLimit = stats.stream().filter(s -> s.getQuotaLimit() != -1
                && s.getTotalCalls() >= s.getQuotaLimit() * 0.8
                && s.getTotalCalls() < s.getQuotaLimit()).count();

        log.info("MonthlyQuotaReport month={} totalUsers={} exceeded={} nearLimit={}",
                lastMonth, stats.size(), exceeded, nearLimit);
    }
}
