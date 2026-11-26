'use strict';

// Decide si el título de una oferta corresponde de verdad a lo que buscas
// (y no a una funda, un cable o un modelo parecido).

const STOPWORDS = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'con', 'para', 'the', 'and', 'for', 'with', 'und', 'mit', 'fur', 'pour', 'per', 'von', 'en', 'y', 'a']);

// Palabras típicas de accesorios. Si NO las has pedido en tu búsqueda, se descartan.
const ACCESSORY_WORDS = [
  'funda', 'fundas', 'carcasa', 'carcasas', 'protector', 'case', 'cases', 'cover', 'hulle', 'hullen',
  'schutzfolie', 'panzerglas', 'coque', 'etui', 'custodia', 'skin', 'sticker', 'cable', 'kabel', 'cavo',
  'ersatz', 'ersatzteil', 'recambio', 'repuesto', 'replacement', 'adaptador', 'adapter', 'soporte', 'halterung',
  'bolsa', 'tasche', 'mando',
];

const USED_RE = /\b(gebraucht|used|usado|usada|segunda mano|d'occasion|occasion|usato|tweedehands|refurbished|reacondicionado|generalüberholt|generalueberholt|renewed|b-ware|open box|ricondizionato|reconditionné|reconditionne|aufgearbeitet)\b/i;

const strip = (s) => String(s).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
const compact = (s) => strip(s).replace(/[^a-z0-9]/g, '');
const words = (s) => strip(s).split(/[^a-z0-9]+/).filter(Boolean);

function titleIndex(title) {
  const toks = words(title);
  const set = new Set(toks);
  for (let i = 0; i < toks.length - 1; i++) set.add(toks[i] + toks[i + 1]); // "128" + "gb" -> "128gb"
  return { set, compactTitle: compact(title) };
}

function tokenPresent(token, idx) {
  const c = compact(token);
  if (!c) return true;
  if (idx.set.has(c)) return true;
  // Códigos de modelo largos (WH1000XM5) también se aceptan dentro de otra palabra.
  return c.length >= 5 && /\d/.test(c) && idx.compactTitle.includes(c);
}

function queryTokens(query) {
  return String(query)
    .split(/\s+/)
    .map((t) => t.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, ''))
    .filter((t) => t && !STOPWORDS.has(strip(t)));
}

/**
 * @param {string} title
 * @param {{query:string, mustInclude?:string[], exclude?:string[], filterAccessories?:boolean}} item
 */
function isRelevant(title, item) {
  if (!title) return false;
  const idx = titleIndex(title);
  const tokens = queryTokens(item.query);
  if (!tokens.length) return true;

  // Todo lo que lleva números (modelos, capacidades) es obligatorio; del resto basta con ~65 %.
  const numeric = tokens.filter((t) => /\d/.test(t));
  const plain = tokens.filter((t) => !/\d/.test(t));
  if (!numeric.every((t) => tokenPresent(t, idx))) return false;
  if (plain.length) {
    const hits = plain.filter((t) => tokenPresent(t, idx)).length;
    if (hits / plain.length < 0.65) return false;
  }

  for (const must of item.mustInclude || []) {
    if (!words(must).every((w) => idx.set.has(w) || (w.length >= 4 && idx.compactTitle.includes(w)))) return false;
  }
  for (const bad of item.exclude || []) {
    const parts = words(bad);
    if (parts.length && parts.every((w) => idx.set.has(w) || (w.length >= 4 && idx.compactTitle.includes(w)))) return false;
  }

  if (item.filterAccessories !== false) {
    const asked = new Set(words(item.query));
    const hasAccessory = words(title).some((w) => ACCESSORY_WORDS.includes(w) && !asked.has(w));
    if (hasAccessory) return false;
  }
  return true;
}

function detectCondition(title) {
  return USED_RE.test(String(title || '')) ? 'used' : 'new';
}

module.exports = { isRelevant, detectCondition, compact };
