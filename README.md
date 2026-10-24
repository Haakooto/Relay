# Relay

A personal Android clock app that replaces the stock one: alarms, timers that chain into
**relays**, stopwatches with tagged and correctable laps, a world clock, and per-instance
home-screen widgets. Alarms can also start a timer, relay or stopwatch instead of ringing.

- **How to use it:** [`docs/usage-guide.md`](docs/usage-guide.md) (also in the app: Settings → How to use Relay, or the ? on each pane)
- **Design spec, feature map and roadmap:** the [wiki](https://github.com/Haakooto/Relay/wiki)
- **On-device test checklist:** [`docs/device-tests.md`](docs/device-tests.md)
- **Release notes:** [`CHANGELOG.md`](CHANGELOG.md)

## Status

v1.0: every pane is built and has been through on-device testing. Planned after 1.0: bedtime,
assistant automation, more widget types, lap export. See the [roadmap](https://github.com/Haakooto/Relay/wiki/Roadmap).

## Docs, issues and roadmap

- **Wiki:** the [design spec](https://github.com/Haakooto/Relay/wiki/Design-Spec), the
  [feature map](https://github.com/Haakooto/Relay/wiki/Feature-Map) (what is built and tested)
  and the [roadmap](https://github.com/Haakooto/Relay/wiki/Roadmap) (what is planned).
- **Issues:** features and bugs are tracked as
  [GitHub issues](https://github.com/Haakooto/Relay/issues). To ask for a feature or report a
  bug, [open an issue](https://github.com/Haakooto/Relay/issues/new/choose).
- **In this repo:** the user guide (`docs/usage-guide.md`, `docs/usage-guide.nn.md`), the
  on-device checklist (`docs/device-tests.md`) and `CHANGELOG.md`.

## Modules

- `app/`: the Android app (Kotlin, Jetpack Compose, Room, DataStore, Glance, AlarmManager).
  Package and `applicationId` `com.relay.app`.
- `chainlang/`: the relay language (parser, evaluator, safety limits, cycle checks), plain
  Kotlin/JVM with no Android dependency.

## Building and testing

GitHub Actions builds every PR and every merge (`.github/workflows/build.yml`): PRs get a
"Relay Debug" APK on the `dev-pr-<n>` release page, `main` a signed release APK on `main-latest`,
and `vX.Y` tags a proper release. Locally:

```
./gradlew :app:assembleDebug          # the app
./gradlew :app:testDebugUnitTest      # app unit tests (scheduling, runs, plans, stopwatch, editor)
./gradlew :chainlang:test             # relay language tests
./gradlew :app:connectedAndroidTest   # database migration tests (needs a device or emulator)
```

`minSdk` is 33. Alarms use `USE_EXACT_ALARM`, granted automatically, so there is no exact-alarm
permission screen.

Bundled fonts (DSEG7, Fraunces) are under the SIL Open Font Licence; their licences are in
`app/src/main/assets/licenses/`.
