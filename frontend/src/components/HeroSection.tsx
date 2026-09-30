import React from "react";
import { HandwrittenAnnotation } from "./HandwrittenAnnotation";

export const HeroSection: React.FC = () => {
  return (
    <header className="hero-section">
      <div className="hero-stamp">
        [PROYECTO: SYNCUTP // INFRAESTRUCTURA PARA ESTUDIANTES DESARROLLADORES]
      </div>

      <HandwrittenAnnotation
        text="¡Cero contraseñas en BD! Cookie exchange transparente"
        className="annotation-hero"
        arrowDirection="curved-down-left"
      />

      <h1 className="hero-title">
        La API rectora que transforma los datos de la UTP en código limpio.
      </h1>

      <p className="hero-subtitle">
        SyncUTP desacopla los portales de la universidad para que puedas construir
        aplicaciones móviles, bots de Telegram, simuladores de promedios ponderados
        y agentes de estudio con Inteligencia Artificial sin lidiar con scraping inestable.
      </p>

      <div className="hero-actions">
        <a href="#sandbox" className="btn btn-primary" aria-label="Explorar sandbox interactivo de endpoints">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polygon points="5 3 19 12 5 21 5 3" />
          </svg>
          Explorar Sandbox Live
        </a>

        <a href="#capacidades" className="btn btn-secondary" aria-label="Ver las 6 capacidades principales">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
            <line x1="3" y1="9" x2="21" y2="9" />
            <line x1="9" y1="21" x2="9" y2="9" />
          </svg>
          Capacidades del Sistema
        </a>

        <a
          href="/api/v1/swagger-ui/index.html"
          target="_blank"
          rel="noopener noreferrer"
          className="btn btn-secondary"
          aria-label="Abrir documentación Swagger OpenAPI"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" />
            <line x1="16" y1="17" x2="8" y2="17" />
            <polyline points="10 9 9 9 8 9" />
          </svg>
          Swagger Docs ↗
        </a>
      </div>
    </header>
  );
};
