# INFORME DE AUDITORÍA, MAPEO Y LIMPIEZA DE DEUDA TÉCNICA — `api-externa-utp`

> **Rol:** Staff Software Engineer & Principal Systems Architect  
> **Fecha:** 2026-11-20  
> **Alcance:** `api-externa-utp` (pasarela académica UTP — **caja negra**, no sujeta a DDD estricto; el objetivo DDD es `horario-inteligente-fullstack`)  
> **Estado del árbol antes/después:** `git status --short` → sin cambios. **Este informe es el único archivo creado. No se modificó código fuente.**  
> **Restricción operativa:** todo el trabajo queda confinado en local. **Prohibido `git push` a `origin` ni a `heroku`** (detalle en §7).

---

## 0. Resumen ejecutivo

| Dimensión | Hallazgo |
|---|---|
| Arquitectura | Hexagonal real y consistente: 7 controladores → 5 puertos IN → servicios de aplicación → puertos OUT → 1 adaptador de pasarela + 3 adaptadores de persistencia + Supabase/OpenRouter. |
| Endpoints | **24 handlers mapeados / 23 rutas distintas** bajo el context-path `/api/v1` (`/` tiene dos variantes por `Accept`). Todos los controladores tienen algún test; **5 handlers carecen de test de controlador dedicado**. |
| Suite de pruebas | `./mvnw test` → **219 tests, 0 failures, 0 errors, 8 skipped, BUILD SUCCESS** (29,96 s). **Cero `@Disabled`**; los 8 skips son `Assumptions` por credenciales/PDF locales ausentes. |
| Clase Dios | `UtpPortalGatewayAdapter.java` = **2.646 líneas, 52 métodos, 8 mapas de estado en memoria, ~9 flujos externos distintos**. Confirmado como punto crítico. |
| Fail-Fast | **No existe `fallbackItem`, nota inventada ni lista hardcodeada de calificaciones.** Pero sí hay **fallbacks engañosos**: tenant ID fijo, aula/tipo de aula inferida por nombre de curso, créditos/horas fijos y degradación silenciosa en 3 méthodes. |
| Artefactos | `frontend/` = fuente Next.js 16.3.7 del portal de desarrolladores (no runtime Java), pero su build está **versionado a mano** en `src/main/resources/static`. `laboratorio/` = sandbox ignorado por `.gitignore`, **no runtime**. |
| Documentación | Deriva importante: `README.md` documenta `POST /syllabus/sync` y `GET /tasks/specification` que **no existen**; `application.yml` y `README.md` difieren en `UTP_JWT_MODE` y `UTP_JWT_FAIL_OPEN`. |
| Documento citado | **`UTP_CLASS_API_CHECKPOINT.md` no existe** en el workspace. Se usó como sustituto `laboratorio/REVERSE_ENGINEERING_LOG.md` (Checkpoints 1-7) y `laboratorio/CHECKPOINT_7_MULTI_STUDENT_AUDIT.md`. |

**Veredicto:** la base es sana y compila/testing en verde; es segura para incorporar las 4 capacidades nuevas **solo si primero se ejecuta el plan de desacoplamiento de §5 y se erradican los fallbacks engañosos de §4.3**. Agregar foto CloudFront, asistencia, SP, GPS de 27 edificios y blindaje directamente sobre el adaptador actual lo elevaría por encima de ~3.500 líneas.

---

## 1. Mapa de Endpoints y Servicios Activos

Base URL real: `http://localhost:8080/api/v1` (`server.servlet.context-path=/api/v1`). Estado = **ACTIVO** (enrutado y con test), **ACTIVO-SIN-TEST** (enrutado pero sin test de controlador dedicado), **DOC-OBSOLETA** (documentado pero no existe).

