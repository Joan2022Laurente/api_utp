package com.utp.horario.presentation.exception;

import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.exception.UpstreamServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de regresión del mapeo de códigos de estado y del formato del sobre de error.
 *
 * <p>Verifican que ninguna excepción escape del controlador sin envolver en
 * {@code ApiResponse} y que los códigos de estado sean coherentes con el contrato
 * (400 validación, 401 credenciales, 403 acceso ajeno, 404 inexistente, 502 dependencia
 * externa, 500 error imprevisto).</p>
 */
class GlobalExceptionHandlerStatusTest {

    private MockMvc mockMvc;

    @RestController
    static class FailingController {

        @GetMapping("/test/validation")
        public void validation() {
            throw new IllegalArgumentException("Parámetro inválido");
        }

        @GetMapping("/test/authentication")
        public void authentication() {
            throw new AuthenticationException("Token de autenticación UTP inválido o expirado.");
        }

        @GetMapping("/test/security")
        public void security() {
            throw new SecurityException("Acceso no autorizado");
        }

        @GetMapping("/test/notfound")
        public void notFound() {
            throw new ResourceNotFoundException("Estudiante no encontrado: X");
        }

        @GetMapping("/test/state-notfound")
        public void stateNotFound() {
            throw new IllegalStateException("No se encontraron cursos matriculados en el periodo 2263");
        }

        @GetMapping("/test/upstream")
        public void upstream() {
            throw new UpstreamServiceException("SSO UTP no disponible");
        }

        @GetMapping("/test/boom")
        public void boom() {
            throw new RuntimeException("Fallo no clasificado");
        }

        @GetMapping("/test/requires-param")
        public void requiresParam(@RequestParam String obligatorio) {
            // nunca se ejecuta: dispara MissingServletRequestParameterException
        }

        @PostMapping("/test/body")
        public void body(@RequestBody Payload payload) {
            // nunca se ejecuta: dispara HttpMessageNotReadableException
        }
    }

    /** DTO mínimo para forzar el parseo JSON real del cuerpo de la petición. */
    public static class Payload {
        public String value;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Error de validación -> 400 con sobre ApiResponse")
    void validationErrorReturns400() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Parámetro inválido"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Credenciales inválidas -> 401 con sobre ApiResponse")
    void authenticationErrorReturns401() throws Exception {
        mockMvc.perform(get("/test/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Token de autenticación UTP inválido o expirado."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Acceso a recurso ajeno -> 403")
    void securityErrorReturns403() throws Exception {
        mockMvc.perform(get("/test/security"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Recurso inexistente -> 404")
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/test/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Estudiante no encontrado: X"));
    }

    @Test
    @DisplayName("Estado de recurso inexistente -> 404")
    void illegalStateMapsTo404() throws Exception {
        mockMvc.perform(get("/test/state-notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Dependencia externa no disponible -> 502")
    void upstreamErrorReturns502() throws Exception {
        mockMvc.perform(get("/test/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Error no clasificado -> 500 sin filtrar traza al cliente")
    void unexpectedErrorReturns500() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"));
    }

    @Test
    @DisplayName("Parámetro obligatorio ausente -> 400 con sobre ApiResponse")
    void missingParameterReturns400() throws Exception {
        mockMvc.perform(get("/test/requires-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Falta el parámetro obligatorio 'obligatorio'."));
    }

    @Test
    @DisplayName("Cuerpo JSON malformado -> 400 con sobre ApiResponse")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
