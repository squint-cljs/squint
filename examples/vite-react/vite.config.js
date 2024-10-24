/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { visualizer } from 'rollup-plugin-visualizer';

export default defineConfig({
  test: {
    include: ["public/**/**test.mjs", "public/**/**test.jsx"],
  },
  plugins: [
    react(),
    visualizer({ open: false, filename: 'bundle-visualization.html' })
  ]
});
