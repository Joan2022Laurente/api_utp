package com.utp.horario.application.service.validation;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filtro determinista estricto para validar el JSON devuelto por la IA
 * antes de persistir en la base de datos de producción (Supabase).
 * 
 * Evita la polución de la BD con alucinaciones, cronogramas rotos o fórmulas inconsistentes.
 */
@Slf4j
@Component
public class SyllabusDeterministicValidator {

    private static final Pattern HALLUCINATION_PATTERN = Pattern.compile(
            "(?i)(lorem\\s+ipsum|as\\s+an\\s+ai|language\\s+model|\\[insert|\\[completar|\\[placeholder|not\\s+provided|no\\s+disponible)",
            Pattern.CASE_INSENSITIVE
    );

    @Getter
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> violations;

        public ValidationResult(List<String> violations) {
            this.violations = violations != null ? violations : List.of();
            this.valid = this.violations.isEmpty();
        }

        public static ValidationResult ok() {
            return new ValidationResult(List.of());
        }

        public static ValidationResult fail(List<String> violations) {
            return new ValidationResult(violations);
        }
    }

    public ValidationResult validate(Syllabus syllabus) {
        if (syllabus == null) {
            return ValidationResult.fail(List.of("El objeto Syllabus es nulo"));
        }

        List<String> errors = new ArrayList<>();

        // 1. Validación de Identidad del Curso
        validateCourseIdentity(syllabus, errors);

        // 2. Validación del Sistema de Evaluaciones y Pesos
        validateEvaluations(syllabus, errors);

        // 3. Validación de la Fórmula Matemática
        validateFormula(syllabus, errors);

        // 4. Validación del Cronograma Semanal
        validateWeeklySchedule(syllabus, errors);

        // 5. Filtro Anti-Alucinaciones
        validateAntiHallucination(syllabus, errors);

        if (!errors.isEmpty()) {
            log.warn("[SyllabusValidator] ❌ Validación determinista fallida para {}: {}", 
                    syllabus.getCourseCode(), errors);
        } else {
            log.info("[SyllabusValidator] ✅ Sílabo validado exitosamente para {} ({} evaluaciones, {} semanas)", 
                    syllabus.getCourseCode(), 
                    syllabus.getEvaluations() != null ? syllabus.getEvaluations().size() : 0,
                    syllabus.getWeeklySchedule() != null ? syllabus.getWeeklySchedule().size() : 0);
        }

        return new ValidationResult(errors);
    }

    private void validateCourseIdentity(Syllabus s, List<String> errors) {
        if (s.getCourseCode() == null || s.getCourseCode().isBlank()) {
            errors.add("El código del curso no puede estar vacío");
        }

        if (s.getCourseName() == null || s.getCourseName().trim().length() < 3) {
            errors.add("El nombre del curso es inválido o demasiado corto");
        } else if (isGarbageString(s.getCourseName())) {
            errors.add("El nombre del curso contiene valores basura: '" + s.getCourseName() + "'");
        }

        if (s.getCredits() != null && (s.getCredits() < 1 || s.getCredits() > 15)) {
            errors.add("El número de créditos está fuera de rango [1-15]: " + s.getCredits());
        }
    }

    private void validateEvaluations(Syllabus s, List<String> errors) {
        List<SyllabusEvaluation> evals = s.getEvaluations();
        if (evals == null || evals.isEmpty()) {
            errors.add("La lista de evaluaciones no puede estar vacía");
            return;
        }

        if (evals.size() < 2) {
            errors.add("Un sílabo universitario debe tener al menos 2 evaluaciones declaradas");
        }

        int totalWeight = 0;
        Set<String> seenTypes = new HashSet<>();

        for (int i = 0; i < evals.size(); i++) {
            SyllabusEvaluation ev = evals.get(i);
            if (ev.getType() == null || ev.getType().isBlank()) {
                errors.add("Evaluación #" + (i + 1) + " tiene tipo vacío");
                continue;
            }

            String type = ev.getType().trim().toUpperCase();
            if (isGarbageString(type)) {
                errors.add("Tipo de evaluación basura: '" + type + "'");
            }
            seenTypes.add(type);

            if (ev.getWeightPercent() != null) {
                if (ev.getWeightPercent() < 1 || ev.getWeightPercent() > 100) {
                    errors.add("Ponderación de '" + type + "' fuera de rango [1-100]: " + ev.getWeightPercent());
                }
                totalWeight += ev.getWeightPercent();
            }

            if (ev.getWeek() != null && (ev.getWeek() < 1 || ev.getWeek() > 18)) {
                errors.add("Semana de evaluación '" + type + "' fuera del semestre [1-18]: " + ev.getWeek());
            }
        }

        // Tolerancia de pesos: entre 98% y 102% para admitir redondeos de 33.3% * 3
        if (totalWeight < 98 || totalWeight > 102) {
            errors.add("La suma de pesos de las evaluaciones debe ser ~100%, pero sumó: " + totalWeight + "%");
        }
    }

    private void validateFormula(Syllabus s, List<String> errors) {
        String formula = s.getFormula();
        if (formula == null || formula.isBlank()) {
            errors.add("La fórmula de calificación no puede estar vacía");
            return;
        }

        if (isGarbageString(formula)) {
            errors.add("Fórmula contiene texto basura: '" + formula + "'");
            return;
        }

        // Debe contener al menos un operador o variable
        if (!formula.contains("+") && !formula.contains("*") && !formula.contains("%") && !formula.matches(".*[A-Za-z].*")) {
            errors.add("Fórmula no tiene sintaxis matemática válida: " + formula);
        }
    }

    private void validateWeeklySchedule(Syllabus s, List<String> errors) {
        List<SyllabusWeeklySession> schedule = s.getWeeklySchedule();
        if (schedule == null || schedule.isEmpty()) {
            errors.add("El cronograma semanal no puede estar vacío");
            return;
        }

        if (schedule.size() < 8) {
            errors.add("El cronograma debe cubrir al menos 8 semanas (encontradas: " + schedule.size() + ")");
        }

        Set<Integer> seenWeeks = new HashSet<>();

        for (SyllabusWeeklySession session : schedule) {
            Integer week = session.getWeek();
            if (week == null) {
                errors.add("Existe una sesión semanal sin número de semana");
                continue;
            }

            if (week < 1 || week > 18) {
                errors.add("Semana fuera de rango académico [1-18]: " + week);
            }

            if (!seenWeeks.add(week)) {
                errors.add("Semana duplicada en el cronograma: Semana " + week);
            }

            if (session.getTopic() == null || session.getTopic().trim().length() < 3) {
                errors.add("Semana " + week + " no tiene un tema válido asignado");
            } else if (isGarbageString(session.getTopic())) {
                errors.add("Semana " + week + " tiene un tema con texto basura: '" + session.getTopic() + "'");
            }
        }
    }

    private void validateAntiHallucination(Syllabus s, List<String> errors) {
        checkHallucination(s.getCourseName(), "Nombre del Curso", errors);
        checkHallucination(s.getLearningGoal(), "Logro del Curso", errors);
        checkHallucination(s.getFormula(), "Fórmula", errors);
    }

    private void checkHallucination(String value, String fieldName, List<String> errors) {
        if (value != null && HALLUCINATION_PATTERN.matcher(value).find()) {
            errors.add("El campo '" + fieldName + "' contiene marcas de alucinación de LLM");
        }
    }

    private boolean isGarbageString(String str) {
        if (str == null) return true;
        String lower = str.trim().toLowerCase();
        return lower.equals("null") || 
               lower.equals("n/a") || 
               lower.equals("undefined") || 
               lower.equals("none") || 
               lower.equals("sin datos") || 
               lower.equals("desconocido") ||
               lower.equals("-");
    }
}
