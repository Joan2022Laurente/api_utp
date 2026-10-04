# SyncUTP API - Especificación Técnica de Endpoints
> **Nombre del Sistema:** SyncUTP (Pasarela Académica y Core Rector UTP)  
> **Versión:** 2.0.0 (Arquitectura Hexagonal & Pipeline Semántico Adaptativo)  
> **Base URL:** `/api/v1`  
> **Protocolo:** REST / JSON (UTF-8) & Markdown (`text/markdown`)

---

## 1. Protocolo de Comunicación y Formato Global

### 1.1 Envelope Estándar de Respuesta (`ApiResponse<T>`)
Todas las respuestas JSON utilizan el siguiente sobre estándar:
```json
{
  "success": true,
  "message": "Operación exitosa",
  "data": { ... },
  "error": null,
  "timestamp": "2026-09-28T21:30:00Z"
}
```
En caso de error (`4xx` o `5xx`):
```json
{
  "success": false,
  "message": "Descripción del error",
  "data": null,
  "error": "DETALLE_TECNICO_O_VIOLACIONES",
  "timestamp": "2026-09-28T21:30:00Z"
}
```

### 1.2 Autenticación y Token Fallback
- **Header:** `Authorization: Bearer <utp_access_token>`
- **Token Fallback en Memoria:** Tras un `POST /auth/login` exitoso en una instancia activa, el gateway retiene el `lastActiveToken`. Las consultas posteriores (como `/syllabus/{courseCode}`) pueden omitir el header si se ejecutan en la misma sesión.

---

## 2. Endpoints de Sílabos Rectores (`/syllabus`)

### 2.1 `GET /syllabus/{courseCode}`
Recupera el sílabo rector estructurado con evaluaciones normalizadas, fórmulas y cronograma adaptativo.

- **Método:** `GET`
- **Ruta:** `/syllabus/{courseCode}`
- **Parámetros de Ruta:**
  - `courseCode` *(string, requerido)*: Código oficial del curso (ej. `100000ST61`, `100000SI82`).
- **Query Params:**
  - `sectionId` *(string, opcional)*: UUID de sección en Class UTP.
  - `pdfUrl` *(string, opcional)*: URL directa al binario PDF en AWS S3.
- **Headers:**
  - `Authorization: Bearer <token>` *(opcional si hay login previo en memoria)*.

#### Flujo Interno (Cache-Aside + Quality Gate):
1. **Hit en BD:** Si existe en Supabase (`official_syllabi`), retorna inmediatamente el JSON validado.
2. **Miss en BD:**
   - Descarga PDF desde S3/PAO.
   - Pre-procesa a Markdown semántico eliminando ruido y bibliografía para no saturar al LLM.
   - Parsea con la flota de modelos OpenRouter.
   - Valida con `SyllabusDeterministicValidator` (suma de pesos ~100%, rango semanas `[1-24]`, sin semanas fantasma).
   - Fallback automático al motor determinista regex si la IA falla o alucina.
   - Persiste en Supabase DB dedicada y retorna al cliente.

#### Ejemplo de Respuesta (`200 OK`):
```json
{
  "success": true,
  "message": "Operación exitosa",
  "data": {
    "id": "100000ST61",
    "courseCode": "100000ST61",
    "courseName": "DESARROLLO WEB INTEGRADO",
    "credits": 2,
    "weeklyHours": 3,
    "modality": "Presencial",
    "formula": "(20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY",
    "learningGoal": "Al finalizar el curso, el estudiante desarrolla soluciones web integrales mediante frameworks front-end y back-end...",
    "evaluations": [
      {
        "id": "APF1",
        "type": "APF1",
        "description": "AVANCE DE PROYECTO FINAL 1",
        "week": 5,
        "weightPercent": 20,
        "modality": "Grupal",
        "observation": "Evaluación grupal en aula"
      },
      {
        "id": "PROY",
        "type": "PROY",
        "description": "PROYECTO FINAL",
        "week": 18,
        "weightPercent": 40,
        "modality": "Grupal",
        "observation": "Sustentación de proyecto"
      }
    ],
    "weeklySchedule": [
      {
        "week": 1,
        "session": 1,
        "unit": "Unidad 1",
        "topic": "Arquitectura web moderna, Spring Boot y controladores",
        "activities": "Desarrollo de competencias formativas y prácticas de laboratorio.",
        "evaluation": null
      },
      {
        "week": 5,
        "session": 5,
        "unit": "Unidad 2",
        "topic": "Integración de componentes y servicios REST",
        "activities": "Evaluación de primer avance de proyecto.",
        "evaluation": "APF1"
      }
    ]
  }
}
```

