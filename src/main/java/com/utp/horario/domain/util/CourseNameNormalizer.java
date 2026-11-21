package com.utp.horario.domain.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizador canónico de nombres de curso.
 *
 * <p>Unifica los tres criterios que históricamente convivieron en la pasarela
 * (adaptador de pasarela, controlador de sílabos y cliente de Supabase), cada uno con
 * una semántica distinta. La regla única es: sin acentos, sin caracteres que no sean
 * alfanuméricos y en mayúsculas.</p>
 *
 * <p>Se aplica <b>sempre en los dos lados de una comparación</b> (clave de caché y
 * consulta, nombre matriculado y nombre del sílabo, etc.), de modo que el cambio de
 * criterio no altera ningún match existente.</p>
 */
public final class CourseNameNormalizer {

    private CourseNameNormalizer() {
    }

    /** @return el nombre normalizado, o cadena vacía si {@code name} es nulo o blank. */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }
}
