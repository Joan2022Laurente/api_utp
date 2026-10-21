package com.utp.horario.presentation.exception;

import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.presentation.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejador global de excepciones de la pasarela SyncUTP.
 *
 * <p>Garantía central: <b>ninguna excepción escapa al contenedor de servlets</b>. Toda
 * respuesta de error se serializa en el sobre estándar {@link ApiResponse} respetando la
 * estructura documentada en {@code docs/ENDPOINTS.md} §1.1, incluidos los errores
 * generados por el propio framework de Spring (cuerpo JSON malformado, parámetro
 * faltante, tipo no coincidente, método no soportado o ruta inexistente), que de otro
 * modo responderían con el cuerpo de error por defecto de Spring Boot
 * ({@code {"timestamp":..., "status":..., "error":...}}) y violarían el contrato.</p>
 *
 * <p>Mapa de códigos de estado:</p>
 * <ul>
 *   <li>{@code 400 Bad Request} — validación de payload, parámetros o formato.</li>
 *   <li>{@code 401 Unauthorized} — credenciales inválidas, token ausente, expirado o manipulado.</li>
 *   <li>{@code 403 Forbidden} — acceso a un recurso ajeno con identidad válida.</li>
 *   <li>{@code 404 Not Found} — recurso inexistente.</li>
 *   <li>{@code 405 Method Not Allowed} / {@code 415 Unsupported Media Type}.</li>
 *   <li>{@code 502 Bad Gateway} — dependencia externa (SSO UTP, Portal, PAO, S3, OpenRouter) no disponible.</li>
 *   <li>{@code 500 Internal Server Error} — fallo imprevisto (nunca se filtra la traza al cliente).</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // 400 Bad Request - validación de entrada
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Parámetros de solicitud inválidos");
        return badRequest(errorMessage);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Parámetros de solicitud inválidos");
        return badRequest(errorMessage);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("[GlobalExceptionHandler] Cuerpo JSON ilegible: {}", e.getMessage());
        return badRequest("Cuerpo de la solicitud ilegible o ausente. Se espera un JSON válido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return badRequest("Falta el parámetro obligatorio '" + e.getParameterName() + "'.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "el tipo esperado";
        return badRequest("El valor '" + e.getValue() + "' no es válido para el parámetro '"
                + e.getName() + "'. Se esperaba " + expected + ".");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        String message = e.getMessage() != null ? e.getMessage() : "Parámetros de solicitud inválidos";
        return badRequest(message);
    }

    // ------------------------------------------------------------------
    // 401 Unauthorized / 403 Forbidden
    // ------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        log.warn("[GlobalExceptionHandler] Fallo de autenticación: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException e) {
        String message = e.getMessage() != null ? e.getMessage() : "Acceso no autorizado";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(message));
    }

    // ------------------------------------------------------------------
    // 404 Not Found
    // ------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        String message = e.getMessage() != null ? e.getMessage() : "El recurso solicitado no existe";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(message));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("El endpoint solicitado no existe: " + e.getRequestURL()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("El recurso solicitado no existe: " + e.getResourcePath()));
    }

    // ------------------------------------------------------------------
    // 405 / 415
    // ------------------------------------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail("Método HTTP no soportado: " + e.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail("Tipo de contenido no soportado: " + e.getContentType()));
    }

    // ------------------------------------------------------------------
    // 502 Bad Gateway - dependencias externas
    // ------------------------------------------------------------------

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(UpstreamServiceException e) {
        log.error("[GlobalExceptionHandler] Dependencia externa no disponible: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(e.getMessage()));
    }

    // ------------------------------------------------------------------
    // 500 Internal Server Error - último recurso
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[GlobalExceptionHandler] Error interno no controlado: {} - {}",
                e.getClass().getSimpleName(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(
                        "Error interno del servidor",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(error));
    }
}
