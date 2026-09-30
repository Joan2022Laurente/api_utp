import React from "react";
import { TechnicalHeader } from "@/components/TechnicalHeader";
import { Navbar } from "@/components/Navbar";
import { HeroSection } from "@/components/HeroSection";
import { ArchitectureDiagram } from "@/components/ArchitectureDiagram";
import { CapabilitiesGrid } from "@/components/CapabilitiesGrid";
import { InteractiveSandbox } from "@/components/InteractiveSandbox";
import { LLMPromptPipeline } from "@/components/LLMPromptPipeline";
import { EndpointsDirectory } from "@/components/EndpointsDirectory";
import { TechnicalFooter } from "@/components/TechnicalFooter";

export default function Home() {
  return (
    <main className="app-container">
      {/* 100% Full-width Technical Blueprint Header Bar */}
      <TechnicalHeader />

      {/* Main Content Centered Container */}
      <div className="content-container">
        <Navbar />
        <HeroSection />
        <ArchitectureDiagram />
        <CapabilitiesGrid />
        <InteractiveSandbox />
        <LLMPromptPipeline />
        <EndpointsDirectory />
        <TechnicalFooter />
      </div>
    </main>
  );
}
