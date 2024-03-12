export function tag(strs, ...vals) {
  let out = strs[0];
  for (let i = 0; i < vals.length; i++) {
    out += vals[i];
    out += strs[i+1];
  }
  console.log(out);
  return out;  
}