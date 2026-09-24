package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.AiChatMessage;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;

import java.util.Map;

public interface LlmGatewayPort {
    AiChatMessage queryModel(
        String prompt,
        ScheduleInterval schedule,
        Map<String, Syllabus> syllabi
    );
}
