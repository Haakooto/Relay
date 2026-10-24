# Relay

Personal Android app that rivals and replaces the stock Android Clock: alarms, timers that
relay (chain), stopwatch, world clock, home-screen widgets. Built because the stock app can't
chain timers, can't schedule anything but a ringing alarm, and can't run one timer twice at once.

## Status

The current release is the newest `vX.Y` tag (`git describe --tags`); `CHANGELOG.md` says what
each one changed. The app is a clean rebuild from v1.0 (the old v0.5 code is in git history
before the `restart` branch). What comes next is the
[Roadmap](https://github.com/Haakooto/Relay/wiki/Roadmap) in the wiki and the open GitHub issues.

## Principles and skills

- [`docs/principles.md`](docs/principles.md): the product, interaction, architecture, code, data
  and security rules all work is held to. Read it before designing or building anything; a PR
  that breaks a rule says why.
- Project skills in `.claude/skills/` (shared across dev devices through git):
  `/feature-request` and `/bug-report` turn a description into a high-level GitHub issue after
  checking existing issues for a match; `/implement <issue>` works out a detailed spec with the
  user (for features), plans, refactors if needed, builds, tests, updates docs and opens the PR.
- `.claude/scripts/wiki.sh` clones or updates the wiki into `.wiki/` (git-ignored) and prints
  the path. Commit wiki edits from inside `.wiki/`.
- Local Claude state (`.claude/settings.local.json`, `.claude/worktrees/`) stays out of git.

## Docs

- **The wiki holds the design docs and the roadmap**: <https://github.com/Haakooto/Relay/wiki>
  (clone it with `git clone git@github.com:Haakooto/Relay.wiki.git`; it is a separate repo with
  Markdown pages, so edit and push there, not in this repo). Pages: `Home`, `Roadmap`,
  `Feature-Map`, and the design spec split into `Design-Spec` (§01 to §03), `Panes-Spec` (§04,
  §05, §07 to §12), `Relay-Language` (§06), `Firing-Module` (§13), `Data-Model` (§14) and
  `Visual-Direction-and-Build-Order` (§15 to §17). Section numbers such as "spec §13" in code
  comments refer to those spec sections. **Update the spec page when a design decision changes.**
- **Features and bugs are GitHub issues**: <https://github.com/Haakooto/Relay/issues> (issue
  forms for bug reports and feature requests). The Roadmap page links each planned item to its
  issue.
- [`docs/usage-guide.md`](docs/usage-guide.md) and its Nynorsk twin `docs/usage-guide.nn.md`:
  the user-facing guide, one `##` section per pane. **Update both when a feature's behaviour
  changes** (keep it short: main ideas, not every option). The build copies them into the app
  (Settings → How to use Relay, and the ? in each pane header), so the `##` sections must stay in
  the order of `GuideSection`; `GuideMarkdownTest` checks it. Markdown is limited to what
  `GuideMarkdown` parses: headings, paragraphs, `- ` lists, pipe tables, fenced code, `---`,
  `**bold**`, `*italic*` and `` `code` ``.
- **Feature Map (wiki page `Feature-Map`)**: source of truth for what's actually built.
  **Update it whenever you build, fix or retest a feature**: one row per feature, a status
  (Approved/WIP/Lacking/Planned/Deferred/Declined) and a one-line note. Keep the header counts
  in sync with the rows. Also keep the `Roadmap` page in step (anything not Approved lives
  there). In the PR that builds or fixes a feature, reference its issue and close it with
  "Closes #n" in the PR description. Create an issue first if the work has none. Because the wiki
  is its own repo, a feature-map change is a separate commit and push there (the user publishes
  it unless told otherwise).
- [`docs/device-tests.md`](docs/device-tests.md): the user's on-device checklist. **After every
  build, add a section** with specific things to try (short, one action and one expected result
  per line). When the user reports results, tick them there and update the wiki Feature-Map page to match.
