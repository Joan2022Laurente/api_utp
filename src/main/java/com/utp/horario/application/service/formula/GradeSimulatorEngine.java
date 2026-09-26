package com.utp.horario.application.service.formula;

import com.utp.horario.domain.model.CourseSummaryData.CourseGradeItem;
import com.utp.horario.domain.model.CourseSummaryData.EvaluationGrade;
import com.utp.horario.domain.model.EvaluationSimulationItem;
import com.utp.horario.domain.model.GradeSimulationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GradeSimulatorEngine {

    private static final Pattern FORMULA_TERM_PATTERN = Pattern.compile(
            "(?:\\((\\d{1,3})%\\)|(\\d{1,3})%)[\\*\\s]*\\[?\\s*([A-Za-z0-9_\\-]+)\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );

    public GradeSimulationResult simulate(CourseGradeItem course, double targetGrade) {
        if (course == null) {
            throw new IllegalArgumentException("El curso no puede ser nulo.");
        }

        String rawFormula = course.getFormula() != null ? course.getFormula().trim() : "";
        List<AssessmentWeight> terms = parseTermsFromFormula(rawFormula);

        List<EvaluationGrade> evaluations = course.getEvaluations() != null ? course.getEvaluations() : List.of();
        Map<String, EvaluationGrade> evalMap = new LinkedHashMap<>();
        for (EvaluationGrade eg : evaluations) {
            if (eg.getShortName() != null && !eg.getShortName().isBlank()) {
                evalMap.put(eg.getShortName().trim().toUpperCase(), eg);
            }
        }

        // Si la fórmula no pudo extraerse, distribuir 100% equitativamente entre las evaluaciones conocidas
        if (terms.isEmpty() && !evaluations.isEmpty()) {
            int count = evaluations.size();
            int baseWeight = 100 / count;
            int remainder = 100 % count;
            for (int i = 0; i < count; i++) {
                EvaluationGrade eg = evaluations.get(i);
                int w = baseWeight + (i == count - 1 ? remainder : 0);
                String label = (eg.getShortName() != null && !eg.getShortName().isBlank()) 
                        ? eg.getShortName().trim().toUpperCase() 
                        : ("EVAL" + (i + 1));
                terms.add(new AssessmentWeight(label, w));
            }
        }

        double accumulatedScore = 0.0;
        int gradedWeight = 0;
        int remainingWeight = 0;
        List<EvaluationSimulationItem> simulationItems = new ArrayList<>();

        for (AssessmentWeight term : terms) {
            String label = term.label();
            int weight = term.percentage();

            EvaluationGrade matched = evalMap.get(label);
            boolean isGraded = false;
            Double currentGrade = null;
            double pointsContributed = 0.0;
            String displayName = (matched != null && matched.getName() != null) ? matched.getName() : label;

            if (matched != null && Boolean.TRUE.equals(matched.getIsGraded())) {
                try {
                    double parsed = Double.parseDouble(matched.getValue().trim());
                    if (parsed > 1.0) {
                        currentGrade = parsed;
                        isGraded = true;
                        pointsContributed = round((weight / 100.0) * currentGrade);
                        accumulatedScore += pointsContributed;
                        gradedWeight += weight;
                    }
                } catch (Exception ignored) {
                    // Valor no numérico
                }
            }

            if (!isGraded) {
                remainingWeight += weight;
            }

            simulationItems.add(EvaluationSimulationItem.builder()
                    .shortName(label)
                    .name(displayName)
                    .weightPercentage(weight)
                    .currentGrade(currentGrade)
                    .isGraded(isGraded)
                    .pointsContributed(pointsContributed)
                    .build());
        }

        accumulatedScore = round(accumulatedScore);
        double minPossibleGrade = accumulatedScore;
        double maxPossibleGrade = round(accumulatedScore + (remainingWeight / 100.0) * 20.0);
        double neededScore = targetGrade - accumulatedScore;

        Double requiredAverageOnPending = null;
        boolean isPassed = false;
        String status;
        String message;

        if (remainingWeight == 0) {
            isPassed = accumulatedScore >= targetGrade;
            status = isPassed ? "PASSED" : "FAILED";
            message = isPassed 
                    ? String.format("Curso concluido con éxito. Calificación final obtenida: %.2f.", accumulatedScore)
                    : String.format("Curso concluido sin alcanzar la meta. Calificación final: %.2f.", accumulatedScore);
        } else if (neededScore <= 0) {
            requiredAverageOnPending = 0.0;
            isPassed = true;
            status = "ALREADY_PASSED";
            message = String.format("¡Meta asegurada! Ya acumulaste %.2f puntos sobre %.1f. Incluso con 00 en lo pendiente apruebas.", accumulatedScore, targetGrade);
        } else {
            double reqAvg = round((neededScore / remainingWeight) * 100.0);
            requiredAverageOnPending = reqAvg;

            if (reqAvg > 20.0) {
                status = "IMPOSSIBLE";
                message = String.format("Inalcanzable: Necesitarías %.2f en las evaluaciones pendientes para llegar a %.1f (Puntaje máx alcanzable: %.2f).", reqAvg, targetGrade, maxPossibleGrade);
            } else if (reqAvg > 15.0) {
                status = "CHALLENGING";
                message = String.format("Exigencia alta: Necesitas un promedio mínimo de %.2f en las evaluaciones restantes para alcanzar %.1f.", reqAvg, targetGrade);
            } else {
                status = "ACHIEVABLE";
                message = String.format("Alcanzable: Necesitas un promedio de %.2f en las evaluaciones restantes para asegurar %.1f.", reqAvg, targetGrade);
            }
        }

        return GradeSimulationResult.builder()
                .courseCode(course.getCourseCode())
                .courseName(course.getCourseName())
                .formula(rawFormula)
                .targetGrade(targetGrade)
                .currentAccumulatedScore(accumulatedScore)
                .gradedWeightPercentage(gradedWeight)
                .remainingWeightPercentage(remainingWeight)
                .requiredAverageOnPending(requiredAverageOnPending)
                .minPossibleGrade(minPossibleGrade)
                .maxPossibleGrade(maxPossibleGrade)
                .isPassed(isPassed)
                .status(status)
                .message(message)
                .evaluations(simulationItems)
                .build();
    }

    private List<AssessmentWeight> parseTermsFromFormula(String formula) {
        List<AssessmentWeight> terms = new ArrayList<>();
        if (formula == null || formula.isBlank()) {
            return terms;
        }

        Matcher matcher = FORMULA_TERM_PATTERN.matcher(formula);
        while (matcher.find()) {
            String pctGroup = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String label = matcher.group(3);
            if (pctGroup != null && label != null) {
                try {
                    int weight = Integer.parseInt(pctGroup.trim());
                    terms.add(new AssessmentWeight(label.trim().toUpperCase(), weight));
                } catch (NumberFormatException ignored) {}
            }
        }
        return terms;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
