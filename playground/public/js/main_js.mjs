import { default_extensions, complete_keymap } from '@nextjournal/clojure-mode';
import { history, historyKeymap } from '@codemirror/commands';
import * as eval_region from '@nextjournal/clojure-mode/extensions/eval-region';
import { EditorView, drawSelection, keymap } from '@codemirror/view';
import { EditorState } from '@codemirror/state';
import { syntaxHighlighting, defaultHighlightStyle, foldGutter } from '@codemirror/language';
import { javascript } from "@codemirror/lang-javascript";
import prettierPluginJS from "prettier/plugins/estree.mjs";
import prettierPluginBabel from "prettier/plugins/babel.mjs";
import React from "react";
import ReactDOM from "react-dom/client";
import { Inspector } from "react-inspector";
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
  const elt = document.querySelector('#compiledCode');
  const fontSizeTheme = EditorView.theme({
    $: {
      fontSize: "5pt"
    }
  });
  let extensions = [javascript({}).language, fontSizeTheme, foldGutter(), syntaxHighlighting(defaultHighlightStyle),
  EditorView.editable.of(false)];
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

let evalCode = async (code) => {
  try {
    let importSource = url.searchParams.get('jsx.import-source') || 'react';
    let opts = { repl: repl, 'elide-exports': repl, context: repl ? 'return' : 'statement',
                 "jsx-runtime": { "import-source": importSource, development: true }
               };
    globalThis.compilerState = compileStringEx(`${code}`, opts, globalThis.compilerState);
    let js = globalThis.compilerState.javascript;
    if (dev) {
      console.log("Loading local squint libs");
      js = js.replaceAll("'squint-cljs/", "'./squint-local/");
    }
    JSEditor(js);
    if (!repl) {
      const encodedJs = encodeURIComponent(js);
      const dataUri =
        'data:text/javascript;charset=utf-8;eval=' + Date.now() + ',' + encodedJs;
      let result = await import(/* @vite-ignore */dataUri);
    } else {
      let result = await eval(`(async function() { ${js} })()`);
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
      reactRoot.render(React.createElement(Inspector, { data: result }));
      if (docChanged) {
        url.searchParams.delete('src');
        window.history.replaceState(null, null, url);
        localStorage.setItem('document', editor.state.doc.toString());
        docChanged = false;
      }
    }
  } catch (e) {
    document.querySelector('#result').innerText =
      e.message + '\n\n' + e.stack;
    console.error(e);
  }
};

let evalCell = (opts) => {
  let js = opts.state.doc.toString();
  evalCode(js);
  return true;
};

let evalAtCursor = (opts) => {
  if (!repl) return;
  let js = eval_region.cursor_node_string(opts.state).toString();
  evalCode(js);
  return true;
};

let docChanged = false;

let evalToplevel = (js) => {
  if (!repl) return;
  return true;
};
let updateExt = EditorView.updateListener.of((update) => {
  if (update.docChanged) docChanged = true;
});
const platform = navigator.platform;
const isMac = platform.startsWith('Mac') || platform.startsWith('iP');
let modifier = localStorage.getItem("editor.modifier") || (isMac ? 'Meta' : 'Ctrl');
if (!isMac || modifier === 'Ctrl') document.getElementById('modifierDesc').innerText = 'Ctrl'
let extensions = [
  history(),
  theme,
  foldGutter(),
  syntaxHighlighting(defaultHighlightStyle),
  drawSelection(),
  keymap.of(complete_keymap),
  keymap.of(historyKeymap),
  squintExtension({ modifier: modifier }),
  eval_region.extension({ modifier: modifier }),
  updateExt,
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

let src = urlParams.get('src'), isGzip = false;

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
var dev = JSON.parse(urlParams.get('dev')) ?? location.hostname === 'localhost';


var squintCompiler = squint;
if (dev) {
  console.log('Loading development squint compiler');
  // squintCompiler = await import('./squint-local/index.js');
}

var compileStringEx = squintCompiler.compileStringEx;
window.compile = () => {
  let code = editor.state.doc.toString();
  code = (boilerplateSrc || '') + '\n\n' + code;
  evalCode(code);
};

window.share = async () => {
  const code = editor.state.doc.toString().trim();
  const src = await zipCode(code);
  url.searchParams.set('src', src);
  window.location = url;
};
window.blankAOC = async () => {
  const code = await zipCode(aocDoc);
  const url = new URL(window.location);
  url.searchParams.set('src', code);
  url.searchParams.set('boilerplate', aocBoilerplateUrl);
  url.searchParams.set('repl', true);

  window.location = url;
};

window.changeREPL = (target) => {
  document.getElementById('result').innerText = '';
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
