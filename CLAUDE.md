# Relay

Personal Android app that rivals and replaces the stock Android Clock: alarms, timers that
relay (chain), stopwatch, world clock, home-screen widgets. Built because the stock app can't
chain timers, can't schedule anything but a ringing alarm, and can't run one timer twice at once.

## Status: v1.0

v1.0 is a clean rebuild (the old v0.5 code is in git history before the `restart` branch, which
is merged into `main`). All seven build steps in the spec are done. Next is the post-1.0 list in
the feature map: bedtime, assistant automation, more widget types, CSV export of laps.

## Docs

- [`docs/spec.html`](docs/spec.html): the v1.0 design baseline (core model, panes, relay
  language, firing module, data model, build order). Update it when a design decision changes.
- [`docs/usage-guide.md`](docs/usage-guide.md) and its Nynorsk twin `docs/usage-guide.nn.md`:
  the user-facing guide, one `##` section per pane. **Update both when a feature's behaviour
  changes** (keep it short: main ideas, not every option). The build copies them into the app
  (Settings → How to use Relay, and the ? in each pane header), so the `##` sections must stay in
  the order of `GuideSection`; `GuideMarkdownTest` checks it. Markdown is limited to what
  `GuideMarkdown` parses: headings, paragraphs, `- ` lists, pipe tables, fenced code, `---`,
  `**bold**`, `*italic*` and `` `code` ``.
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

- Kotlin + Jetpack Compose (Material 3), Room, DataStore, Glance (widgets), AlarmManager exact
  alarms, kotlinx-serialization (JSON columns).
- Package and `applicationId`: `com.relay.app`. It installs alongside the old v0.5 app
  (`applicationId` of the old build differs), so v1.0 never touches v0.5 data.
- `app/`: the Android app. `chainlang/`: standalone Kotlin/JVM parser and evaluator for the relay
  language (`com.relay.chainlang`), no Android dependency. `./gradlew :chainlang:test`
  (currently 54/54 passing). The module keeps the "chain" name internally; in the app, new code
  says "relay" (`RelayDef`, not `ChainDef`).
- `minSdk = 33` deliberately: single code path, no legacy branches.
- Per-app language uses the system `LocaleManager` API (`ui/settings/AppLanguage.kt`), not
  DataStore. Strings in English and Norwegian from the first screen.
- English is **British English** only, not configurable: British spelling in `values/strings.xml`,
  and any English locale formats dates and times as en-GB (`util/AppLocale.kt`).
- Norwegian is **Nynorsk** only, never bokmål. Edit `values-nn/strings.xml`, then copy it over
  `values-nb/strings.xml` (Android doesn't fall back from nb to nn; `NorwegianStringsTest` fails if
  they differ). A relay is **eit relé** (reléet, relé, relea), never "stafett".

## Releases and signing

- `./gradlew :app:assembleRelease` builds `app/build/outputs/apk/release/app-release.apk`,
  shrunk with R8 (rules in `app/proguard-rules.pro`; @Serializable classes are kept) and signed
  with the user's key via `keystore.properties` (git-ignored; template in
  `keystore.properties.example`). Never read, print or commit the key or that file.
- Debug builds install separately as "Relay Debug" (`com.relay.app.debug`).
- `versionCode` comes from the tag (`gitVersionCode()`): vX.Y = X*10000 + Y*100 + 99,
  vX.Y-alpha.N = X*10000 + Y*100 + N. Updates only install when it goes up, so tag before
  building a release to share.

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
- Bundled fonts (`res/font/`: DSEG7, Fraunces) are SIL OFL; keep their licence texts in
  `assets/licenses/` when adding or updating fonts.
