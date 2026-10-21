package com.utp.horario.infrastructure.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresion de la derivacion determinista del {@code paoUserId}.
 *
 * <p>La regla (namespace fijo de la plataforma + correo del alumno en mayusculas,
 * UUID v5 / SHA-1) se replico del bundle oficial de la SPA UTP+Class. El vector de
 * referencia usado aqui es <b>sintetico</b>: bloquea la regla exacta sin incrustar
 * identificadores reales de ningun alumno en el codigo versionado.</p>
 */
class PaoUserIdDeriverTest {

    private static final String SYNTHETIC_EMAIL = "alumno.prueba@utp.edu.pe";
    private static final String SYNTHETIC_PAO_USER_ID = "b75a54e9-2baf-5129-852d-32f9c698bf29";

    @Test
    @DisplayName("Vector de referencia: el correo produce el paoUserId esperado")
    void knownVectorProducesExpectedPaoUserId() {
        assertEquals(SYNTHETIC_PAO_USER_ID, PaoUserIdDeriver.derive(SYNTHETIC_EMAIL.toUpperCase()));
        assertEquals(SYNTHETIC_PAO_USER_ID, PaoUserIdDeriver.derive(SYNTHETIC_EMAIL));
    }

    @Test
    @DisplayName("La derivacion es insensible a mayusculas (se normaliza a mayusculas)")
    void derivationIsCaseInsensitive() {
        assertEquals(PaoUserIdDeriver.derive(SYNTHETIC_EMAIL.toLowerCase()),
                PaoUserIdDeriver.derive(SYNTHETIC_EMAIL.toUpperCase()));
    }

    @Test
    @DisplayName("Alumnos distintos producen identificadores distintos")
    void differentStudentsProduceDifferentIds() {
        assertNotEquals(PaoUserIdDeriver.derive("otro.alumno@utp.edu.pe"),
                PaoUserIdDeriver.derive(SYNTHETIC_EMAIL));
    }

    @Test
    @DisplayName("El resultado es un UUID v5 (SHA-1) con variante RFC 4122")
    void resultIsAValidV5Uuid() {
        String id = PaoUserIdDeriver.derive(SYNTHETIC_EMAIL);

        assertTrue(id.matches("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"),
                "El identificador debe ser un UUID v5 con variante RFC 4122, pero fue: " + id);
    }

    @Test
    @DisplayName("Un correo ausente o vacio no produce identificador")
    void missingEmailYieldsEmptyId() {
        assertEquals("", PaoUserIdDeriver.derive(null));
        assertEquals("", PaoUserIdDeriver.derive(""));
        assertEquals("", PaoUserIdDeriver.derive("   "));
    }

    @Test
    @DisplayName("El namespace por defecto es el de la plataforma y es configurable por entorno")
    void namespaceIsConfigurable() {
        assertEquals("6ba7b810-9dad-11d1-80b4-00c04fd430c8", PaoUserIdDeriver.DEFAULT_NAMESPACE);
        assertTrue(PaoUserIdDeriver.namespace().matches("^[0-9a-fA-F-]{36}$"),
                "El namespace efectivo debe ser un UUID valido: " + PaoUserIdDeriver.namespace());
    }
}
