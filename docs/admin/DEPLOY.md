# The admin role — what the server needs

An account is a USER or an ADMIN, never both. An admin plays like anyone
else and also has `/admin`: switching games on and off, choosing who sees
them, setting a player's scope (PUBLIC or BETA), and looking a player up.

## 1. Making someone an admin

By hand, in the database. No endpoint grants or takes away the role, so a
stolen session cannot make another admin.

```sql
UPDATE public.users SET role = 'ROLE_ADMIN' WHERE username = 'your-name';
-- and back:
UPDATE public.users SET role = 'ROLE_USER'  WHERE username = 'your-name';
```

It counts from the next request: `/admin/**` reads the role from the database
every time, not from the token. The profile's `role` — which the app uses to
show the admin button — follows on the next sign-in or token refresh.

Changeset `043-admin-role` widens the `users.role` check to allow
`ROLE_ADMIN`; before it has run, the UPDATE above is refused.

## 2. nginx

Next to the other `limit_req_zone` lines:

```nginx
# The admin page. A handful of people, a few clicks a minute.
limit_req_zone $limit_key zone=admin_limit:10m rate=60r/m;
```

In the `deck.bg` HTTPS server:

```nginx
location /api/admin/ {
    limit_req zone=admin_limit burst=20 nodelay;
    proxy_pass http://santase_backend:8080/admin/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Authorization $http_authorization;
}
```

Test and reload — a reload keeps live connections:

```bash
docker exec nginx_proxy nginx -t && docker exec nginx_proxy nginx -s reload
```

## 3. Checking it

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://deck.bg/api/admin/services
```

**401** is right without a token, and **403** with a player's token. A
`200 text/html` means the location fell through to the site.

## The API

| Call | Answer |
|---|---|
| `GET /admin/services` | every game: `{code, state, requiredScope}` |
| `PUT /admin/services/{code}` `{state, requiredScope}` | the game as it is now; the catalogue cache is cleared, so players see it on their next request |
| `GET /admin/players?username=&role=&scope=&page=&size=&sort=` | one page of accounts: `{players, page, size, total, totalPages}`. `username` matches part of the name in any case; `role` is USER or ADMIN; `scope` PUBLIC or BETA. Newest first by default; `sort` may be `createdAt` or `username` with `,asc` or `,desc` — anything else is 400. 8 a page, at most 50 |
| `GET /admin/players/{username}` | the account, and per game: record (with rating), the game in progress, the last 10 finished |
| `PUT /admin/players/{username}/scope` `{scope}` | the account as it is now |

Every change is logged with the admin who made it, the old value and the new.
The page asks before every change, naming the game or the player it touches.
An unknown game or player answers 404.
