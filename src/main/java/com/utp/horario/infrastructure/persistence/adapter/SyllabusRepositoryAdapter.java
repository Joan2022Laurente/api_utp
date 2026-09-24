package com.utp.horario.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.infrastructure.persistence.entity.SyllabusEntity;
import com.utp.horario.infrastructure.persistence.repository.SpringDataSyllabusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SyllabusRepositoryAdapter implements SyllabusRepositoryPort {

    private final SpringDataSyllabusRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public Syllabus save(Syllabus syllabus) {
        try {
            String json = objectMapper.writeValueAsString(syllabus);
            SyllabusEntity entity = SyllabusEntity.builder()
                    .id(syllabus.getId())
                    .courseCode(syllabus.getCourseCode())
                    .courseName(syllabus.getCourseName())
                    .semester(syllabus.getSemester())
                    .credits(syllabus.getCredits())
                    .modality(syllabus.getModality())
                    .formula(syllabus.getFormula())
                    .rawJsonData(json)
                    .build();

            repository.save(entity);
            return syllabus;
        } catch (Exception e) {
            throw new RuntimeException("Error persistiendo sílabo: " + syllabus.getCourseCode(), e);
        }
    }

    @Override
    public Optional<Syllabus> findByCourseCode(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) return Optional.empty();
        String clean = courseCode.trim();
        List<SyllabusEntity> matches = repository.searchSyllabus(clean);
        if (!matches.isEmpty()) {
            return Optional.of(toDomain(matches.get(0)));
        }
        return repository.findById(clean).map(this::toDomain);
    }

    @Override
    public List<Syllabus> findAllByCourseCodes(List<String> courseCodes) {
        List<Syllabus> list = new ArrayList<>();
        for (String code : courseCodes) {
            findByCourseCode(code).ifPresent(list::add);
        }
        return list;
    }

    @Override
    public List<Syllabus> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private Syllabus toDomain(SyllabusEntity entity) {
        try {
            return objectMapper.readValue(entity.getRawJsonData(), Syllabus.class);
        } catch (Exception e) {
            return Syllabus.builder()
                    .id(entity.getId())
                    .courseCode(entity.getCourseCode())
                    .courseName(entity.getCourseName())
                    .semester(entity.getSemester())
                    .credits(entity.getCredits())
                    .modality(entity.getModality())
                    .formula(entity.getFormula())
                    .build();
        }
    }
}
