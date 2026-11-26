'use strict';

/** Horas programadas de ayer, hoy y mañana, ordenadas. */
function slotsAround(now, times) {
  const out = [];
  for (const dayOffset of [-1, 0, 1]) {
    for (const t of times) {
      const [h, m] = t.split(':').map(Number);
      const d = new Date(now);
      d.setDate(d.getDate() + dayOffset);
      d.setHours(h, m, 0, 0);
      out.push(d);
    }
  }
  return out.sort((a, b) => a - b);
}

function slotInfo(now, times) {
  const slots = slotsAround(now, times);
  return {
    latest: slots.filter((s) => s <= now).pop() || null,
    next: slots.find((s) => s > now) || null,
  };
}

/**
 * Lanza la búsqueda en cada hora programada. Si el ordenador estaba apagado o dormido,
 * al volver recupera la última hora perdida (una sola búsqueda, no una por cada hueco).
 */
class Scheduler {
  constructor({ db, engine, getSettings }) {
    this.db = db;
    this.engine = engine;
    this.getSettings = getSettings;
    this.timer = null;
  }

  start() {
    this.tick();
    this.timer = setInterval(() => this.tick(), 30 * 1000);
    this.timer.unref?.();
  }

  stop() {
    clearInterval(this.timer);
  }

  nextRunAt() {
    return slotInfo(new Date(), this.getSettings().scheduleTimes).next;
  }

  tick() {
    const meta = this.db.data.meta;
    const { latest } = slotInfo(new Date(), this.getSettings().scheduleTimes);
    if (!latest || this.engine.running) return;
    if (!meta.lastScheduledAt) {
      // Primera vez: no se recuperan horas anteriores a la instalación.
      meta.lastScheduledAt = new Date().toISOString();
      this.db.save();
      return;
    }
    if (Date.parse(meta.lastScheduledAt) >= latest.getTime()) return;
    meta.lastScheduledAt = new Date().toISOString();
    this.db.save();
    if (!this.db.data.items.some((i) => i.enabled !== false)) return;
    console.log(`[scheduler] búsqueda programada (${latest.toLocaleTimeString()})`);
    this.engine.runAll('scheduled').catch((e) => console.error('[scheduler]', e.message));
  }
}

module.exports = { Scheduler, slotInfo, slotsAround };
