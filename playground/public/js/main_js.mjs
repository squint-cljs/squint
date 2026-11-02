import { default_extensions, complete_keymap } from '@nextjournal/clojure-mode';
import { history, historyKeymap } from '@codemirror/commands';
import * as eval_region from '@nextjournal/clojure-mode/extensions/eval-region';
import { EditorView, drawSelection, keymap } from '@codemirror/view';
import { Compartment, EditorState } from '@codemirror/state';
import { HighlightStyle, syntaxHighlighting, defaultHighlightStyle, foldGutter, syntaxTree } from '@codemirror/language';
import { setDiagnostics, lintGutter } from '@codemirror/lint';
import { javascript } from "@codemirror/lang-javascript";
import { tags } from '@lezer/highlight';
import prettierPluginJS from "prettier/plugins/estree.mjs";
import prettierPluginBabel from "prettier/plugins/babel.mjs";
import React from "react";
import ReactDOM from "react-dom/client";
import { Inspector, chromeDark, chromeLight } from "react-inspector";
const url = new URL(window.location.href);
import * as squint from "squint-cljs";
import * as prettier from "prettier";

const theme = EditorView.theme({
  '.cm-content': {
    whitespace: 'pre-wrap',
    passing: '10px 0',
    flex: '1 1 0',
  },

  '&.cm-focused': { outline: '0 !important' },
  '.cm-line': {
    padding: '0 9px',
    'line-height': '1.6',
    'font-size': '16px',
    'font-family': 'var(--code-font)',
  },
  '.cm-matchingBracket': {
    'border-bottom': '1px solid var(--teal-color)',
    color: 'inherit',
  },
  '.cm-gutters': {
    background: 'transparent',
    border: 'none',
  },
  '.cm-gutterElement': { 'margin-left': '5px' },
  // only show cursor when focused
  '.cm-cursor': { visibility: 'hidden' },
  '&.cm-focused .cm-cursor': { visibility: 'visible' },
});

const darkTheme = EditorView.theme({
  '&': { color: '#dce4ef', backgroundColor: 'var(--bg)' },
  '.cm-content': { caretColor: '#dce4ef' },
  '&.cm-focused .cm-cursor': { borderLeftColor: '#dce4ef' },
  '.cm-gutters': { backgroundColor: 'var(--bg)', color: '#9aa9bc', border: 'none' },
  '.cm-activeLine, .cm-activeLineGutter': { backgroundColor: '#ffffff0d' },
}, { dark: true });

const darkHighlightStyle = HighlightStyle.define([
  { tag: tags.keyword, color: '#c4a7e7' },
  { tag: [tags.atom, tags.number, tags.null], color: '#f6c177' },
  { tag: [tags.string, tags.regexp], color: '#9ccfd8' },
  { tag: [tags.comment, tags.lineComment], color: '#8292a7', fontStyle: 'italic' },
  { tag: tags.definition(tags.variableName), color: '#a7c7e7' },
  { tag: tags.typeName, color: '#f6c177' },
]);

const sourceTheme = new Compartment();
const compiledTheme = new Compartment();
const colorSchemeExtensions = () => document.documentElement.dataset.theme === 'dark'
  ? [darkTheme, syntaxHighlighting(darkHighlightStyle)]
  : syntaxHighlighting(defaultHighlightStyle);

function clearEditorSelection(view) {
  if (view.state.selection.ranges.some(range => !range.empty)) {
    view.dispatch({ selection: { anchor: view.state.selection.main.head } });
  }
}

// Both editors draw their selections; native page selections share the same
// CSS variable. Use the focused selector too, to override CodeMirror's defaults.
const selectionExtensions = [
  drawSelection(),
  EditorView.theme({
    '.cm-selectionBackground, &.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground': {
      backgroundColor: 'var(--selection-background)',
    },
  }),
  EditorView.domEventHandlers({
    blur(_event, view) {
      clearEditorSelection(view);
    },
  }),
];

