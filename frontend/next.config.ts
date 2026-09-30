import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
  basePath: "/api/v1",
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
