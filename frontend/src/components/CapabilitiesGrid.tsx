import React from "react";
import { HandwrittenAnnotation } from "./HandwrittenAnnotation";

interface Capability {
  num: string;
  title: string;
  desc: string;
  pills: string[];
  endpoint: string;
  method: "GET" | "POST";
}

const capabilities: Capability[] = [
  {
    num: "01 // SEGURIDAD & SESIÓN",
    title: "Autenticación SSO Transparente",
    desc: "Intercambio directo de cookies de sesión UTP por tokens JWT Bearer seguros. Cero contraseñas de alumnos almacenadas en bases de datos.",
    pills: ["JWT Bearer", "Zero-Storage", "RFC 7519"],
    endpoint: "/api/v1/auth/login",
    method: "POST",
  },
  {
    num: "02 // EXTRACTOR UNIVERSAL",
    title: "Sílabos & Esquema Adaptativo",
    desc: "Normaliza la estructura curricular reconociendo dinámicamente ciclos regulares de 18 semanas, ciclos de verano intensivos o programas CGT modulares sin sesgos.",
    pills: ["Dynamic Weeks", "Supabase Cache", "Adaptive Schema"],
    endpoint: "/api/v1/syllabus/{code}",
    method: "GET",
  },
  {
    num: "03 // OPTIMIZADO PARA IA",
    title: "Pipeline Markdown para LLMs",
    desc: "Limpia y depura el sílabo eliminando encabezados, firmas y páginas vacías. Ahorra hasta un 70% de tokens al alimentar tus agentes RAG o chats de estudio.",
    pills: ["Token-Saver", "RAG Ready", "Markdown Limpio"],
    endpoint: "/api/v1/syllabus/{code}/markdown",
    method: "GET",
  },
  {
    num: "04 // MOTOR ACADÉMICO",
    title: "Simulador de Promedios Ponderados",
    desc: "Interpreta automáticamente la fórmula matemática de evaluación de cada curso (ej. PC1*0.20 + EXFN*0.40) y calcula la nota requerida para aprobar.",
    pills: ["Formula Parser", "What-If Analysis", "Evaluaciones"],
    endpoint: "/api/v1/grades/simulate",
    method: "POST",
  },
  {
    num: "05 // CANVAS LMS SYNC",
    title: "Tareas & Rúbricas Detalladas",
    desc: "Extrae entregas pendientes de Canvas junto con sus matrices de rúbrica (criterios de calificación, puntajes máximos y descripciones de logro).",
    pills: ["Canvas LMS", "Rubric Matrices", "Deadlines"],
    endpoint: "/api/v1/tasks/rubrics",
    method: "GET",
  },
  {
    num: "06 // INTEGRACIÓN CALENDARIO",
    title: "Exportación RFC 5545 iCalendar",
    desc: "Convierte el horario de clases en un feed .ics compatible con Google Calendar, Apple Calendar y Outlook, con recordatorios y aulas físicas o virtuales.",
    pills: ["RFC 5545", "Google Calendar", "Apple Calendar"],
    endpoint: "/api/v1/schedule/export.ics",
    method: "GET",
  },
];

export const CapabilitiesGrid: React.FC = () => {
  return (
    <section id="capacidades" className="capabilities-section">
      <div className="section-label">// ESPECIFICACIONES FUNCIONALES</div>
      <h2 className="section-title">Los 6 Pilares de la Plataforma SyncUTP</h2>

      <HandwrittenAnnotation
        text="¡Detecta automáticamente ciclos de 18 semanas, verano o módulos sin sesgos!"
        className="annotation-syllabus"
        arrowDirection="curved-down-right"
      />

      <div className="capabilities-grid">
        {capabilities.map((cap) => (
          <article key={cap.num} className="capability-card">
            <div className="card-num">
              <span>{cap.num}</span>
              <span className={`method-tag method-${cap.method.toLowerCase()}`}>
                {cap.method}
              </span>
            </div>

            <h3 className="card-title">{cap.title}</h3>
            <p className="card-desc">{cap.desc}</p>

            <div style={{ margin: "var(--space-2) 0 var(--space-4) 0", fontFamily: "var(--font-mono)", fontSize: "0.75rem", color: "var(--color-ink-muted)" }}>
              <code>{cap.endpoint}</code>
            </div>

            <div className="card-pill-row">
              {cap.pills.map((pill) => (
                <span key={pill} className="pill">
                  {pill}
                </span>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
};
