package com.utp.horario.application.service;

import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RealPdfSyllabusSuiteTest {

    private SyllabusParserEngine parserEngine;
    private SyllabusDeterministicValidator validator;
    private static final String SAMPLES_DIR = "src/test/resources/silabos";

    @BeforeEach
    void setUp() {
        parserEngine = new SyllabusParserEngine();
        validator = new SyllabusDeterministicValidator();
    }

    private Syllabus parseLocalPdf(String fileName) throws Exception {
        Path path = Paths.get(SAMPLES_DIR, fileName).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            var res = getClass().getClassLoader().getResource("silabos/" + fileName);
            if (res != null) {
                path = Paths.get(res.toURI());
            }
        }
        assertTrue(Files.exists(path), "El archivo PDF oficial debe existir en test resources: " + path);

        byte[] bytes = Files.readAllBytes(path);
        return parserEngine.parsePdf(bytes, fileName);
    }

    private void printSyllabusSummary(Syllabus s) {
        System.out.println("================================================================================");
        System.out.println("CURSO: " + s.getCourseName() + " (" + s.getCourseCode() + ")");
        System.out.println("CRÉDITOS: " + s.getCredits() + " | MODALIDAD: " + s.getModality() + " | HORAS: " + s.getWeeklyHours());
        System.out.println("FÓRMULA: " + s.getFormula());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("EVALUACIONES:");
        int totalWeight = 0;
        for (SyllabusEvaluation e : s.getEvaluations()) {
            totalWeight += e.getWeightPercent();
            System.out.println(String.format("  [%-5s] Sem %-2d | %-3d%% | %-10s | %s",
                    e.getType(), e.getWeek(), e.getWeightPercent(), e.getModality(), e.getDescription()));
        }
        System.out.println("TOTAL PONDERACIÓN: " + totalWeight + "%");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("CRONOGRAMA (" + s.getWeeklySchedule().size() + " SEMANAS REALES):");
        for (SyllabusWeeklySession w : s.getWeeklySchedule()) {
            String eval = (w.getEvaluation() != null) ? "[" + w.getEvaluation() + "]" : "    ";
            System.out.println(String.format("  Sem %-2d %-6s | %s", w.getWeek(), eval, w.getTopic()));
        }
        System.out.println("================================================================================\n");
    }

    @Test
    @DisplayName("Golden Test: Desarrollo Web Integrado (1 sesión/semana)")
    void testDesarrolloWebIntegrado() throws Exception {
        Syllabus s = parseLocalPdf("100000ST61_DesarrolloWebIntegrado.pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertEquals("100000ST61", s.getCourseCode());
        assertTrue(s.getCourseName().toUpperCase().contains("DESARROLLO WEB INTEGRADO"));
        assertEquals(2, s.getCredits(), "Desarrollo Web Integrado oficial es de 2 créditos");
        assertEquals("Presencial", s.getModality());
        assertEquals(3, s.getWeeklyHours());
        assertEquals(17, s.getWeeklySchedule().size(), "Semanas reales extraídas del PDF sin relleno sintético");

        // Evaluaciones declaradas en el PDF
        assertEquals(4, s.getEvaluations().size());
        assertEquals(100, s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum());

        SyllabusEvaluation apf1 = s.getEvaluations().stream().filter(e -> "APF1".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(5, apf1.getWeek());
        assertEquals(20, apf1.getWeightPercent());

        SyllabusEvaluation apf2 = s.getEvaluations().stream().filter(e -> "APF2".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(10, apf2.getWeek());
        assertEquals(20, apf2.getWeightPercent());

        SyllabusEvaluation apf3 = s.getEvaluations().stream().filter(e -> "APF3".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(15, apf3.getWeek());
        assertEquals(20, apf3.getWeightPercent());

        SyllabusEvaluation proy = s.getEvaluations().stream().filter(e -> "PROY".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(18, proy.getWeek());
        assertEquals(40, proy.getWeightPercent());

        // Deterministic quality gate
        SyllabusDeterministicValidator.ValidationResult result = validator.validate(s);
        assertTrue(result.isValid(), "Debe pasar la validación determinista sin violaciones: " + result.getViolations());
    }

    @Test
    @DisplayName("Golden Test: Formación para la Investigación - Sistemas (Evaluaciones multilínea)")
    void testFormacionParaLaInvestigacion() throws Exception {
        Syllabus s = parseLocalPdf("100000SI82_FormacionParaLaInvestigacion.pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertEquals("100000SI82", s.getCourseCode());
        assertTrue(s.getCourseName().toUpperCase().contains("FORMACIÓN PARA LA INVESTIGACIÓN"));
        assertEquals(4, s.getCredits());
        assertEquals("Virtual en vivo", s.getModality());
        assertEquals(4, s.getWeeklyHours());
        assertEquals(15, s.getWeeklySchedule().size(), "Semanas reales extraídas del PDF sin relleno sintético");

        assertEquals(5, s.getEvaluations().size());
        assertEquals(100, s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum());

        SyllabusEvaluation ati1 = s.getEvaluations().stream().filter(e -> "ATI1".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(4, ati1.getWeek());
        assertEquals(10, ati1.getWeightPercent());

        SyllabusEvaluation ati2 = s.getEvaluations().stream().filter(e -> "ATI2".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(8, ati2.getWeek());
        assertEquals(20, ati2.getWeightPercent());

        SyllabusEvaluation ati3 = s.getEvaluations().stream().filter(e -> "ATI3".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(13, ati3.getWeek());
        assertEquals(20, ati3.getWeightPercent());

        SyllabusEvaluation pa = s.getEvaluations().stream().filter(e -> "PA".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(17, pa.getWeek());
        assertEquals(20, pa.getWeightPercent());

        SyllabusEvaluation ti = s.getEvaluations().stream().filter(e -> "TI".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(18, ti.getWeek());
        assertEquals(30, ti.getWeightPercent());

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(s);
        assertTrue(result.isValid(), "Debe pasar la validación determinista sin violaciones: " + result.getViolations());
    }

    @Test
    @DisplayName("Golden Test: Gestión del Servicio TI (2 sesiones/semana, cronograma con números)")
    void testGestionDelServicioTi() throws Exception {
        Syllabus s = parseLocalPdf("100000S74T_GestionDelServicioTi.pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertEquals("100000S74T", s.getCourseCode());
        assertTrue(s.getCourseName().toUpperCase().contains("GESTIÓN DEL SERVICIO TI"));
        assertEquals(3, s.getCredits());
        assertEquals("Virtual en vivo", s.getModality());
        assertEquals(4, s.getWeeklyHours());
        assertEquals(17, s.getWeeklySchedule().size(), "Semanas reales extraídas del PDF sin relleno sintético");

        assertEquals(4, s.getEvaluations().size());
        assertEquals(100, s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum());

        SyllabusEvaluation pc1 = s.getEvaluations().stream().filter(e -> "PC1".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(6, pc1.getWeek());
        assertEquals(25, pc1.getWeightPercent());

        SyllabusEvaluation pc2 = s.getEvaluations().stream().filter(e -> "PC2".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(14, pc2.getWeek());
        assertEquals(25, pc2.getWeightPercent());

        SyllabusEvaluation pa = s.getEvaluations().stream().filter(e -> "PA".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(17, pa.getWeek());
        assertEquals(10, pa.getWeightPercent());

        SyllabusEvaluation proy = s.getEvaluations().stream().filter(e -> "PROY".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(18, proy.getWeek());
        assertEquals(40, proy.getWeightPercent());

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(s);
        assertTrue(result.isValid(), "Debe pasar la validación determinista sin violaciones: " + result.getViolations());
    }

    @Test
    @DisplayName("Golden Test: Lenguajes de Programación (cronograma sin semana 18 explicita)")
    void testLenguajesDeProgramacion() throws Exception {
        Syllabus s = parseLocalPdf("100000SI68_LenguajesDeProgramacion.pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertEquals("100000SI68", s.getCourseCode());
        assertTrue(s.getCourseName().toUpperCase().contains("LENGUAJES DE PROGRAMACIÓN"));
        assertEquals(2, s.getCredits());
        assertEquals("Presencial", s.getModality());
        assertEquals(2, s.getWeeklyHours());
        assertEquals(16, s.getWeeklySchedule().size(), "Semanas reales extraídas del PDF sin relleno sintético");

        assertEquals(4, s.getEvaluations().size());
        assertEquals(100, s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum());

        SyllabusEvaluation pc1 = s.getEvaluations().stream().filter(e -> "PC1".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(5, pc1.getWeek());
        assertEquals(25, pc1.getWeightPercent());

        SyllabusEvaluation pc2 = s.getEvaluations().stream().filter(e -> "PC2".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(10, pc2.getWeek());
        assertEquals(25, pc2.getWeightPercent());

        SyllabusEvaluation pa = s.getEvaluations().stream().filter(e -> "PA".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(17, pa.getWeek());
        assertEquals(10, pa.getWeightPercent());

        SyllabusEvaluation proy = s.getEvaluations().stream().filter(e -> "PROY".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(18, proy.getWeek());
        assertEquals(40, proy.getWeightPercent());

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(s);
        assertTrue(result.isValid(), "Debe pasar la validación determinista sin violaciones: " + result.getViolations());
    }

    @Test
    @DisplayName("Golden Test: Servicios Cloud (2 sesiones/semana, 34 sesiones)")
    void testServiciosCloud() throws Exception {
        Syllabus s = parseLocalPdf("100000SI97_ServiciosCloud.pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertEquals("100000SI97", s.getCourseCode());
        assertTrue(s.getCourseName().toUpperCase().contains("SERVICIOS CLOUD"));
        assertEquals(3, s.getCredits());
        assertEquals("Presencial", s.getModality());
        assertEquals(4, s.getWeeklyHours());
        assertEquals(17, s.getWeeklySchedule().size(), "Semanas reales extraídas del PDF sin relleno sintético");

        assertEquals(4, s.getEvaluations().size());
        assertEquals(100, s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum());

        SyllabusEvaluation pc1 = s.getEvaluations().stream().filter(e -> "PC1".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(3, pc1.getWeek());
        assertEquals(25, pc1.getWeightPercent());

        SyllabusEvaluation pc2 = s.getEvaluations().stream().filter(e -> "PC2".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(12, pc2.getWeek());
        assertEquals(25, pc2.getWeightPercent());

        SyllabusEvaluation pa = s.getEvaluations().stream().filter(e -> "PA".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(17, pa.getWeek());
        assertEquals(10, pa.getWeightPercent());

        SyllabusEvaluation proy = s.getEvaluations().stream().filter(e -> "PROY".equals(e.getType())).findFirst().orElseThrow();
        assertEquals(18, proy.getWeek());
        assertEquals(40, proy.getWeightPercent());

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(s);
        assertTrue(result.isValid(), "Debe pasar la validación determinista sin violaciones: " + result.getViolations());
    }
}
