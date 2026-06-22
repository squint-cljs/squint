// vite.config.js
import { defineConfig } from 'vite';
import squint from 'squint-cljs/vite';

export default defineConfig(() => {
  return {
    plugins: [squint()],
  };
});
