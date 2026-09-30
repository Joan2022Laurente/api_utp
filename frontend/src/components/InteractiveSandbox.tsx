"use client";

import React, { useState } from "react";
import { HandwrittenAnnotation } from "./HandwrittenAnnotation";

// Resolves to the current host at runtime: works on localhost AND production
const BASE_URL = typeof window !== "undefined" ? window.location.origin : "";

type EndpointCategory = "TODOS" | "AUTH" | "HORARIO" | "SÍLABO & LLM" | "NOTAS & FÓRMULAS" | "TAREAS & RÚBRICAS";

interface SandboxEndpoint {
  id: string;
  category: EndpointCategory;
  method: "GET" | "POST";
  path: string;
  title: string;
  description: string;
  headers: Record<string, string>;
  body?: string;
  curl: string;
  typescript: string;
  python: string;
  responseStatus: number;
  responseContentType: string;
  responseBody: string;
}

const allEndpoints: SandboxEndpoint[] = [
  // 1. LLM Markdown
  {
    id: "syllabus-markdown",
    category: "SÍLABO & LLM",
    method: "GET",
    path: "/api/v1/syllabus/100000I04N/markdown",
    title: "Sílabo Limpio para LLMs (Token-Saver)",
    description: "Retorna el contenido curricular formateado en Markdown depurado, eliminando ruido institucional y listo para alimentar agentes de IA.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>", "Accept": "text/markdown" },
    curl: `# Paso 1: obtén tu token (reemplaza con tus credenciales UTP)
TOKEN=$(curl -s -X POST "`+BASE_URL+`/api/v1/auth/login" \\
  -H "Content-Type: application/json" \\
  -d '{"username": "U20XXXXXX", "password": "tu_password"}' \\
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# Paso 2: usa el token para obtener el sílabo en Markdown
curl -X GET "`+BASE_URL+`/api/v1/syllabus/100000I04N/markdown" \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Accept: text/markdown"`,
    typescript: `// Paso 1: login para obtener token
const loginRes = await fetch("`+BASE_URL+`/api/v1/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "U20XXXXXX", password: "tu_password" })
});
const { data: { token } } = await loginRes.json();

// Paso 2: obtener sílabo en Markdown
const response = await fetch("`+BASE_URL+`/api/v1/syllabus/100000I04N/markdown", {
  headers: { "Authorization": \`Bearer \${token}\`, "Accept": "text/markdown" }
});
const markdownSyllabus = await response.text();
console.log(markdownSyllabus); // texto .md listo para pasar a un LLM`,
    python: `import requests

# Paso 1: login
login = requests.post(
    "`+BASE_URL+`/api/v1/auth/login",
    json={"username": "U20XXXXXX", "password": "tu_password"}
)
token = login.json()["data"]["token"]

# Paso 2: sílabo en Markdown
res = requests.get(
    "`+BASE_URL+`/api/v1/syllabus/100000I04N/markdown",
    headers={"Authorization": f"Bearer {token}", "Accept": "text/markdown"}
)
print(res.text)  # Markdown limpio listo para RAG/LLM`,
    responseStatus: 200,
    responseContentType: "text/markdown; charset=UTF-8",
    responseBody: `# SÍLABO: INTELIGENCIA ARTIFICIAL (100000I04N)
> Carrera: Ingeniería de Sistemas e Informática
> Créditos: 4.0 | Semanas Oficiales: 18 Semanas

## 1. LOGRO GENERAL DE APRENDIZAJE
Al finalizar el curso, el estudiante diseña e implementa modelos de agentes inteligentes y algoritmos de Machine Learning.

## 2. SISTEMA DE EVALUACIÓN
Fórmula Oficial: Promedio = (PC1 * 0.15) + (PC2 * 0.20) + (EP * 0.25) + (TF * 0.40)

| Evaluación | Semana | Peso | Descripción |
|---|---|---|---|
| PC1 | Sem 04 | 15% | Búsqueda no informada y heurísticas |
| PC2 | Sem 08 | 20% | Algoritmos genéticos y juegos |
| EP  | Sem 10 | 25% | Examen Parcial Teórico-Práctico |
| TF  | Sem 18 | 40% | Proyecto Aplicado con LLM / Red Neuronal |`,
  },

  // 2. Auth Login
  {
    id: "auth-login",
    category: "AUTH",
    method: "POST",
    path: "/api/v1/auth/login",
    title: "Inicio de Sesión Institucional (Keycloak SSO)",
    description: "Autentica al estudiante con su código y contraseña UTP contra el SSO Keycloak institucional. Retorna perfil y par de tokens.",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "U20215894", password: "••••••••" }, null, 2),
    curl: `# Reemplaza U20XXXXXX con tu código UTP real
TOKEN=$(curl -s -X POST "`+BASE_URL+`/api/v1/auth/login" \\
  -H "Content-Type: application/json" \\
  -d '{"username": "U20XXXXXX", "password": "tu_password"}' \\
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

echo $TOKEN  # guarda este valor para los demás endpoints`,
    typescript: `const res = await fetch("`+BASE_URL+`/api/v1/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "U20XXXXXX", password: "tu_password" })
});
const { data } = await res.json();
const token = data.token;          // access_token JWT
const refreshToken = data.refreshToken; // para renovar sin relogin`,
    python: `import requests

res = requests.post(
    "`+BASE_URL+`/api/v1/auth/login",
    json={"username": "U20XXXXXX", "password": "tu_password"}
)
data = res.json()["data"]
token = data["token"]           # access_token JWT
refresh_token = data["refreshToken"]  # para renovar sin relogin`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Autenticación exitosa",
  "data": {
    "id": "U20215894",
    "studentCode": "U20215894",
    "fullName": "JOAN LAURENTE",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresInMs": 86400000
  }
}`,
  },

  // 3. Auth Refresh
  {
    id: "auth-refresh",
    category: "AUTH",
    method: "POST",
    path: "/api/v1/auth/refresh",
    title: "Renovar Sesión (Refresh Token)",
    description: "Obtiene un nuevo access_token y refresh_token usando el refresh token sin reingresar credenciales.",
    headers: { "Content-Type": "application/json" },
    curl: `# Usa el refreshToken obtenido en /auth/login
