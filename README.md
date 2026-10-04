# Relay

A personal Android clock app that replaces the stock one: alarms, timers that chain into
**relays**, stopwatches with tagged and correctable laps, a world clock, and per-instance
home-screen widgets. Alarms can also start a timer, relay or stopwatch instead of ringing.

- **Install or build it:** [`docs/building.md`](docs/building.md) (from a ready-made APK, or from scratch)
- **How to use it:** [`docs/usage-guide.md`](docs/usage-guide.md) (also in the app: Settings → How to use Relay, or the ? on each pane)
- **Design spec:** [`docs/spec.html`](docs/spec.html) (open in a browser)
- **What's built and tested:** [`docs/feature-map.html`](docs/feature-map.html)
- **On-device test checklist:** [`docs/device-tests.md`](docs/device-tests.md)
- **Release notes:** [`CHANGELOG.md`](CHANGELOG.md)

## Status

v1.0: every pane is built and has been through on-device testing. Planned after 1.0: bedtime,
assistant automation, more widget types, lap export. See the feature map.

## Modules

- `app/`: the Android app (Kotlin, Jetpack Compose, Room, DataStore, Glance, AlarmManager).
  Package and `applicationId` `com.relay.app`.
- `chainlang/`: the relay language (parser, evaluator, safety limits, cycle checks), plain
  Kotlin/JVM with no Android dependency.

## Building and testing

Full setup from scratch, signing and troubleshooting: [`docs/building.md`](docs/building.md).
In short:

```
./gradlew :app:installDebug           # build and install "Relay Debug" on a connected phone
./gradlew :app:assembleRelease        # signed release APK (needs keystore.properties)
./gradlew :app:testDebugUnitTest      # app unit tests
./gradlew :chainlang:test             # relay language tests
```

`minSdk` is 33. Alarms use `USE_EXACT_ALARM`, granted automatically, so there is no exact-alarm
permission screen.

Bundled fonts (DSEG7, Fraunces) are under the SIL Open Font Licence; their licences are in
`app/src/main/assets/licenses/`.
