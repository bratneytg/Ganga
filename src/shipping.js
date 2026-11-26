'use strict';

const { isEu } = require('./countries');
const { parsePrice } = require('./price');

const FREE_RE = /gratis|gratuit|free|kostenlos|kostenfrei|versandkostenfrei|sin coste|incluido|inclus|inclusa|gratuita|besplatn|darmow/i;

/**
 * Interpreta textos de entrega como "Envío gratis", "+ 4,99 € de envío", "Get it by Wed (Free)".
 * @returns {{amount:number, currency:string|null}|null} null si no dice nada claro.
 */
function parseDelivery(text) {
  if (!text || typeof text !== 'string') return null;
  if (FREE_RE.test(text)) return { amount: 0, currency: null };
  const p = parsePrice(text);
  return p ? { amount: p.value, currency: p.currency } : null;
}

/**
 * Estimación cuando la tienda no dice cuánto cuesta el envío.
 * @returns {{amount:number, region:'domestic'|'eu'|'nonEu'}} en EUR
 */
function estimateShipping(shopCountry, destCountry, cfg) {
  if (!shopCountry || shopCountry === destCountry) return { amount: cfg.domestic, region: 'domestic' };
  if (isEu(shopCountry) && isEu(destCountry)) return { amount: cfg.eu, region: 'eu' };
  return { amount: cfg.nonEu, region: 'nonEu' };
}

/** Coste de importación estimado (IVA/aduana) si la tienda está fuera de la UE y tú dentro. */
function estimateImport(shopCountry, destCountry, baseEur, pct) {
  if (!shopCountry || isEu(shopCountry) || !isEu(destCountry)) return 0;
  return baseEur * (pct / 100);
}

module.exports = { parseDelivery, estimateShipping, estimateImport };
