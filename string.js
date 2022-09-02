export function blank_QMARK_(s) {
  if (!s) return true;
  if (s.length === 0) return true;
  if (s.trimLeft().length === 0) return true;
  return false;
}
