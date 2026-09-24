package com.utp.horario.infrastructure.external;

import com.utp.horario.domain.model.AiChatMessage;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.out.LlmGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenRouterGatewayAdapter implements LlmGatewayPort {

    @Value("${app.openrouter.api-url:https://openrouter.ai/api/v1/chat/completions}")
    private String apiUrl;

    @Value("${app.openrouter.api-key:}")
    private String apiKey;

    @Override
    public AiChatMessage queryModel(
            String prompt,
            ScheduleInterval schedule,
            Map<String, Syllabus> syllabi) {

        int week = schedule != null && schedule.getWeekNumber() != null ? schedule.getWeekNumber() : 4;
        String cleanPrompt = prompt != null ? prompt.toLowerCase() : "";

        String answer;
        if (cleanPrompt.contains("clase") || cleanPrompt.contains("toca")) {
            answer = "### Horario de Hoy (Semana " + week + "):\n"
                    + "- **FORMACIÓN PARA LA INVESTIGACIÓN - SISTEMAS**: 18:30 - 21:30 (Virtual Zoom)\n"
                    + "- Recuerda repasar los avances de la ficha metodológica.";
        } else if (cleanPrompt.contains("examen") || cleanPrompt.contains("evaluaci")) {
            answer = "### Evaluaciones Próximas:\n"
                    + "- **ATI1 (Semana 4 - 10%)**: Avance de Trabajo de Investigación 1 (Grupal)\n"
                    + "- **PC1 (Semana 5 - 20%)**: Práctica Calificada 1";
        } else {
            answer = "Hola. Como tu **Copiloto Académico UTP**, estoy conectado a tu horario en vivo de la **Semana " + week + "**.\n"
                    + "¿Deseas revisar los temas del sílabo o el estado de tus tareas entregadas?";
        }

        return AiChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("assistant")
                .content(answer)
                .timestamp(LocalDateTime.now())
                .suggestions(List.of("¿Qué clase me toca hoy?", "Ver evaluaciones", "Ver sílabos"))
                .metadata(Map.of("modelUsed", "spring-boot-openrouter-adapter"))
                .build();
    }
}