> **Nota sobre Duración Dinámica:**  
> La lista `weeklySchedule` refleja la duración **real** del curso:
> - **Ciclo Regular:** 16 a 18 semanas.
> - **Ciclo de Verano:** 8 a 9 semanas reales (sin semanas inventadas 10 a 18).
> - **Cursos Modulares:** 4 a 8 semanas.

---

### 2.2 `GET /syllabus/{courseCode}/markdown`
Entrega el contenido del sílabo oficial limpio y pre-procesado en formato Markdown, optimizado para inyección directa en prompts de LLMs (OpenAI, Gemini, Claude, DeepSeek).

- **Método:** `GET`
- **Ruta:** `/syllabus/{courseCode}/markdown`
- **Content-Type producido:** `text/markdown; charset=utf-8`
- **Query Params:**
  - `preferRaw` *(boolean, default: `false`)*: Si es `true`, extrae texto plano sin estructuración semántica.
- **Respuesta:** Documento Markdown sin cabeceras institucionales repetitivas ni bibliografías innecesarias:
```markdown
# SÍLABO OFICIAL UTP - 100000ST61

## 1. INFORMACIÓN GENERAL
- Carrera: Ingeniería de Sistemas e Informática
- Créditos: 2
- Horas semanales: 3
- Modalidad: Presencial

## 7. SISTEMA DE EVALUACIÓN
Fórmula: (20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY

## 8. CRONOGRAMA DE ACTIVIDADES
### Semana 1
Arquitectura web moderna, Spring Boot y controladores
...
```

---

### 2.3 `GET /syllabus`
Lista todos los sílabos matriculados o almacenados del estudiante autenticado.

- **Método:** `GET`
- **Ruta:** `/syllabus`
- **Headers:** `Authorization: Bearer <token>`
- **Respuesta:** `ApiResponse<List<Syllabus>>` con los sílabos del periodo.

---

### 2.4 `GET /syllabus/raw-text`
Extrae el texto plano crudo directo del binario PDF del sílabo alojado en S3 UTP. Útil para tareas de diagnóstico o procesamiento OCR directo.

- **Método:** `GET`
- **Ruta:** `/syllabus/raw-text?courseCode=100000ST61`
- **Query Params:**
  - `courseCode` *(string, requerido)*: Código del curso.
  - `sectionId` *(string, opcional)*: UUID de sección.
  - `pdfUrl` *(string, opcional)*: URL directa al PDF.
- **Headers:** `Authorization: Bearer <token>`
- **Respuesta:** `ApiResponse<String>` con el volcado de texto plano extraído del PDF.

---

## 3. Endpoints de Autenticación (`/auth`)

### 3.1 `POST /auth/login`
Autentica al estudiante contra el SSO Keycloak institucional de UTP.
- **Método:** `POST`
- **Body:**
  ```json
  {
    "username": "U21200000",
    "password": "tu_password_utp"
  }
  ```
- **Respuesta (`200 OK`):**
  ```json
  {
    "success": true,
    "message": "Autenticación exitosa",
    "data": {
      "studentCode": "U21200000",
      "fullName": "ESTUDIANTE EJEMPLO",
      "email": "u21200000@utp.edu.pe",
      "career": "Ingeniería de Sistemas e Informática",
      "campus": "Lima Centro",
      "currentCycle": 8,
      "accessToken": "eyJhbGciOi...",
      "refreshToken": "eyJhbGciOi...",
      "expiresIn": 1800
    }
  }
  ```

### 3.2 `POST /auth/refresh`
Renueva la sesión activa sin solicitar credenciales.
- **Body:** `{ "refreshToken": "eyJhbGciOi..." }`

### 3.3 `GET /auth/me`
Valida la vigencia del token y extrae los claims del estudiante de forma inmediata.
- **Header:** `Authorization: Bearer <token>`

---

## 4. Endpoints de Horarios (`/schedule`)

### 4.1 `GET /schedule`
Obtiene las sesiones semanales, horarios, aulas y docentes del estudiante.
- **Query Params:**
  - `period` *(string, default: `"2026 - Ciclo 2 Agosto"`)*
- **Header:** `Authorization: Bearer <token>`
- **Respuesta:** `ScheduleInterval` con cursos agrupados por día de la semana.

### 4.2 `GET /schedule/export.ics`
Genera el calendario descargable en formato estándar RFC 5545 compatible con Google Calendar, Apple Calendar y Outlook.
- **Content-Type producido:** `text/calendar; charset=utf-8`

---

## 5. Endpoints de Tareas y Rúbricas (`/tasks`)

