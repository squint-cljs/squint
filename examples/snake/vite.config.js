import { env } from "process"
import { defineConfig } from "vite"
import { viteSingleFile } from "vite-plugin-singlefile"

export default defineConfig({
  plugins: [viteSingleFile({ removeViteModuleLoader: true })],
  build: {
    // minify: "terser", // slightly smaller
    outDir: "dist",
    emptyOutDir: false,
    rollupOptions: {
      input: {
        "index": env["SRC"] || "index.html"
      }
    }
  }
})
