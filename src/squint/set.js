import * as core from './core.js';

function _bubble_max_key(k, coll) {
  const max = core.max_key(k, ...coll);
  return [max, ...coll.filter(x => !core.identical_QMARK_(x, max))];
}

export const __testing__bubble_max_key = _bubble_max_key;

function _intersection2(x, y) {
  if (x.size > y.size) {
    const tmp = y;
    y = x;
    x = tmp;
  }
  const res = new Set();
  for (const elem of x) {
    if (y.has(elem)) {
      res.add(elem);
    }
  }
  return res;
}

export function intersection(...xs) {
  switch (xs.length) {
    case 0: return null;
    case 1: return xs[0];
    case 2: return xs[0].length > xs[1].length ? 
      _intersection2(xs[0], xs[1]) :
      _intersection2(xs[1], xs[0]);
    default: return _bubble_max_key(core.count, xs).reduce(_intersection2);
  }
}

function _difference2(x, y) {
  const res = new Set();
  for (const elem of x) {
    if (!y.has(elem)) {
      res.add(elem);
    }
  }
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
  const res = new Set(x);
  for (const elem of y) {
    res.add(elem);
  }
  return res;
}

export function union(...xs) {
  switch (xs.length) {
    case 0: return null;
    case 1: return xs[0];
    case 2: return xs[0].length > xs[1].length ? 
      _union2(xs[0], xs[1]) : 
      _union2(xs[1], xs[0]);
    default: return _bubble_max_key(core.count, xs).reduce(_union2);
  }
}

function _subset_QMARK_2(x, y) {
  for (const elem of x) {
    if (!y.has(elem)) {
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
  if (x.size > y.size) {
    return false;
  }
  return _subset_QMARK_2(x, y);
}

function _superset_QMARK_2(x, y) {
  for (const elem of x) {
    if (!y.has(elem)) {
      return false;
    }
  }
  return true;
}

export function superset_QMARK_(x, y) {
  if (x === undefined) {
    return true;
  }
  if (y === undefined) {
    return true;
  }
  if (x.size < y.size) {
    return false;
  }
  return _superset_QMARK_2(y, x);
}

export function select(pred, xset) {
  if (xset === undefined) {
    return null;
  }
  const res = new Set();
  for (const elem of xset) {
    if (core.truth_(pred(elem))) {
      res.add(elem);
    }
  }
  return res;
}

export function rename_keys(map, kmap) {
  const ks = core.keys(kmap);
  return ks.reduce((m, k) => {
    const newKey = core.get(kmap, k);
    if (core.contains_QMARK_(map, k)) {
      return core.assoc(m, newKey, core.get(map, k));
    }
    return m;
  }, core.dissoc(map, ...ks));
}

export function rename(xrel, kmap) {
  return core.set(core.map(x => rename_keys(x, kmap), xrel));
}

export function project(xrel, ...ks) {
  return core.set(core.map(x => core.select_keys(x, ...ks), xrel));
}

export function map_invert(xmap) {
  if (xmap === undefined) {
    return {};
  }
  return core.reduce_kv((m, k, v) => core.assoc_BANG_(m, v, k), core.empty(xmap), xmap);
}

export function join(xrel, yrel, kmap) {
  if (kmap === undefined) {  // natural join
    if (core.seq(xrel) && core.seq(yrel)) {
      const ks = intersection(core.set(core.keys(core.first(xrel))), core.set(core.keys(core.first(yrel))));
      const [r, s] = core.count(xrel) <= core.count(yrel) ? [xrel, yrel] : [yrel, xrel];
      const idx = core.group_by(core.juxt(...ks), r);
      return core.reduce((ret, x) => {
        const found = core.get(idx, core.juxt(...ks)(x));
        return found ? core.reduce((acc, y) => core.conj(acc, core.merge(y, x)), ret, found) : ret;
      }, new Set(), s);
    } else {
      return new Set();
    }
  } else { // arbitrary key mapping
    const [r, s, k] = core.count(xrel) <= core.count(yrel) ? [xrel, yrel, map_invert(kmap)] : [yrel, xrel, kmap];
    const idx = core.group_by(core.juxt(...core.vals(k)), r);
    return core.reduce((ret, x) => {
      const found = core.get(idx, core.juxt(...core.keys(k))(x));
      return found ? core.reduce((acc, y) => core.conj(acc, core.merge(y, x)), ret, found) : ret;
    }, new Set(), s);
  }
}