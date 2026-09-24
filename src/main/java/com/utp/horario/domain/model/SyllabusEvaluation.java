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
public class SyllabusEvaluation {
    private String id;
    private String type; // PC1, PC2, ATI1, PROY, PA, EF
    private String description;
    private Integer week;
    private Integer weightPercent;
    private String modality; // Individual, Grupal
    private String observation;
    private List<String> rules;
}
