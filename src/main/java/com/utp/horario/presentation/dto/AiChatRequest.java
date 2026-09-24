package com.utp.horario.presentation.dto;

import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String message;
    private String userId;
    private ScheduleInterval schedule;
    private Map<String, Syllabus> syllabi;
}
