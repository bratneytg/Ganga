'use strict';

const path = require('path');
const crypto = require('crypto');
const express = require('express');

const { DB } = require('./src/db');
const { Fx } = require('./src/fx');
const { Engine } = require('./src/engine');
const { Notifier } = require('./src/notify');
const { Scheduler } = require('./src/scheduler');
const { COUNTRIES } = require('./src/countries');
const { getSettings, publicSettings, applySettingsPatch, EBAY_MARKETPLACES } = require('./src/settings');
const { providerStatus } = require('./src/providers');
const { assertPublicUrl } = require('./src/providers/urlwatch');

const DATA_DIR = path.join(__dirname, 'data');
const PORT = Number(process.env.PORT) || 3000;
const HOST = '127.0.0.1'; // solo accesible desde este ordenador

const db = new DB(path.join(DATA_DIR, 'db.json'));
db.load();
const fx = new Fx(DATA_DIR);
const notifier = new Notifier();
const settingsNow = () => getSettings(db);
const engine = new Engine({ db, fx, notifier, getSettings: settingsNow });
const scheduler = new Scheduler({ db, engine, getSettings: settingsNow });

const app = express();
app.disable('x-powered-by');

// Protección contra webs maliciosas que intenten hablar con tu servidor local:
// solo se aceptan peticiones dirigidas a localhost y, si traen Origin, que sea el propio servidor.
app.use((req, res, next) => {
  const host = (req.headers.host || '').toLowerCase();
  if (!/^(localhost|127\.0\.0\.1)(:\d+)?$/.test(host)) return res.status(403).json({ error: 'Host no permitido' });
  const origin = req.headers.origin;
  if (origin && origin.toLowerCase() !== `http://${host}`) return res.status(403).json({ error: 'Origen no permitido' });
  next();
});
app.use(express.json({ limit: '100kb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ---------- validación de artículos ----------

const cleanStr = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');
const cleanWords = (v) => (Array.isArray(v) ? v : String(v || '').split(','))
  .map((w) => cleanStr(w, 40))
  .filter(Boolean)
  .slice(0, 20);
const optionalNumber = (v) => {
  if (v === '' || v == null) return null;
  const n = Number(String(v).replace(',', '.'));
  return Number.isFinite(n) && n > 0 && n < 1e6 ? n : undefined;
};

async function validateItem(body, existing) {
  const errors = [];
  const item = {};
  const name = cleanStr(body.name, 120);
  if (!name) errors.push('Ponle un nombre al artículo');
  item.name = name;
  item.query = cleanStr(body.query, 160) || name; // lo que se busca en las tiendas
  item.condition = ['new', 'used', 'any'].includes(body.condition) ? body.condition : 'new';
  item.mustInclude = cleanWords(body.mustInclude);
  item.exclude = cleanWords(body.exclude);
  item.filterAccessories = body.filterAccessories !== false;
  item.notes = cleanStr(body.notes, 300);
  item.enabled = body.enabled !== false;

  for (const key of ['targetPrice', 'minPrice']) {
    const v = optionalNumber(body[key]);
    if (v === undefined) errors.push(`${key === 'targetPrice' ? 'Precio objetivo' : 'Precio mínimo'} no válido`);
    else item[key] = v;
  }

  item.countries = Array.isArray(body.countries) ? body.countries.filter((c) => COUNTRIES[c]) : [];

  item.urls = [];
  for (const entry of (Array.isArray(body.urls) ? body.urls : []).slice(0, 10)) {
    const raw = cleanStr(typeof entry === 'string' ? entry : entry && entry.url, 600);
    if (!raw) continue;
    try {
      await assertPublicUrl(raw);
      item.urls.push({ url: raw, label: cleanStr(entry && entry.label, 40) });
    } catch (e) {
      errors.push(`URL rechazada (${raw.slice(0, 40)}…): ${e.message}`);
    }
  }
  return { item, errors, existing };
}

const publicItem = (i) => ({
  id: i.id, name: i.name, query: i.query, condition: i.condition, mustInclude: i.mustInclude, exclude: i.exclude,
  filterAccessories: i.filterAccessories, targetPrice: i.targetPrice ?? null, minPrice: i.minPrice ?? null,
  countries: i.countries || [], urls: i.urls || [], notes: i.notes || '', enabled: i.enabled !== false,
  createdAt: i.createdAt, lastCheckedAt: i.lastCheckedAt || null, lastError: i.lastError || null,
  best: i.best || null, stats: i.stats || null, offers: i.offers || [],
  history: (i.history || []).slice(-400),
});

// ---------- API ----------

app.get('/api/state', (req, res) => {
  const s = settingsNow();
  res.json({
    items: db.data.items.map(publicItem),
    status: {
      running: engine.running,
      progress: engine.progress,
      lastRun: db.data.meta.lastRun || null,
      nextRunAt: scheduler.nextRunAt(),
      fxLive: fx.live,
      fxUpdatedAt: fx.updatedAt,
    },
    settings: publicSettings(s),
    providers: providerStatus(s),
    countries: Object.fromEntries(Object.entries(COUNTRIES).map(([k, v]) => [k, v.name])),
    ebayMarketplaces: Object.keys(EBAY_MARKETPLACES),
  });
});

app.get('/api/status', (req, res) => {
  res.json({ running: engine.running, progress: engine.progress, lastRun: db.data.meta.lastRun || null, nextRunAt: scheduler.nextRunAt() });
});

app.post('/api/items', async (req, res) => {
  const { item, errors } = await validateItem(req.body || {});
  if (errors.length) return res.status(400).json({ errors });
  Object.assign(item, { id: crypto.randomUUID(), createdAt: new Date().toISOString(), history: [], offers: [], best: null, stats: null, lastNotified: null });
  db.data.items.push(item);
  db.save();
  res.status(201).json(publicItem(item));
  // Primera búsqueda inmediata, sin hacer esperar a la respuesta.
  if (!engine.running) engine.runOne(item.id).catch((e) => console.error('[engine]', e.message));
});

app.put('/api/items/:id', async (req, res) => {
  const current = db.data.items.find((i) => i.id === req.params.id);
  if (!current) return res.status(404).json({ error: 'No existe' });
  const { item, errors } = await validateItem(req.body || {});
  if (errors.length) return res.status(400).json({ errors });
  const searchChanged = ['query', 'condition', 'urls', 'countries', 'mustInclude', 'exclude', 'filterAccessories', 'minPrice']
    .some((k) => JSON.stringify(current[k] ?? null) !== JSON.stringify(item[k] ?? null));
  Object.assign(current, item);
  if (current.targetPrice !== item.targetPrice) current.lastNotified = null;
  db.save();
  res.json(publicItem(current));
  if (searchChanged && !engine.running) engine.runOne(current.id).catch((e) => console.error('[engine]', e.message));
});

app.delete('/api/items/:id', (req, res) => {
  const before = db.data.items.length;
  db.data.items = db.data.items.filter((i) => i.id !== req.params.id);
  if (db.data.items.length === before) return res.status(404).json({ error: 'No existe' });
  db.save();
  res.json({ ok: true });
});

const startJob = (res, job) => {
  if (engine.running) return res.status(409).json({ error: 'Ya hay una búsqueda en marcha' });
  job().catch((e) => console.error('[engine]', e.message));
  res.status(202).json({ started: true });
};
app.post('/api/run', (req, res) => startJob(res, () => engine.runAll('manual')));
app.post('/api/items/:id/check', (req, res) => {
  if (!db.data.items.some((i) => i.id === req.params.id)) return res.status(404).json({ error: 'No existe' });
  startJob(res, () => engine.runOne(req.params.id));
});

app.put('/api/settings', (req, res) => {
  const errors = applySettingsPatch(db, req.body || {});
  if (errors.length) return res.status(400).json({ errors });
  db.save();
  res.json({ settings: publicSettings(settingsNow()), providers: providerStatus(settingsNow()), nextRunAt: scheduler.nextRunAt() });
});

app.post('/api/test-notify', async (req, res) => {
  const result = await notifier.send(settingsNow(), {
    title: 'Ganga: prueba de aviso',
    message: 'Si lees esto, los avisos funcionan.',
    url: `http://localhost:${PORT}`,
  });
  if (!result.sent.length && !result.errors.length) return res.status(400).json({ error: 'No hay ningún canal de avisos configurado' });
  res.status(result.errors.length && !result.sent.length ? 502 : 200).json(result);
});

app.use('/api', (req, res) => res.status(404).json({ error: 'Ruta no encontrada' }));
app.use((err, req, res, next) => {
  console.error('[server]', err.message);
  res.status(err.status || 500).json({ error: err.status && err.status < 500 ? err.message : 'Error interno' });
});

const server = app.listen(PORT, HOST, () => {
  console.log(`\n  Ganga funcionando en  http://localhost:${PORT}\n  (déjalo abierto: busca solo a las ${settingsNow().scheduleTimes.join(', ')})\n`);
  fx.refresh();
  scheduler.start();
});
server.on('error', (e) => {
  console.error(e.code === 'EADDRINUSE' ? `El puerto ${PORT} ya está en uso. ¿Tienes Ganga abierto en otra ventana?` : e.message);
  process.exit(1);
});

module.exports = { app, db, engine };
