# SantaseService — how the layers are arranged

`bg.deck`: `controller`, `service`, `scheduler`, `repository`, `model`,
`security`, `config`, `constant`, `enums`, `exception`, `util`.

`bg.deck.belot` is a package apart, with its own `controller`, `service`,
`scheduler`, `repository`, `model`, `config` and `engine` — see **The belot**
**seam** below before writing anything in it.

## One repository, one service

**A repository is injected into exactly one class: the service that owns that
table. Everything else — other services, schedulers, controllers — goes through
that service.**

| Repository | Its service |
|---|---|
| `UserRepository` | `UserAccountService` |
| `PlayerRepository` | `PlayerService` |
| `ForgotPasswordRepository` | `ForgotPasswordService` |
| `EmailConfirmationRepository` | `EmailConfirmationService` |
| `UserDeletionRepository` | `UserDeletionService` |
| `GameRepository`, `GameStateRepository` | `GameUtilService` |
| `DeletedUserRepository` | `UserUtilService` |
| `AvailableServiceRepository` | `CacheService` |
| `BelotPlayerRepository` | `BelotPlayerService` |
| `BelotGameRepository` | `BelotTableService` |
| `BelotDealRepository` | `BelotDealService` |
| `BelotPlayerStatsRepository` | `BelotStatsService` |

Check it in one line — every repository must print `1`:

```bash
grep -rc "private final ForgotPasswordRepository " --include=*.java src/main/java | grep -v ":0"
```

### Why

Before this rule, seven classes reached into nine repositories and the same
table was written from four places. Each caller carried its own copy of the
rules about that table, so the copies drifted: the "only the newest link works"
sequence existed three times, and a scheduled job read three tables it had no
other business with.

### What the owner is for

The owner is not a pass-through. Logic that belongs to the table lives in it,
so a caller cannot get it half right:

- `issueFor(user)` ends the previous link and makes a new one — one call,
  because a second live link is a second way into the account.
- `reassignToDeletedUser(user, tombstone)` lets go of a user without losing the
  row, which is the whole of `UserUtilService.deleteUser` now.
- `requireByUsername(name)` is the log-and-throw that three services had each
  written out.

If a method on the owner reads like the repository method it wraps, ask whether
the caller's surrounding lines belong in the owner instead.

### When the owner is not the obvious service

`AvailabilityService` answers who may play what, so it looks like the owner
of `available_service`. The owner is `CacheService`, and the reason is
`@Cacheable`: Spring applies it with a proxy, and a bean calling its own
cached method never goes through that proxy. A cached read has to be called
from another bean, so it cannot sit beside the code that uses it.

```
AvailabilityService  → who may play what
CacheService         → owns AvailableServiceRepository, @Cacheable read
```

So `CacheService` is where a cached read lives, and the next one belongs
there too. Nothing evicts by hand: the entry expires, which is what lets an
`UPDATE` against the table take effect without a deploy.

### Adding a repository

Give it a service of its own in the same commit, and let nothing else inject
it. If two services both seem to need the table, one of them owns it and the
other asks.

### Keep the graph acyclic

An owner that everyone needs must depend on as little as possible.
`UserRepository` is owned by `UserAccountService`, which depends on nothing,
rather than by `UserService`: `UserService` already depends on
`GameUtilService`, which needs accounts, so that would have closed a circle and
the context would not start. Check before adding a dependency between services.

## One top-level type per file

**No nested classes, records, interfaces or enums.** A type that is worth
naming is worth its own file — a nested one is invisible to anyone scanning the
package, and it cannot be found by the name they would search for.

`SchedulingProperties` used to carry a nested `Job`; it is now
`JobSchedule.java`, beside it. Bound configuration nests perfectly well across
files: `SchedulingProperties` has a `JobSchedule` component and Spring binds
`deck.scheduling.expired-links.interval` straight into it.

Check it (`src/main/java` only — see below):

```bash
grep -rn "^    \(public\|private\|protected\|static\)\?.*\(class\|record\|interface\|enum\) [A-Z]" --include=*.java src/main/java
```

Tests are the exception, and only for JUnit's own idioms: a `@Nested` class
groups cases and means nothing outside its test, and a small fake or capturing
appender belongs beside the test that needs it. Neither is a type anyone would
go looking for.

## No unused imports

**An import nothing refers to is deleted in the commit that orphaned it.**
`javac` never complains about one, so they accumulate quietly and then lie: an
import of a repository in a service that no longer touches it reads like a
dependency that is still there.

The scan, over main and tests together:

