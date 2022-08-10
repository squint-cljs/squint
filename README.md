## Cherry :cherries:

Experimental ClojureScript to ES6 module compiler.

Reducing friction between ClojureScript and JS tooling.

> :warning: This project is an experiment and not recommended to be used in
> production. It currently has many bugs and will undergo many breaking changes.

## Quickstart

Although it's early days and far from complete, you're welcome to try out cherry and submit issues.

``` shell
$ mkdir cherry-test && cd cherry-test
$ npm init -y
$ npm install cherry-cljs@latest
```

Create a `.cljs` file, e.g. `example.cljs`:

``` clojure
(ns example
  (:require ["fs" :as fs]
            ["url" :refer [fileURLToPath]]))

(prn (fs/existsSync (fileURLToPath js/import.meta.url)))

(defn foo [{:keys [a b c]}]
  (+ a b c))

(js/console.log (foo {:a 1 :b 2 :c 3}))
```

Then compile and run (`run` does both):

```
$ npx cherry run example.cljs
true
6
```

Run `npx cherry --help` to see all command line options.

## Examples

A few examples of currenly working projects compiled by cherry:

- [playground](https://borkdude.github.io/cherry/)
- [wordle](https://borkdude.github.io/cherry/examples/wordle/index.html)
- [react](https://borkdude.github.io/cherry/examples/react/index.html)
- [vite](examples/vite)
- [cherry-action-example](https://github.com/borkdude/cherry-action-example)

See the [examples](examples) directory for more.

## Project goals

Goals of cherry:

- Compile `.cljs` files on the fly into ES6-compatible `.mjs` files.
- Compiler will be available on NPM and can be used from JS tooling, but isn't
  part of the compiled output unless explicitly used.
- Compiled JS files are fairly readable and have source map support for
  debugging
- Compiled JS files are linked to one shared NPM module `"cherry-cljs"` which
  contains `cljs.core.js`, `cljs.string`, etc.  such that libraries written in
  cherry can be compiled and hosted on NPM, while all sharing the same
  standard library and data structures. See [this
  tweet](https://twitter.com/borkdude/status/1549830159326404616) on how that
  looks.
- Output linked to older versions of cherry will work with newer
  versions of cherry: i.e. 'binary' compatibility.
- Light-weight and fast: heavy lifting such as optimizations are expected to be
  done by JS tooling
- No dependency on Google Closure: this project will use it for bootstrapping
  itself (by using the CLJS compiler), but users of this project won't see any
  `goog.*` stuff.
- Macro support
- REPL support
- Async/await support. See [this tweet](https://twitter.com/borkdude/status/1549843802604638209) for a demo.
- Native support for JS object destructuring: `[^:js {:keys [a b]} #js {:a 1 :b 2}]`
- Native support for JSX via `#jsx` reader tag

Cherry may introduce new constructs such as `js/await` which won't be compatible
with current CLJS. Also it might not support all features that CLJS offers. As
such, using existing libraries from the CLJS ecosystem or compiling Cherry CLJS
code with the CLJS compiler may become challenging. However, some results of
this experiment may end up as improvements in the CLJS compiler if they turn out
to be of value.

Depending on interest both from people working on this and the broader
community, the above goals may or may not be pursued. If you are interested in
maturing cherry, please submit
[issues](https://github.com/borkdude/cherry/issues) for bug reports or share
your thoughts on [Github
Discussions](https://github.com/borkdude/cherry/discussions).

Cherry started out as a fork of
[Scriptjure](https://github.com/arohner/scriptjure). Currently it's being
reworked to meet the above goals.

<!-- ## Funding -->

<!-- This project is developed with the following partners, either by funding time -->
<!-- and/or money: -->

<!-- - [Nextjournal](https://nextjournal.com/) -->
<!-- - The main author's [Github Sponsors](https://github.com/sponsors/borkdude) -->

License
=======
Cherry is licensed under the EPL, the same as Clojure core and [Scriptjure](https://github.com/arohner/scriptjure). See epl-v10.html in the root directory for more information.
