# Relay

Personal Android app that rivals and replaces the stock Android Clock: alarms, timers that
relay (chain), stopwatch, world clock, home-screen widgets. Built because the stock app can't
chain timers, can't schedule anything but a ringing alarm, and can't run one timer twice at once.

## Status: v1.0 clean restart

The v0.5 codebase lives on `main` (last commit before the restart) and in git history. v1.0 is
being rebuilt from scratch on the `restart` branch, following the build order in the spec (§16).
Only `chainlang/` was carried over as-is.

## Docs

- [`docs/spec.html`](docs/spec.html): the v1.0 design baseline (core model, panes, relay
  language, firing module, data model, build order). Update it when a design decision changes.
- [`docs/feature-map.html`](docs/feature-map.html): source of truth for what's actually built.
  **Update it whenever you build, fix or retest a feature**: one row per feature, a status chip
  (Approved/WIP/Lacking/Planned/Deferred/Declined) and a one-line note. Keep the header counts in
  sync with the rows.
- [`docs/device-tests.md`](docs/device-tests.md): the user's on-device checklist. **After every
  build, add a section** with specific things to try (short, one action and one expected result
  per line). When the user reports results, tick them there and update the feature map to match.
- [`CHANGELOG.md`](CHANGELOG.md): human-readable notes per tagged release. Update only when
  tagging, and write what changed for the user, not how.

## Stack

- Kotlin + Jetpack Compose (Material 3), Room, DataStore, Glance (widgets, from build step 7),
  AlarmManager exact alarms.
- Package and `applicationId`: `com.relay.app`. It installs alongside the old v0.5 app
  (`applicationId` of the old build differs), so v1.0 never touches v0.5 data.
- `app/`: the Android app. `chainlang/`: standalone Kotlin/JVM parser and evaluator for the relay
  language (`com.relay.chainlang`), no Android dependency. `./gradlew :chainlang:test`
  (currently 30/30 passing). The module keeps the "chain" name internally; in the app, new code
  says "relay" (`RelayDef`, not `ChainDef`).
- `minSdk = 33` deliberately: single code path, no legacy branches.
- Per-app language uses the system `LocaleManager` API (`ui/settings/AppLanguage.kt`), not
  DataStore. Strings in English and Norwegian (`values-nb`) from the first screen.

## Versioning

- Releases are manual annotated git tags in two parts, `vX.Y`. No auto-bumping.
- Work that is built but not yet tested on the device is tagged as a pre-release:
  `vX.Y-alpha.N` (for example `v1.0-alpha.1` for build step 1). The plain `vX.Y` tag is only for
  a release that has passed the user's device test.
- `versionName` is computed from git at build time (`gitVersionName()` in `app/build.gradle.kts`):
  the exact tag on a tagged commit, else `<nearest-tag>+<short-hash>`.
- Tag a release, then add a `CHANGELOG.md` entry for it in the same session.

## Working conventions

- Only commit when explicitly asked. Never amend, never force-push.
- No device or emulator in this environment. Verify with `./gradlew :app:compileDebugKotlin`
  (plus `:chainlang:test` for language changes, `:app:compileDebugAndroidTestKotlin` for
  instrumented tests). Real correctness comes from the user's own device testing, so say so
  explicitly rather than claiming a UI, gesture or hardware-dependent feature works.
- Alarm firing reliability is the one thing this app cannot regress on. Everything that rings
  goes through one firing module (spec §13). Be extra careful there, and prefer narrow, additive
  changes when it can't be tested on-device in the same session.
- Room: schemas are exported to `app/schemas/` and committed. Every version bump needs a real
  `Migration` in `RelayDatabase.MIGRATIONS` and a test in `MigrationTest`. Never
  `fallbackToDestructiveMigration()`: the user's device carries real data.
- Add Android permissions and settings keys in the build step that first needs them, not ahead.
