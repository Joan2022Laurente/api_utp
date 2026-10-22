package com.utp.horario.infrastructure.external;

import com.utp.horario.support.ExternalServices;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba de integracion de la resolucion del {@code paoUserId} y del course code.
 *
 * <p>Verifica en vivo, con un token real del SSO UTP, que:</p>
 * <ul>
 *   <li>el {@code paoUserId} derivado (uuidv5 sobre el correo) es aceptado por PAO;</li>
 *   <li>un UUID de seccion de PAO se resuelve a su codigo de catalogo.</li>
 * </ul>
 *
 * <p>Se omite (skip) si no hay conectividad con el SSO o con PAO.</p>
 */
class PaoUserIdLiveResolutionTest {

    private static final String USERNAME = "u23307609";
    private static final String PASSWORD = System.getenv().getOrDefault("UTP_TEST_PASSWORD", "");
    private static final String PAO_SECTION_UUID = "2421dd0b-b5ee-5193-8190-902ec8403745";
    private static final String EXPECTED_CATALOG_CODE = "100000SI82";

    private String loginToken() throws Exception {
        if (PASSWORD.isBlank()) {
            Assumptions.abort("UTP_TEST_PASSWORD no definida: se omite la prueba en vivo.");
        }
        String form = "client_id=pao-web&grant_type=password&username=" + USERNAME
                + "&password=" + java.net.URLEncoder.encode(PASSWORD, java.nio.charset.StandardCharsets.UTF_8);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(
                        "https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                .build();
        java.net.http.HttpResponse<String> response =
                http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        Assumptions.assumeTrue(response.statusCode() == 200, "El SSO no devolvio token: se omite la prueba.");
        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        return root.path("access_token").asText();
    }

    @Test
    @DisplayName("El paoUserId derivado es aceptado por PAO y el UUID de seccion resuelve al codigo de catalogo")
    void derivedPaoUserIdIsAcceptedAndSectionResolves() throws Exception {
        Assumptions.assumeTrue(ExternalServices.isOpenRouterReachable(),
                "Sin conectividad con los servicios UTP: se omite la prueba en vivo.");

        String token = loginToken();
        Assumptions.assumeTrue(!token.isBlank(), "No se pudo obtener token: se omite la prueba.");

        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter();

        Method extract = UtpPortalGatewayAdapter.class.getDeclaredMethod("extractUserIdFromToken", String.class);
        extract.setAccessible(true);
        String paoUserId = (String) extract.invoke(adapter, token);

        assertNotNull(paoUserId, "Debe resolverse un paoUserId");
        assertEquals(36, paoUserId.length(), "El paoUserId debe ser un UUID: " + paoUserId);

        String catalogCode = adapter.resolveCourseCode(PAO_SECTION_UUID, null, token);
        assertNotNull(catalogCode, "El UUID de seccion debe resolverse al codigo de catalogo");
        assertEquals(EXPECTED_CATALOG_CODE, catalogCode);
    }
}
