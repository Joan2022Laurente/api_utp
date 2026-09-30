import React from "react";

export const CornerCrosshairs: React.FC = () => {
  return (
    <>
      <div className="corner-crosshair corner-tl" aria-hidden="true" />
      <div className="corner-crosshair corner-tr" aria-hidden="true" />
      <div className="corner-crosshair corner-bl" aria-hidden="true" />
      <div className="corner-crosshair corner-br" aria-hidden="true" />
    </>
  );
};
