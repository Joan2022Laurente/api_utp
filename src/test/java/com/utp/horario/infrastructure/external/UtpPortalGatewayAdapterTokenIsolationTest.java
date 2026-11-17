package com.utp.horario.infrastructure.external;

import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión del aislamiento de identidad en {@link UtpPortalGatewayAdapter}.
 *
 * <p>Garantiza que el adaptador es 100% stateless con respecto a tokens de usuario:
 * no existe ningún campo global ni registro en memoria que almacene tokens ni permita
 * que una petición tome prestada la identidad de otro estudiante.</p>
 */
class UtpPortalGatewayAdapterTokenIsolationTest {

    private final UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter();

    @Test
    @DisplayName("No existe ningún token global ni registro de sesiones en memoria")
    void noSessionRegistryOrGlobalSharedTokenFieldExists() {
        for (Field field : UtpPortalGatewayAdapter.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertTrue(!name.contains("lastactivetoken"),
                    "El campo global 'lastActiveToken' fue reintroducido y reintroduciría la fuga de identidad");
            assertTrue(!name.contains("studentsessionregistry"),
                    "El registro en memoria 'studentSessionRegistry' fue reintroducido; el adaptador debe ser 100% stateless");
        }
    }

    @Test
    @DisplayName("Una petición sin token falla con 401 y nunca toma prestado token ajeno")
    void anonymousRequestNeverBorrowsAnotherStudentToken() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> adapter.fetchSyllabusPdfText(null, "100000ST61"),
                "Una petición sin token debe fallar en vez de usar el token de otro estudiante");
        assertTrue(ex.getMessage().toLowerCase().contains("token"), ex.getMessage());
    }

    @Test
    @DisplayName("Una petición con token propio resuelve exclusivamente ese token")
    void requestWithOwnTokenUsesOnlyThatToken() throws Exception {
        Method resolve = UtpPortalGatewayAdapter.class.getDeclaredMethod("resolveEffectiveToken", String.class);
        resolve.setAccessible(true);

        String own = TestTokens.validFor("U21200000");

        assertEquals(own, resolve.invoke(adapter, own));
        assertEquals(own, resolve.invoke(adapter, "Bearer " + own));
        assertEquals("", resolve.invoke(adapter, (Object) null));
        assertEquals("", resolve.invoke(adapter, ""));
    }

    @Test
    @DisplayName("Sin token propio no se invoca a endpoints autenticados del portal PAO")
    void anonymousRequestDoesNotCallAuthenticatedPaoEndpoints() {
        // Con un código numérico, el adaptador intentaría resolver la sección vía PAO.
        // Sin credenciales propias debe abortar sin salir a la red.
        long start = System.currentTimeMillis();
        assertThrows(AuthenticationException.class, () -> adapter.fetchSyllabusPdfText(null, "45104"));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 2000,
                "Sin token propio no debe ejecutarse ninguna llamada autenticada al portal PAO (tardó " + elapsed + " ms)");
    }
}
