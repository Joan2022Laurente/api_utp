package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusWeeklySession {
    private Integer week;
    private Integer session;
    private String unit;
    private String topic;
    private String activities;
    private String evaluation;
}
