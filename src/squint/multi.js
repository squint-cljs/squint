// Multimethod runtime for squint. Opt-in: only imported when defmulti/
// defmethod (or related ops) appear in user code. Keeping this out of
// core.js means programs that don't use multimethods pay zero bundle cost.

import { _EQ_ } from 'squint-cljs/core.js';
import { toFn } from './internal.js';

function isPrimitive(x) {
  const t = typeof x;
  return x == null || t === 'string' || t === 'number' || t === 'boolean' || t === 'bigint' || t === 'symbol';
}

function findKeyByEquiv(m, v) {
  if (isPrimitive(v) && m.has(v)) return v;
  for (const k of m.keys()) if (_EQ_(k, v)) return k;
  return undefined;
}

function setHasEquiv(s, v) {
  if (isPrimitive(v)) return s.has(v);
  for (const x of s) if (_EQ_(x, v)) return true;
  return false;
}

// Map<any, Set<any>> insert with value equality on both keys and
// members — so a preference/ancestry relation expressed with freshly
// allocated vectors like [:km :m] reads back the same as the original.
// Without this, everything that keys on dispatch values (prefer tables,
// hierarchy maps) silently loses entries for compound keys.
function addRel(m, k, v) {
  const key = findKeyByEquiv(m, k) ?? k;
  let s = m.get(key);
  if (!s) { s = new Set(); m.set(key, s); }
  if (!setHasEquiv(s, v)) s.add(v);
}

export function make_hierarchy() {
  return {
    parents: new Map(),
    ancestors: new Map(),
    descendants: new Map(),
  };
}

let _globalHierarchy = null;
function gh() {
  return _globalHierarchy ?? (_globalHierarchy = make_hierarchy());
}

function _isa(h, child, parent) {
  if (_EQ_(child, parent)) return true;
  if (typeof child === 'function' && typeof parent === 'function') {
    return child === parent || child.prototype instanceof parent;
  }
  const ancKey = findKeyByEquiv(h.ancestors, child);
  if (ancKey !== undefined && setHasEquiv(h.ancestors.get(ancKey), parent)) return true;
  if (Array.isArray(child) && Array.isArray(parent) && child.length === parent.length) {
    for (let i = 0; i < child.length; i++) {
      if (!_isa(h, child[i], parent[i])) return false;
    }
    return true;
  }
  return false;
}

export function isa_QMARK_(a, b, c) {
  if (c === undefined) return _isa(gh(), a, b);
  return _isa(a, b, c);
}

function _deriveInto(h, tag, parent) {
  if (_EQ_(tag, parent)) throw new Error(`${String(tag)} can't derive itself`);
  const tagAncKey = findKeyByEquiv(h.ancestors, tag);
  const tagAnc = tagAncKey !== undefined ? h.ancestors.get(tagAncKey) : undefined;
  if (tagAnc && setHasEquiv(tagAnc, parent)) return;
  if (_isa(h, parent, tag)) {
    throw new Error(`Cyclic derivation: ${String(parent)} has ${String(tag)} as ancestor`);
  }
  addRel(h.parents, tag, parent);
  // Matches Clojure's derive: new ancestor relations flow to
  // { tag and everything under tag } × { parent and everything above parent }.
  // Crucially, the left side is the DESCENDANTS of tag (plus tag), not
  // its ancestors — deriving 'tag isa parent' must not make tag's
  // existing ancestors also isa parent.
  const withSelf = (m, t) => {
    const acc = new Set([t]);
    const key = findKeyByEquiv(m, t);
    const s = key !== undefined ? m.get(key) : undefined;
    if (s) for (const v of s) acc.add(v);
    return acc;
  };
  const parentChain = withSelf(h.ancestors, parent);
  const tagChain = withSelf(h.descendants, tag);
  for (const d of tagChain) {
    for (const a of parentChain) {
      if (!_EQ_(d, a)) {
        addRel(h.ancestors, d, a);
        addRel(h.descendants, a, d);
      }
    }
  }
}

