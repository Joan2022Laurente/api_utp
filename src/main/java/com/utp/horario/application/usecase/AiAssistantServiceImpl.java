package com.utp.horario.application.usecase;

import com.utp.horario.domain.model.AiChatMessage;
import com.utp.horario.domain.model.DailyQuotaStatus;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.AiAssistantServicePort;
import com.utp.horario.domain.port.in.DailyQuotaServicePort;
import com.utp.horario.domain.port.out.LlmGatewayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantServicePort {

    private final LlmGatewayPort llmGatewayPort;
    private final DailyQuotaServicePort dailyQuotaServicePort;

    @Override
    public AiChatMessage processUserQuery(
            String userIdentifier,
            String message,
            ScheduleInterval schedule,
            Map<String, Syllabus> syllabi) {

        // Validar y descontar cuota diaria
        DailyQuotaStatus quota = dailyQuotaServicePort.consumeQuota(userIdentifier);
        if (!quota.getAllowed()) {
            return AiChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("assistant")
                    .content("Has alcanzado el límite de " + quota.getMax() + " consultas diarias. Tu cuota se reiniciará a las 00:00.")
                    .timestamp(LocalDateTime.now())
                    .suggestions(List.of("Ver horario de clases", "Consultar sílabos locales"))
                    .metadata(Map.of("rateLimitReached", true, "quota", quota))
                    .build();
        }

        try {
            return llmGatewayPort.queryModel(message, schedule, syllabi);
        } catch (Exception e) {
            // Fallback de inferencia local si el gateway externo falla
            return generateLocalFallback(message, schedule, syllabi);
        }
    }

    private AiChatMessage generateLocalFallback(String message, ScheduleInterval schedule, Map<String, Syllabus> syllabi) {
        int week = schedule != null && schedule.getWeekNumber() != null ? schedule.getWeekNumber() : 1;
        int courseCount = schedule != null && schedule.getCourses() != null ? schedule.getCourses().size() : 0;

        String content = "### Copiloto Académico UTP (Modo Resiliente Local)\n\n"
                + "Actualmente te encuentras en la **Semana " + week + "** con **" + courseCount + " asignaturas** registradas.\n\n"
                + "Para esta semana, consulta tus avances en el sílabo y asegúrate de verificar las rúbricas de evaluación en la sección de tareas.";

        return AiChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .timestamp(LocalDateTime.now())
                .suggestions(List.of("¿Qué clase me toca hoy?", "¿Cuándo es mi próximo examen?", "Ver sílabos"))
                .metadata(Map.of("modelUsed", "spring-boot-local-engine"))
                .build();
    }
}
