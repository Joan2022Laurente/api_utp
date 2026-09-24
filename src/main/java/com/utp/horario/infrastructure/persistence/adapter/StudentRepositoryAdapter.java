package com.utp.horario.infrastructure.persistence.adapter;

import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.out.StudentRepositoryPort;
import com.utp.horario.infrastructure.persistence.entity.StudentEntity;
import com.utp.horario.infrastructure.persistence.repository.SpringDataStudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StudentRepositoryAdapter implements StudentRepositoryPort {

    private final SpringDataStudentRepository repository;

    @Override
    public StudentProfile save(StudentProfile student) {
        StudentEntity entity = StudentEntity.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .email(student.getEmail())
                .career(student.getCareer())
                .campus(student.getCampus())
                .currentCycle(student.getCurrentCycle())
                .build();

        StudentEntity saved = repository.save(entity);
        return toDomain(saved, student.getToken(), student.getEnrolledCourseCodes());
    }

    @Override
    public Optional<StudentProfile> findById(String id) {
        return repository.findById(id).map(e -> toDomain(e, null, List.of()));
    }

    @Override
    public Optional<StudentProfile> findByStudentCode(String studentCode) {
        return repository.findByStudentCode(studentCode).map(e -> toDomain(e, null, List.of()));
    }

    @Override
    public Optional<StudentProfile> findByEmail(String email) {
        return repository.findByEmail(email).map(e -> toDomain(e, null, List.of()));
    }

    private StudentProfile toDomain(StudentEntity entity, String token, List<String> courses) {
        return StudentProfile.builder()
                .id(entity.getId())
                .studentCode(entity.getStudentCode())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .career(entity.getCareer())
                .campus(entity.getCampus())
                .currentCycle(entity.getCurrentCycle())
                .token(token)
                .enrolledCourseCodes(courses)
                .build();
    }
}
