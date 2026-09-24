package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyQuotaStatus {
    private String userIdentifier;
    private LocalDate date;
    private Integer used;
    private Integer max;
    private Integer remaining;
    private Boolean isUnlimited;
    private Boolean allowed;
}
