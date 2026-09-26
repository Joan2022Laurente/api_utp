package com.utp.horario.application.service.export;

import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.ScheduleInterval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class IcsCalendarExporter {

    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter ICS_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String exportSchedule(ScheduleInterval schedule) {
        StringBuilder sb = new StringBuilder();
        String nowUtc = ICS_UTC.format(java.time.Instant.now());

        sb.append("BEGIN:VCALENDAR").append(CRLF);
        sb.append("VERSION:2.0").append(CRLF);
        sb.append("PRODID:-//UTP Horario API//ES").append(CRLF);
        sb.append("CALSCALE:GREGORIAN").append(CRLF);
        sb.append("METHOD:PUBLISH").append(CRLF);
        sb.append("X-WR-CALNAME:Horario de Clases UTP").append(CRLF);
        sb.append("X-WR-TIMEZONE:America/Lima").append(CRLF);

        // VTIMEZONE para America/Lima (UTC-5 sin horario de verano)
        sb.append("BEGIN:VTIMEZONE").append(CRLF);
        sb.append("TZID:America/Lima").append(CRLF);
        sb.append("X-LIC-LOCATION:America/Lima").append(CRLF);
        sb.append("BEGIN:STANDARD").append(CRLF);
        sb.append("TZOFFSETFROM:-0500").append(CRLF);
        sb.append("TZOFFSETTO:-0500").append(CRLF);
        sb.append("TZNAME:-05").append(CRLF);
        sb.append("DTSTART:19700101T000000").append(CRLF);
        sb.append("END:STANDARD").append(CRLF);
        sb.append("END:VTIMEZONE").append(CRLF);

        if (schedule != null && schedule.getClasses() != null) {
            List<ClassSession> sessions = schedule.getClasses();
            for (ClassSession session : sessions) {
                if (session.getStartAt() == null) {
                    continue;
                }

                LocalDateTime start = session.getStartAt();
                LocalDateTime end = session.getFinishAt() != null ? session.getFinishAt() : start.plusHours(2);

                String uid = (session.getId() != null && !session.getId().isBlank())
                        ? session.getId() + "-" + start.format(ICS_DATE_TIME) + "@utp.edu.pe"
                        : UUID.randomUUID().toString() + "@utp.edu.pe";

                String summary = (session.getCourseName() != null ? session.getCourseName() : "Clase UTP");
                if (session.getCourseCode() != null && !session.getCourseCode().isBlank()) {
                    summary += " (" + session.getCourseCode() + ")";
                }

                String location = buildLocation(session);
                String description = buildDescription(session);

                sb.append("BEGIN:VEVENT").append(CRLF);
                sb.append("UID:").append(uid).append(CRLF);
                sb.append("DTSTAMP:").append(nowUtc).append(CRLF);
                sb.append("DTSTART;TZID=America/Lima:").append(start.format(ICS_DATE_TIME)).append(CRLF);
                sb.append("DTEND;TZID=America/Lima:").append(end.format(ICS_DATE_TIME)).append(CRLF);
                sb.append("SUMMARY:").append(escapeIcsText(summary)).append(CRLF);
                if (!location.isBlank()) {
                    sb.append("LOCATION:").append(escapeIcsText(location)).append(CRLF);
                }
                if (!description.isBlank()) {
                    sb.append("DESCRIPTION:").append(escapeIcsText(description)).append(CRLF);
                }
                sb.append("STATUS:CONFIRMED").append(CRLF);
                sb.append("TRANSP:OPAQUE").append(CRLF);
                sb.append("END:VEVENT").append(CRLF);
            }
        }

        sb.append("END:VCALENDAR").append(CRLF);
        return sb.toString();
    }

    private String buildLocation(ClassSession session) {
        StringBuilder loc = new StringBuilder();
        if (session.getClassroom() != null && !session.getClassroom().isBlank()) {
            loc.append("Aula ").append(session.getClassroom());
        }
        if (session.getBuilding() != null && !session.getBuilding().isBlank()) {
            if (loc.length() > 0) loc.append(", ");
            loc.append(session.getBuilding());
        }
        if (loc.length() == 0) {
            if ("V".equalsIgnoreCase(session.getModality()) || "R".equalsIgnoreCase(session.getModality())) {
                loc.append("Sesión Virtual UTP+ Class");
            }
        }
        return loc.toString();
    }

    private String buildDescription(ClassSession session) {
        StringBuilder desc = new StringBuilder();
        if (session.getTeacher() != null && !session.getTeacher().isBlank()) {
            desc.append("Docente: ").append(session.getTeacher()).append("\n");
        }
        if (session.getSection() != null && !session.getSection().isBlank()) {
            desc.append("Sección: ").append(session.getSection()).append("\n");
        }
        if (session.getModality() != null && !session.getModality().isBlank()) {
            String modName = switch (session.getModality().toUpperCase()) {
                case "P" -> "Presencial";
                case "R" -> "Remoto";
                case "V" -> "Virtual";
                default -> session.getModality();
            };
            desc.append("Modalidad: ").append(modName).append("\n");
        }
        if (session.getFloor() != null && !session.getFloor().isBlank()) {
            desc.append("Piso: ").append(session.getFloor()).append("\n");
        }
        if (session.getZoomLink() != null && !session.getZoomLink().isBlank()) {
            desc.append("Enlace Zoom: ").append(session.getZoomLink()).append("\n");
        }
        if (session.getClassLink() != null && !session.getClassLink().isBlank()) {
            desc.append("Enlace UTP+ Class: ").append(session.getClassLink()).append("\n");
        }
        return desc.toString().trim();
    }

    private String escapeIcsText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,")
                   .replace("\r\n", "\\n")
                   .replace("\n", "\\n")
                   .replace("\r", "\\n");
    }
}
