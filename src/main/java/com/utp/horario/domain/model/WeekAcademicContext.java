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
public class WeekAcademicContext {
    private Integer weekNumber;
    private String sectionId;
    private String courseCode;
    private WeekSyllabusContext syllabus;
    private List<SectionTopic> classroomTopics;
    private List<TaskSpecification> tasks;
    private Boolean rubricAvailable;
    private CompletenessStatus completeness;
    private ContextProvenance provenance;
}
