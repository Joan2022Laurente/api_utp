# INVENTARIO DETERMINISTA DE ENDPOINTS Y LLAMADAS EN CÓDIGO REAL
> **Documento generado programáticamente por script:** `generate_real_endpoint_inventory.py`  
> **Motor de Análisis:** Lexer con enmascarado de comentarios/strings, balanceo de tokens, resolución transitiva de helpers y call-sites HTTP reales.  
> **Context-Path detectado:** `/api/v1` (de `application.yml`)  
> **Total Endpoints Inbound Detectados:** `21`  
> **Total Call-Sites Outbound Detectados:** `25` métodos de red  
> **Total Fallbacks/URLs Hardcodeadas:** `10` declaraciones  

---

## 1. Tabla Maestra de Endpoints Inbound de la Pasarela

| # | Método | Ruta Runtime | Content-Type | Controlador | Línea | Nombre de Método | Delegación a Puertos / Helpers |
| :-: | :---: | :--- | :--- | :--- | :-: | :--- | :--- |
| 1 | `POST` | `/api/v1/auth/login` | `application/json` | [AuthController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java) | [L37](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L37) | `login()` | authenticateStudentUseCase.authenticateWithCredentials() [AuthenticateStudentUseCase]<br>authenticateStudentUseCase.authenticateWithToken() [AuthenticateStudentUseCase] |
| 2 | `GET` | `/api/v1/auth/me` | `application/json` | [AuthController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java) | [L59](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L59) | `getMe()` | authenticateStudentUseCase.authenticateWithToken() [AuthenticateStudentUseCase] |
| 3 | `GET` | `/api/v1/auth/profile/{id}` | `application/json` | [AuthController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java) | [L73](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L73) | `getProfile()` | authenticateStudentUseCase.authenticateWithToken() [AuthenticateStudentUseCase]<br>authenticateStudentUseCase.getProfile() [AuthenticateStudentUseCase] |
| 4 | `POST` | `/api/v1/auth/refresh` | `application/json` | [AuthController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java) | [L51](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L51) | `refresh()` | authenticateStudentUseCase.refreshToken() [AuthenticateStudentUseCase] |
| 5 | `GET` | `/api/v1/courses/simulator` | `application/json` | [CourseSummaryController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java) | [L64](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java#L64) | `simulateAllCourses()` | academicTaskServicePort.simulateAllCourses() [AcademicTaskServicePort] |
| 6 | `GET` | `/api/v1/courses/summary` | `application/json` | [CourseSummaryController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java) | [L28](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java#L28) | `getCourseSummary()` | academicTaskServicePort.getCourseSummary() [AcademicTaskServicePort] |
| 7 | `GET` | `/api/v1/courses/{courseCode}/simulator` | `application/json` | [CourseSummaryController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java) | [L44](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java#L44) | `simulateCourseGrade()` | academicTaskServicePort.simulateCourseGrade() [AcademicTaskServicePort] |
| 8 | `GET` | `/api/v1` | `application/json` | [RootController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java) | [L25](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java#L25) | `root()` | *Sin delegación a puertos (controlador)* |
| 9 | `GET` | `/api/v1/health` | `application/json` | [RootController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java) | [L61](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java#L61) | `health()` | *Sin delegación a puertos (controlador)* |
| 10 | `GET` | `/api/v1/health/upstream` | `application/json` | [RootController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java) | [L67](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java#L67) | `upstreamHealth()` | upstreamHealthService.evaluateUpstreamHealth() [UpstreamHealthService] |
| 11 | `GET` | `/api/v1/schedule` | `application/json` | [ScheduleController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java) | [L29](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java#L29) | `getSchedule()` | scheduleServicePort.getStudentSchedule() [ScheduleServicePort] |
| 12 | `GET` | `/api/v1/schedule/export.ics` | `text/calendar` | [ScheduleController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java) | [L63](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java#L63) | `exportScheduleIcs()` | scheduleServicePort.exportScheduleToIcs() [ScheduleServicePort] |
| 13 | `POST` | `/api/v1/schedule/sync` | `application/json` | [ScheduleController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java) | [L44](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java#L44) | `syncSchedule()` | scheduleServicePort.syncScheduleFromUtp() [ScheduleServicePort] |
| 14 | `GET` | `/api/v1/sections/{sectionId}/full` | `application/json` | [SectionController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SectionController.java) | [L30](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SectionController.java#L30) | `getSectionFull()` | academicTaskServicePort.getSectionFull() [AcademicTaskServicePort] |
| 15 | `GET` | `/api/v1/syllabus` | `application/json` | [SyllabusController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java) | [L65](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L65) | `getAllSyllabi()` | scheduleServicePort.getStudentSchedule() [ScheduleServicePort] (vía helper enrolledCourses())<br>syllabusServicePort.getAllSyllabiForStudent() [SyllabusServicePort] |
| 16 | `GET` | `/api/v1/syllabus/raw-text` | `application/json` | [SyllabusController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java) | [L167](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L167) | `getRawText()` | syllabusServicePort.fetchRawSyllabusText() [SyllabusServicePort] |
| 17 | `GET` | `/api/v1/syllabus/{courseCode}` | `application/json` | [SyllabusController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java) | [L42](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L42) | `getSyllabus()` | syllabusServicePort.getSyllabus() [SyllabusServicePort] |
| 18 | `GET` | `/api/v1/syllabus/{courseCode}/markdown` | `text/markdown` | [SyllabusController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java) | [L179](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L179) | `getSyllabusMarkdown()` | syllabusServicePort.getSyllabusAsMarkdown() [SyllabusServicePort] |
| 19 | `GET` | `/api/v1/tasks/activities` | `application/json` | [TaskController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java) | [L50](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java#L50) | `getCalendarActivities()` | academicTaskServicePort.getCalendarActivitiesFiltered() [AcademicTaskServicePort] |
| 20 | `GET` | `/api/v1/tasks/upcoming` | `application/json` | [TaskController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java) | [L77](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java#L77) | `getUpcomingEvaluations()` | academicTaskServicePort.getUpcomingEvaluations() [AcademicTaskServicePort] |
| 21 | `GET` | `/api/v1/tasks/{sectionId}/{activityId}` | `application/json` | [TaskController.java](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java) | [L32](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java#L32) | `getTaskDetail()` | academicTaskServicePort.getTaskDetail() [AcademicTaskServicePort] |

---

## 2. Detalle Exhaustivo de Endpoints y Parámetros Estructurados

### Controlador: `AuthController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\AuthController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java)
* **Rutas Base Declaradas:** `['/auth']`
* **Dependencias Inyectadas:**
  * `authenticateStudentUseCase`: `AuthenticateStudentUseCase`

#### `POST /api/v1/auth/login`
* **Método Java:** `ResponseEntity<ApiResponse<StudentProfile>> login()` ([L37](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L37))
* **Descripción Swagger:** *"Iniciar sesión institucional"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `request` | `body` | `LoginRequest` | Sí | - | - |

* **Llamadas a Servicios / Puertos:**
  * `authenticateStudentUseCase.authenticateWithCredentials() [AuthenticateStudentUseCase]`
  * `authenticateStudentUseCase.authenticateWithToken() [AuthenticateStudentUseCase]`

#### `GET /api/v1/auth/me`
* **Método Java:** `ResponseEntity<ApiResponse<StudentProfile>> getMe()` ([L59](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L59))
* **Descripción Swagger:** *"Consultar identidad actual (Stateless / me)"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `Authorization` | `header` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `authenticateStudentUseCase.authenticateWithToken() [AuthenticateStudentUseCase]`

#### `GET /api/v1/auth/profile/{id}`
* **Método Java:** `ResponseEntity<ApiResponse<StudentProfile>> getProfile()` ([L73](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L73))
* **Descripción Swagger:** *"Consultar perfil en repositorio por ID"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `id` | `path` | `String` | Sí | - | - |
  | `Authorization` | `header` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `authenticateStudentUseCase.authenticateWithToken() [AuthenticateStudentUseCase]`
  * `authenticateStudentUseCase.getProfile() [AuthenticateStudentUseCase]`

#### `POST /api/v1/auth/refresh`
* **Método Java:** `ResponseEntity<ApiResponse<StudentProfile>> refresh()` ([L51](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/AuthController.java#L51))
* **Descripción Swagger:** *"Renovar sesión (Refresh Token)"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `request` | `body` | `RefreshTokenRequest` | Sí | - | - |

* **Llamadas a Servicios / Puertos:**
  * `authenticateStudentUseCase.refreshToken() [AuthenticateStudentUseCase]`

### Controlador: `CourseSummaryController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\CourseSummaryController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java)
* **Rutas Base Declaradas:** `['/courses']`
* **Dependencias Inyectadas:**
  * `academicTaskServicePort`: `AcademicTaskServicePort`

#### `GET /api/v1/courses/simulator`
* **Método Java:** `ResponseEntity<ApiResponse<java.util.List<com.utp.horario.domain.model.GradeSimulationResult>>> simulateAllCourses()` ([L64](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java#L64))
* **Descripción Swagger:** *"Simular notas requeridas para todos los cursos matriculados"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `targetGrade` | `query` | `double` | No | `12.0` | Nota objetivo común (por defecto 12.0) |
  | `periodId` | `query` | `String` | No | - | ID del periodo académico en Portal UTP (ej. 2263 para 2026-2). Si se omite, se usa el periodo activo configurado. |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.simulateAllCourses() [AcademicTaskServicePort]`

#### `GET /api/v1/courses/summary`
* **Método Java:** `ResponseEntity<ApiResponse<CourseSummaryData>> getCourseSummary()` ([L28](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java#L28))
* **Descripción Swagger:** *"Obtener resumen oficial de cursos, notas parciales y fórmulas rectoras"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `periodId` | `query` | `String` | No | - | ID del periodo académico en Portal UTP (ej. 2263 para 2026-2). Si se omite, se usa el periodo activo configurado. |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.getCourseSummary() [AcademicTaskServicePort]`

#### `GET /api/v1/courses/{courseCode}/simulator`
* **Método Java:** `ResponseEntity<ApiResponse<com.utp.horario.domain.model.GradeSimulationResult>> simulateCourseGrade()` ([L44](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/CourseSummaryController.java#L44))
* **Descripción Swagger:** *"Simular nota requerida para un curso específico"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `courseCode` | `path` | `String` | Sí | - | Código o parte del nombre del curso (ej. 100000I69N o SERVICIOS CLOUD) |
  | `targetGrade` | `query` | `double` | No | `12.0` | Nota objetivo que se desea alcanzar (por defecto 12.0) |
  | `periodId` | `query` | `String` | No | - | ID del periodo académico en Portal UTP (ej. 2263 para 2026-2). Si se omite, se usa el periodo activo configurado. |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.simulateCourseGrade() [AcademicTaskServicePort]`

### Controlador: `RootController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\RootController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java)
* **Rutas Base Declaradas:** `['']`
* **Dependencias Inyectadas:**
  * `upstreamHealthService`: `UpstreamHealthService`

#### `GET /api/v1`
* **Método Java:** `ResponseEntity<Map<String, Object>> root()` ([L25](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java#L25))
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:** *Sin parámetros*
* **Llamadas a Servicios / Puertos:**
  * *Sin delegación a puertos (ejecución interna en controlador)*

#### `GET /api/v1/health`
* **Método Java:** `ResponseEntity<Map<String, String>> health()` ([L61](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java#L61))
* **Descripción Swagger:** *"Liveness probe básico"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:** *Sin parámetros*
* **Llamadas a Servicios / Puertos:**
  * *Sin delegación a puertos (ejecución interna en controlador)*

#### `GET /api/v1/health/upstream`
* **Método Java:** `ResponseEntity<ApiResponse<UpstreamHealthDto>> upstreamHealth()` ([L67](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/RootController.java#L67))
* **Descripción Swagger:** *"Diagnóstico y salud upstream"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:** *Sin parámetros*
* **Llamadas a Servicios / Puertos:**
  * `upstreamHealthService.evaluateUpstreamHealth() [UpstreamHealthService]`

### Controlador: `ScheduleController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\ScheduleController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java)
* **Rutas Base Declaradas:** `['/schedule']`
* **Dependencias Inyectadas:**
  * `scheduleServicePort`: `ScheduleServicePort`

#### `GET /api/v1/schedule`
* **Método Java:** `ResponseEntity<ApiResponse<ScheduleInterval>> getSchedule()` ([L29](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java#L29))
* **Descripción Swagger:** *"Consultar horario del estudiante"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `Authorization` | `header` | `String` | No | - | - |
  | `studentId` | `query` | `String` | No | - | Identificador opcional del estudiante (en peticiones con token se deriva del JWT) |
  | `period` | `query` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `scheduleServicePort.getStudentSchedule() [ScheduleServicePort]`

#### `GET /api/v1/schedule/export.ics`
* **Método Java:** `ResponseEntity<Object> exportScheduleIcs()` ([L63](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java#L63))
* **Descripción Swagger:** *"Exportar horario en formato estándar iCalendar (.ics)"*
* **Content-Type Emitido (`produces`):** `text/calendar`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `Authorization` | `header` | `String` | No | - | - |
  | `studentId` | `query` | `String` | No | - | Identificador opcional del estudiante (en peticiones con token se deriva del JWT) |
  | `period` | `query` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `scheduleServicePort.exportScheduleToIcs() [ScheduleServicePort]`

#### `POST /api/v1/schedule/sync`
* **Método Java:** `ResponseEntity<ApiResponse<ScheduleInterval>> syncSchedule()` ([L44](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/ScheduleController.java#L44))
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `Authorization` | `header` | `String` | No | - | - |
  | `token` | `query` | `String` | No | - | - |
  | `period` | `query` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `scheduleServicePort.syncScheduleFromUtp() [ScheduleServicePort]`

### Controlador: `SectionController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\SectionController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SectionController.java)
* **Rutas Base Declaradas:** `['/sections']`
* **Dependencias Inyectadas:**
  * `academicTaskServicePort`: `AcademicTaskServicePort`

#### `GET /api/v1/sections/{sectionId}/full`
* **Método Java:** `ResponseEntity<ApiResponse<JsonNode>> getSectionFull()` ([L30](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SectionController.java#L30))
* **Descripción Swagger:** *"Obtener árbol completo de contenidos y actividades de una sección"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `sectionId` | `path` | `String` | Sí | - | Identificador de la sección en UTP+ Class |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.getSectionFull() [AcademicTaskServicePort]`

### Controlador: `SyllabusController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\SyllabusController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java)
* **Rutas Base Declaradas:** `['/syllabus']`
* **Dependencias Inyectadas:**
  * `syllabusServicePort`: `SyllabusServicePort`
  * `scheduleServicePort`: `ScheduleServicePort`

#### `GET /api/v1/syllabus`
* **Método Java:** `ResponseEntity<ApiResponse<List<Syllabus>>> getAllSyllabi()` ([L65](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L65))
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `studentId` | `query` | `String` | No | - | Identificador opcional del estudiante (en peticiones con token se deriva del JWT) |
  | `scope` | `query` | `String` | No | `all` | Alcance del catálogo: 'all' (todos los sílabos) o 'student' (filtrado por matrícula del estudiante) |
  | `period` | `query` | `String` | No | - | Periodo académico (ej. 2026 - Ciclo 2 Agosto). Si se omite, se usa el configurado. |
  | `Authorization` | `header` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `scheduleServicePort.getStudentSchedule() [ScheduleServicePort] (vía helper enrolledCourses())`
  * `syllabusServicePort.getAllSyllabiForStudent() [SyllabusServicePort]`

#### `GET /api/v1/syllabus/raw-text`
* **Método Java:** `ResponseEntity<ApiResponse<String>> getRawText()` ([L167](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L167))
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `courseCode` | `query` | `String` | Sí | - | - |
  | `sectionId` | `query` | `String` | No | - | - |
  | `pdfUrl` | `query` | `String` | No | - | - |
  | `Authorization` | `header` | `String` | No | - | - |

* **Llamadas a Servicios / Puertos:**
  * `syllabusServicePort.fetchRawSyllabusText() [SyllabusServicePort]`

#### `GET /api/v1/syllabus/{courseCode}`
* **Método Java:** `ResponseEntity<ApiResponse<Syllabus>> getSyllabus()` ([L42](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L42))
* **Descripción Swagger:** *"Obtener sílabo estructurado por código de curso"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `courseCode` | `path` | `String` | Sí | - | Código del curso (ej. 100000ST61) |
  | `sectionId` | `query` | `String` | No | - | ID de la sección en Class (opcional) |
  | `pdfUrl` | `query` | `String` | No | - | URL directa del PDF en S3 (opcional) |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `syllabusServicePort.getSyllabus() [SyllabusServicePort]`

#### `GET /api/v1/syllabus/{courseCode}/markdown`
* **Método Java:** `ResponseEntity<String> getSyllabusMarkdown()` ([L179](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/SyllabusController.java#L179))
* **Descripción Swagger:** *"Obtener sílabo en formato Markdown para LLMs y Prompt Engineering"*
* **Content-Type Emitido (`produces`):** `text/markdown`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `courseCode` | `path` | `String` | Sí | - | Código del curso (ej. 100000ST61) |
  | `sectionId` | `query` | `String` | No | - | ID de la sección en Class (opcional) |
  | `pdfUrl` | `query` | `String` | No | - | URL directa del PDF en S3 (opcional) |
  | `preferRaw` | `query` | `boolean` | No | `false` | Si es true, fuerza la conversión de texto crudo sin estructuración previa |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `syllabusServicePort.getSyllabusAsMarkdown() [SyllabusServicePort]`

### Controlador: `TaskController`
* **Archivo Fuente:** [c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp\src\main\java\com\utp\horario\presentation\controller\TaskController.java](file:///c:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java)
* **Rutas Base Declaradas:** `['/tasks']`
* **Dependencias Inyectadas:**
  * `academicTaskServicePort`: `AcademicTaskServicePort`

#### `GET /api/v1/tasks/activities`
* **Método Java:** `ResponseEntity<ApiResponse<List<AcademicActivity>>> getCalendarActivities()` ([L50](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java#L50))
* **Descripción Swagger:** *"Consultar calendario unificado de actividades del ciclo"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `dateToQuery` | `query` | `String` | No | - | Fecha base para consulta (yyyy-MM-dd+00:00:00). Si se omite, se calcula dinámicamente la fecha actual. |
  | `intervalMode` | `query` | `String` | No | `period` | Modo de intervalo ('period', 'week', 'day') |
  | `week` | `query` | `Integer` | No | - | Filtrar por número de semana académica (ej. 7) |
  | `status` | `query` | `String` | No | - | Filtrar por estado ('PENDING', 'SUBMITTED', 'GRADED', 'LATE') |
  | `onlyGraded` | `query` | `Boolean` | No | - | Solo evaluaciones calificadas con peso |
  | `type` | `query` | `String` | No | - | Filtrar por tipo de actividad ('HOMEWORK', 'FORUM', 'EVALUATION') |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.getCalendarActivitiesFiltered() [AcademicTaskServicePort]`

#### `GET /api/v1/tasks/upcoming`
* **Método Java:** `ResponseEntity<ApiResponse<List<AcademicActivity>>> getUpcomingEvaluations()` ([L77](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java#L77))
* **Descripción Swagger:** *"Obtener las próximas evaluaciones ponderadas"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `limit` | `query` | `int` | No | `5` | Cantidad máxima de evaluaciones a retornar |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.getUpcomingEvaluations() [AcademicTaskServicePort]`

#### `GET /api/v1/tasks/{sectionId}/{activityId}`
* **Método Java:** `ResponseEntity<ApiResponse<TaskSpecification>> getTaskDetail()` ([L32](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/presentation/controller/TaskController.java#L32))
* **Descripción Swagger:** *"Obtener especificación detallada de una tarea o evaluación"*
* **Content-Type Emitido (`produces`):** `application/json`
* **Parámetros Estructurados:**

  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |
  | :--- | :---: | :--- | :---: | :---: | :--- |
  | `sectionId` | `path` | `String` | Sí | - | UUID de la sección en UTP+ Class |
  | `activityId` | `path` | `String` | Sí | - | UUID de la actividad o homeworkId |
  | `Authorization` | `header` | `String` | No | - | Token Bearer SSO de UTP |

* **Llamadas a Servicios / Puertos:**
  * `academicTaskServicePort.getTaskDetail() [AcademicTaskServicePort]`

---

## 3. Inventario Programático de Call-Sites Outbound (Red Externa)
> Este inventario lista exclusivamente **métodos de Java con llamadas de red reales** (descartando imports, comentarios y logs).

| Dominio Institucional | Verbos | Clase / Adaptador | Método Java | Línea | Operación / URL / GraphQL Ops |
| :--- | :---: | :--- | :--- | :-: | :--- |
| **AI Gateway / OpenRouter** | `POST` | `OpenRouterFleetService` | `parseSyllabusWithAi()` | [L168](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/ai/OpenRouterFleetService.java#L168) | Endpoints: /chat/completions, https://utp-academic-gateway.local<br>Getters: getApiUrl |
| **AI Gateway / OpenRouter** | `GET` | `OpenRouterModelSelector` | `fetchAndRankModels()` | [L95](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/ai/OpenRouterModelSelector.java#L95) | Endpoints: /models<br>Getters: getApiUrl |
| **AI Gateway / OpenRouter [health probe]** | `GET` | `UpstreamHealthService` | `probeOpenRouter()` | [L462](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/health/UpstreamHealthService.java#L462) | Endpoints: /models<br>Getters: getApiUrl |
| **Canvas PAO REST** | `GET` | `UtpPortalGatewayAdapter` | `fetchSyllabusPdfText()` | [L866](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L866) | Endpoints: /course/student/courses/, /course/student/sections/, /courses/, /sections/, /syllabus, http://, https://, https://class.utp.edu.pe, https://class.utp.edu.pe/ |
| **Canvas PAO REST** | `GET` | `UtpPortalGatewayAdapter` | `loadDashboardCourses()` | [L1064](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L1064) | Endpoints: /dashboard-courses, /learning/student/, https://class.utp.edu.pe, https://class.utp.edu.pe/ |
| **Canvas PAO REST** | `GET` | `UtpPortalGatewayAdapter` | `fetchSectionFull()` | [L1231](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L1231) | Endpoints: /course/student/sections/, /full, https://class.utp.edu.pe, https://class.utp.edu.pe/ |
| **Canvas PAO REST** | `GET` | `UtpPortalGatewayAdapter` | `fetchTaskSpecification()` | [L1291](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L1291) | Endpoints: /course/student/sections/, /homeworks/, /resume, https://class.utp.edu.pe, https://class.utp.edu.pe/ |
| **Canvas PAO REST** | `GET` | `UtpPortalGatewayAdapter` | `fetchCalendarActivities()` | [L1447](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L1447) | Endpoints: /course/student/calendar/activities?userId=, https://class.utp.edu.pe, https://class.utp.edu.pe/ |
| **Canvas PAO REST [health probe]** | `GET` | `UpstreamHealthService` | `probePaoGateway()` | [L318](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/health/UpstreamHealthService.java#L318) | Endpoints: /communication/student/ad/banners<br>Getters: getPaoBaseUrl |
| **Keycloak SSO / OIDC** | `GET` | `JwksKeyProvider` | `downloadKeys()` | [L284](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/security/JwksKeyProvider.java#L284) | *Invocación HTTP directa* |
| **Keycloak SSO / OIDC** | `POST` | `UtpPortalGatewayAdapter` | `login()` | [L250](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L250) | Endpoints: https://auth.utp.edu.pe/realms/Xpedition/protocol/openid-connect/token, https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token |
| **Keycloak SSO / OIDC** | `POST` | `UtpPortalGatewayAdapter` | `refreshToken()` | [L322](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L322) | Endpoints: https://auth.utp.edu.pe/realms/Xpedition/protocol/openid-connect/token, https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token |
| **Keycloak SSO / OIDC [health probe]** | `GET` | `UpstreamHealthService` | `probeSsoDiscovery()` | [L114](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/health/UpstreamHealthService.java#L114) | Endpoints: .well-known/openid-configuration, /.well-known/openid-configuration |
| **Keycloak SSO / OIDC [health probe]** | `GET` | `UpstreamHealthService` | `probeSsoJwks()` | [L187](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/health/UpstreamHealthService.java#L187) | Getters: getJwksUrl |
| **Portal UTP GraphQL** | `POST` | `UtpPortalGatewayAdapter` | `enrichProfileFromPortal()` | [L443](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L443) | GraphQL: GetCourseSummary, getProfile |
| **Portal UTP GraphQL** | `POST` | `UtpPortalGatewayAdapter` | `fetchScheduleFromPortalGraphQL()` | [L644](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L644) | GraphQL: getSchedules<br>Endpoints: https://portal.utp.edu.pe/ |
| **Portal UTP GraphQL** | `POST` | `UtpPortalGatewayAdapter` | `ensureBuildingsLoaded()` | [L780](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L780) | GraphQL: getBuildings<br>Endpoints: https://portal.utp.edu.pe/ |
| **Portal UTP GraphQL** | `POST` | `UtpPortalGatewayAdapter` | `fetchCourseSummary()` | [L1631](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L1631) | GraphQL: GetCourseSummary<br>Endpoints: https://portal.utp.edu.pe, https://portal.utp.edu.pe/ |
| **Portal UTP GraphQL [health probe]** | `POST` | `UpstreamHealthService` | `probePortalGraphql()` | [L265](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/health/UpstreamHealthService.java#L265) | Getters: getPortalUrl |
| **Supabase DB REST** | `GET` | `SupabaseSyllabusClient` | `findByCourseCode()` | [L112](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/persistence/supabase/SupabaseSyllabusClient.java#L112) | Endpoints: /rest/v1/<br>Getters: getUrl |
| **Supabase DB REST** | `GET` | `SupabaseSyllabusClient` | `findByCourseNameInsensitive()` | [L173](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/persistence/supabase/SupabaseSyllabusClient.java#L173) | Endpoints: /rest/v1/<br>Getters: getUrl |
| **Supabase DB REST** | `GET` | `SupabaseSyllabusClient` | `findByNormalizedCourseName()` | [L216](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/persistence/supabase/SupabaseSyllabusClient.java#L216) | Endpoints: /rest/v1/<br>Getters: getUrl |
| **Supabase DB REST** | `GET` | `SupabaseSyllabusClient` | `findAll()` | [L290](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/persistence/supabase/SupabaseSyllabusClient.java#L290) | Endpoints: /rest/v1/<br>Getters: getUrl |
| **Supabase DB REST** | `POST` | `SupabaseSyllabusClient` | `postPayload()` | [L462](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/persistence/supabase/SupabaseSyllabusClient.java#L462) | *Invocación HTTP directa* |
| **Supabase DB REST [health probe]** | `GET` | `UpstreamHealthService` | `probeSupabase()` | [L383](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/application/service/health/UpstreamHealthService.java#L383) | Endpoints: /rest/v1/, rest/v1/<br>Getters: getUrl |

### 3.1 URLs y Endpoints Hardcodeados como Fallback en Fuentes

| Archivo Fuente | Línea | Valor Declarado / Fallback |
| :--- | :-: | :--- |
| `UtpProperties.java` | [L21](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/config/UtpProperties.java#L21) | `https://api-portal.utpxpedition.com/graphql` |
| `UtpProperties.java` | [L26](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/config/UtpProperties.java#L26) | `https://api-pao.utpxpedition.com` |
| `OpenRouterProperties.java` | [L26](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/config/ai/OpenRouterProperties.java#L26) | `https://openrouter.ai/api/v1` |
| `SupabaseProperties.java` | [L22](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/config/supabase/SupabaseProperties.java#L22) | `https://vngkmsmyuuukyzvqbovs.supabase.co` |
| `UtpPortalGatewayAdapter.java` | [L81](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L81) | `${utp.portal-url:https://api-portal.utpxpedition.com/graphql}` |
| `UtpPortalGatewayAdapter.java` | [L84](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/external/UtpPortalGatewayAdapter.java#L84) | `${utp.pao-base-url:https://api-pao.utpxpedition.com}` |
| `JwksKeyProvider.java` | [L148](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/security/JwksKeyProvider.java#L148) | `https://auth.utp.edu.pe/realms/Xpedition` |
| `JwksKeyProvider.java` | [L149](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/security/JwksKeyProvider.java#L149) | `https://sso.utp.edu.pe/auth/realms/Xpedition` |
| `JwtVerificationProperties.java` | [L69](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/security/JwtVerificationProperties.java#L69) | `https://auth.utp.edu.pe/realms/Xpedition` |
| `JwtVerificationProperties.java` | [L72](file:///C:/Program%20Files%20%28x86%29/Traslado/escritorio/AUTODIDACTA/Proyectos/Proyecto%20Horario%20AI%20universidad/api-externa-utp/src/main/java/com/utp/horario/infrastructure/security/JwtVerificationProperties.java#L72) | `https://auth.utp.edu.pe/realms/Xpedition/protocol/openid-connect/certs` |

---

## 4. Configuración Upstream de `application.yml`

| Propiedad Jerárquica | Valor Configurado / Fallback |
| :--- | :--- |
| `ai.openrouter.api-url` | `${OPENROUTER_API_URL:https://openrouter.ai/api/v1}` |
| `ai.openrouter.enabled` | `${OPENROUTER_ENABLED:true}` |
| `ai.openrouter.fallback-models.- google/gemma-4-26b-a4b-it` | `free` |
| `ai.openrouter.fallback-models.- liquid/lfm-2.5-2.6b` | `free` |
| `ai.openrouter.primary-model` | `${OPENROUTER_PRIMARY_MODEL:qwen/qwen3.8-27b:free}` |
| `server.port` | `${PORT:8080}` |
| `server.servlet.context-path` | `/api/v1` |
| `spring.datasource.url` | `jdbc:h2:mem:academicdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` |
| `spring.h2.console.enabled` | `false` |
| `supabase.enabled` | `${SUPABASE_ENABLED:true}` |
| `supabase.url` | `${SUPABASE_URL:https://vngkmsmyuuukyzvqbovs.supabase.co}` |
| `utp.pao-base-url` | `${UTP_PAO_BASE_URL:https://api-pao.utpxpedition.com}` |
| `utp.portal-url` | `${UTP_PORTAL_URL:https://api-portal.utpxpedition.com/graphql}` |
| `utp.security.jwt.issuer` | `${UTP_JWT_ISSUER:https://auth.utp.edu.pe/realms/Xpedition}` |
| `utp.security.jwt.jwks-url` | `${UTP_JWT_JWKS_URL:https://auth.utp.edu.pe/realms/Xpedition/protocol/openid-connect/certs}` |
| `utp.security.rate-limit.enabled` | `${UTP_RATE_LIMIT_ENABLED:true}` |
