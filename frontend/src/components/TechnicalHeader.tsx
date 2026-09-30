import React from "react";

export const TechnicalHeader: React.FC = () => {
  return (
    <div className="technical-header-bar">
      <div className="meta-group">
        <div className="meta-item">
          <span className="meta-label">SYS_ID:</span>
          <span className="meta-val">SYNCUTP-2026-ARCH</span>
        </div>
        <div className="meta-item">
          <span className="meta-label">COORD:</span>
          <span className="meta-val">12.0464° S, 77.0428° W</span>
        </div>
        <div className="meta-item">
          <span className="meta-label">SPEC:</span>
          <span className="meta-val">REV_2.0.0</span>
        </div>
      </div>

      <div className="meta-group">
        <div className="meta-item">
          <span className="meta-label">TARGET:</span>
          <span className="meta-val">SPRING BOOT 3.3 + SUPABASE + OPENROUTER</span>
        </div>
        <div className="status-indicator">
          <span className="status-dot" aria-hidden="true" />
          <span>GATEWAY ONLINE</span>
        </div>
      </div>
    </div>
  );
};