const squintExtension = (opts) => {
  return keymap.of([
    { key: 'Alt-Enter', run: evalCell },
    {
      key: opts.modifier + '-Enter',
      run: evalAtCursor,
      shift: evalToplevel,
    },
  ]);
};

const urlParams = new URLSearchParams(window.location.search);
var repl = urlParams.get('repl') !== 'false';

class LazyIterable extends Array {
  constructor(arr) {
    super(arr);
  }
}

globalThis.compilerState = null;

let jsEditor = null;

async function JSEditor(js) {
  js = await prettier.format(js, { parser: "babel", plugins: [prettierPluginJS, prettierPluginBabel] });
  js = js.trim();
  const elt = document.querySelector('#compiled-code');
  const fontSizeTheme = EditorView.theme({
    $: {
      fontSize: "5pt"
    }
  });
  let extensions = [javascript({}).language, fontSizeTheme, foldGutter(), compiledTheme.of(colorSchemeExtensions()),
  selectionExtensions, EditorState.readOnly.of(true), EditorView.editable.of(false),
  EditorView.contentAttributes.of({ tabindex: '0' })];
  let state = EditorState.create({
    doc: js,
    extensions: extensions,
  });
  if (jsEditor) {
    jsEditor.destroy();
  }
  let editor = new EditorView({
    state: state,
    parent: elt,
    extensions: extensions,
  });
  jsEditor = editor;
}

let reactRoot = ReactDOM.createRoot(document.querySelector("#result"));

// react-inspector styles inline from its theme, so CSS can't size it: match the
// editor's code font here
const inspectorTheme = () => ({
  ...(document.documentElement.dataset.theme === 'dark' ? chromeDark : chromeLight),
  BASE_BACKGROUND_COLOR: 'transparent',
  BASE_FONT_SIZE: '13px', BASE_LINE_HEIGHT: 1.4,
  TREENODE_FONT_SIZE: '13px', TREENODE_LINE_HEIGHT: 1.4,
});
let displayedInspectorProps = null;
const renderInspector = () => {
  if (displayedInspectorProps) {
    reactRoot.render(React.createElement(Inspector, {
      ...displayedInspectorProps, theme: inspectorTheme(),
    }));
  }
};

// evals by blob URL, to map frames from code that earlier evals defined
const evals = new Map();

let sourcePosition = (segments, [line, col]) => {
  let found = null;
  for (const [gl, gc, sl, sc] of segments) {
    if (gl > line || (gl === line && gc > col)) break;
    found = [sl, sc];
  }
  return found;
};

// origin is the code's editor range [pos, end) and its boilerplate line count
let editorPosition = (origin, [line, col]) => {
  if (line < origin.skip) return null;
  const doc = editor.state.doc;
  const first = doc.lineAt(Math.min(origin.pos, doc.length));
  const n = first.number + line - origin.skip;
  if (n > doc.lines) return null;
  const docLine = doc.line(n);
  return docLine.from + Math.min((line === origin.skip ? origin.pos - first.from : 0) + col, docLine.length);
};

let stackPosition = (e) => {
  for (const [, blobUrl, line, col] of String(e?.stack ?? '').matchAll(/(blob:\S+?):(\d+):(\d+)/g)) {
    const run = evals.get(blobUrl);
    const src = run?.segments && sourcePosition(run.segments, [line - 1 - run.lineShift, col - 1]);
    const pos = src && editorPosition(run.origin, src);
    if (pos != null) return pos;
  }
  return null;
};

let compilePosition = (data, origin) => {
  // for an unclosed delimiter, mark where it opened
  const opened = data?.['edamame/opened-delimiter-loc'];
  const src = opened?.row != null ? [opened.row - 1, opened.col - 1]
            : data?.line != null ? [data.line - 1, (data.column ?? 1) - 1]
            : null;
  return src && editorPosition(origin, src);
};

