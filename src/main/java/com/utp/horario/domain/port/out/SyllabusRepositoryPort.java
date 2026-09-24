package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.Syllabus;

import java.util.List;
import java.util.Optional;

public interface SyllabusRepositoryPort {
    Syllabus save(Syllabus syllabus);
    Optional<Syllabus> findByCourseCode(String courseCode);
    List<Syllabus> findAllByCourseCodes(List<String> courseCodes);
    List<Syllabus> findAll();
}
