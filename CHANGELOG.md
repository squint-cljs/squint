# Changelog

[Squint](https://github.com/squint-cljs/squint): ClojureScript syntax to JavaScript compiler

## Unreleased

- Return map when `select-keys` is called with `nil`

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