let showError = (pos, message) => {
  // underline the whole form, not its opening bracket
  let node = syntaxTree(editor.state).resolveInner(pos, 1);
  while (node.to - node.from <= 1 && node.parent && node.parent.from === pos
         && node.parent.type.name !== 'Program') {
    node = node.parent;
  }
  const to = node.from === pos ? node.to : Math.min(pos + 1, editor.state.doc.length);
  editor.dispatch(setDiagnostics(editor.state, [{ from: pos, to, severity: 'error', message }]));
  editor.dispatch({ effects: EditorView.scrollIntoView(pos, { y: 'nearest' }) });
};

// The error slot below Compiled JS shows the last run's error, located or not.
// Errors with a source location are also underlined in the editor (showError).
let showErrorPanel = (message) => {
  document.getElementById('error-message').textContent = message;
  document.getElementById('error-slot').hidden = false;
};

let clearErrors = () => {
  editor.dispatch(setDiagnostics(editor.state, []));
  document.getElementById('error-message').textContent = '';
  document.getElementById('error-slot').hidden = true;
};

let replCounter = 0;
globalThis.squintRepl = {};

// runs the classic script at blobUrl, resolves to what it passes to squintRepl[id]
let runReplScript = (blobUrl, id) => new Promise((resolve, reject) => {
  const script = document.createElement('script');
  const onError = (e) => {
    if (e.filename !== blobUrl) return;
    e.preventDefault();
    reject(e.error);
  };
  globalThis.squintRepl[id] = resolve;
  window.addEventListener('error', onError);
  script.onload = script.onerror = () => {
    window.removeEventListener('error', onError);
    delete globalThis.squintRepl[id];
    script.remove();
    reject(new Error('REPL script did not run'));
  };
  script.src = blobUrl;
  document.head.append(script);
});

