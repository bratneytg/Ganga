'use strict';

const crypto = require('crypto');
const { isRelevant, detectCondition, compact } = require('./match');
const { estimateShipping, estimateImport } = require('./shipping');
const { activeProviders } = require('./providers');
const { withTimeout, sleep } = require('./http');
const { round2 } = require('./price');

const MAX_OFFERS = 40;
const MAX_HISTORY = 1500; // ~1 año a 4 búsquedas al día
const DIRECT_PROVIDERS = new Set(['urlwatch', 'demo']); // no necesitan filtro de relevancia

const median = (nums) => {
  if (!nums.length) return null;
  const s = [...nums].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
};

function dedupeKey(o, priceEur) {
  try {
    const u = new URL(o.url);
    if (!/(^|\.)google\./.test(u.hostname)) return `${u.hostname}${u.pathname}`;
  } catch {
    /* sin URL válida: se usa la clave de abajo */
  }
  return `${o.store}|${priceEur.toFixed(2)}|${compact(o.title).slice(0, 30)}`;
}

/**
 * Convierte las ofertas "crudas" de los proveedores en ofertas comparables:
 * filtra lo irrelevante, pasa todo a EUR y calcula precio + envío + importación.
 * Función pura (sin red), por eso es fácil de probar.
 */
function processOffers(raw, item, settings, fx) {
  const dest = settings.homeCountry;
  const byKey = new Map();

  for (const o of raw) {
    if (!o || !(o.price > 0)) continue;
    const priceEur = fx.toEur(o.price, o.currency || 'EUR');
    if (!Number.isFinite(priceEur) || priceEur <= 0) continue;
    if (!DIRECT_PROVIDERS.has(o.provider) && !isRelevant(o.title, item)) continue;
    if (item.minPrice && priceEur < item.minPrice) continue;

    const condition = o.condition || detectCondition(o.title);
    if (item.condition === 'new' && condition === 'used') continue;
    if (item.condition === 'used' && condition === 'new') continue;

    const shopCountry = o.shopCountry || dest;
    let shippingEur;
    let shippingEstimated;
    if (o.shipping && o.shipping.trusted && Number.isFinite(o.shipping.amount)) {
      shippingEur = fx.toEur(o.shipping.amount, o.shipping.currency || 'EUR');
      shippingEstimated = false;
    } else {
      shippingEur = estimateShipping(shopCountry, dest, settings.shipping).amount;
      shippingEstimated = true;
    }
    const importEur = estimateImport(shopCountry, dest, priceEur + shippingEur, settings.shipping.importPct);
    const landedEur = priceEur + shippingEur + importEur;

    const key = dedupeKey(o, priceEur);
    const prev = byKey.get(key);
    if (prev && prev.landedEur <= landedEur) continue;

    byKey.set(key, {
      id: crypto.createHash('sha1').update(key).digest('hex').slice(0, 12),
      provider: o.provider,
      store: o.store,
      title: o.title,
      url: o.url,
      image: o.image || null,
      price: o.price,
      currency: o.currency || 'EUR',
      priceEur: round2(priceEur),
      shippingEur: round2(shippingEur),
      shippingEstimated,
      importEur: round2(importEur),
      landedEur: round2(landedEur),
      country: shopCountry,
      foreign: shopCountry !== dest,
      condition,
      unavailable: Boolean(o.unavailable),
      suspicious: false,
    });
  }

  const offers = [...byKey.values()];

  // Un precio muy por debajo de la mediana suele ser un accesorio, un anuncio engañoso o un error.
  const comparable = offers.filter((o) => !DIRECT_PROVIDERS.has(o.provider) && !o.unavailable);
  if (comparable.length >= 5) {
    const med = median(comparable.map((o) => o.landedEur));
    for (const o of comparable) if (o.landedEur < med * 0.4) o.suspicious = true;
  }

  offers.sort((a, b) => a.unavailable - b.unavailable || a.suspicious - b.suspicious || a.landedEur - b.landedEur);
  return offers.slice(0, MAX_OFFERS);
}

function summarize(offers) {
  const good = offers.filter((o) => !o.unavailable && !o.suspicious);
  const prices = good.map((o) => o.landedEur);
  return {
    best: good[0] || null,
    stats: { count: offers.length, median: prices.length ? round2(median(prices)) : null, max: prices.length ? Math.max(...prices) : null },
  };
}

