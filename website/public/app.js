'use strict';
(() => {
  const base = document.body.dataset.base;
  const COLORS = ['#f5a524', '#4cc9f0', '#80ed99', '#f72585', '#b794f4', '#ffd166', '#06d6a0', '#ef476f', '#8ecae6', '#c0c0c0'];
  const RANGES = [['5 min', 300], ['15 min', 900], ['30 min', 1800], ['1 h', 3600], ['2 h', 7200], ['3 h', 10800], ['6 h', 6 * 3600], ['12 h', 12 * 3600], ['24 h', 86400], ['3 d', 3 * 86400], ['7 d', 7 * 86400], ['14 d', 14 * 86400], ['30 d', 30 * 86400], ['Alles', 40 * 86400]];
  const state = load() || { range: 3600, points: [], items: [], metric: 'netPm' };
  if (state.fit === undefined) state.fit = true;
  let points = [], items = [], data = null;

  const $ = id => document.getElementById(id);
  const canvas = $('chart'), ctx = canvas.getContext('2d'), tip = $('tip');

  function load() { try { return JSON.parse(localStorage.getItem('fla-ui')); } catch { return null; } }
  function save() { try { localStorage.setItem('fla-ui', JSON.stringify(state)); } catch { /* ignore */ } }
  async function api(path, params) {
    const q = new URLSearchParams(params).toString();
    const r = await fetch(`${base}/api/${path}?${q}`, { credentials: 'same-origin' });
    if (r.status === 401) { location.reload(); throw new Error('unauthorized'); }
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  }
  const basePretty = id => id.replace(/^[^:]+:/, '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  const pretty = id => {
    const n = basePretty(id);
    const clash = items.some(o => o !== id && basePretty(o) === n);
    return clash ? `${n} (${id.split(':')[0]})` : n;
  };
  const label = p => `${p.dimension.replace(/^minecraft:/, '')} ${p.x},${p.y},${p.z}`;
  const fmt = v => v == null ? '–' : Math.abs(v) >= 100 ? Math.round(v).toLocaleString('de') : (Math.round(v * 10) / 10).toLocaleString('de');

  function renderRanges() {
    const el = $('ranges'); el.textContent = '';
    for (const [name, sec] of RANGES) {
      const b = document.createElement('button'); b.textContent = name; b.className = state.range === sec ? 'on' : '';
      b.onclick = () => { state.range = sec; save(); renderRanges(); refresh(); };
      el.appendChild(b);
    }
  }
  function checklist(el, list, selected, text, colorOf, onChange) {
    el.textContent = '';
    list.forEach((v, i) => {
      const l = document.createElement('label'); l.className = 'chk';
      const c = document.createElement('input'); c.type = 'checkbox'; c.checked = selected.includes(v);
      c.onchange = () => { const s = new Set(selected); c.checked ? s.add(v) : s.delete(v); onChange([...s]); };
      l.appendChild(c);
      if (colorOf) { const sw = document.createElement('i'); sw.style.background = colorOf(i); l.appendChild(sw); }
      l.appendChild(document.createTextNode(text(v)));
      el.appendChild(l);
    });
    if (!list.length) el.textContent = '–';
  }

  async function loadPoints() {
    const r = await api('points', {});
    points = r.points;
    const ids = points.map(p => p.id);
    state.points = state.points.filter(id => ids.includes(id));
    if (!state.points.length) state.points = ids.slice(0, 8);
    const byId = new Map(points.map(p => [p.id, p]));
    checklist($('points'), ids, state.points, id => label(byId.get(id)), null, s => { state.points = s; save(); refresh(); });
    const last = Math.max(0, ...points.map(p => p.lastSeen));
    const age = Math.max(0, r.serverNow - last);
    const st = $('status');
    if (!last) { st.textContent = 'Noch keine Daten empfangen'; st.className = 'warn'; }
    else {
      st.textContent = `Letzter Empfang: ${age > 120 ? Math.round(age / 60) + ' min' : age + ' s'} her` + (age > 300 ? ' – der Server sendet nicht mehr?' : '');
      st.className = age > 300 ? 'warn' : '';
    }
  }
  async function refresh() {
    await loadPoints();
    const to = Math.floor(Date.now() / 1000), from = to - state.range;
    const it = await api('items', { from, to, points: state.points.join('|') });
    items = it.items;
    state.items = state.items.filter(i => items.includes(i));
    if (!state.items.length) state.items = items.slice(0, 6);
    checklist($('items'), items, state.items, pretty, i => COLORS[i % COLORS.length], s => { state.items = s; save(); refresh(); });
    save();
    data = await api('series', { from, to, points: state.points.join('|'), items: state.items.join('|') });
    draw();
    renderStats();
  }

  function niceStep(raw) {
    const pow = Math.pow(10, Math.floor(Math.log10(raw)));
    const f = raw / pow;
    return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * pow;
  }

  let hoverT = null; // time under the mouse, null = no hover

  // Crisp drawing on high-DPI screens: the canvas gets device pixels, all drawing code uses CSS pixels.
  function fitCanvas() {
    const dpr = window.devicePixelRatio || 1;
    const cssW = Math.max(320, canvas.parentElement.clientWidth - 16);
    const cssH = Math.round(Math.max(340, Math.min(580, cssW * 0.45)));
    const c = canvas._css;
    if (!c || c[0] !== cssW || c[1] !== cssH || c[2] !== dpr) {
      canvas.style.width = cssW + 'px'; canvas.style.height = cssH + 'px';
      canvas.width = Math.round(cssW * dpr); canvas.height = Math.round(cssH * dpr);
      canvas._css = [cssW, cssH, dpr];
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return [cssW, cssH];
  }
  const rgba = (hex, a) => { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`; };
  const colorOf = item => COLORS[items.indexOf(item) % COLORS.length];

  function draw() {
    const [W, H] = fitCanvas();
    const L = 64, R = 18, T = 14, B = 32;
    const css = getComputedStyle(document.body);
    const muted = css.getPropertyValue('--muted').trim() || '#888', line = css.getPropertyValue('--line').trim() || '#333';
    ctx.clearRect(0, 0, W, H);
    ctx.font = '12px system-ui, sans-serif';
    ctx.lineJoin = 'round'; ctx.lineCap = 'round';
    if (!data) return;
    const key = $('metric').value;
    const series = data.series.filter(s => state.items.includes(s.item));
    let v0 = data.from, v1 = data.to;
    if (state.fit) { // cut the empty part off: start shortly before the first measured value
      let tmin = Infinity;
      for (const s of series) for (let i = 0; i < s.t.length; i++) if (s.stock[i] != null || s[key][i] != null) tmin = Math.min(tmin, s.t[i]);
      if (tmin !== Infinity && tmin > v0) v0 = Math.max(v0, tmin - data.bucketSec * 2);
      if (v1 - v0 < 180) v0 = v1 - 180;
    }
    let min = 0, max = 0, any = false;
    for (const s of series) for (const v of s[key]) if (v != null) { min = Math.min(min, v); max = Math.max(max, v); any = true; }
    if (!any) {
      ctx.fillStyle = muted; ctx.textAlign = 'center';
      ctx.fillText('Keine Daten im gewählten Zeitraum', W / 2, H / 2);
      ctx.textAlign = 'left';
      canvas._geo = null; return;
    }
    // round axis values (1, 2, 5 x 10^n), always including 0 so the zero line sits on a grid line
    const step = niceStep((Math.max(max, 0) - Math.min(min, 0) || 1) / 4);
    min = Math.floor(Math.min(min, 0) / step) * step;
    max = Math.ceil(Math.max(max, 0) / step) * step;
    if (max === min) max = min + step;
    const x = t => L + (t - v0) / (v1 - v0) * (W - L - R);
    const y = v => T + (1 - (v - min) / (max - min)) * (H - T - B);
    const base = key === 'stock' ? y(min) : y(Math.max(min, Math.min(0, max)));

    // horizontal grid (dashed) with right-aligned value labels
    ctx.textAlign = 'right'; ctx.fillStyle = muted; ctx.lineWidth = 1; ctx.strokeStyle = line;
    ctx.setLineDash([3, 5]);
    for (let v = min; v <= max + step / 2; v += step) {
      const py = Math.round(y(v)) + 0.5;
      ctx.beginPath(); ctx.moveTo(L, py); ctx.lineTo(W - R, py); ctx.stroke();
      ctx.fillText(fmt(Math.abs(v) < step / 1e6 ? 0 : v), L - 10, py + 4);
    }
    ctx.setLineDash([]);

    // "not measured": hatched bands where no series has data between two points (never drawn as zero)
    const times = [...new Set(series.flatMap(s => s.t.filter((_, i) => s[key][i] != null || s.stock[i] != null)))].sort((a, b) => a - b);
    for (let i = 1; i < times.length; i++) {
      if (times[i] - times[i - 1] > data.bucketSec * 3) {
        const gx0 = Math.max(L, x(times[i - 1])), gx1 = Math.min(W - R, x(times[i]));
        if (gx1 - gx0 < 2) continue;
        ctx.save(); ctx.beginPath(); ctx.rect(gx0, T, gx1 - gx0, H - T - B); ctx.clip();
        ctx.fillStyle = rgba('#ff6b6b', 0.06); ctx.fillRect(gx0, T, gx1 - gx0, H - T - B);
        ctx.strokeStyle = rgba('#ff6b6b', 0.18); ctx.lineWidth = 1;
        for (let d = -(H); d < gx1 - gx0; d += 9) { ctx.beginPath(); ctx.moveTo(gx0 + d, H - B); ctx.lineTo(gx0 + d + (H - T - B), T); ctx.stroke(); }
        ctx.restore();
        if (gx1 - gx0 > 90) { ctx.fillStyle = rgba('#ff6b6b', 0.75); ctx.textAlign = 'center'; ctx.fillText('nicht gemessen', (gx0 + gx1) / 2, T + 16); }
      }
    }

    // zero line
    if (min < 0 && max > 0) { ctx.strokeStyle = muted; ctx.globalAlpha = 0.6; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(L, Math.round(y(0)) + 0.5); ctx.lineTo(W - R, Math.round(y(0)) + 0.5); ctx.stroke(); ctx.globalAlpha = 1; }

    // series: soft area + line; a missing value or a gap of more than 3 buckets breaks the line
    series.forEach(s => {
      const col = colorOf(s.item);
      const segs = []; let cur = null, prev = null;
      s.t.forEach((t, i) => {
        const v = s[key][i];
        if (v == null || (prev !== null && t - prev > data.bucketSec * 3)) cur = null;
        if (v == null) { prev = t; return; }
        if (!cur) { cur = []; segs.push(cur); }
        cur.push([x(t), y(v)]); prev = t;
      });
      const grad = ctx.createLinearGradient(0, T, 0, H - B);
      grad.addColorStop(0, rgba(col, 0.20)); grad.addColorStop(1, rgba(col, 0.01));
      for (const seg of segs) {
        if (seg.length === 1) { ctx.fillStyle = col; ctx.beginPath(); ctx.arc(seg[0][0], seg[0][1], 3, 0, 6.2832); ctx.fill(); continue; }
        ctx.beginPath(); ctx.moveTo(seg[0][0], base); seg.forEach(p => ctx.lineTo(p[0], p[1])); ctx.lineTo(seg[seg.length - 1][0], base); ctx.closePath();
        ctx.fillStyle = grad; ctx.fill();
        ctx.beginPath(); seg.forEach((p, i) => i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1]));
        ctx.strokeStyle = col; ctx.lineWidth = 2.2; ctx.stroke();
      }
    });

    // time axis with small ticks
    ctx.fillStyle = muted; ctx.strokeStyle = line; ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const t = v0 + (v1 - v0) * i / 4, d = new Date(t * 1000), px = x(t);
      const hm = d.toLocaleTimeString('de', { hour: '2-digit', minute: '2-digit' });
      const s = (v1 - v0) > 2 * 86400 ? d.toLocaleDateString('de', { day: '2-digit', month: '2-digit' }) + ' ' + hm : hm;
      ctx.textAlign = i === 0 ? 'left' : i === 4 ? 'right' : 'center';
      ctx.fillText(s, px, H - 10);
      ctx.beginPath(); ctx.moveTo(Math.round(px) + 0.5, H - B); ctx.lineTo(Math.round(px) + 0.5, H - B + 4); ctx.stroke();
    }
    ctx.textAlign = 'left';

    // hover: vertical line and a dot on every series at the nearest measured time
    let snap = null;
    if (hoverT !== null) {
      let bd = Infinity;
      for (const t of times) { const d = Math.abs(t - hoverT); if (d < bd) { bd = d; snap = t; } }
      if (snap !== null && bd <= data.bucketSec * 2) {
        const px = Math.round(x(snap)) + 0.5;
        ctx.strokeStyle = muted; ctx.globalAlpha = 0.7; ctx.setLineDash([4, 4]);
        ctx.beginPath(); ctx.moveTo(px, T); ctx.lineTo(px, H - B); ctx.stroke(); ctx.setLineDash([]); ctx.globalAlpha = 1;
        for (const s of series) {
          const i = s.t.indexOf(snap); if (i < 0 || s[key][i] == null) continue;
          const col = colorOf(s.item);
          ctx.fillStyle = css.getPropertyValue('--panel').trim() || '#1b1e27'; ctx.beginPath(); ctx.arc(x(snap), y(s[key][i]), 5, 0, 6.2832); ctx.fill();
          ctx.fillStyle = col; ctx.beginPath(); ctx.arc(x(snap), y(s[key][i]), 3.4, 0, 6.2832); ctx.fill();
        }
      } else snap = null;
    }
    canvas._geo = { L, R, T, B, W, H, v0, v1, key, series, snap };
  }

  function stats(s, key) {
    const v = s[key].filter(x => x != null);
    if (!v.length) return null;
    return { last: v[v.length - 1], min: Math.min(...v), avg: v.reduce((a, b) => a + b, 0) / v.length, max: Math.max(...v), n: v.length };
  }
  function renderStats() {
    const body = document.querySelector('#stats tbody'); body.textContent = '';
    if (!data) return;
    const key = $('metric').value;
    for (const s of data.series.filter(x => state.items.includes(x.item))) {
      const st = stats(s, key);
      const tr = document.createElement('tr');
      const name = document.createElement('td');
      const sw = document.createElement('i'); sw.style.background = COLORS[items.indexOf(s.item) % COLORS.length];
      name.appendChild(sw); name.appendChild(document.createTextNode(pretty(s.item)));
      tr.appendChild(name);
      const cells = st ? [st.last, st.min, st.avg, st.max] : [null, null, null, null];
      for (const c of cells) { const td = document.createElement('td'); td.textContent = fmt(c); tr.appendChild(td); }
      const n = document.createElement('td');
      n.textContent = st ? Math.round(st.n * data.bucketSec / 60).toLocaleString('de') : '0';
      tr.appendChild(n);
      body.appendChild(tr);
    }
  }
  // CSV of what the chart shows; leading = + - @ are neutralised so spreadsheets never run a value as a formula
  function csvCell(v) { const t = String(v ?? ''); return '"' + (/^[=+\-@]/.test(t) ? "'" + t : t).replace(/"/g, '""') + '"'; }
  $('csv').onclick = () => {
    if (!data) return;
    const rows = [['zeit_iso', 'item', 'net_pro_min', 'in_pro_min', 'out_pro_min', 'bestand']];
    for (const s of data.series) s.t.forEach((t, i) => rows.push([new Date(t * 1000).toISOString(), s.item, s.netPm[i], s.inPm[i], s.outPm[i], s.stock[i]]));
    const blob = new Blob([rows.map(r => r.map(csvCell).join(',')).join('\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'factory-ledger-analytics.csv';
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  };

  // hover: crosshair + a tooltip with one colored row per item (built with DOM nodes, never innerHTML)
  function showTip(e) {
    const g = canvas._geo, rect = canvas.getBoundingClientRect();
    if (!g || g.snap === null) { tip.style.display = 'none'; return; }
    const rows = [];
    for (const s of g.series) {
      const i = s.t.indexOf(g.snap);
      if (i >= 0 && s[g.key][i] != null) rows.push({ name: pretty(s.item), v: s[g.key][i], col: colorOf(s.item) });
    }
    rows.sort((a, b) => b.v - a.v);
    tip.textContent = '';
    const head = document.createElement('div'); head.className = 'tip-head';
    head.textContent = new Date(g.snap * 1000).toLocaleString('de');
    tip.appendChild(head);
    for (const r of rows.slice(0, 14)) {
      const row = document.createElement('div'); row.className = 'tip-row';
      const sw = document.createElement('i'); sw.style.background = r.col;
      const n = document.createElement('span'); n.textContent = r.name;
      const v = document.createElement('b'); v.textContent = fmt(r.v);
      row.append(sw, n, v); tip.appendChild(row);
    }
    if (rows.length > 14) { const m = document.createElement('div'); m.className = 'tip-more'; m.textContent = `+ ${rows.length - 14} weitere`; tip.appendChild(m); }
    tip.style.display = 'block';
    const px = e.clientX - rect.left, py = e.clientY - rect.top;
    const w = tip.offsetWidth, h = tip.offsetHeight;
    tip.style.left = (px + 16 + w > rect.width ? Math.max(0, px - 16 - w) : px + 16) + 'px';
    tip.style.top = Math.max(0, Math.min(py + 12, rect.height - h - 4)) + 'px';
  }
  canvas.addEventListener('mousemove', e => {
    const g = canvas._geo; if (!g) return;
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    hoverT = (px < g.L || px > g.W - g.R) ? null : g.v0 + (px - g.L) / (g.W - g.L - g.R) * (g.v1 - g.v0);
    draw(); showTip(e);
  });
  canvas.addEventListener('mouseleave', () => { hoverT = null; tip.style.display = 'none'; draw(); });
  window.addEventListener('resize', () => draw());

  $('fit').checked = state.fit;
  $('fit').onchange = () => { state.fit = $('fit').checked; save(); draw(); };
  $('metric').value = state.metric;
  $('metric').onchange = () => { state.metric = $('metric').value; save(); draw(); renderStats(); };
  renderRanges();
  refresh().catch(() => {});
  setInterval(() => refresh().catch(() => {}), 30000);
})();
