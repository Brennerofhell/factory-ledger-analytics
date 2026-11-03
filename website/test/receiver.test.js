'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createApp } = require('../server');
const { sign } = require('../lib/verify');

const SECRET = 'test-secret-0123456789';
const PASSWORD = 'dashboard-pass-123';
let NOW = Date.UTC(2026, 10, 3, 18, 0, 0);

async function start(extra = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fla-test-'));
  const app = createApp({ secret: SECRET, password: PASSWORD, dataDir: dir, base: '/fla', now: () => NOW, ...extra });
  await new Promise(r => app.server.listen(0, '127.0.0.1', r));
  const url = `http://127.0.0.1:${app.server.address().port}`;
  return { app, url, dir, stop: () => { app.close(); fs.rmSync(dir, { recursive: true, force: true }); } };
}

function snapshot(points, at = NOW) {
  return { version: 1, generatedAtMillis: at, windowSeconds: 300, kind: 'STOCK_DELTA', note: 'x', points };
}
const point = (id, items, covered = 300) => ({ id, dimension: 'minecraft:overworld', x: 1, y: 2, z: 3, lastSampleMillis: NOW,
  compactingBlock: false, compactingMode: 'NORMALIZED', gapsInWindow: 0, coveredSeconds: covered, items });
const item = (stock, inPm, outPm) => ({ stock, inTotal: 0, outTotal: 0, netTotal: 0, inPerMinute: inPm, outPerMinute: outPm, netPerMinute: inPm - outPm });

async function push(url, body, { secret = SECRET, ts = String(NOW), sigOverride } = {}) {
  const raw = typeof body === 'string' ? body : JSON.stringify(body);
  return fetch(url + '/fla/ingest', { method: 'POST', body: raw,
    headers: { 'Content-Type': 'application/json', 'X-FLA-Timestamp': ts, 'X-FLA-Signature': sigOverride ?? sign(secret, ts, raw) } });
}
async function login(url) {
  const r = await fetch(url + '/fla/login', { method: 'POST', redirect: 'manual',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'password=' + encodeURIComponent(PASSWORD) });
  assert.equal(r.status, 303);
  return r.headers.get('set-cookie').split(';')[0];
}

test('valid push is stored and readable after login', async () => {
  const s = await start();
  try {
    const r = await push(s.url, snapshot([point('overworld@1,2,3', { 'minecraft:iron_nugget': item(100, 30, 10) })]));
    assert.equal(r.status, 204);
    const cookie = await login(s.url);
    const from = Math.floor(NOW / 1000) - 3600, to = Math.floor(NOW / 1000);
    const pts = await (await fetch(s.url + '/fla/api/points', { headers: { cookie } })).json();
    assert.equal(pts.points.length, 1);
    assert.equal(pts.points[0].id, 'overworld@1,2,3');
    const ser = await (await fetch(`${s.url}/fla/api/series?from=${from}&to=${to}&points=overworld@1,2,3&items=minecraft:iron_nugget`, { headers: { cookie } })).json();
    assert.equal(ser.series.length, 1);
    assert.equal(ser.series[0].netPm[0], 20);
    assert.equal(ser.series[0].stock[0], 100);
  } finally { s.stop(); }
});

test('rejects wrong signature, wrong secret, stale timestamp, bad json', async () => {
  const s = await start();
  try {
    const body = snapshot([point('p', { a: item(1, 1, 0) })]);
    assert.equal((await push(s.url, body, { secret: 'another-secret-0123456' })).status, 401);
    assert.equal((await push(s.url, body, { sigOverride: 'zz' })).status, 401);
    assert.equal((await push(s.url, body, { ts: String(NOW - 6 * 60 * 1000) })).status, 401);
    assert.equal((await push(s.url, '{not json')).status, 400);
    // a body changed after signing is rejected
    const raw = JSON.stringify(body);
    const r = await fetch(s.url + '/fla/ingest', { method: 'POST', body: raw + ' ',
      headers: { 'X-FLA-Timestamp': String(NOW), 'X-FLA-Signature': sign(SECRET, String(NOW), raw) } });
    assert.equal(r.status, 401);
  } finally { s.stop(); }
});

test('method, size and missing secret', async () => {
  const s = await start();
  try {
    assert.equal((await fetch(s.url + '/fla/ingest')).status, 405);
    const big = 'x'.repeat(1024 * 1024 + 10);
    const r = await fetch(s.url + '/fla/ingest', { method: 'POST', body: big, headers: { 'X-FLA-Timestamp': String(NOW), 'X-FLA-Signature': 'a'.repeat(64) } }).catch(() => ({ status: 413 }));
    assert.equal(r.status, 413);
  } finally { s.stop(); }
  const t = await start({ secret: '' });
  try { assert.equal((await push(t.url, snapshot([]))).status, 503); } finally { t.stop(); }
});

test('dashboard and api need a login; wrong password fails; five failures lock out', async () => {
  const s = await start();
  try {
    assert.equal((await fetch(s.url + '/fla/api/points')).status, 401);
    const page = await (await fetch(s.url + '/fla/')).text();
    assert.ok(page.includes('type="password"'));
    for (let i = 0; i < 5; i++) {
      const r = await fetch(s.url + '/fla/login', { method: 'POST', body: 'password=wrong', headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
      assert.equal(r.status, 401);
    }
    const locked = await fetch(s.url + '/fla/login', { method: 'POST', body: 'password=' + PASSWORD, headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
    assert.equal(locked.status, 429);
  } finally { s.stop(); }
  const t = await start();
  try {
    const cookie = await login(t.url);
    const dash = await (await fetch(t.url + '/fla/', { headers: { cookie } })).text();
    assert.ok(dash.includes('id="chart"'));
    assert.equal((await fetch(t.url + '/fla/app.js')).status, 200);
    const h = (await fetch(t.url + '/fla/')).headers;
    assert.ok(h.get('content-security-policy').includes("script-src 'self'"));
  } finally { t.stop(); }
});

test('same item from two sources adds up; unmeasured window stores no rates; minutes without data stay empty', async () => {
  const s = await start();
  try {
    await push(s.url, snapshot([
      point('a', { gold: item(10, 6, 0) }),
      point('b', { gold: item(5, 4, 0), wood: item(50, 0, 0) }, 0), // covered 0 -> rates unknown
    ]));
    NOW += 10 * 60 * 1000; // ten minutes later: a gap in between
    await push(s.url, snapshot([point('a', { gold: item(40, 6, 0) })]), { ts: String(NOW) });
    const cookie = await login(s.url);
    const to = Math.floor(NOW / 1000), from = to - 3600;
    const r = await (await fetch(`${s.url}/fla/api/series?from=${from}&to=${to}&points=a|b&items=gold|wood`, { headers: { cookie } })).json();
    const gold = r.series.find(x => x.item === 'gold');
    const wood = r.series.find(x => x.item === 'wood');
    assert.equal(gold.t.length, 2); // only two minutes have data: the gap has no rows
    assert.equal(gold.t[1] - gold.t[0], 600);
    assert.equal(gold.stock[0], 15); // 10 + 5 summed over both sources
    assert.equal(gold.netPm[0], 6); // b was unmeasured: only a's rate counts
    assert.equal(wood.netPm[0], null); // never shown as 0
    assert.equal(wood.stock[0], 50);
  } finally { s.stop(); }
});

test('invalid snapshots are refused', async () => {
  const s = await start();
  try {
    assert.equal((await push(s.url, { version: 2, points: [] })).status, 422);
    assert.equal((await push(s.url, snapshot([], NOW - 3600 * 1000))).status, 422);
  } finally { s.stop(); }
});
