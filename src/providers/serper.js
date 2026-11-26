'use strict';

// Google Shopping a través de Serper.dev: una sola consulta devuelve ofertas de muchísimas tiendas.
// Se consulta una vez por país configurado (gl=de, es, fr...) para cubrir tiendas de toda Europa.

const { request, sleep } = require('../http');
const { parsePrice } = require('../price');
const { parseDelivery } = require('../shipping');
const { COUNTRIES, countryFromUrl } = require('../countries');

const ENDPOINT = 'https://google.serper.dev/shopping';

// Google usa "uk" y no "gb" en el parámetro gl.
const glFor = (country) => (country === 'GB' ? 'uk' : country.toLowerCase());

function normalizeRow(row, searchedCountry, homeCountry) {
  const title = row.title || row.name;
  const url = row.link || row.product_link || row.productLink;
  const priceRaw = row.price ?? row.extracted_price;
  if (!title || !url || priceRaw == null) return null;

  const defaultCurrency = COUNTRIES[searchedCountry]?.currency || 'EUR';
  const parsed = parsePrice(priceRaw, defaultCurrency);
  if (!parsed) return null;

  const shopCountry = countryFromUrl(url) || searchedCountry;
  const delivery = parseDelivery(row.delivery || row.shipping);
  return {
    provider: 'serper',
    store: row.source || row.seller || new URL(url).hostname.replace(/^www\./, ''),
    title: String(title),
    url,
    image: row.imageUrl || row.thumbnail || null,
    price: parsed.value,
    currency: parsed.currency || defaultCurrency,
    // El "envío gratis" de Google solo vale para quien busca desde ese país.
    shipping: delivery ? { amount: delivery.amount, currency: delivery.currency || defaultCurrency, trusted: shopCountry === homeCountry || searchedCountry === homeCountry } : null,
    shopCountry,
    searchedCountry,
    condition: row.secondHandCondition || row.second_hand_condition ? 'used' : undefined,
  };
}

module.exports = {
  id: 'serper',
  name: 'Google Shopping (Serper)',
  isConfigured: (settings) => Boolean(settings.serperApiKey),
  normalizeRow,

  async search(item, { settings }) {
    const countries = (item.countries && item.countries.length ? item.countries : settings.searchCountries).filter((c) => COUNTRIES[c]);
    const offers = [];
    const warnings = [];
    let failures = 0;

    for (const country of countries) {
      try {
        const data = await request(ENDPOINT, {
          method: 'POST',
          headers: { 'X-API-KEY': settings.serperApiKey, 'Content-Type': 'application/json' },
          body: JSON.stringify({ q: item.query, gl: glFor(country), hl: COUNTRIES[country].lang, num: 40 }),
        });
        const rows = data.shopping || data.shopping_results || [];
        for (const row of rows) {
          const offer = normalizeRow(row, country, settings.homeCountry);
          if (offer) offers.push(offer);
        }
      } catch (e) {
        failures++;
        warnings.push(`Serper (${country}): ${e.message}`);
        if (e.status === 401 || e.status === 403) break; // clave inválida: no insistir con los demás países
      }
      await sleep(250);
    }
    if (failures === countries.length) throw new Error(warnings[0] || 'Serper no respondió');
    return { offers, warnings };
  },
};
