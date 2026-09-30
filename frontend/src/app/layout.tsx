import type { Metadata } from "next";
import { Space_Grotesk, Inter, JetBrains_Mono, Caveat } from "next/font/google";
import "./globals.css";

const spaceGrotesk = Space_Grotesk({
  variable: "--font-space-grotesk",
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  display: "swap",
});

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-jetbrains-mono",
  subsets: ["latin"],
  weight: ["400", "500", "700"],
  display: "swap",
});

const caveat = Caveat({
  variable: "--font-caveat",
  subsets: ["latin"],
  weight: ["500", "700"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "SyncUTP // Portal Oficial para Desarrolladores UTP",
  description:
    "Pasarela académica y API rectora para la comunidad de desarrolladores UTP. Acceso a horarios, sílabos normalizados, extracción limpia para LLMs, tareas con rúbricas y sincronización iCalendar.",
  keywords: [
    "SyncUTP",
    "UTP",
    "API UTP",
    "Horario UTP",
    "Sílabo UTP",
    "Canvas UTP",
    "LLM UTP",
    "Developers UTP",
  ],
  authors: [{ name: "SyncUTP Core Engineering Team" }],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="es"
      className={`${spaceGrotesk.variable} ${inter.variable} ${jetbrainsMono.variable} ${caveat.variable}`}
    >
      <body>{children}</body>
    </html>
  );
}
