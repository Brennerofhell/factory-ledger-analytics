'use strict';
/**
 * Receiver + dashboard for Factory Ledger Analytics. No dependencies (Node >= 22.13, uses node:sqlite).
 *
 * Environment:
 *   FLA_SECRET             shared HMAC secret, same as push.secret in the mod config (>= 16 chars)
 *   FLA_DASHBOARD_PASSWORD password for the dashboard (>= 12 chars)
 *   FLA_BASE               URL prefix, default /fla
 *   PORT / BIND            default 8780 / 127.0.0.1
 *   DATA_DIR               default ./data
 *   RETENTION_DAYS         default 30
 *   TRUST_PROXY            "1" behind Caddy/nginx: use X-Forwarded-For / -Proto
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { verify } = require('./lib/verify');
const { Store } = require('./lib/store');

const MAX_BODY = 1024 * 1024;
const SESSION_MS = 12 * 3600 * 1000;
const MAX_RANGE_SEC = 40 * 86400;

function createApp(opts = {}) {
  const cfg = {
    secret: opts.secret ?? process.env.FLA_SECRET ?? '',
    password: opts.password ?? process.env.FLA_DASHBOARD_PASSWORD ?? '',
    base: (opts.base ?? process.env.FLA_BASE ?? '/fla').replace(/\/+$/, ''),
    dataDir: opts.dataDir ?? process.env.DATA_DIR ?? path.join(__dirname, 'data'),
    retentionDays: Number(opts.retentionDays ?? process.env.RETENTION_DAYS ?? 30),
    trustProxy: (opts.trustProxy ?? process.env.TRUST_PROXY) === '1' || opts.trustProxy === true,
    now: opts.now ?? (() => Date.now()),
  };
  const store = new Store(cfg.dataDir, cfg.retentionDays);
  const sessions = new Map(); // token -> expiry
  const loginFails = new Map(); // ip -> {n, until}
  const publicDir = path.join(__dirname, 'public');
  const files = { 'app.js': 'text/javascript; charset=utf-8', 'style.css': 'text/css; charset=utf-8' };

  const secHeaders = {
    'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Cache-Control': 'no-store',
  };

  function send(res, code, body, type = 'application/json; charset=utf-8', extra = {}) {
    res.writeHead(code, { 'Content-Type': type, ...secHeaders, ...extra });
    res.end(body);
  }
  const json = (res, code, obj, extra) => send(res, code, JSON.stringify(obj), undefined, extra);

  function clientIp(req) {
    if (cfg.trustProxy) {
      const x = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
      if (x) return x;
    }
    return req.socket.remoteAddress || '?';
  }
  const isHttps = req => cfg.trustProxy && req.headers['x-forwarded-proto'] === 'https';

  function readBody(req, limit) {
    return new Promise((resolve, reject) => {
      const chunks = []; let size = 0;
      req.on('data', c => {
        size += c.length;
        if (size > limit) { reject(Object.assign(new Error('too large'), { status: 413 })); req.destroy(); return; }
        chunks.push(c);
      });
      req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
      req.on('error', reject);
    });
  }

  function cookie(req, name) {
    for (const part of String(req.headers.cookie || '').split(';')) {
      const [k, ...v] = part.trim().split('=');
      if (k === name) return v.join('=');
    }
    return null;
  }
  function loggedIn(req) {
    const t = cookie(req, 'fla_sid');
    if (!t) return false;
    const exp = sessions.get(t);
    if (!exp || exp < cfg.now()) { sessions.delete(t); return false; }
    return true;
  }

  function safeEqual(a, b) {
    const ha = crypto.createHash('sha256').update(String(a)).digest();
    const hb = crypto.createHash('sha256').update(String(b)).digest();
    return crypto.timingSafeEqual(ha, hb);
  }

  function loginPage(msg) {
    return `<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Factory Ledger Analytics</title><link rel="stylesheet" href="${cfg.base}/style.css"></head><body class="login">
<form method="post" action="${cfg.base}/login"><h1>Factory Ledger Analytics</h1>
${msg ? `<p class="err">${msg}</p>` : ''}
<label>Passwort<input type="password" name="password" autocomplete="current-password" autofocus required></label>
<button type="submit">Anmelden</button></form></body></html>`;
  }
  const dashPage = `<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Factory Ledger Analytics</title><link rel="stylesheet" href="${cfg.base}/style.css"></head><body data-base="${cfg.base}">
<header><h1>Factory Ledger Analytics</h1><span id="status"></span>
<form method="post" action="${cfg.base}/logout"><button type="submit">Abmelden</button></form></header>
<main><aside><div id="ranges"></div><h2>Quellen</h2><div id="points"></div><h2>Items</h2><div id="items"></div>
<h2>Größe</h2><select id="metric"><option value="netPm">Net / min</option><option value="inPm">In / min</option><option value="outPm">Out / min</option><option value="stock">Bestand</option></select>
<label class="chk"><input type="checkbox" id="fit"> Zeitachse auf Daten zuschneiden</label>
<p class="note">Werte sind Bestandsänderungen, keine Brutto-Produktion. Lücken = nicht gemessen.</p></aside>
<section><canvas id="chart" width="1000" height="520"></canvas><div id="tip"></div><table id="stats"><thead><tr><th>Item</th><th>Letzter Wert</th><th>Min</th><th>Ø</th><th>Max</th><th>Gemessene Minuten</th></tr></thead><tbody></tbody></table><button id="csv" type="button">CSV herunterladen</button></section></main>
<script src="${cfg.base}/app.js"></script></body></html>`;

  async function handle(req, res) {
    const url = new URL(req.url, 'http://x');
    let p = url.pathname;
    if (p === cfg.base) { res.writeHead(301, { Location: cfg.base + '/' }); return res.end(); }
    if (!p.startsWith(cfg.base + '/')) return json(res, 404, { error: 'not found' });
    p = p.slice(cfg.base.length); // "/..."

    if (p === '/health' && req.method === 'GET') return json(res, 200, { status: 'ok' });

    if (p === '/ingest') {
      if (req.method !== 'POST') return json(res, 405, { error: 'method not allowed' }, { Allow: 'POST' });
      let raw;
      try { raw = await readBody(req, MAX_BODY); } catch (e) { return json(res, e.status || 400, { error: 'bad body' }); }
      const v = verify({ secret: cfg.secret, timestamp: req.headers['x-fla-timestamp'], signature: req.headers['x-fla-signature'], rawBody: raw, now: cfg.now() });
      if (!v.ok) return json(res, v.reason === 'server secret not configured' ? 503 : 401, { error: v.reason });
      let snap;
      try { snap = JSON.parse(raw); } catch { return json(res, 400, { error: 'invalid json' }); }
      try { const rows = store.ingest(snap, cfg.now()); res.writeHead(204, secHeaders); return res.end(); } catch (e) { return json(res, 422, { error: String(e.message).slice(0, 100) }); }
    }

    if (p === '/login' && req.method === 'POST') {
      const ip = clientIp(req);
      const f = loginFails.get(ip);
      if (f && f.until > cfg.now() && f.n >= 5) return send(res, 429, loginPage('Zu viele Versuche. Bitte später erneut.'), 'text/html; charset=utf-8');
      let raw; try { raw = await readBody(req, 4096); } catch { return json(res, 400, { error: 'bad body' }); }
      const pw = new URLSearchParams(raw).get('password') || '';
      if (cfg.password.length >= 12 && safeEqual(pw, cfg.password)) {
        loginFails.delete(ip);
        const token = crypto.randomBytes(32).toString('hex');
        sessions.set(token, cfg.now() + SESSION_MS);
        res.writeHead(303, { Location: cfg.base + '/', 'Set-Cookie': `fla_sid=${token}; HttpOnly; SameSite=Strict; Path=${cfg.base}; Max-Age=${SESSION_MS / 1000}${isHttps(req) ? '; Secure' : ''}`, ...secHeaders });
        return res.end();
      }
      const n = (f && f.until > cfg.now() ? f.n : 0) + 1;
      loginFails.set(ip, { n, until: cfg.now() + 15 * 60 * 1000 });
      return send(res, 401, loginPage('Falsches Passwort.'), 'text/html; charset=utf-8');
    }
    if (p === '/logout' && req.method === 'POST') {
      const t = cookie(req, 'fla_sid'); if (t) sessions.delete(t);
      res.writeHead(303, { Location: cfg.base + '/', 'Set-Cookie': `fla_sid=; HttpOnly; SameSite=Strict; Path=${cfg.base}; Max-Age=0`, ...secHeaders });
      return res.end();
    }

    if (req.method === 'GET' && (p === '/app.js' || p === '/style.css')) {
      const name = p.slice(1);
      return send(res, 200, fs.readFileSync(path.join(publicDir, name)), files[name]);
    }
    if (req.method === 'GET' && p === '/') {
      return send(res, 200, loggedIn(req) ? dashPage : loginPage(''), 'text/html; charset=utf-8');
    }

    if (p.startsWith('/api/')) {
      if (req.method !== 'GET') return json(res, 405, { error: 'method not allowed' });
      if (!loggedIn(req)) return json(res, 401, { error: 'unauthorized' });
      const nowSec = Math.floor(cfg.now() / 1000);
      let to = Math.min(Number(url.searchParams.get('to')) || nowSec, nowSec + 60);
      let from = Number(url.searchParams.get('from')) || to - 3600;
      from = Math.max(from, to - MAX_RANGE_SEC);
      if (!(from < to)) from = to - 60;
      const list = k => (url.searchParams.get(k) || '').split('|').filter(Boolean).slice(0, k === 'points' ? 8 : 48);
      if (p === '/api/points') return json(res, 200, { points: store.points(), serverNow: nowSec });
      if (p === '/api/items') return json(res, 200, { items: store.items(from, to, list('points')) });
      if (p === '/api/series') return json(res, 200, { from, to, ...store.series(from, to, list('points'), list('items')) });
    }
    return json(res, 404, { error: 'not found' });
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch(() => { if (!res.headersSent) json(res, 500, { error: 'internal error' }); else res.end(); });
  });
  server.requestTimeout = 15000;
  server.headersTimeout = 10000;

  const pruneTimer = setInterval(() => store.prune(cfg.now()), 6 * 3600 * 1000);
  pruneTimer.unref();
  store.prune(cfg.now());

  return { server, store, close: () => { clearInterval(pruneTimer); server.close(); store.close(); } };
}

module.exports = { createApp };

if (require.main === module) {
  const bind = process.env.BIND || '127.0.0.1';
  const port = Number(process.env.PORT || 8780);
  const app = createApp();
  if (!process.env.FLA_SECRET || process.env.FLA_SECRET.length < 16) console.warn('WARNING: FLA_SECRET missing or shorter than 16 chars - ingest is disabled (503).');
  if (!process.env.FLA_DASHBOARD_PASSWORD || process.env.FLA_DASHBOARD_PASSWORD.length < 12) console.warn('WARNING: FLA_DASHBOARD_PASSWORD missing or shorter than 12 chars - login is disabled.');
  app.server.listen(port, bind, () => console.log(`fla-receiver listening on ${bind}:${port}`));
  for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { app.close(); process.exit(0); });
}
