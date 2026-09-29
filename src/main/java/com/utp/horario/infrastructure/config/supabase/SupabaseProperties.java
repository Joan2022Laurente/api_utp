package com.utp.horario.infrastructure.config.supabase;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {
    private boolean enabled = true;
    private String url = "https://vngkmsmyuuukyzvqbovs.supabase.co";
    private String serviceRoleKey = "";
    private String anonKey = "";
    private String table = "official_syllabi";

    @PostConstruct
    public void initFallbackKeys() {
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            // Check local unversioned env files if available in development
            loadKeyFromLocalEnvFiles();
        }
    }

    private void loadKeyFromLocalEnvFiles() {
        List<Path> candidatePaths = List.of(
                Paths.get(".env"),
                Paths.get("laboratorio/.env"),
                Paths.get("../agentic-suite-hub/.env.local")
        );

        for (Path path : candidatePaths) {
            if (Files.exists(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("SUPABASE_URL=")) {
                            this.url = trimmed.substring("SUPABASE_URL=".length()).trim().replace("\"", "");
                        } else if (trimmed.startsWith("SUPABASE_SERVICE_ROLE_KEY=")) {
                            this.serviceRoleKey = trimmed.substring("SUPABASE_SERVICE_ROLE_KEY=".length()).trim().replace("\"", "");
                        } else if (trimmed.startsWith("NEXT_PUBLIC_SUPABASE_ANON_KEY=")) {
                            this.anonKey = trimmed.substring("NEXT_PUBLIC_SUPABASE_ANON_KEY=".length()).trim().replace("\"", "");
                        }
                    }
                    if (this.serviceRoleKey != null && !this.serviceRoleKey.isBlank()) {
                        log.info("[SupabaseProperties] Configuración de Supabase cargada dinámicamente desde entorno local: {} (URL: {})", path, this.url);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
