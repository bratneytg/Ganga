'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parsePrice } = require('../src/price');
const { isRelevant, detectCondition } = require('../src/match');
const { parseDelivery } = require('../src/shipping');
const { countryFromUrl } = require('../src/countries');

test('parsePrice entiende formatos europeos y anglosajones', () => {
  const cases = [
    ['1.299,00 €', 1299, 'EUR'],
    ['€1,299.00', 1299, 'EUR'],
    ['19,99 €', 19.99, 'EUR'],
    ['19.99', 19.99, null],
    ['1 299,00 €', 1299, 'EUR'],
    ['1.299 €', 1299, 'EUR'],
    ['£249.00', 249, 'GBP'],
    ['99,00 zł', 99, 'PLN'],
    ['1299.99', 1299.99, null],
    ['ab 79,90 EUR', 79.9, 'EUR'],
    ['$1,099', 1099, 'USD'],
    ['0,999', 0.999, null],
  ];
  for (const [input, value, currency] of cases) {
    const p = parsePrice(input);
    assert.ok(p, `no se pudo leer ${input}`);
    assert.equal(p.value, Math.round(value * 100) / 100, input);
    assert.equal(p.currency, currency, input);
  }
  assert.equal(parsePrice('sin precio'), null);
  assert.equal(parsePrice(null), null);
  assert.deepEqual(parsePrice(25.5, 'EUR'), { value: 25.5, currency: 'EUR' });
});

test('isRelevant acepta el producto y rechaza accesorios y modelos distintos', () => {
  const item = { query: 'Sony WH-1000XM5' };
  assert.ok(isRelevant('Sony WH-1000XM5 Auriculares inalámbricos negro', item));
  assert.ok(isRelevant('SONY WH1000XM5 Kopfhörer', item));
  assert.ok(!isRelevant('Sony WH-1000XM4 Auriculares', item), 'modelo distinto');
  assert.ok(!isRelevant('Funda para Sony WH-1000XM5', item), 'accesorio');
  assert.ok(!isRelevant('Ersatz Ohrpolster für Sony WH-1000XM5', item), 'accesorio alemán');
  assert.ok(!isRelevant('Sony Cámara Alpha 7', item));
});

test('isRelevant: números exactos y palabras obligatorias/excluidas', () => {
  const item = { query: 'iPhone 15 128GB', exclude: ['pro'], mustInclude: ['negro'] };
  assert.ok(isRelevant('Apple iPhone 15 128 GB negro', item));
  assert.ok(!isRelevant('Apple iPhone 15 Pro 128 GB negro', item), 'excluido');
  assert.ok(!isRelevant('Apple iPhone 15 128 GB azul', item), 'falta palabra obligatoria');
  assert.ok(!isRelevant('Apple iPhone 11 5G 128 GB negro', item), '15 no debe casar con 115');
  // Si pides el accesorio, no se filtra
  assert.ok(isRelevant('Funda silicona iPhone 15', { query: 'funda iPhone 15' }));
});

test('detectCondition reconoce segunda mano en varios idiomas', () => {
  for (const t of ['iPhone gebraucht', 'iPhone reacondicionado', 'iPhone refurbished', "iPhone d'occasion", 'iPhone usato']) {
    assert.equal(detectCondition(t), 'used', t);
  }
  assert.equal(detectCondition('iPhone 15 nuevo'), 'new');
});

test('parseDelivery', () => {
  assert.deepEqual(parseDelivery('Envío gratis'), { amount: 0, currency: null });
  assert.deepEqual(parseDelivery('Kostenloser Versand'), { amount: 0, currency: null });
  assert.deepEqual(parseDelivery('Free delivery'), { amount: 0, currency: null });
  assert.deepEqual(parseDelivery('+ 4,99 € de envío'), { amount: 4.99, currency: 'EUR' });
  assert.equal(parseDelivery('Llega el miércoles'), null);
  assert.equal(parseDelivery(undefined), null);
});

test('countryFromUrl', () => {
  assert.equal(countryFromUrl('https://www.mediamarkt.de/x'), 'DE');
  assert.equal(countryFromUrl('https://www.pccomponentes.com/x'), null);
  assert.equal(countryFromUrl('https://shop.example.co.uk/x'), 'GB');
  assert.equal(countryFromUrl('https://www.google.de/search?q=1'), null);
});
