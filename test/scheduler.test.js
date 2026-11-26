'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { Scheduler } = require('../src/scheduler');
const { DEFAULTS } = require('../src/settings');

function make(meta, items = [{ id: 1, enabled: true }]) {
  const calls = [];
  const db = { data: { meta, items }, save() {} };
  const engine = { running: false, runAll: async (t) => { calls.push(t); } };
  const scheduler = new Scheduler({ db, engine, getSettings: () => DEFAULTS });
  return { scheduler, calls, db };
}

test('primera ejecución: no recupera horas anteriores', () => {
  const { scheduler, calls, db } = make({});
  scheduler.tick();
  assert.equal(calls.length, 0);
  assert.ok(db.data.meta.lastScheduledAt);
});

test('recupera una (y solo una) búsqueda si estuvo apagado', () => {
  const { scheduler, calls } = make({ lastScheduledAt: new Date(Date.now() - 3 * 86400000).toISOString() });
  scheduler.tick();
  scheduler.tick(); // el segundo tick ya no debe lanzar otra
  assert.deepEqual(calls, ['scheduled']);
});

test('no repite si la última hora ya se ejecutó, ni sin artículos', () => {
  const recent = make({ lastScheduledAt: new Date(Date.now() + 1000).toISOString() });
  recent.scheduler.tick();
  assert.equal(recent.calls.length, 0);
  const empty = make({ lastScheduledAt: new Date(Date.now() - 3 * 86400000).toISOString() }, []);
  empty.scheduler.tick();
  assert.equal(empty.calls.length, 0);
});
