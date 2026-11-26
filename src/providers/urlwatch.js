'use strict';

// Vigila páginas de producto concretas (la ficha de una tienda). No necesita claves:
// lee los datos estructurados (JSON-LD de schema.org) o las metaetiquetas de precio.
// Es lo que usan casi todas las tiendas para Google; no funciona en las que bloquean bots (p. ej. Amazon).

const dns = require('dns').promises;
const net = require('net');
const cheerio = require('cheerio');
const { request } = require('../http');
const { parsePrice } = require('../price');
const { countryFromUrl } = require('../countries');

const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
  Accept: 'text/html,application/xhtml+xml',
  'Accept-Language': 'de,es;q=0.8,en;q=0.6',
};

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
  }
  const low = ip.toLowerCase();
  return low === '::1' || low.startsWith('fc') || low.startsWith('fd') || low.startsWith('fe80') || low.startsWith('::ffff:127.') || low.startsWith('::ffff:10.') || low.startsWith('::ffff:192.168.');
}

/** Solo http(s) hacia internet público: evita que la app se use para tocar tu red local. */
async function assertPublicUrl(rawUrl) {
  let u;
  try {
    u = new URL(rawUrl);
  } catch {
    throw new Error('URL no válida');
  }
  if (!/^https?:$/.test(u.protocol)) throw new Error('Solo se admiten URLs http(s)');
  if (u.hostname === 'localhost' || u.hostname.endsWith('.local')) throw new Error('No se admiten direcciones locales');
  const addrs = net.isIP(u.hostname) ? [{ address: u.hostname }] : await dns.lookup(u.hostname, { all: true });
  if (addrs.some((a) => isPrivateIp(a.address))) throw new Error('No se admiten direcciones de red local');
  return u;
}

function* walk(node) {
  if (Array.isArray(node)) {
    for (const n of node) yield* walk(n);
  } else if (node && typeof node === 'object') {
    yield node;
    if (node['@graph']) yield* walk(node['@graph']);
  }
}

const typesOf = (node) => [].concat(node['@type'] || []).map(String);

function offerPrice(offer) {
  const raw = offer.price ?? offer.lowPrice ?? offer.priceSpecification?.price;
  const parsed = parsePrice(raw, offer.priceCurrency || offer.priceSpecification?.priceCurrency || null);
  if (!parsed) return null;
  const availability = String(offer.availability || '');
  return {
    value: parsed.value,
    currency: parsed.currency,
    available: !/OutOfStock|SoldOut|Discontinued/i.test(availability),
    shipping: offer.shippingDetails?.shippingRate?.value ?? null,
    shippingCurrency: offer.shippingDetails?.shippingRate?.currency || null,
  };
}

/** Extrae título, precio, moneda y disponibilidad de una página de producto. */
function extractFromHtml(html, url) {
  const $ = cheerio.load(html);
  let title = null;
  let image = null;
  const found = [];

  $('script[type="application/ld+json"]').each((_, el) => {
    let json;
    try {
      json = JSON.parse($(el).contents().text());
    } catch {
      return;
    }
    for (const node of walk(json)) {
      const types = typesOf(node);
      if (types.includes('Product')) {
        title = title || node.name || null;
        image = image || (Array.isArray(node.image) ? node.image[0] : node.image) || null;
        for (const o of walk(node.offers || [])) {
          const p = offerPrice(o);
          if (p) found.push(p);
        }
      } else if (types.some((t) => /Offer$/.test(t))) {
        const p = offerPrice(node);
        if (p) found.push(p);
      }
    }
  });

  if (!found.length) {
    const meta = (...sels) => sels.map((s) => $(s).attr('content')).find(Boolean);
    const amount = meta('meta[property="product:price:amount"]', 'meta[property="og:price:amount"]', 'meta[itemprop="price"]');
    const parsed = parsePrice(amount, meta('meta[property="product:price:currency"]', 'meta[property="og:price:currency"]', 'meta[itemprop="priceCurrency"]') || null);
    if (parsed) found.push({ value: parsed.value, currency: parsed.currency, available: true, shipping: null });
  }
  if (!found.length) return null;

  // Entre varias ofertas de la misma ficha, la más barata que esté disponible.
  const inStock = found.filter((f) => f.available);
  const best = (inStock.length ? inStock : found).sort((a, b) => a.value - b.value)[0];
  title = title || $('meta[property="og:title"]').attr('content') || $('title').first().text().trim() || url;
  image = image || $('meta[property="og:image"]').attr('content') || null;
  return { title: String(title).trim().slice(0, 200), image, ...best };
}

module.exports = {
  id: 'urlwatch',
  name: 'Páginas concretas (URL)',
  isConfigured: () => true,
  needsUrls: true,
  extractFromHtml,
  assertPublicUrl,

  async search(item, { settings }) {
    const offers = [];
    const warnings = [];
    for (const entry of item.urls || []) {
      try {
        const u = await assertPublicUrl(entry.url);
        const html = await request(u.href, { headers: HEADERS, as: 'text', timeout: 25000 });
        const data = extractFromHtml(html, u.href);
        if (!data) {
          warnings.push(`${u.hostname}: no encontré el precio en la página (puede bloquear robots)`);
          continue;
        }
        const shopCountry = countryFromUrl(u.href) || settings.homeCountry;
        offers.push({
          provider: 'urlwatch',
          store: entry.label || u.hostname.replace(/^www\./, ''),
          title: data.title,
          url: u.href,
          image: data.image,
          price: data.value,
          currency: data.currency || 'EUR',
          shipping: data.shipping != null ? { amount: Number(data.shipping), currency: data.shippingCurrency || data.currency || 'EUR', trusted: shopCountry === settings.homeCountry } : null,
          shopCountry,
          condition: 'unknown',
          unavailable: !data.available,
        });
      } catch (e) {
        warnings.push(`${entry.url.slice(0, 60)}: ${e.message}`);
      }
    }
    return { offers, warnings };
  },
};
