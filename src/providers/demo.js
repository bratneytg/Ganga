'use strict';

// Proveedor de prueba: inventa ofertas verosímiles (los precios cambian en cada ronda)
// para poder ver cómo funciona la app sin ninguna clave. Se activa en Ajustes → Modo demo.

const STORES = [
  { store: 'MegaTienda', country: 'DE', ship: 0 },
  { store: 'ElectroMax', country: 'DE', ship: 4.9 },
  { store: 'PCbarato', country: 'ES', ship: 6.5 },
  { store: 'Tecnomás', country: 'ES', ship: 0 },
  { store: 'LeBonPrix', country: 'FR', ship: null },
  { store: 'Negozio24', country: 'IT', ship: null },
  { store: 'DutchDeals', country: 'NL', ship: 5.95 },
  { store: 'LondonGadgets', country: 'GB', ship: null },
  { store: 'Outlet Polska', country: 'PL', ship: 8.0 },
];

function hash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function rng(seed) {
  let s = seed || 1;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

module.exports = {
  id: 'demo',
  name: 'Demo (datos inventados)',
  isConfigured: (settings) => Boolean(settings.demoMode),

  async search(item, { settings }) {
    const now = new Date();
    const slot = `${now.toISOString().slice(0, 10)}-${Math.floor(now.getHours() / 6)}`;
    const rand = rng(hash(`${item.name}|${slot}`));
    const base = (item.targetPrice ? item.targetPrice * 1.12 : 40 + (hash(item.name) % 360));
    const offers = STORES.map((s, i) => {
      const price = Math.round(base * (0.82 + rand() * 0.45) * 100) / 100;
      return {
        provider: 'demo',
        store: s.store,
        title: `${item.name} (${['nuevo', 'precintado', 'original'][i % 3]})`,
        url: `https://example.com/demo/${encodeURIComponent(item.name)}/${i}`,
        price,
        currency: s.country === 'GB' ? 'GBP' : s.country === 'PL' ? 'PLN' : 'EUR',
        shipping: s.ship == null ? null : { amount: s.ship, currency: 'EUR', trusted: s.country === settings.homeCountry },
        shopCountry: s.country,
        condition: 'new',
      };
    }).map((o) => (o.currency === 'GBP' ? { ...o, price: Math.round(o.price * 0.85 * 100) / 100 } : o.currency === 'PLN' ? { ...o, price: Math.round(o.price * 4.3 * 100) / 100 } : o));
    return { offers, warnings: [] };
  },
};
