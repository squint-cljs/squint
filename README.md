<img src="./logo/logo.svg" width="100%">

Squint is a light-weight dialect of ClojureScript with a compiler and standard
library.

Squint is not intended as a full replacement for ClojureScript but as a tool to
target JS when you need something more light-weight in terms of interop and
bundle size. The most significant difference with CLJS is that squint uses only
built-in JS data structures. Squint's output is designed to work well with ES
modules.

If you want to use squint, but with the normal ClojureScript standard library
and data structures, check out [cherry](https://github.com/squint-cljs/cherry).

> :warning: This project is a work in progress and may still undergo breaking
> changes.

## Quickstart

Although it's early days, you're welcome to try out `squint` and submit issues.

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
- Functions like `map`, `filter`, etc. produce lazy iterable values but their
  results are not cached. If side effects are used in combination with laziness,
  it's recommended to realize the lazy value using `vec` on function
  boundaries. You can detect re-usage of lazy values by calling
  `warn-on-lazy-reusage!`.
- Supports async/await:`(def x (js-await y))`. Async functions must be marked
  with `^:async`: `(defn ^:async foo [])`.
- `assoc!`, `dissoc!`, `conj!`, etc. perform in place mutation on objects
- `assoc`, `dissoc`, `conj`, etc. return a new shallow copy of objects
- `println` is a synonym for `console.log`
- `pr-str` and `prn` coerce values to a string using `JSON.stringify` (currently, this may change)
- Since JavaScript only supports strings for keys in maps, any data structures used as keys will be stringified

If you are looking for closer ClojureScript semantics, take a look at [Cherry 🍒](https://github.com/squint-cljs/cherry).

## Articles

- [Writing a Cloudflare worker with squint and bun](https://blog.michielborkent.nl/squint-cloudflare-bun.html)
- [Porting a CLJS project to squint](https://blog.michielborkent.nl/porting-cljs-project-to-squint.html)

## Projects using squint

- [@nextjournal/clojure-mode](https://github.com/nextjournal/clojure-mode)
- [static search index for Tumblr](https://github.com/holyjak/clj-tumblr-summarizer/commit/a8b2ca8a9f777e4a9059fa0f1381ded24e5f1a0f)
- [wordle](https://github.com/jackdbd/squint-wordle)
- [Zenith](https://tofutheloafu.itch.io/zenith): a game developed for the Lisp Game Jame 2024 by Trevor

## Advent of Code

Solve [Advent of Code](https://adventofcode.com/) puzzles with squint [here](https://squint-cljs.github.io/squint/?src=OzsgSGVscGVyIGZ1bmN0aW9uczoKOzsgKGZldGNoLWlucHV0IHllYXIgZGF5KSAtIGdldCBBT0MgaW5wdXQKOzsgKGFwcGVuZCBzdHIpIC0gYXBwZW5kIHN0ciB0byBET00KOzsgKHNweSB4KSAtIGxvZyB4IHRvIGNvbnNvbGUgYW5kIHJldHVybiB4Cgo7OyBSZW1lbWJlciB0byB1cGRhdGUgdGhlIHllYXIgYW5kIGRheSBpbiB0aGUgZmV0Y2gtaW5wdXQgY2FsbC4KKGRlZiBpbnB1dCAoLT4%2BIChqcy1hd2FpdCAoZmV0Y2gtaW5wdXQgMjAyMiAxKSkKICAgICAgICAgICAgICNfc3B5CiAgICAgICAgICAgICBzdHIvc3BsaXQtbGluZXMKICAgICAgICAgICAgIChtYXB2IHBhcnNlLWxvbmcpKSkKCihkZWZuIHBhcnQtMQogIFtdCiAgKC0%2BPiBpbnB1dAogICAgKHBhcnRpdGlvbi1ieSBuaWw%2FKQogICAgKHRha2UtbnRoIDIpCiAgICAobWFwICMoYXBwbHkgKyAlKSkKICAgIChhcHBseSBtYXgpKSkKCihkZWZuIHBhcnQtMgogIFtdCiAgKC0%2BPiBpbnB1dAogICAgKHBhcnRpdGlvbi1ieSBuaWw%2FKQogICAgKHRha2UtbnRoIDIpCiAgICAobWFwICMoYXBwbHkgKyAlKSkKICAgIChzb3J0LWJ5IC0pCiAgICAodGFrZSAzKQogICAgKGFwcGx5ICspKSkKCih0aW1lIChwYXJ0LTEpKQojXyh0aW1lIChwYXJ0LTIpKQ%3D%3D&boilerplate=https%3A%2F%2Fgist.githubusercontent.com%2Fborkdude%2Fcf94b492d948f7f418aa81ba54f428ff%2Fraw%2Fa6e9992b079e20e21d753e8c75a7353c5908b225%2Faoc_ui.cljs&repl=true).

### Seqs

Squint does not implement Clojure seqs. Instead it uses the JavaScript
[iteration
protocols](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols)
to work with collections. What this means in practice is the following:

- `seq` takes a collection and returns an Iterable of that collection, or nil if it's empty
- `iterable` takes a collection and returns an Iterable of that collection, even if it's empty
- `seqable?` can be used to check if you can call either one

Most collections are iterable already, so `seq` and `iterable` will simply
return them; an exception are objects created via `{:a 1}`, where `seq` and
`iterable` will return the result of `Object.entries`.

`first`, `rest`, `map`, `reduce` et al. call `iterable` on the collection before
processing, and functions that typically return seqs instead return an array of
the results.

#### Memory usage

With respect to memory usage:

- Lazy seqs in squint are built on generators. They do not cache their results, so every time they are consumed, they are re-calculated from scratch.
- Lazy seq function results hold on to their input, so if the input contains resources that should be garbage collected, it is recommended to limit their scope and convert their results to arrays when leaving the scope:


``` clojure
(js/global.gc)

(println (js/process.memoryUsage))

(defn doit []
  (let [x [(-> (new Array 10000000)
               (.fill 0)) :foo :bar]
        ;; Big array `x` is still being held on to by `y`:
        y (rest x)]
    (println (js/process.memoryUsage))
    (vec y)))

(println (doit))

(js/global.gc)
;; Note that big array is garbage collected now:
(println (js/process.memoryUsage))
```

Run the above program with `node --expose-gc ./node_cli mem.cljs`

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

[Play with JSX non the playground](https://squint-cljs.github.io/squint/?repl=true&src=KHJlcXVpcmUgJ1sicmVhY3QiIDphcyByZWFjdF0pCihyZXF1aXJlICdbInJlYWN0LWRvbSIgOmFzIHJkb21dKQoKKGRlZm9uY2UgY29tcG9uZW50LXN0YXRlIChhdG9tIDApKQoKKGRlZm4gQmFyIFt7OmtleXMgW2ZpcnN0bmFtZSBsYXN0bmFtZV0gOmFzIHByb3BzfV0KICAobGV0IFtbY2xpY2tzIHNldENsaWNrc10gKHJlYWN0L3VzZVN0YXRlIEBjb21wb25lbnQtc3RhdGUpXQogICAgI2pzeCBbOjw%2BCiAgICAgICAgICBbOnNwYW4gZmlyc3RuYW1lICIgIiBsYXN0bmFtZV0KICAgICAgICAgIFs6ZGl2ICJZb3UgY2xpY2tlZCAiIGNsaWNrcyAiIHRpbWVzISJdCiAgICAgICAgICBbOmJ1dHRvbiB7Om9uQ2xpY2sgIyhzZXRDbGlja3MgKHN3YXAhIGNvbXBvbmVudC1zdGF0ZSBpbmMpKX0KICAgICAgICAgICAiQ2xpY2sgbWUiXV0pKQoKKGRlZm4gRm9vIFtdCiAgI2pzeCBbOmRpdiAiSGVsbG8sICIKICAgICAgICAobGV0IFttIChhc3NvYyB7OmZpcnN0bmFtZSAiTWljaGllbCJ9IDpsYXN0bmFtZSAiQm9ya2VudCIpXQogICAgICAgICAgI2pzeCBbQmFyIHs6JiBtfV0pXSkKCihkZWZvbmNlIGVsdCAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiZGl2IikKICAgICAgICAgICAgICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCkpKQoKKGRlZiByb290IChyZG9tL2NyZWF0ZVJvb3QgZWx0KSkKCigucmVuZGVyIHJvb3QgI2pzeCBbRm9vXSk%3D)

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

See [this](https://squint-cljs.github.io/squint/?src=KG5zIG15bGl0CiAgKDpyZXF1aXJlIFtzcXVpbnQuY29yZSA6cmVmZXIgW2RlZmNsYXNzIGpzLXRlbXBsYXRlXV0KICAgWyJodHRwczovL2VzbS5zaC9saXRAMy4wLjAiIDphcyBsaXRdKSkKCihkZWZjbGFzcyBNeUVsZW1lbnQKICAoZXh0ZW5kcyBsaXQvTGl0RWxlbWVudCkKICAoXjpzdGF0aWMgZmllbGQgcHJvcGVydGllcyB7OmNvdW50IHt9fSkKCiAgKGNvbnN0cnVjdG9yIFt0aGlzXQogICAgKHN1cGVyKQogICAgKHNldCEgdGhpcy5jb3VudCAwKQogICAgKHNldCEgdGhpcy5uYW1lICJIZWxsbyIpKQoKICBPYmplY3QKICAocmVuZGVyIFt0aGlzXQogICAgI2h0bWwgXmxpdC9odG1sCiAgICBbOmRpdgogICAgIFs6aDEgdGhpcy5uYW1lXQogICAgIFs6YnV0dG9uIHsiQGNsaWNrIiB0aGlzLm9uQ2xpY2sKICAgICAgICAgICAgICAgOnBhcnQgImJ1dHRvbiJ9CiAgICAgICJDbGljayBjb3VudCAiIHRoaXMuY291bnRdXSkKCiAgKG9uQ2xpY2sgW3RoaXNdCiAgICAoc2V0ISB0aGlzLmNvdW50IChpbmMgdGhpcy5jb3VudCkpKSkKCihkZWZvbmNlIGZvbwogIChkbwogICAgKGpzL3dpbmRvdy5jdXN0b21FbGVtZW50cy5kZWZpbmUgIm15LWVsZW1lbnQiIE15RWxlbWVudCkKICAgIHRydWUpKQoKKGRlZiBhcHAgKG9yIChqcy9kb2N1bWVudC5xdWVyeVNlbGVjdG9yICIjYXBwIikKICAgICAgICAgICAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiZGl2IikKICAgICAgICAgICAgIChzZXQhIC1pZCAiYXBwIikKICAgICAgICAgICAgIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkpCgooc2V0ISAoLi1pbm5lckhUTUwgYXBwKSAjaHRtbCBbOmRpdgogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgWzpteS1lbGVtZW50XQogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI19bOm15LWVsZW1lbnRdXSk%3D) playground example for a full example.

## Async/await

Squint supports `async/await`:

``` clojure
(defn ^:async foo [] (js/Promise.resolve 10))

(def x (js-await (foo)))

(println x) ;;=> 10
```

Anonymous functions must have `^:async` on the `fn` symbol or the function's name:

``` clojure
(^:async fn [] (js-await {}) 3)
```

## Generator functions

Generator functions must be marked with `^:gen`:

``` clojure
(defn ^:gen foo []
  (js-yield 1)
  (js-yield* [2 3])
  (let [x (inc 3)]
    (yield x)))

(vec (foo)) ;;=> [1 2 3 4]
```

Anonymous functions must have `^:gen` on the argument vector:

``` clojure
(^:gen fn [] (js-yield 1) (js-yield 2))
```

See the [playground](https://squint-cljs.github.io/squint/?src=KGRlZm4gXjpnZW4gZm9vIFtdCiAgKGpzLXlpZWxkIDEpCiAgKGpzLXlpZWxkKiBbMiAzXSkKICAobGV0IFt4IChpbmMgMyldCiAgICAoanMteWllbGQgeCkpCiAgKGxldCBbeCAoZG8gKGpzLXlpZWxkIDUpCiAgICAgICAgICAgIDYpXQogICAgKGpzLXlpZWxkIHgpKSkKCih2ZWMgKGZvbykp) for an example.

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

## Truthiness

Squint respect CLJS truth semantics: only `null`, `undefined` and `false` are non-truthy, `0` and `""` are truthy.

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

See [examples/vite-react](examples/vite-react) for an example project which uses a `squint.edn`.

## Watch

Run `npx squint watch` to watch the source directories described in `squint.edn` and they will be (re-)compiled whenever they change.
See [examples/vite-react](examples/vite-react) for an example project which uses this.

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

- [Pinball](https://squint-cljs.github.io/squint/?src=https://gist.githubusercontent.com/borkdude/ca3af924dc2526f00361f28dcf5d0bfb/raw/09cd9e17bf0d6fa3655d0e7cbf2c878e19cb894f/pinball.cljs)
- [Wordle](https://squint-cljs.github.io/squint/?src=https://gist.githubusercontent.com/borkdude/9ed90af225a57ba6b8d9dd12e7c71eea/raw/02fd614cad0da4ac696511c438ebd9ed67d412b5/wordle.cljs)
- [React](https://squint-cljs.github.io/squint/?repl=true&src=KHJlcXVpcmUgJ1sicmVhY3QiIDphcyByZWFjdF0pCihyZXF1aXJlICdbInJlYWN0LWRvbSIgOmFzIHJkb21dKQoKKGRlZm9uY2UgY29tcG9uZW50LXN0YXRlIChhdG9tIDApKQoKKGRlZm4gQmFyIFt7OmtleXMgW2ZpcnN0bmFtZSBsYXN0bmFtZV0gOmFzIHByb3BzfV0KICAobGV0IFtbY2xpY2tzIHNldENsaWNrc10gKHJlYWN0L3VzZVN0YXRlIEBjb21wb25lbnQtc3RhdGUpXQogICAgI2pzeCBbOjw%2BCiAgICAgICAgICBbOnNwYW4gZmlyc3RuYW1lICIgIiBsYXN0bmFtZV0KICAgICAgICAgIFs6ZGl2ICJZb3UgY2xpY2tlZCAiIGNsaWNrcyAiIHRpbWVzISJdCiAgICAgICAgICBbOmJ1dHRvbiB7Om9uQ2xpY2sgIyhzZXRDbGlja3MgKHN3YXAhIGNvbXBvbmVudC1zdGF0ZSBpbmMpKX0KICAgICAgICAgICAiQ2xpY2sgbWUiXV0pKQoKKGRlZm4gRm9vIFtdCiAgI2pzeCBbOmRpdiAiSGVsbG8sICIKICAgICAgICAobGV0IFttIChhc3NvYyB7OmZpcnN0bmFtZSAiTWljaGllbCJ9IDpsYXN0bmFtZSAiQm9ya2VudCIpXQogICAgICAgICAgI2pzeCBbQmFyIHs6JiBtfV0pXSkKCihkZWZvbmNlIGVsdCAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiZGl2IikKICAgICAgICAgICAgICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCkpKQoKKGRlZiByb290IChyZG9tL2NyZWF0ZVJvb3QgZWx0KSkKCigucmVuZGVyIHJvb3QgI2pzeCBbRm9vXSk%3D), [preact](https://squint-cljs.github.io/squint/?repl=true&jsx.import-source=https%3A%2F%2Fesm.sh%2Fpreact%4010.19.2&src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvcHJlYWN0QDEwLjE5LjIiIDphcyByZWFjdF0pCihyZXF1aXJlICdbImh0dHBzOi8vZXNtLnNoL3ByZWFjdEAxMC4xOS4yL2hvb2tzIiA6YXMgaG9va3NdKQoKKGRlZm9uY2UgY29tcG9uZW50LXN0YXRlIChhdG9tIDApKQoKKGRlZm4gQmFyIFt7OmtleXMgW2ZpcnN0bmFtZSBsYXN0bmFtZV0gOmFzIHByb3BzfV0KICAobGV0IFtbY2xpY2tzIHNldENsaWNrc10gKGhvb2tzL3VzZVN0YXRlIEBjb21wb25lbnQtc3RhdGUpXQogICAgI2pzeCBbOjw%2BCiAgICAgICAgICBbOnNwYW4gZmlyc3RuYW1lICIgIiBsYXN0bmFtZV0KICAgICAgICAgIFs6ZGl2ICJZb3UgY2xpY2tlZCAiIGNsaWNrcyAiIHRpbWVzISJdCiAgICAgICAgICBbOmJ1dHRvbiB7Om9uQ2xpY2sgIyhzZXRDbGlja3MgKHN3YXAhIGNvbXBvbmVudC1zdGF0ZSBpbmMpKX0KICAgICAgICAgICAiQ2xpY2sgbWUiXV0pKQoKKGRlZm4gRm9vIFtdCiAgI2pzeCBbOmRpdiAiSGVsbG8sICIKICAgICAgICAobGV0IFttIChhc3NvYyB7OmZpcnN0bmFtZSAiTWljaGllbCJ9IDpsYXN0bmFtZSAiQm9ya2VudCIpXQogICAgICAgICAgI2pzeCBbQmFyIHs6JiBtfV0pXSkKCihkZWZvbmNlIGVsdCAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiZGl2IikKICAgICAgICAgICAgICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCkpKQoKKHJlYWN0L3JlbmRlciAjanN4IFtGb29dIGVsdCk%3D)
- [TC39 Records and Tuples](https://squint-cljs.github.io/squint/?repl=true&src=KHJlcXVpcmUgJ1siaHR0cHM6Ly91bnBrZy5jb20vQGJsb29tYmVyZy9yZWNvcmQtdHVwbGUtcG9seWZpbGwiIDphcyB0YzM5XSkKCihkZWYgYSAoanMvUmVjb3JkIHs6YSAxfSkpCihkZWYgYiAoanMvUmVjb3JkIHs6YSAxfSkpCig9IGEgYikgOzsgdHJ1ZSwgdGhhbmsgZ29kCihkZWYgc3RvcmUgKG5ldyBqcy9NYXApKQooLnNldCBzdG9yZSBhICJrZXllZCBieSBjb2xsZWN0aW9uIGEiKQo7OyBhbHRob3VnaCB3ZSBnZXQgdGhlIHZhbHVlIGZyb20gdGhlIG1hcCB1c2luZyBiLCB3ZSBnZXQgdGhlIHNhbWUgdmFsdWUgb3V0IGFzIGEKKC5nZXQgc3RvcmUgYikgOzs9PiAia2V5ZWQgYnkgY29sbGVjdGlvbiBhIgooZGVmIHQxIChqcy9UdXBsZSAxIDIpKQooZGVmIHQyIChqcy9UdXBsZSAxIDIpKQooZGVmIG15LW1hcCAobmV3IGpzL01hcCBbW3QxIDpoZWxsb10gW3QyIDp0aGVyZV1dKSkKKGdldCBteS1tYXAgdDEpIDs7PT4gdGhlcmUKKGNvbnRhaW5zPyAje3QxfSB0MikgOzsgdHJ1ZQooY291bnQgI3t0MSB0Mn0pIDs7IDEgOzsgdGhhbmsgZ29k)
- [edn-data](https://squint-cljs.github.io/squint/?repl=true&src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvZWRuLWRhdGFAMS4xLjEvZGlzdC9pbmRleC5qcyIgOmFzIGVkbl0pCgooZWRuL3BhcnNlRUROU3RyaW5nICJ7OmEgMSA6YiAjezpmb28gOmJhciA6YmF6fSA6YyAjaW5zdCBcIjIwMDBcIn0iCiAgezptYXBBcyA6b2JqZWN0IDprZXl3b3JkQXMgOnN0cmluZyA6c2V0QXMgOnNldH0pCgooZWRuL3BhcnNlRUROU3RyaW5nIChlZG4vdG9FRE5TdHJpbmdGcm9tU2ltcGxlT2JqZWN0IHs6YSAxIDpiIDIgOmMgI3s6YSA6YiA6Y319KQogIHs6bWFwQXMgOm9iamVjdCA6a2V5d29yZEFzIDpzdHJpbmcgOnNldEFzIDpzZXR9KQ%3D%3D)
- [Immutable-js](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvaW1tdXRhYmxlIiA6YXMgaW1tXSkKCihsZXQgW20gKG5ldyBpbW0vTWFwIFtbMSAyXV0pCiAgICAgIG0gKGltbS9zZXQgbSA6Zm9vIDpiYXIpXQogICg6Zm9vICgudG9KU09OIG0pKSk%3D)
- [Loading a UMD module](https://squint-cljs.github.io/squint/?repl=true&src=KGRlZm4gXjphc3luYyBqcy1yZXF1aXJlIFt1cmwgbW9kdWxlXQogIChsZXQgW21vZHVsZSAob3IgbW9kdWxlIHs6ZXhwb3J0cyB7fX0pCiAgICAgICAgcmVzcCAoanMtYXdhaXQgKGpzL2ZldGNoIHVybCkpCiAgICAgICAgc2NyaXB0IChqcy1hd2FpdCAoLnRleHQgcmVzcCkpCiAgICAgICAgZnVuYyAoanMvRnVuY3Rpb24gIm1vZHVsZSIgImV4cG9ydHMiIHNjcmlwdCldCiAgICAoLmNhbGwgZnVuYyBtb2R1bGUgbW9kdWxlICguLWV4cG9ydHMgbW9kdWxlKSkKICAgICguLWV4cG9ydHMgbW9kdWxlKSkpCgooZGVmIGVxdWFsIChqcy1hd2FpdCAoanMtcmVxdWlyZSAiaHR0cHM6Ly91bnBrZy5jb20vZmFzdC1kZWVwLWVxdWFsQDMuMS4zL2luZGV4LmpzIikpKQoKKGVxdWFsIFsxIDIgM10gWzEgMiAzXSk%3D)
- [Vega-lite](https://squint-cljs.github.io/squint/?repl=true&src=KGRlZm4gXjphc3luYyBldmFsLXNjcmlwdCBbdXJsXQogIChsZXQgW3Jlc3AgKGpzLWF3YWl0IChqcy9mZXRjaCB1cmwpKQogICAgICAgIHNjcmlwdCAoanMtYXdhaXQgKC50ZXh0IHJlc3ApKV0KICAgIChqcy9ldmFsIHNjcmlwdCkpKQoKKGpzLWF3YWl0IChldmFsLXNjcmlwdCAiaHR0cHM6Ly9jZG4uanNkZWxpdnIubmV0L25wbS92ZWdhQDUiKSkKKGpzLWF3YWl0IChldmFsLXNjcmlwdCAiaHR0cHM6Ly9jZG4uanNkZWxpdnIubmV0L25wbS92ZWdhLWxpdGVANCIpKQooanMtYXdhaXQgKGV2YWwtc2NyaXB0ICJodHRwczovL2Nkbi5qc2RlbGl2ci5uZXQvbnBtL3ZlZ2EtZW1iZWRANiIpKQoKKGRlZm9uY2UgY3JlYXRlLWRpdgogIChkbwogICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiZGl2IikKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAoc2V0ISAtaWQgInZpcyIpKSkKICAgIHRydWUpKQoKKGRlZiBzcGVjIHs6JHNjaGVtYSAiaHR0cHM6Ly92ZWdhLmdpdGh1Yi5pby9zY2hlbWEvdmVnYS1saXRlL3Y0LjAuanNvbiIKICAgICAgICAgICA6ZGVzY3JpcHRpb24gIkEgc2ltcGxlIGJhciBjaGFydCB3aXRoIGVtYmVkZGVkIGRhdGEuIgogICAgICAgICAgIDpkYXRhIHs6dmFsdWVzIFt7OmEgIkEiLDpiIDI4fSx7OmEgIkIiLDpiIDU1fSx7OmEgIkMiLDpiIDQzfQogICAgICAgICAgICAgICAgICAgICAgICAgICB7OmEgIkQiLDpiIDkxfSx7OmEgIkUiLDpiIDgxfSx7OmEgIkYiLDpiIDUzfQogICAgICAgICAgICAgICAgICAgICAgICAgICB7OmEgIkciLDpiIDE5fSx7OmEgIkgiLDpiIDg3fSx7OmEgIkkiLDpiIDUyfV19CiAgICAgICAgICAgOm1hcmsgOmJhcgogICAgICAgICAgIDplbmNvZGluZyB7OnggeyJmaWVsZCIgImEiLCJ0eXBlIiAib3JkaW5hbCJ9CiAgICAgICAgICAgICAgICAgICAgICA6eSB7ImZpZWxkIiAiYiIsInR5cGUiICJxdWFudGl0YXRpdmUifX19KQoKKGpzL3ZlZ2FFbWJlZCAiI3ZpcyIgc3BlYyk%3D)
- [Vue.js](https://squint-cljs.github.io/squint/?repl=true&src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9jZG4uanNkZWxpdnIubmV0L25wbS92dWVAMy4zLjEwL2Rpc3QvdnVlLmVzbS1icm93c2VyLmpzIiA6YXMgdnVlXSkKKGRlZm9uY2UgZWx0CiAgKGRvdG8gKGpzL2RvY3VtZW50LmNyZWF0ZUVsZW1lbnQgImRpdiIpCiAgICAoanMvZG9jdW1lbnQuYm9keS5wcmVwZW5kKQogICAgKHNldCEgLWlubmVySFRNTCAiPGRpdiBpZD1cImFwcFwiPgogIDxidXR0b24gQGNsaWNrPVwiY291bnQrK1wiPgogICAgQ291bnQgaXM6IHt7IGNvdW50IH19CiAgPC9idXR0b24%2BCjwvZGl2PiIpKSkKCihkZWYgYXBwICh2dWUvY3JlYXRlQXBwCiAgICAgICAgICAgezpzZXR1cCAoZm4gW10KICAgICAgICAgICAgICAgICAgICAgezpjb3VudCAodnVlL3JlZiAwKX0pfSkpCgooLm1vdW50IGFwcCAiI2FwcCIp)
- [Tic-tac-toe](https://squint-cljs.github.io/squint/?src=https://gist.githubusercontent.com/borkdude/6463b9628292e820742838b840096386/raw/8602b9153010af1a11c775dadebbedeae32c8e08/tictactoe.cljs)
- [Date-fns](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6L2VzbS5zaC9kYXRlLWZuc0AzLjAuMC9pbmRleC5tanMiIDphcyBkXSkKCihkL2Zvcm1hdFJlbGF0aXZlIChkL3N1YkRheXMgKGpzL0RhdGUuKSAyKSAoanMvRGF0ZS4pKQ%3D%3D)
- [VanJS](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9jZG4uanNkZWxpdnIubmV0L2doL3ZhbmpzLW9yZy92YW4vcHVibGljL3Zhbi0xLjIuNy5taW4uanMkZGVmYXVsdCIKICAgICAgICAgICA6YXMgdmFuXSkKCihkZWYgYnV0dG9uIHZhbi90YWdzLmJ1dHRvbikKKGRlZiBzcGFuIHZhbi90YWdzLnNwYW4pCgooZGVmbiBDb3VudGVyIFtdCiAgKGxldCBbY291bnRlciAodmFuL3N0YXRlIDApXQogICAgKHNwYW4gIlx1Mjc2NCIgY291bnRlciAiICIKICAgICAgKGJ1dHRvbiB7Om9uY2xpY2sgIyhzZXQhICguLXZhbCBjb3VudGVyKSAoZGVjICguLXZhbCBjb3VudGVyKSkpfQogICAgICAgICJcdWQ4M2RcdWRjNGUiKQogICAgICAoYnV0dG9uIHs6b25jbGljayAjKHNldCEgKC4tdmFsIGNvdW50ZXIpIChpbmMgKC4tdmFsIGNvdW50ZXIpKSl9CiAgICAgICAgIlx1ZDgzZFx1ZGM0ZCIpKSkpCgooZG9jdW1lbnQuYm9keS5wcmVwZW5kIChDb3VudGVyKSk%3D)
- [Mithril](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9jZG5qcy5jbG91ZGZsYXJlLmNvbS9hamF4L2xpYnMvbWl0aHJpbC8yLjIuMi9taXRocmlsLm1pbi5qcyJdKQoKKGRlZiBtIGpzL20pCgooZGVmIHJvb3QgKG9yCiAgICAgICAgICAgIChqcy9kb2N1bWVudC5xdWVyeVNlbGVjdG9yICIjYXBwIikKICAgICAgICAgICAgKGRvdG8gKGpzL2RvY3VtZW50LmNyZWF0ZUVsZW1lbnQgImRpdiIpCiAgICAgICAgICAgICAgKHNldCEgLWlkICJhcHAiKQogICAgICAgICAgICAgIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkpCgooZGVmIGNvdW50ZXIgKGF0b20gMCkpCgooLm1vdW50IG0gcm9vdAogIHs6dmlldyAoZm4gW10KICAgICAgICAgICAobSA6bWFpbgogICAgICAgICAgICAgWyhtIDpoMSAiVHJ5IG1lIG91dCIpCiAgICAgICAgICAgICAgKG0gOmJ1dHRvbgogICAgICAgICAgICAgICAgezpvbmNsaWNrICMoc3dhcCEgY291bnRlciBpbmMpfQogICAgICAgICAgICAgICAgIkNsaWNrczogIiBAY291bnRlcildKSl9KQ%3D%3D)
- [Rough](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvcm91Z2hqc0A0LjYuNiRkZWZhdWx0IiA6YXMgcm91Z2hdKQoKKGRlZiBjYW52YXMgKG9yIChqcy9kb2N1bWVudC5xdWVyeVNlbGVjdG9yICIjY2FudmFzIikKICAgICAgICAgICAgICAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiY2FudmFzIikKICAgICAgICAgICAgICAgIChzZXQhIC1pZCAiY2FudmFzIikKICAgICAgICAgICAgICAgIChzZXQhIC13aWR0aCAyNTApCiAgICAgICAgICAgICAgICAoc2V0ISAtaGVpZ2h0IDI1MCkKICAgICAgICAgICAgICAgIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkpCgooZGVmIHJjIChyb3VnaC9jYW52YXMgY2FudmFzKSkKCihyYy5yZWN0YW5nbGUgMTAgMTAgMjAwIDIwMCkKKHJjLmNpcmNsZSA4MCAxMjAgNTAgezpmaWxsIDpncmVlbn0pCihyYy5jaXJjbGUgODAgMTYwIDUwIHs6ZmlsbCA6cmVkfSk%3D)
- [Immer](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvaW1tZXJAMTAuMC4zIiA6YXMgaW1tZXJdKQoKKGRlZm4gYXBwZW5kIFtzdHJdCiAgKGpzL2RvY3VtZW50LmJvZHkuYXBwZW5kQ2hpbGQKICAgKGRvdG8gKGpzL2RvY3VtZW50LmNyZWF0ZUVsZW1lbnQgImRpdiIpCiAgICAgKHNldCEgLWlubmVyVGV4dCAoanMvSlNPTi5zdHJpbmdpZnkgc3RyKSkpKSkKCihkZWYgYmFzZS1zdGF0ZSBbezp0aXRsZSAiTGVhcm4gU3F1aW50ISIKICAgICAgICAgICAgICAgICAgOmRvbmUgdHJ1ZX0KICAgICAgICAgICAgICAgICB7OnRpdGxlICJUcnkgSW1tZXIiCiAgICAgICAgICAgICAgICAgIDpkb25lIGZhbHNlfV0pCgooZGVmIG5leHQtc3RhdGUgKGltbWVyL3Byb2R1Y2UgYmFzZS1zdGF0ZQogICAgICAgICAgICAgICAgICAoZm4gW2RyYWZ0XQogICAgICAgICAgICAgICAgICAgIChhc3NvYy1pbiEgZHJhZnQgWzAgOmRvbmVdIHRydWUpCiAgICAgICAgICAgICAgICAgICAgKGNvbmohIGRyYWZ0IHs6dGl0bGUgIlR3ZWV0IGFib3V0IGl0In0pKSkpCgooYXBwZW5kIGJhc2Utc3RhdGUpCihhcHBlbmQgbmV4dC1zdGF0ZSk%3D), [mutative](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvbXV0YXRpdmUiIDphcyBtdXRdKQoKKGRlZiBiYXNlLXN0YXRlIHs6Zm9vIDpiYXIKICAgICAgICAgICAgICAgICA6bGlzdCBbezp0ZXh0IDpjb2Rpbmd9XX0pCgooZGVmIHN0YXRlCiAgKG11dC9jcmVhdGUgYmFzZS1zdGF0ZQogICAgKGZuIFtkcmFmdF0KICAgICAgKHVwZGF0ZSEgZHJhZnQgOmxpc3QgY29uaiEgezp0ZXh0IDpsZWFybmluZ30pKSkpCgpzdGF0ZQ%3D%3D)
- [MobX](https://squint-cljs.github.io/squint/?repl=true&jsx=&src=KG5zIGNvdW50ZXIKICAoOnJlcXVpcmUgW3NxdWludC5jb3JlIDpyZWZlciBbZGVmY2xhc3NdXQogICBbInJlYWN0IiA6YXMgcmVhY3RdCiAgIFsicmVhY3QtZG9tIiA6YXMgcmRvbV0KICAgWyJodHRwczovL2VzbS5zaC9tb2J4QDYuMTIuMCIgOnJlZmVyIFttYWtlQXV0b09ic2VydmFibGVdXQogICBbImh0dHBzOi8vZXNtLnNoL21vYngtcmVhY3QtbGl0ZUA0LjAuNSIgOnJlZmVyIFtvYnNlcnZlcl1dCiAgICkpCgooZGVmY2xhc3MgVGltZXIKICAoZmllbGQgc2Vjb25kc1Bhc3NlZCAwKQogIChjb25zdHJ1Y3RvciBbdGhpc10KICAgIChtYWtlQXV0b09ic2VydmFibGUgdGhpcykpCiAgT2JqZWN0CiAgKGluY3JlYXNlVGltZXIgW19dCiAgICAoc2V0ISBzZWNvbmRzUGFzc2VkIChpbmMgc2Vjb25kc1Bhc3NlZCkpKSkKCihkZWYgbXktdGltZXIgKG5ldyBUaW1lcikpCgooZGVmIFRpbWVyVmlldwogIChvYnNlcnZlciAoZm4gW3t0aW1lciA6dGltZXJ9IGNvbXBdCiAgICAgICAgICAgICAgI2pzeAogICAgICAgICAgICAgICBbOnNwYW4gIlNlY29uZHMgcGFzc2VkOiAiICg6c2Vjb25kc1Bhc3NlZCB0aW1lcildKSkpCgooZGVmb25jZSBlbHQgKGRvdG8gKGpzL2RvY3VtZW50LmNyZWF0ZUVsZW1lbnQgImRpdiIpIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkKKGRlZiByb290IChyZG9tL2NyZWF0ZVJvb3QgZWx0KSkKKC5yZW5kZXIgcm9vdCAjanN4IFtUaW1lclZpZXcgezp0aW1lciBteS10aW1lcn1dKQoKKGRlZm9uY2UgY3JlYXRlLWludGVydmFsCiAgKGRvCiAgICAoanMvc2V0SW50ZXJ2YWwgKGZuIFtdICguaW5jcmVhc2VUaW1lciBteS10aW1lcikpIDEwMDApCiAgICB0cnVlKSk%3D) - [another example using atoms](https://squint-cljs.github.io/squint/?repl=true&src=KG5zIGNvdW50ZXIKICAoOnJlcXVpcmUgW3NxdWludC5jb3JlIDpyZWZlciBbZGVmY2xhc3NdXQogICBbInJlYWN0IiA6YXMgcmVhY3RdCiAgIFsicmVhY3QtZG9tIiA6YXMgcmRvbV0KICAgWyJodHRwczovL2VzbS5zaC9tb2J4QDYuMTIuMCIgOnJlZmVyIFttYWtlQXV0b09ic2VydmFibGVdXQogICBbImh0dHBzOi8vZXNtLnNoL21vYngtcmVhY3QtbGl0ZUA0LjAuNSIgOnJlZmVyIFtvYnNlcnZlcl1dKSkKCihkZWYgbXktdGltZXIKICAobWFrZUF1dG9PYnNlcnZhYmxlCiAgICAoYXRvbSB7OmNvdW50ZXIgMH0pKSkKCihkZWZuIGluYy1jb3VudGVyIFtdCiAgKHN3YXAhIG15LXRpbWVyIHVwZGF0ZSA6Y291bnRlciBpbmMpKQoKKGRlZm4gVGltZXJWaWV3IFtdCiAgI2pzeCBbOnNwYW4gIlNlY29uZHMgcGFzc2VkOiAiCiAgICAgICAgKDpjb3VudGVyIEBteS10aW1lcildKQoKKGRlZiBUaW1lclZpZXcgKG9ic2VydmVyIFRpbWVyVmlldykpCgooZGVmb25jZSBlbHQgKGRvdG8gKGpzL2RvY3VtZW50LmNyZWF0ZUVsZW1lbnQgImRpdiIpIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkKKGRlZiByb290IChyZG9tL2NyZWF0ZVJvb3QgZWx0KSkKKC5yZW5kZXIgcm9vdCAjanN4IFtUaW1lclZpZXddKQoKKGRlZm9uY2UgY3JlYXRlLWludGVydmFsCiAgKGRvCiAgICAoanMvc2V0SW50ZXJ2YWwgaW5jLWNvdW50ZXIgMTAwMCkKICAgIHRydWUpKQ%3D%3D)
- [SolidJS](https://squint-cljs.github.io/squint/?repl=true&jsx.import-source=https%3A%2F%2Fesm.sh%2Fsolid-js%401.8.10%2Fh&src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvc29saWQtanNAMS44LjEwIiA6YXMgc29saWRdKQoocmVxdWlyZSAnWyJodHRwczovL2VzbS5zaC9zb2xpZC1qc0AxLjguMTAvd2ViIiA6YXMgc29saWQtd2ViXSkKCihkZWZuIENoaWxkQ29tcG9uZW50IFt7OmtleXMgW2NvdW50XX1dCiAgI2pzeCBbOmRpdiAiQ291bnQgdmFsdWUgaXMgIiBjb3VudF0pCgooZGVmbiBDb3VudGluZ0NvbXBvbmVudCBbXQogIChsZXQgW1tjb3VudCBzZXRDb3VudF0gKHNvbGlkL2NyZWF0ZVNpZ25hbCAxMCkKICAgICAgICBpbnRlcnZhbCAoanMvc2V0SW50ZXJ2YWwgIyhzZXRDb3VudCBpbmMpCiAgICAgICAgICAgICAgICAgICAxMDAwKV0KICAgIChzb2xpZC9vbkNsZWFudXAgIyhqcy9jbGVhckludGVydmFsIGludGVydmFsKSkKICAgICNqc3ggW0NoaWxkQ29tcG9uZW50IHs6Y291bnQKICAgICAgICAgICAgICAgICAgICAgICAgICA7OyByZWFjdGl2ZSB2YWx1ZSBtdXN0IGJlIHdyYXBwZWQgaW4gdGh1bmsKICAgICAgICAgICAgICAgICAgICAgICAgICA7OyB3aGVuIHVzaW5nIGpzeC1ydW50aW1lIGluc3RlYWQgb2Ygc29saWQtanMgY29tcGlsZXIKICAgICAgICAgICAgICAgICAgICAgICAgICAoZm4gW10gY291bnQpfV0pKQoKKGRlZiBlbHQgKG9yCiAgICAgICAgICAgKGpzL2RvY3VtZW50LnF1ZXJ5U2VsZWN0b3IgIiNhcHAiKQogICAgICAgICAgIChkb3RvIChqcy9kb2N1bWVudC5jcmVhdGVFbGVtZW50ICJkaXYiKQogICAgICAgICAgICAgKHNldCEgLWlkICJhcHAiKQogICAgICAgICAgICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCkpKSkKCihkZWZvbmNlIGRpc3Bvc2UgKGF0b20gKGZuIFtdKSkpCihkbwogIChAZGlzcG9zZSkKICAocmVzZXQhIGRpc3Bvc2UKICAgIChzb2xpZC13ZWIvcmVuZGVyIChmbiBbXSAjanN4IFtDb3VudGluZ0NvbXBvbmVudF0pIGVsdCkpKQ%3D%3D)
- [Web components](https://squint-cljs.github.io/squint/?src=KG5zIG15ZWxlbWVudAogICg6cmVxdWlyZSBbc3F1aW50LmNvcmUgOnJlZmVyIFtkZWZjbGFzc11dKSkKCihkZWZjbGFzcyBNeUVsZW1lbnQKICAoZXh0ZW5kcyBqcy9IVE1MRWxlbWVudCkKCiAgKGZpZWxkIC1zaGFkb3cpCiAgKGZpZWxkIGNvdW50IDApCgogIChjb25zdHJ1Y3RvciBbdGhpc10KICAgIChzdXBlcikKICAgIChzZXQhIC1zaGFkb3cgKC5hdHRhY2hTaGFkb3cgdGhpcyB7Om1vZGUgOm9wZW59KSkpCgogIE9iamVjdAogIChoYW5kbGVDbGljayBbdGhpcyBlXQogICAgKHNldCEgY291bnQgKGluYyBjb3VudCkpCiAgICAoLnJlbmRlciB0aGlzKSkKCiAgKGNvbm5lY3RlZENhbGxiYWNrIFt0aGlzXQogICAgKC5hZGRFdmVudExpc3RlbmVyIHRoaXMgImNsaWNrIiB0aGlzLmhhbmRsZUNsaWNrKQogICAgKC5yZW5kZXIgdGhpcykpCgogIChkaXNjb25uZWN0ZWRDYWxsYmFjayBbdGhpc10KICAgICgucmVtb3ZlRXZlbnRMaXN0ZW5lciB0aGlzICJjbGljayIgdGhpcy5oYW5kbGVDbGljaykpCgogIChyZW5kZXIgW3RoaXNdCiAgICAoc2V0ISAoLi1pbm5lckhUTUwgLXNoYWRvdykKICAgICAgI2h0bWwgWzpidXR0b24gIkNsaWNrIGNvdW50ICIgY291bnRdKSkpCgooLmRlZmluZSBjdXN0b21FbGVtZW50cyA6bXktZWxlbWVudCBNeUVsZW1lbnQpCgooZGVmIGFwcCAob3IgKGpzL2RvY3VtZW50LnF1ZXJ5U2VsZWN0b3IgIiNhcHAiKQogICAgICAgICAgIChkb3RvIChqcy9kb2N1bWVudC5jcmVhdGVFbGVtZW50IDpkaXYpCiAgICAgICAgICAgICAoc2V0ISAtaWQgOmFwcCkKICAgICAgICAgICAgIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkpCgooc2V0ISAoLi1pbm5lckhUTUwgYXBwKSAjaHRtbCBbOm15LWVsZW1lbnRdKQ%3D%3D&repl=true)
- [Quill](https://squint-cljs.github.io/squint/?src=KGRlZiBjc3MKICAob3IgKGpzL2RvY3VtZW50LnF1ZXJ5U2VsZWN0b3IgIiNjc3MiKQogICAgKGRvdG8gKGpzL2RvY3VtZW50LmNyZWF0ZUVsZW1lbnQgOmRpdikKICAgICAgKHNldCEgLWlkIDpjc3MpCiAgICAgIChzZXQhIC1pbm5lckhUTUwgI2h0bWwKICAgICAgICAgICAgICAgWzpsaW5rIHs6aHJlZiAiaHR0cHM6Ly9jZG4uanNkZWxpdnIubmV0L25wbS9xdWlsbEAyLjAuMC9kaXN0L3F1aWxsLnNub3cuY3NzIgogICAgICAgICAgICAgICAgICAgICAgIDpyZWwgInN0eWxlc2hlZXQifV0pCiAgICAgIChqcy9kb2N1bWVudC5oZWFkLnByZXBlbmQpKSkpCgooZGVmIGFwcCAob3IgKGpzL2RvY3VtZW50LnF1ZXJ5U2VsZWN0b3IgIiNhcHAiKQogICAgICAgICAgIChkb3RvIChqcy9kb2N1bWVudC5jcmVhdGVFbGVtZW50IDpkaXYpCiAgICAgICAgICAgICAoc2V0ISAtaWQgOmFwcCkKICAgICAgICAgICAgIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQpKSkpCgoocmVxdWlyZSAnWyJodHRwczovL2VzbS5zaC9xdWlsbCRkZWZhdWx0IiA6YXMgUXVpbGxdKQooUXVpbGwuICIjYXBwIiB7OnRoZW1lICJzbm93In0p)
- [defmulti](https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvQHRoaS5uZy9kZWZtdWx0aSIgOnJlZmVyIFtkZWZtdWx0aV1dKQoKKGRlZiBmb28gKGRlZm11bHRpIChmbiBbeF0geCkpKQoKKC5pc2EgZm9vIDIzICJvZGQiKQoKKC5hZGQgZm9vICJvZGQiIChmbiBbeF0gKHN0ciB4ICIgaXMgYW4gb2RkIG51bWJlciIpKSkKCihmb28gMjMp): multimethods in squint as a library
- [Game of life](https://squint-cljs.github.io/squint/?src=KG5zIGluZGV4KQoKKGRlZm4gY2VsbCBbZ3JpZC14IGdyaWQteSBzaXplXQogIHs6c2l6ZSBzaXplCiAgIDp4IGdyaWQteAogICA6eSBncmlkLXkKICAgOmFsaXZlPyAoPiAoanMvTWF0aC5yYW5kb20pIDAuNSl9KQoKKGRlZm4gZHJhdy1jZWxsIFtjdHggW18gezprZXlzIFthbGl2ZT8geCB5IHNpemVdfV1dCiAgKC5iZWdpblBhdGggY3R4KQogICguYXJjIGN0eCAoKiB4IHNpemUpICgqIHkgc2l6ZSkgKC8gc2l6ZSAyKSAwICgqIDIganMvTWF0aC5QSSkpCiAgKHNldCEgKC4tZmlsbFN0eWxlIGN0eCkgKGlmIGFsaXZlPyAiIzExNzg1NSIgIiMzMDMwMzAiKSkKICAoLmZpbGwgY3R4KQogICguc3Ryb2tlIGN0eCkKICAoLmNsb3NlUGF0aCBjdHgpKQoKKGRlZm4gY3JlYXRlLWdyaWQgW3Jvd3MgY29sdW1ucyBjZWxsLXNpemVdCiAgKGludG8ge30KICAgICAgICAoZm9yIFt4IChyYW5nZSByb3dzKQogICAgICAgICAgICAgIHkgKHJhbmdlIGNvbHVtbnMpXQogICAgICAgICAgW1t4IHldIChjZWxsIHggeSBjZWxsLXNpemUpXSkpKQoKKGRlZm4gY2xlYXItc2NyZWVuIFtjdHggY2FudmFzXQogIChzZXQhICguLWZpbGxTdHlsZSBjdHgpICIjMzAzMDMwIikKICAoLmZpbGxSZWN0IGN0eCAwIDAgKC4td2lkdGggY2FudmFzKSAoLi1oZWlnaHQgY2FudmFzKSkpCgooZGVmbiByZW5kZXIgW3s6a2V5cyBbY3R4IGdyaWQgY2FudmFzXX1dCiAgKGNsZWFyLXNjcmVlbiBjdHggY2FudmFzKQogIChkb3NlcSBbY2VsbCBncmlkXQogICAgKGRyYXctY2VsbCBjdHggY2VsbCkpKQoKKGRlZm4gZ2V0LWNlbGwgW2dyaWQgeCB5IG1heC14IG1heC15XQogIChsZXQgW3ggKGNvbmQgKDwgeCAwKSBtYXgteCAoPiB4IG1heC14KSAwIDplbHNlIHgpCiAgICAgICAgeSAoY29uZCAoPCB5IDApIG1heC15ICg%2BIHkgbWF4LXkpIDAgOmVsc2UgeSldCiAgICAoZ2V0IGdyaWQgW3ggeV0pKSkKCihkZWZuIGNvbXB1dGUtZ3JpZCBbezprZXlzIFtncmlkIG1heC14IG1heC15XX1dCiAgKGRvc2VxIFtbXyB7OmtleXMgW3ggeSBhbGl2ZT9dIDphcyBjZWxsfV0gZ3JpZF0KICAgIChsZXQgW2xpdmUtbmVpZ2hib3VyLWNvdW50ICgtPj4gWyhnZXQtY2VsbCBncmlkIChkZWMgeCkgKGRlYyB5KSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIChpbmMgeCkgKGluYyB5KSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIChpbmMgeCkgKGRlYyB5KSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIChkZWMgeCkgKGluYyB5KSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIHggKGluYyB5KSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIHggKGRlYyB5KSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIChkZWMgeCkgeSBtYXgteCBtYXgteSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChnZXQtY2VsbCBncmlkIChpbmMgeCkgeSBtYXgteCBtYXgteSldCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIChtYXAgIyhnZXQgJSA6YWxpdmU%2FKSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgKGZpbHRlciB0cnVlPykKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgKGRvYWxsKQogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAoY291bnQpKV0KICAgICAgKGFzc29jISBjZWxsIDphbGl2ZT8KICAgICAgICAgICAgICAoaWYgYWxpdmU%2FCiAgICAgICAgICAgICAgICAoY29uZHAgPSBsaXZlLW5laWdoYm91ci1jb3VudAogICAgICAgICAgICAgICAgICAwIGZhbHNlCiAgICAgICAgICAgICAgICAgIDEgZmFsc2UKICAgICAgICAgICAgICAgICAgMiB0cnVlCiAgICAgICAgICAgICAgICAgIDMgdHJ1ZQogICAgICAgICAgICAgICAgICA0IGZhbHNlCiAgICAgICAgICAgICAgICAgIGZhbHNlKQogICAgICAgICAgICAgICAgKD0gMyBsaXZlLW5laWdoYm91ci1jb3VudCkpKSkpKQoKKGRlZm4gZ2FtZS1sb29wIFtzdGF0ZV0KICAocmVuZGVyIHN0YXRlKQogIChjb21wdXRlLWdyaWQgc3RhdGUpCiAgKGpzL3NldFRpbWVvdXQgIyhqcy93aW5kb3cucmVxdWVzdEFuaW1hdGlvbkZyYW1lIChmbiBbXSAoZ2FtZS1sb29wIHN0YXRlKSkpIDYwKSkKCihkZWZuIGdhbWUgW2NhbnZhcy1pZF0KICAobGV0IFtjYW52YXMgKGpzL2RvY3VtZW50LmdldEVsZW1lbnRCeUlkIGNhbnZhcy1pZCkKICAgICAgICBjdHggKC5nZXRDb250ZXh0IGNhbnZhcyAiMmQiKQogICAgICAgIG1heC15IDUwCiAgICAgICAgbWF4LXggKCogbWF4LXkgKC8gKC4td2lkdGggY2FudmFzKSAoLi1oZWlnaHQgY2FudmFzKSkpIAogICAgICAgIGluaXRpYWwtZ3JpZCAoY3JlYXRlLWdyaWQgbWF4LXggbWF4LXkgMTApXQogICAgKGdhbWUtbG9vcCB7OmNhbnZhcyBjYW52YXMKICAgICAgICAgICAgICAgIDpjdHggY3R4CiAgICAgICAgICAgICAgICA6bWF4LXggKGRlYyBtYXgteCkKICAgICAgICAgICAgICAgIDptYXgteSAoZGVjIG1heC15KQogICAgICAgICAgICAgICAgOmdyaWQgaW5pdGlhbC1ncmlkfSkpKQoKKGRlZm9uY2UgbWFrZS1jYW52YXMKICAoZG8gKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZAogICAgICAgIChkb3RvIChqcy9kb2N1bWVudC5jcmVhdGVFbGVtZW50ICJjYW52YXMiKQogICAgICAgICAgKHNldCEgLWlkICJjYW52YXMiKQogICAgICAgICAgKHNldCEgLWhlaWdodCAoKiBqcy93aW5kb3cuaW5uZXJIZWlnaHQgMC4yNSkpCiAgICAgICAgICAoc2V0ISAtd2lkdGggKCoganMvd2luZG93LmlubmVyV2lkdGggMC4yNSkpKSkKICAgIHRydWUpKQoKKGdhbWUgImNhbnZhcyIp)
- [docx](https://squint-cljs.github.io/squint/?src=KG5zIG15LWRvY3gKICAoOnJlcXVpcmUgWyJodHRwczovL2VzbS5zaC9kb2N4IiA6YXMgZG9jeAogICAgICAgICAgICAgOnJlZmVyIFtEb2N1bWVudCBQYXJhZ3JhcGggVGV4dFJ1bgogICAgICAgICAgICAgICAgICAgICBQYWNrZXJdXSkpCgooZGVmIGRvYwogIChEb2N1bWVudC4KICAgIHs6c2VjdGlvbnMKICAgICBbezpwcm9wZXJ0aWVzIHt9CiAgICAgICA6Y2hpbGRyZW4KICAgICAgIFsoUGFyYWdyYXBoLgogICAgICAgICAgezpjaGlsZHJlbgogICAgICAgICAgIFsoVGV4dFJ1bi4gIkhlbGxvIFdvcmxkIikKICAgICAgICAgICAgKFRleHRSdW4uCiAgICAgICAgICAgICAgezp0ZXh0ICJGb28gQmFyIgogICAgICAgICAgICAgICA6Ym9sZCB0cnVlfSkKICAgICAgICAgICAgKFRleHRSdW4uCiAgICAgICAgICAgICAgezp0ZXh0ICJcdEdpdGh1YiBpcyB0aGUgYmVzdCIKICAgICAgICAgICAgICAgOmJvbGQgdHJ1ZX0pXX0pXX1dfSkpCgooZGVmbiBeOmFzeW5jIGRvd25sb2FkIFtdCiAgKGxldCBbYnl0ZXMgKGpzLWF3YWl0IChQYWNrZXIudG9CdWZmZXIgZG9jKSkKICAgICAgICBibG9iIChqcy9CbG9iLiBbYnl0ZXNdIHs6dHlwZSAiYXBwbGljYXRpb24vZG9jeCJ9KQogICAgICAgIGxpbmsgKC5jcmVhdGVFbGVtZW50IGpzL2RvY3VtZW50ICJhIildCiAgICAoc2V0ISAoLi1ocmVmIGxpbmspIChqcy9VUkwuY3JlYXRlT2JqZWN0VVJMIGJsb2IpKQogICAgKHNldCEgKC4tZG93bmxvYWQgbGluaykgIk15IHdvcmQgZG9jLmRvY3giKQogICAgKHNldCEgKC4taW5uZXJUZXh0IGxpbmspICJEb3dubG9hZCB5b3VyIHdvcmQgZG9jdW1lbnQhIikKICAgIChqcy9kb2N1bWVudC5ib2R5LnByZXBlbmQgbGluaykpKQoKKGRvd25sb2FkKQ%3D%3D)

## T-shirt

Buy the t-shirt [here](https://www.etsy.com/listing/1761522434/squint-cljs-t-shirt)!

License
=======

Squint is licensed under the EPL. See epl-v10.html in the root directory for more information.
