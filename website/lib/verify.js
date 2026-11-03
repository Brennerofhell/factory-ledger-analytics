'use strict';
const crypto = require('node:crypto');

const MAX_SKEW_MS = 5 * 60 * 1000;

/** hex(HMAC-SHA256(secret, timestamp + "." + rawBody)) - exactly what the mod sends in X-FLA-Signature. */
function sign(secret, timestamp, rawBody) {
  return crypto.createHmac('sha256', secret).update(timestamp + '.' + rawBody).digest('hex');
}

/**
 * Checks timestamp freshness and signature over the RAW body text.
 * @returns {{ok: boolean, reason?: string}}
 */
function verify({ secret, timestamp, signature, rawBody, now = Date.now() }) {
  if (!secret || secret.length < 16) return { ok: false, reason: 'server secret not configured' };
  if (typeof timestamp !== 'string' || !/^\d{10,16}$/.test(timestamp)) return { ok: false, reason: 'bad timestamp' };
  if (Math.abs(now - Number(timestamp)) > MAX_SKEW_MS) return { ok: false, reason: 'stale timestamp' };
  if (typeof signature !== 'string' || !/^[0-9a-f]{64}$/.test(signature)) return { ok: false, reason: 'bad signature' };
  const want = Buffer.from(sign(secret, timestamp, rawBody), 'hex');
  const given = Buffer.from(signature, 'hex');
  if (want.length !== given.length || !crypto.timingSafeEqual(want, given)) return { ok: false, reason: 'bad signature' };
  return { ok: true };
}

module.exports = { sign, verify, MAX_SKEW_MS };
