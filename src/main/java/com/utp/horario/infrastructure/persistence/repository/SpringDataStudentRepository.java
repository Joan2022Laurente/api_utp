package com.utp.horario.infrastructure.persistence.repository;

import com.utp.horario.infrastructure.persistence.entity.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataStudentRepository extends JpaRepository<StudentEntity, String> {
    Optional<StudentEntity> findByStudentCode(String studentCode);
    Optional<StudentEntity> findByEmail(String email);
}
