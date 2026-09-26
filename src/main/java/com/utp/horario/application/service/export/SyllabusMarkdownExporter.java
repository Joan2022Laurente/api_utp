package com.utp.horario.application.service.export;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.springframework.stereotype.Component;

@Component
public class SyllabusMarkdownExporter {

    public String exportToMarkdown(Syllabus syllabus) {
        if (syllabus == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 1. Título Principal
        String courseName = syllabus.getCourseName() != null ? syllabus.getCourseName() : "CURSO UTP";
        String courseCode = syllabus.getCourseCode() != null ? syllabus.getCourseCode() : "";
        sb.append("# SÍLABO OFICIAL: ").append(courseName);
        if (!courseCode.isBlank()) {
            sb.append(" (").append(courseCode).append(")");
        }
        sb.append("\n\n");

        // 2. Información General
        sb.append("## 1. Información General\n\n");
        if (syllabus.getSemester() != null && !syllabus.getSemester().isBlank()) {
            sb.append("- **Periodo Académico:** ").append(syllabus.getSemester()).append("\n");
        }
        if (syllabus.getCredits() != null) {
            sb.append("- **Créditos:** ").append(syllabus.getCredits()).append("\n");
        }
        if (syllabus.getModality() != null && !syllabus.getModality().isBlank()) {
            sb.append("- **Modalidad de Enseñanza:** ").append(syllabus.getModality()).append("\n");
        }
        if (syllabus.getWeeklyHours() != null) {
            sb.append("- **Horas Semanales:** ").append(syllabus.getWeeklyHours()).append("\n");
        }
        if (syllabus.getCareers() != null && !syllabus.getCareers().isEmpty()) {
            sb.append("- **Carreras:** ").append(String.join(", ", syllabus.getCareers())).append("\n");
        }
        sb.append("\n");

        // 3. Logro General de Aprendizaje
        if (syllabus.getLearningGoal() != null && !syllabus.getLearningGoal().isBlank()) {
            sb.append("## 2. Logro General de Aprendizaje\n\n");
            sb.append(syllabus.getLearningGoal().trim()).append("\n\n");
        }

        // 4. Sistema de Evaluación
        sb.append("## 3. Sistema de Evaluación\n\n");
        if (syllabus.getFormula() != null && !syllabus.getFormula().isBlank()) {
            sb.append("**Fórmula Oficial de Calificación:** `").append(syllabus.getFormula()).append("`\n\n");
        }

        if (syllabus.getEvaluations() != null && !syllabus.getEvaluations().isEmpty()) {
            sb.append("| Tipo | Descripción | Semana | Ponderación | Modalidad | Observación |\n");
            sb.append("| :--- | :--- | :---: | :---: | :--- | :--- |\n");
            for (SyllabusEvaluation ev : syllabus.getEvaluations()) {
                String type = ev.getType() != null ? ev.getType() : "-";
                String desc = ev.getDescription() != null ? ev.getDescription() : "-";
                String week = ev.getWeek() != null ? ev.getWeek().toString() : "-";
                String weight = ev.getWeightPercent() != null ? ev.getWeightPercent() + "%" : "-";
                String mod = ev.getModality() != null ? ev.getModality() : "-";
                String obs = ev.getObservation() != null ? ev.getObservation() : "-";
                sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n", type, desc, week, weight, mod, obs));
            }
            sb.append("\n");
        }

        // 5. Cronograma de Actividades Semanales
        if (syllabus.getWeeklySchedule() != null && !syllabus.getWeeklySchedule().isEmpty()) {
            sb.append("## 4. Cronograma de Actividades Semanales (18 Semanas)\n\n");
            sb.append("| Semana | Unidad | Temario y Contenidos | Actividades | Evaluación |\n");
            sb.append("| :---: | :--- | :--- | :--- | :---: |\n");
            for (SyllabusWeeklySession session : syllabus.getWeeklySchedule()) {
                String sem = session.getWeek() != null ? session.getWeek().toString() : "-";
                String unit = session.getUnit() != null ? session.getUnit() : "-";
                String topic = session.getTopic() != null ? session.getTopic() : "-";
                String act = session.getActivities() != null ? session.getActivities() : "-";
                String eval = session.getEvaluation() != null ? session.getEvaluation() : "-";
                sb.append(String.format("| %s | %s | %s | %s | %s |\n", sem, unit, topic, act, eval));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String rawTextToMarkdown(String rawText, String courseCode) {
        if (rawText == null || rawText.isBlank()) {
            return "# SÍLABO UTP (" + courseCode + ")\n\n*No hay contenido disponible para este curso.*";
        }

        String clean = rawText.replace("\r\n", "\n")
                .replaceAll("(?i)(^|\\n)(\\d+\\.\\s+[A-ZÁÉÍÓÚÑ\\s]+)", "\n\n## $2\n\n")
                .replaceAll("(?i)(^|\\n)(\\d+\\.\\d+\\.\\s+[A-ZÁÉÍÓÚÑ\\s]+)", "\n\n### $2\n\n")
                .replaceAll("(?i)(Semana\\s+\\d+)", "\n\n#### $1\n")
                .trim();

        return "# SÍLABO OFICIAL (TEXTO EXTRAÍDO DEL PDF)\n**Código de Asignatura:** " + courseCode + "\n\n" + clean;
    }
}