- [`CHANGELOG.md`](CHANGELOG.md): human-readable notes per tagged release. Update only when
  tagging, and write what changed for the user, not how.

## Stack

- Kotlin + Jetpack Compose (Material 3), Room, DataStore, Glance (widgets), AlarmManager exact
  alarms, kotlinx-serialization (JSON columns).
- Package and `applicationId`: `com.relay.app`. It installs alongside the old v0.5 app
  (`applicationId` of the old build differs), so v1.0 never touches v0.5 data.
- `app/`: the Android app. `chainlang/`: standalone Kotlin/JVM parser and evaluator for the relay
  language (`com.relay.chainlang`), no Android dependency. `./gradlew :chainlang:test`. The
  module keeps the "chain" name internally; in the app, new code says "relay" (`RelayDef`, not
  `ChainDef`).
- `minSdk = 33` deliberately: single code path, no legacy branches.
- Per-app language uses the system `LocaleManager` API (`ui/settings/AppLanguage.kt`), not
  DataStore. Strings in English and Norwegian from the first screen.
- English is **British English** only, not configurable: British spelling in `values/strings.xml`,
  and any English locale formats dates and times as en-GB (`util/AppLocale.kt`).
- Norwegian is **Nynorsk** only, never bokmål. Edit `values-nn/strings.xml`, then copy it over
  `values-nb/strings.xml` (Android doesn't fall back from nb to nn; `NorwegianStringsTest` fails if
  they differ). A relay is **eit relé** (reléet, relé, relea), never "stafett".

## Branches, PRs and CI

- Work happens on feature branches (`feature/<short-name>`), one commit per feature, pushed and
  opened as a PR to `main`. Never commit straight to `main`; merging a PR is the user's call.
- `.github/workflows/build.yml` builds on GitHub, so every dev device gets the same APKs:
  - every PR push: unit tests + a "Relay Debug" APK on the rolling release page `dev-pr-<n>`
    (the PR gets a comment with the link once), deleted when the PR closes;
  - every push to `main` (a merged PR): a signed release APK on the rolling page `main-latest`;
  - a pushed `vX.Y` / `vX.Y-alpha.N` tag: a signed release APK on a GitHub release for that tag,
    with its `CHANGELOG.md` section as notes.
- Alongside the build, on every PR and merge: `checks.yml` runs Android Lint and detekt (new
  findings fail; old ones are in `app/lint-baseline.xml` and `config/detekt/baseline.xml`),
  `codeql.yml` runs CodeQL security analysis (also weekly), and findings show on the PR and in
  the Security tab. Dependabot opens monthly grouped update PRs; `dependency-graph.yml` feeds it
  the Gradle dependencies.
- A PR that changes the Room schema gets a review thread from `migration-reminder.yml`: install
  the PR's dev APK over the previous build and check the data survived, then resolve the thread.
  There is no emulator in CI (it would more than double the run time), so this is the migration
  test.
- So "verify with Gradle" below means: push and read the CI run (`gh run watch`, `gh run view
  --log-failed`). A new Room schema JSON comes back as the `room-schemas` artifact; commit it.
- Signing in CI comes from two repo secrets: `RELAY_KEYSTORE_BASE64` (the .jks, base64) and
  `RELAY_KEYSTORE_PROPERTIES` (the user's keystore.properties). The workflow writes them to the
  runner and points `storeFile` at the decoded key. Debug builds use the same key so each dev APK
  installs over the last.

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
- Development happens on several machines, and not all of them can build. Check at the start:
  with a full JDK (`javac`, not just `java`: a runtime alone fails) and an Android SDK
  (`ANDROID_HOME`, or `sdk.dir` in the git-ignored `local.properties`), run the Gradle tasks
  locally before pushing; without them, verify through CI. CI is the reference either way: it
  runs `:chainlang:test`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin` and the
  APK build. No emulator is assumed. Real correctness comes from the user's own device testing, so say so
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
