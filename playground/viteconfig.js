// vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import analyze from "rollup-plugin-analyzer";


export default defineConfig({
  build: {target: "esnext",
          rollupOptions: {
            plugins: [analyze()]
          }},
  plugins: [react()]
});
