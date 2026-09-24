package com.utp.horario.application.usecase;

import com.utp.horario.domain.model.DailyQuotaStatus;
import com.utp.horario.domain.port.in.DailyQuotaServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DailyQuotaServiceImpl implements DailyQuotaServicePort {

    private final int dailyLimit;
    private final List<String> unlimitedEmails;
    private final ConcurrentHashMap<String, UsageRecord> usageMap = new ConcurrentHashMap<>();

    public DailyQuotaServiceImpl(
            @Value("${app.rate-limit.daily-quota:6}") int dailyLimit,
            @Value("${app.rate-limit.unlimited-emails:joan.laurente@utp.edu.pe}") List<String> unlimitedEmails) {
        this.dailyLimit = dailyLimit;
        this.unlimitedEmails = unlimitedEmails;
    }

    private static class UsageRecord {
        LocalDate date;
        int count;

        UsageRecord(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }
    }

    @Override
    public DailyQuotaStatus checkQuota(String userIdentifier) {
        String identifier = userIdentifier != null ? userIdentifier.toLowerCase().trim() : "anonymous";
        boolean isUnlimited = unlimitedEmails.stream().anyMatch(email -> identifier.contains(email.toLowerCase()));

        LocalDate today = LocalDate.now();
        UsageRecord record = usageMap.compute(identifier, (k, v) -> {
            if (v == null || !v.date.equals(today)) {
                return new UsageRecord(today, 0);
            }
            return v;
        });

        int used = record.count;
        int remaining = isUnlimited ? 9999 : Math.max(0, dailyLimit - used);
        boolean allowed = isUnlimited || used < dailyLimit;

        return DailyQuotaStatus.builder()
                .userIdentifier(identifier)
                .date(today)
                .used(used)
                .max(isUnlimited ? 9999 : dailyLimit)
                .remaining(remaining)
                .isUnlimited(isUnlimited)
                .allowed(allowed)
                .build();
    }

    @Override
    public DailyQuotaStatus consumeQuota(String userIdentifier) {
        DailyQuotaStatus current = checkQuota(userIdentifier);
        if (!current.getAllowed()) {
            return current;
        }

        String identifier = userIdentifier != null ? userIdentifier.toLowerCase().trim() : "anonymous";
        usageMap.compute(identifier, (k, v) -> {
            if (v != null) {
                v.count += 1;
            }
            return v;
        });

        return checkQuota(userIdentifier);
    }

    @Override
    public void resetQuota(String userIdentifier) {
        if (userIdentifier != null) {
            usageMap.remove(userIdentifier.toLowerCase().trim());
        }
    }
}