export function derive(a, b, c) {
  if (c === undefined) {
    // Rebuild-and-swap the global hierarchy so its identity changes;
    // MultiFn's cache compares hierarchy identity to decide when to
    // invalidate, so in-place mutation would leave stale cached
    // resolutions in place (e.g. a subsequent derive can introduce
    // ambiguity that the cache would otherwise hide).
    _globalHierarchy = cloneHierarchy(gh());
    _deriveInto(_globalHierarchy, a, b);
    return null;
  }
  const next = cloneHierarchy(a);
  _deriveInto(next, b, c);
  return next;
}

function rebuildFromPairs(pairs) {
  const h = make_hierarchy();
  for (const [c, p] of pairs) _deriveInto(h, c, p);
  return h;
}

function cloneHierarchy(h) {
  const out = make_hierarchy();
  for (const f of ['parents', 'ancestors', 'descendants']) {
    for (const [k, s] of h[f]) out[f].set(k, new Set(s));
  }
  return out;
}

export function underive(a, b, c) {
  const [h, tag, parent] = c === undefined ? [gh(), a, b] : [a, b, c];
  const pairs = [];
  for (const [child, parents] of h.parents) {
    for (const p of parents) {
      if (!(_EQ_(child, tag) && _EQ_(p, parent))) pairs.push([child, p]);
    }
  }
  if (c === undefined) {
    _globalHierarchy = rebuildFromPairs(pairs);
    return null;
  }
  return rebuildFromPairs(pairs);
}

// Clojure's canonical signature is (parents tag) / (parents h tag):
// the hierarchy, when given, comes FIRST. Mirror that here so
// (parents h :foo) in user code dispatches correctly.
function hAnd(a, b, field) {
  const [h, tag] = b === undefined ? [gh(), a] : [a, b];
  const s = h[field].get(tag);
  return s && s.size ? new Set(s) : null;
}
export function parents(a, b)     { return hAnd(a, b, 'parents'); }
export function ancestors(a, b)   { return hAnd(a, b, 'ancestors'); }
export function descendants(a, b) { return hAnd(a, b, 'descendants'); }

function _prefers(prefer, a, b) {
  const key = findKeyByEquiv(prefer, a);
  if (key === undefined) return false;
  const s = prefer.get(key);
  if (setHasEquiv(s, b)) return true;
  for (const x of s) if (_prefers(prefer, x, b)) return true;
  return false;
}

function dominates(h, prefer, a, b) {
  if (_prefers(prefer, a, b)) return true;
  if (!_EQ_(a, b) && _isa(h, a, b)) return true;
  return false;
}

