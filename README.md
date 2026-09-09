# Clockr

Custom Android alarms / timers / stopwatch app — because the stock one is ugly and its timer can't chain.

- Full spec (stack, data model, timer/chain block system): [`docs/spec.html`](docs/spec.html) — open directly in a browser, no sign-in needed. (Mirrored at https://claude.ai/code/artifact/6b692873-9cb2-4383-b9d6-6f587a16f69d, which requires a claude.ai login.)
- Feature map (what's approved / WIP / lacking / planned / deferred): [`docs/feature-map.html`](docs/feature-map.html) — same deal, no-login mirror of https://claude.ai/code/artifact/43afb42b-7c1a-4f43-88d7-ca8f62c3b11e.

## Status

Past the mockup stage — Alarms, Timers/Chains, Stopwatch, Clock, and Settings are wired to real
Room/DataStore-backed state, not sample data. See the feature map for the honest per-item
breakdown; in short: definitions and CRUD work, but there's no Timer/Chain *run* engine yet (no
countdown, no live notification, no chain-step advancement), and nothing in Alarms has been
verified end-to-end on a device yet.

Modules:
- `app/` — the Android app (Compose UI, Room, Glance widget, AlarmManager scheduling)
- `chainlang/` — standalone Kotlin/JVM parser + evaluator for the timer-chain DSL, no Android
  dependency, runnable with plain `./gradlew :chainlang:test`

## Setup notes

- `gradle/wrapper/gradle-wrapper.jar` was originally fetched directly rather than generated
  locally; Android Studio has since bumped the wrapper itself, so this is no longer a concern —
  regenerate with `gradle wrapper --gradle-version <version>` from any machine with Gradle if it's
  ever suspect.
- No launcher icon yet (`android:icon` omitted from the manifest) — add one before shipping.
- `SCHEDULE_EXACT_ALARM` needs a one-time user grant on Android 13+; `MainActivity` prompts for it
  via system settings on first launch.

## Next

The biggest gap by far: the Timer/Chain run engine (foreground service, live countdown, chain-step
advancement, the Stop/Restart/+1min completion prompt) doesn't exist yet — only create/edit/delete
does. See the feature map for everything else.
