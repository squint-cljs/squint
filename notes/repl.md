# REPL

To keep Clojure semantics for REPL, you don't escape compiling vars to globally
defined vars. This will happen in `dev` mode.

``` clojure
(ns foo)
(def x 1)
```

=>

`cherry.root.foo.x = 1`

But what about the ES6 module then?

``` javascript
foo.mjs:

export { x }
```

In a REPL, when you execute `(require '[foo])` it should not compile to `import * as foo from './foo.mjs` or so, but to:

``` clojure
var foo = cherry.root.foo
```

perhaps?

Or should we compile to:

``` clojure
import * as foo from './foo.mjs?t=123'
```

and replace `t=123` with something else once we detect that `foo.cljs` is out of date?
And we could reload the "current" module by doing the same?


