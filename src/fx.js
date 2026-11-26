'use strict';

const fs = require('fs');
const path = require('path');
const { request } = require('./http');

// Tipos aproximados por si no hay conexión con el servicio de cambio (unidades de moneda por 1 EUR).
const FALLBACK = { EUR: 1, USD: 1.1, GBP: 0.85, PLN: 4.3, SEK: 11.3, DKK: 7.46, NOK: 11.6, CHF: 0.95, CZK: 25, HUF: 400, RON: 4.97 };
const MAX_AGE_MS = 20 * 3600 * 1000;

class Fx {
  constructor(dataDir) {
    this.file = path.join(dataDir, 'fx.json');
    this.rates = { ...FALLBACK };
    this.updatedAt = null;
    this.live = false;
    try {
      const saved = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      if (saved && saved.rates) {
        this.rates = { ...FALLBACK, ...saved.rates };
        this.updatedAt = saved.updatedAt;
        this.live = true;
      }
    } catch {
      /* primera ejecución */
    }
  }

  /** Actualiza los tipos desde el BCE (vía frankfurter.dev) si están viejos. */
  async refresh() {
    if (this.updatedAt && Date.now() - Date.parse(this.updatedAt) < MAX_AGE_MS) return;
    try {
      const data = await request('https://api.frankfurter.dev/v1/latest?base=EUR', { timeout: 10000 });
      if (data && data.rates) {
        this.rates = { ...FALLBACK, ...data.rates, EUR: 1 };
        this.updatedAt = new Date().toISOString();
        this.live = true;
        fs.writeFileSync(this.file, JSON.stringify({ rates: this.rates, updatedAt: this.updatedAt }));
      }
    } catch (e) {
      console.warn('[fx] no se pudieron actualizar los tipos de cambio, uso los guardados:', e.message);
    }
  }

  toEur(amount, currency = 'EUR') {
    const rate = this.rates[currency];
    if (!rate) return NaN;
    return amount / rate;
  }
}

module.exports = { Fx, FALLBACK };