```bash
python - src <<'EOF'
import io, pathlib, re, sys
for f in sorted(pathlib.Path(sys.argv[1]).rglob('*.java')):
    s = io.open(f, encoding='utf-8').read()
    body = re.sub(r'^import .*$', '', s, flags=re.M)
    for imported in re.findall(r'^import (?:static )?([\w.]+);', s, flags=re.M):
        name = imported.split('.')[-1]
        if name != '*' and not re.search(r'\b%s\b' % re.escape(name), body):
            print(f'{f}: {imported}')
EOF
```

It counts a name mentioned anywhere outside the import block as used, including
in a javadoc `{@link}` — removing one of those breaks the build, so the scan
errs towards keeping an import rather than dropping it.

Two things that hide an unused import:

- **A stale import survives a refactor silently.** The last one found had been
  dead since the JWT filter stopped writing its own 401.
- **A scripted edit can no-op.** Most files here use CRLF; a replacement
  written with `\n` matches nothing and reports success anyway. Match the
  file's endings, then re-read to confirm the edit landed.

## Records in `config`

**A type that only carries values is a record. A type that builds beans stays a
class.**

- Records: `EmailProperties`, `SchedulingProperties`, `JobSchedule` — read once
  at startup, never written to.
- Classes: `Config`, `SchedulingConfig`, `ExecutorConfig`, `DevCorsConfig`,
  `WebSocketConfig`, `WebSocketEventListener`, `TemplateLoader`.

The line is not taste. Spring proxies a `@Configuration` class with CGLIB,
which subclasses it, and **a record is final** — a record annotated
`@Configuration` fails at startup. So a `@ConfigurationProperties` record is
registered by the configuration that needs it, with
`@EnableConfigurationProperties(TheRecord.class)`, exactly as `Config` does for
`EmailProperties` and `SchedulingConfig` does for `SchedulingProperties`.

`TemplateLoader` is a class for the other reason: it holds no state at all, it
is behaviour, and a record with no components says nothing about it.

## The belot seam

Belot is built so it can be lifted out into a service of its own later. Three
rules keep that option open, and breaking any of them closes it quietly.

1. **Belot tables live in schema `belot`.** Every entity carries
   `@Table(schema = "belot")`.
2. **No cross-schema foreign key, ever.** Belot names a player by username,
   never by a `@ManyToOne User`.
3. **Belot code reads no `public` table; santase code reads no `belot` table.**
   The only thing that crosses is the authenticated username — on a request,
   from the security context; on a deletion, in a `UserDeleted` event.

The checks, before a belot commit:

```bash
grep -rn "bg.deck.model.User" --include=*.java src/main/java/bg/deck/belot   # empty
grep -rn "bg.deck.belot" --include=*.java src/main/java/bg/deck/service         # empty
```

Three things that look reasonable and are not:

- **Do not add `BELOT` to `GameType`.** `UserService.getProfile` loops over
  `GameType.values()` and `User.statsFor` throws on a missing row: adding a
  value 500s every existing profile until a backfill runs. Belot keeps
  `belot.player_stats` and `GET /belot/profile`, and the profile page asks both.
- **Do not widen `Game`.** Two seats, one state column per game type. Belot
  needs four seats and two teams, and has its own tables.
- **Do not reuse `GameInactivityService`.** It branches on `GameType` and works
  on `Game`. Belot has `BelotTurnScheduler`, built on the same pattern.

A fourth thing is true of the rating, and it is a choice rather than a rule:
**a belot result moves both partners equally.** `TeamElo` rates a pair as the
average of the two in it and hands the same delta to each. Only the K factor
is per player, because that is about how settled their own rating is. Do not
add a contribution term — the reason is written out in `TeamElo` and in
`docs/belot/BUILD.md`.

What is shared, deliberately: `JwtAuthenticationFilter`, `SecurityConfig`, the
STOMP transport, `WebSocketService`, the scheduler and its ShedLock,
`GlobalExceptionHandler` — so the error shape a client sees is identical — and
`RankLadder`, the one rating-to-rank ladder, so a Gold badge means the same
thing on both cards of the profile page. All of it is code; no table is
shared, which is what the third law is about.

`docs/belot/RULES.md` holds the rules of the game; `docs/belot/BUILD.md` the
plan and what is still open; `docs/belot/DEPLOY.md` what belot needs on the
server — the schema, its nginx zones and the security headers the site is
missing; `docs/belot/SECURITY-REVIEW.md` belot against the OWASP Top 10:2025.

## Everything else

The umbrella `CLAUDE.md` one directory up holds the cross-repo contract with
`santase-client`, the pipelines and the environment variables.
