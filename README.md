## ClavaScript

ClavaScript is an experimental ClojureScript syntax to
JavaScript compiler.

It is not intended as a replacement for ClojureScript but as a tool to target JS
for anything you would not use ClojureScript, for whatever reason: performance,
bundle size, ease of interop, etc.

> :warning: This project should be considered experimental and may still undergo
> breaking changes. It's fine to use it for non-critical projects but don't use
> it in production yet.

## Quickstart

Although it's early days, you're welcome to try out `clava` and submit issues.

``` shell
$ mkdir clava-test && cd clava-test
$ npm init -y
$ npm install clavascript@latest
```

Create a `.cljs` or `.clvs` file, e.g. `example.cljs`:

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
$ npx clvs run example.cljs
true
6
```

Run `npx clvs --help` to see all command line options.

## Why ClavaScript

ClavaScript lets you write CLJS syntax but emits small JS output, while still having
parts of the CLJS standard library available (ported to mutable data structures,
so with caveats). This may work especially well for projects e.g. that you'd
like to deploy on CloudFlare workers, node scripts, Github actions, etc. that
need the extra performance, startup time and/or small bundle size.

## Differences with ClojureScript

- ClavaScript does not protect you in any way from the pitfalls of JS with regards to truthiness, mutability and equality
- There is no CLJS standard library. The `"clavascript/core.js"` module has similar JS equivalents
- Keywords are translated into strings
- Maps, sequences and vectors are represented as mutable objects and arrays
- Most functions return arrays and objects, not custom data structures
- Supports async/await:`(def x (js/await y))`. Async functions must be marked
  with `^:async`: `(defn ^:async foo [])`.
- `assoc!`, `dissoc!`, `conj!`, etc. perform in place mutation on objects
- `assoc`, `dissoc`, `conj`, etc. return a new shallow copy of objects
- `println` is a synonym for `console.log`
- `pr-str` and `prn` coerce values to a string using `JSON.stringify`

### Seqs

ClavaScript does not implement Clojure seqs. Instead it uses the JavaScript
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

- Lazy seqs in ClavaScript are built on generators. They do not cache their results, so every time they are consumed, they are re-calculated from scratch.
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

See an example of an application using JSX [here](https://clavascript.github.io/demos/clava/solidjs/) ([source](https://github.com/clavascript/clavascript/blob/main/examples/solidjs/src/App.cljs)).

## Async/await

ClavaScript supports `async/await`:

``` clojure
(defn ^:async foo [] (js/Promise.resolve 10))

(def x (js/await (foo)))

(println x) ;;=> 10
```

## Roadmap

In arbitrary order, these features are planned:

- Macros
- REPL
- Protocols

## Core team

The core team consists of:

- Michiel Borkent ([@borkdude](https://github.com/borkdude))
- Will Acton ([@lilactown](https://github.com/lilactown))
- Cora Sutton ([@corasaurus-hex](https://github.com/corasaurus-hex))

License
=======

ClavaScript is licensed under the EPL, the same as Clojure core and [Scriptjure](https://github.com/arohner/scriptjure). See epl-v10.html in the root directory for more information.
