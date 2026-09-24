package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.DailyQuotaStatus;

public interface DailyQuotaServicePort {
    DailyQuotaStatus checkQuota(String userIdentifier);
    DailyQuotaStatus consumeQuota(String userIdentifier);
    void resetQuota(String userIdentifier);
}
