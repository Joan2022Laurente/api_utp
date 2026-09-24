package com.utp.horario.application.service;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyllabusParserEngineTest {

    private SyllabusParserEngine parserEngine;

    @BeforeEach
    void setUp() {
        parserEngine = new SyllabusParserEngine();
    }

    @Test
    @DisplayName("Debe parsear exitosamente el texto oficial de un sílabo UTP con todas sus secciones")
    void shouldParseOfficialSyllabusTextCorrectly() {
        String samplePdfText = """
                SÍLABO
                DESARROLLO WEB INTEGRADO (100000ST61)
                2026 - Ciclo 2 Agosto
                
                1. INFORMACIÓN GENERAL
                1.1. Carrera:
                Ingeniería de Sistemas e Informática
                Ingeniería de Software
                1.2. Créditos: 3
                1.3. Enseñanza de curso: Presencial
                1.4. Horas semanales: 4
                
                4. LOGRO GENERAL DE APRENDIZAJE
                Al finalizar el curso, el estudiante desarrolla soluciones web integrales mediante frameworks de front-end y back-end, siguiendo el diseño orientado a objetos y tecnologías web para resolver problemas empresariales.
                
                7. SISTEMA DE EVALUACIÓN
                El cálculo del promedio final considera la siguiente fórmula:
                (20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY
                
                Donde:
                Tipo Descripción Semana Observación
                APF1 AVANCE DE PROYECTO FINAL 1 5 Evaluación grupal en aula
                APF2 AVANCE DE PROYECTO FINAL 2 10 Evaluación grupal
                APF3 AVANCE DE PROYECTO FINAL 3 15 Evaluación grupal
                PROY PROYECTO FINAL 18 Sustentación grupal
                
                8. CRONOGRAMA DE ACTIVIDADES
                Semana 1
                Temario: Arquitectura web moderna, Spring Boot y configuración inicial
                
                Semana 2
                Temario: Configuración de endpoints, controladores y Dependency Injection
                
                Semana 5
                Temario: Evaluación APF1 (20%): API RESTful funcional con Spring Boot y TDD
                
                Semana 10
                Temario: Evaluación APF2 (20%): Backend JPA + Spring Security + JWT
                
                Semana 18
                Temario: Sustentación de PROYECTO FINAL PROY (40%): Demostración en vivo
                """;

        Syllabus result = parserEngine.parse(samplePdfText, "100000ST61");

        assertNotNull(result);
        assertEquals("DESARROLLO WEB INTEGRADO", result.getCourseName());
        assertEquals("100000ST61", result.getCourseCode());
        assertEquals(3, result.getCredits());
        assertEquals("Presencial", result.getModality());
        assertEquals(4, result.getWeeklyHours());
        assertTrue(result.getCareers().contains("Ingeniería de Sistemas e Informática"));
        assertTrue(result.getLearningGoal().contains("desarrolla soluciones web integrales"));
        assertEquals("(20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY", result.getFormula());

        // Validar evaluaciones
        assertNotNull(result.getEvaluations());
        assertEquals(4, result.getEvaluations().size());

        SyllabusEvaluation apf1 = result.getEvaluations().stream()
                .filter(e -> "APF1".equals(e.getType()))
                .findFirst()
                .orElse(null);
        assertNotNull(apf1);
        assertEquals(20, apf1.getWeightPercent());

        SyllabusEvaluation proy = result.getEvaluations().stream()
                .filter(e -> "PROY".equals(e.getType()))
                .findFirst()
                .orElse(null);
        assertNotNull(proy);
        assertEquals(40, proy.getWeightPercent());
        assertEquals("Grupal", proy.getModality());

        // Validar 18 semanas de cronograma
        assertNotNull(result.getWeeklySchedule());
        assertEquals(18, result.getWeeklySchedule().size());
        assertEquals("Arquitectura web moderna, Spring Boot y configuración inicial", result.getWeeklySchedule().get(0).getTopic());
        assertEquals("Configuración de endpoints, controladores y Dependency Injection", result.getWeeklySchedule().get(1).getTopic());
    }

    @Test
    @DisplayName("Debe manejar texto vacío o nulo generando un objeto base seguro")
    void shouldHandleEmptyOrNullInputGracefully() {
        Syllabus emptyResult = parserEngine.parse(null, "100000TEST");
        assertNotNull(emptyResult);
        assertEquals("100000TEST", emptyResult.getCourseCode());
        assertEquals("ASIGNATURA UTP", emptyResult.getCourseName());
        assertNotNull(emptyResult.getFormula());
    }

    @Test
    @DisplayName("Debe parsear cronogramas con 2 sesiones por semana sin desfasar las semanas")
    void shouldParseMultiSessionPerWeekCronogramasCorrectly() {
        String samplePdfText = """
                SÍLABO
                SERVICIOS CLOUD (100000SI97)
                2026 - Ciclo 2 Agosto
                
                1. INFORMACIÓN GENERAL
                1.2. Créditos: 3
                1.3. Enseñanza de curso: Presencial
                1.4. Horas semanales: 4
                
                4. LOGRO GENERAL DE APRENDIZAJE
                Al finalizar el curso el estudiante diseña soluciones tecnológicas en la nube para la gestión de la información de las empresas.
                
                7. SISTEMA DE EVALUACIÓN
                El cálculo del promedio final considera la siguiente fórmula:
                (25%)PC1 + (25%)PC2 + (10%)PA + (40%)PROY
                
                Donde:
                Tipo Descripción Semana Observación
                PC1 PRÁCTICA CALIFICADA 1 3 Individual
                PC2 PRÁCTICA CALIFICADA 2 12 Individual
                PA PARTICIPACIÓN EN CLASE 17 Individual
                PROY PROYECTO FINAL 18 Grupal
                
                8. CRONOGRAMA DE ACTIVIDADES
                1 1 Introducción a Computación en Nube 1. Conceptos 2. Características
                1 2 Sistemas Distribuidos 1. Arquitecturas y Comunicación
                2 3 Virtualización 1. Fundamentos de virtualización.
                2 4 Gerenciamiento de contenedores
                3 5 Almacenamiento en la nube
                3 6 Frameworks de base de datos
                4 7 Plataformas basadas en Contenedores
                4 8 Componentes del contenedor
                12 23 Planificación (Scheduling)
                12 24 Asignación eficiente de recursos
                18 35 Sustentación final
                18 36 Evaluación PROYECTO FINAL
                """;

        Syllabus result = parserEngine.parse(samplePdfText, "100000SI97");

        assertNotNull(result);
        assertEquals("SERVICIOS CLOUD", result.getCourseName());
        assertEquals("100000SI97", result.getCourseCode());
        assertEquals(18, result.getWeeklySchedule().size());

        // Semana 1 debe contener ambos temas de la semana 1
        assertTrue(result.getWeeklySchedule().get(0).getTopic().contains("Introducción a Computación en Nube"));
        assertTrue(result.getWeeklySchedule().get(0).getTopic().contains("Sistemas Distribuidos"));

        // Semana 2 debe contener virtualización
        assertTrue(result.getWeeklySchedule().get(1).getTopic().contains("Virtualización"));

        // Semana 3 debe contener almacenamiento
        assertTrue(result.getWeeklySchedule().get(2).getTopic().contains("Almacenamiento en la nube"));

        // Semana 4 debe contener plataformas basadas en contenedores
        assertTrue(result.getWeeklySchedule().get(3).getTopic().contains("Plataformas basadas en Contenedores"));

        // Semana 12 debe contener Planificación
        assertTrue(result.getWeeklySchedule().get(11).getTopic().contains("Planificación (Scheduling)"));

        // Semana 18 debe contener PROYECTO FINAL
        assertTrue(result.getWeeklySchedule().get(17).getTopic().contains("PROYECTO FINAL"));
    }

    @Test
    @DisplayName("Debe parsear cronograma de Gestión del Servicio TI con sesiones distribuidas")
    void shouldParseGestionDelServicioTICorrectly() {
        String samplePdfText = """
                SÍLABO
                GESTIÓN DEL SERVICIO TI (100000S74T)
                2026 - Ciclo 2 Agosto
                
                1. INFORMACIÓN GENERAL
                1.2. Créditos: 3
                1.3. Enseñanza de curso: Remoto
                1.4. Horas semanales: 4
                
                4. LOGRO GENERAL DE APRENDIZAJE
                Al finalizar el curso el alumno aplica el marco de referencia ITIL y otras metodologías complementarias.
                
                7. SISTEMA DE EVALUACIÓN
                (25%)PC1 + (25%)PC2 + (10%)PA + (40%)PROY
                
                Donde:
                Tipo Descripción Semana Observación
                PC1 PRÁCTICA CALIFICADA 1 6 Individual
                PC2 PRÁCTICA CALIFICADA 2 14 Individual
                PA PARTICIPACIÓN EN CLASE 17 Individual
                PROY PROYECTO FINAL 18 Grupal
                
                8. CRONOGRAMA DE ACTIVIDADES
                1
                1
                Introducción a la Gestión del Servicio de TI y el Marco ITIL
                2
                ITIL definición y orígenes
                3
                Estrategia Servicio: Conceptos y Procesos
                4
                Estrategia Servicio: Gestión de la demanda
                5
                Diseño del Servicio: Conceptos y Procesos
                6
                Diseño del Servicio: La Gestión del Nivel de Servicio
                11
                Operación del Servicio: La Gestión de Incidentes
                12
                Evaluación PRÁCTICA CALIFICADA 1
                27
                Práctica de procesos ITIL
                28
                Evaluación PRÁCTICA CALIFICADA 2
                35
                Evaluación PROYECTO FINAL
                """;

        Syllabus result = parserEngine.parse(samplePdfText, "100000S74T");

        assertNotNull(result);
        assertEquals("GESTIÓN DEL SERVICIO TI", result.getCourseName());
        assertEquals(18, result.getWeeklySchedule().size());

        // Semana 1: sesiones 1 y 2
        assertTrue(result.getWeeklySchedule().get(0).getTopic().contains("Introducción a la Gestión"));
        assertTrue(result.getWeeklySchedule().get(0).getTopic().contains("ITIL definición y orígenes"));

        // Semana 2: sesiones 3 y 4
        assertTrue(result.getWeeklySchedule().get(1).getTopic().contains("Estrategia Servicio"));

        // Semana 3: sesiones 5 y 6
        assertTrue(result.getWeeklySchedule().get(2).getTopic().contains("Diseño del Servicio"));

        // Semana 6: PC1 (sesión 11 y 12)
        assertTrue(result.getWeeklySchedule().get(5).getTopic().contains("Operación del Servicio"));
        assertEquals("PC1", result.getWeeklySchedule().get(5).getEvaluation());

        // Semana 14: PC2 (sesión 27 y 28)
        assertEquals("PC2", result.getWeeklySchedule().get(13).getEvaluation());

        // Semana 18: PROY (sesión 35)
        assertEquals("PROY", result.getWeeklySchedule().get(17).getEvaluation());
    }

    @Test
    @DisplayName("Debe parsear correctamente evaluaciones multi-línea y cronograma de Formación para la Investigación")
    void shouldParseInvestigacionSyllabusWithMultiLineEvaluationsCorrectly() {
        String samplePdfText = """
                SÍLABO
                FORMACIÓN PARA LA INVESTIGACIÓN - SISTEMAS (100000SI82)
                2026 - Ciclo 2 Agosto
                
                1. INFORMACIÓN GENERAL
                1.2. Créditos: 3
                1.3. Enseñanza de curso: Remoto
                1.4. Horas semanales: 4
                
                4. LOGRO GENERAL DE APRENDIZAJE
                Al finalizar el curso, el estudiante elabora un trabajo de investigación bajo el formato de Revisión Sistemática de Literatura.
                
                7. SISTEMA DE EVALUACIÓN
                El cálculo del promedio final considera la siguiente fórmula:
                (10%)ATI1 + (20%)ATI2 + (20%)ATI3 + (20%)PA + (30%)TI
                
                Donde:
                Tipo Descripción Semana Observación
                ATI1
                AVANCE DE TRABAJO DE
                INVESTIGACIÓN 1
                4
                Evaluación grupal
                ATI2
                AVANCE DE TRABAJO DE
                INVESTIGACIÓN 2
                8
                Evaluación grupal
                ATI3
                AVANCE DE TRABAJO DE
                INVESTIGACIÓN 3
                13
                Evaluación grupal
                PA
                PARTICIPACIÓN EN CLASE
                17
                Evaluación individual
                TI
                TRABAJO DE INVESTIGACIÓN
                18
                Evaluación grupal
                
                8. CRONOGRAMA DE ACTIVIDADES
                Semana 1
                Temario: Lineamientos generales y dinámica del curso
                Semana 4
                Temario: Ficha de investigación e introducción de la RSL
                Semana 8
                Temario: Metodología de revisión sistemática de literatura científica
                Semana 13
                Temario: Presentación de los resultados de la RSL
                Semana 17
                Temario: Sustentación del Trabajo de Investigación
                Semana 18
                Temario: Retroalimentación final del curso
                """;

        Syllabus result = parserEngine.parse(samplePdfText, "100000SI82");

        assertNotNull(result);
        assertEquals("FORMACIÓN PARA LA INVESTIGACIÓN - SISTEMAS", result.getCourseName());
        assertEquals(5, result.getEvaluations().size());

        // ATI1: Semana 4, 10%
        SyllabusEvaluation ati1 = result.getEvaluations().stream().filter(e -> "ATI1".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(ati1);
        assertEquals(4, ati1.getWeek());
        assertEquals(10, ati1.getWeightPercent());
        assertTrue(ati1.getDescription().contains("AVANCE DE TRABAJO DE INVESTIGACIÓN 1"));

        // ATI2: Semana 8, 20%
        SyllabusEvaluation ati2 = result.getEvaluations().stream().filter(e -> "ATI2".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(ati2);
        assertEquals(8, ati2.getWeek());
        assertEquals(20, ati2.getWeightPercent());

        // ATI3: Semana 13, 20%
        SyllabusEvaluation ati3 = result.getEvaluations().stream().filter(e -> "ATI3".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(ati3);
        assertEquals(13, ati3.getWeek());
        assertEquals(20, ati3.getWeightPercent());

        // PA: Semana 17, 20%
        SyllabusEvaluation pa = result.getEvaluations().stream().filter(e -> "PA".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(pa);
        assertEquals(17, pa.getWeek());
        assertEquals(20, pa.getWeightPercent());

        // TI: Semana 18, 30%
        SyllabusEvaluation ti = result.getEvaluations().stream().filter(e -> "TI".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(ti);
        assertEquals(18, ti.getWeek());
        assertEquals(30, ti.getWeightPercent());
        assertEquals("TRABAJO DE INVESTIGACIÓN", ti.getDescription());

        // Cronograma evaluations
        assertEquals("ATI1", result.getWeeklySchedule().get(3).getEvaluation());
        assertEquals("ATI2", result.getWeeklySchedule().get(7).getEvaluation());
        assertEquals("ATI3", result.getWeeklySchedule().get(12).getEvaluation());
        assertEquals("PA", result.getWeeklySchedule().get(16).getEvaluation());
        assertEquals("TI", result.getWeeklySchedule().get(17).getEvaluation());
    }
}

