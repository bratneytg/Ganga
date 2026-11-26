'use strict';

// eBay Browse API (gratuita con una cuenta de desarrollador). Da precio real, país del vendedor
// y coste de envío hacia tu país, así que es la fuente más precisa del coste total.

const { request, sleep } = require('../http');
const { EBAY_MARKETPLACES } = require('../settings');

const API = 'https://api.ebay.com';
let tokenCache = { key: null, token: null, expiresAt: 0 };

async function getToken(settings) {
  const key = `${settings.ebayClientId}:${settings.ebayClientSecret}`;
  if (tokenCache.key === key && Date.now() < tokenCache.expiresAt) return tokenCache.token;
  const basic = Buffer.from(key).toString('base64');
  const data = await request(`${API}/identity/v1/oauth2/token`, {
    method: 'POST',
    headers: { Authorization: `Basic ${basic}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=client_credentials&scope=' + encodeURIComponent(`${API}/oauth/api_scope`),
  });
  tokenCache = { key, token: data.access_token, expiresAt: Date.now() + (data.expires_in - 120) * 1000 };
  return tokenCache.token;
}

function cheapestShipping(shippingOptions) {
  const costs = (shippingOptions || [])
    .map((o) => (o.shippingCost ? { amount: Number(o.shippingCost.value), currency: o.shippingCost.currency } : null))
    .filter((c) => c && Number.isFinite(c.amount));
  if (!costs.length) return null;
  return costs.sort((a, b) => a.amount - b.amount)[0];
}

function normalizeItem(it, marketplaceCountry) {
  const price = Number(it.price?.value);
  if (!it.title || !it.itemWebUrl || !Number.isFinite(price)) return null;
  const ship = cheapestShipping(it.shippingOptions);
  const used = /used|usado|gebraucht|refurb|certified|seller refurbished|for parts/i.test(it.condition || '');
  return {
    provider: 'ebay',
    store: `eBay${it.seller?.username ? ` · ${it.seller.username}` : ''}`,
    title: it.title,
    url: it.itemWebUrl,
    image: it.image?.imageUrl || null,
    price,
    currency: it.price.currency || 'EUR',
    shipping: ship ? { ...ship, trusted: true } : null,
    shopCountry: it.itemLocation?.country || marketplaceCountry,
    searchedCountry: marketplaceCountry,
    condition: used ? 'used' : 'new',
  };
}

module.exports = {
  id: 'ebay',
  name: 'eBay',
  isConfigured: (settings) => Boolean(settings.ebayClientId && settings.ebayClientSecret),
  normalizeItem,

  async search(item, { settings }) {
    const token = await getToken(settings);
    const offers = [];
    const warnings = [];
    let failures = 0;
    const marketplaces = settings.ebayMarketplaces.filter((m) => EBAY_MARKETPLACES[m]);

    const filters = ['buyingOptions:{FIXED_PRICE}'];
    if (item.condition === 'new') filters.push('conditions:{NEW}');
    if (item.condition === 'used') filters.push('conditions:{USED}');

    // Contexto del comprador: eBay calcula el envío hacia este país (y código postal si lo das).
    const ctx = `country=${settings.homeCountry}` + (settings.homeZip ? `,zip=${settings.homeZip}` : '');

    for (const marketplace of marketplaces) {
      try {
        const params = new URLSearchParams({ q: item.query, limit: '50', sort: 'price', filter: filters.join(',') });
        const data = await request(`${API}/buy/browse/v1/item_summary/search?${params}`, {
          headers: {
            Authorization: `Bearer ${token}`,
            'X-EBAY-C-MARKETPLACE-ID': marketplace,
            'X-EBAY-C-ENDUSERCTX': `contextualLocation=${encodeURIComponent(ctx)}`,
          },
        });
        for (const it of data.itemSummaries || []) {
          const offer = normalizeItem(it, EBAY_MARKETPLACES[marketplace]);
          if (offer) offers.push(offer);
        }
      } catch (e) {
        failures++;
        warnings.push(`eBay (${marketplace}): ${e.message}`);
        if (e.status === 401 || e.status === 403) break;
      }
      await sleep(250);
    }
    if (marketplaces.length && failures === marketplaces.length) throw new Error(warnings[0] || 'eBay no respondió');
    return { offers, warnings };
  },
};
