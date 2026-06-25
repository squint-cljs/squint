import * as core from './core.js';

const DISCARD = Symbol('discard');
const EOF = Symbol('eof');

class Reader {
  constructor(s) {
    this.s = s;
    this.idx = 0;
  }
  peek() {
    return this.s[this.idx];
  }
  read() {
    return this.s[this.idx++];
  }
  eof() {
    return this.idx >= this.s.length;
  }
}

function whitespace(c) {
  return c === ' ' || c === '\t' || c === '\n' || c === '\r' || c === '\f' || c === ',';
}

function macroTerminating(c) {
  return c === undefined || c === '"' || c === ';' || c === '@' || c === '^' ||
    c === '`' || c === '~' || c === '(' || c === ')' || c === '[' || c === ']' ||
    c === '{' || c === '}' || c === '\\';
}

function terminating(c) {
  return whitespace(c) || macroTerminating(c);
}

function skipWhitespace(rdr) {
  while (!rdr.eof()) {
    const c = rdr.peek();
    if (whitespace(c)) {
      rdr.idx++;
    } else if (c === ';') {
      while (!rdr.eof() && rdr.peek() !== '\n') rdr.idx++;
    } else {
      break;
    }
  }
}

function readToken(rdr) {
  const start = rdr.idx;
  while (!rdr.eof() && !terminating(rdr.peek())) rdr.idx++;
  return rdr.s.slice(start, rdr.idx);
}

// int-pattern carries a trailing `0\d+` poison branch (no capture group) so
// leading-zero non-octals like 08 match here, yield no digits below, and are
// rejected as invalid before the float pattern can accept them
const intRe = /^([+-]?)(?:(0)|([1-9]\d*)|0[xX]([0-9a-fA-F]+)|0([0-7]+)|([1-9]\d?)[rR]([0-9a-zA-Z]+)|0\d+)(N)?$/;
const floatRe = /^([+-]?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)(M)?$/;

// the radix-digit class is wider than the radix, so validate each digit
function intFromRadix(s, radix) {
  let n = 0;
  for (let i = 0; i < s.length; i++) {
    const d = parseInt(s[i], radix);
    if (Number.isNaN(d)) return undefined;
    n = n * radix + d;
  }
  return n;
}

// numbers are plain JS numbers; like CLJS the N and M suffixes are ignored
function parseNumber(token) {
  const m = intRe.exec(token);
  if (m) {
    const neg = m[1] === '-';
    // radix digits range wider than the radix, so they never take N
    if (m[7] !== undefined) {
      const n = intFromRadix(m[7], parseInt(m[6], 10));
      return n === undefined ? undefined : (neg ? -n : n);
    }
    let digits, radix;
    if (m[2] !== undefined) { digits = '0'; radix = 10; }
    else if (m[3] !== undefined) { digits = m[3]; radix = 10; }
    else if (m[4] !== undefined) { digits = m[4]; radix = 16; }
    else if (m[5] !== undefined) { digits = m[5]; radix = 8; }
    else return undefined; // poison branch: leading-zero non-octal
    const n = parseInt(digits, radix);
    return neg ? -n : n;
  }
  // int matched above wins, so the float pattern never sees a leading-zero int
  const f = floatRe.exec(token);
  if (f) return parseFloat(f[1]);
  return undefined;
}

function numberLiteral(c, c2) {
  return (c >= '0' && c <= '9') || ((c === '+' || c === '-') && c2 >= '0' && c2 <= '9');
}

function parseToken(rdr) {
  const token = readToken(rdr);
  if (token === 'nil') return null;
  if (token === 'true') return true;
  if (token === 'false') return false;
  if (numberLiteral(token[0], token[1])) {
    const n = parseNumber(token);
    if (n === undefined) throw new Error('Invalid number: ' + token);
    return n;
  }
  return token;
}

function parseKeyword(rdr) {
  rdr.idx++;
  const token = readToken(rdr);
  if (token === '') throw new Error('Invalid token: :');
  // ::foo auto-resolved keywords are not valid edn
  if (token[0] === ':') throw new Error('Invalid token: :' + token);
  return token;
}

