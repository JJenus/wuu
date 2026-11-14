# Phone / browser tracking: setup, HTTPS and dev testing

Lets a phone or browser act as a tracker for live demos: open a page, tap Start, watch movement, speed and alerts
in the platform. Design notes are in [PROTOCOLS.md](PROTOCOLS.md); this page is the operational how-to.

## Why HTTPS matters

Browsers expose the Geolocation API only in a **secure context**. The page is served by device-comm's phone listener
(default port **8891**) over plain HTTP, so something in front of it must add TLS, except on the exact hosts below.

| Where the page is opened from | Secure context? | Geolocation works? |
|---|---|---|
| `https://…` (any valid certificate) | yes | yes |
| `http://localhost:8891` or `http://127.0.0.1:8891` on the same machine | yes (browsers special-case loopback) | yes |
| `http://192.168.x.x:8891` (laptop's LAN address, opened on a phone) | **no** | **no** (the page shows "Location needs a secure (https) connection") |
| `http://your-domain` | **no** | **no** |

The page picks `wss://` automatically when loaded over https and `ws://` over http, so the proxy must forward
WebSocket upgrades on `/ingest`.

## Local development

**1. Enable the listener** (device-comm) and set the same secret on core and device-comm (32+ characters):

```bash
export PHONE_PAIRING_SECRET="$(openssl rand -hex 32)"      # use the same value for both services
export PHONE_INGEST_ENABLED=true                           # device-comm only
export PHONE_PUBLIC_URL=http://localhost:8891              # core only; makes the API return pairing_url
```

**2. Register a PHONE tracker** (any 10-15 digit `device_id`, assigned to a vehicle) and get a token:

```bash
curl -X POST $API/api/v1/trackers -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d '{"tracker_id":"PHONE-01","device_id":"2348000000001","vehicle_id":"VAN-01","protocol":"PHONE"}'
curl -X POST $API/api/v1/trackers/PHONE-01/pairing-token -H "Authorization: Bearer $JWT"
```

**3. Pick how to test:**

- **Same computer (easiest).** Open `pairing_url` (`http://localhost:8891/#t=…`) in Chrome or Firefox. Desktop
  browsers have no GPS, so fake movement: Chrome DevTools, three-dot menu, More tools, **Sensors**, Location: choose a
  city or enter coordinates, change them to simulate driving. Firefox: `about:config`, `geo.provider.testing = true`.
- **A real phone.** The phone cannot use `http://<laptop-ip>:8891`. Use an HTTPS tunnel to the listener:
  ```bash
  cloudflared tunnel --url http://localhost:8891     # or: ngrok http 8891
  ```
  Set `PHONE_PUBLIC_URL` (core) to the `https://….trycloudflare.com` (or ngrok) address it prints, restart core, request
  a new pairing token, and open the returned `pairing_url` on the phone. WebSockets work through both tunnels. The
  page is public while the tunnel runs, but sending data still needs a valid token; stop the tunnel when done.
- **Android Chrome without a tunnel (dev only).** Open `chrome://flags/#unsafely-treat-insecure-origin-as-secure`, add
  `http://<laptop-ip>:8891`, enable, relaunch. Not available on iOS.

## Production

Terminate TLS at your reverse proxy and forward to the listener. Example for nginx (the `Upgrade` headers and long
read timeout are what keep the WebSocket alive):

```nginx
server {
  listen 443 ssl;
  server_name track.example.com;
  # ssl_certificate / ssl_certificate_key ...
  location / {
    proxy_pass http://127.0.0.1:8891;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
  }
}
```

Caddy equivalent: `track.example.com { reverse_proxy 127.0.0.1:8891 }` (certificate and WebSocket handled automatically).
Set `PHONE_PUBLIC_URL=https://track.example.com` on core. Do not expose port 8891 directly to the internet.

## Configuration reference

| Variable | Service | Default | Meaning |
|---|---|---|---|
| `PHONE_PAIRING_SECRET` | both | empty | HMAC secret, 32+ bytes, must match. Empty disables pairing; device-comm refuses to start with the listener enabled and a short secret. |
| `PHONE_INGEST_ENABLED` | device-comm | `false` | Starts the phone listener. |
| `PHONE_INGEST_HOST` / `PHONE_INGEST_PORT` | device-comm | `0.0.0.0` / `8891` | Bind address. Use `127.0.0.1` when a proxy is on the same host. |
| `PHONE_MAX_ACCURACY_METERS` | device-comm | `100` | Fixes less precise than this are rejected. |
| `PHONE_READ_TIMEOUT_SECONDS` | device-comm | `120` | Idle connections are closed. |
| `PHONE_PAIRING_TTL_MINUTES` | core | `720` | Token lifetime (1 minute to 7 days). |
| `PHONE_PUBLIC_URL` | core | empty | Public origin of the listener; builds `pairing_url`. |

## Wire protocol (WebSocket `/ingest`)

1. Client sends `{"token":"v1...."}`. Server replies `{"type":"ready"}`, or closes the socket on a bad or expired token.
2. Client then sends one JSON fix per message, at most about once a second, max 1 KiB:
   `{"lat":6.5244,"lng":3.3792,"speed_kmh":12.5,"accuracy_m":8,"ts":1760000000000,"battery_pct":80}`
   Required: `lat`, `lng`, `speed_kmh`, `ts` (epoch milliseconds, within 1 hour back and 5 minutes forward of server
   time). Optional: `accuracy_m`, `battery_pct`. A `device_id` in the payload is ignored.
3. A rejected fix is answered with `{"type":"rejected","code":"PROTOCOL_PARSE_ERROR"}` and the connection stays open.

## Troubleshooting

| Symptom | Cause |
|---|---|
| "Location needs a secure (https) connection" | Page opened over plain HTTP on a non-localhost host. Use localhost, a tunnel or the proxy. |
| "Location permission was denied" | Allow location for the site in browser settings. |
| "Pairing failed. Ask for a new token." | Token expired, secrets differ between core and device-comm, or the tracker's device id changed. |
| 503 from `pairing-token` | `PHONE_PAIRING_SECRET` is not set on core. |
| 409 from `pairing-token` | The tracker's protocol is not `PHONE`. |
| Connects but no movement in the platform | Tracker has no vehicle assignment, or fixes are rejected (check device-comm logs for `Rejected phone frame`). |
| Socket drops after about a minute behind a proxy | Proxy lacks the WebSocket `Upgrade` headers or has a short read timeout. |
