# Belot — putting it live

Everything on this page happens on the server, not in the repository. It is
written down because the two things belot needs at deploy time are both easy to
forget and both only fail in production.

## 1. The schema has to exist before the app starts

Changeset `021-belot-schema.yaml` runs `CREATE SCHEMA IF NOT EXISTS belot`, so
Liquibase makes it on the first deploy that carries it — but only if the
database user is allowed to. Check before, not after:

```bash
docker exec -it <postgres-container> psql -U <user> -d <db> -c "\dn"
```

If `belot` is not listed and the deploy user cannot create a schema, make it by
hand once, as a superuser, and let Liquibase carry on from there:

```sql
CREATE SCHEMA IF NOT EXISTS belot AUTHORIZATION <deploy-user>;
```

Then confirm, after the deploy, that belot's tables landed in `belot` and that
**nothing new appeared in `public`** — the third law of the seam, checked
rather than trusted:

```sql
SELECT table_schema, table_name
  FROM information_schema.tables
 WHERE table_name LIKE '%belot%' OR table_schema = 'belot'
 ORDER BY table_schema, table_name;
```

## 2. Rate limits

Belot's endpoints inherit the gap the OWASP review noted: without a `limit_req`
of their own they fall under no zone at all. Two zones, because the two kinds
of request are nothing alike.

Next to the other `limit_req_zone` lines:

```nginx
# Matchmaking. Opening belot and coming back to the tab both call it, but a
# person cannot want a table sixty times a minute.
limit_req_zone $limit_key zone=belot_search_limit:10m rate=30r/m;

# Playing. A deal is eight cards a player plus the bidding, and a reconnect
# asks for the whole state again, so this has to be roomy enough that a fast
# table never feels it.
limit_req_zone $limit_key zone=belot_limit:10m rate=120r/m;
```

In the `deck.bg` HTTPS server, **the exact-match search block first** — nginx
prefers an exact match over a prefix, so the order in the file does not matter,
but keeping them together does:

```nginx
location = /api/belot/search {
    limit_req zone=belot_search_limit burst=10 nodelay;
    proxy_pass http://santase_backend:8080/belot/search;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Authorization $http_authorization;
}

location /api/belot/ {
    limit_req zone=belot_limit burst=30 nodelay;
    proxy_pass http://santase_backend:8080/belot/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Authorization $http_authorization;
}
```

`burst` with `nodelay` is what keeps a real table playable: a burst of thirty is
let through at once and only then is the rate enforced, so four people playing
quickly are never queued, while a script hammering `/belot/play` is.

The socket needs nothing. Belot pushes over the same `/ws-game` endpoint as
santase, on `/topic/belot/{username}`, and that location is already configured.

Test and reload — a reload keeps live connections, including the game sockets:

```bash
docker exec <nginx-container> nginx -t && docker exec <nginx-container> nginx -s reload
```

If `nginx -t` fails, nothing has changed yet.

## 3. Belot is off until the catalogue says otherwise

Changeset `023-belot-deal.yaml` inserts the `BELOT` row into
`available_service` as `ON` with scope `BETA`. Who that covers is
`AvailabilityService`'s business, and the answer is read through a cache that
expires after thirty seconds — so turning belot on or off in production is an
`UPDATE`, not a deploy:

```sql
UPDATE available_service SET state = 'OFF' WHERE code = 'BELOT';
```

Half a minute later the lobby stops offering it. Tables already being played
are not interrupted; the gate is on searching for a new one.

## Verifying from your own machine

```bash
curl -s -o /dev/null -w "search %{http_code}\n" https://deck.bg/api/belot/search
```

**401** is right — the request reached Spring and was refused for having no
token. A `200 text/html` means the location fell through to the SPA.
