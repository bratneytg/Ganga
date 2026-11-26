'use strict';

const { COUNTRIES } = require('./countries');

const DEFAULTS = {
  homeCountry: 'DE', // país al que te envían las cosas
  homeZip: '', // opcional: mejora el cálculo de envío de eBay
  searchCountries: ['DE', 'ES', 'FR', 'IT', 'NL'], // dónde se busca en Google Shopping
  scheduleTimes: ['00:00', '06:00', '12:00', '18:00'], // 4 búsquedas al día
  serperApiKey: '',
  ebayClientId: '',
  ebayClientSecret: '',
  ebayMarketplaces: ['EBAY_DE', 'EBAY_ES', 'EBAY_FR', 'EBAY_IT'],
  ntfyServer: 'https://ntfy.sh',
  ntfyTopic: '',
  telegramBotToken: '',
  telegramChatId: '',
  demoMode: false,
  shipping: { domestic: 4.99, eu: 9.99, nonEu: 19.99, importPct: 19 },
};

const SECRET_KEYS = ['serperApiKey', 'ebayClientId', 'ebayClientSecret', 'telegramBotToken'];
const ENV_FALLBACK = {
  serperApiKey: 'SERPER_API_KEY',
  ebayClientId: 'EBAY_CLIENT_ID',
  ebayClientSecret: 'EBAY_CLIENT_SECRET',
  telegramBotToken: 'TELEGRAM_BOT_TOKEN',
  telegramChatId: 'TELEGRAM_CHAT_ID',
  ntfyTopic: 'NTFY_TOPIC',
};
const EBAY_MARKETPLACES = {
  EBAY_DE: 'DE', EBAY_ES: 'ES', EBAY_FR: 'FR', EBAY_IT: 'IT', EBAY_NL: 'NL', EBAY_AT: 'AT',
  EBAY_BE: 'BE', EBAY_IE: 'IE', EBAY_PL: 'PL', EBAY_GB: 'GB', EBAY_CH: 'CH', EBAY_US: 'US',
};

function getSettings(db) {
  const saved = db.data.settings || {};
  const s = { ...DEFAULTS, ...saved, shipping: { ...DEFAULTS.shipping, ...(saved.shipping || {}) } };
  for (const [key, envName] of Object.entries(ENV_FALLBACK)) {
    if (!s[key] && process.env[envName]) s[key] = process.env[envName];
  }
  return s;
}

/** Lo que se envía al navegador: las claves nunca salen del servidor. */
function publicSettings(s) {
  const out = { ...s };
  for (const key of SECRET_KEYS) {
    out[`${key}Set`] = Boolean(s[key]);
    delete out[key];
  }
  out.telegramBotTokenSet = Boolean(s.telegramBotToken);
  return out;
}

const isTime = (t) => typeof t === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(t);
const num = (v, min, max) => (Number.isFinite(Number(v)) && Number(v) >= min && Number(v) <= max ? Number(v) : null);
const str = (v, max = 200) => (typeof v === 'string' ? v.trim().slice(0, max) : null);

/** Valida el parche recibido y lo funde en db.data.settings. Devuelve la lista de errores. */
function applySettingsPatch(db, patch) {
  const errors = [];
  const next = { ...(db.data.settings || {}) };
  const p = patch || {};

  if (p.homeCountry !== undefined) {
    if (COUNTRIES[p.homeCountry]) next.homeCountry = p.homeCountry;
    else errors.push('País de envío no válido');
  }
  if (p.homeZip !== undefined) next.homeZip = str(p.homeZip, 12) ?? '';
  if (p.searchCountries !== undefined) {
    const list = Array.isArray(p.searchCountries) ? p.searchCountries.filter((c) => COUNTRIES[c]) : [];
    if (list.length) next.searchCountries = [...new Set(list)];
    else errors.push('Elige al menos un país de búsqueda');
  }
  if (p.scheduleTimes !== undefined) {
    const times = Array.isArray(p.scheduleTimes) ? [...new Set(p.scheduleTimes.filter(isTime))].sort() : [];
    if (times.length >= 1 && times.length <= 12) next.scheduleTimes = times;
    else errors.push('Horario no válido (formato HH:MM, entre 1 y 12 horas)');
  }
  if (p.ebayMarketplaces !== undefined) {
    next.ebayMarketplaces = (Array.isArray(p.ebayMarketplaces) ? p.ebayMarketplaces : []).filter((m) => EBAY_MARKETPLACES[m]);
  }
  for (const key of ['ntfyServer', 'ntfyTopic', 'telegramChatId']) {
    if (p[key] !== undefined) next[key] = str(p[key]) ?? '';
  }
  if (next.ntfyServer && !/^https?:\/\//i.test(next.ntfyServer)) errors.push('El servidor ntfy debe empezar por http(s)://');
  // Secretos: solo se tocan si llegan como texto (cadena vacía = borrar).
  for (const key of SECRET_KEYS) {
    if (typeof p[key] === 'string') next[key] = p[key].trim().slice(0, 300);
  }
  if (p.demoMode !== undefined) next.demoMode = Boolean(p.demoMode);
  if (p.shipping && typeof p.shipping === 'object') {
    const ship = { ...(next.shipping || {}) };
    for (const [k, max] of [['domestic', 500], ['eu', 500], ['nonEu', 500], ['importPct', 60]]) {
      if (p.shipping[k] !== undefined) {
        const v = num(p.shipping[k], 0, max);
        if (v === null) errors.push(`Valor de envío no válido: ${k}`);
        else ship[k] = v;
      }
    }
    next.shipping = ship;
  }

  if (!errors.length) db.data.settings = next;
  return errors;
}

module.exports = { DEFAULTS, EBAY_MARKETPLACES, getSettings, publicSettings, applySettingsPatch };
