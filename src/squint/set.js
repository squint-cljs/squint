import * as core from './core.js';
import {
  ICounted__count,
  IAssociative__contains_key_QMARK_,
  ICollection__conj,
  ISet__disjoin,
  IEditableCollection__as_transient,
  ITransientCollection__conj_BANG_,
  ITransientCollection__persistent_BANG_,
  ITransientSet__disjoin_BANG_,
} from './core.js';

function _bubble_max_key(k, coll) {
  const max = core.max_key(k, ...coll);
  return [max, ...coll.filter(x => x !== max)];
}

// a js/Set or set-like with reference .has/.add (SortedSet); a persistent
// set from squint.immutable dispatches through core instead
function jsSetLike(x) {
  return x instanceof Set || (x != null && typeof x.has === 'function' && typeof x.add === 'function');
}

// the persistent paths dispatch through the slot symbols directly: the
// symbols are tiny consts, where the core wrapper fns (count, conj!, ...)
// drag their whole dispatch chains into js-Set-only bundles
function setSize(x) {
  return typeof x.size === 'number' ? x.size : x[ICounted__count](x);
}

function setHas(x, e) {
  // contains? resolves keyword/string representations, consistent with =
  return jsSetLike(x) ? core.contains_QMARK_(x, e) : x[IAssociative__contains_key_QMARK_](x, e);
}

// fold elements into a copy of target, preserving its type: js/Set mutates a
// copy, a protocol set goes through its transient when it has one and folds
// persistent -conj otherwise (a set type need not implement transients)
function addAll(target, elems) {
  if (jsSetLike(target)) {
    const res = new Set(target);
    for (const e of elems) res.add(e);
    return res;
  }
  const es = core.iterable(elems);
  if (target[IEditableCollection__as_transient] !== undefined) {
    let t = target[IEditableCollection__as_transient](target);
    for (const e of es) t = t[ITransientCollection__conj_BANG_](t, e);
    return t[ITransientCollection__persistent_BANG_](t);
  }
  let res = target;
  for (const e of es) res = res[ICollection__conj](res, e);
  return res;
}

// remove each elem for which pred is true, preserving the set's type
function removeWhere(xset, pred) {
  const es = core.iterable(xset);
  if (xset[IEditableCollection__as_transient] !== undefined) {
    let t = xset[IEditableCollection__as_transient](xset);
    for (const e of es) {
      if (pred(e)) t = t[ITransientSet__disjoin_BANG_](t, e);
    }
    return t[ITransientCollection__persistent_BANG_](t);
  }
  let res = xset;
  for (const e of es) {
    if (pred(e)) res = res[ISet__disjoin](res, e);
  }
  return res;
}

function _intersection2(x, y) {
  if (setSize(x) > setSize(y)) {
    const tmp = y;
    y = x;
    x = tmp;
  }
  if (jsSetLike(x)) {
    const res = new Set();
    for (const elem of x) {
      if (setHas(y, elem)) {
        res.add(elem);
      }
    }
    return res;
  }
  // protocol set: disj the non-members, keeping the type and sharing
  return removeWhere(x, (elem) => !setHas(y, elem));
}

export function intersection(...xs) {
  switch (xs.length) {
    case 0: return null;
    case 1: return xs[0];
    case 2: return _intersection2(xs[0], xs[1]);
    default: return _bubble_max_key((x) => 0 - setSize(x), xs).reduce(_intersection2);
  }
}

function _difference2(x, y) {
  if (jsSetLike(x)) {
    const res = new Set();
    for (const elem of x) {
      if (!setHas(y, elem)) {
        res.add(elem);
      }
    }
    return res;
  }
  const ys = core.iterable(y);
  if (x[IEditableCollection__as_transient] !== undefined) {
    let t = x[IEditableCollection__as_transient](x);
    for (const elem of ys) t = t[ITransientSet__disjoin_BANG_](t, elem);
    return t[ITransientCollection__persistent_BANG_](t);
  }
  let res = x;
  for (const elem of ys) res = res[ISet__disjoin](res, elem);
  return res;
}

export function difference(...xs) {
  switch (xs.length) {
    case 0: return null;
    case 1: return xs[0];
    case 2: return _difference2(xs[0], xs[1]);
    default: return xs.reduce(_difference2);
  }
}

function _union2(x, y) {
  if (setSize(x) < setSize(y)) {
    const tmp = y;
    y = x;
    x = tmp;
  }
  return addAll(x, y);
}

