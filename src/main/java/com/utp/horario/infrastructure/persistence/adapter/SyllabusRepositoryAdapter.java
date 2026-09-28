package com.utp.horario.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.infrastructure.persistence.entity.SyllabusEntity;
import com.utp.horario.infrastructure.persistence.repository.SpringDataSyllabusRepository;
import com.utp.horario.infrastructure.persistence.supabase.SupabaseSyllabusClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyllabusRepositoryAdapter implements SyllabusRepositoryPort {

    private final SpringDataSyllabusRepository localRepository;
    private final SupabaseSyllabusClient supabaseClient;
    private final ObjectMapper objectMapper;

    @Override
    public Syllabus save(Syllabus syllabus) {
        if (syllabus == null) return null;

        // 1. Persistir localmente en H2 (L1 cache)
        try {
            String json = objectMapper.writeValueAsString(syllabus);
            SyllabusEntity entity = SyllabusEntity.builder()
                    .id(syllabus.getId() != null ? syllabus.getId() : syllabus.getCourseCode())
                    .courseCode(syllabus.getCourseCode())
                    .courseName(syllabus.getCourseName())
                    .semester(syllabus.getSemester())
                    .credits(syllabus.getCredits())
                    .modality(syllabus.getModality())
                    .formula(syllabus.getFormula())
                    .rawJsonData(json)
                    .build();

            localRepository.save(entity);
        } catch (Exception e) {
            log.warn("[SyllabusRepo] Error guardando copia en caché local H2: {}", e.getMessage());
        }

        // 2. Persistir en Supabase PostgreSQL (Cloud Database)
        try {
            supabaseClient.upsert(syllabus);
        } catch (Exception e) {
            log.error("[SyllabusRepo] Error guardando sílabo en Supabase: {}", e.getMessage());
        }

        return syllabus;
    }

    @Override
    public Optional<Syllabus> findByCourseCode(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) return Optional.empty();
        String clean = courseCode.trim();

        // 1. Consultar primero en Supabase Cloud DB
        try {
            Optional<Syllabus> cloudResult = supabaseClient.findByCourseCode(clean);
            if (cloudResult.isPresent() && isCompleteSyllabus(cloudResult.get())) {
                // Guardar en H2 para acelerar lecturas posteriores
                saveLocalSilently(cloudResult.get());
                return cloudResult;
            }
        } catch (Exception e) {
            log.warn("[SyllabusRepo] Error consultando Supabase para {}: {}", clean, e.getMessage());
        }

        // 2. Fallback a caché local H2
        List<SyllabusEntity> matches = localRepository.searchSyllabus(clean);
        if (!matches.isEmpty()) {
            return Optional.of(toDomain(matches.get(0)));
        }
        return localRepository.findById(clean).map(this::toDomain);
    }

    @Override
    public List<Syllabus> findAllByCourseCodes(List<String> courseCodes) {
        List<Syllabus> list = new ArrayList<>();
        if (courseCodes == null) return list;
        for (String code : courseCodes) {
            findByCourseCode(code).ifPresent(list::add);
        }
        return list;
    }

    @Override
    public List<Syllabus> findAll() {
        // Consultar primero en Supabase
        try {
            List<Syllabus> cloudList = supabaseClient.findAll();
            if (!cloudList.isEmpty()) {
                return cloudList;
            }
        } catch (Exception e) {
            log.warn("[SyllabusRepo] Error listando sílabos desde Supabase: {}", e.getMessage());
        }

        // Fallback a H2
        return localRepository.findAll().stream().map(this::toDomain).toList();
    }

    private boolean isCompleteSyllabus(Syllabus s) {
        return (s.getWeeklySchedule() != null && !s.getWeeklySchedule().isEmpty())
                || (s.getEvaluations() != null && !s.getEvaluations().isEmpty())
                || (s.getFormula() != null && !s.getFormula().isBlank());
    }

    private void saveLocalSilently(Syllabus syllabus) {
        try {
            String json = objectMapper.writeValueAsString(syllabus);
            SyllabusEntity entity = SyllabusEntity.builder()
                    .id(syllabus.getId() != null ? syllabus.getId() : syllabus.getCourseCode())
                    .courseCode(syllabus.getCourseCode())
                    .courseName(syllabus.getCourseName())
                    .semester(syllabus.getSemester())
                    .credits(syllabus.getCredits())
                    .modality(syllabus.getModality())
                    .formula(syllabus.getFormula())
                    .rawJsonData(json)
                    .build();
            localRepository.save(entity);
        } catch (Exception ignored) {
        }
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
