import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { SquintPlugin } from 'squint-cljs/webpack';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default {
  mode: 'development',
  // The compiled cljs entry (SquintPlugin compiles src/*.cljs -> js/ first).
  entry: './js/app.js',
  output: { filename: 'main.js', publicPath: '/' },
  // repl-mode output uses top-level await (await import('squint-cljs/core.js')).
  experiments: { topLevelAwait: true },
  plugins: [new SquintPlugin({ nreplPort: 1341, wsPort: 1342 })],
  devServer: {
    port: 5299,
    // :hot :ws owns cljs reload; keep webpack from also reloading the page
    // (that would wipe REPL state - the design's fallback needs auto-refresh off).
    hot: false,
    liveReload: false,
    static: { directory: resolve(__dirname, 'public') },
  },
};
