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
public class SectionUnit {
    private Integer unitNumber;
    private String title;
    private List<SectionWeek> weeks;

    @JsonProperty("themes")
    public List<SectionWeek> getThemes() {
        return weeks;
    }
}
