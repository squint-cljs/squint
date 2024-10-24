import * as squint_core from './core.js';

class Html {
  constructor(s) {
    // if (typeof(s) !== 'string')
    //   throw Error(`Object not a string: ${s.constructor}`);
    this.s = s;
  }
  toString() {
    return this.s.toString();
  }
}

export function html([s]) {
  return new Html(s);
}

function escapeHTML(text) {
  return text.toString()
    .replace("&",  "&amp;")
    .replace("<",  "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;");
}

function safe(x) {
  if (x instanceof Html) return x;
  if (squint_core.string_QMARK_(x)) {
    return escapeHTML(x);
  }
  return escapeHTML(x.toString());
}

export function css(v, props) {
  v = Object.assign(props, v);
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

function attr(v) {
  if (typeof(v) === 'object') {
    return css({}, v);
  } else
  {
    return v;
  }
}

function toHTML(v) {
  // console.log('v', v);
  if (v == null) return;
  if (v instanceof Html) return v;
  if (typeof(v) === 'string') return safe(v);
  if (v[Symbol.iterator]) {
    return [...v].map(toHTML).join("");
  }
  return safe(v.toString());
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
  return new Html(ret);
}

export function tag(strs, ...vals) {
  let out = strs[0];
  for (let i = 0; i < vals.length; i++) {
    out += toHTML(vals[i]);
    out += strs[i+1];
  }
  return new Html(out);
}
