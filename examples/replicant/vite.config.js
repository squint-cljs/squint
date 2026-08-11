// vite.config.js
import { defineConfig } from 'vite';
import squint from 'squint-cljs/vite';
import analyzer from 'rollup-plugin-analyzer';

const { plugin: analyze } = analyzer;

export default defineConfig(() => {
  return {
    plugins: [squint()],
    build: {
      rollupOptions: {
        plugins: [analyze({ summaryOnly: true, limit: 15 })],
      },
    },
  };
});
