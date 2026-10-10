# Moving santase and табла into their own schemas — deploy runbook

Santase and табла leave `public` for schemas of their own, `santase` and
`tabla`, the way belot already lives in `belot`. The move is split into
phases. Each phase is its own release, and each can be rolled back on its own.

| Phase | Changesets | What moves | Database change |
|---|---|---|---|
| A | — | Code only: common imports no game package | none |
| B | 032–036 | Records: `santase.player_stats`, `tabla.player_stats` | copy, old table kept |
| C1 | — | Entities split over the existing tables | none |
| C2 | 037–040 | Game tables into `santase.*`, `tabla.*` | copy, old tables kept |
| D | 041 | Drop the old `public` tables | irreversible |

Nothing is deployed until every phase is built. Phases still ship as
separate releases, in order, because each one is the rollback point for the
next.

## Pre-flight (read-only, on prod)

Checked on 2026-10-11:

| Check | Result | Meaning |
|---|---|---|
| `current_user` | `wolf_tv_user` | no role named after a new schema |
| `current_schema()` | `public` | |
| `search_path` | `"$user", public` | safe: `$user` names no schema. Liquibase is pinned to `public` anyway (`application.yml`) |
| may create schemas | `true` | changeset 033 can create `santase` and `tabla` itself |
| longest username | 16 | fits the `VARCHAR(20)` the new tables use |

Run these again just before each phase is deployed:

```sql
SELECT current_user, current_schema(), current_setting('search_path');
SELECT has_database_privilege(current_user, current_database(), 'CREATE') AS can_create_schema,
       (SELECT max(length(username)) FROM users) AS longest_username;
SELECT game_type, count(*) FROM user_game_stats GROUP BY game_type;   -- phase B: rows to copy
```

## Deploying a phase

1. **Tag the image that is running now.** The workflow pushes only `:latest`, so there is nothing else to roll back to:
   `docker tag <user>/santase_backend:latest <user>/santase_backend:pre-<phase>`
2. **Back up**, and prove the dump reads:
   `pg_dump -Fc -f before-<phase>.dump <db>`, then `pg_restore --list before-<phase>.dump`. Copy it off the box.
3. **Drain the games.** About 10 minutes before a quiet hour, switch SANTASE and TABLA off in `available_service`. This stops new games; the ones already being played finish. Check with `SELECT game_type, count(*) FROM game WHERE winner_id IS NULL GROUP BY 1`.
4. **One instance only.** Nothing else may connect to prod while Liquibase runs.
5. **Deploy**, and watch the log for each changeset. A copy that does not match its source raises an exception, and that changeset's transaction is rolled back in full.
6. **Post-checks** (phase B):
   ```sql
   SELECT (SELECT count(*) FROM user_game_stats WHERE game_type = 'SANTASE') AS old_santase,
          (SELECT count(*) FROM santase.player_stats) AS new_santase,
          (SELECT count(*) FROM user_game_stats WHERE game_type = 'TABLA') AS old_tabla,
          (SELECT count(*) FROM tabla.player_stats) AS new_tabla;
   ```
   Then smoke-test with two test accounts: the profile shows the same santase and табла record as before; a game finished moves both records; a throwaway account deletes.
7. **Switch the games back on.**

## Rolling back

Use Liquibase, to the phase's tag, with the changelog of the release being
rolled back. Its rollback blocks copy back what was played meanwhile before
they drop anything:

```
liquibase rollback before-stats-split      # phase B
```

Then redeploy the `pre-<phase>` image. Never undo by hand-written SQL: the
changelog would still say the copy ran, and the next deploy would skip it.

## How phase B was checked

- **H2 tests:** `SantaseStatsTest`, `TablaStatsTest` and `EloTest` cover the new records, the rating arithmetic and forgetting a deleted account.
- **Throwaway Postgres 17 cluster**, seeded with the prod shape of `users` and `user_game_stats`:
  - forward 033–036: every row copied exactly, verification passed, and the foreign key set to cascade;
  - a game played and a player registered while "live";
  - rollback 036–033: the play and the new player copied back into the old table, the schemas dropped, the key restored;
  - forward again: clean.
