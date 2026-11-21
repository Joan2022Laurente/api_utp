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
public class MaterialTextExtraction {
    private String materialId;
    private String title;
    private String url;
    private List<Integer> pagesExtracted;
    private Integer totalLength;
    private String contentMarkdown;
    private String quality; // HIGH, MEDIUM, LOW, NO_TEXT
}
