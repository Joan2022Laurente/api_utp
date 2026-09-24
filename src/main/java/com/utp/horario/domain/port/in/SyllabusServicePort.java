package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.Syllabus;

import java.util.List;

public interface SyllabusServicePort {
    Syllabus getSyllabusByCourseCode(String courseCode);
    Syllabus getSyllabus(String courseCode, String sectionId, String pdfUrl, String token);
    List<Syllabus> getAllSyllabiForStudent(String studentId);
    Syllabus parseAndSaveSyllabusText(String courseCode, String syllabusText);
    Syllabus saveSyllabus(Syllabus syllabus);
    String fetchRawSyllabusText(String courseCode, String sectionId, String pdfUrl, String token);
}
