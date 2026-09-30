import React from "react";

export const TechnicalFooter: React.FC = () => {
  return (
    <footer className="technical-footer">
      <div className="footer-top">
        <div className="stamp-box">
          <span className="stamp-tag">// COMITÉ DE ARQUITECTURA DE SOFTWARE</span>
          <span className="stamp-title">SYNCUTP // CORE GATEWAY V2.0.0</span>
          <span style={{ fontSize: "0.75rem", color: "var(--color-ink-muted)", marginTop: "var(--space-1)" }}>
            ESTÁNDAR: ARQUITECTURA HEXAGONAL & PROTOCOLO ADAPTATIVO
          </span>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)", fontFamily: "var(--font-mono)", fontSize: "0.8125rem" }}>
          <div>
            <strong>DISPONIBILIDAD:</strong> <span style={{ color: "#276A3C" }}>99.9% UPTIME</span>
          </div>
          <div>
            <strong>LICENCIA:</strong> COMUNIDAD ESTUDIANTIL UTP
          </div>
        </div>
      </div>

      <div className="footer-bottom">
        <div>
          <span>© 2026 SyncUTP Core Project. Diseñado bajo el sistema "Analog Blueprint & Precision Draft".</span>
        </div>

        <div className="footer-links">
          <a
            href="/api/v1/swagger-ui/index.html"
            target="_blank"
            rel="noopener noreferrer"
            className="footer-link"
          >
            Swagger UI
          </a>
          <a
            href="/api/v1/"
            target="_blank"
            rel="noopener noreferrer"
            className="footer-link"
          >
            API Metadata JSON
          </a>
          <a
            href="/api/v1/health"
            target="_blank"
            rel="noopener noreferrer"
            className="footer-link"
          >
            Health Check
          </a>
        </div>
      </div>
    </footer>
  );
};
