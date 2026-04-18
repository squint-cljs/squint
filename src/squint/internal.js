// Private helpers shared between squint runtime modules. Not public API —
// shape and semantics may change without notice. Consumers outside the
// squint npm package should not depend on this file.

import { get } from './core.js';

export function toFn(x) {
  if (x == null) return x;
  if (x instanceof Function) return x;
  const t = typeof x;
  if (t === 'string') return (coll, d) => get(coll, x, d);
  if (t === 'object') return (k, d) => get(x, k, d);
  return x;
}
