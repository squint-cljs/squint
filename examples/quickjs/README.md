# QuickJS

[QuickJS](https://bellard.org/quickjs/) is a small and embeddable Javascript engine. You can use it to produce standalone executables from JS scripts.

To install QuickJS on macOS, you can use [brew](https://formulae.brew.sh/formula/quickjs).

Let's compile the `main.cljs` script using ClavaScript first:

```
$ npx clava main.cljs
[clava] Compiling CLJS file: main.cljs
[clava] Wrote JS file: main.mjs
```

Unfortunately, QuickJS doesn't load dependencies from NPM. Given the script `main.cljs`, we'll first have to bundle it, e.g. using esbuild:

```
$ npx esbuild main.mjs --bundle --minify --platform=node --outfile=main.min.mjs --format=esm

  main.min.mjs  842b

⚡ Done in 11ms
```

Now we are ready to run the script with QuickJS:

```
$ qjs main.min.mjs
Hello world!
[[1,4,7],[2,5,8],[3,6,9]]
```

Finally, to create a standalone executable:

```
$ qjsc -o hello main.min.mjs
```

Now we are ready to execute it:

```
$ ./hello
Hello world!
[[1,4,7],[2,5,8],[3,6,9]]
```

The executable is about 750kb.
