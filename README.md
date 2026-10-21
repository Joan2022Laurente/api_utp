# UTP Academic Gateway API (Multi-Tenant & Production-Grade)

> **High-throughput, stateless Academic Gateway for UTP (Universidad TecnolÃ³gica del PerÃº).**  
> DiseÃ±ada para que servicios backend, bots acadÃ©micos y plataformas educativas gestionen de forma segura, aislada y concurrente el acceso a notas, horarios, actividades y fÃ³rmulas oficiales de miles de estudiantes.

---

## ðŸ“‘ Tabla de Contenidos
- [1. Arquitectura y Principios de DiseÃ±o](#1-arquitectura-y-principios-de-diseÃ±o)
- [2. AuditorÃ­a de Seguridad y Aislamiento Multi-Tenant](#2-auditorÃ­a-de-seguridad-y-aislamiento-multi-tenant)
- [3. CatÃ¡logo de Endpoints y Contratos de Datos](#3-catÃ¡logo-de-endpoints-y-contratos-de-datos)
  - [AutenticaciÃ³n e Identidad (`/auth`)](#autenticaciÃ³n-e-identidad-auth)
  - [Horarios y ExportaciÃ³n iCalendar (`/schedule`)](#horarios-y-exportaciÃ³n-icalendar-schedule)
  - [Actividades, Tareas y RÃºbricas (`/tasks`)](#actividades-tareas-y-rÃºbricas-tasks)
  - [Cursos, FÃ³rmulas y Simulador de Calificaciones (`/courses`)](#cursos-fÃ³rmulas-y-simulador-de-calificaciones-courses)
  - [SÃ­labos y Cronograma Semanal (`/syllabus`)](#sÃ­labos-y-cronograma-semanal-syllabus)
- [4. Calidad de Software, Pruebas y Quality Gate](#4-calidad-de-software-pruebas-y-quality-gate)
- [5. GuÃ­a de IntegraciÃ³n para Agentes y LLMs](#5-guÃ­a-de-integraciÃ³n-para-agentes-y-llms)
- [6. Despliegue y EjecuciÃ³n Local](#6-despliegue-y-ejecuciÃ³n-local)

---

## 1. Arquitectura y Principios de DiseÃ±o

El sistema estÃ¡ construido bajo los principios de **Arquitectura Hexagonal (Ports and Adapters)** y **Domain-Driven Design (DDD)** sobre **Java 17** y **Spring Boot 3.3.3**:

```text
               â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
               â”‚              Presentation Layer (REST)                 â”‚
               â”‚   AuthController | ScheduleController | TaskController â”‚
               â”‚   CourseSummaryController | SyllabusController         â”‚
               â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                                          â”‚
                                          â–¼
               â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
               â”‚             Application / Domain Core                  â”‚
               â”‚   Ports IN: AcademicTaskServicePort, ScheduleService   â”‚
               â”‚   Engines: GradeSimulatorEngine, IcsCalendarExporter   â”‚
               â”‚   Domain Models: AcademicActivity, GradeSimulation     â”‚
               â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                                          â”‚
                                          â–¼
               â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
               â”‚             Infrastructure Adapters (OUT)              â”‚
               â”‚   UtpPortalGatewayAdapter (Keycloak, Portal GraphQL)   â”‚
               â”‚   H2 / JPA Persistence Adapter                         â”‚
               â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

- **Stateless por diseÃ±o:** Cada peticiÃ³n entrante se autentica mediante su propio `Authorization: Bearer <JWT>`, permitiendo escalar horizontalmente en Kubernetes, Docker o AWS ECS.
- **Resiliencia ante servicios upstream:** Desacopla las variaciones de la infraestructura interna de UTP (GraphQL Portal, Keycloak SSO y PAO Class Engine) proveyendo una API uniforme en JSON y RFC 5545.

---

## 2. AuditorÃ­a de Seguridad y Aislamiento Multi-Tenant

Con el objetivo de garantizar total transparencia y confiabilidad para backends que gestionan miles de alumnos:

### ðŸ”’ MitigaciÃ³n Estricta IDOR / BOLA (Broken Object Level Authorization)
- **ValidaciÃ³n en `GET /auth/profile/{id}`:** El gateway valida criptogrÃ¡ficamente que el claim `sub` del token Bearer del caller coincida exactamente con el `{id}` solicitado.
- **Resultado del Quality Gate:** Los intentos de consulta cruzada entre estudiantes retornan `403 Forbidden` (`SecurityException` gestionada en `GlobalExceptionHandler`), impidiendo cualquier acceso no autorizado a datos personales o acadÃ©micos de terceros.

### ðŸ›¡ï¸ Cero Fugas de Memoria entre Estudiantes (No Cross-Tenant Data Leaking)
- **EliminaciÃ³n de Singletons Compartidos:** Erradicados los identificadores estÃ¡ticos de prueba en las peticiones upstream.
- **Particionamiento por Alumno:** Todos los cachÃ©s en memoria (`userCourseRefMap`, `studentPaoUserMap`) residen aislados por cÃ³digo de estudiante (`studentCode`), impidiendo colisiones de horarios, notas o secciones.

---

## 3. CatÃ¡logo de Endpoints y Contratos de Datos

URL Base por defecto: `http://localhost:8080/api/v1`  
DocumentaciÃ³n Swagger interactiva: `http://localhost:8080/api/v1/swagger-ui/index.html`

### AutenticaciÃ³n e Identidad (`/auth`)

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
    "message": "AutenticaciÃ³n exitosa",
    "data": {
      "id": "709837bc-ab2b-4c98-abb6-d1e9c49a5a8d",
      "studentCode": "U23307609",
      "fullName": "JOAN JOAQUIN CALLAÃ‘AUPA LAURENTE",
      "email": "u23307609@utp.edu.pe",
      "career": "ING. DE SISTEMAS E INFORMÃTICA",
      "campus": "LIMA",
      "token": "eyJhbGciOiJSUzI1NiIs..."
    }
  }
  ```

#### `GET /auth/me`
Valida el token Bearer del caller y extrae los datos del estudiante de forma stateless.
- **Headers:** `Authorization: Bearer <JWT>`

#### `GET /auth/profile/{id}`
Consulta el perfil completo con protecciÃ³n IDOR (solo el titular puede consultarse a sÃ­ mismo).

---

### Horarios y ExportaciÃ³n iCalendar (`/schedule`)

#### `GET /schedule`
Obtiene las sesiones de clases del periodo, detallando aulas fÃ­sicas, pabellones, pisos, nombres de docentes y enlaces virtuales.
- **Query Params:**
  - `period` (opcional, default: `"2026 - Ciclo 2 Agosto"` o cÃ³digo `2263`).
  - `studentId` (opcional, default: `"current-student"`).

#### `GET /schedule/export.ics`
Genera un stream en vivo en estÃ¡ndar **RFC 5545 (iCalendar)** con `Content-Type: text/calendar; charset=utf-8` para sincronizaciÃ³n directa con Google Calendar, Apple Calendar y Microsoft Outlook.
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
  SUMMARY:GestiÃ³n del Servicio TI (100000S74T)
  LOCATION:Aula AV002, Campus Lima Sur
  DESCRIPTION:Docente: Matias Hernan Palomino Garcia\nModalidad: Presencial
  STATUS:CONFIRMED
  END:VEVENT
  END:VCALENDAR
  ```

---

### Actividades, Tareas y RÃºbricas (`/tasks`)

#### `GET /tasks/activities`
Consulta el calendario de actividades de Canvas / UTP+ Class con enriquecimiento semÃ¡ntico y filtros dinÃ¡micos.
- **Filtros (Query Params):**
  - `week`: NÃºmero de semana acadÃ©mica (ej. `week=7`).
  - `status`: Estado de la entrega (`pending`, `delivered`, `missing`).
  - `onlyGraded`: Si es `true`, filtra exclusivamente actividades ponderadas para el promedio.
  - `type`: Tipo de actividad (`homework`, `forum`, `quiz`).
- **Campos Enriquecidos Retornados:**
  - `classificationCategory`: `WEIGHTED_EVALUATION` | `PRACTICE_HOMEWORK` | `PARTICIPATION_FORUM` | `EXAM`
  - `urgency`: `OVERDUE` | `DUE_TODAY` | `DUE_THIS_WEEK` | `UPCOMING`
  - `daysRemaining`: DÃ­as calendario restantes respecto a la fecha lÃ­mite (`finishAt`).

#### `GET /tasks/upcoming`
Retorna las evaluaciones calificadas mÃ¡s urgentes ordenadas cronolÃ³gicamente.
- **Query Params:** `limit` (default: 5).

#### `GET /tasks/specification`
Extrae la consigna en Markdown, tipo de entrega (subida de archivos o URL), intentos mÃ¡ximos y rÃºbrica detallada con puntajes por criterio.
- **Query Params:** `sectionId`, `activityId`.

---

### Cursos, FÃ³rmulas y Simulador de Calificaciones (`/courses`)

#### `GET /courses/summary`
Retorna los cursos matriculados, calificaciones parciales registradas en el Portal UTP y fÃ³rmulas oficiales ponderadas:
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
Simulador matemÃ¡tico y proyector de notas rectoras.
- **Query Params:**
  - `targetGrade`: Nota meta deseada (default: `12.0` - nota mÃ­nima aprobatoria UTP).
  - `periodId`: Periodo acadÃ©mico (default: `2263`).
- **Contrato de Respuesta:**
  ```json
  {
    "success": true,
    "message": "SimulaciÃ³n de calificaciÃ³n calculada exitosamente",
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
- **Estados de SimulaciÃ³n (`status`):**
  - `ALREADY_PASSED`: El estudiante ya acumulÃ³ suficientes puntos para aprobar independientemente de las notas futuras (`requiredAverageOnPending: 0.0`).
  - `ACHIEVABLE`: Requiere un promedio $\le 15.0$ en lo pendiente.
  - `CHALLENGING`: Requiere un promedio entre $15.01$ y $20.0$.
  - `IMPOSSIBLE`: MatemÃ¡ticamente inalcanzable (requerirÃ­a $> 20.0$).
  - `PASSED` / `FAILED`: Si el ciclo concluyÃ³ (100% calificado).

---

### SÃ­labos y Cronograma Semanal (`/syllabus`)

#### `POST /syllabus/sync`
Sincroniza y parsea automÃ¡ticamente el sÃ­labo en PDF del curso mediante el motor determinista de expresiones regulares y mÃ¡quinas de estado.

#### `GET /syllabus/{courseCode}`
Retorna las competencias, fÃ³rmula oficial, unidades de aprendizaje y cronograma detallado de las 18 semanas de clases en JSON (`ApiResponse<Syllabus>`).

#### `GET /syllabus/{courseCode}/markdown` & `GET /syllabus/markdown`
> **Especial para LLMs y Prompt Engineering:** Devuelve el sÃ­labo completo formateado en **Markdown estÃ¡ndar** (`Content-Type: text/markdown; charset=utf-8`) con encabezados `#`, listas y tablas de ponderaciones/cronograma.
- **Ventaja para LLMs:** Los backends pueden inyectar directamente el cuerpo de esta respuesta en el contexto de un modelo (OpenAI GPT-4o, Claude 3.5 Sonnet, Gemini 1.5 Pro, DeepSeek) para extraer esquemas ad-hoc o resolver ambigÃ¼edades sin lidiar con parsing manual de PDFs o JSON anidado.
- **Query Params:**
  - `sectionId`: ID opcional de la secciÃ³n en Class.
  - `pdfUrl`: URL directa en S3 del PDF del sÃ­labo.
  - `preferRaw`: Si es `true`, fuerza la conversiÃ³n de texto crudo sin estructuraciÃ³n previa (`default: false`).

---

## 4. Calidad de Software, Pruebas y Quality Gate

El proyecto mantiene un rigor de ingenierÃ­a de nivel Staff Engineer:

| Componente | Tipo de Prueba | Cobertura / ValidaciÃ³n | Estado |
| :--- | :--- | :--- | :--- |
| `GradeSimulatorEngineTest` | Unitaria | Casos estÃ¡ndar, cursos con nota ya asegurada (`ALREADY_PASSED`) y metas imposibles (`IMPOSSIBLE`) | âœ… 100% PASS |
| `IcsCalendarExporterTest` | Unitaria | GeneraciÃ³n sintÃ¡ctica RFC 5545, zona horaria `America/Lima`, escape de caracteres y campos VEVENT | âœ… 100% PASS |
| `AcademicTaskServiceTest` | Unitaria / Mockito | RÃºbricas completas, filtros por fecha, resumen de notas y proyecciones | âœ… 100% PASS |
| `AuthControllerTest` | IntegraciÃ³n / WebMvc | Validaciones de payloads, beans de seguridad y flujos de autenticaciÃ³n | âœ… 100% PASS |
| `SyllabusFormulaParserTest`| Unitaria | ExtracciÃ³n de ponderaciones porcentuales y normalizaciÃ³n a 100% | âœ… 100% PASS |
| **Suite de PenetraciÃ³n IDOR** | End-to-End Live | VerificaciÃ³n de 403 Forbidden al intentar consultar perfiles ajenos con JWT vÃ¡lido | âœ… 100% PASS |
| **Suite Completa End-to-End**| End-to-End Live | ValidaciÃ³n contra servidores activos de UTP (153 sesiones, 142 actividades, 6 cursos con fÃ³rmulas) | âœ… 100% PASS |

---

## 5. GuÃ­a de IntegraciÃ³n para Agentes y LLMs

> ðŸ“˜ **EspecificaciÃ³n TÃ©cnica Completa:** Consulta la guÃ­a compacta de referencia tÃ©cnica y payloads en [docs/ENDPOINTS.md](docs/ENDPOINTS.md).

Si estÃ¡s desarrollando un bot, agente de IA o microservicio backend que consuma esta API:

1. **Flujo de AutenticaciÃ³n:**
   - Realizar `POST /auth/login` con el usuario institucional del alumno.
   - Guardar el `token` devuelto. Utilizar siempre el header `Authorization: Bearer <token>` en todas las llamadas subsiguientes (o aprovechar el fallback en memoria del gateway).
2. **Contexto de Respuestas:**
   - Todas las respuestas exitosas devuelven la envoltura estÃ¡ndar:
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
   - `400 Bad Request`: ParÃ¡metros de periodo o formato invÃ¡lidos.

---

## 6. Despliegue y EjecuciÃ³n Local

### Prerrequisitos
- JDK 17 o superior
- ConexiÃ³n a Internet (para conectores externos UTP)

### Comandos de CompilaciÃ³n y EjecuciÃ³n
```bash
# Compilar cÃ³digo y pruebas
./mvnw clean test-compile

# Ejecutar pruebas unitarias del Quality Gate
./mvnw test "-Dtest=GradeSimulatorEngineTest,IcsCalendarExporterTest,AcademicTaskServiceTest,AuthControllerTest,SyllabusFormulaParserTest"

# Iniciar servidor local
./mvnw spring-boot:run
```
El servicio estarÃ¡ disponible en `http://localhost:8080/api/v1`.

### Variables de Entorno Obligatorias

| Variable | Obligatoria | Descripcion |
|---|---|---|
| `SUPABASE_URL` | Recomendada | URL del proyecto Supabase donde se cachean los silabos (`official_syllabi`). Sin ella el Cache-Aside degrada a la base local. |
| `SUPABASE_SERVICE_ROLE_KEY` | Recomendada | Service role key de Supabase. Sin ella se **omite** la llamada remota (se evita un 401 por timeout en cada cache-miss). |
| `OPENROUTER_KEYS` | Recomendada | Lista de claves OpenRouter separadas por comas para la flota de modelos `:free`. Sin ellas el pipeline cae al motor determinista regex. |
| `UTP_PAO_USER_MAP` | **Critica** | Correspondencia PAO por alumno: `u23307609=uuid,u21200000=uuid`. El portal UTP+ Class exige la cabecera `user-id` con el **`paoUserId`**, que **no viaja en el JWT de Keycloak** (solo el `sub` de la cuenta SSO). Sin esta correspondencia, `/syllabus/*` y `/tasks/activities` reciben **HTTP 403** del PAO y el alumno obtiene un silabo vacio / cero actividades. Configurable tambien como `utp.pao-user-map` en `application.yml`. |
| `JWT_SECRET` | Opcional | Secreto de firma JWT. **Nunca** debe versionarse con un valor literal. |
| `PORT` | Opcional | Puerto HTTP (por defecto `8080`). |

> Los secretos de Supabase y OpenRouter pueden residir en un `.env` local **no versionado**
> (`.env`, `laboratorio/.env`); el gateway los carga automaticamente en desarrollo.


### Resolucion automatica del `paoUserId` (UTP+ Class / api-pao)

El portal UTP+ Class (`api-pao.utpxpedition.com`) exige la cabecera `user-id` con el
**`paoUserId`** del alumno: una clave interna de PAO (UUID) que **no viaja en el JWT de
Keycloak** (el token solo trae el `sub` de la cuenta SSO) y que PAO rechaza con
`403 Forbidden` si no coincide. El gateway lo resuelve **de forma determinista, sin mapas
ni configuracion por alumno**.

#### Mecanismo (replica del cliente oficial)

El bundle publico de la SPA UTP+Class construye el identificador localmente como un
**UUID v5 (SHA-1)** sobre el correo institucional, con un namespace fijo de la plataforma:

```
paoUserId = uuidv5( "6ba7b810-9dad-11d1-80b4-00c04fd430c8" , email_del_alumno.toUpperCase() )
```

El gateway aplica exactamente la misma regla (`PaoUserIdDeriver`), por lo que el
identificador se obtiene **sin llamadas de red y sin configuracion**, para cualquier
alumno. Vector de referencia verificado en produccion:

```
uuidv5("6ba7b810-9dad-11d1-80b4-00c04fd430c8", "U23307609@UTP.EDU.PE")
  = 4e535263-79a0-5890-ae33-72a7aa0629ab
```

#### Cascada de resolucion

| Paso | Origen | Coste | Automatico |
|---|---|---|---|
| 1 | **Derivacion determinista** `uuidv5(namespace, email)` | local, sin red | **si, siempre** |
| 2 | Claim del token (`paoUserId`, `userId`, `classUserId`) | local | si la plataforma lo expone |
| 3 | Registro en cache del alumno | local | cacheado por alumno |
| 4 | Auto-descubrimiento validado (identificadores del Portal GraphQL, cada uno verificado contra PAO) | 1 sonda HTTP | si el id es derivable |
| 5 | Configuracion externa (`utp.pao-user-map` / `UTP_PAO_USER_MAP`) | local | manual (respaldo) |
| 6 | Fallback al claim `sub` de Keycloak | local | puede ser rechazado por PAO |

- **Aislamiento multi-tenant:** el mapeo `studentCode -> paoUserId` vive en un
  `ConcurrentHashMap` indexado por el codigo de alumno en minusculas. Nunca hay una clave
  compartida ni se presta la identidad de otro alumno.
- **Persistencia:** el mapeo resuelto se guarda en `.pao-user-map.cache.json` (no
  versionado), configurable con `UTP_PAO_USER_CACHE_FILE` para apuntar a un volumen
  persistente.
- **Degradacion elegante:** un `403` de PAO se registra con un `WARN` explicativo y el
  endpoint continua con la cache de base de datos / Supabase, sin lanzar `500`.
- **Acoplamiento:** si UTP cambiase el namespace, la derivacion dejaria de ser valida. Por
  eso el namespace es configurable con `UTP_PAO_UUID_NAMESPACE` y la derivacion es solo el
  primer paso de la cascada: si falla, se prueban los siguientes.
