package com.utp.horario.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validacion de la regla de derivacion del {@code paoUserId} contra multiples cuentas
 * reales de la plataforma UTP.
 *
 * <p>Las capturas de {@code laboratorio/} (no versionadas) contienen, para cada curso,
 * el par {@code teacherEmail} + {@code teacherId} de los docentes. El {@code teacherId}
 * es la identidad interna de PAO, de modo que cada par es un vector de prueba real.</p>
 *
 * <p>Si la captura no esta disponible, la prueba se omite (skip) en lugar de fallar.</p>
 */
class PaoUserIdDeriverRealWorldValidationTest {

    private static final Path CAPTURE = Paths.get("laboratorio", "live_DASHBOARD_COURSES.json");

    @Test
    @DisplayName("La regla uuidv5 reproduce el paoUserId de multiples cuentas reales de UTP")
    void ruleHoldsForEveryRealAccountInTheCapture() throws Exception {
        Assumptions.assumeTrue(Files.exists(CAPTURE),
                "No hay captura de laboratorio para validar contra cuentas reales: se omite.");

        JsonNode root = new ObjectMapper().readTree(Files.readString(CAPTURE));
        JsonNode data = root.path("data");
        Assumptions.assumeTrue(data.isArray() && !data.isEmpty(), "La captura no contiene datos.");

        Map<String, String> realAccounts = new LinkedHashMap<>();
        for (JsonNode item : data) {
            JsonNode teachers = item.path("teachers");
            if (!teachers.isArray() || teachers.isEmpty()) {
                teachers = new ObjectMapper().createArrayNode().add(teachers);
            }
            for (JsonNode teacher : teachers) {
                String email = teacher.path("teacherEmail").asText("");
                String id = teacher.path("teacherId").asText("");
                if (!email.isBlank() && !id.isBlank()) {
                    realAccounts.putIfAbsent(email, id);
                }
            }
        }

        Assumptions.assumeTrue(realAccounts.size() >= 5,
                "La captura no aporta suficientes cuentas distintas: se omite.");

        int matched = 0;
        StringBuilder failures = new StringBuilder();
        for (Map.Entry<String, String> account : realAccounts.entrySet()) {
            String derived = PaoUserIdDeriver.derive(account.getKey());
            if (derived.equals(account.getValue())) {
                matched++;
            } else if (failures.length() < 600) {
                failures.append("\n  ").append(account.getKey())
                        .append(" -> real=").append(account.getValue())
                        .append(" derivado=").append(derived);
            }
        }

        assertEquals(realAccounts.size(), matched,
                "La derivacion debe coincidir con el paoUserId real de TODAS las cuentas. Fallos:" + failures);
    }

    @Test
    @DisplayName("Cada identidad derivada es un UUID v5 valido y unico")
    void everyDerivedIdentityIsAUniqueV5Uuid() {
        java.util.Set<String> derived = new java.util.LinkedHashSet<>();
        for (String email : new String[]{"a@utp.edu.pe", "b@utp.edu.pe", "c@utp.edu.pe", "A@utp.edu.pe"}) {
            String id = PaoUserIdDeriver.derive(email);
            assertTrue(id.matches("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"), id);
            derived.add(id);
        }
        assertEquals(3, derived.size(), "Cada correo (normalizado) debe producir un identificador distinto");
    }
}
