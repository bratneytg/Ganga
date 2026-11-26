'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { processOffers, summarize, decideNotification } = require('../src/engine');
const { slotInfo } = require('../src/scheduler');
const { DEFAULTS } = require('../src/settings');
const { Fx } = require('../src/fx');
const serper = require('../src/providers/serper');
const ebay = require('../src/providers/ebay');
const { extractFromHtml } = require('../src/providers/urlwatch');
const os = require('os');

const fx = new Fx(os.tmpdir() + '/ganga-test-nonexistent');
const settings = { ...DEFAULTS, homeCountry: 'DE' };
const item = { name: 'Sony WH-1000XM5', query: 'Sony WH-1000XM5', condition: 'new' };

const raw = (over) => ({
  provider: 'serper', store: 'Tienda', title: 'Sony WH-1000XM5 auriculares', url: 'https://tienda.de/a', price: 300, currency: 'EUR',
  shipping: null, shopCountry: 'DE', condition: undefined, ...over,
});

test('processOffers: calcula el precio total con envío, importación y moneda', () => {
  const offers = processOffers([
    raw({ store: 'A', url: 'https://a.de/1', price: 300, shipping: { amount: 0, currency: 'EUR', trusted: true } }),
    raw({ store: 'B', url: 'https://b.es/1', price: 280, shopCountry: 'ES' }), // envío UE estimado
    raw({ store: 'C', url: 'https://c.co.uk/1', price: 230, currency: 'GBP', shopCountry: 'GB' }), // fuera de la UE
  ], item, settings, fx);

  const byStore = Object.fromEntries(offers.map((o) => [o.store, o]));
  assert.equal(byStore.A.landedEur, 300);
  assert.equal(byStore.A.shippingEstimated, false);
  assert.equal(byStore.B.shippingEur, settings.shipping.eu);
  assert.equal(byStore.B.landedEur, 280 + settings.shipping.eu);
  assert.equal(byStore.B.foreign, true);
  const c = byStore.C;
  const priceEur = 230 / fx.rates.GBP;
  const expected = (priceEur + settings.shipping.nonEu) * (1 + settings.shipping.importPct / 100);
  assert.ok(Math.abs(c.landedEur - expected) < 0.02, `${c.landedEur} vs ${expected}`);
  assert.ok(c.importEur > 0);
});

test('processOffers: descarta accesorios, usados y duplicados; ordena por precio total', () => {
  const offers = processOffers([
    raw({ store: 'A', url: 'https://a.de/1', price: 320 }),
    raw({ store: 'Dup', url: 'https://a.de/1?utm=x', price: 320 }), // mismo producto
    raw({ store: 'Funda', url: 'https://f.de/1', title: 'Funda para Sony WH-1000XM5', price: 12 }),
    raw({ store: 'Usado', url: 'https://u.de/1', title: 'Sony WH-1000XM5 gebraucht', price: 150 }),
    raw({ store: 'Barata', url: 'https://b.de/1', price: 289 }),
  ], item, settings, fx);
  assert.equal(offers.length, 2);
  assert.equal(offers[0].store, 'Barata');
  assert.equal(offers[1].store, 'A');
});

test('processOffers: marca como sospechosa una oferta muy por debajo de la mediana', () => {
  const prices = [300, 310, 305, 315, 308];
  const list = prices.map((p, i) => raw({ store: `T${i}`, url: `https://t${i}.de/x`, price: p }));
  list.push(raw({ store: 'Trampa', url: 'https://trampa.de/x', price: 49 }));
  const offers = processOffers(list, item, settings, fx);
  const trap = offers.find((o) => o.store === 'Trampa');
  assert.equal(trap.suspicious, true);
  assert.equal(summarize(offers).best.store, 'T0');
  assert.equal(offers[offers.length - 1].store, 'Trampa'); // las sospechosas van al final
});

test('processOffers: el "envío gratis" de otro país no se da por bueno', () => {
  const offers = processOffers([
    raw({ store: 'Fuera', url: 'https://x.fr/1', shopCountry: 'FR', shipping: { amount: 0, currency: 'EUR', trusted: false } }),
  ], item, settings, fx);
  assert.equal(offers[0].shippingEur, settings.shipping.eu);
  assert.equal(offers[0].shippingEstimated, true);
});

