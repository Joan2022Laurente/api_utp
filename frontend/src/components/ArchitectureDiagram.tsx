import React from "react";

export const ArchitectureDiagram: React.FC = () => {
  return (
    <section id="arquitectura" className="diagram-section">
      <div className="section-label">// ESQUEMA DE INTEGRACIÓN DEL SISTEMA</div>
      <h2 className="section-title">Arquitectura Hexagonal & Flujo de Datos</h2>

      <div className="blueprint-diagram">
        <div className="diagram-grid">
          {/* CLIENT APPS */}
          <div className="diagram-node">
            <div className="diagram-node-title">
              <span>01. CLIENTES CONSUMIDORES</span>
              <span className="pill">REST</span>
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-accent)" }}>▸</span> Apps Móviles (Flutter / RN)
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-accent)" }}>▸</span> Frontend Web (Next.js / Vite)
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-accent)" }}>▸</span> Bots de Telegram / Discord
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-accent)" }}>▸</span> Agentes IA & Pipelines RAG
            </div>
          </div>

          {/* CONNECTOR 1 */}
          <div className="diagram-connector">
            <span>HTTPS</span>
            <span>─────▶</span>
            <span>JWT Bearer</span>
          </div>

          {/* SYNCUTP CORE */}
          <div className="diagram-node diagram-core">
            <div className="diagram-node-title">
              <span style={{ color: "var(--color-accent)" }}>★ SYNCUTP GATEWAY</span>
              <span className="pill">SPRING BOOT</span>
            </div>
            <div className="diagram-node-item">
              <strong>1. Auth Adapter:</strong> SSO Cookie Handshake
            </div>
            <div className="diagram-node-item">
              <strong>2. Syllabus Engine:</strong> PDF Parser & Adaptive Normalizer (18 sem / verano / mod)
            </div>
            <div className="diagram-node-item">
              <strong>3. LLM Pipeline:</strong> Minified Clean Markdown (anti-noise)
            </div>
            <div className="diagram-node-item">
              <strong>4. Grades Formula:</strong> Dynamic Evaluation Parser
            </div>
            <div className="diagram-node-item">
              <strong>5. iCal Exporter:</strong> RFC 5545 Calendar Stream
            </div>
          </div>

          {/* CONNECTOR 2 */}
          <div className="diagram-connector">
            <span>APIs</span>
            <span>─────▶</span>
            <span>Upstream</span>
          </div>

          {/* UPSTREAM SERVICES */}
          <div className="diagram-node">
            <div className="diagram-node-title">
              <span>03. SERVICIOS OFICIALES</span>
              <span className="pill">ORIGEN</span>
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-ink-muted)" }}>▸</span> Portal UTP (SSO & Horarios)
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-ink-muted)" }}>▸</span> Canvas LMS (Tareas & Rúbricas)
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-ink-muted)" }}>▸</span> Supabase (Caché de Sílabos)
            </div>
            <div className="diagram-node-item">
              <span style={{ color: "var(--color-ink-muted)" }}>▸</span> OpenRouter (Qwen / Gemma AI)
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