curl -X POST "`+BASE_URL+`/api/v1/auth/refresh" \\
  -H "Content-Type: application/json" \\
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}"`,
    typescript: `// refreshToken obtenido previamente en /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/auth/refresh", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ refreshToken })
});
const { data } = await res.json();
const newToken = data.token; // nuevo access_token`,
    python: `# refresh_token obtenido previamente en /auth/login
res = requests.post(
    "`+BASE_URL+`/api/v1/auth/refresh",
    json={"refreshToken": refresh_token}
)
new_token = res.json()["data"]["token"]`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Sesión renovada exitosamente",
  "data": {
    "studentCode": "U20215894",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new..."
  }
}`,
  },

  // 4. Auth Me
  {
    id: "auth-me",
    category: "AUTH",
    method: "GET",
    path: "/api/v1/auth/me",
    title: "Identidad del Estudiante (Stateless /me)",
    description: "Valida el token Bearer en memoria e inspecciona el perfil de forma inmediata sin consultar a Keycloak.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
curl -X GET "`+BASE_URL+`/api/v1/auth/me" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/auth/me", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: profile } = await res.json();
console.log(profile.studentCode, profile.fullName);`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/auth/me",
    headers={"Authorization": f"Bearer {token}"}
)
profile = res.json()["data"]
print(profile["studentCode"], profile["fullName"])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Sesión válida",
  "data": {
    "studentCode": "U20215894",
    "fullName": "JOAN LAURENTE",
    "roles": ["STUDENT"]
  }
}`,
  },

  // 5. Schedule Get
  {
    id: "schedule-get",
    category: "HORARIO",
    method: "GET",
    path: "/api/v1/schedule",
    title: "Horario Semanal con Aulas y Docentes",
    description: "Retorna el intervalo de horario con sesiones por día, cursos matriculados, aulas físicas o Zoom y docentes.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
# Si omites ?period, retorna el ciclo vigente automáticamente
curl -X GET "`+BASE_URL+`/api/v1/schedule" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/schedule", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: schedule } = await res.json();
console.log(schedule.sessions); // array de sesiones con aula y docente`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/schedule",
    headers={"Authorization": f"Bearer {token}"}
)
schedule = res.json()["data"]
for s in schedule["sessions"]:
    print(s["dayOfWeek"], s["startTime"], s["courseName"], s["room"])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "data": {
    "studentId": "U20215894",
    "period": "2026 - Ciclo 2 Agosto",
    "sessions": [
      {
        "courseCode": "100000I04N",
        "courseName": "INTELIGENCIA ARTIFICIAL",
        "dayOfWeek": "LUNES",
        "startTime": "18:30",
        "endTime": "21:45",
        "room": "LAB-B402",
        "teacher": "ING. CARLOS MENDOZA"
      }
    ]
  }
}`,
  },

  // 6. Schedule ICS
  {
    id: "schedule-ics",
    category: "HORARIO",
    method: "GET",
    path: "/api/v1/schedule/export.ics",
    title: "Exportación RFC 5545 iCalendar (.ics)",
    description: "Genera el feed iCalendar estándar para suscripción directa en Google Calendar, Apple Calendar o Microsoft Outlook.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
# -o descarga el archivo directamente
curl -X GET "`+BASE_URL+`/api/v1/schedule/export.ics" \\
  -H "Authorization: Bearer $TOKEN" \\
  -o "horario_utp.ics"

# Luego abre horario_utp.ics con Google/Apple/Outlook Calendar`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/schedule/export.ics", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const icsText = await res.text();
// Descargar en el browser:
const blob = new Blob([icsText], { type: "text/calendar" });
const url = URL.createObjectURL(blob);
const a = Object.assign(document.createElement("a"), { href: url, download: "horario.ics" });
a.click();`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/schedule/export.ics",
    headers={"Authorization": f"Bearer {token}"}
)
with open("horario_utp.ics", "w", encoding="utf-8") as f:
    f.write(res.text)
print("Archivo guardado: horario_utp.ics")`,
    responseStatus: 200,
    responseContentType: "text/calendar; charset=utf-8",
    responseBody: `BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//SyncUTP Academic Gateway//EN
CALSCALE:GREGORIAN
BEGIN:VEVENT
UID:syncutp-100000I04N-24501@utp.edu.pe
SUMMARY:INTELIGENCIA ARTIFICIAL - LAB-B402
LOCATION:Torre Arequipa, Aula LAB-B402
RRULE:FREQ=WEEKLY;UNTIL=20261220T235959Z
END:VEVENT
END:VCALENDAR`,
  },

  // 7. Course Summary
  {
    id: "courses-summary",
    category: "NOTAS & FÓRMULAS",
    method: "GET",
    path: "/api/v1/courses/summary",
    title: "Resumen Oficial de Cursos y Notas (Portal UTP)",
    description: "Consulta la operación GraphQL GetCourseSummary del portal UTP para obtener calificaciones parciales y la fórmula rectora del ciclo.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
# periodId es opcional; sin él retorna el ciclo vigente
curl -X GET "`+BASE_URL+`/api/v1/courses/summary" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/courses/summary", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: summary } = await res.json();
summary.courses.forEach(c => console.log(c.courseName, c.evaluations));`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/courses/summary",
    headers={"Authorization": f"Bearer {token}"}
)
for course in res.json()["data"]["courses"]:
    print(course["courseName"], course["evaluations"])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Resumen oficial de cursos y calificaciones obtenido",
  "data": {
    "periodId": "2263",
    "periodName": "2026-2",
    "courses": [
      {
        "courseCode": "100000I04N",
        "courseName": "INTELIGENCIA ARTIFICIAL",
        "formula": "(PC1*0.15) + (PC2*0.20) + (EP*0.25) + (TF*0.40)",
        "evaluations": [
          { "type": "PC1", "grade": 16.5, "weight": 0.15 },
          { "type": "PC2", "grade": 14.0, "weight": 0.20 }
        ]
      }
    ]
  }
}`,
  },

  // 8. Single Course Simulator
  {
    id: "courses-simulator-single",
    category: "NOTAS & FÓRMULAS",
    method: "GET",
    path: "/api/v1/courses/100000I04N/simulator",
    title: "Simular Nota Requerida por Curso",
    description: "Calcula el acumulado actual según la fórmula oficial del curso y proyecta la nota mínima necesaria en las evaluaciones pendientes para aprobar.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
# targetGrade=12.0 es la nota mínima para aprobar (puedes cambiarla)
curl -X GET "`+BASE_URL+`/api/v1/courses/100000I04N/simulator?targetGrade=12.0" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/courses/100000I04N/simulator?targetGrade=12.0", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: sim } = await res.json();
console.log(\`Necesitas \${sim.requiredAverageOnRemaining} en las evaluaciones restantes\`);`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/courses/100000I04N/simulator?targetGrade=12.0",
    headers={"Authorization": f"Bearer {token}"}
)
sim = res.json()["data"]
print(f"Nota requerida en restantes: {sim['requiredAverageOnRemaining']}")`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Simulación de calificación calculada exitosamente",
  "data": {
    "courseCode": "100000I04N",
    "courseName": "INTELIGENCIA ARTIFICIAL",
    "formula": "(PC1*0.15) + (PC2*0.20) + (EP*0.25) + (TF*0.40)",
    "currentAccumulated": 5.275,
    "targetGrade": 12.0,
    "remainingWeight": 0.65,
    "requiredAverageOnRemaining": 10.35,
    "isPassAchievable": true
  }
}`,
  },

  // 9. All Courses Simulator
  {
    id: "courses-simulator-all",
    category: "NOTAS & FÓRMULAS",
    method: "GET",
    path: "/api/v1/courses/simulator",
    title: "Simular Notas de Todos los Cursos Matriculados",
    description: "Proyecta en paralelo para cada curso del ciclo las evaluaciones pendientes, acumulados y promedios necesarios para aprobar.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
curl -X GET "`+BASE_URL+`/api/v1/courses/simulator?targetGrade=12.0" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/courses/simulator?targetGrade=12.0", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: sims } = await res.json();
sims.forEach(s => console.log(s.courseName, "→", s.requiredAverageOnRemaining));`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/courses/simulator?targetGrade=12.0",
    headers={"Authorization": f"Bearer {token}"}
)
for s in res.json()["data"]:
    print(s["courseName"], "→", s["requiredAverageOnRemaining"])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Simulación de todos los cursos calculada exitosamente",
  "data": [
    { "courseCode": "100000I04N", "courseName": "INTELIGENCIA ARTIFICIAL", "requiredAverage": 10.35, "isPassAchievable": true },
    { "courseCode": "100000SI08", "courseName": "ARQUITECTURA DE SOFTWARE", "requiredAverage": 11.20, "isPassAchievable": true }
  ]
}`,
  },

  // 10. Task Calendar Activities
  {
    id: "tasks-activities",
    category: "TAREAS & RÚBRICAS",
    method: "GET",
    path: "/api/v1/tasks/activities",
    title: "Calendario Unificado de Actividades (Canvas LMS)",
    description: "Obtiene todas las actividades (foros, tareas, prácticas) programadas en el periodo con sus estados y filtros de semana.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
# status: PENDING | SUBMITTED | GRADED | ALL
curl -X GET "`+BASE_URL+`/api/v1/tasks/activities?status=PENDING&onlyGraded=true" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/tasks/activities?status=PENDING&onlyGraded=true", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: activities } = await res.json();
activities.forEach(a => console.log(a.title, a.dueDate, a.courseCode));`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/tasks/activities?status=PENDING&onlyGraded=true",
    headers={"Authorization": f"Bearer {token}"}
)
for a in res.json()["data"]:
    print(a["title"], a["dueDate"], a["courseCode"])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Actividades de calendario obtenidas exitosamente",
  "data": [
    {
      "activityId": "0c621204-1014-59be-aece-5664fb6e32a0",
      "title": "Avance 1 de Proyecto Final",
      "courseCode": "100000I04N",
      "dueDate": "2026-10-18T23:59:00Z",
      "status": "PENDING",
      "isGraded": true
    }
  ]
}`,
  },

  // 11. Task Detail with Rubric
  {
    id: "tasks-detail",
    category: "TAREAS & RÚBRICAS",
    method: "GET",
    path: "/api/v1/tasks/7eddf3c8-98eb-5d6e-a295-856ce4ee6c3c/0c621204-1014-59be-aece-5664fb6e32a0",
    title: "Detalle de Tarea con Rúbrica Multinivel",
    description: "Retorna la consigna en Markdown, formato de entrega, intentos permitidos y rúbrica completa con puntajes por criterio.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# courseId y activityId vienen de /api/v1/tasks/activities
# $TOKEN obtenido desde /auth/login
curl -X GET "`+BASE_URL+`/api/v1/tasks/{courseId}/{activityId}" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// courseId y activityId vienen de /api/v1/tasks/activities
// token obtenido desde /auth/login
const { courseId, activityId } = activities[0]; // ejemplo
const res = await fetch("`+BASE_URL+`/api/v1/tasks/\${courseId}/\${activityId}", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: task } = await res.json();
console.log(task.title, task.rubric?.criteria);`,
    python: `# courseId y activityId vienen de /api/v1/tasks/activities
# token obtenido desde /auth/login
course_id = activities[0]["courseId"]
activity_id = activities[0]["activityId"]
res = requests.get(
    f"`+BASE_URL+`/api/v1/tasks/{course_id}/{activity_id}",
    headers={"Authorization": f"Bearer {token}"}
)
task = res.json()["data"]
print(task["title"], [c["name"] for c in task["rubric"]["criteria"]])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "data": {
    "title": "Avance 1: Modelo de Clasificación",
    "descriptionMarkdown": "Desarrollar un pipeline en Python con validación cruzada...",
    "allowedExtensions": [".zip", ".pdf"],
    "rubric": {
      "criteria": [
        { "name": "Preprocesamiento de datos", "maxPoints": 5.0 },
        { "name": "Elección de algoritmos", "maxPoints": 7.0 },
        { "name": "Métricas y conclusiones", "maxPoints": 8.0 }
      ]
    }
  }
}`,
  },

  // 12. Task Upcoming
  {
    id: "tasks-upcoming",
    category: "TAREAS & RÚBRICAS",
    method: "GET",
    path: "/api/v1/tasks/upcoming",
    title: "Próximas Evaluaciones Ponderadas",
    description: "Filtra cronológicamente las próximas evaluaciones de todos los cursos que impactan el promedio final ponderado.",
    headers: { "Authorization": "Bearer <TOKEN_JWT>" },
    curl: `# $TOKEN obtenido desde /auth/login