let evalCode = async (code, origin) => {
  // drop whatever is in the result panel: a previous error must not linger
  // when the next run succeeds (non-REPL runs render no result of their own)
  displayedInspectorProps = null;
  reactRoot.render(null);
  clearErrors();
  const run = { origin, segments: null, lineShift: repl ? 1 : 0 };
  if (!repl) evals.clear();
  let compiled = false;
  try {
    let importSource = url.searchParams.get('jsx.import-source') || 'react';
    // In REPL mode, :repl-return wraps the top-level value in [v] so a Promise
    // the user returned isn't auto-unwrapped by the async eval IIFE; we unbox
    // below before rendering, so e.g. `(js/Promise.resolve 1)` shows as
    // Promise(1) rather than just 1.
    let opts = { repl: repl, 'elide-exports': repl, context: repl ? 'repl-return' : 'statement',
                 'source-map': true,
                 "jsx-runtime": { "import-source": importSource, development: true }
               };
    // only REPL evals build on earlier state; a non-REPL run compiles the whole
    // document as a fresh module, and carried-over vars emit exports for names
    // the document no longer defines
    globalThis.compilerState = compileStringEx(`${code}`, opts, repl ? globalThis.compilerState : null);
    let js = globalThis.compilerState.javascript;
    run.segments = globalThis.compilerState['source-map-segments'];
    compiled = true;
    if (dev) {
      console.log("Loading local squint libs");
      // Rewrite to absolute URLs: Blob URLs used for non-REPL eval have an
      // opaque origin that can't resolve relative specifiers. Anchor on the
      // page origin — import.meta.url can point to an unexpected bundle path
      // under Vite.
      //
      // Route everything through /src/squint/ (matching the importmap) so
      // user-compiled code and the runtime modules (test.js, multi.js) end
      // up importing the SAME URL for core.js. Previously user code went
      // via /js/squint-local/core.js → Vite-transformed shim →
      // /@fs/.../src/squint/core.js, while the runtime's bare specifier
      // resolved via the importmap to /src/squint/core.js — two URLs,
      // two ES-module instances, two _metaSym symbols, and with-meta
      // written through one was invisible to meta in the other.
      const base = `${window.location.origin}/src/squint/`;
      js = js.replaceAll("'squint-cljs/src/squint/", `'${base}`);
      js = js.replaceAll("'squint-cljs/", `'${base}`);
    }
    JSEditor(js);
    // a classic script keeps the frames JavaScriptCore drops in eval and strict code
    const id = ++replCounter;
    const source = repl ? `squintRepl[${id}]((async function() {\n${js}\n})());` : js;
    const blobUrl = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
    evals.set(blobUrl, run);
    let result;
    try {
      if (repl) result = await runReplScript(blobUrl, id);
      else await import(/* @vite-ignore */blobUrl);
    } finally {
      URL.revokeObjectURL(blobUrl);
    }
    if (repl) {
      // unbox the [v] wrapper added by :repl-return; forms like require/ns
      // emit no wrapper so the IIFE resolves to undefined
      result = result?.[0];
      // If the user returned a Promise, race it against a short timeout so the
      // playground can still drill into the resolved value via Inspector while
      // surfacing that it came wrapped in a Promise. Pending falls back to a
      // plain text marker.
      let promiseTag = null;
      if (result instanceof Promise) {
        const settled = result.then(v => ({tag: 'resolved', val: v}),
                                    e => ({tag: 'rejected', val: e}));
        const timer = new Promise(r => setTimeout(() => r({tag: 'pending'}), 1000));
        const r = await Promise.race([settled, timer]);
        promiseTag = r.tag;
        result = r.val; // undefined for pending; fine, not rendered via Inspector
      }
      if (result && result?.constructor?.name === 'LazyIterable') {
        let stdlib = (await import("squint-cljs/core.js"));
        let take_fn = stdlib.take;
        let concat_fn = stdlib.concat;
        let end = {};
        result = [...take_fn(10, concat_fn(result, [end]))];
        let isEnd = result[result.length - 1] !== end;
        if (isEnd) result.push('...')
        else result.pop();
        result = new LazyIterable(result);
      }
      // Use Inspector's root `name` to label a Promise wrapper: renders as one
      // expandable line ("Promise: <value>") instead of stitching text spans
      // around a separate tree, which looked broken on multi-line values.
      if (promiseTag === 'pending') {
        reactRoot.render(React.createElement('span', null, '#<Promise pending>'));
      } else {
        displayedInspectorProps = promiseTag === 'rejected' ? { name: 'Promise rejected', data: result }
                                : promiseTag === 'resolved' ? { name: 'Promise', data: result }
                                : { data: result };
        renderInspector();
      }
      if (docChanged) {
        // edited code is no longer the preset, so a reload restores it from localStorage
        url.searchParams.delete('src');
        url.searchParams.delete('example');
        window.history.replaceState(null, null, url);
        localStorage.setItem('document', editor.state.doc.toString());
        docChanged = false;
      }
    }
  } catch (e) {
    // render through the React root: writing to #result directly detaches the
    // nodes React still tracks, and later renders update orphans
    reactRoot.render(React.createElement('span', { style: { whiteSpace: 'pre-wrap' } },
                                         e.message + '\n\n' + e.stack));
    console.error(e);
    showErrorPanel(e.message);
    const pos = compiled ? stackPosition(e) : compilePosition(e?.data, origin);
    if (pos != null) showError(pos, e.message);
  }
};

let evalCell = (opts) => {
  let js = opts.state.doc.toString();
  evalCode(js, { pos: 0, end: js.length, skip: 0 });
  return true;
};

let evalAtCursor = (opts) => {
  if (!repl) return;
  let node = eval_region.node_at_cursor(opts.state);
  if (!node) return;
  let js = opts.state.sliceDoc(node.from, node.to);
  if (!js.trim()) return;
  evalCode(js, { pos: node.from, end: node.to, skip: 0 });
  return true;
};

let docChanged = false;

