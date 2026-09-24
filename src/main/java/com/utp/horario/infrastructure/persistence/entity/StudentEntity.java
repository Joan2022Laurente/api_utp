package com.utp.horario.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "students")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentEntity {
    @Id
    private String id;
    private String studentCode;
    private String fullName;
    private String email;
    private String career;
    private String campus;
    private Integer currentCycle;
}
