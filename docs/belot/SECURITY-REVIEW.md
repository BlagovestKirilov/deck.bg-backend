# Belot against the OWASP Top 10:2025

Reviewed 27 September 2026, against the [2025 list](https://top10.owasp.org/2025/),
which is final. Scope is belot and everything belot touches; findings outside
belot are marked as such, because they live on `dev` and reach production
before belot does.

Four things were fixed in the same pass. Everything else is either already
right or is a server-side change written out in [`DEPLOY.md`](DEPLOY.md).

| | Category | Verdict |
|---|---|---|
| A01 | Broken Access Control | Clean |
| A02 | Security Misconfiguration | **No security headers at all** — fixed in DEPLOY.md, not yet applied |
| A03 | Software Supply Chain Failures | **`@master` action, no scanning** — action pinned, scanning still open |
| A04 | Cryptographic Failures | **Shuffle went through a 48-bit LCG** — fixed |
| A05 | Injection | Clean |
| A06 | Insecure Design | Rate limits written, not yet applied |
| A07 | Authentication Failures | Clean |
| A08 | Software or Data Integrity Failures | **Deploy skipped the tests** — fixed |
| A09 | Logging and Alerting Failures | Email addresses logged in full (santase) |
| A10 | Mishandling of Exceptional Conditions | **A double-tap answered 500** — fixed |

---

## A04 — the shuffle went through `java.util.Random` *(fixed)*

The worst of the four, and it undercut the thing belot advertises.

A table commits to a hash of a 256-bit seed before a card is dealt, and every
deal is derived from it. But the derivation did this:

```java
byte[] digest = mac.doFinal(("deal-" + dealNumber).getBytes(UTF_8));
long value = 0;
for (int i = 0; i < Long.BYTES; i++) {
    value = (value << 8) | (digest[i] & 0xFFL);
}
return new Random(value);          // ← 256 bits in, 48 bits out
```

`java.util.Random` is a 48-bit linear congruential generator. Eight bytes of a
SHA-256 HMAC went in and 48 bits of state came out, and that state is
recoverable from the generator's own output — an LCG is not a cipher. A player
sees their own eight cards immediately and all thirty-two at the end of the
deal, which is a great deal of output to work from.

It does not chain: each deal is a fresh HMAC, so recovering one deal's state
tells you nothing about the seed or the next deal. So this was not a break of
the whole game. It was a single deal reduced from 2^256 to 2^48, which is a
number a determined person can search.

Табла never had this problem — `TablaDiceService` takes its dice straight from
HMAC bytes with rejection sampling. Belot is now the same shape:

- `ShuffleStream` — an HMAC keystream, unbiased draws by rejection sampling,
  no LCG anywhere.
- `Dealing.shuffled` — Fisher–Yates written out, rather than
  `Collections.shuffle`.
- `BelotSeedService.shuffleFor` — eight HMAC blocks, numbered `deal-N/0` and
  up.

The second reason is the one a player would care about. "Provably fair" means
nothing unless somebody can actually check it, and a shuffle that depends on
`java.util.Random` and `Collections.shuffle` can only be replayed by
reimplementing two pieces of Java. Fisher–Yates over HMAC-SHA256 bytes can be
replayed in ten lines of anything.

## A10 — an ordinary double-tap answered 500 *(fixed)*

Tapping a card as the turn moves on, or bidding into an auction that has just
closed, came out of the engine as `IllegalArgumentException` or
`IllegalStateException`. Neither had a handler, so both reached the catch-all:
**HTTP 500, and a stack trace at error level**.

Wrong twice. The caller is told the server broke when the server did the right
thing, and a log full of expected races is a log nobody reads when something
does break. The client is behind the socket by a few hundred milliseconds all
the time; this was not an edge case.

Now `IllegalMoveException` → **409**, with the same words the optimistic-lock
handler already used, logged at info with no stack trace. The catch-all goes
back to meaning what it says.

## A08 — the deploy skipped the tests *(fixed)*

`mvn clean package -DskipTests`. Three hundred and ninety-four tests, none of
them run before a production deploy. A suite that does not run before a deploy
stops being true; the whole of it takes about a minute.

## A03 — a mutable action tag *(fixed)*, and no scanning *(open)*

`appleboy/scp-action@master` ran with the EC2 deploy key. `master` moves, and
whatever it moves to gets the key. Now `@v1.0.0`; pinning to the commit SHA is
stronger still and is the thing to do next.

Also added `permissions: contents: read` at the workflow level — the default
`GITHUB_TOKEN` is write-all on older repositories, and nothing in that job
writes to the repository.

**Still open:** no dependency scanning and no SBOM. Versions are all pinned and
current (Boot 4.1.1, jjwt 0.12.7, MapStruct 1.6.3, Caffeine, ShedLock 7.10.1),
but "current today" is not a control. This is the category that was promoted to
A03 in 2025, and the cheapest answer is Dependabot plus an OWASP
dependency-check step in the build.

## A02 — no security headers *(written, not applied)*

The production nginx sends none: no HSTS, no `X-Content-Type-Options`, no
frame-ancestors, no `Referrer-Policy`, no `Permissions-Policy`, and
`server_tokens` is on, so the version is in every answer and every error page.

The block is in [`DEPLOY.md`](DEPLOY.md) §3. A full Content-Security-Policy is
worth doing and is deliberately not in it: it needs report-only first, because
a wrong policy breaks the site silently.

Otherwise this category is in good order — actuator exposes `health` alone with
`show-details: never`, prod runs `ddl-auto: none`, and the two security filter
chains are bound to `dev` and `!dev` rather than to named profiles, so a third
profile cannot leave the application with no chain at all.

## A01 — clean

The thing that usually goes wrong here does not:

- **The seat comes from the token, never from the request.** `BelotBidRequest`
  and `BelotPlayRequest` carry a call and a card and nothing else. There is no
  seat, no game id and no username to tamper with, so there is no IDOR to have.
- **A player is sent their own view.** `BelotStateResponse` is built per seat,
  and `BelotViewTest` asserts no other seat's cards appear in it. Opponents
  carry a count, not cards.
- **`/belot/**` needs the role**, and searching also goes through
  `AvailabilityService`, so a PUBLIC account cannot join a BETA game.
- **STOMP.** The handshake is open because a browser cannot put a header on it;
  authentication happens on CONNECT, and `StompAuthChannelInterceptor` requires
  a destination ending in the caller's own name.

One deliberate gap, noted rather than fixed: `/belot/bid`, `/play` and `/state`
are not gated on availability, only `/search` is. Turning belot off should not
end the games already being played.

## A05 — clean

No native SQL anywhere in belot, no string-built queries, no dynamic JPQL.
Everything is Spring Data derived queries and entity graphs. The one place user
text reaches the database is a username, through a bound parameter.

## A06 — rate limits written, not applied

Belot's endpoints fall under no `limit_req` zone, which is the gap the previous
review noted for `/services`. Two zones are in [`DEPLOY.md`](DEPLOY.md) §2:
30r/m for matchmaking, 120r/m with a burst of 30 for play.

The design side is in better shape. Legal moves are computed server-side and
sent per seat, so the client offers only what the server would accept; hands
are derived from the seed and stored nowhere; the turn clock acts for an absent
player rather than letting one person hold three hostage.

## A07 — clean

Own-JWT auth, `/auth/**` rate-limited at 10r/m with a burst of 5, passwords
through `PasswordEncoder`, one live reset link at a time (`issueFor` ends the
previous one), tokens fingerprinted in logs rather than printed. Account
deletion is a POST behind a confirmation, not a GET on an email link — that was
fixed earlier, after link prefetchers started deleting accounts.

## A09 — one thing to change, and it is santase's

Log discipline is good: `TokenFingerprint.of(...)` everywhere a token would
otherwise be printed, no passwords, no seeds.

But `AuthService` logs **email addresses in full** — `FORGOT_PASSWORD_STARTED`,
`FORGOT_PASSWORD_EMAIL_SENT`, `FORGOT_PASSWORD_EMAIL_NOT_CONFIRMED`. That is
personal data sitting in application logs, including for people who only ever
typed an address into a forgot-password box. The username or a fingerprint says
as much for debugging.

Left alone here because it is santase code on `dev`, and this branch is belot's.

**Also open, and larger:** there is no alerting. Failures are logged and
nothing reads them. "Logging and *Alerting* Failures" is what the category was
renamed to in 2025, and the rename is the point — a log nobody is paged by is
evidence after the fact, not a control.

---

## What to do next, in order

1. **Reload nginx** with the headers and the belot rate limits from
   `DEPLOY.md`. Nothing here needs a deploy, and it is the largest remaining
   gap.
2. **Dependabot** on both repositories, and a dependency-check step in the
   build. Half an hour, and it closes A03.
3. **Stop logging email addresses** in `AuthService`, on `dev`.
4. **Pin the actions by SHA**, not by tag.
5. **A Content-Security-Policy**, report-only first.
