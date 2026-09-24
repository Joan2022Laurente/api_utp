package com.utp.horario.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Proxy transparente de alta fidelidad para DuckDuckGo AI (duck.ai).
 * Reenvía todas las cabeceras del navegador intactas (User-Agent, retos DOM, señales de frontend)
 * para evitar bloqueos por discrepancia de huella digital (HTTP 418).
 */
@Slf4j
@RestController
@RequestMapping("/duckchat")
public class DuckChatProxyController {

    private static final String DUCK_AI_BASE = "https://duck.ai/duckchat";

    private final HttpClient httpClient;

    public DuckChatProxyController() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @GetMapping("/v1/auth/token")
    public ResponseEntity<String> getAuthToken(HttpServletRequest request) {
        return forwardGetRequest("/v1/auth/token", request);
    }

    @GetMapping("/v1/capabilities")
    public ResponseEntity<String> getCapabilities(HttpServletRequest request) {
        return forwardGetRequest("/v1/capabilities", request);
    }

    @GetMapping("/v1/status")
    public ResponseEntity<String> getStatus(HttpServletRequest request) {
        return forwardGetRequest("/v1/status", request);
    }

    private ResponseEntity<String> forwardGetRequest(String path, HttpServletRequest request) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(DUCK_AI_BASE + path))
                    .GET()
                    .header("accept", "*/*")
                    .header("accept-language", "en-US,en;q=0.9,es;q=0.8")
                    .header("cache-control", "no-store")
                    .header("referer", "https://duck.ai/")
                    .timeout(Duration.ofSeconds(15));

            // Reenviar cabeceras relevantes del cliente
            copyHeaderIfPresent(request, reqBuilder, "user-agent");
            copyHeaderIfPresent(request, reqBuilder, "x-vqd-accept");
            copyHeaderIfPresent(request, reqBuilder, "x-ddg-journey-id");
            copyHeaderIfPresent(request, reqBuilder, "x-fe-version");
            copyHeaderIfPresent(request, reqBuilder, "x-fe-signals");

            if (request.getHeader("x-vqd-accept") == null && path.contains("status")) {
                reqBuilder.header("x-vqd-accept", "1");
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            HttpHeaders responseHeaders = new HttpHeaders();
            for (Map.Entry<String, List<String>> header : response.headers().map().entrySet()) {
                String key = header.getKey().toLowerCase();
                if (key.startsWith("x-vqd") || key.equals("x-ddg-journey-id") || key.equals("content-type") || key.equals("set-cookie")) {
                    for (String val : header.getValue()) {
                        responseHeaders.add(header.getKey(), val);
                    }
                }
            }

            return new ResponseEntity<>(response.body(), responseHeaders, HttpStatus.valueOf(response.statusCode()));
        } catch (Exception e) {
            log.error("[DuckChatProxy] Error en GET {}: {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Proxy error: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/v1/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(
            @RequestBody String requestBody,
            HttpServletRequest request) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(DUCK_AI_BASE + "/v1/chat"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("accept", "text/event-stream")
                    .header("accept-language", "en-US,en;q=0.9,es;q=0.8")
                    .header("content-type", "application/json")
                    .header("origin", "https://duck.ai")
                    .header("referer", "https://duck.ai/")
                    .timeout(Duration.ofSeconds(60));

            // Copiar cabeceras críticas del cliente
            copyHeaderIfPresent(request, reqBuilder, "user-agent");
            copyHeaderIfPresent(request, reqBuilder, "x-vqd-hash-1");
            copyHeaderIfPresent(request, reqBuilder, "x-vqd-4");
            copyHeaderIfPresent(request, reqBuilder, "x-ddg-journey-id");
            copyHeaderIfPresent(request, reqBuilder, "x-fe-signals");
            copyHeaderIfPresent(request, reqBuilder, "x-fe-version");

            HttpResponse<InputStream> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            HttpHeaders responseHeaders = new HttpHeaders();
            for (Map.Entry<String, List<String>> header : response.headers().map().entrySet()) {
                String key = header.getKey().toLowerCase();
                if (key.startsWith("x-vqd") || key.equals("x-ddg-journey-id") || key.equals("content-type")) {
                    for (String val : header.getValue()) {
                        responseHeaders.add(header.getKey(), val);
                    }
                }
            }

            if (response.statusCode() != 200) {
                log.warn("[DuckChatProxy] DuckDuckGo respondió con código: {}", response.statusCode());
            }

            StreamingResponseBody stream = (OutputStream outputStream) -> {
                try (InputStream is = response.body()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush();
                    }
                } catch (Exception e) {
                    log.warn("[DuckChatProxy] Stream interrumpido: {}", e.getMessage());
                }
            };

            return new ResponseEntity<>(stream, responseHeaders, HttpStatus.valueOf(response.statusCode()));
        } catch (Exception e) {
            log.error("[DuckChatProxy] Error en /v1/chat: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void copyHeaderIfPresent(HttpServletRequest request, HttpRequest.Builder reqBuilder, String headerName) {
        String val = request.getHeader(headerName);
        if (val != null && !val.isBlank()) {
            reqBuilder.header(headerName, val);
        }
    }
}
