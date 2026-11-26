'use strict';

const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const safeUrl = (u) => (/^https?:\/\//i.test(u) ? u : '#');
const money = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
const eur = (n) => money.format(n);

let state = null;
let pollTimer = null;
const expanded = new Set();

// ---------- comunicación con el servidor ----------

async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(`/api${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error((data.errors && data.errors.join('. ')) || data.error || `Error ${res.status}`);
  return data;
}

let toastTimer;
function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.toggle('toast--error', isError);
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), isError ? 7000 : 3500);
}

async function load() {
  try {
    state = await api('/state');
  } catch (e) {
    $('#status').textContent = 'No se puede conectar con Ganga. ¿Sigue abierta la ventana de comandos?';
    return;
  }
  render();
  clearTimeout(pollTimer);
  if (state.status.running) pollTimer = setTimeout(load, 2000);
}

// ---------- fechas ----------

function when(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const time = d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
  const days = Math.round((new Date().setHours(0, 0, 0, 0) - new Date(d).setHours(0, 0, 0, 0)) / 86400000);
  if (days === 0) return `hoy a las ${time}`;
  if (days === 1) return `ayer a las ${time}`;
  if (days === -1) return `mañana a las ${time}`;
  return `${d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })} a las ${time}`;
}

// ---------- pintado ----------

function render() {
  renderStatus();
  renderBanner();
  renderItems();
}

function renderStatus() {
  const { status } = state;
  $('#runAll').disabled = status.running;
  const el = $('#status');
  if (status.running) {
    const p = status.progress;
    el.textContent = `Buscando${p.current ? `: ${p.current}` : ''} (${Math.min(p.done + 1, p.total)} de ${p.total})…`;
    return;
  }
  const parts = [];
  if (status.lastRun) parts.push(`Última búsqueda ${when(status.lastRun.at)}.`);
  if (status.nextRunAt) parts.push(`Próxima ${when(status.nextRunAt)}.`);
  if (!status.fxLive) parts.push('Cambios de moneda aproximados.');
  el.textContent = parts.join(' ');
}

function renderBanner() {
  const s = state.settings;
  const real = state.providers.filter((p) => p.configured && p.id !== 'demo' && p.id !== 'urlwatch');
  const hasUrls = state.items.some((i) => i.urls.length);
  let html = '';
  if (!real.length && !hasUrls && !s.demoMode) {
    html = `<div class="banner"><p>Ganga todavía no tiene de dónde sacar precios. Añade la clave gratuita de Serper (Google Shopping) para buscar en cientos de tiendas, o activa el modo demo para probar la app.</p><button class="btn" type="button" data-open-settings>Abrir ajustes</button></div>`;
  } else if (s.demoMode) {
    html = `<div class="banner banner--demo"><p>Modo demo: las ofertas son inventadas. Desactívalo en Ajustes cuando añadas una clave real.</p></div>`;
  }
  $('#banner').innerHTML = html;
}

function sparkline(item) {
  const hist = item.history.slice(-120);
  if (hist.length < 2) return '';
  const W = 190, H = 44, P = 5;
  const values = hist.map((h) => h.landed);
  let lo = Math.min(...values), hi = Math.max(...values);
  if (item.targetPrice) { lo = Math.min(lo, item.targetPrice); hi = Math.max(hi, item.targetPrice); }
  if (hi === lo) { hi += 1; lo -= 1; }
  const x = (i) => P + (i * (W - 2 * P)) / (hist.length - 1);
  const y = (v) => H - P - ((v - lo) / (hi - lo)) * (H - 2 * P);
  const pts = hist.map((h, i) => `${x(i).toFixed(1)},${y(h.landed).toFixed(1)}`).join(' ');
  const last = hist[hist.length - 1];
  const label = `Evolución del mejor precio total: de ${eur(hist[0].landed)} a ${eur(last.landed)} en ${hist.length} búsquedas, mínimo ${eur(Math.min(...values))}`;
  const target = item.targetPrice ? `<line class="spark__target" x1="${P}" x2="${W - P}" y1="${y(item.targetPrice).toFixed(1)}" y2="${y(item.targetPrice).toFixed(1)}"/>` : '';
  return `<svg class="spark" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(label)}" data-spark="${esc(item.id)}">
    ${target}<polyline class="spark__line" points="${pts}"/>
    <circle class="spark__now" cx="${x(hist.length - 1).toFixed(1)}" cy="${y(last.landed).toFixed(1)}" r="4.5"/>
    <circle class="spark__hover" r="4" hidden/>
    <rect class="spark__hit" x="0" y="0" width="${W}" height="${H}"/>
  </svg>`;
}

function flagsFor(item) {
  const best = item.best;
  if (!best) return { deal: false, flags: [] };
  const flags = [];
  const deal = item.targetPrice != null && best.landedEur <= item.targetPrice;
  if (deal) flags.push('<span class="flag flag--deal">Por debajo de tu objetivo</span>');
  const prior = item.history.slice(0, -1).map((h) => h.landed);
  if (prior.length >= 3 && best.landedEur < Math.min(...prior)) flags.push('<span class="flag">Mínimo histórico</span>');
  const med = item.stats && item.stats.median;
  if (med && best.landedEur < med * 0.95) flags.push(`<span class="flag">${Math.round((1 - best.landedEur / med) * 100)} % bajo la mediana</span>`);
  if (best.foreign) flags.push(`<span class="flag" title="Google Shopping no dice si esta tienda envía a tu país. Compruébalo antes de pagar.">Tienda de ${esc(state.countries[best.country] || best.country)}</span>`);
  return { deal, flags };
}

function offersTable(item) {
  const home = state.settings.homeCountry;
  const rows = item.offers.map((o, i) => {
    const classes = [i === 0 && !o.unavailable && !o.suspicious ? 'is-best' : '', o.suspicious || o.unavailable ? 'is-dim' : ''].join(' ');
    const note = o.unavailable ? 'Agotado' : o.suspicious ? 'Precio sospechosamente bajo' : '';
    const ship = o.shippingEur === 0 && !o.shippingEstimated ? 'Gratis' : eur(o.shippingEur);
    const shipCell = o.shippingEstimated ? `<span class="est" title="La tienda no indica el envío hacia ${esc(state.countries[home])}: es una estimación de tus tarifas.">~${ship}</span>` : ship;
    return `<tr class="${classes}">
      <td><a href="${esc(safeUrl(o.url))}" target="_blank" rel="noopener noreferrer">${esc(o.store)}</a>
        <span class="sub">${esc(state.countries[o.country] || o.country)}${o.foreign ? ', fuera de tu país' : ''}${note ? `. ${note}` : ''}</span>
        <span class="title" title="${esc(o.title)}">${esc(o.title)}</span></td>
      <td class="num">${eur(o.priceEur)}${o.currency !== 'EUR' ? `<span class="sub">${esc(o.price.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))} ${esc(o.currency)}</span>` : ''}</td>
      <td class="num">${shipCell}</td>
      <td class="num">${o.importEur ? `<span class="est" title="IVA y aduana estimados por comprar fuera de la UE">~${eur(o.importEur)}</span>` : '—'}</td>
      <td class="num total">${eur(o.landedEur)}</td>
    </tr>`;
  }).join('');
  return `<div class="offers"><p class="offers__note">${item.offers.length} ofertas ordenadas por precio total hasta ${esc(state.countries[home] || home)}. El signo ~ marca estimaciones.</p><div class="offers__scroll"><table>
    <thead><tr><th scope="col">Tienda</th><th scope="col" class="num">Precio</th><th scope="col" class="num">Envío</th><th scope="col" class="num">Importación</th><th scope="col" class="num">Total</th></tr></thead>
    <tbody>${rows}</tbody></table></div></div>`;
}

function renderItems() {
  const host = $('#items');
  if (!state.items.length) {
    host.innerHTML = `<div class="empty"><h2>Aún no vigilas nada</h2><p>Escribe arriba qué quieres comprar y, si quieres, el precio al que te parecería una ganga. Ganga lo buscará en varias tiendas y países ${state.settings.scheduleTimes.length} veces al día y te avisará cuando baje.</p></div>`;
    return;
  }
  host.innerHTML = state.items.map((item) => {
    const best = item.best;
    const { deal, flags } = flagsFor(item);
    const open = expanded.has(item.id);
    const tag = best
      ? `<span class="tag ${deal ? 'tag--deal' : ''}" aria-label="Mejor precio total ${eur(best.landedEur)}">${esc(eur(best.landedEur).replace(/\s?€/, ''))}<small>€</small></span>`
      : `<span class="tag tag--empty">Sin precio</span>`;
    let where;
    if (best) {
      const ship = best.shippingEur === 0 && !best.shippingEstimated ? 'envío gratis' : `${best.shippingEstimated ? '~' : ''}${eur(best.shippingEur)} de envío`;
      where = `<p class="item__where">Mejor oferta en <strong>${esc(best.store)}</strong> (${esc(state.countries[best.country] || best.country)}).
        <span class="item__split">${eur(best.priceEur)} y ${ship}${best.importEur ? `, ~${eur(best.importEur)} de importación` : ''}.</span></p>`;
    } else {
      where = `<p class="item__where item__split">${item.lastCheckedAt ? 'No se ha encontrado ninguna oferta que coincida.' : 'Pendiente de la primera búsqueda.'}</p>`;
    }
    const meta = [];
    if (item.targetPrice) {
      meta.push(best ? (deal ? `Objetivo ${eur(item.targetPrice)}: lo superas por ${eur(item.targetPrice - best.landedEur)}.` : `Objetivo ${eur(item.targetPrice)}: te faltan ${eur(best.landedEur - item.targetPrice)}.`) : `Objetivo ${eur(item.targetPrice)}.`);
    }
    if (item.stats && item.stats.count) meta.push(`${item.stats.count} ofertas encontradas.`);
    if (item.lastCheckedAt) meta.push(`Revisado ${when(item.lastCheckedAt)}.`);
    const busy = state.status.running && state.status.progress.current === item.name;
    return `<article class="item" data-id="${esc(item.id)}">
      <div class="item__row">
        <div>
          <h2 class="item__name">${esc(item.name)}</h2>
          ${where}
          ${flags.length ? `<div class="item__flags">${flags.join('')}</div>` : ''}
          <p class="item__meta">${esc(meta.join(' '))}${busy ? ' Buscando ahora…' : ''}</p>
          ${item.lastError ? `<p class="item__problem">${esc(item.lastError)}</p>` : ''}
        </div>
        <div class="item__side">${tag}${sparkline(item)}</div>
      </div>
      <div class="item__actions">
        ${item.offers.length ? `<button class="btn btn--quiet" type="button" data-action="toggle" aria-expanded="${open}">${open ? 'Ocultar ofertas' : 'Ver ofertas'}</button>` : ''}
        <button class="btn btn--quiet" type="button" data-action="check"${state.status.running ? ' disabled' : ''}>Buscar ahora</button>
        <button class="btn btn--quiet" type="button" data-action="edit">Editar</button>
        <button class="btn btn--quiet btn--danger" type="button" data-action="delete">Eliminar</button>
      </div>
      ${open && item.offers.length ? offersTable(item) : ''}
    </article>`;
  }).join('');
}

// ---------- tooltip del historial ----------

const tip = $('#tip');
document.addEventListener('mousemove', (e) => {
  const svg = e.target.closest && e.target.closest('svg[data-spark]');
  if (!svg) { tip.hidden = true; return; }
  const item = state.items.find((i) => i.id === svg.dataset.spark);
  const hist = item.history.slice(-120);
  const box = svg.getBoundingClientRect();
  const frac = Math.min(1, Math.max(0, (e.clientX - box.left - 5) / (box.width - 10)));
  const i = Math.round(frac * (hist.length - 1));
  const h = hist[i];
  const P = 5, W = 190, H = 44;
  const values = hist.map((p) => p.landed);
  let lo = Math.min(...values), hi = Math.max(...values);
  if (item.targetPrice) { lo = Math.min(lo, item.targetPrice); hi = Math.max(hi, item.targetPrice); }
  if (hi === lo) { hi += 1; lo -= 1; }
  const dot = $('.spark__hover', svg);
  dot.setAttribute('cx', P + (i * (W - 2 * P)) / (hist.length - 1));
  dot.setAttribute('cy', H - P - ((h.landed - lo) / (hi - lo)) * (H - 2 * P));
  dot.hidden = false;
  const d = new Date(h.t);
  tip.textContent = `${d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })} ${d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}: ${eur(h.landed)} en ${h.store}`;
  tip.hidden = false;
  tip.style.left = `${Math.min(e.clientX + 12, window.innerWidth - tip.offsetWidth - 8)}px`;
  tip.style.top = `${e.clientY - 34}px`;
});
document.addEventListener('mouseout', (e) => {
  const svg = e.target.closest && e.target.closest('svg[data-spark]');
  if (svg && !svg.contains(e.relatedTarget)) { tip.hidden = true; $('.spark__hover', svg).hidden = true; }
});

// ---------- acciones sobre artículos ----------

$('#items').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;
  const id = btn.closest('.item').dataset.id;
  const item = state.items.find((i) => i.id === id);
  try {
    if (btn.dataset.action === 'toggle') {
      expanded.has(id) ? expanded.delete(id) : expanded.add(id);
      renderItems();
    } else if (btn.dataset.action === 'check') {
      await api(`/items/${id}/check`, { method: 'POST' });
      await load();
    } else if (btn.dataset.action === 'edit') {
      openItemDialog(item);
    } else if (btn.dataset.action === 'delete') {
      if (!confirm(`¿Dejar de vigilar «${item.name}»? Se borrará también su historial de precios.`)) return;
      await api(`/items/${id}`, { method: 'DELETE' });
      expanded.delete(id);
      toast('Artículo eliminado');
      await load();
    }
  } catch (err) {
    toast(err.message, true);
  }
});

$('#runAll').addEventListener('click', async () => {
  if (!state.items.length) return toast('Añade primero algo que vigilar');
  try {
    await api('/run', { method: 'POST' });
    await load();
  } catch (err) {
    toast(err.message, true);
  }
});

$('#banner').addEventListener('click', (e) => {
  if (e.target.closest('[data-open-settings]')) openSettings();
});

// ---------- alta rápida ----------

$('#quickAdd').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  const body = { name: form.name.value, targetPrice: form.targetPrice.value };
  try {
    await api('/items', { method: 'POST', body });
    form.reset();
    toast('Listo. Ganga ya está buscando las mejores ofertas.');
    await load();
  } catch (err) {
    toast(err.message, true);
  }
});

$('#moreOptions').addEventListener('click', () => {
  const form = $('#quickAdd');
  openItemDialog({ name: form.name.value, targetPrice: form.targetPrice.value || null });
});

// ---------- diálogo de artículo ----------

const itemDialog = $('#itemDialog');
const itemForm = $('#itemForm');

function chipList(container, name, options, selected) {
  container.innerHTML = options.map(([value, label]) => `<label class="chip"><input type="checkbox" name="${name}" value="${esc(value)}"${selected.includes(value) ? ' checked' : ''}><span>${esc(label)}</span></label>`).join('');
}

function openItemDialog(item = {}) {
  const f = itemForm;
  f.reset();
  f.id.value = item.id || '';
  $('#itemDialogTitle').textContent = item.id ? 'Editar artículo' : 'Nuevo artículo';
  f.name.value = item.name || '';
  f.query.value = item.query && item.query !== item.name ? item.query : '';
  f.targetPrice.value = item.targetPrice ?? '';
  f.minPrice.value = item.minPrice ?? '';
  f.condition.value = item.condition || 'new';
  f.mustInclude.value = (item.mustInclude || []).join(', ');
  f.exclude.value = (item.exclude || []).join(', ');
  f.filterAccessories.checked = item.filterAccessories !== false;
  f.urls.value = (item.urls || []).map((u) => u.url).join('\n');
  f.notes.value = item.notes || '';
  chipList($('#itemCountries'), 'countries', Object.entries(state.countries), item.countries || []);
  $('#itemError').hidden = true;
  itemDialog.showModal();
  f.name.focus();
}

itemForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = itemForm;
  const body = {
    name: f.name.value,
    query: f.query.value,
    targetPrice: f.targetPrice.value,
    minPrice: f.minPrice.value,
    condition: f.condition.value,
    mustInclude: f.mustInclude.value,
    exclude: f.exclude.value,
    filterAccessories: f.filterAccessories.checked,
    urls: f.urls.value.split('\n').map((l) => l.trim()).filter(Boolean).map((url) => ({ url })),
    countries: [...itemForm.querySelectorAll('input[name="countries"]:checked')].map((c) => c.value),
    notes: f.notes.value,
  };
  const btn = $('#itemSave');
  btn.disabled = true;
  try {
    if (f.id.value) await api(`/items/${f.id.value}`, { method: 'PUT', body });
    else await api('/items', { method: 'POST', body });
    itemDialog.close();
    $('#quickAdd').reset();
    toast(f.id.value ? 'Cambios guardados' : 'Listo. Ganga ya está buscando las mejores ofertas.');
    await load();
  } catch (err) {
    const el = $('#itemError');
    el.textContent = err.message;
    el.hidden = false;
  } finally {
    btn.disabled = false;
  }
});

// ---------- ajustes ----------

const settingsDialog = $('#settingsDialog');
const sf = $('#settingsForm');
const cleared = new Set();

function updateQuota() {
  const n = sf.querySelectorAll('#searchCountries input:checked').length;
  const items = Math.max(state.items.filter((i) => i.enabled).length, 1);
  const times = [...sf.querySelectorAll('#times input')].filter((i) => i.value).length;
  const perMonth = n * items * times * 30;
  $('#quota').textContent = `Con ${items} ${items === 1 ? 'artículo' : 'artículos'}, ${n} ${n === 1 ? 'país' : 'países'} y ${times} búsquedas al día, Serper gastaría unas ${perMonth.toLocaleString('es-ES')} consultas al mes (la cuenta gratuita incluye 2.500). Menos países o menos horas, menos gasto.`;
}

function openSettings() {
  const s = state.settings;
  cleared.clear();
  $('#homeCountry').innerHTML = Object.entries(state.countries).map(([c, n]) => `<option value="${c}">${esc(n)}</option>`).join('');
  sf.homeCountry.value = s.homeCountry;
  sf.homeZip.value = s.homeZip || '';
  for (const k of ['domestic', 'eu', 'nonEu', 'importPct']) sf[`ship_${k}`].value = s.shipping[k];
  chipList($('#searchCountries'), 'searchCountries', Object.entries(state.countries), s.searchCountries);
  chipList($('#ebayMarkets'), 'ebayMarketplaces', state.ebayMarketplaces.map((m) => [m, m.replace('EBAY_', 'eBay ')]), s.ebayMarketplaces);
  for (const key of ['serperApiKey', 'ebayClientId', 'ebayClientSecret', 'telegramBotToken']) {
    sf[key].value = '';
    sf[key].placeholder = s[`${key}Set`] ? 'Guardada. Escribe para cambiarla' : '';
  }
  sf.ntfyTopic.value = s.ntfyTopic || '';
  sf.ntfyServer.value = s.ntfyServer || '';
  sf.telegramChatId.value = s.telegramChatId || '';
  sf.demoMode.checked = s.demoMode;
  const times = [...s.scheduleTimes, '', ''].slice(0, Math.max(s.scheduleTimes.length + 1, 5));
  $('#times').innerHTML = times.map((t) => `<input type="time" value="${t}" aria-label="Hora de búsqueda">`).join('');
  $('#settingsError').hidden = true;
  updateQuota();
  settingsDialog.showModal();
}

$('#openSettings').addEventListener('click', openSettings);
sf.addEventListener('change', updateQuota);
sf.addEventListener('click', (e) => {
  const key = e.target.dataset && e.target.dataset.clear;
  if (!key) return;
  cleared.add(key);
  sf[key].value = '';
  sf[key].placeholder = 'Se quitará al guardar';
});

function collectSettings() {
  const body = {
    homeCountry: sf.homeCountry.value,
    homeZip: sf.homeZip.value,
    searchCountries: [...sf.querySelectorAll('input[name="searchCountries"]:checked')].map((c) => c.value),
    ebayMarketplaces: [...sf.querySelectorAll('input[name="ebayMarketplaces"]:checked')].map((c) => c.value),
    scheduleTimes: [...sf.querySelectorAll('#times input')].map((i) => i.value).filter(Boolean),
    ntfyTopic: sf.ntfyTopic.value,
    ntfyServer: sf.ntfyServer.value,
    telegramChatId: sf.telegramChatId.value,
    demoMode: sf.demoMode.checked,
    shipping: { domestic: sf.ship_domestic.value, eu: sf.ship_eu.value, nonEu: sf.ship_nonEu.value, importPct: sf.ship_importPct.value },
  };
  for (const key of ['serperApiKey', 'ebayClientId', 'ebayClientSecret', 'telegramBotToken']) {
    if (sf[key].value.trim()) body[key] = sf[key].value.trim();
    else if (cleared.has(key)) body[key] = '';
  }
  return body;
}

async function saveSettings() {
  await api('/settings', { method: 'PUT', body: collectSettings() });
  // Las claves ya están guardadas: se vacían los campos para no reenviarlas.
  cleared.clear();
  for (const key of ['serperApiKey', 'ebayClientId', 'ebayClientSecret', 'telegramBotToken']) sf[key].value = '';
}

sf.addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await saveSettings();
    settingsDialog.close();
    toast('Ajustes guardados');
    await load();
  } catch (err) {
    const el = $('#settingsError');
    el.textContent = err.message;
    el.hidden = false;
  }
});

$('#testNotify').addEventListener('click', async () => {
  // Guarda primero lo escrito para probar con los datos actuales, sin cerrar el diálogo.
  try {
    await saveSettings();
    await load();
    const r = await api('/test-notify', { method: 'POST' });
    toast(`Aviso enviado por ${r.sent.join(' y ')}`);
  } catch (err) {
    toast(err.message, true);
  }
});

document.addEventListener('click', (e) => {
  if (e.target.closest('[data-close]')) e.target.closest('dialog').close();
});

load();
