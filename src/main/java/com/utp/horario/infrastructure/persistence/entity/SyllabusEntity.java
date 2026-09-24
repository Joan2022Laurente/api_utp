package com.utp.horario.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "syllabi")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusEntity {
    @Id
    private String id;
    private String courseCode;
    private String courseName;
    private String semester;
    private Integer credits;
    private String modality;
    private String formula;

    @Lob
    @Column(length = 65536)
    private String rawJsonData;
}
