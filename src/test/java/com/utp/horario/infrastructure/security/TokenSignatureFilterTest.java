package com.utp.horario.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.support.TestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del filtro de verificacion de firma en el limite HTTP.
 *
 * <p>El objetivo es doble: <b>bloquear</b> los tokens falsificados y, al mismo tiempo,
 * <b>no alterar el contrato</b> para las peticiones legitimas. En particular, una peticion
 * sin token debe seguir recibiendo exactamente la misma respuesta que antes, porque las
 * apps cliente dependen de ese comportamiento.</p>
 */
class TokenSignatureFilterTest {

    private TestKeys keys;
    private JwtVerificationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        keys = new TestKeys();
        properties = new JwtVerificationProperties();
        properties.setIssuer(TestKeys.ISSUER);
    }

    private MockMvc mockMvc(JwtVerificationProperties props, JwksKeyProvider keyProvider) {
        JwtSignatureVerifier verifier = new JwtSignatureVerifier(props, keyProvider);
        TokenSignatureFilter filter = new TokenSignatureFilter(props, verifier, objectMapper);
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(filter)
                .build();
    }

    @Test
    @DisplayName("Con el modo 'off' el comportamiento es identico al anterior")
    void offModeDoesNotChangeAnything() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.OFF);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Con el modo 'audit' el token falso se registra pero NO se rechaza")
    void auditModeVerifiesButDoesNotReject() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.AUDIT);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + keys.unsignedToken("U99999999")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Con el modo 'enforce' un token falsificado se responde con 401 y el sobre ApiResponse")
    void enforceModeRejectsForgedToken() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + keys.unsignedToken("U99999999")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Con el modo 'enforce' un token legitimo se acepta")
    void enforceModeAcceptsGenuineToken() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + keys.signedToken("U99999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @DisplayName("Una peticion SIN token no cambia de comportamiento (el contrato se preserva)")
    void requestWithoutTokenIsUnaffected() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @DisplayName("Si el JWKS no esta disponible y hay fail-open, la peticion no se rechaza")
    void jwksDownWithFailOpenDoesNotReject() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        properties.setFailOpen(true);
        MockMvc mockMvc = mockMvc(properties, keys.unavailableProvider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + keys.signedToken("U99999999")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Si el JWKS no esta disponible y no hay fail-open, la peticion se rechaza")
    void jwksDownWithoutFailOpenRejects() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        properties.setFailOpen(false);
        MockMvc mockMvc = mockMvc(properties, keys.unavailableProvider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + keys.signedToken("U99999999")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Las rutas exentas (login y refresh) no se verifican")
    void skippedPathsAreNotVerified() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/auth/login").header("Authorization", "Bearer token-cualquiera"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El contenido de la respuesta de error sigue el media type JSON del contrato")
    void errorResponseUsesJsonContentType() throws Exception {
        properties.setMode(JwtVerificationProperties.Mode.ENFORCE);
        MockMvc mockMvc = mockMvc(properties, keys.provider(properties));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + keys.unsignedToken("U99999999")))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /**
     * Controlador de prueba: una ruta cualquiera que devuelve 200.
     */
    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        public Map<String, Object> probe() {
            return Map.of("ok", true);
        }
    }
}
