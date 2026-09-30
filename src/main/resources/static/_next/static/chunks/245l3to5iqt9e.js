(globalThis.TURBOPACK||(globalThis.TURBOPACK=[])).push(["object"==typeof document?document.currentScript:void 0,20198,e=>{"use strict";var a=e.i(43476),s=e.i(71645);let t=({text:e,className:s="",arrowDirection:t="curved-down-left"})=>(0,a.jsxs)("div",{className:`hand-annotation ${s}`,"aria-hidden":"true",children:["curved-up-left"===t&&(0,a.jsx)("svg",{width:"46",height:"34",viewBox:"0 0 46 34",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M40 30C28 28 12 24 8 10M8 10L14 12M8 10L6 18",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})}),"curved-down-right"===t&&(0,a.jsx)("svg",{width:"48",height:"36",viewBox:"0 0 48 36",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M4 6C18 8 36 14 42 28M42 28L36 26M42 28L44 20",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})}),(0,a.jsx)("span",{children:e}),"curved-down-left"===t&&(0,a.jsx)("svg",{width:"50",height:"38",viewBox:"0 0 50 38",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M46 6C34 10 18 20 8 32M8 32L16 32M8 32L8 24",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})}),"curved-up-right"===t&&(0,a.jsx)("svg",{width:"44",height:"32",viewBox:"0 0 44 32",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M6 28C18 24 30 16 38 6M38 6L32 6M38 6L40 14",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})})]}),r=window.location.origin,o=[{id:"syllabus-markdown",category:"SÍLABO & LLM",method:"GET",path:"/api/v1/syllabus/100000I04N/markdown",title:"Sílabo Limpio para LLMs (Token-Saver)",description:"Retorna el contenido curricular formateado en Markdown depurado, eliminando ruido institucional y listo para alimentar agentes de IA.",headers:{Authorization:"Bearer <TOKEN_JWT>",Accept:"text/markdown"},curl:'curl -X GET "'+r+`/api/v1/syllabus/100000I04N/markdown" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}" \\
  -H "Accept: text/markdown"`,typescript:'const response = await fetch("'+r+`/api/v1/syllabus/100000I04N/markdown", {
  headers: {
    "Authorization": \`Bearer \${token}\`,
    "Accept": "text/markdown"
  }
});
const markdownSyllabus = await response.text();`,python:`import requests
res = requests.get(
    "`+r+`/api/v1/syllabus/100000I04N/markdown",
    headers={"Authorization": f"Bearer {token}", "Accept": "text/markdown"}
)
clean_md = res.text`,responseStatus:200,responseContentType:"text/markdown; charset=UTF-8",responseBody:`# S\xcdLABO: INTELIGENCIA ARTIFICIAL (100000I04N)
> Carrera: Ingenier\xeda de Sistemas e Inform\xe1tica
> Cr\xe9ditos: 4.0 | Semanas Oficiales: 18 Semanas

## 1. LOGRO GENERAL DE APRENDIZAJE
Al finalizar el curso, el estudiante dise\xf1a e implementa modelos de agentes inteligentes y algoritmos de Machine Learning.

## 2. SISTEMA DE EVALUACI\xd3N
F\xf3rmula Oficial: Promedio = (PC1 * 0.15) + (PC2 * 0.20) + (EP * 0.25) + (TF * 0.40)

| Evaluaci\xf3n | Semana | Peso | Descripci\xf3n |
|---|---|---|---|
| PC1 | Sem 04 | 15% | B\xfasqueda no informada y heur\xedsticas |
| PC2 | Sem 08 | 20% | Algoritmos gen\xe9ticos y juegos |
| EP  | Sem 10 | 25% | Examen Parcial Te\xf3rico-Pr\xe1ctico |
| TF  | Sem 18 | 40% | Proyecto Aplicado con LLM / Red Neuronal |`},{id:"auth-login",category:"AUTH",method:"POST",path:"/api/v1/auth/login",title:"Inicio de Sesión Institucional (Keycloak SSO)",description:"Autentica al estudiante con su código y contraseña UTP contra el SSO Keycloak institucional. Retorna perfil y par de tokens.",headers:{"Content-Type":"application/json"},body:JSON.stringify({username:"U20215894",password:"••••••••"},null,2),curl:'curl -X POST "'+r+`/api/v1/auth/login" \\
  -H "Content-Type: application/json" \\
  -d '{"username": "U20215894", "password": "tu_password"}'`,typescript:'const res = await fetch("'+r+`/api/v1/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "U20215894", password: "tu_password" })
});
const { data } = await res.json();
console.log("Tokens recibidos:", data.token, data.refreshToken);`,python:`import requests
res = requests.post(
    "`+r+`/api/v1/auth/login",
    json={"username": "U20215894", "password": "tu_password"}
)
data = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Autenticaci\xf3n exitosa",
  "data": {
    "id": "U20215894",
    "studentCode": "U20215894",
    "fullName": "JOAN LAURENTE",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresInMs": 86400000
  }
}`},{id:"auth-refresh",category:"AUTH",method:"POST",path:"/api/v1/auth/refresh",title:"Renovar Sesión (Refresh Token)",description:"Obtiene un nuevo access_token y refresh_token usando el refresh token sin reingresar credenciales.",headers:{"Content-Type":"application/json"},curl:'curl -X POST "'+r+`/api/v1/auth/refresh" \\
  -H "Content-Type: application/json" \\
  -d '{"refreshToken": "eyJhbGciOiJIUzI1Ni..."}'`,typescript:'const res = await fetch("'+r+`/api/v1/auth/refresh", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ refreshToken: currentRefreshToken })
});`,python:`import requests
res = requests.post(
    "`+r+`/api/v1/auth/refresh",
    json={"refreshToken": current_refresh_token}
)`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Sesi\xf3n renovada exitosamente",
  "data": {
    "studentCode": "U20215894",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new..."
  }
}`},{id:"auth-me",category:"AUTH",method:"GET",path:"/api/v1/auth/me",title:"Identidad del Estudiante (Stateless /me)",description:"Valida el token Bearer en memoria e inspecciona el perfil de forma inmediata sin consultar a Keycloak.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/auth/me" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/auth/me", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: profile } = await res.json();`,python:`import requests
res = requests.get(
    "`+r+`/api/v1/auth/me",
    headers={"Authorization": f"Bearer {token}"}
)
profile = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Sesi\xf3n v\xe1lida",
  "data": {
    "studentCode": "U20215894",
    "fullName": "JOAN LAURENTE",
    "roles": ["STUDENT"]
  }
}`},{id:"schedule-get",category:"HORARIO",method:"GET",path:"/api/v1/schedule",title:"Horario Semanal con Aulas y Docentes",description:"Retorna el intervalo de horario con sesiones por día, cursos matriculados, aulas físicas o Zoom y docentes.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/schedule?period=2026+-+Ciclo+2+Agosto" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/schedule", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: schedule } = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/schedule", headers={"Authorization": f"Bearer {token}"})
schedule = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
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
}`},{id:"schedule-ics",category:"HORARIO",method:"GET",path:"/api/v1/schedule/export.ics",title:"Exportación RFC 5545 iCalendar (.ics)",description:"Genera el feed iCalendar estándar para suscripción directa en Google Calendar, Apple Calendar o Microsoft Outlook.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/schedule/export.ics" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}" \\
  -o "horario_utp.ics"`,typescript:'const res = await fetch("'+r+`/api/v1/schedule/export.ics", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const icsText = await res.text();`,python:`import requests
res = requests.get("`+r+`/api/v1/schedule/export.ics", headers={"Authorization": f"Bearer {token}"})
with open("horario.ics", "w", encoding="utf-8") as f:
    f.write(res.text)`,responseStatus:200,responseContentType:"text/calendar; charset=utf-8",responseBody:`BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//SyncUTP Academic Gateway//EN
CALSCALE:GREGORIAN
BEGIN:VEVENT
UID:syncutp-100000I04N-24501@utp.edu.pe
SUMMARY:INTELIGENCIA ARTIFICIAL - LAB-B402
LOCATION:Torre Arequipa, Aula LAB-B402
RRULE:FREQ=WEEKLY;UNTIL=20261220T235959Z
END:VEVENT
END:VCALENDAR`},{id:"courses-summary",category:"NOTAS & FÓRMULAS",method:"GET",path:"/api/v1/courses/summary",title:"Resumen Oficial de Cursos y Notas (Portal UTP)",description:"Consulta la operación GraphQL GetCourseSummary del portal UTP para obtener calificaciones parciales y la fórmula rectora del ciclo.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/courses/summary?periodId=2263" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/courses/summary?periodId=2263", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const summary = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/courses/summary?periodId=2263", headers={"Authorization": f"Bearer {token}"})
summary = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
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
}`},{id:"courses-simulator-single",category:"NOTAS & FÓRMULAS",method:"GET",path:"/api/v1/courses/100000I04N/simulator",title:"Simular Nota Requerida por Curso",description:"Calcula el acumulado actual según la fórmula oficial del curso y proyecta la nota mínima necesaria en las evaluaciones pendientes para aprobar.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/courses/100000I04N/simulator?targetGrade=12.0" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/courses/100000I04N/simulator?targetGrade=12.0", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: simulation } = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/courses/100000I04N/simulator?targetGrade=12.0", headers={"Authorization": f"Bearer {token}"})
sim = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Simulaci\xf3n de calificaci\xf3n calculada exitosamente",
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
}`},{id:"courses-simulator-all",category:"NOTAS & FÓRMULAS",method:"GET",path:"/api/v1/courses/simulator",title:"Simular Notas de Todos los Cursos Matriculados",description:"Proyecta en paralelo para cada curso del ciclo las evaluaciones pendientes, acumulados y promedios necesarios para aprobar.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/courses/simulator?targetGrade=12.0" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/courses/simulator?targetGrade=12.0", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: allSimulations } = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/courses/simulator?targetGrade=12.0", headers={"Authorization": f"Bearer {token}"})
sims = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Simulaci\xf3n de todos los cursos calculada exitosamente",
  "data": [
    { "courseCode": "100000I04N", "courseName": "INTELIGENCIA ARTIFICIAL", "requiredAverage": 10.35, "isPassAchievable": true },
    { "courseCode": "100000SI08", "courseName": "ARQUITECTURA DE SOFTWARE", "requiredAverage": 11.20, "isPassAchievable": true }
  ]
}`},{id:"tasks-activities",category:"TAREAS & RÚBRICAS",method:"GET",path:"/api/v1/tasks/activities",title:"Calendario Unificado de Actividades (Canvas LMS)",description:"Obtiene todas las actividades (foros, tareas, prácticas) programadas en el periodo con sus estados y filtros de semana.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/tasks/activities?status=PENDING&onlyGraded=true" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/tasks/activities?status=PENDING&onlyGraded=true", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: activities } = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/tasks/activities?status=PENDING&onlyGraded=true", headers={"Authorization": f"Bearer {token}"})
activities = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
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
}`},{id:"tasks-detail",category:"TAREAS & RÚBRICAS",method:"GET",path:"/api/v1/tasks/7eddf3c8-98eb-5d6e-a295-856ce4ee6c3c/0c621204-1014-59be-aece-5664fb6e32a0",title:"Detalle de Tarea con Rúbrica Multinivel",description:"Retorna la consigna en Markdown, formato de entrega, intentos permitidos y rúbrica completa con puntajes por criterio.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/tasks/7eddf3c8-98eb-5d6e-a295-856ce4ee6c3c/0c621204-1014-59be-aece-5664fb6e32a0" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/tasks/7eddf3c8-98eb-5d6e-a295-856ce4ee6c3c/0c621204-1014-59be-aece-5664fb6e32a0", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: taskDetail } = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/tasks/7eddf3c8.../0c621204...", headers={"Authorization": f"Bearer {token}"})
task = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "data": {
    "title": "Avance 1: Modelo de Clasificaci\xf3n",
    "descriptionMarkdown": "Desarrollar un pipeline en Python con validaci\xf3n cruzada...",
    "allowedExtensions": [".zip", ".pdf"],
    "rubric": {
      "criteria": [
        { "name": "Preprocesamiento de datos", "maxPoints": 5.0 },
        { "name": "Elecci\xf3n de algoritmos", "maxPoints": 7.0 },
        { "name": "M\xe9tricas y conclusiones", "maxPoints": 8.0 }
      ]
    }
  }
}`},{id:"tasks-upcoming",category:"TAREAS & RÚBRICAS",method:"GET",path:"/api/v1/tasks/upcoming",title:"Próximas Evaluaciones Ponderadas",description:"Filtra cronológicamente las próximas evaluaciones de todos los cursos que impactan el promedio final ponderado.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:'curl -X GET "'+r+`/api/v1/tasks/upcoming?limit=5" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:'const res = await fetch("'+r+`/api/v1/tasks/upcoming?limit=5", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: upcoming } = await res.json();`,python:`import requests
res = requests.get("`+r+`/api/v1/tasks/upcoming?limit=5", headers={"Authorization": f"Bearer {token}"})
upcoming = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Pr\xf3ximas evaluaciones ponderadas obtenidas",
  "data": [
    { "title": "Pr\xe1ctica Calificada 2", "courseName": "INTELIGENCIA ARTIFICIAL", "dueDate": "2026-10-25T23:59:00Z", "weight": "20%" },
    { "title": "Examen Parcial", "courseName": "ARQUITECTURA DE SOFTWARE", "dueDate": "2026-11-02T20:00:00Z", "weight": "25%" }
  ]
}`}],i=["TODOS","AUTH","HORARIO","SÍLABO & LLM","NOTAS & FÓRMULAS","TAREAS & RÚBRICAS"];e.s(["InteractiveSandbox",0,()=>{let[e,r]=(0,s.useState)("TODOS"),[n,c]=(0,s.useState)("syllabus-markdown"),[l,d]=(0,s.useState)("curl"),[p,u]=(0,s.useState)(!1),h="TODOS"===e?o:o.filter(a=>a.category===e),m=o.find(e=>e.id===n)||o[0],T=()=>{switch(l){case"ts":return m.typescript;case"py":return m.python;default:return m.curl}};return(0,a.jsxs)("section",{id:"sandbox",className:"sandbox-section",children:[(0,a.jsx)("div",{className:"section-label",children:"// INTERFACE TESTING & CODE GENERATOR"}),(0,a.jsx)("h2",{className:"section-title",children:"Sandbox Interactivo de Endpoints (20+ Rutas Disponibles)"}),(0,a.jsx)(t,{text:"Filtrado por categorías: Auth, Horarios, Sílabos, Simulador y Canvas",className:"annotation-sandbox",arrowDirection:"curved-down-right"}),(0,a.jsx)("div",{style:{display:"flex",flexWrap:"wrap",gap:"var(--space-2)",marginBottom:"var(--space-4)"},children:i.map(s=>(0,a.jsxs)("button",{onClick:()=>r(s),className:"pill",style:{cursor:"pointer",backgroundColor:e===s?"var(--color-ink-primary)":"var(--color-paper-muted)",color:e===s?"var(--color-paper-base)":"var(--color-ink-primary)",fontWeight:700,padding:"var(--space-2) var(--space-3)"},children:[s," ","TODOS"===s?`(${o.length})`:""]},s))}),(0,a.jsxs)("div",{className:"sandbox-container",children:[(0,a.jsxs)("div",{className:"sandbox-header",children:[(0,a.jsxs)("div",{className:"sandbox-title",children:[(0,a.jsx)("span",{className:`method-tag method-${m.method.toLowerCase()}`,children:m.method}),(0,a.jsx)("span",{children:m.path})]}),(0,a.jsxs)("div",{style:{display:"flex",alignItems:"center",gap:"var(--space-3)"},children:[(0,a.jsx)("span",{style:{color:"#9A9DA8",fontSize:"0.75rem"},children:"Content-Type:"}),(0,a.jsx)("span",{style:{color:"var(--color-paper-base)",fontWeight:700},children:m.responseContentType})]})]}),(0,a.jsxs)("div",{className:"sandbox-body",children:[(0,a.jsx)("div",{className:"endpoint-list",role:"tablist","aria-label":"Lista de endpoints disponibles",children:h.map(e=>{let s=e.id===n;return(0,a.jsxs)("button",{className:`endpoint-btn ${s?"active":""}`,onClick:()=>c(e.id),role:"tab","aria-selected":s,children:[(0,a.jsxs)("div",{style:{display:"flex",alignItems:"center",gap:"var(--space-2)"},children:[(0,a.jsx)("span",{className:`method-tag method-${e.method.toLowerCase()}`,children:e.method}),(0,a.jsx)("span",{className:"endpoint-path",children:e.path})]}),(0,a.jsx)("span",{className:"endpoint-desc-brief",children:e.title})]},e.id)})}),(0,a.jsxs)("div",{className:"playground-view",children:[(0,a.jsxs)("div",{className:"tab-bar",children:[(0,a.jsxs)("div",{className:"tabs-group",role:"tablist",children:[(0,a.jsx)("button",{className:`code-tab ${"curl"===l?"active":""}`,onClick:()=>d("curl"),children:"cURL"}),(0,a.jsx)("button",{className:`code-tab ${"ts"===l?"active":""}`,onClick:()=>d("ts"),children:"TypeScript (Fetch)"}),(0,a.jsx)("button",{className:`code-tab ${"py"===l?"active":""}`,onClick:()=>d("py"),children:"Python (requests)"})]}),(0,a.jsx)("button",{className:"copy-btn",onClick:()=>{navigator.clipboard.writeText(T()),u(!0),setTimeout(()=>u(!1),2e3)},"aria-label":"Copiar código al portapapeles",children:p?"✓ Copiado":"Copiar Snippet"})]}),(0,a.jsx)("pre",{className:"code-display",children:(0,a.jsx)("code",{children:T()})}),(0,a.jsxs)("div",{className:"response-panel",children:[(0,a.jsxs)("div",{className:"response-header",children:[(0,a.jsxs)("div",{children:[(0,a.jsx)("span",{children:"STATUS: "}),(0,a.jsxs)("span",{className:"status-badge",children:[m.responseStatus," OK"]}),(0,a.jsx)("span",{style:{marginLeft:"1rem"},children:"LATENCY: ~85ms"})]}),(0,a.jsx)("span",{children:"RESPONSE PREVIEW"})]}),(0,a.jsx)("pre",{className:"code-display",style:{maxHeight:"240px",overflowY:"auto",color:"#E2DFD8"},children:(0,a.jsx)("code",{children:m.responseBody})})]})]})]})]})]})}],20198)},55364,e=>{"use strict";var a=e.i(43476);e.s(["LLMPromptPipeline",0,()=>{let e=window.location.origin;return(0,a.jsxs)("section",{id:"llm-pipeline",className:"llm-section",children:[(0,a.jsx)("div",{className:"section-label",children:"// AI & AGENTIC INTEGRATION"}),(0,a.jsx)("h2",{className:"section-title",children:"Pipeline Semántico para LLMs y Asistentes RAG"}),(0,a.jsxs)("div",{className:"llm-container",children:[(0,a.jsxs)("div",{className:"llm-card-paper",children:[(0,a.jsx)("div",{style:{fontFamily:"var(--font-mono)",fontSize:"0.8125rem",color:"var(--color-accent)",fontWeight:700,marginBottom:"var(--space-2)"},children:"// PROBLEM VS SOLUTION"}),(0,a.jsx)("h3",{style:{fontFamily:"var(--font-display)",fontSize:"1.35rem",marginBottom:"var(--space-3)"},children:"¿Por qué no pasar el PDF crudo a la IA?"}),(0,a.jsx)("p",{style:{fontSize:"var(--text-small)",color:"var(--color-ink-muted)",lineHeight:1.6},children:"Los PDFs universitarios contienen marcas de agua, encabezados redundantes en cada página, tablas rotas y firmas digitales que saturan la ventana de contexto de los modelos de lenguaje y provocan alucinaciones."}),(0,a.jsxs)("ul",{className:"llm-spec-list",children:[(0,a.jsxs)("li",{className:"llm-spec-item",children:[(0,a.jsx)("span",{className:"llm-spec-bullet",children:"01."}),(0,a.jsxs)("span",{children:[(0,a.jsx)("strong",{children:"Ahorro del 70% de tokens:"})," Se remueve la metadata institucional irrelevante para concentrarse únicamente en unidades, temas y fechas de entrega."]})]}),(0,a.jsxs)("li",{className:"llm-spec-item",children:[(0,a.jsx)("span",{className:"llm-spec-bullet",children:"02."}),(0,a.jsxs)("span",{children:[(0,a.jsx)("strong",{children:"Resiliencia Temporal:"})," Respeta la duración real del curso (ciclo regular de 18 semanas, ciclos de verano acelerados de 8-10 semanas o módulos CGT)."]})]}),(0,a.jsxs)("li",{className:"llm-spec-item",children:[(0,a.jsx)("span",{className:"llm-spec-bullet",children:"03."}),(0,a.jsxs)("span",{children:[(0,a.jsx)("strong",{children:"Parser de Fórmulas Matemáticas:"})," Traduce la sintaxis de evaluación a tablas comprensibles por GPT-4, Claude y Qwen sin ambigüedades."]})]})]})]}),(0,a.jsxs)("div",{style:{backgroundColor:"#191B1F",border:"2px solid var(--color-ink-primary)",padding:"var(--space-5)",color:"#ECEAE4",boxShadow:"var(--shadow-card)"},children:[(0,a.jsxs)("div",{style:{display:"flex",justifyContent:"space-between",alignItems:"center",borderBottom:"1px solid #2B2E36",paddingBottom:"var(--space-2)",marginBottom:"var(--space-3)",fontFamily:"var(--font-mono)",fontSize:"0.75rem",color:"#9A9DA8"},children:[(0,a.jsx)("span",{children:"EJEMPLO DE INTEGRACIÓN CON AGENTE IA"}),(0,a.jsx)("span",{style:{color:"var(--color-accent)",fontWeight:700},children:"PYTHON + OPENROUTER"})]}),(0,a.jsx)("pre",{style:{fontSize:"0.8125rem",lineHeight:1.55,overflowX:"auto",color:"#E0DDD5"},children:(0,a.jsx)("code",{children:`# 1. Obtener s\xedlabo depurado de SyncUTP
res = requests.get(
  f"${e}/api/v1/syllabus/{'{course_code}'}/markdown",
  headers={"Authorization": f"Bearer {'{jwt_token}'}"}
)
clean_markdown = res.text

# 2. Inyectar como System Context al LLM
system_prompt = f"""
Eres un tutor universitario de la UTP. Responde dudas del estudiante
bas\xe1ndote estrictamente en el siguiente s\xedlabo oficial normalizado:

{'{clean_markdown}'}
"""

# 3. Llamada al LLM sin saturar el contexto
completion = client.chat.completions.create(
  model="qwen/qwen3.8-27b:free",
  messages=[
    {"role": "system", "content": system_prompt},
    {"role": "user", "content": "\xbfQu\xe9 temas entran en la PC2 y qu\xe9 semana es?"}
  ]
)`})})]})]})]})}])}]);