import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "src"),
      "server-only": path.resolve(import.meta.dirname, "tests/server-only.ts"),
    },
  },
  test: {
    environment: "node",
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "lcov", "json-summary"],
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.d.ts"],
      reportsDirectory: "coverage",
    },
    include: ["tests/unit/**/*.test.ts"],
  },
});
