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

### 4. `clojure.set/intersection` & `union` size-optimization is dead code — `set.js:27,65`

```js
case 2: return xs[0].length > xs[1].length ? ...
```

Squint sets are JS `Set`s, which expose `.size`, not `.length`. The comparison
is always `undefined > undefined` → `false`, so the "pick the smaller set"
branch never fires. Results are still correct (the ops are symmetric and
`_intersection2` re-swaps by size internally), but the optimization is inert.

Fix: use `.size`.

## Semantic deviations from Clojure

### 5. `compare` throws on booleans — `core.js:2363`

Only handles number/string/array; booleans fall through to
`throw "comparing boolean to boolean"`. Clojure: `(compare false true)` → `-1`.
Also means `(sort [true false])` throws.

### 6. `seqable?` returns `false` for plain objects (maps) — `core.js:575`

Checks only `x[Symbol.iterator]`, which objects lack. But `seq`/`iterable` do
work on objects (via `Object.entries`), and the README says `seqable?` should
predict whether `seq`/`iterable` will work. So `(seqable? {:a 1})` → `false`
while `(seq {:a 1})` succeeds — internally inconsistent, and Clojure returns
`true`.

### 7. `clojure.string/replace` with string match interprets `$` in the replacement — `string.js:97`

Passes the replacement straight to `String.prototype.replace`, so `$&`, `$1`,
etc. are expanded. `(str/replace "hello" "l" "$&")` → squint `"hello"`, Clojure
`"he$&$&o"` (literal). For the string/string arity the replacement should be
treated literally.

### 8. `nth` returns the not-found default for an in-bounds `undefined` element — `core.js:520`

`(nth [1 nil 3] 1 :x)` → `:x` instead of `nil`, because it tests
`elt !== undefined` rather than checking the index bound. Affects arrays holding
`undefined`.

## Minor / cosmetic

- **`parse-double`'s whitespace class is double-escaped — `core.js:2658,2661`:**
  `[\\x00-\\x20]` in a non-raw regex literal matches a literal backslash + `x00`…,
  not the intended control-char range. Leading/trailing whitespace handling is
  broken (the `*` quantifier hides it for normal input).
- **`parse-double` error path — `core.js:2670`:** `throw new parsing_err(s)` —
  `parsing_err` already throws, so `new` never completes. Harmless but odd;
  `parse-long` uses the plain `return parsing_err(s)` form.
