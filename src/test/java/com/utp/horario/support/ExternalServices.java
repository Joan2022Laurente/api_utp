package com.utp.horario.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Utilidad de soporte para pruebas de integración que dependen de servicios externos
 * (Supabase PostgreSQL, OpenRouter, SSO Keycloak UTP).
 *
 * <p>Las pruebas de integración de red <b>no son herméticas</b>: requieren conectividad
 * real y credenciales válidas. Sin este control, {@code ./mvnw clean test} falla en
 * cualquier entorno sin salida a Internet o sin las claves configuradas, lo que impide
 * certificar la salud del código en CI.</p>
 *
 * <p>Este helper sondea la alcanzabilidad una sola vez por JVM y permite a las pruebas
 * <b>omitirse</b> (skip) en lugar de <b>fallar</b> cuando la dependencia externa no está
 * disponible. Cuando la dependencia sí está disponible, la prueba se ejecuta con normalidad
 * conservando su cobertura real.</p>
 */
public final class ExternalServices {

    private static final int PROBE_TIMEOUT_MS = 3000;

    private static volatile Boolean supabaseReachable;
    private static volatile Boolean openRouterReachable;

    private ExternalServices() {
    }

    /** URL de Supabase efectiva (resuelta desde {@code SupabaseProperties} o valor por defecto). */
    public static String supabaseUrl() {
        try {
            com.utp.horario.infrastructure.config.supabase.SupabaseProperties properties =
                    new com.utp.horario.infrastructure.config.supabase.SupabaseProperties();
            properties.initFallbackKeys();
            String url = properties.getUrl();
            if (url != null && !url.isBlank()) {
                return url;
            }
        } catch (Exception ignored) {
            // Se usa el host por defecto
        }
        return "https://vngkmsmyuuukyzvqbovs.supabase.co";
    }

    public static boolean isSupabaseReachable() {
        Boolean cached = supabaseReachable;
        if (cached == null) {
            cached = probeHost(supabaseUrl());
            supabaseReachable = cached;
        }
        return cached;
    }

    public static boolean isOpenRouterReachable() {
        Boolean cached = openRouterReachable;
        if (cached == null) {
            cached = probeHost("https://openrouter.ai/api/v1");
            openRouterReachable = cached;
        }
        return cached;
    }

    private static boolean probeHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 443;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
                return true;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