| # | Método | Ruta (`/api/v1` +) | Controlador | DTO entrada → salida | Puerto / servicio consumido | Estado |
|---|---|---|---|---|---|---|
| 1 | `POST` | `/auth/login` | `AuthController` | `LoginRequest` ({username,password} **o** {token}) → `ApiResponse<StudentProfile>` | `AuthenticateStudentUseCase` | ACTIVO — `AuthControllerTest` (9) |
| 2 | `POST` | `/auth/refresh` | `AuthController` | `RefreshTokenRequest` → `ApiResponse<StudentProfile>` | `AuthenticateStudentUseCase` | ACTIVO — `AuthControllerTest` |
| 3 | `GET` | `/auth/me` | `AuthController` | Bearer → `ApiResponse<StudentProfile>` | `AuthenticateStudentUseCase.authenticateWithToken` | ACTIVO — `AuthControllerTest` |
| 4 | `GET` | `/auth/profile/{id}` | `AuthController` | Bearer + `id` → `ApiResponse<StudentProfile>` | `AuthenticateStudentUseCase.getProfile` + guarda IDOR | ACTIVO — `AuthControllerTest` (incluye 403 a víctima) |
| 5 | `GET` | `/schedule` | `ScheduleController` | `studentId`, `period`, Bearer → `ApiResponse<ScheduleInterval>` | `ScheduleServicePort.getStudentSchedule` | ACTIVO — `ScheduleControllerFailFastTest` (9) |
| 6 | `POST` | `/schedule/sync` | `ScheduleController` | `period`, `token`(**obsoleto**), Bearer → `ApiResponse<ScheduleInterval>` | `ScheduleServicePort.syncScheduleFromUtp` | ACTIVO — `ScheduleControllerFailFastTest` |
| 7 | `GET` | `/schedule/export.ics` | `ScheduleController` | Bearer → `text/calendar` (RFC 5545) | `ScheduleServicePort.exportScheduleToIcs` | ACTIVO — `ScheduleControllerFailFastTest` |
| 8 | `GET` | `/courses/summary` | `CourseSummaryController` | `periodId`, Bearer → `ApiResponse<CourseSummaryData>` | `AcademicTaskServicePort.getCourseSummary` | ACTIVO — `CourseSummaryControllerFailFastTest` (6) |
| 9 | `GET` | `/courses/{courseCode}/simulator` | `CourseSummaryController` | `targetGrade`, `periodId`, Bearer → `ApiResponse<GradeSimulationResult>` | `AcademicTaskServicePort.simulateCourseGrade` | ACTIVO — `CourseSummaryControllerFailFastTest` |
| 10 | `GET` | `/courses/simulator` | `CourseSummaryController` | `targetGrade`, `periodId`, Bearer → `ApiResponse<List<GradeSimulationResult>>` | `AcademicTaskServicePort.simulateAllCourses` | ACTIVO — `CourseSummaryControllerFailFastTest` |
| 11 | `GET` | `/tasks/{sectionId}/{activityId}` | `TaskController` | Bearer → `ApiResponse<TaskSpecification>` | `AcademicTaskServicePort.getTaskDetail` | ACTIVO — `TaskControllerFailFastTest` (6) |
| 12 | `GET` | `/tasks/activities` | `TaskController` | `dateToQuery`, `intervalMode`, `week`, `status`, `onlyGraded`, `type`, Bearer → `ApiResponse<List<AcademicActivity>>` | `AcademicTaskServicePort.getCalendarActivitiesFiltered` | ACTIVO — `TaskControllerFailFastTest` |
| 13 | `GET` | `/tasks/upcoming` | `TaskController` | `limit`, Bearer → `ApiResponse<List<AcademicActivity>>` | `AcademicTaskServicePort.getUpcomingEvaluations` | ACTIVO — `TaskControllerFailFastTest` |
| 14 | `GET` | `/syllabus` | `SyllabusController` | `studentId`, `scope`, `period`, Bearer **solo si `scope=student`** → `ApiResponse<List<Syllabus>>` | `SyllabusServicePort.getAllSyllabiForStudent` + `ScheduleServicePort.getStudentSchedule` | ACTIVO — `SyllabusCatalogControllerTest` (4, **mal nombrada**, ver §3) |
| 15 | `GET` | `/syllabus/{courseCode}` | `SyllabusController` | `sectionId`, `pdfUrl`, Bearer opcional → `ApiResponse<Syllabus>` | `SyllabusServicePort.getSyllabus` | ACTIVO — `SyllabusControllerAuthorizationTest` (5) |
| 16 | `GET` | `/syllabus/raw-text` | `SyllabusController` | `courseCode`, `sectionId`, `pdfUrl` → `ApiResponse<String>` | `SyllabusServicePort.fetchRawSyllabusText` | **ACTIVO-SIN-TEST** |
| 17 | `GET` | `/syllabus/{courseCode}/markdown` | `SyllabusController` | `sectionId`, `pdfUrl`, `preferRaw` → `text/markdown` | `SyllabusServicePort.getSyllabusAsMarkdown` | **ACTIVO-SIN-TEST** (sí cubierto por tests de servicio/exportador) |
| 18 | `GET` | `/syllabus/markdown` | `SyllabusController` | `courseCode`, ... → delega en #17 | `SyllabusServicePort.getSyllabusAsMarkdown` | **ACTIVO-SIN-TEST** + **ausente del índice de `/`** |
| 19 | `GET` | `/sections/{sectionId}/full` | `SectionController` | Bearer → `ApiResponse<JsonNode>` | `AcademicTaskServicePort.getSectionFull` | ACTIVO — `SectionControllerFailFastTest` (4) |
| 20 | `GET` | `/tasks/sections/{sectionId}/full` | `SectionController` | Alias de #19 | ídem | ACTIVO — `SectionControllerFailFastTest` |
| 21 | `GET` | `/` (`Accept: text/html`) | `RootController` | → `static/index.html` | — | **ACTIVO-SIN-TEST** |
| 22 | `GET` | `/` (`Accept: application/json`) | `RootController` | → mapa de metadata + lista de endpoints | — | ACTIVO — `RootControllerHealthTest` |
| 23 | `GET` | `/health` | `RootController` | → `{"status":"UP"}` | — | ACTIVO — `RootControllerHealthTest` |
| 24 | `GET` | `/health/upstream` | `RootController` | → `ApiResponse<UpstreamHealthDto>` | `UpstreamHealthService.evaluateUpstreamHealth` | ACTIVO — `RootControllerHealthTest`, `UpstreamHealthServiceTest` |

### 1.1 Cadena de dependencias (quién consume qué)

```
Controllers (7)
 ├─ AuthController            → AuthenticateStudentUseCase → Impl → UtpPortalGatewayPort + StudentRepositoryPort
 ├─ ScheduleController        → ScheduleServicePort        → Impl → UtpPortalGatewayPort + ScheduleRepositoryPort
 ├─ CourseSummaryController   ┐
 ├─ TaskController            ├→ AcademicTaskServicePort   → AcademicTaskService → UtpPortalGatewayPort + SyllabusRepositoryPort
 └─ SectionController         ┘
 ├─ SyllabusController        → SyllabusServicePort        → Impl → UtpPortalGatewayPort + SyllabusRepositoryPort
 │                            → ScheduleServicePort        → Impl → UtpPortalGatewayPort + ScheduleRepositoryPort
 └─ RootController            → UpstreamHealthService (Keycloak/JWKS, GraphQL Portal, PAO, Supabase, OpenRouter)
```
`SyllabusServiceImpl` además orquesta `OpenRouterFleetService`, `SyllabusDeterministicValidator`, `SyllabusParserEngine` y al cliente `SupabaseSyllabusClient`.

### 1.2 Endpoints documentados que NO existen (deriva)