let evalToplevel = (js) => {
  if (!repl) return;
  return true;
};
let updateExt = EditorView.updateListener.of((update) => {
  if (update.docChanged) {
    docChanged = true;
    for (const [blobUrl, run] of evals) {
      const { pos, end } = run.origin;
      let edited = false;
      update.changes.iterChangedRanges((from, to) => { if (from < end && to > pos) edited = true; });
      if (edited) evals.delete(blobUrl);
      else Object.assign(run.origin, { pos: update.changes.mapPos(pos, 1), end: update.changes.mapPos(end, -1) });
    }
  }
});
const platform = navigator.platform;
const isMac = platform.startsWith('Mac') || platform.startsWith('iP');
let modifier = localStorage.getItem("editor.modifier") || (isMac ? 'Meta' : 'Ctrl');
// every place that names the eval shortcut (the REPL-mode tooltip and helper text)
if (!isMac || modifier === 'Ctrl') document.querySelectorAll('.modifier-desc').forEach((el) => { el.innerText = 'Ctrl'; });
let extensions = [
  history(),
  theme,
  foldGutter(),
  sourceTheme.of(colorSchemeExtensions()),
  selectionExtensions,
  // unwrap on macOS Option+S, until a clojure-mode release binds it
  keymap.of([{ mac: 'Alt-\u00df', run: complete_keymap.find(b => b.key === 'Alt-s').run, preventDefault: true }]),
  keymap.of(complete_keymap),
  keymap.of(historyKeymap),
  squintExtension({ modifier: modifier }),
  eval_region.extension({ modifier: modifier }),
  updateExt,
  lintGutter(),
  ...default_extensions,
];

let doc = `
      (comment
  (fizz-buzz 1)
  (fizz-buzz 3)
  (fizz-buzz 5)
  (fizz-buzz 15)
  (fizz-buzz 17)
  (fizz-buzz 42))

(defn fizz-buzz [n]
  (condp (fn [a b] (zero? (mod b a))) n
    15 "fizzbuzz"
    3  "fizz"
    5  "buzz"
    n))

(require '["https://esm.sh/canvas-confetti@1.6.0$default" :as confetti])

(do
  (js-await (confetti))
  (+ 1 2 3))
`.trim();

const aocDoc = `
;; Helper functions:
;; (fetch-input year day) - get AOC input
;; (append str) - append str to DOM
;; (spy x) - log x to console and return x

;; Remember to update the year and day in the fetch-input call.
(def input (->> (js-await (fetch-input 2022 1))
             #_spy
             str/split-lines
             (mapv parse-long)))

(defn part-1
  []
  (->> input
    (partition-by nil?)
    (take-nth 2)
    (map #(apply + %))
    (apply max)))

(defn part-2
  []
  (->> input
    (partition-by nil?)
    (take-nth 2)
    (map #(apply + %))
    (sort-by -)
    (take 3)
    (apply +)))

(time (part-1))
#_(time (part-2))`.trim();
const aocBoilerplateUrl = 'https://gist.githubusercontent.com/borkdude/cf94b492d948f7f418aa81ba54f428ff/raw/3b58a80710fbbbda091966c8eb85323eef4652c1/aoc_ui.cljs';

const boilerplate = urlParams.get('boilerplate');
let boilerplateSrc;
if (boilerplate) {
  boilerplateSrc = await fetch(boilerplate).then((p) => p.text());
}

function base64ToBytes(b64) {
  const bin = atob(b64);
  return Uint8Array.from(bin, c => c.charCodeAt(0));
}

function base64ToUtf8(b64) {
  const bytes = base64ToBytes(b64);
  return new TextDecoder().decode(bytes);
}

