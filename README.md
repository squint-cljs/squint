<img src="./logo/logo.svg" width="100%">

[![Clojars Project](https://img.shields.io/clojars/v/io.github.squint-cljs/squint.svg)](https://clojars.org/io.github.squint-cljs/squint)

Squint is a light-weight dialect of ClojureScript with a compiler and standard
library.

Squint is not intended as a full replacement for ClojureScript but as a tool to
target JS when you need something more light-weight in terms of interop and
bundle size. The most significant difference with CLJS is that squint uses only
built-in JS data structures. Squint's output is designed to work well with ES
modules.

If you want to use squint, but with the normal ClojureScript standard library
and data structures, check out [cherry](https://github.com/squint-cljs/cherry).

## Quickstart

``` shell
$ mkdir squint-test && cd squint-test
$ npm init -y
$ npm install squint-cljs@latest
```

Create a `.cljs` file, e.g. `example.cljs`:

``` clojure
(ns example
  (:require ["fs" :as fs]
            ["url" :refer [fileURLToPath]]))

(println (fs/existsSync (fileURLToPath js/import.meta.url)))

(defn foo [{:keys [a b c]}]
  (+ a b c))

(println (foo {:a 1 :b 2 :c 3}))
```

Then compile and run (`run` does both):

```
$ npx squint run example.cljs
true
6
```

Run `npx squint --help` to see all command line options.

## Why Squint

Squint lets you write CLJS syntax but emits small JS output, while still having
parts of the CLJS standard library available (ported to mutable data structures,
so with caveats). This may work especially well for projects e.g. that you'd
like to deploy on CloudFlare workers, node scripts, Github actions, etc. that
need the extra performance, startup time and/or small bundle size.

## Talk

[![ClojureScript re-imagined at Dutch Clojure Days 2022](https://img.youtube.com/vi/oCd74TQ-gf4/0.jpg)](https://www.youtube.com/watch?v=oCd74TQ-gf4)

([slides](https://www.dropbox.com/s/955jgzy6hgpx67r/dcd2022-cljs-reimagined.pdf?dl=0))

## Differences with ClojureScript

- The CLJS standard library is replaced with `"squint-cljs/core.js"`, a smaller re-implemented subset
- Keywords are translated into strings
- Maps, sequences and vectors are represented as mutable objects and arrays
- Standard library functions never mutate arguments if the CLJS counterpart do
  not do so. Instead, shallow cloning is used to produce new values, a pattern that JS developers
  nowadays use all the time: `const x = [...y];`
- Most functions return arrays, objects or `Symbol.iterator`, not custom data structures
- Supports async/await: `(def x (await y))`. Async functions must be marked
  with `^:async`: `(defn ^:async foo [])`. The legacy spellings `js-await`
  and `js/await` are still accepted.
- `assoc!`, `dissoc!`, `conj!`, etc. perform in place mutation on objects
- `assoc`, `dissoc`, `conj`, etc. return a new shallow copy of objects
- `equiv` compares by identity or `-equiv`, unlike `=`, which deep-compares
  plain data. `hash` follows `equiv`: plain mutable objects and arrays hash
  by reference
- `pr-str` and `prn` print EDN with the idea that you can paste the output back into your programs
  - JavaScript `Map`s are printed like maps with a `#js/Map` prefix
- Since JavaScript only supports strings for keys in maps, any data structures used as keys will be stringified

If you are looking for closer ClojureScript semantics, take a look at [Cherry 🍒](https://github.com/squint-cljs/cherry).

## Articles

- [Writing a Cloudflare worker with squint and bun](https://blog.michielborkent.nl/squint-cloudflare-bun.html)
- [Porting a CLJS project to squint](https://blog.michielborkent.nl/porting-cljs-project-to-squint.html)

## Projects using squint

- [cljdoc](https://cljdoc.org) ([source](https://github.com/cljdoc/cljdoc/tree/master/front-end/src/cljdoc/client))
- [epupp](https://github.com/PEZ/epupp): a browser extension for tampering with web pages, live or with userscripts
- [hyper](https://github.com/dynamic-alpha/hyper): a reactive server-rendered web framework for Clojure
- [weave](https://nakkaya.github.io/weave/reference/40_javascript/): a Clojure web framework that compiles client-side code with squint
- [Eucalypt](https://chr15m.github.io/eucalypt/): a Reagent clone in squint
- [pavlov](https://github.com/thomascothran/pavlov#experimental-squint-support): behavioral programming for Clojure
- [clj-proj](https://github.com/willcohen/clj-proj): PROJ for the JVM and JS
- [datastar-expressions](https://github.com/outskirtslabs/datastar-expressions): Write 🚀 datastar expressions using Clojure (squint)
- [longdown](https://github.com/dundalek/longdown): convert longform markdown files to the Logseq outline format
- [simpleviz](https://github.com/sstoehrm/simpleviz): EDN-driven graph visualization
- [hiccupad](https://brianium.github.io/hiccupad/) ([source](https://github.com/brianium/hiccupad))
- [Feles Tales](https://rafaeldelboni.itch.io/feles-tales) ([source](https://github.com/rafaeldelboni/feles-tales)): a game about a cat sneaking and stealing stuff
- [sapper](https://github.com/tonsky/sapper): Minesweeper
- [Zenith](https://tofutheloafu.itch.io/zenith): a game developed for the Lisp Game Jame 2024 by Trevor
- [wordle](https://github.com/jackdbd/squint-wordle)
- [static search index for Tumblr](https://github.com/holyjak/clj-tumblr-summarizer/commit/a8b2ca8a9f777e4a9059fa0f1381ded24e5f1a0f)

## Libraries supporting squint

- [@nextjournal/clojure-mode](https://github.com/nextjournal/clojure-mode)
- [replicant](https://codeberg.org/cjohansen/replicant)
- [babashka.cli](https://github.com/babashka/cli#javascript)
- [babashka.fs](https://github.com/babashka/fs#nodejs)
- [nexus](https://github.com/cjohansen/nexus)

## Advent of Code

Solve [Advent of Code](https://adventofcode.com/) puzzles with squint [here](https://squint-cljs.github.io/squint/?src=OzsgSGVscGVyIGZ1bmN0aW9uczoKOzsgKGZldGNoLWlucHV0IHllYXIgZGF5KSAtIGdldCBBT0MgaW5wdXQKOzsgKGFwcGVuZCBzdHIpIC0gYXBwZW5kIHN0ciB0byBET00KOzsgKHNweSB4KSAtIGxvZyB4IHRvIGNvbnNvbGUgYW5kIHJldHVybiB4Cgo7OyBSZW1lbWJlciB0byB1cGRhdGUgdGhlIHllYXIgYW5kIGRheSBpbiB0aGUgZmV0Y2gtaW5wdXQgY2FsbC4KKGRlZiBpbnB1dCAoLT4%2BIChqcy1hd2FpdCAoZmV0Y2gtaW5wdXQgMjAyMiAxKSkKICAgICAgICAgICAgICNfc3B5CiAgICAgICAgICAgICBzdHIvc3BsaXQtbGluZXMKICAgICAgICAgICAgIChtYXB2IHBhcnNlLWxvbmcpKSkKCihkZWZuIHBhcnQtMQogIFtdCiAgKC0%2BPiBpbnB1dAogICAgKHBhcnRpdGlvbi1ieSBuaWw%2FKQogICAgKHRha2UtbnRoIDIpCiAgICAobWFwICMoYXBwbHkgKyAlKSkKICAgIChhcHBseSBtYXgpKSkKCihkZWZuIHBhcnQtMgogIFtdCiAgKC0%2BPiBpbnB1dAogICAgKHBhcnRpdGlvbi1ieSBuaWw%2FKQogICAgKHRha2UtbnRoIDIpCiAgICAobWFwICMoYXBwbHkgKyAlKSkKICAgIChzb3J0LWJ5IC0pCiAgICAodGFrZSAzKQogICAgKGFwcGx5ICspKSkKCih0aW1lIChwYXJ0LTEpKQojXyh0aW1lIChwYXJ0LTIpKQ%3D%3D&boilerplate=https%3A%2F%2Fgist.githubusercontent.com%2Fborkdude%2Fcf94b492d948f7f418aa81ba54f428ff%2Fraw%2Fa6e9992b079e20e21d753e8c75a7353c5908b225%2Faoc_ui.cljs&repl=true).

### Seqs

The seq concept in Squint builds on
[iteration
protocols](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols).

Squint has lazy seqs, but a seq is not a distinct type. Next to `seq`, squint
has `iterable`, which returns an iterable collection even when it's empty.
Unlike in CLJS, `seq?` returns true for anything that is JS iterable, so also
for vectors, strings and sets. To get the CLJS behavior, use
`(and (sequential? x) (not (vector? x)))`.

Most collections are iterable already, so `seq` and `iterable` will simply
return them.

`first`, `rest`, `map`, `reduce` et al. call `iterable` on the collection before
processing. Functions that are lazy in Clojure, like `map`, `filter`, `range`
and `concat`, return a lazy seq. Eager ones, like `keys`, `vals`, `sort` and
`reverse`, return an array.

## JSX

You can produce JSX syntax using the `#jsx` tag:

``` clojure
#jsx [:div "Hello"]
```

produces:

``` html
<div>Hello</div>
```

and outputs the `.jsx` extension automatically.

You can use Clojure expressions within `#jsx` expressions:

``` clojure
(let [x 1] #jsx [:div (inc x)])
```

Note that when using a Clojure expression, you escape the JSX context so when you need to return more JSX, use the `#jsx` once again:

``` clojure
(let [x 1]
  #jsx [:div
         (if (odd? x)
           #jsx [:span "Odd"]
           #jsx [:span "Even"])])
```

To pass props, you can use `:&`:

``` clojure
(let [props {:a 1}]
  #jsx [App {:& props}])
```

See an example of an application using JSX [here](https://squint-cljs.github.io/demos/squint/solidjs/) ([source](https://github.com/squint-cljs/squint/blob/main/examples/solid-js/src/App.cljs)).

[Play with JSX non the playground](https://squint-cljs.github.io/squint/?example=react)

## HTML

Similar to JSX, squint allows you to produce HTML as strings using hiccup notation:

``` clojure
(def my-html #html [:div "Hello"])
```

will set the `my-html` variable to an HTML object equal to
`<div>Hello</div>`. To produce a string from the HTML object, call `str` on it.

HTML objects can be nested:

``` clojure
(defn my-html [x] #html [:<> [:div "Hello"] x])
(my-html #html [:div "Goodbye"]) ;;=> Html {s: "<div>Hello</div><div>Goodbye</div>"}
```

HTML content is escaped by default:

``` clojure
(my-html #html [:div "<>"]) ;;=> Html {s: "<div>Hello</div><div>&lt;&gt;</div>"}
```

You can produce unsafe (unescaped) HTML with `[:$ ...]`:

``` clojure
(str #html
  [:<> [:$ "<!DOCTYPE html>"]
   [:html [:head] [:body]]])
;;=> "<!DOCTYPE html><html><head></head><body></body></html>"
```

Using metadata you can modify the tag function, e.g. to use this together with lit-html:

``` clojure
(ns my-app
  (:require ["lit" :as lit]))

#html ^lit/html [:div "Hello"]
```

This will produce:

``` clojure
lit/html`<div>Hello</div>`
```

See [this](https://squint-cljs.github.io/squint/?example=lit) playground example for a full example.

## Asset imports

You can do static asset imports like this:

```
["./test.json$default" :as json :with {:type :json}]
```

This will produce the following code, which tools like vite can inline at compile time:

```
import data from "../test.json$default" with { type: "json" };
```

You can also import other types of assets if using vite with `?raw`:

```
["logo.svg?raw" :as logo]
```

## Async/await

Squint supports `async/await`:

``` clojure
(defn ^:async foo [] (js/Promise.resolve 10))

(def x (await (foo)))

(println x) ;;=> 10
```

Anonymous functions must have `^:async` on the `fn` symbol or the function's name:

``` clojure
(^:async fn [] (await {}) 3)
```

## Generator functions

Generator functions must be marked with `^:gen`:

``` clojure
(defn ^:gen foo []
  (js-yield 1)
  (js-yield* [2 3])
  (let [x (inc 3)]
    (js-yield x)))

(vec (foo)) ;;=> [1 2 3 4]
```

Anonymous functions must have `^:gen` on the `fn` symbol:

``` clojure
(^:gen fn [] (js-yield 1) (js-yield 2))
```

See the [playground](https://squint-cljs.github.io/squint/?example=generators) for an example.

## Async generator functions

Async generator functions must be marked with both `^:async` and `^:gen`:

``` clojure
(defn ^:async ^:gen nums []
  (js-yield (await (js/Promise.resolve 1)))
  (js-yield 2))
```

Mark any `doseq` binding with `^:await` to iterate async iterables:

``` clojure
(doseq [^:await x (nums)] ;; emits `for await`
  (println x))

;; prints 1, 2
```

Tag the binding vector to apply to all bindings:

``` clojure
(doseq ^:await [x (nums)  ;; outer `for await`
                y (nums)] ;; inner `for await`
  (println [x y]))

;; prints [1 1], [1 2], [2 1], [2 2]
```

## Arrow functions

If for some reason you need to emit arrow functions `() => ...` rather than
`function`, you can use `:=>` metadata on the function expression, `fn` symbol
or argument vector:

``` clojure
(fn ^:=> [] 1)
```

## Defclass

See [doc/defclass.md](doc/defclass.md).

## JS API

The JavaScript API exposes the `compileString` function:

``` javascript
import { compileString } from 'squint-cljs';

const f = eval(compileString("(fn [] 1)"
                             , {"context": "expr",
                                "elide-imports": true}
                            ));

console.log(f()); // prints 1
```

## REPL

Squint has a console repl which can be started with `squint repl`.

## nREPL

A (currently immature!) nREPL implementation can be used on Node.js with:

``` clojure
squint nrepl-server :port 1888
```

Please try it out and file issues so it can be improved.

### Emacs

You can use this together with `inf-clojure` in emacs as follows:

In a `.cljs` buffer, type `M-x inf-clojure`. Then enter the startup command `npx
squint repl` (or `bunx --bun repl`) and select the `clojure` or `babashka` type
REPL. REPL away!

<img src="https://pbs.twimg.com/media/F6Pry0eWwAEwsRD?format=jpg">

## Browser REPL

The `squint-cljs/vite` plugin provides a browser REPL: it hot-reloads compiled
squint in the page and runs an nREPL server that your editor connects to,
evaluating forms in the live page. See
[doc/browser-repl.md](doc/browser-repl.md).

## Truthiness

Squint respect CLJS truth semantics: only `null`, `undefined` and `false` are non-truthy, `0` and `""` are truthy.

## Dynamic vars

A `def` with an earmuffed name (`*foo*`) is always a dynamic var in squint. It compiles to a
mutable box `{val: ...}`. References read `.val`, `set!` assigns it, and
`binding` saves, sets and restores it in a `finally`.

``` clojure
(def ^:dynamic *x* 1)

(defn f [] *x*)

(f)                  ;; => 1
(binding [*x* 2]
  (f))               ;; => 2
(set! *x* 3)
(f)                  ;; => 3
```

The box object is shared by reference, so a `binding` is visible across
separately-compiled ESM modules: a module that reads `*x*` sees a value bound by
another module. Only the box's property is mutated, never the import binding.

## Macros

To load macros, add a `squint.edn` file in the root of your project with
`{:paths ["src-squint"]}` that describes where to find your macro files.  Macros
are written in `.cljs` or `.cljc` files and are executed using
[SCI](https://github.com/babashka/sci).

The following searches for a `foo/macros.cljc` file in the `:paths` described in `squint.edn`.

``` clojure
(ns foo (:require-macros [foo.macros :refer [my-macro]]))

(my-macro 1 2 3)
```

## Tagged literals

Map a tag to a reader function in a `data_readers.cljc` file in one of the `:paths` of `squint.edn`:

```clojure
{my/point my.readers/point}
```

The reader function runs at compile time in SCI. It receives the form after the tag and returns the form to compile:

```clojure
(ns my.readers)

(defn point [[x y]]
  {:x x :y y})
```

```clojure
(js/console.log #my/point [1 2]) ;; prints { x: 1, y: 2 }
```

A tag in `data_readers.cljc` replaces a built-in tag with the same name, e.g. `#js`.

Tagged literals from `data_readers.cljc` work in `squint compile`, `squint watch` and `squint run`.

## `squint.edn`

In `squint.edn` you can describe the following options:

- `:paths`: the source paths to search for files. At the moment, only `.cljc` and `.cljs` are supported.
- `:extension`: the preferred extension to output, which defaults to `.mjs`, but can be set to `.jsx` for React(-like) projects.
- `:copy-resources`: a set of keywords that represent file extensions of files
  that should be copied over from source paths. E.g. `:css`, `:json`. Strings
  may also be used which represent regexes which are processed through
  `re-find`.
- `:output-dir`: the directory where compiled files will be created,
  which defaults to the project root directory.
- `:source-map`: write a `.map` file next to each compiled file. Defaults to
  `true` for `squint watch` and `false` for `squint compile`.
- `:deps`: a map of dependencies from the Clojure ecosystem, in the same format
  as `deps.edn`. Only `:git/url` (with `:git/sha`) and `:local/root` libraries
  are supported, no jars yet. Their source directories are resolved via the
  `clojure` CLI (`-Spath`) and added to `:paths`. Requires `clojure` on the
  `PATH`. Example:
  ```clojure
  {:paths ["src"]
   :deps {no.cjohansen/replicant {:git/url "https://codeberg.org/cjohansen/replicant"
                                  :git/sha "..."}}}
  ```

See [examples/vite-react](examples/vite-react) for an example project which uses a `squint.edn`.
See [examples/replicant](examples/replicant) for one that pulls a git dependency via `:deps`.

## Watch

Run `npx squint watch` to watch the source directories described in `squint.edn` and they will be (re-)compiled whenever they change.
See [examples/vite-react](examples/vite-react) for an example project which uses this.

## Source maps

Use `--source-map` to write a `.map` file next to each compiled file:

```bash
npx squint compile --source-map src/app.cljs
```

`squint watch` writes source maps by default. Turn them off with
`--no-source-map` or `:source-map false` in `squint.edn`.

Run Node with `--enable-source-maps` to get `.cljs` locations in stack traces:

```bash
node --enable-source-maps src/app.mjs
```

The vite plugin writes source maps in development. Set `build.sourcemap` to
include them in a production build:

```js
export default defineConfig({
  plugins: [squint()],
  build: { sourcemap: true },
});
```

The plugin's `sourceMap` option or `:source-map` in `squint.edn` overrides both.

See [examples/source-maps](examples/source-maps) for an example.

To build a source map for JavaScript compiled in memory, see
[doc/source-maps.md](doc/source-maps.md).

## Svelte

A svelte pre-processor for squint can be found [here](https://github.com/jruz/svelte-preprocess-cljs).

## Vite

See [examples/vite-react](examples/vite-react).

## React Native (Expo)

See [examples/expo-react-native](examples/expo-react-native).

## Compile on a server, use in a browser

See [examples/babashka/index.clj](examples/babashka/index.clj).

<!-- This is a small demo of how to leverage squint from a JVM to compile snippets of -->
<!-- JavaScript that you can use in the browser. -->

<!-- ``` clojure -->
<!-- (require '[squint.compiler]) -->
<!-- (-> (squint.compiler/compile-string* "(prn (map inc [1 2 3]))" {:core-alias "_sc"}) :body) -->
<!-- ;;=> "_sc.prn(_sc.map(_sc.inc, [1, 2, 3]));\n" -->
<!-- ``` -->

<!-- The `:core-alias` option takes care of prefixing any `squint.core` function with an alias, in the example `_sc`. -->

<!-- In HTML, to avoid any async ES6, there is also a UMD build of `squint.core` -->
<!-- available. See the below HTML how it is used. We alias the core library to our -->
<!-- shorter `_sc` alias ourselves using -->

<!-- ``` html -->
<!-- <script>globalThis._sc = squint.core;</script> -->
<!-- ``` -->

<!-- to make it all work. -->

<!-- ``` html -->
<!-- <!DOCTYPE html> -->
<!-- <html> -->
<!--   <head> -->
<!--     <title>Squint</title> -->
<!--     <script src="https://cdn.jsdelivr.net/npm/squint-cljs@0.2.30/lib/squint.core.umd.js"></script> -->
<!--     <\!-- rename squint.core to a shorter alias at your convenience: -\-> -->
<!--     <script>globalThis._sc = squint.core;</script> -->
<!--     <\!-- compile JS on the server using: (squint.compiler/compile-string* "(prn (map inc [1 2 3]))" {:core-alias "_sc"}) -\-> -->
<!--     <script> -->
<!--       _sc.prn(_sc.map(_sc.inc, [1, 2, 3])); -->
<!--     </script> -->
<!--   </head> -->
<!--   <body> -->
<!--     <button onClick="_sc.prn(_sc.map(_sc.inc, [1, 2, 3]));"> -->
<!--       Click me -->
<!--     </button> -->
<!--   </body> -->
<!-- </html> -->
<!-- ``` -->

## Playground

To draw into the page from a playground example, render into the element with the id `app`:

```clojure
(let [el (js/document.getElementById "app")] ...)
```

The playground shows `#app` in a resizable frame, either above the editor or in a section in the output pane. DOM elements you add anywhere else on the page still render, but sit outside that frame.

- [Pinball](https://squint-cljs.github.io/squint/?example=pinball)
- [Wordle](https://squint-cljs.github.io/squint/?example=wordle)
- [React](https://squint-cljs.github.io/squint/?example=react), [Preact](https://squint-cljs.github.io/squint/?example=preact), [Preact + runtime hiccup](https://squint-cljs.github.io/squint/?example=preact-hiccup)
- [TC39 Records and Tuples](https://squint-cljs.github.io/squint/?example=records-and-tuples)
- [edn-data](https://squint-cljs.github.io/squint/?example=edn-data)
- [Immutable-js](https://squint-cljs.github.io/squint/?example=immutable-js)
- [Loading a UMD module](https://squint-cljs.github.io/squint/?example=umd-module)
- [Vega-lite](https://squint-cljs.github.io/squint/?example=vega-lite)
- [Vue.js](https://squint-cljs.github.io/squint/?example=vue)
- [Tic-tac-toe](https://squint-cljs.github.io/squint/?example=tictactoe)
- [Date-fns](https://squint-cljs.github.io/squint/?example=date-fns)
- [VanJS](https://squint-cljs.github.io/squint/?example=vanjs)
- [Mithril](https://squint-cljs.github.io/squint/?example=mithril)
- [Rough](https://squint-cljs.github.io/squint/?example=rough)
- [Immer](https://squint-cljs.github.io/squint/?example=immer), [mutative](https://squint-cljs.github.io/squint/?example=mutative)
- [MobX](https://squint-cljs.github.io/squint/?example=mobx) - [another example using atoms](https://squint-cljs.github.io/squint/?example=mobx-atoms)
- [SolidJS](https://squint-cljs.github.io/squint/?example=solidjs)
- [Web components](https://squint-cljs.github.io/squint/?example=web-components)
- [Quill](https://squint-cljs.github.io/squint/?example=quill)
- [defmulti](https://squint-cljs.github.io/squint/?example=defmulti): multimethods in squint as a library
- [Game of life](https://squint-cljs.github.io/squint/?example=game-of-life)
- [docx](https://squint-cljs.github.io/squint/?example=docx)
- [snabbdom](https://squint-cljs.github.io/squint/?example=snabbdom), [Ohm's law](https://squint-cljs.github.io/squint/?example=ohms-law)
- [Idiomorph](https://squint-cljs.github.io/squint/?example=idiomorph)
- [Eucalypt](https://squint-cljs.github.io/squint/?example=eucalypt), a Reagent-without-React clone in squint


## Developing Squint

Requires Babashka, Clojure, Node, npm, and pnpm.

After cloning the repo, run:

```shell
cd squint
pnpm install
bb dev
```

`pnpm install` installs the root dependencies using the checked-in lockfile.

`bb dev` installs the playground's dependencies with npm, then starts the Squint
compiler watcher and playground dev server.

Vite will print the local playground URL, normally http://localhost:5173.

Run `bb tasks` to see other development tasks.

## T-shirt

Buy the t-shirt [here](https://www.etsy.com/listing/1761522434/squint-cljs-t-shirt)!

## License

Copyright © 2022 - 2026 Michiel Borkent

Distributed under the Eclipse Public License 1.0. This project contains code
from Clojure and ClojureScript which are also licensed under the EPL 1.0. See
LICENSE.
