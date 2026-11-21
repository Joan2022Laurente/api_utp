package com.utp.horario.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas canónicas de la política de sesiones asíncronas (Checkpoint §18.1). */
class AsynchronousSessionPolicyTest {

    @Test
    @DisplayName("VT es asíncrono independientemente del classType")
    void virtualModeIsAsync() {
        assertTrue(AsynchronousSessionPolicy.isAsynchronous("VT", "1"));
        assertTrue(AsynchronousSessionPolicy.isAsynchronous("VT", null));
        assertTrue(AsynchronousSessionPolicy.isAsynchronous("vt", "5"));
    }

    @Test
    @DisplayName("classType 3 y 4 son asíncronos (String, con y sin espacios)")
    void asyncClassTypesAreAsync() {
        assertTrue(AsynchronousSessionPolicy.isAsynchronous("P", "3"));
        assertTrue(AsynchronousSessionPolicy.isAsynchronous("R", "4"));
        assertTrue(AsynchronousSessionPolicy.isAsynchronous(null, " 3 "));
    }

    @Test
    @DisplayName("SP con classType 1/2 es síncrono: la subcondición es absorbida")
    void semiPresentialSyncTypesAreNotAsync() {
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("SP", "1"));
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("SP", "2"));
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("SP", null));
    }

    @Test
    @DisplayName("Modalidades presencial/remoto y tipos 1,2,5,6 no son asíncronos")
    void synchronousCombinationsAreNotAsync() {
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("P", "1"));
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("R", "6"));
        assertFalse(AsynchronousSessionPolicy.isAsynchronous(null, "5"));
    }

    @Test
    @DisplayName("Nulos y valores desconocidos no se inventan como asíncronos")
    void missingInputsAreNotAsync() {
        assertFalse(AsynchronousSessionPolicy.isAsynchronous(null, null));
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("", ""));
        assertFalse(AsynchronousSessionPolicy.isAsynchronous("UNKNOWN", "X"));
    }
}