function bytesToBase64(bytes) {
  let binary = '';
  const len = bytes.length;
  for (let i = 0; i < len; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

async function gzipUtf8ToBytes(str) {
  const encoder = new TextEncoder();
  const data = encoder.encode(str);
  const cs = new CompressionStream('gzip');
  const compressedStream = new Response(data).body.pipeThrough(cs);
  const compressedArrayBuffer = await new Response(compressedStream).arrayBuffer();
  return new Uint8Array(compressedArrayBuffer);
}

async function gunzipBytesToUtf8(uint8arr) {
  const ds = new DecompressionStream('gzip');
  const decompressedStream = new Blob([uint8arr]).stream().pipeThrough(ds);
  const decompressedArrayBuffer = await new Response(decompressedStream).arrayBuffer();
  return new TextDecoder().decode(decompressedArrayBuffer);
}

async function zipCode(code) {
  const zippedBytes = await gzipUtf8ToBytes(code);
  const base64 = bytesToBase64(zippedBytes);
  return 'gzip:' + base64;
}

// Examples served from ./examples/, keyed by the ?example= value. `params` are
// URL settings the example needs, applied whenever it loads. The dropdown lists
// them under their `group`, in this order.
const examples = {
  'pinball': { label: 'Pinball', group: 'DOM examples', file: 'pinball.cljs' },
  'tictactoe': { label: 'Tic-tac-toe', group: 'DOM examples', file: 'tictactoe.cljs' },
  'wordle': { label: 'Wordle', group: 'DOM examples', file: 'wordle.cljs' },
  'react': { label: 'React', group: 'DOM examples', file: 'react.cljs' },
  'preact': { label: 'Preact', group: 'DOM examples', file: 'preact.cljs',
              params: { 'jsx.import-source': 'https://esm.sh/preact@10.19.2' } },
  'preact-hiccup': { label: 'Preact + hiccup', group: 'DOM examples', file: 'preact-hiccup.cljs' },
  'mobx': { label: 'MobX', group: 'DOM examples', file: 'mobx.cljs' },
  'mobx-atoms': { label: 'MobX + atoms', group: 'DOM examples', file: 'mobx-atoms.cljs' },
  'solidjs': { label: 'SolidJS', group: 'DOM examples', file: 'solidjs.cljs',
               params: { 'jsx.import-source': 'https://esm.sh/solid-js@1.8.10/h' } },
  'vue': { label: 'Vue.js', group: 'DOM examples', file: 'vue.cljs' },
  'mithril': { label: 'Mithril', group: 'DOM examples', file: 'mithril.cljs' },
  'vanjs': { label: 'VanJS', group: 'DOM examples', file: 'vanjs.cljs' },
  'web-components': { label: 'Web components', group: 'DOM examples', file: 'web-components.cljs' },
  'lit': { label: 'Lit', group: 'DOM examples', file: 'lit.cljs' },
  'quill': { label: 'Quill', group: 'DOM examples', file: 'quill.cljs' },
  'vega-lite': { label: 'Vega-lite', group: 'DOM examples', file: 'vega-lite.cljs' },
  'rough': { label: 'Rough', group: 'DOM examples', file: 'rough.cljs' },
  'game-of-life': { label: 'Game of life', group: 'DOM examples', file: 'game-of-life.cljs' },
  'snabbdom': { label: 'Snabbdom', group: 'DOM examples', file: 'snabbdom.cljs' },
  'ohms-law': { label: "Ohm's law", group: 'DOM examples', file: 'ohms-law.cljs' },
  'idiomorph': { label: 'Idiomorph', group: 'DOM examples', file: 'idiomorph.cljs' },
  'eucalypt': { label: 'Eucalypt', group: 'DOM examples', file: 'eucalypt.cljs' },
  'docx': { label: 'docx', group: 'DOM examples', file: 'docx.cljs' },
  'immer': { label: 'Immer', group: 'DOM examples', file: 'immer.cljs' },
  'edn-data': { label: 'edn-data', group: 'Examples (no DOM)', file: 'edn-data.cljs' },
  'records-and-tuples': { label: 'TC39 Records and Tuples', group: 'Examples (no DOM)', file: 'records-and-tuples.cljs' },
  'immutable-js': { label: 'Immutable-js', group: 'Examples (no DOM)', file: 'immutable-js.cljs' },
  'date-fns': { label: 'Date-fns', group: 'Examples (no DOM)', file: 'date-fns.cljs' },
  'defmulti': { label: 'defmulti', group: 'Examples (no DOM)', file: 'defmulti.cljs' },
  'mutative': { label: 'mutative', group: 'Examples (no DOM)', file: 'mutative.cljs' },
  'generators': { label: 'Generators', group: 'Examples (no DOM)', file: 'generators.cljs' },
  'umd-module': { label: 'Loading a UMD module', group: 'Examples (no DOM)', file: 'umd-module.cljs' },
};
// every param any example sets, cleared before loading another one
const exampleParamNames = [...new Set(Object.values(examples)
  .flatMap((e) => Object.keys(e.params || {})))];
const exampleUrl = (file) => new URL(`./examples/${file}`, window.location.href).href;

const exampleName = urlParams.get('example');
const example = examples[exampleName] || null;
// Tells the stylesheet to hold the DOM frame open while the example loads.
document.documentElement.classList.toggle('dom-example', example?.group === 'DOM examples');
if (example) {
  for (const [k, v] of Object.entries(example.params || {})) url.searchParams.set(k, v);
  window.history.replaceState(null, null, url);
}

let src = example ? exampleUrl(example.file) : urlParams.get('src'), isGzip = false;

if (src) {
  if (/http(s)?:\/\/.*/.test(src)) {
    src = await fetch(src).then((p) => p.text());
  } else {
    if (src.startsWith('gzip:')) {
      isGzip = true;
      src = src.substring(5);
    }
    if (isGzip) {
      const bytes = base64ToBytes(src);
      src = await gunzipBytesToUtf8(bytes);
    } else {
      src = base64ToUtf8(src);
    }
  }
  doc = src;
} else {
  doc = localStorage.getItem('document') || doc;
}
let state = EditorState.create({
  doc: doc,
  extensions: extensions,
});
let editorElt = document.querySelector('#editor');
let editor = new EditorView({
  state: state,
  parent: editorElt,
  extensions: extensions,
});
globalThis.editor = editor;

// A click on non-focusable page content may leave an editor focused, so blur
// alone doesn't cover every outside click. Keep selections in the clicked pane.
document.addEventListener('pointerdown', event => {
  for (const view of [editor, jsEditor]) {
    if (view && !view.dom.contains(event.target)) clearEditorSelection(view);
  }
}, { capture: true });

new MutationObserver(() => {
  editor.dispatch({ effects: sourceTheme.reconfigure(colorSchemeExtensions()) });
  if (jsEditor) {
    jsEditor.dispatch({ effects: compiledTheme.reconfigure(colorSchemeExtensions()) });
  }
  renderInspector();
}).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

var dev = JSON.parse(urlParams.get('dev')) ?? location.hostname === 'localhost';


var squintCompiler = squint;
if (dev) {
  console.log('Loading development squint compiler');
  // squintCompiler = await import('./squint-local/index.js');
}

var compileStringEx = squintCompiler.compileStringEx;
window.compile = () => {
  let code = editor.state.doc.toString();
  const prefix = (boilerplateSrc || '') + '\n\n';
  evalCode(prefix + code, { pos: 0, end: code.length, skip: prefix.split('\n').length - 1 });
};

window.share = async () => {
  const code = editor.state.doc.toString().trim();
  const src = await zipCode(code);
  url.searchParams.set('src', src);
  url.searchParams.delete('example');
  window.location = url;
};
window.blankAOC = async () => {
  const code = await zipCode(aocDoc);
  const url = new URL(window.location);
  url.searchParams.set('src', code);
  url.searchParams.delete('example');
  for (const k of exampleParamNames) url.searchParams.delete(k);
  url.searchParams.set('boilerplate', aocBoilerplateUrl);
  url.searchParams.set('repl', true);

  window.location = url;
};

window.loadExample = (name) => {
  if (name === 'blank-aoc') return window.blankAOC();
  if (!examples[name]) return;
  // read by the inline script in <head> so the next page load starts with the DOM frame open
  try {
    sessionStorage.setItem('playground.dom-example', String(examples[name].group === 'DOM examples'));
    // the frame's current height, so the next page starts at the same height (not in full screen)
    const frame = document.getElementById('dom-frame');
    if (frame.offsetHeight && !document.body.classList.contains('dom-fullscreen')) {
      sessionStorage.setItem('playground.dom-height', String(Math.round(frame.getBoundingClientRect().height)));
    }
  } catch (e) {}
  const url = new URL(window.location);
  for (const k of exampleParamNames) url.searchParams.delete(k);
  for (const [k, v] of Object.entries(examples[name].params || {})) url.searchParams.set(k, v);
  url.searchParams.set('example', name);
  url.searchParams.set('repl', true);
  url.searchParams.delete('src');
  url.searchParams.delete('boilerplate');
  window.location = url;
};

// fill the dropdown from the registry, one <optgroup> per group, and show
// which example the page was opened with
const exampleSelect = document.getElementById('example-select');
if (exampleSelect) {
  // Same group order as the popover menu, whatever the registry order. AOC is
  // not a file example, so its group is added here, last. A blank row
  // separates the groups.
  const optgroup = (label) => Object.assign(document.createElement('optgroup'), { label });
  const groups = new Map(['DOM examples', 'Examples (no DOM)'].map((g) => [g, optgroup(g)]));
  for (const [name, { label, group }] of Object.entries(examples)) {
    groups.get(group).append(new Option(label, name));
  }
  const aocGroup = optgroup('Advent of Code');
  aocGroup.append(new Option('Blank AOC playground', 'blank-aoc'));
  [...groups.values(), aocGroup].forEach((g, i) => {
    if (i > 0) {
      const spacer = new Option('\u00a0');
      spacer.disabled = true;
      exampleSelect.append(spacer);
    }
    exampleSelect.append(g);
  });
  exampleSelect.value = boilerplate === aocBoilerplateUrl ? 'blank-aoc'
    : example ? exampleName
    : '';
}

// The popover menu that replaces the select on wider screens. Each registry
// group fills the list in its own column. The current example is marked.
const exampleMenu = document.getElementById('example-menu');
if (exampleMenu) {
  const lists = {
    'DOM examples': exampleMenu.querySelector('#example-menu-dom ul'),
    'Examples (no DOM)': exampleMenu.querySelector('#example-menu-no-dom ul'),
  };
  for (const [name, { label, group }] of Object.entries(examples)) {
    const item = document.createElement('button');
    item.type = 'button';
    item.setAttribute('role', 'menuitem');
    item.textContent = label;
    item.addEventListener('click', () => window.loadExample(name));
    if (name === exampleName && example) item.setAttribute('aria-current', 'true');
    const li = document.createElement('li');
    li.append(item);
    lists[group]?.append(li);
  }
  const isAoc = boilerplate === aocBoilerplateUrl;
  if (isAoc) exampleMenu.querySelector('#example-menu-aoc button').setAttribute('aria-current', 'true');
  document.getElementById('example-trigger').textContent =
    example ? example.label : isAoc ? 'Blank AOC playground' : 'Choose…';

  // On open, focus the current item (or the first). Arrow keys, Home and End
  // move through the items in reading order. Escape and outside clicks are
  // handled by the popover itself.
  const items = () => [...exampleMenu.querySelectorAll('[role="menuitem"]')];
  exampleMenu.addEventListener('toggle', (e) => {
    if (e.newState !== 'open') return;
    (exampleMenu.querySelector('[aria-current="true"]') || items()[0]).focus();
  });
  exampleMenu.addEventListener('keydown', (e) => {
    const all = items();
    const i = all.indexOf(document.activeElement);
    const next = { ArrowDown: i + 1, ArrowRight: i + 1, ArrowUp: i - 1, ArrowLeft: i - 1,
                   Home: 0, End: all.length - 1 }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    all[(next + all.length) % all.length].focus();
  });
}

window.changeREPL = (target) => {
  displayedInspectorProps = null;
  reactRoot.render(null);
  // vars from the other mode don't carry over: their definitions are gone
  globalThis.compilerState = null;
  evals.clear();
  if (target.checked) {
    repl = true;
    window.compile();
  } else {
    repl = false;
    window.compile();
  }
  url.searchParams.set('repl', repl);
  window.history.replaceState(null, null, url);
};
if (repl) {
  document.getElementById('replCheckBox').checked = true;
}
window.compile();
