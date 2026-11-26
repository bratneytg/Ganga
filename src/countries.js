'use strict';

// Países que Ganga sabe tratar: nombre (para la interfaz), moneda y idioma de búsqueda.
const COUNTRIES = {
  DE: { name: 'Alemania', currency: 'EUR', lang: 'de', eu: true },
  ES: { name: 'España', currency: 'EUR', lang: 'es', eu: true },
  FR: { name: 'Francia', currency: 'EUR', lang: 'fr', eu: true },
  IT: { name: 'Italia', currency: 'EUR', lang: 'it', eu: true },
  NL: { name: 'Países Bajos', currency: 'EUR', lang: 'nl', eu: true },
  BE: { name: 'Bélgica', currency: 'EUR', lang: 'nl', eu: true },
  AT: { name: 'Austria', currency: 'EUR', lang: 'de', eu: true },
  PT: { name: 'Portugal', currency: 'EUR', lang: 'pt', eu: true },
  IE: { name: 'Irlanda', currency: 'EUR', lang: 'en', eu: true },
  PL: { name: 'Polonia', currency: 'PLN', lang: 'pl', eu: true },
  SE: { name: 'Suecia', currency: 'SEK', lang: 'sv', eu: true },
  DK: { name: 'Dinamarca', currency: 'DKK', lang: 'da', eu: true },
  CZ: { name: 'Chequia', currency: 'CZK', lang: 'cs', eu: true },
  GB: { name: 'Reino Unido', currency: 'GBP', lang: 'en', eu: false },
  CH: { name: 'Suiza', currency: 'CHF', lang: 'de', eu: false },
  US: { name: 'EE. UU.', currency: 'USD', lang: 'en', eu: false },
};

const isEu = (code) => !!(COUNTRIES[code] && COUNTRIES[code].eu);

// Dominios de primer nivel que delatan el país de una tienda (.co.uk -> GB).
const TLD_TO_COUNTRY = {
  de: 'DE', es: 'ES', fr: 'FR', it: 'IT', nl: 'NL', be: 'BE', at: 'AT', pt: 'PT', ie: 'IE',
  pl: 'PL', se: 'SE', dk: 'DK', cz: 'CZ', uk: 'GB', ch: 'CH', us: 'US',
};

// Los TLD que no dicen nada del país: se ignoran.
function countryFromUrl(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    if (/(^|\.)google\./.test(host)) return null; // enlaces de Google Shopping: no sirven
    const tld = host.split('.').pop();
    return TLD_TO_COUNTRY[tld] || null;
  } catch {
    return null;
  }
}

module.exports = { COUNTRIES, isEu, countryFromUrl };