const stringEscapes = { t: '\t', r: '\r', n: '\n', '\\': '\\', '"': '"', b: '\b', f: '\f' };

function parseString(rdr) {
  rdr.idx++;
  let out = '';
  let runStart = rdr.idx;
  for (;;) {
    if (rdr.eof()) throw new Error('EOF while reading string');
    const c = rdr.read();
    if (c === '"') return out + rdr.s.slice(runStart, rdr.idx - 1);
    if (c === '\\') {
      out += rdr.s.slice(runStart, rdr.idx - 1);
      const e = rdr.read();
      if (e === 'u') {
        out += String.fromCharCode(parseInt(rdr.s.substr(rdr.idx, 4), 16));
        rdr.idx += 4;
      } else if (e in stringEscapes) {
        out += stringEscapes[e];
      } else {
        throw new Error('Unsupported escape character: \\' + e);
      }
      runStart = rdr.idx;
    }
  }
}

const symbolicValues = { Inf: Infinity, '-Inf': -Infinity, NaN: NaN };

const namedChars = { newline: '\n', space: ' ', tab: '\t', return: '\r', backspace: '\b', formfeed: '\f' };

function parseChar(rdr) {
  rdr.idx++;
  if (rdr.eof()) throw new Error('EOF while reading character');
  const start = rdr.idx;
  rdr.idx++; // first char is taken literally, even if a terminator
  while (!rdr.eof() && !terminating(rdr.peek())) rdr.idx++;
  const token = rdr.s.slice(start, rdr.idx);
  if (token.length === 1) return token;
  if (token in namedChars) return namedChars[token];
  if (token[0] === 'u') return String.fromCharCode(parseInt(token.slice(1), 16));
  if (token[0] === 'o') return String.fromCharCode(parseInt(token.slice(1), 8));
  throw new Error('Unsupported character: \\' + token);
}

function parseToDelimiter(rdr, opts, delimiter) {
  rdr.idx++;
  const elements = [];
  for (;;) {
    skipWhitespace(rdr);
    if (rdr.eof()) throw new Error('EOF while reading, expected ' + delimiter);
    if (rdr.peek() === delimiter) {
      rdr.idx++;
      return elements;
    }
    const val = parseNext(rdr, opts);
    if (val !== DISCARD) elements.push(val);
  }
}

function duplicates(coll) {
  const dups = [];
  for (let i = 0; i < coll.length; i++) {
    for (let j = 0; j < i; j++) {
      if (core._EQ_(coll[i], coll[j])) {
        dups.push(coll[i]);
        break;
      }
    }
  }
  return dups;
}

function duplicateKeyError(kind, dups) {
  return kind + ' literal contains duplicate key' + (dups.length > 1 ? 's' : '') +
    ': ' + dups.map((x) => core.pr_str(x)).join(', ');
}

function parseSet(rdr, opts) {
  const elements = parseToDelimiter(rdr, opts, '}');
  const dups = duplicates(elements);
  if (dups.length) throw new Error(duplicateKeyError('Set', dups));
  return new Set(elements);
}

function qualifyKey(ns, k) {
  if (typeof k !== 'string') return k;
  const i = k.indexOf('/');
  if (i === -1) return ns + '/' + k;
  if (k.slice(0, i) === '_') return k.slice(i + 1);
  return k;
}

function buildMap(elements, ns) {
  if (elements.length % 2 !== 0) {
    throw new Error('The map literal starting with ' + core.pr_str(elements[0]) + ' contains ' + elements.length +
      ' form(s). Map literals must contain an even number of forms.');
  }
  const ks = elements.filter((_, i) => i % 2 === 0);
  const dups = duplicates(ks);
  if (dups.length) throw new Error(duplicateKeyError('Map', dups));
  const obj = {};
  for (let i = 0; i < elements.length; i += 2) {
    obj[ns ? qualifyKey(ns, elements[i]) : elements[i]] = elements[i + 1];
  }
  return obj;
}

