# Stdlib bugs & Clojure deviations

Findings from a review of `src/squint/core.js`, `src/squint/string.js`, and
`src/squint/set.js`. The "confirmed" items were verified by running the code.

## Confirmed bugs

### 1. `parse-long` range check is a no-op — `core.js:2647` — FIXED

```js
if (Number.MIN_SAFE_INTEGER <= i <= Number.MAX_SAFE_INTEGER) {
```

Chained-comparison bug: `MIN <= i` yields a boolean, then `bool <= MAX` is
always true, so the bound is never enforced.
`(parse-long "99999999999999999999")` returned `1e20` instead of `nil`.

Confirmed against CLJS (both `plk` and `cljs/core.cljs:12230`): CLJS guards the
safe-integer range inclusively and returns `nil` outside it — `9007199254740992`
(2^53) → `nil`, `9007199254740991` (MAX_SAFE_INTEGER) → kept. Squint's code
intended the same check; the chained comparison defeated it.

Fixed: `Number.MIN_SAFE_INTEGER <= i && i <= Number.MAX_SAFE_INTEGER`.

### 2. `clojure.string/split` ignores Clojure's limit semantics — `string.js:52` — FIXED

JS `String.prototype.split(re, limit)` *truncates* the result; Clojure's limit
caps the number of *splits* and keeps the remainder.
`(str/split "a-b-c-d" #"-" 2)` → squint `["a" "b"]`, Clojure `["a" "b-c-d"]`.

Fixed: a positive limit now loops, splitting at most `limit-1` times and keeping
the remainder; `<1`/unset does a full split. Also fixed `discardTrailingIfNeeded`
to drop trailing empties for limit `0` (not just unset), matching Clojure
(negative limit keeps them). Confirmed against CLJS (plk).

### 3. `select-keys` drops keys with `nil` values — `core.js:1304` — FIXED

```js
if (v != undefined) { assoc_BANG_(ret, k, v); }
```

`null != undefined` is `false` (loose equality), so a key mapped to `nil` is
silently omitted. `(select-keys {:a nil :b 2} [:a :b])` → squint `{b:2}`,
Clojure `{:a nil :b 2}`.

Fixed: `v !== undefined`. Confirmed against CLJS (plk):
`(select-keys {:a nil :b 2 :c 3} [:a :b :missing])` → `{:a nil, :b 2}`.

### 4. `clojure.set/intersection` & `union` size-optimization is dead code — `set.js:27,65` — FIXED

```js
case 2: return xs[0].length > xs[1].length ? ...
```

Squint sets are JS `Set`s, which expose `.size`, not `.length`. The comparison
is always `undefined > undefined` → `false`, so the "pick the smaller set"
branch never fires. Results are still correct (the ops are symmetric and
`_intersection2` re-swaps by size internally), but the optimization is inert.

Fixed: use `.size`. No behaviour change, just restores the intended
"iterate the smaller set" / "copy the larger set" optimization.

## Semantic deviations from Clojure

### 5. `compare` throws on booleans — `core.js:2363` — FIXED

Only handled number/string/array; booleans fell through to
`throw "comparing boolean to boolean"`. Clojure: `(compare false true)` → `-1`.
Also meant `(sort [true false])` threw.

Fixed: added a `boolean`/`boolean` case to the same-type branch (`false < true`
via JS coercion). Confirmed against CLJS (plk): `(compare false true)` → `-1`,
`(sort [true false true false])` → `(false false true true)`.

### 6. `seqable?` returns `false` for plain objects (maps) — `core.js:575` — FIXED

Checked only `x[Symbol.iterator]`, which objects lack. But `seq`/`iterable` do
work on objects (via `Object.entries`), and the README says `seqable?` should
predict whether `seq`/`iterable` will work. So `(seqable? {:a 1})` → `false`
while `(seq {:a 1})` succeeds — internally inconsistent, and Clojure returns
`true`.

Fixed: added `object_QMARK_(x)` (plain-object check) to `seqable?`.
`iterable` previously short-circuited on `seqable?` and would have returned the
raw object instead of its entries, so `iterable` was decoupled and now inlines
the `x[Symbol.iterator]` fast path (bit-identical to its original behaviour;
plain objects still fall through to the `Object.entries` branch). `seqable?` now
has no internal callers, so its predicate change can't affect iteration.
`iterable`'s fallback stays `instanceof Object` (not `object_QMARK_`) so TC39
Records keep working. Confirmed against CLJS (plk): `(seqable? {:a 1})` → `true`,
`(seqable? 1)`/`(seqable? true)`/`(seqable? inc)` → `false`.

### 7. `clojure.string/replace` with string match interprets `$` in the replacement — `string.js:97` — NOT A BUG

Passes the replacement straight to `String.prototype.replace`, so `$&`, `$1`,
etc. are expanded. This deviates from Clojure JVM (which treats the string/string
replacement literally), but MATCHES ClojureScript, which squint targets.
Confirmed against CLJS (plk): `(str/replace "hello" "l" "$&")` → `"hello"`,
`(str/replace "hello" "l" "$1x")` → `"he$1x$1xo"` - identical to squint. No change.

### 8. `nth` returns the not-found default for an in-bounds `undefined` element — `core.js:520` — FIXED

Tested `elt !== undefined` (a value check) rather than the index bound, so an
in-bounds element that is `undefined` was reported as missing. The common
`(nth [1 nil 3] 1 :x)` actually worked (squint `nil` is `null`, and
`null !== undefined`); the bug bit on real `undefined` holes -
`(nth (js/Array. 3) 0 :x)` → `:x` instead of `nil`. Affects sparse arrays,
`(object-array n)` / `(make-array n)`, and JS-interop arrays holding `undefined`.

Fixed: the array path now checks `idx >= 0 && idx < coll.length` and returns
`coll[idx]` (even when `undefined`); the iterable path returns the value as soon
as the loop reaches `idx`. The iterable path deliberately avoids `.length` so it
still works on length-less and infinite/lazy seqs (`(nth (range) 5)` → `5`).

## Minor / cosmetic

- **`parse-double`'s whitespace class is double-escaped — `core.js:2658,2661`:**
  `[\\x00-\\x20]` in a non-raw regex literal matches a literal backslash + `x00`…,
  not the intended control-char range. Leading/trailing whitespace handling is
  broken (the `*` quantifier hides it for normal input).
- **`parse-double` error path — `core.js:2670`:** `throw new parsing_err(s)` —
  `parsing_err` already throws, so `new` never completes. Harmless but odd;
  `parse-long` uses the plain `return parsing_err(s)` form.
