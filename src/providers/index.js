'use strict';

const serper = require('./serper');
const ebay = require('./ebay');
const urlwatch = require('./urlwatch');
const demo = require('./demo');

const ALL = [serper, ebay, urlwatch, demo];

/** Proveedores que pueden buscar este artículo ahora mismo. */
function activeProviders(settings, item) {
  return ALL.filter((p) => {
    if (!p.isConfigured(settings)) return false;
    if (p.needsUrls && !(item.urls && item.urls.length)) return false;
    return true;
  });
}

function providerStatus(settings) {
  return ALL.map((p) => ({ id: p.id, name: p.name, configured: p.isConfigured(settings) }));
}

module.exports = { ALL, activeProviders, providerStatus };
