'use strict';

// Interpreta precios escritos de cualquier forma: "1.299,00 €", "€1,299.00", "99 zł", "19,99".

const CURRENCY_PATTERNS = [
  ['CHF', /\bCHF\b|\bFr\.(?=\s|\d)/i],
  ['EUR', /€|\bEUR\b/i],
  ['GBP', /£|\bGBP\b/i],
  ['PLN', /\bPLN\b|zł/i],
  ['SEK', /\bSEK\b/i],
  ['DKK', /\bDKK\b/i],
  ['NOK', /\bNOK\b/i],
  ['CZK', /\bCZK\b|Kč/i],
  ['HUF', /\bHUF\b|\bFt\b/i],
  ['RON', /\bRON\b|\blei\b/i],
  ['USD', /US\$|\bUSD\b|\$/],
];

const NUMBER_RE = /\d{1,3}(?:[   .,]\d{3})+(?:[.,]\d{1,2})?(?!\d)|\d+(?:[.,]\d{1,2})?/;

function normalizeNumber(raw) {
  let s = raw.replace(/[   ]/g, '');
  const lastDot = s.lastIndexOf('.');
  const lastComma = s.lastIndexOf(',');
  if (lastDot > -1 && lastComma > -1) {
    const decimal = lastDot > lastComma ? '.' : ',';
    const thousands = decimal === '.' ? ',' : '.';
    s = s.split(thousands).join('').replace(decimal, '.');
  } else if (lastDot > -1 || lastComma > -1) {
    const sep = lastDot > -1 ? '.' : ',';
    const parts = s.split(sep);
    const isThousands = parts.length > 2 || (parts[1].length === 3 && parts[0] !== '0');
    s = isThousands ? parts.join('') : parts.join('.');
  }
  return Number(s);
}

/** @returns {{value:number, currency:string|null}|null} */
function parsePrice(input, fallbackCurrency = null) {
  if (input == null) return null;
  if (typeof input === 'number') {
    return Number.isFinite(input) && input >= 0 ? { value: round2(input), currency: fallbackCurrency } : null;
  }
  const text = String(input);
  const match = text.match(NUMBER_RE);
  if (!match) return null;
  const value = normalizeNumber(match[0]);
  if (!Number.isFinite(value) || value < 0) return null;
  let currency = null;
  for (const [code, re] of CURRENCY_PATTERNS) {
    if (re.test(text)) {
      currency = code;
      break;
    }
  }
  return { value: round2(value), currency: currency || fallbackCurrency };
}

const round2 = (n) => Math.round(n * 100) / 100;

module.exports = { parsePrice, round2 };