export function union(...xs) {
  switch (xs.length) {
    case 0: return null;
    case 1: return xs[0];
    case 2: return _union2(xs[0], xs[1]);
    default: return _bubble_max_key((x) => setSize(x), xs).reduce(_union2);
  }
}

function _subset_QMARK_2(x, y) {
  for (const elem of core.iterable(x)) {
    if (!setHas(y, elem)) {
      return false;
    }
  }
  return true;
}

export function subset_QMARK_(x, y) {
  if (x === undefined) {
    return true;
  }
  if (y === undefined) {
    return false;
  }
  if (setSize(x) > setSize(y)) {
    return false;
  }
  return _subset_QMARK_2(x, y);
}

export function superset_QMARK_(x, y) {
  if (x === undefined) {
    return true;
  }
  if (y === undefined) {
    return true;
  }
  if (setSize(x) < setSize(y)) {
    return false;
  }
  return _subset_QMARK_2(y, x);
}

export function select(pred, xset) {
  if (xset === undefined) {
    return null;
  }
  if (jsSetLike(xset)) {
    const res = new Set();
    for (const elem of xset) {
      if (core.truth_(pred(elem))) {
        res.add(elem);
      }
    }
    return res;
  }
  return removeWhere(xset, (elem) => !core.truth_(pred(elem)));
}

// true when m is an immutable slot-map: mutate-in-place helpers would corrupt it
function slotMap(m) {
  return m != null && m[core.IAssociative__assoc] !== undefined;
}

export function rename_keys(map, kmap) {
  const ks = core.keys(kmap);
  let without = core.dissoc(map, ...ks);
  if (slotMap(without)) {
    return ks.reduce((m, k) => {
      if (core.contains_QMARK_(map, k)) {
        return core.assoc(m, core.get(kmap, k), core.get(map, k));
      }
      return m;
    }, without);
  }
  if (without === map) {
    without = {...map};
  }
  return ks.reduce((m, k) => {
    const newKey = core.get(kmap, k);
    if (core.contains_QMARK_(map, k)) {
      return core.assoc_BANG_(m, newKey, core.get(map, k));
    }
    return m;
  }, without);
}

export function rename(xrel, kmap) {
  return into_set_like(xrel, core.map(x => rename_keys(x, kmap), xrel));
}

export function project(xrel, ...ks) {
  return into_set_like(xrel, core.map(x => core.select_keys(x, ...ks), xrel));
}

// an empty set of the same kind as rel (a persistent set stays persistent),
// filled from elems
function into_set_like(rel, elems) {
  const empty = rel != null && !jsSetLike(rel) && rel[ISet__disjoin] !== undefined
    ? core.empty(rel)
    : new Set();
  return addAll(empty, elems);
}

export function map_invert(xmap) {
  if (xmap === undefined) {
    return {};
  }
  const empty = core.empty(xmap);
  if (slotMap(empty)) {
    return core.reduce_kv((m, k, v) => core.assoc(m, v, k), empty, xmap);
  }
  return core.reduce_kv((m, k, v) => core.assoc_BANG_(m, v, k), empty, xmap);
}

export function join(xrel, yrel, kmap) {
  if (kmap === undefined) {  // natural join
    if (core.seq(xrel) && core.seq(yrel)) {
      const ks = intersection(core.set(core.keys(core.first(xrel))), core.set(core.keys(core.first(yrel))));
      const [r, s] = core.count(xrel) <= core.count(yrel) ? [xrel, yrel] : [yrel, xrel];
      const select = core.juxt(...ks);
      const idx = core.group_by(select, r);
      return core.reduce((ret, x) => {
        const found = core.get(idx, select(x));
        return found ? core.reduce((acc, y) => acc.add(core.merge(y, x)), ret, found) : ret;
      }, new Set(), s);
    } else {
      return new Set();
    }
  } else { // arbitrary key mapping
    const [r, s, k] = core.count(xrel) <= core.count(yrel) ? [xrel, yrel, map_invert(kmap)] : [yrel, xrel, kmap];
    const idx = core.group_by(core.juxt(...core.vals(k)), r);
    const select = core.juxt(...core.keys(k));
    return core.reduce((ret, x) => {
      const found = core.get(idx, select(x));
      return found ? core.reduce((acc, y) => acc.add(core.merge(y, x)), ret, found) : ret;
    }, new Set(), s);
  }
}
