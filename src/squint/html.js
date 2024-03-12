function toHTML(v) {
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