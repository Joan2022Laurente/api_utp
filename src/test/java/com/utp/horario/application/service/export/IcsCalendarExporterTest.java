package com.utp.horario.application.service.export;

import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.ScheduleInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IcsCalendarExporterTest {

    private IcsCalendarExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new IcsCalendarExporter();
    }

    @Test
    @DisplayName("Debe generar archivo iCalendar RFC 5545 válido con sesiones")
    void testExportSchedule() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 14, 30);
        LocalDateTime end = LocalDateTime.of(2026, 8, 10, 16, 45);

        ClassSession session = ClassSession.builder()
                .id("sess-100")
                .courseCode("100000SI97")
                .courseName("SERVICIOS CLOUD")
                .section("53366")
                .classroom("A302")
                .building("Pabellón A")
                .teacher("PEREZ, JUAN")
                .modality("P")
                .startAt(start)
                .finishAt(end)
                .build();

        ScheduleInterval schedule = ScheduleInterval.builder()
                .id("sched-1")
                .periodName("2026 - Ciclo 2 Agosto")
                .classes(List.of(session))
                .build();

        String ics = exporter.exportSchedule(schedule);

        assertNotNull(ics);
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("VERSION:2.0"));
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:SERVICIOS CLOUD (100000SI97)"));
        assertTrue(ics.contains("LOCATION:Aula A302\\, Pabellón A"));
        assertTrue(ics.contains("DTSTART;TZID=America/Lima:20260810T143000"));
        assertTrue(ics.contains("DTEND;TZID=America/Lima:20260810T164500"));
        assertTrue(ics.contains("END:VEVENT"));
        assertTrue(ics.contains("END:VCALENDAR"));
    }

    @Test
    @DisplayName("Sesión sin ID oficial genera UID determinista basado en curso y fecha/hora (RFC 5545)")
    void testDeterministicUidWhenSessionIdIsNull() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 14, 30);
        ClassSession session = ClassSession.builder()
                .id(null)
                .courseCode("100000SI97")
                .courseName("SERVICIOS CLOUD")
                .startAt(start)
                .finishAt(start.plusHours(2))
                .build();

        ScheduleInterval schedule = ScheduleInterval.builder()
                .id("sched-1")
                .classes(List.of(session))
                .build();

        String ics1 = exporter.exportSchedule(schedule);
        String ics2 = exporter.exportSchedule(schedule);

        String uid1 = ics1.lines().filter(l -> l.startsWith("UID:")).findFirst().orElse("");
        String uid2 = ics2.lines().filter(l -> l.startsWith("UID:")).findFirst().orElse("");

        assertFalse(uid1.isBlank());
        assertEquals(uid1, uid2, "El UID debe ser determinista para evitar eventos duplicados");
        assertTrue(uid1.endsWith("@utp.edu.pe"));
    }
}
