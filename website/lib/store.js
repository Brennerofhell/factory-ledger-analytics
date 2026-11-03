'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const MAX_POINTS = 512;
const MAX_ITEMS_PER_POINT = 64;

/**
 * Stores one value per (point, item, minute). The mod pushes a snapshot every ~30 s; the last snapshot of a
 * minute wins, so the database stays small. Minutes without a snapshot simply have no row = "not measured".
 */
class Store {
  constructor(dataDir, retentionDays = 30) {
    fs.mkdirSync(dataDir, { recursive: true });
    this.db = new DatabaseSync(path.join(dataDir, 'fla.sqlite'));
    this.retentionDays = retentionDays;
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS points (
        id TEXT PRIMARY KEY, dimension TEXT, x INTEGER, y INTEGER, z INTEGER, mode TEXT, last_seen INTEGER);
      CREATE TABLE IF NOT EXISTS samples (
        t INTEGER NOT NULL, point TEXT NOT NULL, item TEXT NOT NULL,
        stock REAL, in_pm REAL, out_pm REAL, net_pm REAL, covered INTEGER,
        PRIMARY KEY (point, item, t)) WITHOUT ROWID;
      CREATE INDEX IF NOT EXISTS samples_t ON samples (t);
    `);
    this.upPoint = this.db.prepare(`INSERT INTO points (id, dimension, x, y, z, mode, last_seen) VALUES (?,?,?,?,?,?,?)
      ON CONFLICT(id) DO UPDATE SET dimension=excluded.dimension, x=excluded.x, y=excluded.y, z=excluded.z,
      mode=excluded.mode, last_seen=excluded.last_seen`);
    this.upSample = this.db.prepare(`INSERT OR REPLACE INTO samples (t, point, item, stock, in_pm, out_pm, net_pm, covered)
      VALUES (?,?,?,?,?,?,?,?)`);
  }

  /** @returns {number} number of sample rows written; throws on an invalid snapshot */
  ingest(snapshot, receivedAtMs = Date.now()) {
    if (!snapshot || snapshot.version !== 1 || !Array.isArray(snapshot.points)) throw new Error('unsupported snapshot');
    const gen = Number(snapshot.generatedAtMillis);
    if (!Number.isFinite(gen) || Math.abs(gen - receivedAtMs) > 10 * 60 * 1000) throw new Error('bad generatedAtMillis');
    const minute = Math.floor(gen / 60000) * 60;
    let rows = 0;
    this.db.exec('BEGIN');
    try {
      for (const p of snapshot.points.slice(0, MAX_POINTS)) {
        if (typeof p.id !== 'string' || p.id.length > 200 || typeof p.items !== 'object' || p.items === null) continue;
        this.upPoint.run(p.id, String(p.dimension ?? '').slice(0, 100), int(p.x), int(p.y), int(p.z),
          String(p.compactingMode ?? '').slice(0, 20), Math.floor(gen / 1000));
        const covered = int(p.coveredSeconds);
        let n = 0;
        for (const [item, v] of Object.entries(p.items)) {
          if (++n > MAX_ITEMS_PER_POINT) break;
          if (typeof item !== 'string' || item.length > 200 || typeof v !== 'object' || v === null) continue;
          // no measured time in the window -> the rates are meaningless: keep the stock, store NULL rates
          const has = covered > 0;
          this.upSample.run(minute, p.id, item, num(v.stock), has ? num(v.inPerMinute) : null,
            has ? num(v.outPerMinute) : null, has ? num(v.netPerMinute) : null, covered);
          rows++;
        }
      }
      this.db.exec('COMMIT');
    } catch (e) {
      this.db.exec('ROLLBACK');
      throw e;
    }
    return rows;
  }

  points() {
    return this.db.prepare('SELECT id, dimension, x, y, z, mode, last_seen AS lastSeen FROM points ORDER BY id').all();
  }

  /** Items seen in the time range, so the UI only offers what exists. */
  items(fromSec, toSec, pointIds) {
    const ph = pointIds.map(() => '?').join(',');
    const sql = `SELECT DISTINCT item FROM samples WHERE t BETWEEN ? AND ?` + (pointIds.length ? ` AND point IN (${ph})` : '') + ' ORDER BY item';
    return this.db.prepare(sql).all(fromSec, toSec, ...pointIds).map(r => r.item);
  }

  /**
   * Time series per item, summed over the selected points (same as the mod: same item in two sources adds up).
   * Buckets are at least one minute wide and at most ~600 per query.
   */
  series(fromSec, toSec, pointIds, itemIds) {
    const span = Math.max(60, toSec - fromSec);
    const bucket = Math.max(60, Math.ceil(span / 600 / 60) * 60);
    const pp = pointIds.map(() => '?').join(',');
    const ip = itemIds.map(() => '?').join(',');
    const sql = `SELECT item, (t / ${bucket}) * ${bucket} AS b,
        SUM(stock) AS stock, SUM(in_pm) AS in_pm, SUM(out_pm) AS out_pm, SUM(net_pm) AS net_pm,
        COUNT(*) AS n, SUM(CASE WHEN net_pm IS NULL THEN 1 ELSE 0 END) AS unmeasured
      FROM samples WHERE t BETWEEN ? AND ?` + (pointIds.length ? ` AND point IN (${pp})` : ' AND 1=0')
      + (itemIds.length ? ` AND item IN (${ip})` : '') + ' GROUP BY item, b ORDER BY item, b';
    const rows = this.db.prepare(sql).all(fromSec, toSec, ...pointIds, ...itemIds);
    const map = new Map();
    for (const r of rows) {
      if (!map.has(r.item)) map.set(r.item, { item: r.item, t: [], stock: [], inPm: [], outPm: [], netPm: [] });
      const s = map.get(r.item);
      const measured = r.unmeasured < r.n; // at least one measured sample in this bucket
      s.t.push(r.b); s.stock.push(r.stock);
      s.inPm.push(measured ? r.in_pm : null); s.outPm.push(measured ? r.out_pm : null); s.netPm.push(measured ? r.net_pm : null);
    }
    return { bucketSec: bucket, series: [...map.values()] };
  }

  prune(nowMs = Date.now()) {
    const cutoff = Math.floor(nowMs / 1000) - this.retentionDays * 86400;
    this.db.prepare('DELETE FROM samples WHERE t < ?').run(cutoff);
    this.db.prepare('DELETE FROM points WHERE last_seen < ?').run(cutoff);
  }

  close() { this.db.close(); }
}

function num(v) { const n = Number(v); return Number.isFinite(n) ? n : null; }
function int(v) { const n = Math.trunc(Number(v)); return Number.isFinite(n) ? n : 0; }

module.exports = { Store };
