# Changelog

Conceptual, human-readable notes per release — not a commit log (see `git log` for that).
One entry per tag; "Unreleased" collects what's landed since the last one.

## Unreleased

## v1.0-alpha.2 (2026-09-30)

Untested pre-release: alarms. Not tried on a device yet.

- Alarms work: one-off or repeating on chosen weekdays, ending on a date or after a number of
  times. Each alarm has its own label, sound, vibration, gradual volume increase, snooze length
  and snooze limit. New alarms open straight into the time picker.
- Alarms ring full screen over the lock screen and keep ringing until you act (or until the
  ring timeout, 10 minutes by default). Several alarms ringing at once stack on one screen.
- Skip or move an alarm on a given day or date range, and apply that to several alarms at once,
  for example for a holiday week.
- A notification shows before an alarm (2 hours by default), with "Dismiss now" to skip just
  that one. A snoozed alarm can be dismissed from its notification.
- An alarm missed while the phone was off rings when it's back, if it's less than an hour late,
  and says it was delayed. Older ones leave a "missed alarm" notification.
- The Alarms pane warns when something would stop alarms from working (notifications off,
  full-screen blocked, battery optimization, alarm volume at zero), with a button to fix it.
- Known gap: after a restart, alarms come back only once the phone has been unlocked.

## v1.0-alpha.1 (2026-09-30)

Untested pre-release: the start of the v1.0 rebuild. Not tried on a device yet.

- Relay is rebuilt from scratch and installs as a new app next to the old one, which keeps
  working (and keeps ringing its alarms) until you uninstall it. Nothing is carried over.
- The five panes are in place. Settings works (theme including AMOLED, 24-hour clock, first day
  of week, language, haptics). Alarms, Timers, Stopwatch and Clock are placeholders for now.
- Language can be switched between English and Norwegian, and the choice also shows in the
  phone's own app-language settings.

## v0.5 — 2026-09-15

- Renamed the app to Relay: new launcher icon (adaptive, a two-clock-face mark), new
  app name, and every on-screen mention of "Chain" (the Timer/Chain block kind) now reads "Relay"
  — "Timer or Relay", "New relay", "Writing a relay", and so on. This is a user-facing rename
  only: the database, the `chainlang` module, and internal Kotlin class names are unchanged, so
  existing installs and data are unaffected.
- Fixed the launcher icon glyph filling the whole canvas edge-to-edge with no padding, which read
  as oversized once masked into a launcher's icon shape compared to other apps' icons.

## v0.4 — 2026-09-15

- Alarms can now have schedule exceptions: skip a single day or a whole date range (or ring at a
  different time instead) without disabling the alarm outright — for a one-off "no alarm
  tomorrow" or a whole vacation week. Manage them from the alarm's own config screen, with the
  option to apply one exception to several alarms at once.
- Fixed a bug where the Timer and Settings tabs would stop switching panes and always land on
  Clock instead. Any change that recreates the app's screen (switching Language, rotating the
  device, changing system theme) was re-triggering the "jump to Clock" behavior meant only for
  opening the app from the home-screen widget.

## v0.3 — 2026-09-14

- Fixed a stopwatch bug: the first lap after starting with an offset (e.g. "start from 1m")
  showed its delta as if it included that offset, instead of measuring from it.
- Started real i18n: every hardcoded UI string (screens, notifications, error messages) now
  comes from resources, with English and Norwegian (Bokmål) translations. Picking Norsk in
  Settings should now actually translate the app, not just change date/number formatting.

## v0.2 — 2026-09-14

- Alarms can now target a Timer or Chain instead of firing themselves — pick "Timer or Chain" in
  an alarm's Fires row and it starts that block when it goes off, using the run engine's own
  Stop/Restart/+1min prompt instead of a separate alarm notification. Plain alarms are unaffected.
- App version is now shown at the bottom of Settings, and reflects the actual build: the tagged
  release name (`v0.1`) when built exactly at a tag, or `v0.1+<commit>` for anything since.
- Fixed the home-screen widget freezing permanently after a single missed tick (any failure
  updating its view used to also cancel all future ticks) and not resuming after a device reboot.
- "New Header" button in the Timers library now matches the capitalization of the other menu items.

## v0.1 — 2026-09-11

First tagged milestone. Alarms and Stopwatch are confirmed solid on-device. Everything else got a
first real device-testing pass this round, which is most of what's in this release:

- Timer/Chain runs no longer play their alert sound the instant you press Play — it was reusing
  the same notification channel for "starting" and "finished," so a channel's sound fired on
  every post.
- Starting a Chain no longer crashes the app (it was hitting the database from the wrong thread).
- The live countdown while a Timer/Chain runs actually counts down now, instead of freezing at
  the starting number.
- Settings that looked like they did something but didn't: 24-hour clock format, first day of the
  week, and default snooze length are all actually wired up now. Haptics went from a fully dead
  toggle to one that actually controls vibration.
- Seamless chain transitions (no "tap to continue" step) used to be completely silent between
  steps — they now play a short configurable tone, set per-Chain so different chains can sound
  different.
- The Timers library can now be organized: add named header sections, and freely drag any Timer
  or Chain under any header (or leave it ungrouped).
