package com.utp.horario.application.service.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del parser de la formula oficial del Portal UTP.
 *
 * <p>El Portal expone el sistema de evaluaciones en formato estructurado
 * ({@code 20%*[APF1] + 40%*[PROY]}), distinto del impreso en el PDF
 * ({@code (20%)APF1 + (40%)PROY}).</p>
 */
class OfficialFormulaParserTest {

    @Test
    @DisplayName("Extrae los pesos del formato oficial del Portal")
    void parsesOfficialPortalFormat() {
        Map<String, Integer> weights = SyllabusFormulaParser.parseOfficialWeights(
                "10%*[ATI1] + 20%*[ATI2] + 20%*[ATI3] + 20%*[PA] + 30%*[TI]");

        assertEquals(5, weights.size());
        assertEquals(10, weights.get("ATI1"));
        assertEquals(20, weights.get("ATI2"));
        assertEquals(20, weights.get("ATI3"));
        assertEquals(20, weights.get("PA"));
        assertEquals(30, weights.get("TI"));
        assertEquals(100, weights.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    @DisplayName("Admite el formato sin corchetes y con espacios variables")
    void parsesVariants() {
        Map<String, Integer> weights = SyllabusFormulaParser.parseOfficialWeights(
                " 20% * [APF1] +  40%*[PROY] ");

        assertEquals(2, weights.size());
        assertEquals(20, weights.get("APF1"));
        assertEquals(40, weights.get("PROY"));
    }

    @Test
    @DisplayName("Una formula vacia o invalida no produce pesos")
    void handlesEmptyOrInvalidFormula() {
        assertTrue(SyllabusFormulaParser.parseOfficialWeights(null).isEmpty());
        assertTrue(SyllabusFormulaParser.parseOfficialWeights("").isEmpty());
        assertTrue(SyllabusFormulaParser.parseOfficialWeights("sin porcentajes").isEmpty());
    }
}
