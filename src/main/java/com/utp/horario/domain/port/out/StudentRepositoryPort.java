package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.StudentProfile;

import java.util.Optional;

public interface StudentRepositoryPort {
    StudentProfile save(StudentProfile studentProfile);
    Optional<StudentProfile> findById(String id);
    Optional<StudentProfile> findByStudentCode(String studentCode);
    Optional<StudentProfile> findByEmail(String email);
}