# limit=5 retorna las 5 próximas evaluaciones ponderadas
curl -X GET "`+BASE_URL+`/api/v1/tasks/upcoming?limit=5" \\
  -H "Authorization: Bearer $TOKEN"`,
    typescript: `// token obtenido desde /auth/login
const res = await fetch("`+BASE_URL+`/api/v1/tasks/upcoming?limit=5", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: upcoming } = await res.json();
upcoming.forEach(e => console.log(e.title, e.dueDate, e.weight));`,
    python: `# token obtenido desde /auth/login
res = requests.get(
    "`+BASE_URL+`/api/v1/tasks/upcoming?limit=5",
    headers={"Authorization": f"Bearer {token}"}
)
for e in res.json()["data"]:
    print(e["title"], e["dueDate"], e["weight"])`,
    responseStatus: 200,
    responseContentType: "application/json",
    responseBody: `{
  "success": true,
  "message": "Próximas evaluaciones ponderadas obtenidas",
  "data": [
    { "title": "Práctica Calificada 2", "courseName": "INTELIGENCIA ARTIFICIAL", "dueDate": "2026-10-25T23:59:00Z", "weight": "20%" },
    { "title": "Examen Parcial", "courseName": "ARQUITECTURA DE SOFTWARE", "dueDate": "2026-11-02T20:00:00Z", "weight": "25%" }
  ]
}`,
  },
];

const categories: EndpointCategory[] = [
  "TODOS",
  "AUTH",
  "HORARIO",
  "SÍLABO & LLM",
  "NOTAS & FÓRMULAS",
  "TAREAS & RÚBRICAS",
];

export const InteractiveSandbox: React.FC = () => {
  const [selectedCategory, setSelectedCategory] = useState<EndpointCategory>("TODOS");
  const [selectedId, setSelectedId] = useState<string>("syllabus-markdown");
  const [activeTab, setActiveTab] = useState<"curl" | "ts" | "py">("curl");
  const [copied, setCopied] = useState<boolean>(false);

  const filteredEndpoints = selectedCategory === "TODOS"
    ? allEndpoints
    : allEndpoints.filter((e) => e.category === selectedCategory);

  const current = allEndpoints.find((e) => e.id === selectedId) || allEndpoints[0];

  const getActiveCode = () => {
    switch (activeTab) {
      case "ts":
        return current.typescript;
      case "py":
        return current.python;
      case "curl":
      default:
        return current.curl;
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(getActiveCode());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section id="sandbox" className="sandbox-section">
      <div className="section-label">// INTERFACE TESTING & CODE GENERATOR</div>
      <h2 className="section-title">Sandbox Interactivo de Endpoints (20+ Rutas Disponibles)</h2>

      <HandwrittenAnnotation
        text="Filtrado por categorías: Auth, Horarios, Sílabos, Simulador y Canvas"
        className="annotation-sandbox"
        arrowDirection="curved-down-right"
      />

      {/* Category Filter Pills */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-2)", marginBottom: "var(--space-4)" }}>
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className="pill"
            style={{
              cursor: "pointer",
              backgroundColor: selectedCategory === cat ? "var(--color-ink-primary)" : "var(--color-paper-muted)",
              color: selectedCategory === cat ? "var(--color-paper-base)" : "var(--color-ink-primary)",
              fontWeight: 700,
              padding: "var(--space-2) var(--space-3)",
            }}
          >
            {cat} {cat === "TODOS" ? `(${allEndpoints.length})` : ""}
          </button>
        ))}
      </div>

      <div className="sandbox-container">
        {/* Top Header */}
        <div className="sandbox-header">
          <div className="sandbox-title">
            <span className={`method-tag method-${current.method.toLowerCase()}`}>
              {current.method}
            </span>
            <span>{current.path}</span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
            <span style={{ color: "#9A9DA8", fontSize: "0.75rem" }}>Content-Type:</span>
            <span style={{ color: "var(--color-paper-base)", fontWeight: 700 }}>
              {current.responseContentType}
            </span>
          </div>
        </div>

        {/* Body Grid: Endpoints list + Code Playground */}
        <div className="sandbox-body">
          {/* Endpoint Selector Menu */}
          <div className="endpoint-list" role="tablist" aria-label="Lista de endpoints disponibles">
            {filteredEndpoints.map((item) => {
              const isActive = item.id === selectedId;
              return (
                <button
                  key={item.id}
                  className={`endpoint-btn ${isActive ? "active" : ""}`}
                  onClick={() => setSelectedId(item.id)}
                  role="tab"
                  aria-selected={isActive}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                    <span className={`method-tag method-${item.method.toLowerCase()}`}>
                      {item.method}
                    </span>
                    <span className="endpoint-path">{item.path}</span>
                  </div>
                  <span className="endpoint-desc-brief">{item.title}</span>
                </button>
              );
            })}
          </div>

          {/* Playground / Output View */}
          <div className="playground-view">
            {/* Tabs Bar */}
            <div className="tab-bar">
              <div className="tabs-group" role="tablist">
                <button
                  className={`code-tab ${activeTab === "curl" ? "active" : ""}`}
                  onClick={() => setActiveTab("curl")}
                >
                  cURL
                </button>
                <button
                  className={`code-tab ${activeTab === "ts" ? "active" : ""}`}
                  onClick={() => setActiveTab("ts")}
                >
                  TypeScript (Fetch)
                </button>
                <button
                  className={`code-tab ${activeTab === "py" ? "active" : ""}`}
                  onClick={() => setActiveTab("py")}
                >
                  Python (requests)
                </button>
              </div>

              <button
                className="copy-btn"
                onClick={handleCopy}
                aria-label="Copiar código al portapapeles"
              >
                {copied ? "✓ Copiado" : "Copiar Snippet"}
              </button>
            </div>

            {/* Code Snippet */}
            <pre className="code-display">
              <code>{getActiveCode()}</code>
            </pre>

            {/* Response Section */}
            <div className="response-panel">
              <div className="response-header">
                <div>
                  <span>STATUS: </span>
                  <span className="status-badge">{current.responseStatus} OK</span>
                  <span style={{ marginLeft: "1rem" }}>LATENCY: ~85ms</span>
                </div>
                <span>RESPONSE PREVIEW</span>
              </div>
              <pre className="code-display" style={{ maxHeight: "240px", overflowY: "auto", color: "#E2DFD8" }}>
                <code>{current.responseBody}</code>
              </pre>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
