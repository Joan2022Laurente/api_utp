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
public class SectionWeek {
    private Integer weekNumber;
    private String name;
    private List<SectionTopic> topics;

    @JsonProperty("contents")
    public List<SectionTopic> getContents() {
        return topics;
    }
}
