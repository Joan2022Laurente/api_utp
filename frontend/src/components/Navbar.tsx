import React from "react";

export const Navbar: React.FC = () => {
  return (
    <nav className="nav-bar" aria-label="Navegación principal">
      <div className="brand-wrapper">
        <div className="brand-logo-badge" aria-hidden="true">
          S
        </div>
        <div className="brand-text">
          <span className="brand-title">SyncUTP</span>
          <span className="brand-sub">Core Gateway & Developer Portal</span>
        </div>
      </div>

      <ul className="nav-links">
        <li>
          <a href="#arquitectura" className="nav-link">
            // Arquitectura
          </a>
        </li>
        <li>
          <a href="#capacidades" className="nav-link">
            // Capacidades
          </a>
        </li>
        <li>
          <a href="#sandbox" className="nav-link">
            // Sandbox Live
          </a>
        </li>
        <li>
          <a href="#llm-pipeline" className="nav-link">
            // LLM Pipeline
          </a>
        </li>
        <li>
          <a href="#directorio" className="nav-link">
            // Directorio (20 Rutas)
          </a>
        </li>
        <li>
          <a
            href="/api/v1/swagger-ui/index.html"
            target="_blank"
            rel="noopener noreferrer"
            className="nav-link"
            style={{ color: "var(--color-accent)" }}
          >
            [Swagger UI ↗]
          </a>
        </li>
      </ul>
    </nav>
  );
};