test('decideNotification: objetivo, mínimo histórico y sin repetir', () => {
  const hist = [{ landed: 300 }, { landed: 295 }, { landed: 310 }];
  const it = { targetPrice: 250, lastNotified: null };
  assert.deepEqual(decideNotification(it, { landedEur: 240 }, hist), ['target', 'lowest']);
  it.lastNotified = { landed: 240 };
  assert.equal(decideNotification(it, { landedEur: 240 }, hist), null, 'mismo precio: no repetir');
  assert.deepEqual(decideNotification(it, { landedEur: 220 }, hist), ['target', 'lowest'], 'baja más: avisa otra vez');
  assert.equal(decideNotification({ targetPrice: null, lastNotified: null }, { landedEur: 290 }, hist), null);
  // rebote del precio y nueva bajada: vuelve a avisar
  const it2 = { targetPrice: 250, lastNotified: { landed: 240 } };
  const hist2 = [...hist, { landed: 240 }];
  assert.equal(decideNotification(it2, { landedEur: 280 }, hist2), null);
  assert.equal(it2.lastNotified, null);
  assert.deepEqual(decideNotification(it2, { landedEur: 245 }, hist2), ['target']);
});

test('scheduler: horas programadas', () => {
  const times = ['00:00', '06:00', '12:00', '18:00'];
  const now = new Date(2026, 10, 26, 13, 30);
  const { latest, next } = slotInfo(now, times);
  assert.equal(latest.getHours(), 12);
  assert.equal(next.getHours(), 18);
  const late = slotInfo(new Date(2026, 10, 26, 23, 59), times);
  assert.equal(late.next.getDate(), 27);
  assert.equal(late.next.getHours(), 0);
});

test('serper.normalizeRow lee precio, tienda y envío', () => {
  const o = serper.normalizeRow({ title: 'Sony WH-1000XM5', source: 'MediaMarkt', link: 'https://www.mediamarkt.de/p/1', price: '299,00 €', delivery: 'Kostenloser Versand' }, 'DE', 'DE');
  assert.equal(o.store, 'MediaMarkt');
  assert.equal(o.price, 299);
  assert.equal(o.currency, 'EUR');
  assert.deepEqual(o.shipping, { amount: 0, currency: 'EUR', trusted: true });
  assert.equal(o.shopCountry, 'DE');
  const g = serper.normalizeRow({ title: 'X', source: 'Shop', link: 'https://www.google.com/search?q=x', price: '£199.00' }, 'GB', 'DE');
  assert.equal(g.currency, 'GBP');
  assert.equal(g.shopCountry, 'GB');
  assert.equal(serper.normalizeRow({ title: 'sin precio', link: 'https://x.de' }, 'DE', 'DE'), null);
});

test('ebay.normalizeItem lee precio y el envío más barato', () => {
  const o = ebay.normalizeItem({
    title: 'Sony WH-1000XM5', itemWebUrl: 'https://www.ebay.de/itm/1', price: { value: '250.00', currency: 'EUR' },
    condition: 'Neu', shippingOptions: [{ shippingCost: { value: '5.90', currency: 'EUR' } }, { shippingCost: { value: '3.50', currency: 'EUR' } }],
    itemLocation: { country: 'PL' }, seller: { username: 'vendedor1' },
  }, 'DE');
  assert.equal(o.price, 250);
  assert.deepEqual(o.shipping, { amount: 3.5, currency: 'EUR', trusted: true });
  assert.equal(o.shopCountry, 'PL');
  assert.equal(o.condition, 'new');
  assert.equal(ebay.normalizeItem({ title: 'x', itemWebUrl: 'https://e', price: { value: 'abc' } }, 'DE'), null);
});

test('urlwatch.extractFromHtml lee JSON-LD y metaetiquetas', () => {
  const ld = `<html><head><title>Tienda</title><script type="application/ld+json">
    {"@context":"https://schema.org","@graph":[{"@type":"Product","name":"Sony WH-1000XM5","image":"https://img/1.jpg",
    "offers":{"@type":"Offer","price":"279.00","priceCurrency":"EUR","availability":"https://schema.org/InStock"}}]}
  </script></head></html>`;
  const a = extractFromHtml(ld, 'https://shop.de/p');
  assert.equal(a.value, 279);
  assert.equal(a.currency, 'EUR');
  assert.equal(a.available, true);
  assert.equal(a.title, 'Sony WH-1000XM5');

  const agg = `<script type="application/ld+json">{"@type":"Product","name":"P","offers":[
    {"@type":"Offer","price":"90,50","priceCurrency":"EUR","availability":"OutOfStock"},
    {"@type":"Offer","price":"99.90","priceCurrency":"EUR","availability":"InStock"}]}</script>`;
  assert.equal(extractFromHtml(agg, 'https://x.de').value, 99.9, 'ignora la oferta agotada');

  const meta = '<head><meta property="og:title" content="Cosa"><meta property="product:price:amount" content="45.00"><meta property="product:price:currency" content="EUR"></head>';
  const m = extractFromHtml(meta, 'https://x.de');
  assert.equal(m.value, 45);
  assert.equal(m.title, 'Cosa');
  assert.equal(extractFromHtml('<html><body>nada</body></html>', 'https://x.de'), null);
});
