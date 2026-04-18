// Multimethod runtime for squint. Opt-in: only imported when defmulti/
// defmethod (or related ops) appear in user code. Keeping this out of
// core.js means programs that don't use multimethods pay zero bundle cost.

import { _EQ_ } from 'squint-cljs/core.js';
import { toFn } from './internal.js';

function addRel(m, k, v) {
  let s = m.get(k);
  if (!s) { s = new Set(); m.set(k, s); }
  s.add(v);
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
  const anc = h.ancestors.get(child);
  if (anc && anc.has(parent)) return true;
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
  const tagAnc = h.ancestors.get(tag);
  if (tagAnc && tagAnc.has(parent)) return;
  if (_isa(h, parent, tag)) {
    throw new Error(`Cyclic derivation: ${String(parent)} has ${String(tag)} as ancestor`);
  }
  addRel(h.parents, tag, parent);
  const collectAncestors = (t) => {
    const acc = new Set([t]);
    const ta = h.ancestors.get(t);
    if (ta) for (const a of ta) acc.add(a);
    return acc;
  };
  const parentChain = collectAncestors(parent);
  const tagChain = collectAncestors(tag);
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
  if (c === undefined) { _deriveInto(gh(), a, b); return null; }
  const next = {
    parents: new Map(a.parents),
    ancestors: new Map(a.ancestors),
    descendants: new Map(a.descendants),
  };
  _deriveInto(next, b, c);
  return next;
}

function rebuildFromPairs(pairs) {
  const h = make_hierarchy();
  for (const [c, p] of pairs) _deriveInto(h, c, p);
  return h;
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

export function parents(x, h) {
  h = h ?? gh();
  const s = h.parents.get(x);
  return s && s.size ? new Set(s) : null;
}
export function ancestors(x, h) {
  h = h ?? gh();
  const s = h.ancestors.get(x);
  return s && s.size ? new Set(s) : null;
}
export function descendants(x, h) {
  h = h ?? gh();
  const s = h.descendants.get(x);
  return s && s.size ? new Set(s) : null;
}

function _prefers(prefer, a, b) {
  const s = prefer.get(a);
  if (s && s.has(b)) return true;
  if (s) for (const x of s) if (_prefers(prefer, x, b)) return true;
  return false;
}

function dominates(h, prefer, a, b) {
  if (_prefers(prefer, a, b)) return true;
  if (!_EQ_(a, b) && _isa(h, a, b)) return true;
  return false;
}

function isPrimitive(x) {
  const t = typeof x;
  return x == null || t === 'string' || t === 'number' || t === 'boolean' || t === 'bigint' || t === 'symbol';
}
function findKeyByEquiv(m, v) {
  if (isPrimitive(v) && m.has(v)) return v;
  for (const k of m.keys()) if (_EQ_(k, v)) return k;
  return undefined;
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
    if (isPrimitive(val)) {
      const cached = this.methodCache.get(val);
      if (cached !== undefined) return cached;
    }
    const exactKey = findKeyByEquiv(this.methodTable, val);
    if (exactKey !== undefined) {
      const fn = this.methodTable.get(exactKey);
      if (isPrimitive(val)) this.methodCache.set(val, fn);
      return fn;
    }
    const best = this.findBest(val);
    if (best) {
      if (isPrimitive(val)) this.methodCache.set(val, best[1]);
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
  const hierarchy = opts.hierarchy || { deref: gh };
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
