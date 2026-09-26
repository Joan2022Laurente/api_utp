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
public class RubricCriterion {
    private String id;
    private String name;
    private Double score;
    private String description;
    private Integer order;
    private List<RubricLevel> performanceRatings;
}
