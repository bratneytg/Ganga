'use strict';

const fs = require('fs');
const path = require('path');

/** Base de datos mínima: un único JSON (data/db.json) con escritura atómica. */
class DB {
  constructor(file) {
    this.file = file;
    this.data = { settings: {}, items: [], meta: {} };
  }

  load() {
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    if (!fs.existsSync(this.file)) return;
    try {
      const parsed = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      this.data = { settings: {}, items: [], meta: {}, ...parsed };
    } catch (e) {
      const backup = `${this.file}.corrupto-${Date.now()}`;
      fs.copyFileSync(this.file, backup);
      console.error(`[db] db.json ilegible (${e.message}). Copia guardada en ${backup}`);
    }
  }

  save() {
    const tmp = `${this.file}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(this.data));
    fs.renameSync(tmp, this.file);
  }
}

module.exports = { DB };