/** ¿Hay que avisar? Devuelve la lista de motivos o null. */
function decideNotification(item, best, priorHistory) {
  if (!best) return null;
  if (item.lastNotified && best.landedEur > item.lastNotified.landed * 1.05) item.lastNotified = null; // el precio rebotó: vuelve a avisar si baja
  const reasons = [];
  if (item.targetPrice && best.landedEur <= item.targetPrice) reasons.push('target');
  const prior = priorHistory.map((h) => h.landed);
  if (prior.length >= 3 && best.landedEur < Math.min(...prior) * 0.98) reasons.push('lowest');
  if (!reasons.length) return null;
  if (item.lastNotified && best.landedEur >= item.lastNotified.landed * 0.995) return null; // ya avisado a este precio
  return reasons;
}

/** Busca un artículo en todas las fuentes activas y actualiza el item en sitio. */
async function checkItem(item, { settings, fx }) {
  const now = new Date().toISOString();
  item.lastCheckedAt = now;
  const providers = activeProviders(settings, item);
  if (!providers.length) {
    item.lastError = 'No hay ninguna fuente activa: añade una clave en Ajustes, activa el modo demo o pega la URL de una tienda.';
    return { notify: null };
  }

  const raw = [];
  const problems = [];
  const results = await Promise.allSettled(providers.map((p) => withTimeout(p.search(item, { settings, fx }), 180000, p.name)));
  results.forEach((r, i) => {
    if (r.status === 'fulfilled') {
      raw.push(...r.value.offers);
      problems.push(...r.value.warnings);
    } else {
      problems.push(`${providers[i].name}: ${r.reason.message}`);
    }
  });
  item.lastError = problems.length ? problems.join(' · ').slice(0, 600) : null;
  if (results.every((r) => r.status === 'rejected')) return { notify: null }; // conserva las ofertas anteriores

  const offers = processOffers(raw, item, settings, fx);
  const { best, stats } = summarize(offers);
  const priorHistory = item.history || [];

  item.offers = offers;
  item.best = best;
  item.stats = stats;
  if (best) {
    item.history = [...priorHistory, { t: now, landed: best.landedEur, price: best.priceEur, store: best.store, country: best.country }].slice(-MAX_HISTORY);
  }
  const reasons = decideNotification(item, best, priorHistory);
  if (reasons) item.lastNotified = { landed: best.landedEur, t: now };
  return { notify: reasons ? { reasons, best } : null };
}

class Engine {
  constructor({ db, fx, notifier, getSettings }) {
    this.db = db;
    this.fx = fx;
    this.notifier = notifier;
    this.getSettings = getSettings;
    this.running = false;
    this.progress = { done: 0, total: 0, current: null };
  }

  async _checkAndNotify(item) {
    const settings = this.getSettings();
    const { notify } = await checkItem(item, { settings, fx: this.fx });
    this.db.save();
    if (notify) await this.notifier.sendDeal(settings, item, notify.best, notify.reasons);
  }

  async _exclusive(fn, total) {
    if (this.running) throw Object.assign(new Error('Ya hay una búsqueda en marcha'), { code: 'BUSY' });
    this.running = true;
    this.progress = { done: 0, total, current: null };
    try {
      await this.fx.refresh();
      return await fn();
    } finally {
      this.running = false;
      this.progress.current = null;
    }
  }

  async runAll(trigger = 'manual') {
    const items = this.db.data.items.filter((i) => i.enabled !== false);
    const started = Date.now();
    await this._exclusive(async () => {
      for (const item of items) {
        this.progress.current = item.name;
        try {
          await this._checkAndNotify(item);
        } catch (e) {
          item.lastError = `Error inesperado: ${e.message}`.slice(0, 300);
          this.db.save();
        }
        this.progress.done++;
        await sleep(400);
      }
    }, items.length);
    this.db.data.meta.lastRun = { at: new Date().toISOString(), trigger, items: items.length, ms: Date.now() - started };
    this.db.save();
  }

  async runOne(id) {
    const item = this.db.data.items.find((i) => i.id === id);
    if (!item) throw Object.assign(new Error('Artículo no encontrado'), { code: 'NOT_FOUND' });
    await this._exclusive(async () => {
      this.progress.current = item.name;
      await this._checkAndNotify(item);
      this.progress.done = 1;
    }, 1);
  }
}

module.exports = { Engine, processOffers, summarize, decideNotification, checkItem, median };
