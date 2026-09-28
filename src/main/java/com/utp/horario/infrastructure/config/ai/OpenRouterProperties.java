package com.utp.horario.infrastructure.config.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ai.openrouter")
public class OpenRouterProperties {
    private boolean enabled = true;
    private String apiUrl = "https://openrouter.ai/api/v1";
    private String primaryModel = "qwen/qwen3.8-27b:free";
    private List<String> fallbackModels = new ArrayList<>(Arrays.asList(
            "google/gemma-4-31b-it:free",
            "nvidia/nemotron-3.5-lightning:free",
            "liquid/lfm-2.5-2.6b:free",
            "google/gemma-4-26b-a4b-it:free"
    ));
    private List<String> keys = new ArrayList<>();

    @PostConstruct
    public void initFleetKeys() {
        if (keys == null || keys.isEmpty()) {
            loadKeysFromEnvironmentOrFile();
        }
    }

    private void loadKeysFromEnvironmentOrFile() {
        // 1. Check system environment
        String envKeys = System.getenv("OPENROUTER_KEYS");
        if (envKeys != null && !envKeys.isBlank()) {
            this.keys = Arrays.stream(envKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            log.info("[OpenRouterProperties] {} claves cargadas desde variable de entorno OPENROUTER_KEYS", this.keys.size());
            return;
        }

        // 2. Check unversioned local fleet keys file
        List<Path> candidateFiles = List.of(
                Paths.get("laboratorio/openrouter_fleet_keys.json"),
                Paths.get("openrouter_fleet_keys.json")
        );

        ObjectMapper mapper = new ObjectMapper();
        for (Path p : candidateFiles) {
            if (Files.exists(p)) {
                try {
                    List<String> loaded = mapper.readValue(p.toFile(), new TypeReference<List<String>>() {});
                    if (loaded != null && !loaded.isEmpty()) {
                        this.keys = loaded;
                        log.info("[OpenRouterProperties] {} claves de flota OpenRouter cargadas desde {}", loaded.size(), p);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("[OpenRouterProperties] Error leyendo {}: {}", p, e.getMessage());
                }
            }
        }
    }
}
