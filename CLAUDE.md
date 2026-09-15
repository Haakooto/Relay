# Relay

Personal Android app: alarms, relayable timers, stopwatch, clock, home-screen widget. Built
because the stock alarm/timer app is ugly and can't chain timers together.

> **Naming note (v0.5):** the app was renamed Clockr → Relay, user-facing only. In-app strings say
> "Relay"; the code, database, and the standalone `chainlang/` module still say "Chain"/"chain"
> throughout (`ChainDefEntity`, `ChainDefDao`, `BlockKind.CHAIN`, the `chain_defs` table,
> `ClockrDatabase`, `ClockrTheme`, `applicationId "com.clockr.app"`, etc.) — these are the same
> thing as what the UI calls a "Relay." Deliberately not renamed internally: no user-visible
> benefit, and renaming the DB table or the `chainlang` module's own package/AST types would be a
> much larger, riskier change for zero functional gain. When working in this codebase, "chain" and
> "relay" refer to the same concept.

## Docs

- [`docs/spec.html`](docs/spec.html) — design spec (stack, data model, timer/chain DSL). Living
  document, not always in sync with what's actually built.
- [`docs/feature-map.html`](docs/feature-map.html) — the source of truth for what's actually
  built vs. planned. **Update this whenever you fix, add, or retest a feature** — one row per
  feature, status chip (Approved/WIP/Lacking/Planned/Deferred/Declined) + a one-line note. Keep
  the header stats counts in sync with the actual row counts.
- [`CHANGELOG.md`](CHANGELOG.md) — conceptual, human-readable notes per tagged release. Update
  only when tagging a release, not per-commit; write what changed for the user, not how.

## Stack

- Kotlin + Jetpack Compose (Material 3), Room (persistence), DataStore (settings), Glance
  (home-screen widget), AlarmManager (exact alarms).
- `app/` — the Android app. `chainlang/` — standalone Kotlin/JVM parser+evaluator for the
  timer-relay DSL (module/package name kept as "chainlang" internally, see the naming note
  above), no Android dependency (`./gradlew :chainlang:test`, currently 30/30 passing).
- `minSdk = 33` deliberately — single code path, no legacy branches.

## Versioning

- Releases are manual git tags, `vX.Y` (annotated: `git tag -a vX.Y -m "..."`). No auto-bumping.
- The app's `versionName` (shown in Settings) is computed at build time from git, not hardcoded:
  the exact tag when `HEAD` is on one, else `<nearest-tag>+<short-hash>` for anything in between.
  See `gitVersionName()` in `app/build.gradle.kts`.
- Tag a release → add a `CHANGELOG.md` entry for it in the same commit/session.

## Working conventions

- Only commit when explicitly asked. Never amend, never force-push.
- No device or emulator is available in this environment — verify changes with
  `./gradlew :app:compileDebugKotlin` (and `:chainlang:test` for DSL changes), but real
  correctness comes from the user's own device testing pass. Say so explicitly rather than
  claiming a UI/gesture/hardware-dependent feature works.
- Be extra cautious touching `alarm/AlarmReceiver`, `alarm/NotificationHelper`, or
  `alarm/AlarmScheduler` — alarm firing reliability is the one thing this app cannot regress on,
  and bugs there are invisible until a real alarm fails to go off. Prefer additive, narrowly-scoped
  changes over refactors there when it can't be tested on-device in the same session.
- Room schema changes need a real `Migration`, not `fallbackToDestructiveMigration()` — the user's
  device carries real data across sessions.
