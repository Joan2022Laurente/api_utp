# UTP Academic Gateway API (Multi-Tenant & Production-Grade)

> **High-throughput, stateless Academic Gateway for UTP (Universidad Tecnológica del Perú).**  
> Diseñada para que servicios backend, bots académicos y plataformas educativas gestionen de forma segura, aislada y concurrente el acceso a notas, horarios, actividades y fórmulas oficiales de miles de estudiantes.

---

## 📑 Tabla de Contenidos
- [1. Arquitectura y Principios de Diseño](#1-arquitectura-y-principios-de-diseño)
- [2. Auditoría de Seguridad y Aislamiento Multi-Tenant](#2-auditoría-de-seguridad-y-aislamiento-multi-tenant)
- [3. Catálogo de Endpoints y Contratos de Datos](#3-catálogo-de-endpoints-y-contratos-de-datos)
  - [Autenticación e Identidad (`/auth`)](#autenticación-e-identidad-auth)
  - [Horarios y Exportación iCalendar (`/schedule`)](#horarios-y-exportación-icalendar-schedule)
  - [Actividades, Tareas y Rúbricas (`/tasks`)](#actividades-tareas-y-rúbricas-tasks)
  - [Cursos, Fórmulas y Simulador de Calificaciones (`/courses`)](#cursos-fórmulas-y-simulador-de-calificaciones-courses)
  - [Sílabos y Cronograma Semanal (`/syllabus`)](#sílabos-y-cronograma-semanal-syllabus)
- [4. Calidad de Software, Pruebas y Quality Gate](#4-calidad-de-software-pruebas-y-quality-gate)
- [5. Guía de Integración para Agentes y LLMs](#5-guía-de-integración-para-agentes-y-llms)
- [6. Despliegue y Ejecución Local](#6-despliegue-y-ejecución-local)

---

## 1. Arquitectura y Principios de Diseño

El sistema está construido bajo los principios de **Arquitectura Hexagonal (Ports and Adapters)** y **Domain-Driven Design (DDD)** sobre **Java 17** y **Spring Boot 3.3.3**:

```text
               ┌────────────────────────────────────────────────────────┐
               │              Presentation Layer (REST)                 │
               │   AuthController | ScheduleController | TaskController │
               │   CourseSummaryController | SyllabusController         │
               └──────────────────────────┬─────────────────────────────┘
                                          │
                                          ▼
               ┌────────────────────────────────────────────────────────┐
               │             Application / Domain Core                  │
               │   Ports IN: AcademicTaskServicePort, ScheduleService   │
               │   Engines: GradeSimulatorEngine, IcsCalendarExporter   │
               │   Domain Models: AcademicActivity, GradeSimulation     │
               └──────────────────────────┬─────────────────────────────┘
                                          │
                                          ▼
               ┌────────────────────────────────────────────────────────┐
               │             Infrastructure Adapters (OUT)              │
               │   UtpPortalGatewayAdapter (Keycloak, Portal GraphQL)   │
               │   H2 / JPA Persistence Adapter                         │
               └────────────────────────────────────────────────────────┘
```

- **Stateless por diseño:** Cada petición entrante se autentica mediante su propio `Authorization: Bearer <JWT>`, permitiendo escalar horizontalmente en Kubernetes, Docker o AWS ECS.
- **Resiliencia ante servicios upstream:** Desacopla las variaciones de la infraestructura interna de UTP (GraphQL Portal, Keycloak SSO y PAO Class Engine) proveyendo una API uniforme en JSON y RFC 5545.

---

## 2. Auditoría de Seguridad y Aislamiento Multi-Tenant

Con el objetivo de garantizar total transparencia y confiabilidad para backends que gestionan miles de alumnos:

### 🔒 Mitigación Estricta IDOR / BOLA (Broken Object Level Authorization)
- **Validación en `GET /auth/profile/{id}`:** El gateway valida criptográficamente que el claim `sub` del token Bearer del caller coincida exactamente con el `{id}` solicitado.
- **Resultado del Quality Gate:** Los intentos de consulta cruzada entre estudiantes retornan `403 Forbidden` (`SecurityException` gestionada en `GlobalExceptionHandler`), impidiendo cualquier acceso no autorizado a datos personales o académicos de terceros.

### 🛡️ Cero Fugas de Memoria entre Estudiantes (No Cross-Tenant Data Leaking)
- **Eliminación de Singletons Compartidos:** Erradicados los identificadores estáticos de prueba en las peticiones upstream.
- **Particionamiento por Alumno:** Todos los cachés en memoria (`userCourseRefMap`, `studentPaoUserMap`) residen aislados por código de estudiante (`studentCode`), impidiendo colisiones de horarios, notas o secciones.

---

## 3. Catálogo de Endpoints y Contratos de Datos

URL Base por defecto: `http://localhost:8080/api/v1`  
Documentación Swagger interactiva: `http://localhost:8080/api/v1/swagger-ui/index.html`

### Autenticación e Identidad (`/auth`)

#### `POST /auth/login`
Autentica directamente contra el Keycloak SSO institucional de UTP.
- **Body:**
  ```json
  {
    "username": "u23307609",
    "password": "Password123"
  }
  ```
- **Respuesta (200 OK):**
  ```json
  {
    "success": true,
    "message": "Autenticación exitosa",
    "data": {
      "id": "709837bc-ab2b-4c98-abb6-d1e9c49a5a8d",
      "studentCode": "U23307609",
      "fullName": "JOAN JOAQUIN CALLAÑAUPA LAURENTE",
      "email": "u23307609@utp.edu.pe",
      "career": "ING. DE SISTEMAS E INFORMÁTICA",
      "campus": "LIMA",
      "token": "eyJhbGciOiJSUzI1NiIs..."
    }
  }
  ```

#### `GET /auth/me`
Valida el token Bearer del caller y extrae los datos del estudiante de forma stateless.
- **Headers:** `Authorization: Bearer <JWT>`

#### `GET /auth/profile/{id}`
Consulta el perfil completo con protección IDOR (solo el titular puede consultarse a sí mismo).

---

### Horarios y Exportación iCalendar (`/schedule`)

#### `GET /schedule`
Obtiene las sesiones de clases del periodo, detallando aulas físicas, pabellones, pisos, nombres de docentes y enlaces virtuales.
- **Query Params:**
  - `period` (opcional, default: `"2026 - Ciclo 2 Agosto"` o código `2263`).
  - `studentId` (opcional, default: `"current-student"`).

#### `GET /schedule/export.ics`
Genera un stream en vivo en estándar **RFC 5545 (iCalendar)** con `Content-Type: text/calendar; charset=utf-8` para sincronización directa con Google Calendar, Apple Calendar y Microsoft Outlook.
- **Ejemplo de Cabeceras HTTP:**
  ```http
  HTTP/1.1 200 OK
  Content-Type: text/calendar; charset=utf-8
  Content-Disposition: attachment; filename="horario_utp.ics"
  ```
- **Estructura RFC 5545 retornada:**
  ```text
  BEGIN:VCALENDAR
  VERSION:2.0
  PRODID:-//UTP Horario API//ES
  CALSCALE:GREGORIAN
  METHOD:PUBLISH
  X-WR-CALNAME:Horario de Clases UTP
  X-WR-TIMEZONE:America/Lima
  BEGIN:VTIMEZONE
  TZID:America/Lima
  ...
  BEGIN:VEVENT
  UID:sess-153-20260810T183000@utp.edu.pe
  DTSTART;TZID=America/Lima:20260810T183000
  DTEND;TZID=America/Lima:20260810T200000
  SUMMARY:Gestión del Servicio TI (100000S74T)
  LOCATION:Aula AV002, Campus Lima Sur
  DESCRIPTION:Docente: Matias Hernan Palomino Garcia\nModalidad: Presencial
  STATUS:CONFIRMED
  END:VEVENT
  END:VCALENDAR
  ```

---

### Actividades, Tareas y Rúbricas (`/tasks`)

#### `GET /tasks/activities`
Consulta el calendario de actividades de Canvas / UTP+ Class con enriquecimiento semántico y filtros dinámicos.
- **Filtros (Query Params):**
  - `week`: Número de semana académica (ej. `week=7`).
  - `status`: Estado de la entrega (`pending`, `delivered`, `missing`).
  - `onlyGraded`: Si es `true`, filtra exclusivamente actividades ponderadas para el promedio.
  - `type`: Tipo de actividad (`homework`, `forum`, `quiz`).
- **Campos Enriquecidos Retornados:**
  - `classificationCategory`: `WEIGHTED_EVALUATION` | `PRACTICE_HOMEWORK` | `PARTICIPATION_FORUM` | `EXAM`
  - `urgency`: `OVERDUE` | `DUE_TODAY` | `DUE_THIS_WEEK` | `UPCOMING`
  - `daysRemaining`: Días calendario restantes respecto a la fecha límite (`finishAt`).

#### `GET /tasks/upcoming`
Retorna las evaluaciones calificadas más urgentes ordenadas cronológicamente.
- **Query Params:** `limit` (default: 5).

#### `GET /tasks/specification`
Extrae la consigna en Markdown, tipo de entrega (subida de archivos o URL), intentos máximos y rúbrica detallada con puntajes por criterio.
- **Query Params:** `sectionId`, `activityId`.

---

### Cursos, Fórmulas y Simulador de Calificaciones (`/courses`)

#### `GET /courses/summary`
Retorna los cursos matriculados, calificaciones parciales registradas en el Portal UTP y fórmulas oficiales ponderadas:
```json
{
  "courseCode": "100000SI97",
  "courseName": "SERVICIOS CLOUD",
  "formula": "10%*[PA] + 25%*[PC1] + 25%*[PC2] + 40%*[PROY]",
  "evaluations": [
    { "shortName": "PC1", "name": "PRACTICA CALIFICADA 1", "value": "16.00", "isGraded": true },
    { "shortName": "PA", "name": "PARTICIPACION EN CLASE", "value": "0.02", "isGraded": false }
  ]
}
```

#### `GET /courses/{courseCode}/simulator` & `GET /courses/simulator`
Simulador matemático y proyector de notas rectoras.
- **Query Params:**
  - `targetGrade`: Nota meta deseada (default: `12.0` - nota mínima aprobatoria UTP).
  - `periodId`: Periodo académico (default: `2263`).
- **Contrato de Respuesta:**
  ```json
  {
    "success": true,
    "message": "Simulación de calificación calculada exitosamente",
    "data": {
      "courseCode": "100000SI97",
      "courseName": "SERVICIOS CLOUD",
      "formula": "10%*[PA] + 25%*[PC1] + 25%*[PC2] + 40%*[PROY]",
      "targetGrade": 12.0,
      "currentAccumulatedScore": 4.0,
      "gradedWeightPercentage": 25,
      "remainingWeightPercentage": 75,
      "requiredAverageOnPending": 10.67,
      "minPossibleGrade": 4.0,
      "maxPossibleGrade": 19.0,
      "isPassed": false,
      "status": "ACHIEVABLE",
      "message": "Alcanzable: Necesitas un promedio de 10.67 en las evaluaciones restantes para asegurar 12.0.",
      "evaluations": [
        {
          "shortName": "PC1",
          "name": "PRACTICA CALIFICADA 1",
          "weightPercentage": 25,
          "currentGrade": 16.0,
          "isGraded": true,
          "pointsContributed": 4.0
        },
        {
          "shortName": "PC2",
          "name": "PRACTICA CALIFICADA 2",
          "weightPercentage": 25,
          "currentGrade": null,
          "isGraded": false,
          "pointsContributed": 0.0
        }
      ]
    }
  }
  ```
- **Estados de Simulación (`status`):**
  - `ALREADY_PASSED`: El estudiante ya acumuló suficientes puntos para aprobar independientemente de las notas futuras (`requiredAverageOnPending: 0.0`).
  - `ACHIEVABLE`: Requiere un promedio $\le 15.0$ en lo pendiente.
  - `CHALLENGING`: Requiere un promedio entre $15.01$ y $20.0$.
  - `IMPOSSIBLE`: Matemáticamente inalcanzable (requeriría $> 20.0$).
  - `PASSED` / `FAILED`: Si el ciclo concluyó (100% calificado).

---

### Sílabos y Cronograma Semanal (`/syllabus`)

#### `POST /syllabus/sync`
Sincroniza y parsea automáticamente el sílabo en PDF del curso mediante el motor determinista de expresiones regulares y máquinas de estado.

#### `GET /syllabus/{courseCode}`
Retorna las competencias, fórmula oficial, unidades de aprendizaje y cronograma detallado de las 18 semanas de clases.

---

## 4. Calidad de Software, Pruebas y Quality Gate

El proyecto mantiene un rigor de ingeniería de nivel Staff Engineer:

| Componente | Tipo de Prueba | Cobertura / Validación | Estado |
| :--- | :--- | :--- | :--- |
| `GradeSimulatorEngineTest` | Unitaria | Casos estándar, cursos con nota ya asegurada (`ALREADY_PASSED`) y metas imposibles (`IMPOSSIBLE`) | ✅ 100% PASS |
| `IcsCalendarExporterTest` | Unitaria | Generación sintáctica RFC 5545, zona horaria `America/Lima`, escape de caracteres y campos VEVENT | ✅ 100% PASS |
| `AcademicTaskServiceTest` | Unitaria / Mockito | Rúbricas completas, filtros por fecha, resumen de notas y proyecciones | ✅ 100% PASS |
| `AuthControllerTest` | Integración / WebMvc | Validaciones de payloads, beans de seguridad y flujos de autenticación | ✅ 100% PASS |
| `SyllabusFormulaParserTest`| Unitaria | Extracción de ponderaciones porcentuales y normalización a 100% | ✅ 100% PASS |
| **Suite de Penetración IDOR** | End-to-End Live | Verificación de 403 Forbidden al intentar consultar perfiles ajenos con JWT válido | ✅ 100% PASS |
| **Suite Completa End-to-End**| End-to-End Live | Validación contra servidores activos de UTP (153 sesiones, 142 actividades, 6 cursos con fórmulas) | ✅ 100% PASS |

---

## 5. Guía de Integración para Agentes y LLMs

Si estás desarrollando un bot, agente de IA o microservicio backend que consuma esta API:

1. **Flujo de Autenticación:**
   - Realizar `POST /auth/login` con el usuario institucional del alumno.
   - Guardar el `token` devuelto. Utilizar siempre el header `Authorization: Bearer <token>` en todas las llamadas subsiguientes.
2. **Contexto de Respuestas:**
   - Todas las respuestas exitosas devuelven la envoltura estándar:
     ```json
     {
       "success": true,
       "message": "...",
       "data": { ... }
     }
     ```
3. **Manejo de Errores para LLMs:**
   - `401 Unauthorized`: Token expirado. Debe invocarse nuevamente el login.
   - `403 Forbidden`: Intento de acceder a recursos fuera del tenant del estudiante (ej. IDOR bloqueado).
   - `400 Bad Request`: Parámetros de periodo o formato inválidos.

---

## 6. Despliegue y Ejecución Local

### Prerrequisitos
- JDK 17 o superior
- Conexión a Internet (para conectores externos UTP)

### Comandos de Compilación y Ejecución
```bash
# Compilar código y pruebas
./mvnw clean test-compile

# Ejecutar pruebas unitarias del Quality Gate
./mvnw test "-Dtest=GradeSimulatorEngineTest,IcsCalendarExporterTest,AcademicTaskServiceTest,AuthControllerTest,SyllabusFormulaParserTest"

# Iniciar servidor local
./mvnw spring-boot:run
```
El servicio estará disponible en `http://localhost:8080/api/v1`.
