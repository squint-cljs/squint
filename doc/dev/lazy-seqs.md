# Lazy seqs

Developer notes on how lazy sequences work in the squint runtime
(`src/squint/core.js`). For users: lazy seqs behave like ClojureScript. This
document is about the implementation.

## Goals

A lazy seq must satisfy three constraints at once:

1. Cached: traversing the same seq more than once computes each element once.
2. Streaming: a single forward pass over a large or infinite seq that is not
   retained runs in roughly constant memory.
3. Fast: per-element overhead low enough for hot pipelines.

The native JS iterator is single-shot, so caching cannot be the iterator
itself. The cache is a linked structure of cells; iteration walks it with a
disposable cursor.

## Cells

`LazyIterable` (and `LazySeq`, used by the `lazy-seq` macro) is one cell of a
self-caching chunked chain. A cell holds:

- `chunk`: an array of realized values, or `null` for the terminal cell.
- `_rest`: the next cell.
- `step`: a thunk producing `[chunkArray, nextStep]` or `null` at the end.

`force()` runs `step` once, caching `chunk` and building `_rest`. A cell is
realized at most once. `chunk` is always non-empty: an op that can drop a whole
chunk (e.g. `filter`) skips ahead internally rather than emitting an empty one.

`[Symbol.iterator]()` returns a cursor that flattens chunks into elements:

```
cell0 -> cell1 -> cell2 -> (terminal, chunk = null)
chunk    chunk    chunk
[a b c]  [d e f]  [g h]
```

The cursor holds only its current cell and an index. Walking advances
`cell = cell._rest`. Two iterations are two independent cursors over the same
cells:

- First pass forces cells, pulling the underlying iterator. Realized cells cache
  their chunk.
- Second pass starts a fresh cursor at the head; every cell is already realized,
  so it reads cached chunks and never touches the underlying iterator.

## Streaming

A cursor references only its current cell, so cells behind it become
unreachable and collectable, as long as nothing retains the head. Two things
would otherwise pin the head:

1. An op's generator closing over its input. `(map f coll)` must not capture
   `coll`; it captures the input cursor instead, obtained before the generator:

   ```js
   const it = es6_iterator(iterable(coll));
   return lazy(function* () { for (const x of it) yield f(x); });
   ```

   The generator closes over `it` (a moving cursor), not the input head, so the
   input streams.

2. `Cons` and `concat` hold their tail. Both release the reference once the
   tail's iterator is taken.

Caveat: V8 keeps a function argument alive for the duration of the call. A lazy
seq passed to `reduce`/`dorun` is therefore pinned while that call runs, so
reducing a very large lazy retains it. Nulling the parameter inside the callee
does not help: the caller still holds the argument. The JVM JIT drops the dead
local; V8 does not. `doseq` and op chains stream because the seq is a `for...of`
iterable, not a held argument.

## Chunking

Each cell carries a chunk of up to `CHUNK_SIZE` (32) values, like ClojureScript.
Chunk-aware ops transform a whole chunk array in a tight loop instead of driving
a generator per element, which is the bulk of the throughput win.

Chunkedness is source-dependent, matching ClojureScript:

- `range` and arrays are chunked sources: they realize 32 values at a time.
- `lazy` / `lazy-seq` / `iterate` / `cons` are unchunked: one element at a time,
  so exact laziness is preserved.

Chunk-aware ops read input through `chunkCells`, which passes existing cells
through unchanged. A chunked input stays chunked, an unchunked input stays
unchunked, following the same principle as ClojureScript's `chunked-seq?`
branch. Chunk-aware ops: `map` (one coll), `filter`, `remove`, `keep`,
`map-indexed`, `keep-indexed`, `concat`. Other ops stay on the generic unchunked
path: they consume input element-wise and emit one-element chunks.

`concat` passes each coll's chunks through, so concatenating chunked sources
stays fast (about 5x over the element-wise path for large arrays).

`take` is unchunked, here and in ClojureScript. It must not chunk its output, or
a downstream chunked op would over-realize past the take boundary.

A consequence, same as ClojureScript: `(take 1 (map prn (range)))` prints 32,
because `range` is a chunked source and `map` realizes its full chunk.
`(take 1 (map prn (iterate inc 0)))` prints once, because `iterate` is
unchunked.

## Adding an op

- Element-wise op over one coll, where realizing a whole chunk is acceptable:
  make it chunk-aware. Read the input with `chunkCells`, build a `step` that maps
  the input chunk to an output chunk, and skip ahead on a chunk that empties out
  so a step never returns an empty chunk.
- Anything else: use `lazy(function* () { ... })`. Hoist the input iterator out
  of the generator (see Streaming) so it does not pin the input head. The result
  is unchunked.

## Realizers

Functions that consume a seq fully take a chunk-aware fast path: they process
whole chunk arrays instead of going element by element through the seq cursor.

Arrays keep a dedicated shortcut (`coll[idx]`, `coll.length`, an index loop -
all O(1) or the tightest inline loop). The non-array tail (chunked cells and any
other seqable) goes through `chunkCursor(coll)`, which returns a function
yielding the next chunk array or null. Callers drive it with an inline loop, so
the accumulator stays a plain local and it runs as fast as a hand-written cell
walk. A callback or generator does not: a callback closes over the mutated
accumulator and deopts, a generator pays per-chunk yield overhead.

- `reduce` runs the reducing function over each chunk array in a tight loop
  (and an index loop for plain array input). Other reducers build on it.
- `vec` and `into` (vector target) bulk-append chunks via `pushAll`. `into` must
  not spread the whole seq into `conj` (it overflows the call stack on large
  input), and it preserves the target's metadata via `copy`.
- `count` sums chunk lengths.

## Tests

`test/squint/lazy_memory_test.cljs` pins the contract: caching (element-fn call
counts), chunked vs unchunked realization, and streaming (max live heap under a
ceiling). The streaming assertions need `--expose-gc`, which `bb test:node` sets
when running `node lib/squint_tests.js`.
