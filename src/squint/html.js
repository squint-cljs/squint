function toHTML(v) {
  if (v == null) return;
  if (typeof(v) === 'string') return v;
  if (v[Symbol.iterator]) {
    return [...v].join("");
  }
  if (typeof v === 'object') {
    let ret = "";
    for (const kv of Object.entries(v)) {
      ret += kv[0];
      ret += "=";
      ret += '"' + kv[1] + '"';
      return ret;
    }
  }
  return v;
}

export function tag(strs, ...vals) {
  let out = strs[0];
  for (let i = 0; i < vals.length; i++) {
    out += toHTML(vals[i]);
    out += strs[i+1];
  }
  return out;
}
