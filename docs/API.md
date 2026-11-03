# Website-Schnittstelle

Beides ist standardmäßig **aus**. Konfiguration in `config/factoryledgeranalytics-common.toml`.
Die Daten sind Bestandsänderungen (`STOCK_DELTA`), keine Brutto-Produktion. Messlücken werden nicht mitgezählt (`gapsInWindow`).

## A) Push (empfohlen für Kinetic Hosting: kein zusätzlicher Port nötig)
```toml
[push]
enabled = true
url = "https://deine-seite.de/fla/ingest.php"   # nur https (oder localhost)
secret = "<mindestens 16 zufällige Zeichen>"
intervalSeconds = 30
```
Der Server sendet alle `intervalSeconds` ein `POST` mit JSON-Body und den Headern
`X-FLA-Timestamp` und `X-FLA-Signature` = `hex(HMAC-SHA256(secret, timestamp + "." + body))`.
Deine Seite muss die Signatur prüfen und alte Zeitstempel (z. B. > 5 min) ablehnen. Beispiel Node.js:
```js
const crypto = require('crypto');
function valid(req, rawBody, secret) {
  const ts = req.headers['x-fla-timestamp'];
  if (Math.abs(Date.now() - Number(ts)) > 5 * 60_000) return false;
  const mac = crypto.createHmac('sha256', secret).update(ts + '.' + rawBody).digest('hex');
  const given = String(req.headers['x-fla-signature']);
  return given.length === mac.length && crypto.timingSafeEqual(Buffer.from(given), Buffer.from(mac));
}
```
Deine Seite speichert die Werte selbst, so entsteht dort auch eine Historie.

## B) Pull (eigener HTTP-Port)
```toml
[api]
enabled = true
bind = "127.0.0.1"      # hinter Reverse-Proxy; 0.0.0.0 nur wenn nötig
port = 8765
token = "<mindestens 16 zufällige Zeichen>"
allowedOrigin = "https://deine-seite.de"
```
`GET /api/v1/snapshot` und `GET /api/v1/health` mit Header `Authorization: Bearer <token>`.
Nur lesend, Rate-Limit pro Minute, Antworten auf 512 Messpunkte x 64 Items begrenzt.
Ohne TLS nicht öffentlich ins Internet stellen: Reverse-Proxy mit HTTPS davor oder Push verwenden.

## Antwortformat (Version 1)
`version, generatedAtMillis, windowSeconds, kind, points[]` mit je
`id, dimension, x, y, z, lastSampleMillis, compactingBlock, compactingMode, gapsInWindow, coveredSeconds, items{itemId: {stock, inTotal, outTotal, netTotal, inPerMinute, outPerMinute, netPerMinute}}`.
