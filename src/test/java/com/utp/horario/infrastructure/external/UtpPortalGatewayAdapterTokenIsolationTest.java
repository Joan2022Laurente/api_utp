package com.utp.horario.infrastructure.external;

import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión del aislamiento de identidad en {@link UtpPortalGatewayAdapter}.
 *
 * <p>Reproducen el hallazgo crítico C-01: el campo global {@code lastActiveToken} permitía
 * que una petición sin token tomara prestado el token del último estudiante autenticado
 * en la instancia.</p>
 */
class UtpPortalGatewayAdapterTokenIsolationTest {

    private final UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter();

    @Test
    @DisplayName("No existe ningún token global compartido entre estudiantes")
    void noGlobalSharedTokenFieldExists() throws Exception {
        for (Field field : UtpPortalGatewayAdapter.class.getDeclaredFields()) {
            assertTrue(!field.getName().toLowerCase().contains("lastactivetoken"),
                    "El campo global 'lastActiveToken' fue reintroducido y reintroduciría la fuga de identidad");
        }
    }

    @Test
    @DisplayName("El estado de sesión es un ConcurrentHashMap indexado por estudiante")
    void sessionStateIsIsolatedPerStudent() throws Exception {
        Field field = UtpPortalGatewayAdapter.class.getDeclaredField("studentSessionRegistry");
        field.setAccessible(true);

        Object registry = field.get(adapter);

        assertNotNull(registry);
        assertTrue(registry instanceof ConcurrentHashMap,
                "El registro de sesiones debe ser un ConcurrentHashMap (aislamiento por clave de estudiante)");
    }

    @Test
    @DisplayName("Una petición sin token NO toma prestado el token de otro estudiante")
    void anonymousRequestNeverBorrowsAnotherStudentToken() throws Exception {
        // Se simula que el alumno U23307609 tiene una sesión activa registrada.
        Method register = UtpPortalGatewayAdapter.class.getDeclaredMethod(
                "registerStudentSession", String.class, String.class);
        register.setAccessible(true);
        register.invoke(adapter, "U23307609", TestTokens.validFor("U23307609"));

        Field field = UtpPortalGatewayAdapter.class.getDeclaredField("studentSessionRegistry");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> registry = (Map<String, String>) field.get(adapter);
        assertEquals(1, registry.size(), "La sesión del alumno debe registrarse bajo su propio código");

        // Petición anónima: NO debe resolverse el PDF con el token del alumno registrado.
        String text = adapter.fetchSyllabusPdfText(null, "100000ST61");

        assertEquals("", text,
                "Una petición sin token no debe usar el token de ningún otro estudiante");
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
        String text = adapter.fetchSyllabusPdfText(null, "45104");
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("", text);
        assertTrue(elapsed < 2000,
                "Sin token propio no debe ejecutarse ninguna llamada autenticada al portal PAO (tardó " + elapsed + " ms)");
    }
}