| Documento | Ruta anunciada | Realidad |
|---|---|---|
| `README.md` §3 | `POST /syllabus/sync` | **No existe**. `SyllabusController` no tiene `@PostMapping` (quedó el import huérfano, ver §3.1). |
| `README.md` §3 | `GET /tasks/specification?sectionId=&activityId=` | **No existe**. La ruta real es `/tasks/{sectionId}/{activityId}` (#11). |
| `docs/ENDPOINTS.md` §1.2 | "Tras `POST /auth/login` el gateway retiene la sesión y permite omitir el header" | **Contradice la implementación**: `resolveEffectiveToken` (L697) devuelve `""` si no hay token propio; la pasarela es stateless. |
| `RootController` §root JSON | Lista de 19 endpoints | Omite `/sections/*`, `/tasks/sections/*` y `/syllabus/markdown`. |
| `docs/ENDPOINTS.md` | Catálogo de rutas | Omite `/sections/*`, `/tasks/sections/*`, `/health/upstream` y `/syllabus/markdown`. |
| `README.md` §6 vs `application.yml` | `UTP_JWT_MODE` default `off`; `UTP_JWT_FAIL_OPEN` default `true` | Código: **`enforce`** y **`false`** (más seguro). La documentación induce a error de seguridad. |
| `README.md` §env | `UTP_PAO_USER_MAP` "Crítica" | Código: respaldo opcional; la vía principal es uuidv5 determinista (`PaoUserIdDeriver`). |

---

## 2. Análisis del punto crítico: `UtpPortalGatewayAdapter.java`

**Métricas:** 2.646 líneas · 52 métodos · 8 `ConcurrentHashMap`/estados en memoria · 4 `@Value` · 2 constructores · ~40 bloques `catch`.

### 2.1 Acoplamiento identificado (mapa de responsabilidades)

| # | Responsabilidad | Líneas | Acoplamiento externo | Estado en memoria |
|---|---|---|---|---|
| A | **Identidad Keycloak**: `login`, `refreshToken`, `parseProfileFromTokens`, `enrichProfileFromPortal` | 634-910 | 2 endpoints SSO (auth./sso.utp.edu.pe) + GraphQL `GetCourseSummary` para ciclo/campus | — |
| B | **Resolución `paoUserId`**: claims, uuidv5, sondas PAO, invalidación, caché en disco | 158-494, 534-631 | PAO + disco `.pao-user-map.cache.json` | `studentPaoUserMap`, `derivedPaoUserMap`, `invalidatedPaoIds`, `paoDiscoveryAttempts` |
| C | **Horario GraphQL Portal**: `fetchSchedule`, `fetchScheduleFromPortalGraphQL`, `ensureBuildingsLoaded` | 913-1013, 1015-1361 | GraphQL `scheduleByDate` + `getBuildings` (18 barridos/semana) | `buildingCache` |
| D | **Horario PAO / parseo**: `parsePaoCalendarResponse`, `parseTitle`, `getClassroomLocation`, fechas | 496-515, 1508-1729 | REST `/course/student/calendar` | `userCourseRefMap` |
| E | **Sílabos PAO**: `fetchSyllabusPdfText`, `CourseRef`, `populateCourseSectionsFromPao` | 1363-1431, 1433-1498, 1730-1863 | PAO syllabus + S3 (PDFBox) | `userCourseRefMap` |
| F | **Catálogo canónico**: `loadDashboardCourses`, `resolveCourseCode`, `CatalogCode`, normalizadores | 1898-2112 | REST `/learning/student/{id}/dashboard-courses` | `sectionToCourseCodeCache`, `courseNameToCourseCodeCache` |
| G | **Contenido académico**: `fetchSectionFull`, `fetchTaskSpecification`, HTML→Markdown | 2115-2325, 2624-2644 | PAO `/sections/full`, `/homeworks/resume` | — |
| H | **Actividades y calificaciones**: `fetchCalendarActivities`, `calculateCategory/Urgency`, `fetchCourseSummary` | 2328-2622 | PAO `/calendar/activities` + GraphQL `GetCourseSummary` | — |
| I | **Plomería HTTP**: `getPortalEndpoint`, `getPaoEndpoint`, headers repetidos | 123-135 y ~6 métodos | — | — |

**Observación de diseño:** los bloques A, C y H mezclan **dos proveedores distintos** (Portal GraphQL y PAO REST) en un mismo método orquestador (`fetchSchedule` prueba GraphQL y cae a PAO). Es el acoplamiento más peligroso para las nuevas capacidades: cualquier nuevo campo (foto, asistencia, GPS) exigirá tocar este método.

### 2.2 Duplicación interna

- **Tres normalizadores de nombre de curso:** `UtpPortalGatewayAdapter.normalizeCourseName` (L2102), `normalizeSearchKey` (L1500) y `SyllabusController.normalizeCourseName` (L149). Tres semánticas distintas (sin acentos+sin espacios / sin caracteres no alnum / NFD+regex). Riesgo de match inconsistente en #14.
- **Dos `enrichmentStatus` calculados por separado** (L1301-1302 y L1626-1627) con la misma regla `PARTIAL/COMPLETE`.
- **Dos `credits(3)/weeklyHours(4)` fijos** (L1213/1217 y L1589/1592).
- **Dos listas de endpoints SSO** idéntricas en `login` (L640) y `refreshToken` (L714).
- **Cabeceras PAO repetidas** (`transaction-id`, `user-agent`, `origin`, `referer`, `x-tenant-id`) en 6 métodos.
- `resolveCourseCode` (L2062) implementa el método del puerto `UtpPortalGatewayPort` **sin `@Override`**.

---

## 3. Inventario de Deuda y Código Basura

### 3.1 Imports sin uso (escaneo mecánico sobre `src/main/java`)

| Archivo | Import(s) sobrante(s) | Interpretación |
|---|---|---|
| `presentation/controller/SyllabusController.java` | `PostMapping`, `RequestBody` | **Residuo del endpoint `POST /syllabus/sync` eliminado.** Confirmación de que `README.md` está desactualizado. |
| `infrastructure/external/UtpPortalGatewayAdapter.java` | `lombok.RequiredArgsConstructor` | El adaptador usa constructores explícitos; el import nunca se aplica. |
| `application/service/ai/OpenRouterModelSelector.java` | `java.util.Arrays` | Sobrante. |
| `application/service/ai/SyllabusPdfToMarkdownSanitizer.java` | `java.util.regex.Matcher` | Sobrante. |
| `infrastructure/persistence/supabase/SupabaseSyllabusClient.java` | `com.fasterxml.jackson.core.type.TypeReference` | Sobrante. |

### 3.2 Clases / métodos / contratos a limpiar

| Ítem | Ubicación | Acción recomendada | Prioridad |
|---|---|---|---|
| `SyllabusCatalogControllerTest` | `src/test/.../controller/` | **Renombrar** a `SyllabusControllerCatalogTest`. Hoy instancia `SyllabusController` (L41): no existe `SyllabusCatalogController` en el código ni en los tests. El nombre del brief es un fantasma documental. | Media |
| `getClassroomLocation` | Adaptador L508-515 | **Eliminar**. Es un `public static` que inventa `"Laboratorio"`/`"Aula"` por substring del nombre del curso; usar el catálogo real de edificios (§5). | **Alta** |
| `extractTenantIdFromToken` fallback | Adaptador L616-631 | **Eliminar el UUID fijo** `a5f469d2-3c0e-5c68-8d32-5265923a8e40`. Fail-fast si el claim no está. | **Alta** |
| `fetchAndCacheDashboardCourses` | Adaptador L2093-2100 | Declarado "vía legada: degradación silenciosa histórica". Sustituir por propagación tipada o renombrar a `tryRefreshCatalog` con métrica explícita. | **Alta** |
| `populateCourseSectionsFromPao` | Adaptador L1433-1498 | `catch (Exception)` → `log.warn`. Convertir en fallo tipado o quitar el `catch` amplio. | Media |
| `ensureBuildingsLoaded` | Adaptador L1317-1361 | El `catch` deja `buildingCache` vacío y el horario GraphQL sale con edificios en blanco. Fail-fast o `enrichmentStatus=PARTIAL`. | **Alta** |
| `resolveEffectiveToken` | Adaptador L697-706 | Accedido por **reflexión** en `UtpPortalGatewayAdapterTokenIsolationTest:50`. Acoplamiento de test a privado: elevar a package-private o eliminar y usar el token ya normalizado del controlador. | Media |
| `emptyScheduleInterval` → `enrichmentStatus("COMPLETE")` | Adaptador L1001-1013 | Un intervalo vacío legítimo no debería declarar "COMPLETE" sin distinción; usar `"EMPTY"`/`"NO_CLASSES"`. | Media |
| Normalizadores duplicados | L1500, L2102, `SyllabusController:149` | Unificar en `CourseNameNormalizer` en aplicación/dominio. | Media |
| `@Value paoUserMapProperty` | Adaptador L114-115 | Se inicializa por `@PostConstruct`, pero hay un `System.getenv` paralelo. Consolidar en `PaoIdentityProperties`. | Baja |
| `UtpPortalGatewayPort` sin `@Override` | Adaptador L2062 | Añadir `@Override` (o extraer el método al nuevo puerto de catálogo, §5). | Baja |

### 3.3 Artefactos `frontend/` y `laboratorio/`

| Directorio | Clasificación | Evidencia | Recomendación |
|---|---|---|---|
| `frontend/` | **Fuente viva del portal de desarrolladores, NO runtime Java.** Next.js 16.3.7 + React 19; 18 componentes TSX. | `package.json`, `src/app/page.tsx`, `src/components/*`. El `pom.xml` **no** invoca Node/npm. | Mantener como fuente, pero **automatizar la copia del build a `src/main/resources/static`** (hoy es manual) o servirlo como app separada. |
| `src/main/resources/static/` | **Build versionado a mano del frontend** (`index.html`, `404.html`, `_next/`, `__next.__PAGE__.txt`, `__next._full.txt`, `__next._tree.txt`, `index.txt`, fuentes .woff2). | `RootController.indexHtml()` lee `classpath:static/index.html`. Los archivos RSC (`__next.*.txt`) son payloads duplicados del mismo build y pueden quedar obsoletos frente a los chunks hashados. | **No son mocks**: son el runtime de la página `/`. Limpiar los `.txt` RSC intermedios y generar el build en el pipeline; verificar que los hashes de `index.html` coinciden con los chunks presentes. |
| `laboratorio/` | **Sandbox de ingeniería inversa, NO runtime.** 60+ scripts Python, HAR/JSON capturados, `.env`. | `.gitignore` L18 ignora todo el directorio; `README.md` L337 lo menciona como entorno local. | Mantener ignorado. **No incluir en artefactos de release**: contiene payloads reales de alumnos y `laboratorio/.env` con secretos. |
| `target/` | Build outputs de Maven. | Ignorado. | Sin acción. |

### 3.4 Deuda documental

1. `README.md`: eliminar `POST /syllabus/sync` y `GET /tasks/specification`; corregir defaults de JWT.
2. `docs/ENDPOINTS.md`: añadir `/sections/*`, `/health/upstream`, `/syllabus/markdown`; corregir el párrafo de "token fallback en memoria".
3. `RootController` lista de endpoints: añadir los 3 faltantes o generarla desde el `RequestMappingHandlerMapping` (mejor: endpoint `/` autoconsistente).
4. `AGENTS.md` raíz apunta a `PROJECT_CONTEXT.md`, **archivo inexistente** en el workspace → referencia de gobernanza rota.
5. `UTP_CLASS_API_CHECKPOINT.md` (citado en el brief) **no existe**: hay que incorporarlo o declarar `REVERSE_ENGINEERING_LOG.md` como fuente autoritativa.

---

## 4. Auditoría Fail-Fast / Cero Fallbacks Engañosos

### 4.1 Lo que SÍ cumple

- **No hay `fallbackItem`, calificaciones inventadas ni listas hardcodeadas de notas.** Las notas de `fetchCourseSummary` (L2567-2597) se leen del GraphQL `evaluations[].value`; el simulador opera sobre esos valores.
- Errores tipados coherentes: `AuthenticationException` (401), `ResourceNotFoundException` (404), `UpstreamServiceException` (5xx/red) en los 7 flujos principales.
- `isDashboardFailure` (L1886-1890) + `DashboardAuthException/NotFound/Upstream` (L1873-1884) impiden que una caída del catálogo se disfrace de horario vacío. **Cubierto por test** (`dashboardFailureFailsFastInsteadOfEmptySchedule`).
- `fetchTaskSpecification` rechaza 200 con `errors`, 200 sin `data` y 200 sin título en vez de inventar "Asignación" (**3 tests dedicados**).
- Aislamiento multi-tenant: claves `studentCode + ":" + norm`, sin clave global; **test** `noSessionRegistryOrGlobalSharedTokenFieldExists`.
- Firma JWT: `UTP_JWT_MODE=enforce`, `fail-open=false` en `application.yml` (config segura real, aunque `README` diga lo contrario).

### 4.2 Riesgo medio (heurísticas no documentadas)

| Caso | Línea | Detalle |
|---|---|---|
| `isGraded = Double.parseDouble(val) > 1.0` | L2572-2575 | Regla empírica para decidir si una evaluación cuenta; `catch` silencioso. El README muestra `0.02 → isGraded:false`, coherente, pero la regla no está documentada ni testeada. |
| `submissionTypes` por palabras clave | L2226-2236 | Si no aparece "video/url" ni "pdf/archivo", se inyecta `online_upload`. Es un valor por defecto de protocolo, **no** un dato académico; documentar. |
| `evaluationTopScore` default `20.0`, `attempts` default `1`, rubric `score` default `0.0` | L2286, L2305, L2255/2270 | Defaults de esquema. Aceptables si se marcan como "no provisto por PAO", no como dato oficial. |
| `calculateUrgency` catch → `UNKNOWN` | L2465-2467 | Degradación explícita y legítima. |

### 4.3 Violaciones P0/P1 del principio "Fail-Fast & Cero Fallbacks Engañosos"

| # | Ubicación | Valor sintético inyectado | Impacto |
|---|---|---|---|
| **F1** | `extractTenantIdFromToken` L630 | `x-tenant-id: a5f469d2-3c0e-5c68-8d32-5265923a8e40` fijo | Identidad de tenant falsa enviada a PAO en ~6 endpoints. Si el claim falta, debe fallar con 401, no suplantar. |
| **F2** | `getClassroomLocation` L508-515 + uso L1557 | `aula = "Laboratorio"` si el nombre contiene `WEB/CLOUD/PROGRAMACI/SISTEMAS/LAB`, si no `"Aula"`; `pabellon=""`, `campus=""` | **Dato físico fabricado** en `ClassSession.classroom/building` (L1613-1614). El catálogo `getBuildings` sí existe y no se usa aquí. |
| **F3** | `parsePaoCalendarResponse` L1581-1593 y GraphQL L1213/1217 | `credits(3)`, `weeklyHours(4)`, `id("c-"+slug)`, `modality` derivada de `"P"` por defecto (L1547), `floor("")`, `teacher("")`, `environmentType="AULA TEÓRICA"` por defecto | `Course`/`ClassSession` con datos académicos inventados servidos como oficiales. |
| **F4** | `ensureBuildingsLoaded` L1358-1360 | `catch(Exception)` → `buildingCache` vacío | El horario GraphQL se devuelve con edificios en blanco sin marcar `PARTIAL`. Degradación silenciosa. |
| **F5** | `fetchAndCacheDashboardCourses` L2093-2100 | `catch(Exception)` → log | Documentado en el propio código como "degradación silenciosa histórica". Afecta resolución de `courseCode` en sílabos/tareas. |
| **F6** | `populateCourseSectionsFromPao` L1495-1497 | `catch(Exception)` → log | Resolución de URL de sílabo degradada sin señal al cliente. |
| **F7** | `emptyScheduleInterval` L1011 | `enrichmentStatus("COMPLETE")` para intervalo vacío | "COMPLETE" sugiere enriquecimiento exitoso cuando no hay datos. |
| **F8** | `extractUserIdFromToken` L594-595 | Fallback al claim `sub` de Keycloak | El propio README admite que PAO lo rechaza con 403. Debe propagar error de identidad, no reintentar con un ID inválido. |

> **Nota:** no se encontró ninguna inyección de notas, calificaciones ni lista de cursos ficticia comparable a un `fallbackItem`. El problema está en **metadatos académicos/físicos** (aula, créditos, horas, tenant), no en calificaciones.

---

## 5. Plan de Desacoplamiento de `UtpPortalGatewayAdapter.java`

### 5.1 Principio rector

Convertir el adaptador en una **fachada delgada de orquestación (~150-250 líneas)**. Cada componente extraído conserva el `FakeHttpClient` inyectable (ya existe el constructor package-private L105) para prueba unitaria sin red. **Sin cambios de comportamiento en la fase 1** (tests golden-master primero).

### 5.2 Arquitectura objetivo

```
UtpPortalGatewayAdapter  (@Component, fachada)
 ├── KeycloakIdentityClient        → login / refresh / claims / perfil      [A]
 ├── PaoIdentityResolver           → uuidv5 + cascade + caché disco         [B]
 ├── PortalGraphQLClient           → getSchedules / getBuildings / Summary  [C,H]
 ├── PaoCourseCatalogClient        → dashboard-courses + CatalogCode        [F]
 ├── PaoScheduleParser (puro)      → eventos → ScheduleInterval + VT/SP     [C,D]
 ├── BuildingCatalogClient         → catálogo 27 edificios + GPS            [C, NUEVO]
 ├── PaoAcademicContentClient      → sections/full, homeworks, activities   [G,H]
 ├── PaoSyllabusResolver           → URL sílabo + PDFBox                    [E]
 ├── ProfileMediaClient            → foto CloudFront (NUEVO)
 ├── PaoAttendanceClient           → asistencia sesión a sesión (NUEVO)
 └── GatewayHttpSupport            → HttpClient, endpoints, headers, errores [I]

Puertos OUT (dominio):
  UtpPortalGatewayPort (transitorio, se delega)
  ├─ AuthenticationGatewayPort
  ├─ ScheduleGatewayPort
  ├─ CourseCatalogPort
  ├─ AcademicContentGatewayPort
  ├─ BuildingCatalogPort
  ├─ ProfileMediaPort
  └─ AttendanceGatewayPort
```

### 5.3 Mapeo de las nuevas capacidades del checkpoint

| Capacidad nueva | Componente dueño | Regla de blindaje |
|---|---|---|
| **Foto de perfil CloudFront** | `ProfileMediaClient` + `ProfileMediaPort`; DTO `ProfilePhotoDto{url, cdnProvider, expiresAt}` | URL solo desde claim/GraphQL verificado; **nunca** placeholder ni gravatar sintético; firma/expiración si aplica. |
| **Asistencia sesión a sesión** | `PaoAttendanceClient` + modelo `SessionAttendance{sessionId, status, minutes, source}` | Leer metadata real de PAO; si el campo falta → `null`/excepción tipada, nunca `PRESENT` por defecto. |
| **Modalidad Semipresencial `SP`** | Extender `ModalityType` con `SEMIPRESENCIAL`; `ModalityResolver` único | Sustituye la lógica duplicada en L1262, L1570-1592 y L2022; `isHybrid=true`, conserva `classLink` + aula real. Test de normalización `P/R/VT/SP`. |
| **Catálogo 27 edificios con GPS** | `BuildingCatalogClient`; DTO `BuildingDto{id, description, address, lat, lng}` | Extender el query `getBuildings` (L1323-1327) con coordenadas; **eliminar `getClassroomLocation`**; si el edificio no resuelve → `building=null` + `PARTIAL`, nunca "Laboratorio" inventado. |
| **Blindaje de payloads** | `GatewayHttpSupport` + `PayloadContractValidator` | Rechazar `200` con `errors`, `data` ausente o tipo inesperado (ya implementado en G/H, generalizar a C/D/E/F); esquema estricto para los 5 payloads nuevos; **sin defaults para datos oficiales**. |

### 5.4 Secuencia de migración (cada paso con `./mvnw test` en verde)

1. **Caracterizar:** tests golden-master sobre los 7 flujos del adaptador (hoy cubiertos parcialmente por `UtpPortalGatewayAdapterUpstreamErrorTest`, `...CatalogEnrichmentTest`, `...TokenIsolationTest`). Añadir tests para F1-F8 **antes** de tocar código.
2. **Extraer puros (cero riesgo):** `CatalogCode` (ya es `record`, L1898), `ModalityType`, `ParsedTitle`, `parseLocalDateTimeSafe`, `convertHtmlToMarkdown`, `CourseNameNormalizer` (unificar los 3).
3. **Extraer clientes HTTP:** uno por proveedor (Keycloak, Portal GraphQL, PAO), con constructor inyectable y `GatewayHttpSupport` para cabeceras/errores. Extraer `PaoIdentityResolver` con su caché en disco.
4. **Extraer parsers:** `PaoScheduleParser` devuelve `ScheduleInterval` a partir de DTOs crudos; aquí se erradican F2/F3 y se centraliza VT/SP.
5. **Dividir el puerto:** crear los 7 puertos nuevos; `UtpPortalGatewayPort` queda como fachada delegada una versión y se marca `@Deprecated` (no borrar de golpe: rompería controllers y tests).
6. **Incorporar capacidades nuevas** sobre los componentes ya separados; cada una con su propio test de contrato y su propio fixture en `laboratorio/`.
7. **Cerrar deuda:** imports, renombrar `SyllabusCatalogControllerTest`, actualizar `README`/`docs/ENDPOINTS`/índice de `/`, regenerar `static/` desde `frontend/`.

### 5.5 Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Cambio de comportamiento en horario (VT, THP, multi-alumno) | Tests `studentBRealFixtureColdStartRegressionTest` y `dualStudentCrossContaminationRegressionTest` como guardarraíl; ejecutarlos en cada paso. |
| Pérdida de cachés `paoUserId` | Mantener `.pao-user-map.cache.json` y las claves por alumno; test `registryIsIndexedByLowercaseStudentCode`. |
| Firma JWT / tenant | No tocar `TokenSignatureFilter`; solo eliminar el tenant fijo una vez añadido el test F1. |
| Divergencia frontend/static | Generar `static/` en build; no editar a mano chunks hashados. |

---

## 6. Estado de compilación, pruebas y cobertura

### 6.1 Ejecución real

```
./mvnw test
Tests run: 219, Failures: 0, Errors: 0, Skipped: 8
BUILD SUCCESS  (Total time: 29.959 s)
```

**Tests omitidos (8), todos por `Assumptions`, ninguno por `@Disabled`:**

| Test | Skips | Causa |
|---|---|---|
| `RealPdfSyllabusSuiteTest` | 6 | `assumeTrue(Files.exists(path))` — PDFs de sílabo no presentes localmente. |
| `SyllabusPopulationAndAnalysisIntegrationTest` | 1 | `Assumptions` sobre Supabase/OpenRouter. |
| `PaoUserIdLiveResolutionTest` | 1 | `UTP_TEST_PASSWORD` no definida (test en vivo). |

> Los logs muestran `ERROR`/`WARN` esperados de `GlobalExceptionHandler`, `JwtSignatureVerifier` y `TokenSignatureFilter` — son** pruebas de comportamiento negativo**, no fallos.

### 6.2 Cobertura de la pasarela frente a los checkpoints disponibles

> **Limitación:** `UTP_CLASS_API_CHECKPOINT.md` no existe en el workspace. Se evalúa contra `REVERSE_ENGINEERING_LOG.md` (Checkpoints 1-7) y `CHECKPOINT_7_MULTI_STUDENT_AUDIT.md`.

| Edge case documentado | Cobertura de test | Estado |
|---|---|---|
| Checkpoint 1 — dashboard como fuente de `courseCode`, `classNumber`, modalidad | `UtpPortalGatewayAdapterCatalogEnrichmentTest.scheduleIsEnrichedWithCatalogCodeAndTypedModality` | ✅ |
| Checkpoint 2 — JOIN por `sectionId` de `linkCourseClass` |_tests de enriquecimiento con fixture real| ✅ |
| Checkpoint 3 — árbol de sección sin `courseCode` | `fetchSectionFull` cubierto por `SectionControllerFailFastTest` (401/404/flujo) | ✅ parcial (no valida ausencia de `courseCode`) |
| Checkpoint 4 — `P/R/VT`, doble inyección del título como código | `modalityNormalization`, `studentBRealFixtureColdStartRegressionTest` | ✅ |
| Checkpoint 5 — fail-fast ante 5xx del dashboard | `dashboardFailureFailsFastInsteadOfEmptySchedule` | ✅ |
| Checkpoint 6 — VT: cero sesiones en `classes`, curso en `courses` | `UtpPortalGatewayAdapterCatalogEnrichmentTest` + `ae0f329` | ✅ |
| Checkpoint 7 — THP `10000TE103`, NPE en `ConcurrentHashMap`, 57 cursos, multi-alumno | `workshopCourseAndMalformedItemsDoNotCrashSchedule`, `studentBRealFixtureColdStartRegressionTest`, `dualStudentCrossContaminationRegressionTest` | ✅ |
| 401/403/404/5xx/200-con-errors en actividades, resumen, tarea, horario | `UtpPortalGatewayAdapterUpstreamErrorTest` (14 tests) | ✅ |
| Aislamiento de token / sin sesión global | `UtpPortalGatewayAdapterTokenIsolationTest` (4) + `PaoUserIdResolutionTest` (6) | ✅ |
| Derivación uuidv5 real | `PaoUserIdDeriverTest` (6) + `PaoUserIdDeriverRealWorldValidationTest` (2) | ✅ |
| **F1 tenant fijo** | Sin test | ❌ **Gap** |
| **F2 aula "Laboratorio"/"Aula" inferida** | Sin test | ❌ **Gap** |
| **F3 créditos/horas fijos** | Sin test | ❌ **Gap** |
| **F4/F5/F6 degradación silenciosa** | Sin test | ❌ **Gap** |
| **Nueva modalidad `SP`** | Sin test (enum no la contempla) | ❌ **Gap** |
| **Nuevo catálogo GPS 27 edificios** | Sin test (`getBuildings` no cubierto) | ❌ **Gap** |
| **Foto CloudFront / asistencia** | Sin tester (capacidades no implementadas) | ⬜ **Pendiente** |
| Handlers #16, #17, #18 (`raw-text`, markdown x2) | Sin test de controlador (sí de servicio/exportador) | ⚠️ **Gap menor** |

**Conclusión de cobertura:** la suite es robusta en **identidad, catálogo, multi-tenant y mapeo de errores**, pero **no protege los fallbacks engañosos**, que son justamente lo que hay que eliminar antes de añadir capacidades.

---

## 7. Reglas de Restricción (cumplidas)

1. **PROHIBIDO** `git push` a `origin`. Ningún comando de este tipo fue ejecutado.
2. **PROHIBIDO** `git push` a `heroku`, ni `heroku config:set`, ni cualquier despliegue/redespliegue.
3. **Todo el trabajo es local:** único artefacto creado = `INFORME_AUDITORIA_Y_MAPEO_API_EXTERNA.md` en la raíz de `api-externa-utp`.
4. **No se modificó código fuente, configuración, tests ni documentación existente.** `git status --short` permanece limpio.
5. **No se ejecutaron llamadas a servicios UTP reales** salvo las que la propia suite de tests realiza en modo mockeado (`FakeHttpClient`); los tests en vivo se omitieron por `Assumptions`.
6. **Secretos/PII:** no se leyó ni copyó `laboratorio/.env`; los payloads con datos de alumnos se trataron solo como evidencia de esquema.
7. Las recomendaciones de §3 y §5 son **propuestas para validación del equipo**; requieren aprobación explícita antes de cualquier implementación.

---

## 8. Priorización sugerida (para decisión del equipo)

| Orden | Acción | Esfuerzo | Riesgo si no se hace |
|---|---|---|---|
| 1 | Erradicar **F1-F3** (tenant, aula inferida, créditos/horas) con tests de regresión | M | Datos académicos falsos servidos como oficiales |
| 2 | Extraer `BuildingCatalogClient` y eliminar `getClassroomLocation` | M | El catálogo GPS de 27 edificios se montaría sobre un heuristic |
| 3 | Extraer `PaoIdentityResolver`, `KeycloakIdentityClient`, `PortalGraphQLClient` | L | El adaptador supera las 3.000 líneas con las nuevas capacidades |
| 4 | Unificar normalizadores y `ModalityResolver` (+`SP`) | S | Matches inconsistentes entre horario, tareas y sílabos |
| 5 | F4-F8: eliminar degradaciones silenciosas | M | Errores upstream enmascarados como 200 |
| 6 | Cerrar deriva documental y regenerar `static/` | S | Clientes y agentes LLM consumen contratos inexistentes |
| 7 | Añadir `UTP_CLASS_API_CHECKPOINT.md` al repo | S | Trazabilidad imperfecta de los edge cases nuevos |

---

**Firmado:** Auditoría técnica local — 2026-11-20. Estado: **APTO PARA REFACTORIZACIÓN, NO APTO PARA AMPLIACIÓN DIRECTA** sin ejecutar §5.1-§5.4.

---

## Apéndice A — Ejecución de la Fase 2 (2026-11-21)

Tras la aprobación del informe y de la especificación maestra
`UTP_CLASS_API_CHECKPOINT.md`, se ejecutó la **Fase 2: Horario Blindado, Saneamiento y
Desacoplamiento (Strangler)**. Resultado: **BUILD SUCCESS — 228 tests, 0 fallos, 0 errores,
8 omitidos** (219 preexistentes + 9 nuevos).

| Tarea | Implementación |
|---|---|
| Regresiones F1-F3 | `UtpGatewayDefensiveFallbacksTest` (4 tests): tenant sin claim → 401, aula/edificio/docente `null` tipado, créditos/horas `null`, edificio no resuelto conserva `SL04_PACIF`. |
| Sesiones asíncronas | `domain/policy/AsynchronousSessionPolicy.java` + `AsynchronousSessionPolicyTest` (5 tests). Regla absorbida `VT ∨ classType∈{3,4}`. |
| Parser tipado | `ScheduleItemParsingResult` (sealed: `Session`/`AsyncCourseRef`/`Rejected`) + `infrastructure/external/schedule/ScheduleItemParser`. Rechazo explícito de epochs corruptos; `PARTIAL` cuando hay rechazos o edificios no resueltos. |
| AST GraphQL | Query completa de `scheduleByDate` con `classType`, `instructionMode`, `building.address`, `professors`; barrido de 18 semanas sin descartes silenciosos. |
| Ruptura 5 | Filtro de periodos compuesto `{periodo actual, 9999}` en `loadDashboardCourses`; eliminado el flag `active`. |
| Fallback PAO eliminado | `fetchSchedule` ya NO consulta `/course/student/calendar`; propagación honesta de `UpstreamServiceException` → 502. `/calendar/activities` permanece intacto. |
| Edificios | Read-Through Cache contra `getBuildings`; cero tablas estáticas. Id crudo + nombre `null` + `PARTIAL` si no resuelve. |
| Identidad | Eliminadas 4 cachés en memoria, sondas HTTP y `.pao-user-map.cache.json`. `extractUserIdFromToken` deriva por UUIDv5 en 0 ms. `PaoUserIdDeriver.deriveCandidates`/namespace alternativo retirados por código muerto. |
| Catálogo | Regex `(?i)^\d{5,6}[A-Z0-9]{4,5}$`; guarda anti-nulos en `ConcurrentHashMap`. |
| Strangler | `UtpPortalGatewayAdapter` conservado como fachada con su constructor package-private y `FakeHttpClient`. Reducción neta de ~1.200 líneas. |

**Tests migrados (sin pérdida de cobertura):** `PaoUserIdResolutionTest` (6 tests
reescritos para la derivación determinista), fixtures `scheduleByDate` de
`UtpPortalGatewayAdapterCatalogEnrichmentTest` (item VT marcado como `VT`/`classType=3`,
expectativa `PARTIAL` ante edificio no resuelto).

**Restricciones cumplidas:** ningún `git push` a `origin` ni `heroku`; todos los cambios
permanecen en el árbol de trabajo local.

---

## Apéndice B — Auditoría cruzada final contra `UTP_CLASS_API_CHECKPOINT.md` (2026-11-21)

Auditoría punto por punto entre la especificación maestra y el código resultante.
**Resultado de la suite: 233 tests, 0 fallos, 0 errores, 8 omitidos — BUILD SUCCESS.**

### B.1 Reglas verificadas como CUMPLIDAS

| Regla del checkpoint | Evidencia en el código |
|---|---|
| §18.3 — Prohibido usar `/course/student/calendar` como fallback de horario | `fetchSchedule` solo invoca `fetchScheduleFromPortalGraphQL`; fallos → `UpstreamServiceException` (502). `/calendar/activities` intacto. |
| §13 Ruptura 1 — regex `(?i)^\d{5,6}[A-Z0-9]{4,5}$` y guarda anti-nulos | `CatalogCode.REGULAR_PATTERN` + `put` condicionados en `loadDashboardCourses`. |
| §13 Ruptura 5 — conjunto compuesto de periodos, sin flag `active` | `targetPeriods = {currentPeriod, "9999"}` en `fetchScheduleFromPortalGraphQL` y `loadDashboardCourses`. |
| §12 Invariante 1 — `linkCourseClass` como ancla | `SECTION_UUID_PATTERN` en `ScheduleItemParser`. |
| §16 — `getBuildings` con `id/description/address`, sin tablas estáticas | Query y `buildingCache`; id crudo + nombre `null` + `PARTIAL` si no resuelve. |
| §18.1 — `classType` String, absorción booleana | `AsynchronousSessionPolicy` + 5 tests. |
| §19 — derivación UUIDv5 en 0 ms, sin cachés/disco/sondas | `extractUserIdFromToken` + `PaoUserIdDeriver`. |
| §Checkpoint 4 — modalidad institucional del dashboard manda | Refuerzo `dashboardAsync` en `ScheduleItemParser`. |

### B.2 Desviaciones ENCONTRADAS Y CORREGIDAS en esta auditoría

| # | Hallazgo | Corrección | Test |
|---|---|---|---|
| 1 | `extractUserIdFromToken` usaba el claim `email` como semilla; si el JWT trae un alias, el `paoUserId` no coincide con el de la SPA (§19). | La semilla es ahora `UPPER(preferred_username/username) + "@UTP.EDU.PE"`; `email` solo como último respaldo. | `emailAliasDoesNotChangeDerivation` → `4e535263-79a0-5890-ae33-72a7aa0629ab`. |
| 2 | `fetchCalendarActivities` pasaba `courseName` a `resolveCourseCode`, activando el JOIN por nombre prohibido en Ruptura 4. | Se pasa `null`; la resolución es exclusivamente por `sectionId`. | `activitiesNeverJoinByCourseName`. |
| 3 | El parser leía `item.modality.id`, `startTime`, `endTime` y `descAmb`, pero la query no los pedía: fallbacks muertos (Ruptura 2 / §8.8). | La query ahora solicita `modality { id }`, `startTime`, `endTime` y `class.descAmb`; jerarquía `modality.id → instructionMode`. | `checkpointHierarchicalFieldsAreHonored`, `descAmbAndProfessorArrayAreParsed`. |
| 4 | `professors` se asumía objeto; el AST puede devolver lista. | Parser tolerante a array (toma el primero, sin inventar datos). | `descAmbAndProfessorArrayAreParsed`. |
| 5 | `GetCourseSummary` no pedía `approvalStatus` ni `professors` (§8.3). | Añadidos a la query y a `CourseSummaryData.CourseGradeItem`, junto con `weeklyHours` a nivel curso. | Suite existente de simulador/resumen. |
| 6 | Comentario de la query decía "AST oficial completo" siendo una proyección reducida. | Renombrado a "Proyección verificada". | — |

### B.3 Desviaciones RESTANTES (declaradas, no corregidas)

1. **La query de `scheduleByDate` es una proyección reducida**, no el AST literal de §8.8. Faltan `hasCrossing`, `isRescheduled`, `date`, `typeSchedule{id,name}`, `modality{name,location}`, `class{name,desc,hours,isClass,isHybrid,isReprog}` y `building{image,coordinates}`. Esto responde a la instrucción explícita de la Fase 2 de usar la query reducida; **no afecta a la grilla ni a la identidad**, pero impide exponer `coordinates` (GPS) de los 27 edificios. **Pendiente para la fase del catálogo GPS.**
2. **Docente ausente**: el checkpoint Ruptura 2 sugiere `"Docente por asignar"`. La directiva superior de Fase 2 ("Cero Textos Centinela") prevalece: se emite `null`. Decisión deliberada y documentada.
3. **`enrichProfileFromPortal`** usa una proyección mínima de `GetCourseSummary` (solo `campus`/`relativeCycle`) para enriquecer el perfil; no expone `approvalStatus`/`professors`. Es coherente con su responsabilidad y no contradice §8.3, que aplica a `GET /courses/summary` (ya corregido).
4. **Limite de alcance**: no se implementaron foto de perfil CloudFront ni asistencia sesión a sesión, por exclusión explícita de la Fase 2.

### B.4 Veredicto

El código **respeta las reglas estructurales del checkpoint** que caen dentro del alcance de la Fase 2 tras las correcciones B.2. Queda como deuda consciente y declarada la proyección reducida del AST (B.3.1), que debe resolverse cuando se incorpore el catálogo GPS de 27 edificios.

**Restricciones:** ningún `git push` a `origin` ni `heroku`; todo permanece en el árbol de trabajo local.