### 5.1 `GET /tasks/{sectionId}/{activityId}`
Especificación completa de una tarea con su consigna en Markdown, matriz de rúbrica oficial, código de curso rector y correlación con el sílabo.
- **Respuesta:**
  ```json
  {
    "success": true,
    "data": {
      "id": "0c621204-1014-59be-aece-5664fb6e32a0",
      "title": "Avance de Proyecto Final 1",
      "courseCode": "100000ST61",
      "sectionId": "4853a86a-4e82-5d4f-b14b-10ec8addcc91",
      "descriptionMarkdown": "## Consigna\nDesarrollar la API RESTful...",
      "deliverablesMarkdown": "## Formato de Entrega\nSubir repositorio GitHub...",
      "maxAttempts": 1,
      "submissionTypes": ["online_upload"],
      "evaluationTopScore": 20.0,
      "dueAt": "2026-09-28T23:59:00",
      "evaluationSystem": "AVANCE DE PROYECTO FINAL 1",
      "syllabusCorrelation": {
        "courseCode": "100000ST61",
        "evaluationType": "APF1",
        "weightPercent": 20,
        "evaluationDescription": "AVANCE DE PROYECTO FINAL 1",
        "syllabusWeek": 5,
        "syllabusUnit": "Unidad 2",
        "syllabusTopic": "Controladores REST y Servicios",
        "isSyllabusMatched": true,
        "syllabusUrl": "/api/v1/syllabus/100000ST61",
        "syllabusMarkdownUrl": "/api/v1/syllabus/100000ST61/markdown"
      },
      "gradingRubric": [
        {
          "name": "Arquitectura y Modelado",
          "score": 8.0,
          "levels": [
            { "name": "Excelente", "score": 8.0, "description": "Aplica Clean Architecture..." },
            { "name": "En Proceso", "score": 4.0, "description": "Estructura monolítica con acoplamiento..." }
          ]
        }
      ]
    }
  }
  ```

### 5.2 `GET /tasks/activities`
Calendario unificado de actividades del periodo con soporte de filtros:
- **Query Params:**
  - `intervalMode` (`period`, `week`, `day`)
  - `week` (número de semana, ej. `5`)
  - `status` (`PENDING`, `DELIVERED`, `MISSING`, `PROGRAMMED`)
  - `onlyGraded` (`true` para filtrar únicamente actividades calificadas)
  - `type` (`HOMEWORK`, `FORUM`, `EVALUATION`)

---

## 6. Endpoints de Resumen de Cursos y Simulador de Notas (`/courses`)

### 6.1 `GET /courses/summary`
Consulta las notas parciales registradas en el Portal UTP y sus fórmulas de cálculo.
- **Query Params:** `periodId` (ej. `2263` para ciclo actual).

### 6.2 `GET /courses/{courseCode}/simulator`
Simula la nota mínima requerida en las evaluaciones pendientes para aprobar la asignatura.
- **Query Params:**
  - `targetGrade` *(double, default: `12.0`)*: Nota meta aprobatoria.
  - `periodId` *(string, default: `2263`)*.
- **Respuesta:**
  ```json
  {
    "success": true,
    "data": {
      "courseCode": "100000ST61",
      "courseName": "DESARROLLO WEB INTEGRADO",
      "targetGrade": 12.0,
      "accumulatedScore": 4.0,
      "remainingWeightPercent": 60,
      "requiredAverageGrade": 13.33,
      "isPassingGuaranteed": false,
      "isApprovedAlready": false,
      "pendingEvaluations": ["APF2", "APF3", "PROY"]
    }
  }
  ```

### 6.3 `GET /courses/simulator`
Simulación paralela de nota meta requerida para la totalidad de cursos matriculados en el ciclo.

---

## 7. Guía de Integración Rápida para Clientes / LLMs

1. **Obtener Sílabo para un Chatbot / Copilot:**
   - Si se necesita texto para armar contexto de prompt $\to$ consumir `GET /api/v1/syllabus/{courseCode}/markdown`.
   - Si se necesita pintar cronograma en UI $\to$ consumir `GET /api/v1/syllabus/{courseCode}` y mapear `data.weeklySchedule`.
2. **Adaptación a Ciclos de Verano:**
   - **No asumir `length === 18`**. Iterar dinámicamente sobre `data.weeklySchedule.length` (puede ser 8, 9 o 16 semanas).
3. **Manejo de Errores Deterministas:**
   - Si la IA genera un objeto inconsistente en llamadas de parseo, la API automáticamente activa el motor determinista local asegurando que la respuesta siempre cumpla el Quality Gate.
