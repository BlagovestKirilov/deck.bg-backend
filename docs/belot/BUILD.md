# Belot — the build, step by step

Belot lives **inside SantaseService**, in its own database schema, with no
foreign key to anything in `public`. That is the whole design: one deployment to
run, one codebase to fix, and a seam clean enough that pulling belot out into its
own service later is mechanical rather than archaeological.

Rules and their open questions: [`RULES.md`](RULES.md). The engine cannot be
finished before those are answered.

---

## The three laws of the seam

Break any of these and the option to extract belot later quietly disappears.

1. **Belot tables live in schema `belot`.** Every entity carries
   `@Table(schema = "belot")`.
2. **No cross-schema foreign key, ever.** Belot stores `user_id` as a plain
   `UUID` column. No `@ManyToOne User`, no join to `public.users`.
3. **Belot code reads no `public` table; santase code reads no `belot` table.**
   The only thing that crosses the line is the authenticated username, taken
   from the SecurityContext.

A check to run before every belot commit:

```bash
grep -rn "schema = \"belot\"" --include=*.java src/main/java | wc -l   # every belot entity
grep -rn "bg.deck.model.User\b" --include=*.java src/main/java/bg/deck/belot   # must be empty
```

## Three landmines, already found

**1. Do not add `BELOT` to `GameType`.** `User.statsFor` throws when a row is
missing, and `UserService.getProfile` loops over `GameType.values()`. Adding a
value 500s every existing user's profile until a backfill runs. Belot keeps its
own `belot.player_stats` and its own `GET /belot/profile`; the profile page calls
both and merges client-side.

**2. Do not widen `Game`.** It has exactly two seats (`firstPlayer`,
`secondPlayer`) and one state column per game type. Belot needs four seats and
two teams — it gets its own tables.

**3. Do not reuse `GameInactivityService`.** It branches on `GameType` and works
on `Game`. Belot needs its own, built on the same pattern (`@Scheduled` +
ShedLock, see `ExpiredLinkScheduler`).

## What is reused as-is

| Reused | Note |
|---|---|
| `JwtAuthenticationFilter`, `SecurityConfig` | add `.requestMatchers("/belot/**").hasRole(USER)` |
| STOMP transport | topic `/topic/belot/{username}` — already passes `StompAuthChannelInterceptor.authorise`, which requires the destination to end in the caller's name |
| `WebSocketService` | per-player push |
| Scheduler + ShedLock | proven in production as of `fb39c4b` |
| `GlobalExceptionHandler`, `ErrorResponse`, `Constants` | error shape stays identical for the client |
| Tabla's seed-commitment dealing | provably fair shuffling, same approach |

## House rules that apply (from `../../CLAUDE.md`)

- One repository per service; everything else goes through that service.
- One top-level type per file — no nested classes or records.
- `model/{request,response,dto,event}` hold records only.
- No unused imports; strings in `constant/`.
- New request/response records must be added to `WireFormatSnapshotTest`.

---

# M0 — the seam (½–1 day)

- [x] `db/changelog/changes/021-belot-schema.yaml` — `CREATE SCHEMA IF NOT EXISTS belot;`, included from the master changelog.
- [x] Package `bg.deck.belot` with `model`, `engine`, `service`, `controller`, `repository`.
- [x] One entity, `belot.player(id, username UNIQUE, created_at, updated_at)`, `@Table(schema = "belot")`. Keyed by **username**: it is the token's subject, fixed at registration, with no rename path — so belot never needs the user id and never reads `public.users`.
- [x] Provisioning: on first authenticated belot request, insert the row from the SecurityContext. Model it on `UserProvisioningFilter` in the Keycloak stash.
- [x] `GET /belot/ping` behind `hasRole(USER)`.
- [x] `SecurityConfig`: `/belot/**` requires the role.

**Checkpoint.** App starts against dev Postgres · Liquibase applies 019 · the
table exists in `belot` and **nothing new appears in `public`**:

```sql
select table_schema, table_name from information_schema.tables
 where table_name like '%belot%' or table_schema = 'belot';
```

# M1 — rules, then the engine (3–4 weeks — the bulk)

**Answer the OPEN questions in `RULES.md` first.** Especially §3 (no-trump
totals), §8 (rounding) and §7 (declaration comparison): each one silently
changes every score in the game.

Pure classes. **No Spring, no database, no entities** — `bg.deck.belot.engine`
depends on nothing but the JDK, which is what makes it testable and portable.

- [x] `Suit`, `Rank`, `Card`, `Deck` (32 cards).
- [x] `Contract` — pass · ♣ ♦ ♥ ♠ · no trumps · all trumps; ordering per RULES §5.
- [x] `CardOrder` — trump vs plain ordering (RULES §2).
- [x] `CardPoints` — per-contract values (RULES §3).
- [x] `Bidding` — turn order, legal raises, contra/recontra, three-pass end, all-pass redeal.
- [x] `LegalMoves` — follow suit · trump when the opponent holds the trick · overtrump · partner-winning exemption (RULES §6).
- [x] `Seat`, `Play`, `Trick`, `TrickResolver` — counter-clockwise seating, partnerships, who holds a trick.
- [x] `Declarations` — detection, comparison, cancellation, belote, no-trump prohibition (RULES §7).
- [x] `DealScorer` — contract made / вътре / висящи, contra multipliers, rounding (RULES §8). Capot and the last trick are added by the caller of it, so the points it receives are the finished ones.
- [x] `GameScorer` — 151, the no-capot extra deal, `Team` and `GameVerdict` (RULES §9).

**Tests, written alongside:**

