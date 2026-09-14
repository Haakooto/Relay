# Changelog

Conceptual, human-readable notes per release — not a commit log (see `git log` for that).
One entry per tag; "Unreleased" collects what's landed since the last one.

## Unreleased

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
