'use strict';

const { request } = require('./http');
const { COUNTRIES } = require('./countries');

const eur = (n) => `${n.toFixed(2).replace('.', ',')} €`;

function formatDeal(item, best, reasons) {
  const flags = [];
  if (reasons.includes('target')) flags.push(`por debajo de tu objetivo (${eur(item.targetPrice)})`);
  if (reasons.includes('lowest')) flags.push('mínimo histórico');
  const where = `${best.store}${COUNTRIES[best.country] ? ` (${COUNTRIES[best.country].name})` : ''}`;
  const detail = `${eur(best.priceEur)} + ${eur(best.shippingEur)} envío${best.importEur ? ` + ${eur(best.importEur)} importación` : ''}`;
  return {
    title: `Ganga: ${item.name}`,
    message: `${eur(best.landedEur)} en total en ${where} — ${flags.join(' y ')}.\n${detail}`,
    url: best.url,
  };
}

const channels = {
  async ntfy(settings, { title, message, url }) {
    const base = settings.ntfyServer.replace(/\/+$/, '');
    await request(base, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ topic: settings.ntfyTopic, title, message, click: url, tags: ['moneybag'] }),
      as: 'text',
    });
  },
  async telegram(settings, { title, message, url }) {
    await request(`https://api.telegram.org/bot${settings.telegramBotToken}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: settings.telegramChatId, text: `🔥 ${title}\n${message}\n${url}`, disable_web_page_preview: false }),
      as: 'text',
    });
  },
};

class Notifier {
  /** Envía un aviso por todos los canales configurados. Nunca lanza: devuelve los errores. */
  async send(settings, payload) {
    const sent = [];
    const errors = [];
    const jobs = [];
    if (settings.ntfyTopic) jobs.push(['ntfy', channels.ntfy]);
    if (settings.telegramBotToken && settings.telegramChatId) jobs.push(['telegram', channels.telegram]);
    for (const [name, fn] of jobs) {
      try {
        await fn(settings, payload);
        sent.push(name);
      } catch (e) {
        errors.push(`${name}: ${e.message}`);
      }
    }
    if (errors.length) console.warn('[notify]', errors.join(' · '));
    return { sent, errors };
  }

  sendDeal(settings, item, best, reasons) {
    return this.send(settings, formatDeal(item, best, reasons));
  }
}

module.exports = { Notifier, formatDeal };
