package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletenessStatus {
    private String status; // "COMPLETE", "PARTIAL"
    private List<GapInfo> gaps;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GapInfo {
        private String source;
        private String ref;
        private String reason;
    }
}
