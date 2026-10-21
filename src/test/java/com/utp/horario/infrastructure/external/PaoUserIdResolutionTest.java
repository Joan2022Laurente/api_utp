package com.utp.horario.infrastructure.external;

import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión de la resolución en cascada del {@code paoUserId}.
 *
 * <p>Cubren laInvestigación de arquitectura (H-06 ampliado): el {@code paoUserId} es una
 * clave interna de PAO y debe resolverse sin intervention manual cuando sea derivable,
 * manteniendo aislamiento estricto por alumno.</p>
 */
class PaoUserIdResolutionTest {

    private final UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter();

    @SuppressWarnings("unchecked")
    private Map<String, String> registry() throws Exception {
        Field field = UtpPortalGatewayAdapter.class.getDeclaredField("studentPaoUserMap");
        field.setAccessible(true);
        return (Map<String, String>) field.get(adapter);
    }

    private Method method(String name, Class<?>... params) throws Exception {
        Method m = UtpPortalGatewayAdapter.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    @DisplayName("Un claim paoUserId del token se resuelve como candidato (via automatica)")
    void paoUserIdClaimIsExtractedAsCandidate() throws Exception {
        String token = TestTokens.build("U23307609", 4102444800L,
                java.util.Map.of("paoUserId", "11111111-2222-3333-4444-555555555555"));

        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) method("extractPaoUserIdCandidatesFromToken", String.class)
                .invoke(adapter, token);

        assertFalse(candidates.isEmpty(), "El claim paoUserId debe extraerse como candidato");
        assertEquals("11111111-2222-3333-4444-555555555555", candidates.get(0));
    }

    @Test
    @DisplayName("Un token sin claim de PAO no produce candidatos automaticos")
    void tokenWithoutPaoClaimHasNoCandidates() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) method("extractPaoUserIdCandidatesFromToken", String.class)
                .invoke(adapter, TestTokens.validFor("U23307609"));

        assertTrue(candidates.isEmpty(), "Sin claim paoUserId no debe inventarse un candidato");
    }

    @Test
    @DisplayName("El registro PAO se indexa por codigo de alumno en minusculas (aislamiento multi-tenant)")
    void registryIsIndexedByLowercaseStudentCode() throws Exception {
        method("registerPaoUserId", String.class, String.class)
                .invoke(adapter, "U23307609", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        Map<String, String> map = registry();
        assertTrue(map.containsKey("u23307609"), "La clave debe normalizarse a minusculas");
        assertFalse(map.containsKey("current-student"), "No debe existir ninguna clave compartida");
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("El auto-registro por observacion cachea el id del alumno que funciono")
    void observedSuccessfulCallRegistersTheStudentId() throws Exception {
        String token = TestTokens.validFor("U21200000");

        method("rememberResolvedPaoUserId", String.class, String.class)
                .invoke(adapter, token, "99999999-8888-7777-6666-555555555555");

        Map<String, String> map = registry();
        assertEquals("99999999-8888-7777-6666-555555555555", map.get("u21200000"));
        assertFalse(map.containsKey("u23307609"), "No debe contaminar el registro de otro alumno");
    }

    @Test
    @DisplayName("Cada alumno resuelve su propia identidad: nunca se presta la de otro")
    void studentsNeverShareTheirPaoIdentity() throws Exception {
        method("registerPaoUserId", String.class, String.class)
                .invoke(adapter, "U23307609", "id-de-alumno-a");
        method("registerPaoUserId", String.class, String.class)
                .invoke(adapter, "U21200000", "id-de-alumno-b");

        Map<String, String> map = registry();
        assertEquals("id-de-alumno-a", map.get("u23307609"));
        assertEquals("id-de-alumno-b", map.get("u21200000"));
        assertEquals(2, map.size());
    }

    @Test
    @DisplayName("El intento de auto-descubrimiento se cachea por alumno (TTL) para no repetir sondas")
    void discoveryAttemptsAreThrottledPerStudent() throws Exception {
        String token = TestTokens.validFor("U23307609");

        Method discover = method("discoverPaoUserId", String.class, String.class);
        Field attemptsField = UtpPortalGatewayAdapter.class.getDeclaredField("paoDiscoveryAttempts");
        attemptsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> attempts = (Map<String, Long>) attemptsField.get(adapter);

        // El descubrimiento se ejecuta (con sondas reales) pero deja constancia del intento.
        discover.invoke(adapter, "u23307609", token);

        assertNotNull(attempts.get("u23307609"), "Debe registrarse el intento de auto-descubrimiento");
    }
}
