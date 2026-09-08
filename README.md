# Clockr

Custom Android alarms / timers / stopwatch app — because the stock one is ugly and its timer can't chain.

Full spec (stack, data model, timer/chain block system): [`docs/spec.html`](docs/spec.html) — open it directly in a browser, no sign-in needed. (Also mirrored at https://claude.ai/code/artifact/6b692873-9cb2-4383-b9d6-6f587a16f69d, kept up to date the same way, but that one requires a claude.ai login.)

## Status

Mockup stage — Compose Preview screens for the five panes (Alarms, Timers, Stopwatch, Clock, Settings)
with sample data, no real logic wired up yet. Open in Android Studio, let it install the missing SDK
platforms it asks for, and use the Preview pane on any `*ScreenPreview` composable in `app/src/main/java/com/clockr/app/ui/screens/`.

## Setup notes

- This checkout has no Android SDK configured — Android Studio will prompt to install one on first open.
- `gradle/wrapper/gradle-wrapper.jar` was fetched directly (no local Gradle/network access to `services.gradle.org`
  assumed during scaffolding besides that one file) — if it's ever missing or corrupt, regenerate it with
  `gradle wrapper --gradle-version 8.10` from any machine with Gradle installed.
- No launcher icon yet (`android:icon` omitted from the manifest) — add one before shipping.

## Next

- Prototype the chain parser + reference-graph validator (cycle detection, depth, plan-size caps) standalone
  in plain Kotlin before wiring it into the UI — see spec §6.5.
- Pressure-test the seed palette/type pairing in spec §10 against these mockups.
