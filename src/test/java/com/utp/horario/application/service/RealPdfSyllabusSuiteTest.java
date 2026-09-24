package com.utp.horario.application.service;

import com.utp.horario.application.service.pdf.PdfLayoutLine;
import com.utp.horario.application.service.pdf.PdfSpatialLayoutExtractor;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RealPdfSyllabusSuiteTest {

    private SyllabusParserEngine parserEngine;
    private static final String SAMPLES_DIR = "../labs/silabos ejemplos";

    @BeforeEach
    void setUp() {
        parserEngine = new SyllabusParserEngine();
    }

    private Syllabus parseLocalPdf(String fileName) throws Exception {
        Path path = Paths.get(SAMPLES_DIR, fileName).toAbsolutePath().normalize();
        assertTrue(Files.exists(path), "El archivo PDF debe existir: " + path);

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
        System.out.println("CRONOGRAMA (18 SEMANAS):");
        for (SyllabusWeeklySession w : s.getWeeklySchedule()) {
            String eval = (w.getEvaluation() != null) ? "[" + w.getEvaluation() + "]" : "    ";
            System.out.println(String.format("  Sem %-2d %-6s | %s", w.getWeek(), eval, w.getTopic()));
        }
        System.out.println("================================================================================\n");
    }

    @Test
    @DisplayName("Golden Test: Desarrollo Web Integrado (1 sesión/semana)")
    void testDesarrolloWebIntegrado() throws Exception {
        Syllabus s = parseLocalPdf("DESARROLLOWEBINTEGRADO_undefined (1).pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertTrue(s.getCourseName().toUpperCase().contains("DESARROLLO WEB"));
        assertEquals(2, s.getCredits(), "Desarrollo Web Integrado oficial es de 2 créditos");
        assertFalse(s.getEvaluations().isEmpty());
        assertEquals(18, s.getWeeklySchedule().size());

        // Validar que la suma de pesos sea 100%
        int sum = s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum();
        assertEquals(100, sum, "La suma de ponderaciones debe ser 100%");
    }

    @Test
    @DisplayName("Golden Test: Formación para la Investigación - Sistemas (Evaluaciones multilínea)")
    void testFormacionParaLaInvestigacion() throws Exception {
        Path path = Paths.get(SAMPLES_DIR, "FORMACIÓNPARALAINVESTIGACIÓN-SISTEMAS_undefined (3).pdf").toAbsolutePath().normalize();
        byte[] bytes = Files.readAllBytes(path);
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String rawText = stripper.getText(doc);
            int idx = rawText.indexOf("7. SISTEMA DE EVALUACIÓN");
            int endIdx = rawText.indexOf("8. FUENTES", idx);
            if (idx >= 0 && endIdx > idx) {
                System.out.println(">>> RAW EVALUATION BLOCK FOR FORMACION:\n" + rawText.substring(idx, endIdx));
            }
        }

        Syllabus s = parseLocalPdf("FORMACIÓNPARALAINVESTIGACIÓN-SISTEMAS_undefined (3).pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertTrue(s.getCourseName().toUpperCase().contains("INVESTIGACI"));
        assertEquals(5, s.getEvaluations().size());
        assertEquals(18, s.getWeeklySchedule().size());

        // Validar semanas exactas de evaluaciones
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

        int sum = s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum();
        assertEquals(100, sum);
    }

    @Test
    @DisplayName("Golden Test: Gestión del Servicio TI (2 sesiones/semana, 36 sesiones)")
    void testGestionDelServicioTi() throws Exception {
        Syllabus s = parseLocalPdf("GESTIÓNDELSERVICIOTI_undefined (1).pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertTrue(s.getCourseName().toUpperCase().contains("GESTI"));
        assertEquals(18, s.getWeeklySchedule().size());

        int sum = s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum();
        assertEquals(100, sum);
    }

    @Test
    @DisplayName("Golden Test: Herramientas para la Comunicación Efectiva (Virtual)")
    void testHerramientasComunicacionEfectiva() throws Exception {
        Syllabus s = parseLocalPdf("HERRAMIENTASPARALACOMUNICACIÓNEFECTIVA_undefined.pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertTrue(s.getCourseName().toUpperCase().contains("COMUNICACI"));
        assertEquals(18, s.getWeeklySchedule().size());

        int sum = s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum();
        assertEquals(100, sum);
    }

    @Test
    @DisplayName("Golden Test: Lenguajes de Programación (2 sesiones/semana)")
    void testLenguajesDeProgramacion() throws Exception {
        Syllabus s = parseLocalPdf("LENGUAJESDEPROGRAMACIÓN_undefined (1).pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertTrue(s.getCourseName().toUpperCase().contains("LENGUAJES"));
        assertEquals(18, s.getWeeklySchedule().size());

        int sum = s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum();
        assertEquals(100, sum);
    }

    @Test
    @DisplayName("Golden Test: Servicios Cloud (2 sesiones/semana, 36 sesiones)")
    void testServiciosCloud() throws Exception {
        Syllabus s = parseLocalPdf("SERVICIOSCLOUD_undefined (2).pdf");
        printSyllabusSummary(s);

        assertNotNull(s);
        assertTrue(s.getCourseName().toUpperCase().contains("CLOUD"));
        assertEquals(18, s.getWeeklySchedule().size());

        int sum = s.getEvaluations().stream().mapToInt(SyllabusEvaluation::getWeightPercent).sum();
        assertEquals(100, sum);
    }
}
