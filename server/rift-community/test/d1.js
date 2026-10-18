// A tiny stand-in for Cloudflare D1 over node:sqlite, enough for the worker's queries.
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';

class Statement {
  constructor(db, sql, params = []) {
    this.db = db;
    this.sql = sql;
    this.params = params;
  }

  bind(...params) {
    return new Statement(this.db, this.sql, params);
  }

  async first(column) {
    const row = this.db.prepare(this.sql).get(...this.params);
    if (!row) return null;
    return column ? row[column] : { ...row };
  }

  async all() {
    return { results: this.db.prepare(this.sql).all(...this.params).map((row) => ({ ...row })) };
  }

  async run() {
    const info = this.db.prepare(this.sql).run(...this.params);
    return { meta: { last_row_id: Number(info.lastInsertRowid), changes: Number(info.changes) } };
  }
}

export function createD1() {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  return { prepare: (sql) => new Statement(db, sql) };
}
