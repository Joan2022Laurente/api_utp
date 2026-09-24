package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {
    private String id;
    private String role; // user, assistant, system
    private String content;
    private LocalDateTime timestamp;
    private List<String> suggestions;
    private Map<String, Object> metadata;
}
