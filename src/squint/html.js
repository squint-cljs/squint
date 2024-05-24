function css(v) {
  let ret = "";
  if (v == null) return ret;
  let first = true;
  for (const kv of Object.entries(v)) {
    if (!first) ret += ' ';
    ret += kv[0];
    ret += ":";
    ret += kv[1];
    ret += ';';
    first = false;
  }
  return ret;
}

export function attrs(v) {
  let ret = "";
  if (v == null) return ret;
  let first = true;
  for (const kv of Object.entries(v)) {
    if (!first) {
      ret += ' ';
    }
    ret += kv[0];
    ret += "=";
    ret += '"';
    const v1 = kv[1];
    if (typeof(v1) === 'object') {
      ret += css(v1);
    } else
      ret += v1;
    ret += '"';
    first = false;
  }
  return ret;
}

function toHTML(v) {
  if (v == null) return;
  if (typeof(v) === 'string') return v;
  if (v[Symbol.iterator]) {
    return [...v].join("");
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
