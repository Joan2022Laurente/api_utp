package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionMaterial {
    private String id;
    private String title;
    private String type; // DOCUMENT, LINK, URL, etc.
    private String url;  // Enlace directo en S3 o enlace externo
    private Boolean viewed;
}
