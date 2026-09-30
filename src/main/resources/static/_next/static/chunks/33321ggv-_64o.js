(globalThis.TURBOPACK||(globalThis.TURBOPACK=[])).push(["object"==typeof document?document.currentScript:void 0,20198,e=>{"use strict";var a=e.i(43476),t=e.i(71645);let s=({text:e,className:t="",arrowDirection:s="curved-down-left"})=>(0,a.jsxs)("div",{className:`hand-annotation ${t}`,"aria-hidden":"true",children:["curved-up-left"===s&&(0,a.jsx)("svg",{width:"46",height:"34",viewBox:"0 0 46 34",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M40 30C28 28 12 24 8 10M8 10L14 12M8 10L6 18",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})}),"curved-down-right"===s&&(0,a.jsx)("svg",{width:"48",height:"36",viewBox:"0 0 48 36",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M4 6C18 8 36 14 42 28M42 28L36 26M42 28L44 20",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})}),(0,a.jsx)("span",{children:e}),"curved-down-left"===s&&(0,a.jsx)("svg",{width:"50",height:"38",viewBox:"0 0 50 38",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M46 6C34 10 18 20 8 32M8 32L16 32M8 32L8 24",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})}),"curved-up-right"===s&&(0,a.jsx)("svg",{width:"44",height:"32",viewBox:"0 0 44 32",fill:"none",xmlns:"http://www.w3.org/2000/svg",children:(0,a.jsx)("path",{d:"M6 28C18 24 30 16 38 6M38 6L32 6M38 6L40 14",stroke:"var(--color-accent)",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round"})})]}),o=[{id:"syllabus-markdown",method:"GET",path:"/api/v1/syllabus/100000I04N/markdown",title:"Sílabo Limpio para LLMs (Token-Saver)",description:"Retorna el contenido curricular formateado en Markdown depurado, eliminando ruido administrativo y listo para RAG.",headers:{Authorization:"Bearer <TOKEN_JWT>",Accept:"text/markdown"},curl:`curl -X GET "http://localhost:8080/api/v1/syllabus/100000I04N/markdown" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}" \\
  -H "Accept: text/markdown"`,typescript:`// TypeScript / Fetch
const response = await fetch("http://localhost:8080/api/v1/syllabus/100000I04N/markdown", {
  headers: {
    "Authorization": \`Bearer \${token}\`,
    "Accept": "text/markdown"
  }
});
const markdownSyllabus = await response.text();
console.log("Tokens ahorrados vs PDF:", markdownSyllabus.length);`,python:`# Python / Requests
import requests

url = "http://localhost:8080/api/v1/syllabus/100000I04N/markdown"
headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "text/markdown"
}
response = requests.get(url, headers=headers)
syllabus_md = response.text
print(f"Longitud Markdown: {len(syllabus_md)} chars")`,responseStatus:200,responseContentType:"text/markdown; charset=UTF-8",responseBody:`# S\xcdLABO: INTELIGENCIA ARTIFICIAL (100000I04N)
> Carrera: Ingenier\xeda de Sistemas e Inform\xe1tica
> Cr\xe9ditos: 4.0 | Modalidad: Presencial / H\xedbrida
> Semanas Oficiales: 18 Semanas | Temporada: Regular 2026-1

## 1. LOGRO GENERAL DE APRENDIZAJE
Al finalizar el curso, el estudiante dise\xf1a e implementa modelos de agentes inteligentes, \xe1rboles de b\xfasqueda heur\xedstica y algoritmos de Machine Learning supervisado.

## 2. SISTEMA DE EVALUACI\xd3N
F\xf3rmula Oficial: Promedio = (PC1 * 0.15) + (PC2 * 0.20) + (EP * 0.25) + (TF * 0.40)

| Evaluaci\xf3n | Semana | Peso | Descripci\xf3n |
|---|---|---|---|
| PC1 | Sem 04 | 15% | B\xfasqueda no informada y A* |
| PC2 | Sem 08 | 20% | Algoritmos gen\xe9ticos y Minimax |
| EP  | Sem 10 | 25% | Examen Parcial Te\xf3rico-Pr\xe1ctico |
| TF  | Sem 18 | 40% | Proyecto Aplicado con LLM / Red Neuronal |

## 3. UNIDADES DE APRENDIZAJE (SEMANAS)
- **Semana 01 - 04**: Fundamentos de Agentes y B\xfasqueda en Espacios de Estados.
- **Semana 05 - 08**: Razonamiento con Incertidumbre y Juegos con Adversarios.
- **Semana 09 - 13**: Aprendizaje Autom\xe1tico: Clasificaci\xf3n y Regresi\xf3n.
- **Semana 14 - 18**: Redes Neuronales y Procesamiento de Lenguaje Natural (LLMs).`},{id:"auth-login",method:"POST",path:"/api/v1/auth/login",title:"SSO Cookie Exchange (Autenticación)",description:"Intercambia la cookie de sesión del portal UTP por un token JWT con vigencia de 24 horas.",headers:{"Content-Type":"application/json"},body:JSON.stringify({authToken:"utp_sso_session_cookie_payload..."},null,2),curl:`curl -X POST "http://localhost:8080/api/v1/auth/login" \\
  -H "Content-Type: application/json" \\
  -d '{"authToken": "eyJh...utp_cookie_payload"}'`,typescript:`const res = await fetch("http://localhost:8080/api/v1/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ authToken: utpCookieToken })
});
const { data } = await res.json();
const jwtToken = data.token;`,python:`import requests

res = requests.post(
    "http://localhost:8080/api/v1/auth/login",
    json={"authToken": "eyJh...utp_cookie_payload"}
)
jwt_token = res.json()["data"]["token"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Autenticaci\xf3n exitosa",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "studentCode": "U20215894",
    "fullName": "JOAN LAURENTE",
    "expiresInMs": 86400000
  },
  "error": null,
  "timestamp": "2026-09-30T10:00:00Z"
}`},{id:"schedule-get",method:"GET",path:"/api/v1/schedule",title:"Horario Semanal Normalizado",description:"Devuelve todas las clases matriculadas con aulas, pabellón, tipo de sesión y docentes.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:`curl -X GET "http://localhost:8080/api/v1/schedule" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:`const res = await fetch("http://localhost:8080/api/v1/schedule", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: schedule } = await res.json();
console.log("Cursos del lunes:", schedule.filter(c => c.dayOfWeek === "LUNES"));`,python:`import requests

res = requests.get(
    "http://localhost:8080/api/v1/schedule",
    headers={"Authorization": f"Bearer {token}"}
)
courses = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Horario recuperado correctamente",
  "data": [
    {
      "courseCode": "100000I04N",
      "courseName": "INTELIGENCIA ARTIFICIAL",
      "section": "24501",
      "dayOfWeek": "LUNES",
      "startTime": "18:30",
      "endTime": "21:45",
      "room": "LAB-B402",
      "building": "TORRE AREQUIPA",
      "modality": "PRESENCIAL",
      "teacher": "ING. CARLOS MENDOZA"
    },
    {
      "courseCode": "100000SI08",
      "courseName": "ARQUITECTURA DE SOFTWARE",
      "section": "19302",
      "dayOfWeek": "MIERCOLES",
      "startTime": "20:00",
      "endTime": "22:15",
      "room": "VIRTUAL-01",
      "building": "ZOOM",
      "modality": "VIRTUAL_SINCRONO",
      "teacher": "MG. ELENA PACHECO"
    }
  ],
  "error": null,
  "timestamp": "2026-09-30T10:00:00Z"
}`},{id:"grades-simulate",method:"POST",path:"/api/v1/grades/simulate",title:"Simulador de Promedios Ponderados",description:"Calcula el promedio ponderado o la nota necesaria en el examen final según fórmula oficial.",headers:{Authorization:"Bearer <TOKEN_JWT>","Content-Type":"application/json"},body:JSON.stringify({courseCode:"100000I04N",currentGrades:{PC1:16,PC2:14.5,EP:15},targetAverage:14},null,2),curl:`curl -X POST "http://localhost:8080/api/v1/grades/simulate" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}" \\
  -H "Content-Type: application/json" \\
  -d '{"courseCode":"100000I04N","currentGrades":{"PC1":16.0,"PC2":14.5,"EP":15.0},"targetAverage":14.0}'`,typescript:`const res = await fetch("http://localhost:8080/api/v1/grades/simulate", {
  method: "POST",
  headers: {
    "Authorization": \`Bearer \${token}\`,
    "Content-Type": "application/json"
  },
  body: JSON.stringify({
    courseCode: "100000I04N",
    currentGrades: { PC1: 16.0, PC2: 14.5, EP: 15.0 },
    targetAverage: 14.0
  })
});
const simulation = await res.json();`,python:`import requests

res = requests.post(
    "http://localhost:8080/api/v1/grades/simulate",
    headers={"Authorization": f"Bearer {token}"},
    json={
        "courseCode": "100000I04N",
        "currentGrades": {"PC1": 16.0, "PC2": 14.5, "EP": 15.0},
        "targetAverage": 14.0
    }
)
print(res.json()["data"])`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Simulaci\xf3n calculada con \xe9xito",
  "data": {
    "courseCode": "100000I04N",
    "formula": "(PC1*0.15) + (PC2*0.20) + (EP*0.25) + (TF*0.40)",
    "currentAccumulated": 9.05,
    "targetAverage": 14.0,
    "requiredScoreOnRemaining": {
      "evaluation": "TF",
      "weight": 0.40,
      "minimumGradeNeeded": 12.38
    },
    "isPassAchievable": true
  }
}`},{id:"tasks-rubrics",method:"GET",path:"/api/v1/tasks/rubrics",title:"Tareas & Rúbricas Detalladas (Canvas)",description:"Obtiene las entregas pendientes de Canvas LMS desglosando cada criterio de evaluación y puntaje.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:`curl -X GET "http://localhost:8080/api/v1/tasks/rubrics" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}"`,typescript:`const res = await fetch("http://localhost:8080/api/v1/tasks/rubrics", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const { data: assignments } = await res.json();`,python:`import requests

res = requests.get(
    "http://localhost:8080/api/v1/tasks/rubrics",
    headers={"Authorization": f"Bearer {token}"}
)
tasks = res.json()["data"]`,responseStatus:200,responseContentType:"application/json",responseBody:`{
  "success": true,
  "message": "Tareas y r\xfabricas recuperadas",
  "data": [
    {
      "taskId": "canvas_1028394",
      "title": "Avance 1: Arquitectura de Microservicios",
      "courseName": "ARQUITECTURA DE SOFTWARE",
      "dueDate": "2026-10-12T23:59:00Z",
      "pointsPossible": 20.0,
      "rubricCriteria": [
        {
          "id": "crit_1",
          "description": "Diagrama C4 (Contexto y Contenedores)",
          "points": 8.0
        },
        {
          "id": "crit_2",
          "description": "Especificaci\xf3n OpenAPI 3.0",
          "points": 7.0
        },
        {
          "id": "crit_3",
          "description": "Implementaci\xf3n de Calidad y Tests",
          "points": 5.0
        }
      ]
    }
  ]
}`},{id:"schedule-ics",method:"GET",path:"/api/v1/schedule/export.ics",title:"Feed iCalendar Universal (.ics)",description:"Genera el archivo estándar iCalendar RFC 5545 para sincronizar clases directamente en Google Calendar o Apple Calendar.",headers:{Authorization:"Bearer <TOKEN_JWT>"},curl:`curl -X GET "http://localhost:8080/api/v1/schedule/export.ics" \\
  -H "Authorization: Bearer \${SYNCUTP_JWT}" \\
  -o "horario_utp.ics"`,typescript:`// Descarga de archivo .ics para integraci\xf3n
const res = await fetch("http://localhost:8080/api/v1/schedule/export.ics", {
  headers: { "Authorization": \`Bearer \${token}\` }
});
const blob = await res.blob();
const downloadUrl = URL.createObjectURL(blob);
// Revoke object URL after trigger...`,python:`import requests

res = requests.get(
    "http://localhost:8080/api/v1/schedule/export.ics",
    headers={"Authorization": f"Bearer {token}"}
)
with open("horario_utp.ics", "wb") as f:
    f.write(res.content)`,responseStatus:200,responseContentType:"text/calendar; charset=UTF-8",responseBody:`BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//SyncUTP Academic Gateway//EN
CALSCALE:GREGORIAN
METHOD:PUBLISH
X-WR-CALNAME:Horario UTP 2026-1
BEGIN:VEVENT
UID:syncutp-100000I04N-24501@utp.edu.pe
DTSTAMP:20260930T100000Z
DTSTART;TZID=America/Lima:20261005T183000
DTEND;TZID=America/Lima:20261005T214500
RRULE:FREQ=WEEKLY;UNTIL=20261220T235959Z
SUMMARY:INTELIGENCIA ARTIFICIAL - LAB-B402
LOCATION:Torre Arequipa, Aula LAB-B402
DESCRIPTION:Docente: Ing. Carlos Mendoza | Secci\xf3n: 24501
END:VEVENT
END:VCALENDAR`}];e.s(["InteractiveSandbox",0,()=>{let[e,r]=(0,t.useState)("syllabus-markdown"),[i,n]=(0,t.useState)("curl"),[c,d]=(0,t.useState)(!1),l=o.find(a=>a.id===e)||o[0],p=()=>{switch(i){case"ts":return l.typescript;case"py":return l.python;default:return l.curl}};return(0,a.jsxs)("section",{id:"sandbox",className:"sandbox-section",children:[(0,a.jsx)("div",{className:"section-label",children:"// INTERFACE TESTING & CODE GENERATOR"}),(0,a.jsx)("h2",{className:"section-title",children:"Sandbox Interactivo de Endpoints"}),(0,a.jsx)(s,{text:"Pruébalo en vivo con cURL, Python o TypeScript",className:"annotation-sandbox",arrowDirection:"curved-down-right"}),(0,a.jsxs)("div",{className:"sandbox-container",children:[(0,a.jsxs)("div",{className:"sandbox-header",children:[(0,a.jsxs)("div",{className:"sandbox-title",children:[(0,a.jsx)("span",{className:`method-tag method-${l.method.toLowerCase()}`,children:l.method}),(0,a.jsx)("span",{children:l.path})]}),(0,a.jsxs)("div",{style:{display:"flex",alignItems:"center",gap:"var(--space-3)"},children:[(0,a.jsx)("span",{style:{color:"#9A9DA8",fontSize:"0.75rem"},children:"Content-Type:"}),(0,a.jsx)("span",{style:{color:"var(--color-paper-base)",fontWeight:700},children:l.responseContentType})]})]}),(0,a.jsxs)("div",{className:"sandbox-body",children:[(0,a.jsx)("div",{className:"endpoint-list",role:"tablist","aria-label":"Lista de endpoints disponibles",children:o.map(t=>{let s=t.id===e;return(0,a.jsxs)("button",{className:`endpoint-btn ${s?"active":""}`,onClick:()=>r(t.id),role:"tab","aria-selected":s,children:[(0,a.jsxs)("div",{style:{display:"flex",alignItems:"center",gap:"var(--space-2)"},children:[(0,a.jsx)("span",{className:`method-tag method-${t.method.toLowerCase()}`,children:t.method}),(0,a.jsx)("span",{className:"endpoint-path",children:t.path})]}),(0,a.jsx)("span",{className:"endpoint-desc-brief",children:t.title})]},t.id)})}),(0,a.jsxs)("div",{className:"playground-view",children:[(0,a.jsxs)("div",{className:"tab-bar",children:[(0,a.jsxs)("div",{className:"tabs-group",role:"tablist",children:[(0,a.jsx)("button",{className:`code-tab ${"curl"===i?"active":""}`,onClick:()=>n("curl"),children:"cURL"}),(0,a.jsx)("button",{className:`code-tab ${"ts"===i?"active":""}`,onClick:()=>n("ts"),children:"TypeScript (Fetch)"}),(0,a.jsx)("button",{className:`code-tab ${"py"===i?"active":""}`,onClick:()=>n("py"),children:"Python (requests)"})]}),(0,a.jsx)("button",{className:"copy-btn",onClick:()=>{navigator.clipboard.writeText(p()),d(!0),setTimeout(()=>d(!1),2e3)},"aria-label":"Copiar código al portapapeles",children:c?"✓ Copiado":"Copiar Snippet"})]}),(0,a.jsx)("pre",{className:"code-display",children:(0,a.jsx)("code",{children:p()})}),(0,a.jsxs)("div",{className:"response-panel",children:[(0,a.jsxs)("div",{className:"response-header",children:[(0,a.jsxs)("div",{children:[(0,a.jsx)("span",{children:"STATUS: "}),(0,a.jsxs)("span",{className:"status-badge",children:[l.responseStatus," OK"]}),(0,a.jsx)("span",{style:{marginLeft:"1rem"},children:"LATENCY: ~85ms"})]}),(0,a.jsx)("span",{children:"RESPONSE PREVIEW"})]}),(0,a.jsx)("pre",{className:"code-display",style:{maxHeight:"240px",overflowY:"auto",color:"#A8D7FE"},children:(0,a.jsx)("code",{children:l.responseBody})})]})]})]})]})]})}],20198)}]);