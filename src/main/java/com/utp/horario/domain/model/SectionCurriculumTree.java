package com.utp.horario.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionCurriculumTree {
    private String sectionId;
    private String courseId;
    private String courseCode;
    private Integer numWeeks;
    private Integer currentWeek;
    private List<SectionUnit> units;

    @JsonProperty("unities")
    public List<SectionUnit> getUnities() {
        return units;
    }
}
