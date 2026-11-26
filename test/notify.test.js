'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { Notifier, formatDeal } = require('../src/notify');
const { DEFAULTS } = require('../src/settings');

test('ntfy recibe un JSON con título, mensaje y enlace', async () => {
  let received;
  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => { received = { method: req.method, body: JSON.parse(body) }; res.end('{}'); });
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const settings = { ...DEFAULTS, ntfyServer: `http://127.0.0.1:${server.address().port}`, ntfyTopic: 'prueba' };
  const item = { name: 'Sony WH-1000XM5', targetPrice: 250 };
  const best = { landedEur: 239.5, priceEur: 229.51, shippingEur: 9.99, importEur: 0, store: 'MediaMarkt', country: 'DE', url: 'https://x.de/p' };
  const result = await new Notifier().sendDeal(settings, item, best, ['target', 'lowest']);
  server.close();
  assert.deepEqual(result.sent, ['ntfy']);
  assert.equal(received.body.topic, 'prueba');
  assert.equal(received.body.click, 'https://x.de/p');
  assert.match(received.body.message, /239,50 €/);
  assert.match(received.body.message, /mínimo histórico/);
});

test('formatDeal menciona importación cuando existe', () => {
  const m = formatDeal({ name: 'X', targetPrice: null }, { landedEur: 100, priceEur: 80, shippingEur: 10, importEur: 10, store: 'T', country: 'GB', url: 'u' }, ['lowest']);
  assert.match(m.message, /importación/);
});
