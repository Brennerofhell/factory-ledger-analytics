'use strict';
/**
 * Local demo: starts the receiver on 127.0.0.1:8792 with synthetic data (3 sources, several items, a 20 minute
 * outage) so the dashboard can be looked at without Minecraft. A random login password is generated on every start
 * and printed to the console (local only, nothing is stored).
 * Usage: node tools/seed-demo.js
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { createApp } = require('../server');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fla-demo-'));
const demoPassword = crypto.randomBytes(12).toString('hex');
const app = createApp({ secret: crypto.randomBytes(24).toString('hex'), password: demoPassword, dataDir: dir, retentionDays: 30 });
const now = Date.now();
const MIN = 60000;

const sources = [
  { id: 'minecraft:overworld@-19,65,-353', x: -19, y: 65, z: -353, items: { 'minecraft:iron_nugget': 30, 'minecraft:gold_nugget': 12 } },
  { id: 'minecraft:overworld@-20,64,-353', x: -20, y: 64, z: -353, items: { 'minecraft:oak_log': 45, 'minecraft:gold_nugget': 6 } },
  { id: 'minecraft:the_nether@4,70,12', x: 4, y: 70, z: 12, items: { 'minecraft:quartz': 20 } },
];
const stock = {};
for (let m = 180; m >= 0; m--) {
  const t = now - m * MIN;
  if (m <= 100 && m >= 80) continue; // the server was offline for 20 minutes: no push, no rows
  const points = sources.map(s => {
    const items = {};
    for (const [item, base] of Object.entries(s.items)) {
      const wave = Math.sin((t / MIN) / 17 + base) * base * 0.5;
      const net = Math.max(-base, base + wave + (m % 7 === 0 ? -base * 0.8 : 0));
      const key = s.id + item;
      stock[key] = (stock[key] ?? 1000) + net;
      items[item] = { stock: Math.round(stock[key]), inTotal: 0, outTotal: 0, netTotal: 0,
        inPerMinute: Math.max(net, 0) + 3, outPerMinute: Math.max(-net, 0) + 3, netPerMinute: net };
    }
    return { id: s.id, dimension: s.id.split('@')[0], x: s.x, y: s.y, z: s.z, lastSampleMillis: t,
      compactingBlock: true, compactingMode: 'NORMALIZED', gapsInWindow: 0, coveredSeconds: 300, items };
  });
  app.store.ingest({ version: 1, generatedAtMillis: t, windowSeconds: 300, kind: 'STOCK_DELTA', note: 'demo', points }, t);
}
const PORT = Number(process.env.DEMO_PORT || 8792);
app.server.listen(PORT, '127.0.0.1', () => console.log('demo dashboard: http://127.0.0.1:' + PORT + '/fla/  (password: ' + demoPassword + ')'));
setTimeout(() => { app.close(); process.exit(0); }, 20 * 60 * 1000); // demo stops itself after 20 minutes
process.on('SIGTERM', () => { app.close(); process.exit(0); });
