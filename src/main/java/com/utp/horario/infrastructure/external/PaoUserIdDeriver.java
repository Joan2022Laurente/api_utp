package com.utp.horario.infrastructure.external;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derivacion determinista del identificador interno de PAO ({@code paoUserId}).
 *
 * <p><b>Hallazgo de arquitectura (investigacion sobre el bundle oficial UTP+Class).</b>
 * El portal UTP+Class no recibe el {@code paoUserId} en el JWT de Keycloak ni lo expone en
 * ninguna API publica: lo deriva localmente y lo persiste en el navegador. El bundle
 * oficial de la SPA construye el identificador como un <b>UUID v5 (SHA-1)</b> sobre el
 * correo institucional del alumno, usando un namespace fijo:</p>
 *
 * <pre>
 *   paoUserId = uuidv5( namespace , email.toUpperCase() )
 * </pre>
 *
 * <p>Como el namespace vive en el bundle JavaScript publico que descarga cualquier
 * navegador, no es un secreto: es un detalle de implementacion del cliente oficial que
 * este gateway replica para no depender de un mapa de configuracion por alumno.</p>
 *
 * <p><b>Nota de acoplamiento:</b> si UTP cambiase el namespace, esta derivacion dejaria de
 * ser valida. Por eso el namespace es configurable
 * ({@code UTP_PAO_UUID_NAMESPACE}) y la derivacion es solo la <b>primera</b> de las vias de
 * la cascasa de resolucion: si falla, se prueban las siguientes
 * (claim del token, cache, auto-descubrimiento validado contra PAO, configuracion externa).</p>
 */
final class PaoUserIdDeriver {

    /**
     * Namespace UUID v5 de la plataforma UTP+Class (constante publica del bundle oficial).
     * Sobrescribible con {@code UTP_PAO_UUID_NAMESPACE} o con la propiedad
     * {@code utp.pao-uuid-namespace}.
     */
    static final String DEFAULT_NAMESPACE = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PaoUserIdDeriver() {
    }

    /**
     * Namespace efectivo: variable de entorno o propiedad de Spring si estan definidas,
     * por defecto la constante de la plataforma.
     */
    static String namespace() {
        String configured = System.getenv("UTP_PAO_UUID_NAMESPACE");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("utp.pao-uuid-namespace");
        }
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return DEFAULT_NAMESPACE;
    }

    /**
     * Deriva el {@code paoUserId} a partir del correo institucional del alumno.
     *
     * @param email correo del alumno (se normaliza a mayusculas, igual que la SPA oficial)
     * @return el UUID v5 resultante, o cadena vacia si el correo no es utilizable.
     */
    static String derive(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        try {
            byte[] namespaceBytes = uuidToBytes(namespace());
            byte[] nameBytes = email.trim().toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(namespaceBytes);
            sha1.update(nameBytes);
            byte[] digest = sha1.digest();

            byte[] uuid = new byte[16];
            System.arraycopy(digest, 0, uuid, 0, 16);
            uuid[6] = (byte) ((uuid[6] & 0x0F) | 0x50); // version 5
            uuid[8] = (byte) ((uuid[8] & 0x3F) | 0x80); // variante RFC 4122
            return toHex(uuid);
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return "";
        }
    }

    private static byte[] uuidToBytes(String uuid) {
        String hex = uuid.replace("-", "").trim();
        if (hex.length() != 32) {
            throw new IllegalArgumentException("Namespace UUID invalido: " + uuid);
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        String hex = new String(out);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
    }
}
