import React from "react";

interface EndpointInfo {
  method: "GET" | "POST";
  path: string;
  description: string;
  auth: boolean;
  format: "JSON" | "Markdown" | "iCalendar";
}

interface DomainGroup {
  domain: string;
  description: string;
  endpoints: EndpointInfo[];
}

const apiCatalog: DomainGroup[] = [
  {
    domain: "01. Autenticación & Identidad (/auth)",
    description: "Autenticación institucional de estudiantes mediante SSO Keycloak UTP.",
    endpoints: [
      { method: "POST", path: "/api/v1/auth/login", description: "Autentica con código y contraseña institucional. Retorna tokens de acceso y refresh.", auth: false, format: "JSON" },
      { method: "POST", path: "/api/v1/auth/refresh", description: "Renueva sesión sin reingresar credenciales usando el refresh_token emitido por Keycloak.", auth: false, format: "JSON" },
      { method: "GET", path: "/api/v1/auth/me", description: "Inspección stateless inmediata del token Bearer en memoria.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/auth/profile/{id}", description: "Consulta de perfil de estudiante en repositorio por ID.", auth: true, format: "JSON" },
    ],
  },
  {
    domain: "02. Horarios & Sesiones de Clase (/schedule)",
    description: "Sincronización y exportación de sesiones semanales con aulas físicas y virtuales.",
    endpoints: [
      { method: "GET", path: "/api/v1/schedule", description: "Consulta de horario matriculado con sesiones por día, cursos, aulas y docentes.", auth: true, format: "JSON" },
      { method: "POST", path: "/api/v1/schedule/sync", description: "Fuerza la sincronización directa del horario desde los servidores de UTP.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/schedule/export.ics", description: "Exporta el horario completo en formato RFC 5545 para suscripción en Google/Apple/Outlook Calendar.", auth: true, format: "iCalendar" },
    ],
  },
  {
    domain: "03. Sílabos Oficiales & Pipeline LLM (/syllabus)",
    description: "Extracción posicional, caché en Supabase y depuración anti-ruido para agentes de IA.",
    endpoints: [
      { method: "GET", path: "/api/v1/syllabus/{courseCode}", description: "Obtiene sílabo estructurado (unidades, semanas dinámicas, evaluaciones).", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/syllabus/{courseCode}/markdown", description: "Sílabo limpio en Markdown para LLMs (ahorra ~70% de tokens vs PDF).", auth: true, format: "Markdown" },
      { method: "GET", path: "/api/v1/syllabus", description: "Lista todos los sílabos matriculados o almacenados del estudiante.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/syllabus/raw-text", description: "Extrae el texto plano crudo directo del PDF del sílabo en S3.", auth: true, format: "JSON" },
    ],
  },
  {
    domain: "04. Cursos, Calificaciones & Simulador (/courses)",
    description: "Calificaciones oficiales de Portal UTP y motor matemático de notas requeridas.",
    endpoints: [
      { method: "GET", path: "/api/v1/courses/summary", description: "Consulta GraphQL de cursos, notas parciales registradas y fórmulas oficiales rectoras.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/courses/{courseCode}/simulator", description: "Simula el promedio ponderado o la nota necesaria en el examen final según fórmula oficial.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/courses/simulator", description: "Calcula en paralelo para TODOS los cursos del ciclo el promedio requerido para aprobar.", auth: true, format: "JSON" },
    ],
  },
  {
    domain: "05. Tareas, Evaluaciones & Canvas LMS (/tasks)",
    description: "Entregas pendientes, rúbricas multinivel, código de curso y correlación con el sílabo oficial.",
    endpoints: [
      { method: "GET", path: "/api/v1/tasks/activities", description: "Calendario unificado de actividades con courseCode oficial y vinculación con evaluaciones del sílabo.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/tasks/{sectionId}/{activityId}", description: "Consigna en Markdown, rúbrica completa, courseCode rector y match con el sílabo oficial.", auth: true, format: "JSON" },
      { method: "GET", path: "/api/v1/tasks/upcoming", description: "Próximas evaluaciones ponderadas con peso porcentual del sílabo rector y urgencia cronológica.", auth: true, format: "JSON" },
    ],
  },
  {
    domain: "06. Monitoreo & Gateway Root (/)",
    description: "Endpoints de salud operativa y negociación de contenido.",
    endpoints: [
      { method: "GET", path: "/api/v1/", description: "Portal desarrollador (Accept: text/html) o metadatos de endpoints (Accept: application/json).", auth: false, format: "JSON" },
      { method: "GET", path: "/api/v1/health", description: "Health check operativo del servicio para Kubernetes o balanceadores.", auth: false, format: "JSON" },
    ],
  },
];

export const EndpointsDirectory: React.FC = () => {
  return (
    <section id="directorio" style={{ paddingBottom: "var(--space-12)", borderBottom: "2px solid var(--color-ink-primary)", marginBottom: "var(--space-12)" }}>
      <div className="section-label">// CATÁLOGO COMPLETO DE RUTAS</div>
      <h2 className="section-title">Directorio Oficial de Endpoints de SyncUTP</h2>
      <p style={{ color: "var(--color-ink-muted)", fontSize: "var(--text-small)", marginBottom: "var(--space-6)", maxWidth: "860px" }}>
        A continuación se detalla el inventario exhaustivo de las rutas REST/Markdown/iCal activas en el Gateway de SyncUTP, organizadas por dominio funcional.
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)" }}>
        {apiCatalog.map((group) => (
          <div key={group.domain} style={{ backgroundColor: "var(--color-paper-muted)", border: "2px solid var(--color-ink-primary)", padding: "var(--space-5)", boxShadow: "var(--shadow-card)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", flexWrap: "wrap", gap: "var(--space-2)", borderBottom: "1px solid var(--color-ink-secondary)", paddingBottom: "var(--space-2)", marginBottom: "var(--space-3)" }}>
              <h3 style={{ fontFamily: "var(--font-mono)", fontSize: "1rem", fontWeight: 700, color: "var(--color-ink-primary)" }}>
                {group.domain}
              </h3>
              <span style={{ fontSize: "0.75rem", color: "var(--color-ink-muted)" }}>
                {group.description}
              </span>
            </div>

            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "var(--font-mono)", fontSize: "0.8125rem" }}>
                <thead>
                  <tr style={{ textAlign: "left", color: "var(--color-ink-faint)", borderBottom: "1px dashed var(--color-ink-secondary)" }}>
                    <th style={{ padding: "var(--space-2)", width: "80px" }}>MÉTODO</th>
                    <th style={{ padding: "var(--space-2)" }}>RUTA DE ENDPOINT</th>
                    <th style={{ padding: "var(--space-2)" }}>DESCRIPCIÓN</th>
                    <th style={{ padding: "var(--space-2)", width: "90px" }}>AUTH</th>
                    <th style={{ padding: "var(--space-2)", width: "90px" }}>FORMATO</th>
                  </tr>
                </thead>
                <tbody>
                  {group.endpoints.map((ep) => (
                    <tr key={ep.path} style={{ borderBottom: "1px solid rgba(30, 32, 34, 0.06)" }}>
                      <td style={{ padding: "var(--space-2)" }}>
                        <span className={`method-tag method-${ep.method.toLowerCase()}`}>
                          {ep.method}
                        </span>
                      </td>
                      <td style={{ padding: "var(--space-2)", fontWeight: 700, color: "var(--color-ink-primary)" }}>
                        <code>{ep.path}</code>
                      </td>
                      <td style={{ padding: "var(--space-2)", fontFamily: "var(--font-body)", color: "var(--color-ink-muted)" }}>
                        {ep.description}
                      </td>
                      <td style={{ padding: "var(--space-2)" }}>
                        {ep.auth ? (
                          <span style={{ color: "var(--color-accent)", fontWeight: 700, fontSize: "0.75rem" }}>Bearer JWT</span>
                        ) : (
                          <span style={{ color: "var(--color-ink-faint)", fontSize: "0.75rem" }}>Público</span>
                        )}
                      </td>
                      <td style={{ padding: "var(--space-2)" }}>
                        <span className="pill" style={{ fontSize: "0.7rem" }}>
                          {ep.format}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
};
