package com.utp.horario.infrastructure.persistence.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pruebas de regresion de la seleccion de fila en la cache de silabos.
 *
 * <p>La cache esta indexada por codigo de catalogo, pero los clientes consultan tambien
 * por el nombre del curso. La busqueda en Supabase cubre las tres columnas y la eleccion
 * final se valida en Java para no devolver una fila que no corresponda exactamente.</p>
 */
class SupabaseSyllabusSelectionTest {

    private final SupabaseSyllabusClient client = new SupabaseSyllabusClient(
            new com.utp.horario.infrastructure.config.supabase.SupabaseProperties(), new ObjectMapper());

    private Syllabus select(String... rows) throws Exception {
        String json = "[" + String.join(",", rows) + "]";
        Method method = SupabaseSyllabusClient.class.getDeclaredMethod("selectBestMatch",
                com.fasterxml.jackson.databind.JsonNode.class, String.class);
        method.setAccessible(true);
        return (Syllabus) method.invoke(client,
                new ObjectMapper().readTree(json), rows.length > 0 ? extractCode(rows[0]) : "");
    }

    private String extractCode(String row) {
        int idx = row.indexOf("\"course_code\":\"");
        if (idx < 0) {
            return "";
        }
        int start = idx + "\"course_code\":\"".length();
        return row.substring(start, row.indexOf('"', start));
    }

    @Test
    @DisplayName("Prioriza la coincidencia exacta de codigo de catalogo")
    void codeMatchHasPriority() throws Exception {
        Syllabus result = select(
                "{\"course_id\":\"100000ST61\",\"course_code\":\"100000ST61\",\"course_name\":\"DESARROLLO WEB INTEGRADO\"}",
                "{\"course_id\":\"100000SI82\",\"course_code\":\"100000SI82\",\"course_name\":\"FORMACION PARA LA INVESTIGACION\"}");

        assertNotNull(result);
        assertEquals("100000ST61", result.getCourseCode());
    }

    @Test
    @DisplayName("Resuelve la coincidencia por nombre ignorando mayusculas")
    void nameMatchIsCaseInsensitive() throws Exception {
        String row = "{\"course_id\":\"100000ST61\",\"course_code\":\"100000ST61\",\"course_name\":\"DESARROLLO WEB INTEGRADO\"}";

        Method method = SupabaseSyllabusClient.class.getDeclaredMethod("selectBestMatch",
                com.fasterxml.jackson.databind.JsonNode.class, String.class);
        method.setAccessible(true);
        Syllabus result = (Syllabus) method.invoke(client,
                new ObjectMapper().readTree("[" + row + "]"), "desarrollo web integrado");

        assertNotNull(result, "Un nombre en minusculas debe resolver la fila");
        assertEquals("100000ST61", result.getCourseCode());
    }

    @Test
    @DisplayName("Descarta filas que no corresponden exactamente a la clave (ilike con comodines)")
    void rejectsInexactRows() throws Exception {
        String row = "{\"course_id\":\"100000EMP1\",\"course_code\":\"100000EMP1\",\"course_name\":\"RUTA LABORAL 1\"}";

        Method method = SupabaseSyllabusClient.class.getDeclaredMethod("selectBestMatch",
                com.fasterxml.jackson.databind.JsonNode.class, String.class);
        method.setAccessible(true);
        Syllabus result = (Syllabus) method.invoke(client,
                new ObjectMapper().readTree("[" + row + "]"), "RUTA LABORAL X");

        assertNull(result, "No debe aceptarse una fila que solo coincide por el comodin de ilike");
    }
}
