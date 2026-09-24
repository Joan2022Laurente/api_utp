package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.AiChatMessage;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;

import java.util.List;
import java.util.Map;

public interface AiAssistantServicePort {
    AiChatMessage processUserQuery(
        String userIdentifier,
        String message,
        ScheduleInterval schedule,
        Map<String, Syllabus> syllabi
    );
}
