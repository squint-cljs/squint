import * as core from "./core.js";
export const E = Math.E;
export const PI = Math.PI;
export const DEGREES_TO_RADIANS = 0.017453292519943295;
export const RADIANS_TO_DEGREES = 57.29577951308232;
export const TWO_TO_THE_52 = 4503599627370496;
export const SIGNIFICAND_WIDTH32 = 21;
export const EXP_BIAS = 1023;
export const EXP_BITMASK32 = 2146435072;
export const EXP_MAX = 1023;
export const EXP_MIN = -1022;
export function get_little_endian() {
  var a = new ArrayBuffer(4);
  var i = new Uint32Array(a);
  var b = new Uint8Array(a);
  i[0] = 857870592;
  return b[0] === 0;
}
export const little_endian_QMARK_ = get_little_endian();
export const HI = little_endian_QMARK_ ? 1 : 0;
export const LO = 1 - HI;
export const INT32_MASK = 4294967295;
export const INT32_NON_SIGN_BIT = 2147483648;
export const INT32_NON_SIGN_BITS = 2147483647;
export function u_LT_(a, b) {
  var ab = a >>> 28;
  var bb = b >>> 28;
  return ab < bb || ab === bb && (a & 268435455) < (b & 268435455);
}
export function sin(a) {
  return Math.sin(a);
}
export function cos(a) {
  return Math.cos(a);
}
export function tan(a) {
  return Math.tan(a);
}
export function asin(a) {
  return Math.asin(a);
}
export function acos(a) {
  return Math.acos(a);
}
export function atan(a) {
  return Math.atan(a);
}
export function to_radians(deg) {
  return deg * 0.017453292519943295;
}
export function to_degrees(r) {
  return r * 57.29577951308232;
}
export function exp(a) {
  return Math.exp(a);
}
export function log(a) {
  return Math.log(a);
}
export function log10(a) {
  return Math.log10(a);
}
export function sqrt(a) {
  return Math.sqrt(a);
}
export function cbrt(a) {
  return Math.cbrt(a);
}
export function fabs(x) {
  var a = new ArrayBuffer(8);
  var d = new Float64Array(a);
  var i = new Uint32Array(a);
  var hi = little_endian_QMARK_ ? 1 : 0;
  d[0] = x;
  i[hi] = i[hi] & 2147483647;
  return d[0];
}
export const Zero = function() {
  var a = new ArrayBuffer(16);
  var d = new Float64Array(a);
  var b = new Uint8Array(a);
  d[0] = 0;
  d[1] = 0;
  b[little_endian_QMARK_ ? 15 : 8] = -128;
  return d;
}();
export const xpos = 0;
export const ypos = 1;
export const HI_x = 2 * 0 + HI;
export const LO_x = 2 * 0 + LO;
export const HI_y = 2 * 1 + HI;
export const LO_y = 2 * 1 + LO;
export function ilogb(hx, lx) {
  if (hx < 1048576) {
    var hx_zero_QMARK_ = hx === 0;
    var start_ix = hx_zero_QMARK_ ? -1043 : -1022;
    var start_i = hx_zero_QMARK_ ? lx : hx << 11;
    var ix = start_ix;
    var i = start_i;
    while (true) {
      if (!(i > 0)) {
        return ix;
      } else {
        var G__50301 = ix - 1;
        var G__50302 = i << 1;
        ix = G__50301;
        i = G__50302;
        continue;
      }
      break;
    }
  } else {
    return (hx >> 20) - 1023;
  }
}
export function setup_hl(i, h, l) {
  if (i >= -1022) {
    return [1048576 | 1048575 & h, l];
  } else {
    var n = -1022 - i;
    if (n <= 31) {
      return [h << n | l >>> 32 - n, l << n];
    } else {
      return [l << n - 32, 0];
    }
  }
}
export function IEEE_fmod(x, y) {
  if (y === 0 || (isNaN(y) || !isFinite(x))) {
    return NaN;
  } else {
    var a = new ArrayBuffer(16);
    var d = new Float64Array(a);
    var i = new Uint32Array(a);
    var _ = d[0] = x;
    var ___$1 = d[1] = y;
    var hx = i[HI_x];
    var lx = i[LO_x];
    var hy = i[HI_y];
    var ly = i[LO_y];
    var sx = hx & 2147483648;
    var hx__$1 = hx & 2147483647;
    var hy__$1 = hy & 2147483647;
    var hx_LT__EQ_hy = hx__$1 <= hy__$1;
    if (hx_LT__EQ_hy && (hx__$1 < hy__$1 || lx < ly)) {
      return x;
    } else {
      if (hx_LT__EQ_hy && lx === ly) {
        return Zero[sx >>> 31];
      } else {
        try {
          var ix = ilogb(hx__$1, lx);
          var iy = ilogb(hy__$1, ly);
          const [hx__$2, lx__$1] = setup_hl(ix, hx__$1, lx);
          const [hy__$2, ly__$1] = setup_hl(iy, hy__$1, ly);
          const [hx__$3, lx__$2] = function() {
            var n2 = ix - iy;
            var hx__$32 = hx__$2;
            var lx__$22 = lx__$1;
            while (true) {
              if (n2 === 0) {
                return [hx__$32, lx__$22];
              } else {
                var hz2 = u_LT_(lx__$22, ly__$1) ? hx__$32 - hy__$2 - 1 : hx__$32 - hy__$2;
                var lz2 = lx__$22 - ly__$1;
                const [hx__$42, lx__$32] = hz2 < 0 ? [hx__$32 + hx__$32 + (lx__$22 >>> 31), lx__$22 + lx__$22] : (hz2 | lz2) === 0 ? function() {
                  throw new Error("Signed zero");
                }() : [hz2 + hz2 + (lz2 >>> 31), lz2 + lz2];
                var G__50303 = n2 - 1;
                var G__50304 = 4294967295 & hx__$42;
                var G__50305 = 4294967295 & lx__$32;
                n2 = G__50303;
                hx__$32 = G__50304;
                lx__$22 = G__50305;
                continue;
              }
              break;
            }
          }();
          var hz = u_LT_(lx__$2, ly__$1) ? hx__$3 - hy__$2 - 1 : hx__$3 - hy__$2;
          var lz = lx__$2 - ly__$1;
          const [hx__$4, lx__$3] = hz >= 0 ? [hz, lz] : [hx__$3, lx__$2];
          if ((hx__$4 | lx__$3) === 0) {
            throw new Error("Signed zero");
          }
          const [hx__$5, lx__$4, iy__$1] = function() {
            var hx__$52 = hx__$4;
            var lx__$42 = lx__$3;
            var iy__$12 = iy;
            while (true) {
              if (!(hx__$52 < 1048576)) {
                return [hx__$52, lx__$42, iy__$12];
              } else {
                var G__50306 = hx__$52 + hx__$52 + (lx__$42 >>> 31);
                var G__50307 = lx__$42 + lx__$42;
                var G__50308 = iy__$12 - 1;
                hx__$52 = G__50306;
                lx__$42 = G__50307;
                iy__$12 = G__50308;
                continue;
              }
              break;
            }
          }();
          if (iy__$1 >= -1022) {
            var hx__$6 = hx__$5 - 1048576 | iy__$1 + 1023 << 20;
            i[HI_x] = hx__$6 | sx;
            i[LO_x] = lx__$4;
            return d[0];
          } else {
            var n = -1022 - iy__$1;
            const [hx__$6, lx__$5] = n <= 20 ? [hx__$5 >> n, lx__$4 >>> n | hx__$5 << 32 - n] : n <= 31 ? [sx, hx__$5 << 32 - n | lx__$4 >>> n] : [sx, hx__$5 >> n - 32];
            i[HI_x] = hx__$6 | sx;
            i[LO_x] = lx__$5;
            return d[0] * 1;
          }
        } catch (e50260) {
          var ___$2 = e50260;
          return Zero[sx >>> 31];
        }
      }
    }
  }
}
export function IEEE_remainder(dividend, divisor) {
  if (divisor === 0) {
    return NaN;
  } else {
    if (isNaN(divisor)) {
      return NaN;
    } else {
      if (isNaN(dividend)) {
        return NaN;
      } else {
        if (!isFinite(dividend)) {
          return NaN;
        } else {
          if (!isFinite(divisor)) {
            return dividend;
          } else {
            var a = new ArrayBuffer(16);
            var d = new Float64Array(a);
            var i = new Uint32Array(a);
            d[0] = dividend;
            d[1] = divisor;
            var hx = i[HI];
            var lx = i[LO];
            var hp = i[HI + 2];
            var lp = i[LO + 2];
            var sx = hx & 2147483648;
            var hp__$1 = hp & 2147483647;
            var hx__$1 = hx & 2147483647;
            var dividend__$1 = hp__$1 <= 2145386495 ? IEEE_fmod(dividend, divisor + divisor) : dividend;
            if ((hx__$1 - hp__$1 | lx - lp) === 0) {
              return 0 * dividend__$1;
            } else {
              var dividend__$2 = Math.abs(dividend__$1);
              var divisor__$1 = Math.abs(divisor);
              var dividend__$3 = hp__$1 < 2097152 ? dividend__$2 + dividend__$2 > divisor__$1 ? function() {
                var dividend__$32 = dividend__$2 - divisor__$1;
                if (dividend__$32 + dividend__$32 >= divisor__$1) {
                  return dividend__$32 - divisor__$1;
                } else {
                  return dividend__$32;
                }
              }() : dividend__$2 : function() {
                var divisor_half = 0.5 * divisor__$1;
                if (dividend__$2 > divisor_half) {
                  var dividend__$32 = dividend__$2 - divisor__$1;
                  if (dividend__$32 >= divisor_half) {
                    return dividend__$32 - divisor__$1;
                  } else {
                    return dividend__$32;
                  }
                } else {
                  return dividend__$2;
                }
              }();
              d[0] = dividend__$3;
              var hx__$2 = i[HI] ^ sx;
              i[HI] = hx__$2;
              return d[0];
            }
          }
        }
      }
    }
  }
}
export function ceil(a) {
  if (!(a == null)) {
    return Math.ceil(a);
  } else {
    throw new Error("Unexpected Null passed to ceil");
  }
}
export function floor(a) {
  if (!(a == null)) {
    return Math.floor(a);
  } else {
    throw new Error("Unexpected Null passed to floor");
  }
}
export function copy_sign(magnitude, sign) {
  var a = new ArrayBuffer(16);
  var d = new Float64Array(a);
  var b = new Uint8Array(a);
  var sbyte = little_endian_QMARK_ ? 7 : 0;
  d[0] = magnitude;
  d[1] = sign;
  var sign_sbyte = 128 & b[8 + sbyte];
  var mag_sbyte = 127 & b[sbyte];
  b[sbyte] = sign_sbyte | mag_sbyte;
  return d[0];
}
export function rint(a) {
  var sign = copy_sign(1, a);
  var a__$1 = Math.abs(a);
  var a__$2 = a__$1 < 4503599627370496 ? 4503599627370496 + a__$1 - 4503599627370496 : a__$1;
  return sign * a__$2;
}
export function atan2(y, x) {
  return Math.atan2(y, x);
}
export function pow(a, b) {
  return Math.pow(a, b);
}
export function round(a) {
  if (isNaN(a)) {
    return 0;
  } else {
    if (isFinite(a)) {
      return Math.round(a);
    } else {
      if (Infinity === a) {
        return Number.MAX_SAFE_INTEGER;
      } else {
        return Number.MIN_SAFE_INTEGER;
      }
    }
  }
}
export function random() {
  return Math.random();
}
export function add_exact(x, y) {
  var r = x + y;
  if (r > Number.MAX_SAFE_INTEGER || r < Number.MIN_SAFE_INTEGER) {
    throw new Error("Integer overflow");
  } else {
    return r;
  }
}
export function subtract_exact(x, y) {
  var r = x - y;
  if (r > Number.MAX_SAFE_INTEGER || r < Number.MIN_SAFE_INTEGER) {
    throw new Error("Integer overflow");
  } else {
    return r;
  }
}
export function multiply_exact(x, y) {
  var r = x * y;
  if (r > Number.MAX_SAFE_INTEGER || r < Number.MIN_SAFE_INTEGER) {
    throw new Error("Integer overflow");
  } else {
    return r;
  }
}
export function increment_exact(a) {
  if (a >= Number.MAX_SAFE_INTEGER || a < Number.MIN_SAFE_INTEGER) {
    throw new Error("Integer overflow");
  } else {
    return a + 1;
  }
}
export function decrement_exact(a) {
  if (a <= Number.MIN_SAFE_INTEGER || a > Number.MAX_SAFE_INTEGER) {
    throw new Error("Integer overflow");
  } else {
    return a - 1;
  }
}
export function negate_exact(a) {
  if (a > Number.MAX_SAFE_INTEGER || a < Number.MIN_SAFE_INTEGER) {
    throw new Error("Integer overflow");
  } else {
    return -a;
  }
}
export function xor(a, b) {
  return a && !b || !a && b;
}
export function floor_div(x, y) {
  if (!(Number.isSafeInteger(x) && Number.isSafeInteger(y))) {
    throw new Error("floor-div called with non-safe-integer arguments");
  } else {
    var r = core.long$(x / y);
    if (xor(x < 0, y < 0) && !(r * y === x)) {
      return r - 1;
    } else {
      return r;
    }
  }
}
export function floor_mod(x, y) {
  if (!(Number.isSafeInteger(x) && Number.isSafeInteger(y))) {
    throw new Error("floor-mod called with non-safe-integer arguments");
  } else {
    var r = core.long$(x / y);
    if (xor(x < 0, y < 0) && !(r * y === x)) {
      return x - y * r - -y;
    } else {
      return x - y * r;
    }
  }
}
export function get_exponent(d) {
  if (isNaN(d) || !isFinite(d)) {
    return EXP_MAX + 1;
  } else {
    if (d === 0) {
      return -1022 - 1;
    } else {
      var a = new ArrayBuffer(8);
      var f = new Float64Array(a);
      var i = new Uint32Array(a);
      var hi = little_endian_QMARK_ ? 1 : 0;
      f[0] = d;
      return ((i[hi] & 2146435072) >> 21 - 1) - 1023;
    }
  }
}
export function hi_lo__GT_double(h, l) {
  var a = new ArrayBuffer(8);
  var f = new Float64Array(a);
  var i = new Uint32Array(a);
  i[LO] = l;
  i[HI] = h;
  return f[0];
}
export function power_of_two(n) {
  if (n >= -1022 && n <= EXP_MAX) {
  } else {
    throw new Error("Assert failed: (and (>= n EXP-MIN) (<= n EXP-MAX))");
  }
  return hi_lo__GT_double(n + 1023 << 21 - 1 & 2146435072, 0);
}
export function ulp(d) {
  if (isNaN(d)) {
    return d;
  } else {
    if (isFinite(d)) {
      var e = get_exponent(d);
      var G__50285 = e;
      switch (G__50285) {
        case 1024:
          return Math.abs(d);
          break;
        case -1023:
          return Number.MIN_VALUE;
          break;
        default:
          var e__$1 = e - (31 + 21);
          if (e__$1 >= -1022) {
            return power_of_two(e__$1);
          } else {
            var shift = e__$1 - (-1022 - 31 - 21);
            if (shift < 32) {
              return hi_lo__GT_double(0, 1 << shift);
            } else {
              return hi_lo__GT_double(1 << shift - 32, 0);
            }
          }
      }
    } else {
      return Infinity;
    }
  }
}
export function signum(d) {
  if (d === 0 || isNaN(d)) {
    return d;
  } else {
    return copy_sign(1, d);
  }
}
export function sinh(x) {
  return Math.sinh(x);
}
export function cosh(x) {
  return Math.cosh(x);
}
export function tanh(x) {
  return Math.tanh(x);
}
export function hypot(x, y) {
  return Math.hypot(x, y);
}
export function expm1(x) {
  return Math.expm1(x);
}
export function log1p(x) {
  return Math.log1p(x);
}
export function add64(hx, lx, hy, ly) {
  var sx = (lx & 2147483648) >>> 31;
  var sy = (ly & 2147483648) >>> 31;
  var lr = (2147483647 & lx) + (2147483647 & ly);
  var c31 = (lr & 2147483648) >>> 31;
  var b31 = sx + sy + c31;
  var lr__$1 = lr & 2147483647 | b31 << 31;
  var c32 = b31 >> 1;
  var hr = 4294967295 & hx + hy + c32;
  return [hr, lr__$1];
}
export function next_after(start, direction) {
  var a = new ArrayBuffer(8);
  var f = new Float64Array(a);
  var i = new Uint32Array(a);
  if (start > direction) {
    if (!(start === 0)) {
      var _ = f[0] = start;
      var ht = i[HI];
      var lt = i[LO];
      const [hr, lr] = (ht & 2147483648) === 0 ? add64(ht, lt, 4294967295, 4294967295) : add64(ht, lt, 0, 1);
      i[HI] = hr;
      i[LO] = lr;
      return f[0];
    } else {
      return -Number.MIN_VALUE;
    }
  } else {
    if (start < direction) {
      var _ = f[0] = start + 0;
      var ht = i[HI];
      var lt = i[LO];
      const [hr, lr] = (ht & 2147483648) === 0 ? add64(ht, lt, 0, 1) : add64(ht, lt, 4294967295, 4294967295);
      i[HI] = hr;
      i[LO] = lr;
      return f[0];
    } else {
      if (start === direction) {
        return direction;
      } else {
        return start + direction;
      }
    }
  }
}
export function next_up(d) {
  if (d < Number.POSITIVE_INFINITY) {
    var a = new ArrayBuffer(8);
    var f = new Float64Array(a);
    var i = new Uint32Array(a);
    var _ = f[0] = d + 0;
    var ht = i[HI];
    var lt = i[LO];
    const [hr, lr] = (ht & 2147483648) === 0 ? add64(ht, lt, 0, 1) : add64(ht, lt, 4294967295, 4294967295);
    i[HI] = hr;
    i[LO] = lr;
    return f[0];
  } else {
    return d;
  }
}
export function next_down(d) {
  if (isNaN(d) || -Infinity === d) {
    return d;
  } else {
    if (d === 0) {
      return -Number.MIN_VALUE;
    } else {
      var a = new ArrayBuffer(8);
      var f = new Float64Array(a);
      var i = new Uint32Array(a);
      var _ = f[0] = d;
      var ht = i[HI];
      var lt = i[LO];
      const [hr, lr] = d > 0 ? add64(ht, lt, 4294967295, 4294967295) : add64(ht, lt, 0, 1);
      i[HI] = hr;
      i[LO] = lr;
      return f[0];
    }
  }
}
export const MAX_SCALE = EXP_MAX + 1022 + 21 + 32 + 1;
export const two_to_the_double_scale_up = power_of_two(512);
export const two_to_the_double_scale_down = power_of_two(-512);
export function scalb(d, scaleFactor) {
  const [scale_factor, scale_increment, exp_delta] = scaleFactor < 0 ? [Math.max(scaleFactor, -MAX_SCALE), -512, two_to_the_double_scale_down] : [Math.min(scaleFactor, MAX_SCALE), 512, two_to_the_double_scale_up];
  var t = scale_factor >> 8 >>> 23;
  var exp_adjust = (scale_factor + t & 511) - t;
  var d__$1 = d * power_of_two(exp_adjust);
  var scale_factor__$1 = scale_factor - exp_adjust;
  while (true) {
    if (scale_factor__$1 === 0) {
      return d__$1;
    } else {
      var G__50310 = d__$1 * exp_delta;
      var G__50311 = scale_factor__$1 - scale_increment;
      d__$1 = G__50310;
      scale_factor__$1 = G__50311;
      continue;
    }
    break;
  }
}
