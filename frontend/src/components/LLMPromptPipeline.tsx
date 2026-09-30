"use client";

import React from "react";

export const LLMPromptPipeline: React.FC = () => {
  const baseUrl = typeof window !== "undefined" ? window.location.origin : "";

  return (
    <section id="llm-pipeline" className="llm-section">
      <div className="section-label">// AI &amp; AGENTIC INTEGRATION</div>
      <h2 className="section-title">Pipeline Semántico para LLMs y Asistentes RAG</h2>

      <div className="llm-container">
        {/* Left explanation card */}
        <div className="llm-card-paper">
          <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.8125rem", color: "var(--color-accent)", fontWeight: 700, marginBottom: "var(--space-2)" }}>
            // PROBLEM VS SOLUTION
          </div>
          <h3 style={{ fontFamily: "var(--font-display)", fontSize: "1.35rem", marginBottom: "var(--space-3)" }}>
            ¿Por qué no pasar el PDF crudo a la IA?
          </h3>
          <p style={{ fontSize: "var(--text-small)", color: "var(--color-ink-muted)", lineHeight: 1.6 }}>
            Los PDFs universitarios contienen marcas de agua, encabezados redundantes en cada página, tablas rotas y firmas digitales que saturan la ventana de contexto de los modelos de lenguaje y provocan alucinaciones.
          </p>

          <ul className="llm-spec-list">
            <li className="llm-spec-item">
              <span className="llm-spec-bullet">01.</span>
              <span><strong>Ahorro del 70% de tokens:</strong> Se remueve la metadata institucional irrelevante para concentrarse únicamente en unidades, temas y fechas de entrega.</span>
            </li>
            <li className="llm-spec-item">
              <span className="llm-spec-bullet">02.</span>
              <span><strong>Resiliencia Temporal:</strong> Respeta la duración real del curso (ciclo regular de 18 semanas, ciclos de verano acelerados de 8-10 semanas o módulos CGT).</span>
            </li>
            <li className="llm-spec-item">
              <span className="llm-spec-bullet">03.</span>
              <span><strong>Parser de Fórmulas Matemáticas:</strong> Traduce la sintaxis de evaluación a tablas comprensibles por GPT-4, Claude y Qwen sin ambigüedades.</span>
            </li>
          </ul>
        </div>

        {/* Right prompt example card */}
        <div style={{ backgroundColor: "#191B1F", border: "2px solid var(--color-ink-primary)", padding: "var(--space-5)", color: "#ECEAE4", boxShadow: "var(--shadow-card)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid #2B2E36", paddingBottom: "var(--space-2)", marginBottom: "var(--space-3)", fontFamily: "var(--font-mono)", fontSize: "0.75rem", color: "#9A9DA8" }}>
            <span>EJEMPLO DE INTEGRACIÓN CON AGENTE IA</span>
            <span style={{ color: "var(--color-accent)", fontWeight: 700 }}>PYTHON + OPENROUTER</span>
          </div>

          <pre style={{ fontSize: "0.8125rem", lineHeight: 1.55, overflowX: "auto", color: "#E0DDD5" }}>
            <code>{
`# 1. Obtener sílabo depurado de SyncUTP
res = requests.get(
  f"${baseUrl}/api/v1/syllabus/{'{course_code}'}/markdown",
  headers={"Authorization": f"Bearer {'{jwt_token}'}"}
)
clean_markdown = res.text

# 2. Inyectar como System Context al LLM
system_prompt = f"""
Eres un tutor universitario de la UTP. Responde dudas del estudiante
basándote estrictamente en el siguiente sílabo oficial normalizado:

{'{clean_markdown}'}
"""

# 3. Llamada al LLM sin saturar el contexto
completion = client.chat.completions.create(
  model="qwen/qwen3.8-27b:free",
  messages=[
    {"role": "system", "content": system_prompt},
    {"role": "user", "content": "¿Qué temas entran en la PC2 y qué semana es?"}
  ]
)`
            }</code>
          </pre>
        </div>
      </div>
    </section>
  );
};
