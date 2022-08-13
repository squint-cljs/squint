## ClavaScript

Experimental ClojureScript syntax to JavaScript compiler.

> :warning: This project is an experiment and not recommended to be used in
> production. It currently has many bugs and will undergo many breaking changes.

## Quickstart

Although it's early days and far from complete, you're welcome to try out `clava` and submit issues.

``` shell
$ mkdir clava-test && cd clava-test
$ npm init -y
$ npm install clavascript@latest
```

Create a `.cljs` file, e.g. `example.cljs`:

``` clojure
(ns example
  (:require ["fs" :as fs]
            ["url" :refer [fileURLToPath]]))

(prn (fs/existsSync (fileURLToPath js/import.meta.url)))

(defn foo [{:keys [a b c]}]
  (+ a b c))

(prn (foo {:a 1 :b 2 :c 3}))
```

Then compile and run (`run` does both):

```
$ npx clava run example.cljs
true
6
```

Run `npx clavascript --help` to see all command line options.

## Differences with ClojureScript

- Keywords are translated into strings
- Maps and vectors are translated into objects and arrays
- `assoc!` and `dissoc!` perform in place mutation on objects
- `println` is a synonym for `console.log`
- Truth semantics of JS

License
=======

Clava is licensed under the EPL, the same as Clojure core and [Scriptjure](https://github.com/arohner/scriptjure). See epl-v10.html in the root directory for more information.
