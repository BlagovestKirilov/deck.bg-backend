# Moving off AWS to Hetzner — migration runbook

Everything on the AWS account moves: deck.bg (frontend + backend), Amarok
(`/home/ubuntu/app` on the same EC2), the shared `nginx_proxy`, and every
database on the `wolf-tv-db` RDS instance. When it is done the AWS account
costs nothing and can be closed.

## Status

| Date | What |
|---|---|
| 2026-11-28 | **deck.bg moved.** Server `deck-1`, CX23 in Nuremberg (IPv4 in the Hetzner console; kept out of this repository so the origin stays hidden behind Cloudflare), Ubuntu 26.04, Docker 29. Postgres 17 holds `santase`, owned by role `deck`. Cloudflare's A records for `deck.bg` and `www` point here. The EC2's `santase_backend` is stopped; RDS `santase` is frozen at the cut-over and no longer written to. |
| open | Amarok (app, nginx blocks from `nginx.conf`, database `amarok`) |
| open | Off-site backups (Phase 3): only the local nightly dump and Hetzner's server backups so far |
| open | Monitoring (Phase 7), lock 80/443 to Cloudflare, close AWS (Phase 8) |

## Cost

| | AWS (before) | Hetzner (ordered) |
|---|---|---|
| Server | EC2 t3.small, 2 GB — $17.86 | **CX23**, 2 vCPU / 4 GB / 40 GB, Cost-Optimized — €6.59 |
| Database | RDS db.t4g.micro — $16.88 | Postgres in Docker on the same server — €0 |
| Public IPv4 | 2 addresses — $7.44 | 1 address — €0.60 (IPv6 free) |
| Disk | EBS — $1.79 | included |
| Backups | RDS automated | Hetzner server backups — €1.32 |
| Monitoring | CloudWatch basics | Grafana Cloud free tier — €0 (Phase 7) |
| Tax | $8.79 | included above (Hetzner's prices include 20% VAT) |
| **Total** | **~$53/month** | **€8.51/month** |

CX33 (8 GB) was out of stock in every location on the day; CX23 holds
deck.bg, Amarok and Postgres with each JVM capped (`mem_limit` plus
`-XX:MaxRAMPercentage`) and 2 GB of swap. Resizing stays within the location:
CX33 in Nuremberg when it is back in stock, or the Regular Performance CPX
line, which costs about three times as much.

**Downtime:** one cut-over window of about 15–30 minutes, in a quiet hour.
Everything before it runs next to production without touching it.

---

## Phase 0 — Inventory (read-only, on AWS)

Nothing here changes anything. Write the answers down; later phases use them.

### Found so far (2026-11-28)

**Containers on the EC2** (`docker ps -a`):

| Container | Image | Note |
|---|---|---|
| `santase_frontend` | `bkirilov/santase_frontend` | deck.bg |
| `santase_backend` | `bkirilov/santase_backend` | deck.bg, Java |
| `amarok_frontend` | `bkirilov/amarok_frontend` | Amarok |
| `amarok_backend` | `bkirilov/amarok_backend` | Amarok, **Java too**, published on `127.0.0.1:8080` |
| `nginx_proxy` | `nginx:1.28.1-alpine` | the only thing on 80/443 |
| `certbot` | `certbot/certbot` | **exited with an error 8 months ago**; see below |

Two JVMs plus Postgres is why the target is CX32 (8 GB) and not CX22.

**DNS and TLS for deck.bg are on Cloudflare.** The nameservers are
`*.ns.cloudflare.com`. The certificate players see is Cloudflare's own
(Google Trust Services, `deck.bg` and `*.deck.bg`), so the site is proxied
(orange cloud). This changes the plan for the better:

- **The switch is instant.** Changing the origin IP in Cloudflare takes
  effect at its edge in seconds. No TTL to lower, no players stuck on the
  old IP, and rollback is the same one change.
- **The broken certbot does not matter to players.** Between Cloudflare and
  the server, `nginx_proxy` presents some certificate of its own. Find out
  which one (below) and carry it over. If it is a Cloudflare **Origin CA**
  certificate, it is valid for years and needs no certbot at all.
- **The firewall can let only Cloudflare in** on 80/443 (Phase 1).
- AWS DNS (Route 53) is not involved; nothing to move.

**Domains:** both are registered at superhosting.bg, and both point their
nameservers at Cloudflare. In the Cloudflare zones:

| Record | Points to | Proxy |
|---|---|---|
| `deck.bg`, `www.deck.bg` A | the EC2's IP | proxied |
| `amarok.bg`, `www.amarok.bg` A | the EC2's IP | proxied |
| `deck.bg` MX | `mx2.bgdns.net` (superhosting mail) | — |
| `amarok.bg` CNAME/TXT | the email provider's DKIM/DMARC/tracking records | — |

So the switch is **four A records**, nothing else. Mail records stay as they
are, since mail is not on the EC2. The two `NS redirns*.bgdns.net` rows in the
deck.bg zone are leftovers from before Cloudflare. They are not in use and
can be left alone.

> **deck.bg expires on 21.12.2026, with auto-renewal off.** Renew it at
> superhosting.bg, or switch auto-renewal on, before anything else: a lapsed
> domain takes the site and the app down whatever the server. amarok.bg runs
> to 04.08.2027, also without auto-renewal.

**nginx_proxy's files** all live in one folder on the EC2,
`/home/ubuntu/proxy`. Copy the whole folder:

| On the EC2 | In the container |
|---|---|
| `proxy/nginx.conf` | `/etc/nginx/nginx.conf` |
| `proxy/cloudflare-certs/` | `/etc/ssl/cloudflare` |
| `proxy/certbot-certs/` | `/etc/letsencrypt` |
| `proxy/certbot-webroot/` | `/var/www/certbot` |

**Both sites already use Cloudflare Origin certificates** (`nginx.conf`):

| Lines | Site | Certificate |
|---|---|---|
| 67 / 77–81 | `deck.bg`, `www.deck.bg` | `/etc/ssl/cloudflare/origin.pem` |
| 227 / 237–241 | `amarok.bg`, `www.amarok.bg` | `/etc/ssl/cloudflare/amarok.bg.pem` |
| 219 | `menucraft.online` and `_` (any other host) | **not moving**: the site is no longer needed |

So **certbot is not needed at all**. The failed container, and the
`proxy/certbot-certs` and `proxy/certbot-webroot` folders, stay behind on
AWS. On Hetzner, `nginx_proxy` keeps only two mounts, `nginx.conf` and
`cloudflare-certs/`.

**Saved here:** [`nginx.aws.conf`](nginx.aws.conf) is the config running on
the EC2 today, as of 2026-11-28. [`nginx.conf`](nginx.conf) is the one for
Hetzner, with the changes below already made. Use that one, and check it on
the server before starting the proxy:

```bash
docker run --rm -v /home/ubuntu/proxy:/p:ro nginx:1.28.1-alpine \
  sh -c 'cp /p/nginx.conf /etc/nginx/nginx.conf && cp -r /p/cloudflare-certs /etc/ssl/cloudflare \
         && echo "127.0.0.1 santase_frontend santase_backend amarok_frontend" >> /etc/hosts && nginx -t'
```

**Changes to `nginx.conf` on the way over** (already made in `nginx.conf`):

- **Delete the `menucraft.online` server block** (line 219). It also carries
  `_`, the catch-all for any unknown host, so put a plain catch-all in its
  place that answers nothing:

  ```nginx
  server {
      listen 80 default_server;
      listen 443 ssl default_server;
      ssl_reject_handshake on;   # no certificate needed: unknown hosts are refused in the handshake
      return 444;
  }
  ```

- **Delete any `location /.well-known/acme-challenge/` block** that serves
  from `/var/www/certbot`. Nothing renews through it any more.
- **Check the end dates** of the two origin certificates. Origin
  certificates usually run for 15 years, but confirm:

  ```bash
  for f in /home/ubuntu/proxy/cloudflare-certs/*.pem; do echo "== $f"; openssl x509 -noout -subject -enddate -in "$f" 2>/dev/null; done
  ```

- In Cloudflare, `menucraft.online` (if it is a zone there) can be removed
  once nothing points at the EC2 any more.

**The database** (RDS `wolf-tv-db`, checked 2026-11-28): **PostgreSQL 15.17**.
The only extension is `plpgsql`, which is built in. Every database is owned
by `wolf_tv_user`.

| Database | Size | Fate |
|---|---|---|
| `santase` | 10 MB | **moves** (deck.bg), to its own role `deck` |
| `amarok` | 8 MB | **moves** later with Amarok, to its own role `amarok` |
| `menu_craft` | 10 MB | archived: one final dump kept off-site |
| `wolf_tv_db`, `video_service` | 8 MB each | archived: old projects, not in use |

The target is **Postgres 17**, the version the schema split was rehearsed on
(`docs/split/DEPLOY.md`). Use the Debian image `postgres:17`, not alpine: the
databases use `en_US.UTF-8` collation, which alpine's libc does not provide.
No changeset names a role, so restoring with `--no-owner --no-acl` into a
database owned by `deck` is safe.

**Still to check:** in the Cloudflare dashboard, for each domain, the
**SSL/TLS → Overview** mode. It should be *Full (strict)*.

**On the EC2:**

```bash
ls -la /home/ubuntu                       # every project directory
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}'
docker network ls
docker inspect nginx_proxy --format '{{json .Mounts}}'   # where its config and certs live
crontab -l; sudo crontab -l               # anything scheduled
df -h; du -sh /home/ubuntu/*
ls /home/ubuntu/*/.env                    # env files to carry over (do not print them)
```

**On the database** (from the EC2, which can reach RDS):

```sql
SELECT version();                                   -- Postgres major version → same or newer on Hetzner
\l                                                  -- every database: which app uses which
SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database;
\dx                                                 -- extensions in use, per database
\du                                                 -- roles
```

**DNS** — who answers for each domain (deck.bg and Amarok's):

```powershell
nslookup -type=NS deck.bg
```

`awsdns` in the answer means Route 53. Anything else means only the A records
change at that provider.

**Mail** — note the SMTP port in the `SANTASE_MAIL_PORT` GitHub secret.
Hetzner blocks outgoing ports **25 and 465** on new Cloud accounts. **587**
(STARTTLS) is open. If the port is 465, switch to 587 if the mail provider
offers it, or ask Hetzner support to unblock 465 (possible after the first
paid invoice).

**Then:** keep a list of every domain, container, env file, cron job and
database found. Anything on it that is not covered below gets its own step.

---

## Phase 1 — The Hetzner server

1. **Account and project.** Sign-up may ask for ID verification; allow a day.
   Create a project, e.g. `deck`.
2. **SSH key.** Add your public key to the project before creating the server.
3. **Server:** CX32, **Ubuntu 24.04**, location Nuremberg or Falkenstein
   (about 35 ms from Sofia), public IPv4 + IPv6, **Backups on**.
4. **Cloud Firewall**, attached to the server:
   - in: 22 (ideally from your own IP only)
   - in: 80 and 443 **from Cloudflare's IP ranges only**
     (https://www.cloudflare.com/ips/). Nobody can reach the server around
     Cloudflare. Add these rules only after the rehearsal, which reaches the
     server directly.
   - nothing else. Postgres is never published.
5. **First login as root**, then:

   ```bash
   apt update && apt -y full-upgrade
   adduser --disabled-password --gecos "" ubuntu     # same user as EC2, so every path in compose and the pipelines stays valid
   usermod -aG sudo ubuntu
   rsync -a /root/.ssh /home/ubuntu/ && chown -R ubuntu:ubuntu /home/ubuntu/.ssh
   echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/ubuntu
   # SSH: keys only, no root
   sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/; s/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
   systemctl restart ssh
   apt -y install unattended-upgrades fail2ban
   # Docker Engine + compose plugin (official repo: https://docs.docker.com/engine/install/ubuntu/)
   usermod -aG docker ubuntu
   # 2 GB swap as a cushion for the JVMs
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab
   timedatectl set-timezone Europe/Sofia
   ```

6. **Deploy key for GitHub Actions:** a new key pair just for deploys. Its
   public half goes into `/home/ubuntu/.ssh/authorized_keys`. Its private half
   becomes the new value of the `EC2_KEY` secret in Phase 6.

---

## Phase 2 — Postgres on the server

Its own compose project in `/home/ubuntu/db`, on its own Docker network that
each app joins.

```bash
docker network create db_network
docker network create app_santase_network   # the name deck.bg's compose expects (external)
```

`/home/ubuntu/db/docker-compose.yml`:

```yaml
name: db

services:
  postgres:
    image: postgres:17          # the same major as RDS (Phase 0), or newer — never older
    container_name: postgres
    restart: always
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/pg_superuser
      TZ: Europe/Sofia
    secrets: [pg_superuser]
    volumes:
      - /home/ubuntu/db/data:/var/lib/postgresql/data
    ports:
      - "127.0.0.1:5432:5432"   # reachable from the server itself (and an SSH tunnel), never from outside
    command: >
      postgres -c shared_buffers=512MB -c effective_cache_size=2GB
               -c max_connections=100 -c log_min_duration_statement=500
    networks: [db_network]

secrets:
  pg_superuser:
    file: /home/ubuntu/db/pg_superuser.txt   # chmod 600

networks:
  db_network:
    external: true
```

Then, inside it:

```sql
CREATE ROLE wolf_tv_user LOGIN PASSWORD '<new password>';
CREATE DATABASE <each database from Phase 0> OWNER wolf_tv_user;
-- plus any extension found by \dx, per database: CREATE EXTENSION ...;
```

A new password is a good moment: the old one has lived in AWS.

**deck.bg's compose** (`deck-backend/docker-compose.yml`) needs the backend on
`db_network` as well, as an external network next to `app_santase_network`.
The same change goes into Amarok's compose.

---

## Phase 3 — Backups, before any real data arrives

A nightly dump of each database, kept 7 days on the server and 30 days off it.

`/home/ubuntu/db/backup.sh`:

```bash
#!/bin/sh
set -e
stamp=$(date +%F)
dir=/home/ubuntu/backups
mkdir -p "$dir"
for db in <each database>; do
  docker exec postgres pg_dump -U postgres -Fc "$db" > "$dir/$db-$stamp.dump"
  docker exec -i postgres pg_restore --list < "$dir/$db-$stamp.dump" > /dev/null   # proves it reads
done
find "$dir" -name '*.dump' -mtime +7 -delete
rclone copy "$dir" offsite-crypt:deck-backups --max-age 25h   # rclone "crypt" remote: encrypted before it leaves
```

Cron as `ubuntu`: `15 4 * * * /home/ubuntu/db/backup.sh >> /home/ubuntu/backups/backup.log 2>&1`

- **Off-site:** a Hetzner Storage Box (another building, the same company) or
  Backblaze B2 (another company, the safer choice). Either way, through an
  rclone `crypt` remote.
- **Test a restore once**, into a throwaway container, and count rows. A
  backup never restored is a hope, not a backup.
- The Hetzner server backups (Phase 1) are a second, coarser layer: a whole
  image, daily, 7 kept.

---

## Phase 4 — Rehearsal (production untouched)

The whole stack comes up on Hetzner against a **copy** of the data, and is
tested without moving DNS.

1. **Dump from RDS** on the EC2, and copy it over:

   ```bash
   pg_dump -h <rds endpoint> -U wolf_tv_user -Fc --no-owner --no-acl -f <db>.dump <db>
   scp <db>.dump ubuntu@<hetzner ip>:/home/ubuntu/
   ```

   `--no-owner --no-acl` leaves RDS's own roles (`rdsadmin`, `rds_superuser`)
   out. Everything restored belongs to whoever restores it.

2. **Restore** on Hetzner, as `wolf_tv_user`:

   ```bash
   docker exec -i postgres pg_restore -U wolf_tv_user -d <db> --no-owner --no-acl < <db>.dump
   ```

3. **Compare** both sides. These must match:

   ```sql
   SELECT count(*), max(orderexecuted) FROM public.databasechangelog;  -- the same → Liquibase runs nothing new
   SELECT count(*) FROM public.users;
   SELECT count(*) FROM santase.game; SELECT count(*) FROM tabla.game; SELECT count(*) FROM belot.game;
   ```

4. **Carry over from the EC2:**
   - `nginx_proxy`: `/home/ubuntu/proxy/nginx.conf` and
     `/home/ubuntu/proxy/cloudflare-certs/` (`sudo tar czf proxy.tgz -C
     /home/ubuntu proxy/nginx.conf proxy/cloudflare-certs`, then `scp`). Keep
     the private keys off your own machine if you can: copy EC2 → Hetzner
     directly. Replace `nginx.conf` with `docs/hetzner/nginx.conf` from this
     repository, and run the `nginx -t` check from Phase 0. Then run `nginx_proxy` with those two
     mounts, ports 80/443 published, on `app_santase_network` and Amarok's
     network.
   - each project's `.env`, with the database URL changed to
     `jdbc:postgresql://postgres:5432/<db>` and the new password
   - Amarok's compose file and anything else on the Phase 0 list
   - `/home/ubuntu/santase/backend_logs` is not needed; it starts empty.

5. **Start it:** `db`, then each app (`docker compose up -d`), then `nginx_proxy`.
   The backend log must say Liquibase found **no changesets to run**.

6. **Test without DNS:** point the domains at the Hetzner IP in your own hosts
   file (`C:\Windows\System32\drivers\etc\hosts`).

   `nginx.conf` answers 403 to anything that does not come through
   Cloudflare, and a hosts-file visit does not. For the rehearsal only, add
   your own public IP to the `geo` block (`<your ip>/32 1;`), then
   `docker exec nginx_proxy nginx -s reload`. Take that line out again
   before the cut-over.

   Your browser will warn about the certificate: an Origin certificate is
   trusted by Cloudflare, not by browsers. Accept it for the test. Then:
   - sign in, play a santase, a tabla and a belot game; the socket connects
   - the friends list and a challenge work
   - an email arrives (registration or forgotten password); this is where a
     blocked SMTP port shows up
   - `/admin` opens
   - Amarok works the way it does today

   Remove the hosts entries afterwards. Data written now is thrown away at
   cut-over.

7. **Optional:** a k6 load test with a few hundred simulated players against
   the rehearsal, to know the limit before marketing finds it.

---

## Phase 5 — Cut-over (one quiet hour)

Nothing to prepare in DNS: deck.bg is proxied by Cloudflare, so a changed
origin IP takes effect in seconds.

On the night:

1. **Drain the games.** 10 minutes before, switch santase, tabla and belot off
   on `/admin`. Games in progress finish; no new ones start. Check:
   `SELECT count(*) FROM santase.game WHERE winner_id IS NULL;` (and tabla,
   belot) reaches 0, or close enough.
2. **Stop writes on AWS:** `docker compose stop backend` in
   `/home/ubuntu/santase`, and the same for Amarok's backend.
3. **Final dump** from RDS (Phase 4, step 1).
4. **Restore on Hetzner** into emptied databases:
   `DROP DATABASE <db>; CREATE DATABASE <db> OWNER wolf_tv_user;`, extensions
   again, then `pg_restore` as before.
5. **Compare** the counts (Phase 4, step 3). They must equal the final RDS numbers.
6. **Start the backends on Hetzner.** Liquibase: no changesets to run.
7. **Switch in Cloudflare:** DNS → the four A records (`deck.bg`,
   `www.deck.bg`, `amarok.bg`, `www.amarok.bg`) → change the EC2's IP to
   the Hetzner IPv4, proxy left on (orange cloud). It takes
   effect in seconds. Cloudflare serves IPv6 to players itself, so no AAAA
   record is needed.
8. **Smoke test** through the real domain. Use the phone app too: it calls
   `https://deck.bg/api`, so it needs no new release.
9. **Switch the games back on** in `/admin`.
10. **TLS mode:** both sites already use Origin certificates. Make sure each
    Cloudflare zone is on *Full (strict)* (SSL/TLS → Overview), so Cloudflare
    checks the certificate instead of trusting anything.
11. **Lock the firewall** to Cloudflare's ranges (Phase 1, step 4).

**Rollback** (from here until AWS is closed): the A records in Cloudflare back
to the EC2's IP, then start its backends. Anything written on Hetzner since the cut-over must first be dumped
and restored into RDS, or it is lost. The further you get from the cut-over,
the more that costs, so decide within the first day.

---

## Phase 6 — Pipelines

Both repositories deploy on push to `master` over SSH. Only secrets change,
not the workflows:

| GitHub secret (both repos, and Amarok's) | New value |
|---|---|
| `EC2_HOST` | the Hetzner IPv4 |
| `EC2_KEY` | the deploy key from Phase 1, step 6 |
| `DB_URL_SANTASE` | `jdbc:postgresql://postgres:5432/<db>` |
| `DB_PASSWORD` | the new password from Phase 2 |
| `SANTASE_MAIL_PORT` | 587, if Phase 0 found 465 |

The backend workflow rewrites `.env` on every deploy from these secrets, so a
wrong secret undoes a hand-edited `.env` on the next deploy. Change the
secrets before merging anything to `master` after the cut-over.

The first deploy after the cut-over is the test: it must pass the health check
in "Verify backend health".

**Later (cosmetic):** rename `EC2_HOST`/`EC2_KEY` to `SERVER_HOST`/`SERVER_KEY`,
and update the step names and comments that say EC2.

---

## Phase 7 — Monitoring (Grafana Cloud, free tier)

**Cost: €0.** Grafana Cloud's free tier is a permanent plan, not a trial. As
of writing it includes roughly:

- 10,000 active metric series
- 50 GB of logs a month
- 14 days of retention
- 3 users
- alerting

Check https://grafana.com/pricing/ when signing up. The limits change from
time to time.

**Will it fit?** Two Spring apps, the server, Postgres and container logs come
to a few thousand series, well under 10,000. What can blow the limit is a
metric with a label per URL or per user. Spring's `http.server.requests`
labels by URL *template* (`/santase/state`), not by the real path, so it is
safe. Never add a username as a metric label.

**Over the limit?** Grafana Cloud stops taking what does not fit; it does not
bill on the free plan unless a card is added and the plan is upgraded. The
fallback is self-hosted Prometheus + Grafana in Docker on the same server,
also €0, using about 300–500 MB of RAM.

**Setup:**

1. Sign up at grafana.com and create a stack in the EU region (Frankfurt).
   Under *Connections*, add the **Linux server** integration. It shows the
   one-line install command for **Grafana Alloy**, their agent, with the
   stack's URL and token already filled in.
2. **Spring side:** add `micrometer-registry-prometheus`, and expose
   `prometheus` on a management port that nginx never forwards:
   `management.server.port: 8081`, with
   `management.endpoints.web.exposure.include: health,prometheus`.
   `/actuator/health` on 8080 keeps working for the deploy check. This is a
   backend change, made in `dev` like any other. The same for Amarok's
   backend.
3. **On the server:** Grafana Alloy (installed in step 1) scraping:
   - the server itself (CPU, memory, disk)
   - Docker container logs
   - `postgres_exporter`
   - each backend's `:8081/actuator/prometheus` over the Docker network
   Grafana's ready-made dashboards cover these: "JVM (Micrometer)" and
   "Spring Boot", "Node Exporter Full", "PostgreSQL". Import them by ID in
   *Dashboards → New → Import*.
4. **Alerts** (e-mail or Telegram):
   - backend down
   - CPU above 80% for 10 minutes
   - memory above 85%
   - disk above 80%
   - newest backup older than 26 hours
5. **An outside uptime check** on `https://deck.bg` (Grafana synthetic
   monitoring, or UptimeRobot free). It sees what players see, including DNS and TLS.

---

## Phase 8 — Closing AWS (after 7–14 quiet days)

1. A last `pg_dump` of every database from RDS, kept off-site for good.
2. Delete the RDS instance. A final snapshot is optional: it costs storage
   every month, and the dump in step 1 already covers it.
3. Terminate the EC2, then delete what outlives it: EBS volumes and
   snapshots, Elastic IPs (release them), AMIs.
4. DNS stays in Cloudflare for both domains; nothing to move.
5. In the meantime, an AWS Budget alert at $1, so anything left running shows itself.
6. **Billing → Bills** the next month should show $0 in every region. Then
   close the account, or leave it empty.

---

## Risks and what covers them

| Risk | Cover |
|---|---|
| Liquibase runs changesets again | `databasechangelog` comes over with the data; counts compared before start |
| Postgres version mismatch | restore into the same major or newer, never older |
| Extensions missing | `\dx` in Phase 0, created before restore |
| Email stops (port 25/465 blocked) | found in the rehearsal; 587 or an unblock request |
| Hetzner ID check delays sign-up | done first, days ahead |
| Something on the EC2 nobody remembered | Phase 0 inventory: every container, cron job and env file is listed |
| Data loss on the new server | nightly encrypted off-site dumps plus Hetzner image backups, restore tested |
| Need to go back | AWS stays intact for 7–14 days; rollback is one IP change in Cloudflare (Phase 5) |
| Origin certificate expires | both sites already on Cloudflare Origin certificates; end dates checked in Phase 0 |
| Grafana free limits | a few thousand series in use against 10,000; no per-user labels; self-hosted fallback |
