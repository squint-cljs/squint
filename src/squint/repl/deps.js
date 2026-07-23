// On-demand esbuild bundling of a single npm dep for the browser REPL, shared
// by the vite plugin (../../../vite-common.js) and the webpack plugin
// (./webpack.js). See notes/internal/browser-repl-webpack-design.md (tier 3).
//
// esbuild is not a squint dependency: it's imported lazily and errors with an
// install hint when missing.

// Returns a `bundleDep(spec) -> Promise<esm text>` fn. Concurrent requests for
// the same spec share one build (cached on `.cache`, a Map the caller may read
// or invalidate: on error, delete the entry to allow a retry). `plugins` are
// extra esbuild plugins (vite passes one that externalizes already-optimized
// transitives; webpack passes none, so transitives inline - accepted caveat).
export function makeBundleDep({ root, plugins = [] }) {
  const cache = new Map();
  const bundleDep = (spec) => {
    let p = cache.get(spec);
    if (p) return p;
    p = (async () => {
      let esbuild;
      try {
        esbuild = await import('esbuild');
      } catch {
        throw new Error('esbuild is required for on-demand REPL deps (npm i esbuild)');
      }
      // A proxy module: esbuild's entryPoints treat the arg as a file path, so
      // bundle a stdin module that imports the spec instead, and normalize CJS
      // default-export interop (`import('lodash')` should hand back the lodash
      // object, not a { default } namespace).
      const proxy =
        `import * as __m from ${JSON.stringify(spec)};\n` +
        `export * from ${JSON.stringify(spec)};\n` +
        `export default (__m.default !== undefined ? __m.default : __m);`;
      const out = await esbuild.build({
        stdin: { contents: proxy, resolveDir: root, sourcefile: 'repl-dep-proxy.js' },
        bundle: true, format: 'esm', platform: 'browser', write: false,
        logLevel: 'silent',
        define: { 'process.env.NODE_ENV': '"development"' },
        plugins,
      });
      return out.outputFiles[0].text;
    })();
    cache.set(spec, p);
    return p;
  };
  bundleDep.cache = cache;
  return bundleDep;
}
