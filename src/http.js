'use strict';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * fetch con timeout y un reintento. Los mensajes de error solo llevan el host
 * (nunca la ruta ni la query) para que no se cuelen tokens en logs o en la interfaz.
 */
async function request(url, { method = 'GET', headers = {}, body, timeout = 20000, retries = 1, as = 'json' } = {}) {
  const host = new URL(url).host;
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetch(url, { method, headers, body, signal: AbortSignal.timeout(timeout), redirect: 'follow' });
      if (!res.ok) {
        const err = new Error(`${host} respondió HTTP ${res.status}`);
        err.status = res.status;
        // Errores del cliente (clave mala, etc.) no se reintentan, salvo 429.
        err.noRetry = res.status < 500 && res.status !== 429;
        throw err;
      }
      return as === 'json' ? await res.json() : await res.text();
    } catch (e) {
      lastError = e.status ? e : new Error(`${host}: ${e.name === 'TimeoutError' ? 'tiempo de espera agotado' : e.message}`);
      lastError.status = e.status;
      if (e.noRetry || attempt === retries) break;
      await sleep(1500 * (attempt + 1));
    }
  }
  throw lastError;
}

function withTimeout(promise, ms, label) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label}: tiempo máximo superado`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

module.exports = { request, withTimeout, sleep };
