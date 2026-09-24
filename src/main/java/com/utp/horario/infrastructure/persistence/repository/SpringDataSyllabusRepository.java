package com.utp.horario.infrastructure.persistence.repository;

import com.utp.horario.infrastructure.persistence.entity.SyllabusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataSyllabusRepository extends JpaRepository<SyllabusEntity, String> {
    Optional<SyllabusEntity> findByCourseCodeIgnoreCase(String courseCode);
    Optional<SyllabusEntity> findByCourseNameIgnoreCase(String courseName);

    @Query("SELECT s FROM SyllabusEntity s WHERE UPPER(s.courseCode) = UPPER(:query) OR UPPER(s.courseName) = UPPER(:query) OR UPPER(s.courseName) LIKE UPPER(CONCAT('%', :query, '%'))")
    List<SyllabusEntity> searchSyllabus(@Param("query") String query);
}
