import React from "react";

interface HandwrittenAnnotationProps {
  text: string;
  className?: string;
  arrowDirection?: "curved-down-left" | "curved-down-right" | "curved-up-left" | "curved-up-right";
}

export const HandwrittenAnnotation: React.FC<HandwrittenAnnotationProps> = ({
  text,
  className = "",
  arrowDirection = "curved-down-left",
}) => {
  return (
    <div className={`hand-annotation ${className}`} aria-hidden="true">
      {arrowDirection === "curved-up-left" && (
        <svg width="46" height="34" viewBox="0 0 46 34" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M40 30C28 28 12 24 8 10M8 10L14 12M8 10L6 18"
            stroke="var(--color-accent)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}

      {arrowDirection === "curved-down-right" && (
        <svg width="48" height="36" viewBox="0 0 48 36" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M4 6C18 8 36 14 42 28M42 28L36 26M42 28L44 20"
            stroke="var(--color-accent)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}

      <span>{text}</span>

      {arrowDirection === "curved-down-left" && (
        <svg width="50" height="38" viewBox="0 0 50 38" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M46 6C34 10 18 20 8 32M8 32L16 32M8 32L8 24"
            stroke="var(--color-accent)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}

      {arrowDirection === "curved-up-right" && (
        <svg width="44" height="32" viewBox="0 0 44 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M6 28C18 24 30 16 38 6M38 6L32 6M38 6L40 14"
            stroke="var(--color-accent)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}
    </div>
  );
};
