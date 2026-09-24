package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.AiChatMessage;
import com.utp.horario.domain.model.DailyQuotaStatus;
import com.utp.horario.domain.port.in.AiAssistantServicePort;
import com.utp.horario.domain.port.in.DailyQuotaServicePort;
import com.utp.horario.presentation.dto.AiChatRequest;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiAssistantServicePort aiAssistantServicePort;
    private final DailyQuotaServicePort dailyQuotaServicePort;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatMessage>> chat(@RequestBody AiChatRequest request) {
        AiChatMessage response = aiAssistantServicePort.processUserQuery(
                request.getUserId(),
                request.getMessage(),
                request.getSchedule(),
                request.getSyllabi()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<DailyQuotaStatus>> getQuota(
            @RequestParam(defaultValue = "anonymous_user") String userId) {
        DailyQuotaStatus status = dailyQuotaServicePort.checkQuota(userId);
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}
