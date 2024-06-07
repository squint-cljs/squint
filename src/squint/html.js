import * as squint_core from './core.js';

function escapeHTML(text) {
  return text.toString()
    .replace("&",  "&amp;")
    .replace("<",  "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;");
}

export function _safe(x) {
  if (squint_core.string_QMARK_(x)) {
    return escapeHTML(x);
  }
  return x;
}

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

export function attr(v) {
  if (typeof(v) === 'object') {
    return css(v);
  } else
  {
    return v;
  }
}

export function attrs(v, props) {
  v = Object.assign(props, v);
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
    const v1 = attr(kv[1]);
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
