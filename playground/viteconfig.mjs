// vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import analyze from "rollup-plugin-analyzer";
// import { viteStaticCopy } from 'vite-plugin-static-copy';

export default defineConfig({
  build: {target: "esnext",
          rollupOptions: {
            plugins: [analyze()]
          }},
  plugins: [react()
    //         , viteStaticCopy({
    // targets: [
    //   {
    //     src: 'node_modules/squint-cljs',
    //     dest: 'squint-cljs'
    //   }
    // ]
    //         })
           ]
});
//