- [x] One test per row of RULES §10 that the answered rules allow.
- [x] A table-driven test per open question, named after it, so a wrong answer surfaces as a failing test rather than a player's complaint.
- [x] **Self-play fuzz**, modelled on `TablaEngineTest`: 2000 random deals played to the end, asserting
  - 32 cards conserved, no card played twice,
  - every move legal by `LegalMoves`,
  - deal totals land **exactly** on 162 / 258 / 260 (RULES §3),
  - a scored deal's two halves sum to the total plus bonuses.

**Checkpoint. Do not start M2 until the fuzz test is green.** A scoring bug found
after launch reads to players as cheating, and it is the one thing they will not
forgive.

# M2 — a table over STOMP (1 week)

- [x] Tables: `belot.game`, `belot.seat` (022), `belot.deal`, `belot.bid` (023), `belot.play` (024). All `@Table(schema = "belot")`. No trick table — a trick is four plays in order — and no declaration table, for the reason below.
- [x] Matchmaking for four: `POST /belot/search` — the oldest table short of players, or a new one; seats handed out in playing order so partners sit opposite.
- [x] Turn order counter-clockwise; the deal moves one seat along each hand, thrown-in hands included.
- [x] Per-player views — one `BelotStateResponse` per seat on `/topic/belot/{gameId}/{username}`, carrying that seat’s hand and the calls it may make. `BelotViewTest` checks no other seat’s cards appear in it.
- [x] Provably fair dealing: the hash is committed when the table opens and travels in every view; hands are derived from seed + deal number and stored nowhere. The reveal at the end comes with the game’s finish.
- [x] Inactivity: own scheduler (`BelotTurnScheduler`, ShedLock), own timeout
      (`deck.belot.turn-timeout`, 45s). **Answered: neither.** A dropped player
      does not forfeit — their partner did nothing wrong — and the table does not
      pause, or one person could hold three hostage. The table takes the least
      consequential legal action for them: a pass while bidding, the first legal
      card while playing. The deadline is sent to the client as a moment, so the
      clock a player watches is the clock they are judged by.
- [ ] Reconnect: rejoining mid-deal restores the full view. `GET /belot/state` already re-sends one seat’s view; what is missing is the play in progress, which does not exist yet.
- [x] The play itself: `belot.play` holds one row per card; tricks, hands and whose turn it is are rebuilt from it. `POST /belot/play` enforces the turn and `LegalMoves`; the last card scores the deal onto the sheet and deals the next hand.
- [x] Declarations are detected from the hands rather than announced — they are in the cards, and the cards are in the seed. **Simplification worth revisiting:** at a real table an unannounced declaration does not count.
- [x] Each deal records what it came to (card points, game points, made/вътре/висящи), which is the score sheet M3 needs.
- [x] `BelotStateResponse` and `BelotBidRequest` pinned in `WireFormatSnapshotTest`. The rest join them as they are written.

**Checkpoint, the automated half.** `BelotTableEndToEndTest` seats four,
bids a contract, plays all thirty-two cards through `BelotService` against a
real database, and checks the hand was scored and the next one dealt — plus
the thrown-in redeal and the "searching twice keeps your seat" case. What it
cannot do is the socket, so:

**Checkpoint.** Two browsers × two tabs play a full deal end to end; killing one
tab and reopening it restores that seat's hand exactly.

# M3 — client (1.5–2 weeks)

- [ ] `src/api/belotService.ts`, `src/types/belot.types.ts` (hand-mirrored, no codegen).
- [ ] Route `/play/belot`; a Belot card in `GameHub`.
- [ ] Four-hand layout — **the hard part on a phone**; santase's two-hand layout gives nothing to reuse. Partner opposite, opponents left and right, only your own cards face up.
- [ ] Bidding panel: pass · four suits · no trumps · all trumps · contra.
- [ ] Declaration prompts on the first trick.
- [ ] Score sheet: per-deal rows, running totals, the 151 line.
- [ ] Design tokens only — no hardcoded hex, px or durations (`theme.css`).
- [ ] ≥44px touch targets, visible focus rings, reduced-motion respected.

# M4 — stats and profile (2–3 days)

- [ ] `belot.player_stats(user_id, wins, losses, elo, …)`.
- [ ] **❓ Team Elo:** does a 2v2 result move both partners equally, or by individual contribution?
- [ ] `GET /belot/profile`.
- [ ] `ProfilePage` merges santase stats with belot stats client-side.

# M5 — loose ends that are easy to forget

- [x] **Account deletion.** `UserUtilService` publishes `UserDeleted` after the deletion commits; `BelotAccountListener` forgets the `belot.player` row and renames every seat that account sat in. Seats are kept, so a finished game can still name four people and nobody loses their record because an opponent left. The event carries a username and nothing else — the same thing that crosses the seam on every request.
- [ ] Rate limiting — belot's matchmaking endpoints inherit the gap noted in the OWASP review.
- [ ] `CLAUDE.md`: add belot's package and the three laws.
- [ ] Prod `ddl-auto: none` means **Liquibase must create every belot table** — dev's `update` will hide a missing changeset until deploy.
- [ ] Check the belot schema exists in production before the first deploy that needs it.

---

## Open questions, collected

Rules (see `RULES.md`): 1 no-trump totals · 2 suit order · 3 contra and further
bidding · 4 all-trumps obligation with a partner winning · 5 fours vs sequences ·
6 belote independence · 7 which four wins · 8 declarations in suit contracts ·
9 rounding · 10 both teams over 151 · 11 all-pass and the capot rule.

Build: dropped player — forfeit or pause? · team Elo — equal or individual?
