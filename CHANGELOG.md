# Changelog

[Squint](https://github.com/squint-cljs/squint): Light-weight ClojureScript dialect

## Unreleased

- [#513](https://github.com/squint-cljs/squint/issues/513): Fix `shuffle` core function random distribution and performances

## v0.7.105 (2024-04-15)

- [#509](https://github.com/squint-cljs/squint/issues/509): Optimization: use arrow fn for implicit IIFE when possible
- Optimization: emit `const` in let expressions, which esbuild optimizes better

## v0.7.104 (2024-04-05)

- Don't wrap arrow function in parens, see [this issue](https://github.com/squint-cljs/vite-plugin-squint/pull/12)

## v0.7.103 (2024-04-05)

- Fix [#499](https://github.com/squint-cljs/squint/issues/499): add support for emitting arrow functions with `^:=>` metadata

## v0.7.102 (2024-04-03)

- Fix [#505](https://github.com/squint-cljs/squint/issues/505): Support `:rename` in `:require`

## v0.7.101 (2024-04-02)

- Fix [#490](https://github.com/squint-cljs/squint/issues/490): render css maps in html mode
- Fix [#502](https://github.com/squint-cljs/squint/issues/502): allow method names in `defclass` to override squint built-ins

## v0.7.100 (2024-03-29)

- Fix [#496](https://github.com/squint-cljs/squint/issues/496): don't wrap strings in another set of quotes

## v0.7.99 (2024-03-29)

- Fix rendering of attribute expressions in HTML (should be wrapped in quotes)

## v0.7.98 (2024-03-25)

- Compile destructured function args to JS destructuring when annotated with `^:js`. This benefits working with vitest and playwright.

## v0.7.97 (2024-03-25)

- [#481](https://github.com/squint-cljs/squint/issues/481): BREAKING, squint no longer automatically copies all non-compiled files to the `:output-dir`. This behavior is now explicit with `:copy-resources`, see docs.

## v0.7.96 (2024-03-14)

- Add new `#html` reader for producing HTML literals using hiccup. See [docs](https://github.com/squint-cljs/squint?tab=readme-ov-file#html) and [playground example](https://squint-cljs.github.io/squint/?src=KG5zIG15ZWxlbWVudAogICg6cmVxdWlyZSBbc3F1aW50LmNvcmUgOnJlZmVyIFtkZWZjbGFzcyBqcy10ZW1wbGF0ZV1dCiAgIFsiaHR0cHM6Ly9lc20uc2gvbGl0IiA6YXMgbGl0XSkpCgooZGVmY2xhc3MgTXlFbGVtZW50CiAgKGV4dGVuZHMgbGl0L0xpdEVsZW1lbnQpCiAgKGZpZWxkIG5hbWUgIldvcmxkIikKICAoZmllbGQgY291bnQgMCkKCiAgKGNvbnN0cnVjdG9yIFtfXQogICAgKHN1cGVyKSkKCiAgT2JqZWN0CiAgKHJlbmRlciBbdGhpc10KICAgICNodG1sIF5saXQvaHRtbAogICAgWzpkaXYKICAgICBbOmgxIG5hbWVdCiAgICAgWzpidXR0b24geyJAY2xpY2siICguLW9uQ2xpY2sgdGhpcykKICAgICAgICAgICAgICAgOnBhcnQgImJ1dHRvbiJ9CiAgICAgICJDbGljayBjb3VudCAiIGNvdW50XV0pCgogIChvbkNsaWNrIFt0aGlzXQogICAgKHNldCEgY291bnQgKGluYyBjb3VudCkpCiAgICAoLmRpc3BhdGNoRXZlbnQgdGhpcyAobmV3IGpzL0N1c3RvbUV2ZW50ICJjb3VudC1jaGFuZ2VkIikpKSkKCihzZXQhICguLXByb3BlcnRpZXMgTXlFbGVtZW50KSAjanMgeyJjb3VudCIgI2pzIHt9fSkKCihqcy93aW5kb3cuY3VzdG9tRWxlbWVudHMuZGVmaW5lICJteS1lbGVtZW50IiBNeUVsZW1lbnQpCgooZGVmIGFwcCAob3IgKGpzL2RvY3VtZW50LnF1ZXJ5U2VsZWN0b3IgIiNhcHAiKQogICAgICAgICAgIChkb3RvIChqcy9kb2N1bWVudC5jcmVhdGVFbGVtZW50ICJkaXYiKQogICAgICAgICAgICAgKHNldCEgLWlkICJhcHAiKQogICAgICAgICAgICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCkpKSkKCihzZXQhICguLWlubmVySFRNTCBhcHApICNodG1sIFs6bXktZWxlbWVudF0p).
- [#483](https://github.com/squint-cljs/squint/issues/483): Fix operator precedence problem

## v0.6.95 (2024-02-26)

- [#472](https://github.com/squint-cljs/squint/issues/472): Use consistent alias
- [#474](https://github.com/squint-cljs/squint/issues/474): fix JSX fragment
- [#475](https://github.com/squint-cljs/squint/issues/475): don't crash watcher on deleting file

## v0.6.94 (2024-02-20)

- Add `simple-benchmark`

## v0.6.93 (2024-02-16)

- [#468](https://github.com/squint-cljs/squint/issues/468): Keywords in JSX should render with hyphens

## v0.6.92 (2024-02-15)

- [#466](https://github.com/squint-cljs/squint/issues/466): Fix `doseq` expression with `set!` in function return position

## v0.6.91 (2024-02-14)

- [#462](https://github.com/squint-cljs/squint/issues/462): Add `"exports"` field to `package.json`
- [#460](https://github.com/squint-cljs/squint/issues/460): escape `<` and `>` in JSX strings

## v0.6.90 (2024-02-06)

- [#458](https://github.com/squint-cljs/squint/issues/458): don't emit `null` in statement position

## v0.6.89 (2024-02-01)

- [#455](https://github.com/squint-cljs/squint/issues/455): don't export non-public vars

## v0.6.88 (2024-01-10)

- Fix infix operator in return position
- Allow playground to use JSX in non-REPL mode

## v0.6.87 (2024-01-06)

- Add transducer arity to all existing core functions

## v0.5.86 (2023-12-23)

- Support `^:gen` + `js-yield` + `js-yield*` to write JS generator functions. See [playground](https://squint-cljs.github.io/squint/?src=KGRlZm4gXjpnZW4gZm9vIFtdCiAgKGpzLXlpZWxkIDEpCiAgKGpzLXlpZWxkKiBbMiAzXSkKICAobGV0IFt4IChpbmMgMyldCiAgICAoanMteWllbGQgeCkpCiAgKGxldCBbeCAoZG8gKGpzLXlpZWxkIDUpCiAgICAgICAgICAgIDYpXQogICAgKGpzLXlpZWxkIHgpKSkKCih2ZWMgKGZvbykp)
- Add `update-keys` and `update-vals`
- Add `=` as reified function

## v0.4.85 (2023-12-20)

- [#449](https://github.com/squint-cljs/squint/issues/449): fix issue with `:refer`

## v0.4.84 (2023-12-19)

- Add `memoize`, `filter` transducer arity, `peek`, `pop`
- Export classes defined with `defclass`
- Support `^:async` Object method in `defclass`

## v0.4.83 (2023-12-16)

- Better support for pragmas and JSDoc via `js*` + `//` and `/* */`
- Add `rem`, `nnext`, `str/end-with?`, `str/index-of` and `str/last-index-of`
- Fix alias with hypen in REPL mode

## v0.4.82 (2023-12-14)

- Keep top level strings and comments (via `js*`) before imports (for JSDoc, Next.js `"use client"`, etc)

## v0.4.81 (2023-12-11)

- Fix `compileString` in JS API

## v0.4.80 (2023-12-11)

- Optimization: sort largest set first for `set/union`, and smallest first for `set/intersection`
- Add `sorted-set`, `long`, `abs`, `keep-indexed` transducer arity

## v0.4.79 (2023-12-09)

- The `children` function in `tree-seq` may return `nil`

## v0.4.78 (2023-12-09)

- Add `clojure.set/join`
- Add `tree-seq`, `flatten`, `seq?` and `sequential?`

## 0.4.77 (2023-12-09)

- Add `clojure.set` functions `select`, `rename-keys`, `rename`, `project`, and `map-invert` ([@PEZ](https://github.com/PEZ))
- Fix `reduce-kv` with `js/Map` input

## 0.4.76 (2023-12-07)

- Add more `clojure.set` functions: `difference`, `union`, `subset?`, and `superset?` ([@PEZ](https://github.com/PEZ))

## 0.4.75 (2023-12-07)

- Let any object that has `Symbol.iterable` be destructureable even if it is not `instance of Object`

## 0.4.74 (2023-12-07)

- Initial version of `clojure.set`
- [#418](https://github.com/squint-cljs/squint/issues/418): Add `reductions`
- Add `bit-shift-left` and more `bit-` related macros
- Fix `not` with respect to truthiness
- Fix `reduce` without initial value + empty coll, it should call `f()`
- Add serve-playground bb task
- Update playground with button for creating a blank AOC playground

## 0.4.73 (2023-12-05)

- [#407](https://github.com/squint-cljs/squint/issues/407): fix conditional rendering
- Add `not-empty`
- Fix `into` + set + xform

## 0.4.72 (2023-12-04)

- Allow JSX to be used in playground. See [react](https://squint-cljs.github.io/squint/?repl=true&src=KHJlcXVpcmUgJ1sicmVhY3QiIDphcyByZWFjdF0pCihyZXF1aXJlICdbInJlYWN0LWRvbSIgOmFzIHJkb21dKQoKKGRlZm9uY2UgY29tcG9uZW50LXN0YXRlIChhdG9tIDApKQoKKGRlZm4gQmFyIFt7OmtleXMgW2ZpcnN0bmFtZSBsYXN0bmFtZV0gOmFzIHByb3BzfV0KICAobGV0IFtbY2xpY2tzIHNldENsaWNrc10gKHJlYWN0L3VzZVN0YXRlIEBjb21wb25lbnQtc3RhdGUpXQogICAgI2pzeCBbOjw%2BCiAgICAgICAgICBbOnNwYW4gZmlyc3RuYW1lICIgIiBsYXN0bmFtZV0KICAgICAgICAgIFs6ZGl2ICJZb3UgY2xpY2tlZCAiIGNsaWNrcyAiIHRpbWVzISJdCiAgICAgICAgICBbOmJ1dHRvbiB7Om9uQ2xpY2sgIyhzZXRDbGlja3MgKHN3YXAhIGNvbXBvbmVudC1zdGF0ZSBpbmMpKX0KICAgICAgICAgICAiQ2xpY2sgbWUiXV0pKQoKKGRlZm4gRm9vIFtdCiAgI2pzeCBbOmRpdiAiSGVsbG8sICIKICAgICAgICAobGV0IFttIChhc3NvYyB7OmZpcnN0bmFtZSAiTWljaGllbCJ9IDpsYXN0bmFtZSAiQm9ya2VudCIpXQogICAgICAgICAgI2pzeCBbQmFyIHs6JiBtfV0pXSkKCihkZWZvbmNlIGVsdCAoZG90byAoanMvZG9jdW1lbnQuY3JlYXRlRWxlbWVudCAiZGl2IikKICAgICAgICAgICAgICAgKGpzL2RvY3VtZW50LmJvZHkucHJlcGVuZCkpKQoKKGRlZiByb290IChyZG9tL2NyZWF0ZVJvb3QgZWx0KSkKCigucmVuZGVyIHJvb3QgI2pzeCBbRm9vXSk%3D) example.
- Add `re-pattern` (fixes [#396](https://github.com/squint-cljs/squint/issues/396))
- Add `zipmap`

## 0.4.71 (2023-12-02)

- More helpful error from JS when using unresolved symbol in REPL mode

## 0.4.70 (2023-12-02)

- Allow `>`, `<` etc to be used as HOFs
- Fix `str/split` with respect to trailing empty split elements

## 0.4.69 (2023-12-02)

- Fix vararg functions in REPL mode

## 0.4.68 (2023-12-01)

- [#394](https://github.com/squint-cljs/squint/issues/394): add `int`
- [#393](https://github.com/squint-cljs/squint/issues/393): `Math` can be used without `js/` prefix
- Expose compiler state via `compileStringEx` for playground, preserves namespace transitions

## 0.4.67 (2023-11-28)

- Restore backward compatibility with code that is compiled with older versions of squint

## 0.4.66 (2023-11-28)

- Optimize various outputs for smaller size
- Add `js-in`

## 0.4.65 (2023-11-28)

- Support `into` + `xform`

## 0.4.64 (2023-11-27)

- Support `sort` on strings

## 0.4.63 (2023-11-26)

- [#386](https://github.com/squint-cljs/squint/issues/386): allow expression in value position in map literal

## 0.4.62 (2023-11-25)

- Improvements with respect to laziness in `mapcat` and `concat`
- Do not array mutate argument in `reverse`

## 0.4.61 (2023-11-25)

- Escape JSX attribute vector value (and more)

## 0.4.60 (2023-11-24)

- `map` + `transduce` support
- Fix `for` in REPL mode
- Throw when object is not iterable in `for`
- Make next lazy when input is lazy
- Fix playground shim (fixes issue in older versions of Safari)

## 0.4.59 (2023-11-23)

- Add `js-mod` and `quot`

## 0.4.58 (2023-11-22)

- [#380](https://github.com/squint-cljs/squint/issues/380): Don't emit space in between `#jsx` tags
- Add `re-find`

## 0.4.57 (2023-11-21)

- Add `condp` macro

## 0.4.56 (2023-11-20)

- Use `compare` as default compare function in `sort` (which fixes numerical sorting)

## 0.4.55 (2023-11-20)

- Allow `assoc!` to be called on arbitrary classes (regression)

## 0.4.54 (2023-11-19)

- Improve `get` to call `get` method when present.

## 0.4.53 (2023-11-19)

- False alarm, I was playing the pinball game in low power mode on my mobile :facepalm:

## 0.4.52 (2023-11-19)

- Revert truthiness checks via funcall, negative impact on performance with
  pinball game on mobile

## 0.4.51 (2023-11-18)

- Allow keywords and collections to be used as functions in HOFs
- Make filter, etc aware of truthiness
- Reduce code size for truthiness checks

## 0.4.50 (2023-11-18)

- Add `str/split-lines`
- Add `partition-by`
- Add `parse-long`
- Add `sort-by`

## 0.4.49 (2023-11-18)

- Fix top level await

## 0.4.48 (2023-11-17)

- Support multiple dimensions in `aset`
- Add `coercive-=` as alias for `==`
- Add `js-delete`

## 0.4.47 (2023-11-17)

- Fix `min-key` and `max-key` and improve tests

## 0.4.46 (2023-11-16)

- Add `min-key` and `max-key`
- Fix `defonce` in REPL-mode

## 0.4.45 (2023-11-16)

- Fix `doseq` and `for` when binding name clashes with core var

## 0.4.44 (2023-11-15)

- No-op release to hopefully fix caching issue with jspm

## 0.4.43 (2023-11-15)

- Several REPL improvements
- Improve [https://squint-cljs.github.io/squint/](https://squint-cljs.github.io/squint/)

## 0.4.42 (2023-11-13)

- Allow alias name to be used as object in REPL mode

## 0.4.41 (2023-11-12)

- Copy resources when using `squint compile` or `squint watch`

## 0.4.40 (2023-11-12)

- Return map when `select-keys` is called with `nil`
- nREPL server: print values through `cljs.pprint` ([@PEZ](https://github.com/PEZ))

## 0.4.39 (2023-11-10)

- Initial (incomplete!) nREPL server on Node.js: `npx squint nrepl-server :port 1888`
- Update/refactor [threejs](examples/threejs) example

## 0.3.38 (2023-11-07)

- [#360](https://github.com/squint-cljs/squint/issues/360): `assoc-in!` should not mutate objects in the middle if they already exist
- Evaluate `lazy-seq` body just once
- Avoid stackoverflow with `min` and `max`
- [#360](https://github.com/squint-cljs/squint/issues/360): fix assoc-in! with immutable objects in the middle
- Add `mod`, `object?`
- Optimize `get`
- Add [threejs](examples/threejs) example

## 0.3.37 (2023-11-05)

- [#357](https://github.com/squint-cljs/squint/issues/357): fix version in help text
- Fix iterating over objects
- Add `clojure.string`'s `triml`, `trimr`, `replace`
- Fix `examples/vite-react` by adding `public/index.html`
- Add `find`, `bounded-count`, `boolean?`, `merge-with`, `meta`, `with-meta`, `int?`, `ex-message`, `ex-cause`, `ex-info`
- Fix munging of reserved symbols in function arguments

## 0.3.36 (2023-10-31)

- [#350](https://github.com/squint-cljs/squint/issues/350): `js*` should default to `:context :expr`
- [#352](https://github.com/squint-cljs/squint/issues/352): fix `zero?` in return position
- Add `NaN?` ([@sher](https://github.com/sher))

## 0.3.35 (2023-10-25)

- [#347](https://github.com/squint-cljs/squint/issues/347): Add `:pre` and `:post` support in `fn`
- Add `number?`
- Support `docstring` in `def`

## 0.3.34 (2023-10-24)

- Handle multipe source `:paths` in a more robust fashion

## 0.3.33 (2023-10-24)

- [#344](https://github.com/squint-cljs/squint/issues/344): macros can't be used via aliases

## 0.3.32 (2023-10-17)

- Add `squint.edn` support, see [docs](README.md#squintedn)
- Add `watch` subcommand to watch `:paths` from `squint.edn`
- Make generated `let` variable names in JS more deterministic, which helps hot reloading in React
- Added a [vite + react example project](examples/vite-react).
- Resolve symbolic namespaces `(:require [foo.bar])` from `:paths`

## 0.2.31 (2023-10-09)

- Add `bit-and` and `bit-or`

## 0.2.30 (2023-10-04)

- Include `lib/squint.core.umd.js` which defines a global `squint.core` object (easy to use in browsers, see [docs](README.md#compile-on-a-server-use-in-a-browser))

## 0.2.29 (2023-10-03)

- Add `subs`, `fn?`, `re-seq`
- Add `squint.edn` with `:paths` to resolve macros from (via `:require-macros`)

## 0.2.28 (2023-09-18)

- Fix `and` and `or` with respect to CLJS truthiness

## 0.2.27 (2023-09-18)

- Respect CLJS truth semantics: only `null`, `undefined` and `false` are non-truthy, `0` and `""` are truthy.
- Fix `dotimes`

## 0.2.26 (2023-09-17)

- `squint repl` improvements

## 0.2.25 (2023-09-17)

- Do not resolve binding in `catch` to core var
- Fix reading `.cljc` files
- Allow passing JS object as opts in JavaScript API's `compileString`

## 0.2.24 (2023-09-13)

- Fix `instance?` in return position
- `to-array`, `str/starts-with?`

## 0.1.23

- Support next on non-arrays
- Add `compare`

## 0.1.22

- Fix [#325](https://github.com/squint-cljs/squint/issues/325): fix varargs destructuring
- Fix [#326](https://github.com/squint-cljs/squint/issues/326): bun compatibility

## 0.1.21

- Fix `into-array`

## 0.1.20

- Add `into-array`, `some-fn`, `keep-indexed`
- Fix for `lazy-seq` when body returns `nil`

## 0.1.19

- Add `max`, `min`, `every-pred`

## 0.1.18

- Add `reduce-kv`

## 0.1.17

- [#320](https://github.com/squint-cljs/squint/issues/320): fix overriding core vars

## 0.1.16

- Add `rand-nth`, `aclone`, `add-watch`, `remove-watch`

## 0.1.15

- [#288](https://github.com/squint-cljs/squint/issues/288): support `defclass`. See [doc/defclass.md](doc/defclass.md).
- [#312](https://github.com/squint-cljs/squint/issues/312): implement doall and dorun

## 0.0.14

- Drop cherry core from NPM package (cruft from porting cherry to squint)

## 0.0.13

- Fix compilation of empty list
- Fix `and` and `or` without arguments
- [#308](https://github.com/squint-cljs/squint/issues/308): `doseq` in return position emits invalid code

## 0.0.12

- Add `js-obj`

## 0.0.11

- Add `for` and `doseq` ([@lilactown](https://github.com/lilactown))

## 0.0.10

- [#289](https://github.com/squint-cljs/squint/issues/289): support `:as` alias with hyphen in namespace require libspec

## 0.0.9

- [#286](https://github.com/squint-cljs/squint/issues/286): make dissoc work with multiple keys

## 0.0.8

- [#284](https://github.com/squint-cljs/squint/issues/284): fix undefined keys/values when passing objects and maps to conj

## 0.0.7

- [#274](https://github.com/squint-cljs/squint/issues/274): fix logic precedence by wrapping in parens

## 0.0.6

Add preliminary Node.js API in `node.js`

## 0.0.5

- Support `{:& more :foo :bar}` syntax in JSX to spread the more map into the props, inspired by [helix](https://github.com/lilactown/helix)

## 0.0.4

- Add `zero?` `pos?` and `neg?` core functions

## 0.0.3

- Fix `boolean` core function export

## 0.0.2

- Escape boolean in JSX attribute
- Add `boolean` core function

## 0.0.1

- Fix `+` symbol import by bumping shadow-cljs
- Move `@babel/preset-react` dependency to dev dependency

## 0.0.0-alpha.52

- Fix async/await + variadiac arguments
- Fix namespaced component in JSX

## 0.0.0-alpha.51

- Change default extension back to `.mjs`. Use `--extension js` to change this to `.js`.

## 0.0.0-alpha.50

- Fix rendering of number attributes in JSX

## 0.0.0-alpha.49

- Support `--extension` option to set extension for JS files
- Change default extension from `.mjs` to `.js`