class MultiFn {
  constructor(name, dispatchFn, defaultVal, hierarchy) {
    this.name = name;
    this.dispatchFn = toFn(dispatchFn);
    this.defaultDispatchVal = defaultVal;
    this.hierarchy = hierarchy;
    this.methodTable = new Map();
    this.preferTable = new Map();
    this.methodCache = new Map();
    this.cachedHierarchy = hierarchy.deref();
  }
  resetCache() {
    this.methodCache = new Map();
    this.cachedHierarchy = this.hierarchy.deref();
  }
  addMethod(dispatchVal, fn) {
    const existing = findKeyByEquiv(this.methodTable, dispatchVal);
    if (existing !== undefined) this.methodTable.delete(existing);
    this.methodTable.set(dispatchVal, fn);
    this.resetCache();
  }
  removeMethod(dispatchVal) {
    const key = findKeyByEquiv(this.methodTable, dispatchVal);
    if (key !== undefined) this.methodTable.delete(key);
    this.resetCache();
  }
  preferMethod(a, b) {
    if (_prefers(this.preferTable, b, a)) {
      throw new Error(`Preference conflict in multimethod '${this.name}': ${String(b)} is already preferred to ${String(a)}`);
    }
    addRel(this.preferTable, a, b);
    this.resetCache();
  }
  findBest(val) {
    const h = this.hierarchy.deref();
    let best = null;
    for (const [dv, fn] of this.methodTable) {
      if (_isa(h, val, dv)) {
        if (best === null || dominates(h, this.preferTable, dv, best[0])) {
          best = [dv, fn];
        } else if (!dominates(h, this.preferTable, best[0], dv)) {
          throw new Error(`Multiple methods in multimethod '${this.name}' match dispatch value: ${String(val)} -> ${String(dv)} and ${String(best[0])}, and neither is preferred`);
        }
      }
    }
    return best;
  }
  getMethod(val) {
    if (this.cachedHierarchy !== this.hierarchy.deref()) this.resetCache();
    // Two-path cache read: primitives hit Map.get in O(1); non-primitive
    // dispatch values (typically vectors) scan the cache with _EQ_ so
    // freshly-allocated structurally-equal vectors hit prior entries.
    // Previously non-primitives skipped the cache entirely — every
    // dispatch redid findBest, which scans methodTable × _isa cost.
    if (isPrimitive(val)) {
      const cached = this.methodCache.get(val);
      if (cached !== undefined) return cached;
    } else {
      for (const [k, fn] of this.methodCache) if (_EQ_(k, val)) return fn;
    }
    const exactKey = findKeyByEquiv(this.methodTable, val);
    if (exactKey !== undefined) {
      const fn = this.methodTable.get(exactKey);
      this.methodCache.set(val, fn);
      return fn;
    }
    const best = this.findBest(val);
    if (best) {
      this.methodCache.set(val, best[1]);
      return best[1];
    }
    const defKey = findKeyByEquiv(this.methodTable, this.defaultDispatchVal);
    return defKey !== undefined ? this.methodTable.get(defKey) : null;
  }
  invoke(args) {
    const val = this.dispatchFn.apply(null, args);
    const fn = this.getMethod(val);
    if (!fn) throw new Error(`No method in multimethod '${this.name}' for dispatch value: ${String(val)}`);
    return fn.apply(null, args);
  }
}

export function defmulti(name, dispatchFn, opts) {
  opts = opts || {};
  const defaultVal = 'default' in opts ? opts.default : 'default';
  // Accept three shapes for :hierarchy —
  //   (a) omitted     → defer to the global hierarchy
  //   (b) a deref-able ref (atom/var-like) → use as-is
  //   (c) a plain hierarchy (the result of make-hierarchy) → wrap so
  //       MultiFn can call .deref() on it uniformly. The wrapped form
  //       is a frozen snapshot of that hierarchy at defmulti time.
  let hierarchy;
  if (opts.hierarchy == null) hierarchy = { deref: gh };
  else if (typeof opts.hierarchy.deref === 'function') hierarchy = opts.hierarchy;
  else { const h = opts.hierarchy; hierarchy = { deref: () => h }; }
  const mf = new MultiFn(name, dispatchFn, defaultVal, hierarchy);
  const call = function (...args) { return mf.invoke(args); };
  call.multiFn = mf;
  return call;
}

export function defmethod(mf, dispatchVal, fn) {
  mf.multiFn.addMethod(dispatchVal, fn);
  return mf;
}

export function get_method(mf, dispatchVal) {
  return mf.multiFn.getMethod(dispatchVal);
}

export function methods(mf) {
  return new Map(mf.multiFn.methodTable);
}

export function remove_method(mf, dispatchVal) {
  mf.multiFn.removeMethod(dispatchVal);
  return mf;
}

export function remove_all_methods(mf) {
  mf.multiFn.methodTable.clear();
  mf.multiFn.resetCache();
  return mf;
}

export function prefer_method(mf, a, b) {
  mf.multiFn.preferMethod(a, b);
  return mf;
}

export function prefers(mf) {
  return new Map(mf.multiFn.preferTable);
}
