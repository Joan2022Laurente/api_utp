package com.utp.horario.infrastructure.external;

import com.utp.horario.support.FakeHttpClient;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolución de identidad PAO 100% determinista (Checkpoint §19).
 *
 * <p>Tras la Fase 2 no hay mapas en memoria, sondas HTTP ni caché en disco: el paoUserId
 * se deriva por UUIDv5 en 0 ms. Estos tests sustituyen a los antiguos tests de cascada
 * conservando su número y sus garantías de aislamiento multi-alumno.</p>
 */
class PaoUserIdResolutionTest {

    private UtpPortalGatewayAdapter adapter(FakeHttpClient client) {
        return new UtpPortalGatewayAdapter(client);
    }

    @Test
    @DisplayName("Un claim paoUserId del token ya no se usa: manda la derivación UUIDv5")
    void paoUserIdClaimIsExtractedAsCandidate() {
        String token = TestTokens.build("U99999999", 4102444800L,
                Map.of("paoUserId", "11111111-2222-3333-4444-555555555555"));
        String resolved = adapter(FakeHttpClient.always(200, "{}")).extractUserIdFromToken(token);

        assertEquals(PaoUserIdDeriver.derive("U99999999@UTP.EDU.PE"), resolved);
        assertNotEquals("11111111-2222-3333-4444-555555555555", resolved);
    }

    @Test
    @DisplayName("Un token sin claim de PAO resuelve igual por derivación determinista")
    void tokenWithoutPaoClaimHasNoCandidates() {
        String token = TestTokens.validFor("U99999999");
        String resolved = adapter(FakeHttpClient.always(200, "{}")).extractUserIdFromToken(token);

        assertEquals(36, resolved.length());
        assertTrue(resolved.matches("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"));
    }

    @Test
    @DisplayName("No existen cachés de identidad indexadas por alumno")
    void registryIsIndexedByLowercaseStudentCode() {
        for (Field field : UtpPortalGatewayAdapter.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("paousermap"), "No debe existir caché " + field.getName());
            assertFalse(name.contains("paoidentity"), "No debe existir caché " + field.getName());
            assertFalse(name.contains("discoveryattempt"), "No debe existir caché " + field.getName());
            assertFalse(name.contains("invalidatedpao"), "No debe existir caché " + field.getName());
        }
    }

    @Test
    @DisplayName("La derivación es estable entre peticiones y no necesita registro previo")
    void observedSuccessfulCallRegistersTheStudentId() {
        UtpPortalGatewayAdapter adapter = adapter(FakeHttpClient.always(200, "{}"));
        String token = TestTokens.validFor("U21200000");

        String first = adapter.extractUserIdFromToken(token);
        String second = adapter.extractUserIdFromToken(token);

        assertEquals(first, second);
        assertEquals(PaoUserIdDeriver.derive("U21200000@UTP.EDU.PE"), first);
    }

    @Test
    @DisplayName("Checkpoint §19: un claim email con alias NO altera la semilla UPPER(codigo)@UTP.EDU.PE")
    void emailAliasDoesNotChangeDerivation() {
        String token = TestTokens.build("U23307609", 4102444800L,
                Map.of("email", "alias.personal@gmail.com"));
        String resolved = adapter(FakeHttpClient.always(200, "{}")).extractUserIdFromToken(token);

        assertEquals("4e535263-79a0-5890-ae33-72a7aa0629ab", resolved);
    }

    @Test
    @DisplayName("Cada alumno resuelve su propia identidad: nunca se presta la de otro")
    void studentsNeverShareTheirPaoIdentity() {
        UtpPortalGatewayAdapter adapter = adapter(FakeHttpClient.always(200, "{}"));
        String idA = adapter.extractUserIdFromToken(TestTokens.validFor("U99999999"));
        String idB = adapter.extractUserIdFromToken(TestTokens.validFor("U21200000"));

        assertEquals(PaoUserIdDeriver.derive("U99999999@UTP.EDU.PE"), idA);
        assertEquals(PaoUserIdDeriver.derive("U21200000@UTP.EDU.PE"), idB);
        assertNotEquals(idA, idB);
    }

    @Test
    @DisplayName("Sin sondas HTTP: la resolución no consume peticiones de red")
    void discoveryAttemptsAreThrottledPerStudent() {
        FakeHttpClient client = FakeHttpClient.always(200, "{}");
        UtpPortalGatewayAdapter adapter = adapter(client);

        assertDoesNotThrow(() -> adapter.extractUserIdFromToken(TestTokens.validFor("U99999999")));
        assertTrue(client.requests().isEmpty(),
                "La derivación UUIDv5 no debe ejecutar sondas HTTP (peticiones: " + client.requests().size() + ")");
    }
}
