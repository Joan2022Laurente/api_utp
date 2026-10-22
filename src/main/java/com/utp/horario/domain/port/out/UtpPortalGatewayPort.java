package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.model.TaskSpecification;

import java.util.List;

public interface UtpPortalGatewayPort {
    StudentProfile login(String username, String password);
    StudentProfile refreshToken(String refreshToken);
    ScheduleInterval fetchSchedule(String token, String period);
    String fetchSyllabusPdfText(String token, String courseCode);
    TaskSpecification fetchTaskSpecification(String sectionId, String activityId, String token);
    List<AcademicActivity> fetchCalendarActivities(String dateToQuery, String intervalMode, String token);
    CourseSummaryData fetchCourseSummary(String periodId, String token);

    /**
     * Resuelve el codigo de catalogo de un curso a partir de su identificador de seccion
     * (UUID de PAO o numerico del Portal) o de su nombre.
     *
     * <p>Se usa como clave alternativa de busqueda en la cache de silabos, que esta
     * indexada por codigo de catalogo (p. ej. {@code 100000SI82}).</p>
     *
     * @return el codigo de catalogo, o {@code null} si no se pudo resolver.
     */
    String resolveCourseCode(String sectionId, String courseName, String token);
}

