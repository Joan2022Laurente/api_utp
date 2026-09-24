package com.utp.horario.application.service.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyllabusFormulaParserTest {

    @Test
    @DisplayName("Debe parsear una fórmula estándar de 4 evaluaciones y validar que sume 100%")
    void shouldParseStandardFourTermsFormula() {
        String input = """
                7. SISTEMA DE EVALUACIÓN
                El cálculo del promedio final considera la siguiente fórmula:
                (20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY
                """;

        SyllabusFormulaParser.FormulaParseResult result = SyllabusFormulaParser.parse(input);

        assertNotNull(result);
        assertEquals("(20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY", result.formulaString());
        assertEquals(4, result.terms().size());
        assertEquals(100, result.totalPercentage());
        assertTrue(result.isValid100Percent());
        assertEquals(20, result.weightMap().get("APF1"));
        assertEquals(40, result.weightMap().get("PROY"));
    }

    @Test
    @DisplayName("Debe parsear fórmula de 5 evaluaciones de Formación para la Investigación")
    void shouldParseFiveTermsInvestigacionFormula() {
        String input = "(10%)ATI1 + (20%)ATI2 + (20%)ATI3 + (20%)PA + (30%)TI";

        SyllabusFormulaParser.FormulaParseResult result = SyllabusFormulaParser.parse(input);

        assertNotNull(result);
        assertEquals(5, result.terms().size());
        assertEquals(100, result.totalPercentage());
        assertTrue(result.isValid100Percent());
        assertEquals(10, result.weightMap().get("ATI1"));
        assertEquals(20, result.weightMap().get("ATI2"));
        assertEquals(20, result.weightMap().get("ATI3"));
        assertEquals(20, result.weightMap().get("PA"));
        assertEquals(30, result.weightMap().get("TI"));
    }

    @Test
    @DisplayName("Debe detectar advertencia si la suma de ponderaciones no es 100%")
    void shouldFlagNon100PercentFormula() {
        String input = "(20%)PC1 + (20%)PC2 + (50%)PROY"; // Suma 90%

        SyllabusFormulaParser.FormulaParseResult result = SyllabusFormulaParser.parse(input);

        assertNotNull(result);
        assertEquals(3, result.terms().size());
        assertEquals(90, result.totalPercentage());
        assertFalse(result.isValid100Percent());
    }

    @Test
    @DisplayName("Debe manejar entrada vacía de forma segura sin excepciones")
    void shouldHandleEmptyGracefully() {
        SyllabusFormulaParser.FormulaParseResult result = SyllabusFormulaParser.parse("");
        assertNotNull(result);
        assertTrue(result.terms().isEmpty());
        assertEquals(0, result.totalPercentage());
        assertFalse(result.isValid100Percent());
    }
}
