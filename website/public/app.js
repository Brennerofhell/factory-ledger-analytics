'use strict';
(() => {
  const base = document.body.dataset.base;
  const COLORS = ['#f5a524', '#4cc9f0', '#80ed99', '#f72585', '#b794f4', '#ffd166', '#06d6a0', '#ef476f', '#8ecae6', '#c0c0c0'];
  const RANGES = [['1 h', 3600], ['6 h', 6 * 3600], ['24 h', 86400], ['7 d', 7 * 86400], ['30 d', 30 * 86400]];
  const state = load() || { range: 3600, points: [], items: [], metric: 'netPm' };
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
  const pretty = id => id.replace(/^[^:]+:/, '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
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
    $('status').textContent = last ? `Letzter Empfang: ${Math.max(0, r.serverNow - last)} s her` : 'Noch keine Daten empfangen';
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
  }

  function draw() {
    const W = canvas.width, H = canvas.height, L = 70, R = 16, T = 16, B = 34;
    const css = getComputedStyle(document.body);
    const text = css.getPropertyValue('--muted').trim() || '#888', line = css.getPropertyValue('--line').trim() || '#333';
    ctx.clearRect(0, 0, W, H);
    ctx.font = '14px system-ui, sans-serif';
    if (!data) return;
    const key = $('metric').value;
    const series = data.series.filter(s => state.items.includes(s.item));
    let min = 0, max = 0, any = false;
    for (const s of series) for (const v of s[key]) if (v != null) { min = Math.min(min, v); max = Math.max(max, v); any = true; }
    if (!any) { ctx.fillStyle = text; ctx.fillText('Keine Daten im gewählten Zeitraum', L + 20, H / 2); return; }
    if (max === min) max = min + 1;
    const pad = (max - min) * 0.08; max += pad; if (min < 0) min -= pad;
    const x = t => L + (t - data.from) / (data.to - data.from) * (W - L - R);
    const y = v => T + (1 - (v - min) / (max - min)) * (H - T - B);
    ctx.strokeStyle = line; ctx.fillStyle = text; ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const v = min + (max - min) * i / 4, py = y(v);
      ctx.beginPath(); ctx.moveTo(L, py); ctx.lineTo(W - R, py); ctx.stroke();
      ctx.fillText(fmt(v), 6, py + 4);
    }
    for (let i = 0; i <= 4; i++) {
      const t = data.from + (data.to - data.from) * i / 4;
      const d = new Date(t * 1000);
      const s = state.range > 2 * 86400 ? d.toLocaleDateString('de', { day: '2-digit', month: '2-digit' }) + ' ' + d.toLocaleTimeString('de', { hour: '2-digit', minute: '2-digit' }) : d.toLocaleTimeString('de', { hour: '2-digit', minute: '2-digit' });
      ctx.fillText(s, Math.min(x(t), W - 80), H - 10);
    }
    series.forEach(s => {
      const col = COLORS[items.indexOf(s.item) % COLORS.length];
      ctx.strokeStyle = col; ctx.lineWidth = 2; ctx.beginPath();
      let pen = false, prev = null;
      s.t.forEach((t, i) => {
        const v = s[key][i];
        // no value or a time gap bigger than 3 buckets = not measured: break the line, never draw it as 0
        if (v == null || (prev !== null && t - prev > data.bucketSec * 3)) pen = false;
        if (v == null) { prev = t; return; }
        pen ? ctx.lineTo(x(t), y(v)) : ctx.moveTo(x(t), y(v));
        pen = true; prev = t;
      });
      ctx.stroke();
    });
    if (min < 0 && max > 0) { ctx.strokeStyle = text; ctx.beginPath(); ctx.moveTo(L, y(0)); ctx.lineTo(W - R, y(0)); ctx.stroke(); }
    canvas._geo = { L, R, W, H, min, max, key, series };
  }

  canvas.addEventListener('mousemove', e => {
    const g = canvas._geo; if (!g || !data) return;
    const rect = canvas.getBoundingClientRect(), sx = canvas.width / rect.width;
    const px = (e.clientX - rect.left) * sx;
    const t = data.from + (px - g.L) / (g.W - g.L - g.R) * (data.to - data.from);
    if (t < data.from || t > data.to) { tip.style.display = 'none'; return; }
    const lines = [new Date(t * 1000).toLocaleString('de')];
    for (const s of g.series) {
      let best = -1, bd = Infinity;
      s.t.forEach((tt, i) => { const d = Math.abs(tt - t); if (d < bd) { bd = d; best = i; } });
      if (best >= 0 && bd <= data.bucketSec * 2) lines.push(`${pretty(s.item)}: ${fmt(s[g.key][best])}`);
    }
    tip.textContent = lines.join('\n'); tip.style.display = 'block';
    tip.style.left = Math.min(e.clientX - rect.left + 14, rect.width - 200) + 'px'; tip.style.top = (e.clientY - rect.top + 14) + 'px';
  });
  canvas.addEventListener('mouseleave', () => { tip.style.display = 'none'; });

  $('metric').value = state.metric;
  $('metric').onchange = () => { state.metric = $('metric').value; save(); draw(); };
  renderRanges();
  refresh().catch(() => {});
  setInterval(() => refresh().catch(() => {}), 30000);
})();