function parseMap(rdr, opts) {
  return buildMap(parseToDelimiter(rdr, opts, '}'), null);
}

function parseMeta(rdr, opts) {
  rdr.idx++;
  skipWhitespace(rdr);
  const c = rdr.peek();
  let m;
  if (c === '{') {
    m = parseMap(rdr, opts);
  } else if (c === ':') {
    m = {};
    m[parseKeyword(rdr)] = true;
  } else {
    // a symbol or string tags the value
    m = { tag: parseNext(rdr, opts) };
  }
  skipWhitespace(rdr);
  const form = parseNext(rdr, opts);
  if (form === null || typeof form !== 'object') {
    throw new Error('Metadata can only be applied to IMetas');
  }
  // stacked metadata merges, with the outer map winning
  return core.with_meta(form, Object.assign({}, core.meta(form), m));
}

function parseNamespacedMap(rdr, opts) {
  const token = readToken(rdr);
  if (token === ':' || token[1] === ':') throw new Error('Invalid token: ' + token);
  skipWhitespace(rdr);
  if (rdr.peek() !== '{') throw new Error('Namespaced map must specify a map');
  return buildMap(parseToDelimiter(rdr, opts, '}'), token.slice(1));
}

const defaultReaders = {
  inst: (val) => new Date(val),
  uuid: (val) => val,
};

function parseTagged(rdr, opts) {
  const tag = readToken(rdr);
  skipWhitespace(rdr);
  const val = parseNext(rdr, opts);
  const readers = opts && opts.readers;
  if (readers && readers[tag]) return readers[tag](val);
  if (defaultReaders[tag]) return defaultReaders[tag](val);
  if (opts && opts.default) return opts.default(tag, val);
  throw new Error('No reader function for tag ' + tag);
}

function parseDispatch(rdr, opts) {
  rdr.idx++;
  const c = rdr.peek();
  if (c === '_') {
    rdr.idx++;
    // discard one real form; skip nested discards so #_ #_ a b drops both
    let v = parseNext(rdr, opts);
    while (v === DISCARD) v = parseNext(rdr, opts);
    return DISCARD;
  }
  if (c === '!') {
    while (!rdr.eof() && rdr.peek() !== '\n') rdr.idx++;
    return DISCARD;
  }
  if (c === '{') return parseSet(rdr, opts);
  if (c === ':') return parseNamespacedMap(rdr, opts);
  if (c === '#') {
    rdr.idx++;
    const token = readToken(rdr);
    if (token in symbolicValues) return symbolicValues[token];
    throw new Error('Invalid token: ##' + token);
  }
  return parseTagged(rdr, opts);
}

function parseNext(rdr, opts) {
  skipWhitespace(rdr);
  if (rdr.eof()) return EOF;
  const c = rdr.peek();
  switch (c) {
    case '(': return core.list(...parseToDelimiter(rdr, opts, ')'));
    case '[': return parseToDelimiter(rdr, opts, ']');
    case '{': return parseMap(rdr, opts);
    case ')': case ']': case '}': throw new Error('Unmatched delimiter: ' + c);
    case '"': return parseString(rdr);
    case '\\': return parseChar(rdr);
    case ':': return parseKeyword(rdr);
    case '#': return parseDispatch(rdr, opts);
    case "'": rdr.idx++; return core.list('quote', parseNext(rdr, opts));
    case '^': return parseMeta(rdr, opts);
    case '@': case '`': case '~': throw new Error('Invalid leading character: ' + c);
    default: return parseToken(rdr);
  }
}

export function read_string(a, b) {
  let opts, s;
  if (b === undefined) {
    opts = null;
    s = a;
  } else {
    opts = a;
    s = b;
  }
  if (s == null || s === '') return opts && 'eof' in opts ? opts.eof : null;
  const rdr = new Reader(s);
  let val = parseNext(rdr, opts);
  while (val === DISCARD) val = parseNext(rdr, opts);
  if (val === EOF) return opts && 'eof' in opts ? opts.eof : null;
  return val;
}
