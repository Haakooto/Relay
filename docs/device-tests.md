# Device tests

What to try on the phone after each build. Tick a box when it works; write a note under it when
it doesn't (a line starting with `>` is enough). Claude reads this file, updates the feature map
from it, and adds a new section after every build.

Untested sections stay here until everything in them is ticked or has a note.

Legend: `[x]` works · `[ ]` not tried yet · `[!]` tried, broken (add a note)

## Every build: quick smoke test (2 min)

- [x] App opens, and all five panes open from the bottom bar.
- [x] Set an alarm 2 minutes out, lock the phone: it wakes the screen and rings until you act.

## v1.0-alpha.1: foundation

- [x] Switching between the five panes lands on the pane you tapped. (The old app got stuck on Clock.)
- [x] Settings → Language → Norsk: the UI switches to Norwegian **and you stay on Settings**.
- [x] Language → System: back to the phone's language.
- [x] Each theme (System, Light, Dark, AMOLED) applies at once. AMOLED is pure black.
- [x] Close and reopen the app in Dark: no white flash on launch.
- [x] Settings shows a version like `v1.0-alpha.2+abc1234`.
- [x] The phone's own Settings → Apps → Relay → Language also lists English and Norsk.

## v1.0-alpha.2: alarms

### Ringing
- [x] Alarm 2 minutes out, phone locked: wakes the screen, shows full screen over the lock screen.
- [x] It keeps ringing (doesn't stop after one play of the sound).
- [x] Snooze works; it rings again after the snooze length.
- [x] Dismiss stops it.
- [ ] While ringing, get a text or other notification: the alarm sound keeps going.
- [ ] Two alarms set to the same minute: both show as stacked cards; dismiss them one at a time.
- [ ] Let one ring without touching it: it stops after 10 minutes and leaves a notification saying so.
- [ ] Gradual volume increase (set 30 s on an alarm): starts quiet, gets loud.
- [ ] Vibrate off on an alarm: no vibration. On: vibrates.
- [ ] Pick a different sound in the editor: that sound plays.

### Snooze
- [ ] Max snoozes 1: after one snooze, the Snooze button is gone on the next ring.
- [ ] Max snoozes None: no Snooze button at all, and the editor hides the snooze length.
- [ ] Snooze length Custom (e.g. 2 min): the alarm comes back after 2 minutes.
- [ ] While snoozed, the "Snoozed until" notification's Dismiss cancels the snooze.

### Creating and editing
- [x] New alarm opens straight into the time picker.
- [ ] After Save, a toast says how long until it rings.
- [x] The list is sorted by next ring time and shows "Rings in …".
- [ ] One-off with "Delete" after ringing: gone from the list after you dismiss it.
- [ ] One-off with "Keep, switched off": stays, toggled off.
- [ ] Repeating on chosen days: the list shows the days in your first-day-of-week order.
- [ ] Repeating, "After a number of times" = 2: switches itself off after ringing twice.
- [ ] Toggle an alarm off and on from the list: toast on enable, no ring while off.
- [ ] Delete from the editor.

### Exceptions
- [ ] On a saved repeating alarm, add a Skip for tomorrow: "Rings in" jumps past tomorrow.
- [ ] Add "Ring at another time" for a day: it rings at that time that day only.
- [ ] Add one exception with "Also apply to" ticked for a second alarm: both show it.
- [ ] Delete an exception with its ✕.

### Upcoming, missed, restarts
- [ ] About 2 hours before an alarm, an "Upcoming alarm" notification appears.
- [ ] Its "Dismiss now" skips just that occurrence; the alarm still rings the next day.
- [ ] Restart the phone, unlock: the alarm is still set and rings. (Known gap: not before the first unlock.)
- [ ] Turn the phone off across an alarm time, turn it on within the hour: it rings, saying "Delayed from …".
- [ ] Change the phone's time zone: alarms keep their local time.

### Warnings banner
- [ ] Alarm volume at 0: a red banner on the Alarms pane with "Turn up".
- [ ] Deny notifications (phone settings): the banner asks to allow them.

## Uncommitted: snooze tweaks

- [ ] Alarm editor → Snooze length → Custom → 2: saves, and the chip shows "2 min".
- [ ] Max snoozes → None: the snooze length row disappears, and the alarm rings with no Snooze button.
- [ ] Settings → Default snooze length → Custom works the same way.

## Uncommitted: timers and relays (build steps 3 and 4)

### Quick timers and runs
- [ ] Timers → + → tap "1m": a card appears at the top and counts down; a notification shows the countdown too.
- [ ] At zero it rings full screen (like an alarm) with Stop / Restart / +1 min. Each does what it says.
- [ ] Start two quick timers at once: two independent cards, two notifications.
- [ ] Pause, then Resume from the card: the time left is kept.
- [ ] Pause from the notification: the card updates too.
- [ ] Reset (while paused): back to the full time, still paused.
- [ ] +1 min while running adds a minute.
- [ ] Lock the phone during a 1-minute timer: it still rings on time.
- [ ] Settings → Timers → tap a quick-start chip, change it to 3: the + menu shows 3m.

### Saved timers
- [ ] + → New timer, label `Tea`, duration `4m`, Save: appears in the library as "Timer · 4m".
- [ ] Try saving a label with a space (`Green tea`): refused with an explanation.
- [ ] New timer with a label but no Save, tap Start: it runs with that label and isn't saved.
- [ ] ▶ on Tea twice: two Tea runs at once.
- [ ] Open Tea, change the duration, then back out without saving: the library still shows 4m.

### Relays
- [ ] + → New relay, label `Wake`, steps `(1, 1, 1)`, Wait for me, Save.
- [ ] Start Wake: after 1 minute it rings with Continue / Stop relay; Continue starts the next minute.
- [ ] Change Wake to Continue by itself: steps pass with a short beep, and it rings only at the end.
- [ ] Pick each cue (Beep, Chime, ...) in the editor: you hear it as you pick it.
- [ ] Type `(Te` in the steps: a `Tea` suggestion appears; tapping it completes the name.
- [ ] Type `(` : `)` appears after the cursor. Enter between them: indented new line.
- [ ] `(Tea - 1)`: preview shows one 3-minute step.
- [ ] `(range(1, 3, 0.5))`: preview shows 1m, 1m 30s, 2m, 2m 30s.
- [ ] `(5m "stretch")`: the step label shows while it runs and when it rings.
- [ ] A relay that includes itself (`(Wake)` inside Wake): Save is refused, showing the loop.
- [ ] A broken script (`(1,`): the error shows its line and column, and Start is disabled.
- [ ] Tap a step in the preview, set "Wait for me" after it: only that boundary waits.
- [ ] Rename Tea to `Chai`: relays using Tea now say Chai.
- [ ] Delete a timer a relay uses: the dialog names that relay.

### Library
- [ ] + → New header "Morning"; long-press a timer and drag it under the header.
- [ ] Move headers up and down; rename and delete one (its timers drop back to the top).

### Restarts
- [ ] Start a 5-minute timer, swipe the app away from recents: it still rings on time.
- [ ] Start a 10-minute timer, restart the phone, unlock: the timer is still there and rings on time.

## Uncommitted: languages and editors

- [ ] Settings → Language → Norsk: everything is Nynorsk ("Innstillingar", "Tidtakarar", "Nytt relé").
- [ ] Phone set to bokmål, app language System: the app shows Nynorsk too.
- [ ] Phone set to US English, app language System: dates read like "1 Oct 2026", and 12-hour times say "pm".
- [ ] Open an alarm, timer or relay editor: the bottom bar is gone.
- [ ] Alarm editor: Save sits next to the big time; no scrolling needed to save.
- [ ] Timer and relay editors: Start and Save are in the top bar.
- [ ] Change something, then press back (or ✕): asks "Discard changes?". Keep editing stays; Discard leaves.
- [ ] Open an editor and back out without changing anything: leaves straight away, no question.

## Uncommitted: themes (second try)

- [ ] Light: the cream background is back; selected chips and the bottom-bar highlight are warm grey with dark text, no lavender.
- [ ] Light: orange headings read clearly.
- [ ] Dark: warm dark background, vivid orange; selected chips are a deep orange tint and stand out.
- [ ] AMOLED: pure black, same vivid orange, selected chips stand out.
- [ ] The + button is solid orange in all themes.

## Uncommitted: stopwatch (build step 6)

- [ ] Stopwatch → + → no name, no start time → Start: it counts, with hundredths.
- [ ] + again with name "Run" and start time 1 m: shows 01:00.00 and counts up; "Started from" shows under it.
- [ ] Lap at about 01:05: the lap shows 00:05 (measured from the start time) and 01:05 total.
- [ ] Tap a lap: add a note; it shows under the lap. The lap time can't be changed.
- [ ] Pause and Resume keep the time; Restart goes back to the start time and clears the laps.
- [ ] While running, the notification counts up; its Lap and Pause work.
- [ ] Swipe the app away, reopen: the stopwatch kept counting.
- [ ] Rename and delete work.

## Uncommitted: clock and widgets (build step 7)

### Clock pane
- [ ] A big clock on top, ticking every second, in the display font.
- [ ] Tap it (or the palette icon): the style editor opens without the bottom bar, with a live preview.
- [ ] Try each font, including 7-segment with seconds: the clock always fits the width.
- [ ] Analogue: thick, thin and dot hands; each mark style; ring, filled and numeral faces; second hand on and off.
- [ ] Change a colour (palette, then a custom one): the preview follows; Save keeps it.
- [ ] + → search "tokyo" → pick: Tokyo shows "Tomorrow" or "Today" with "+7 h" or "+8 h", and the time.
- [ ] Swipe a city away to delete; long-press and drag to reorder.
- [ ] Settings → Clock → Home time zone → pick a city in another zone: a "Home" row appears at the top of the cities.

### Widgets
- [ ] Long-press home screen → Widgets → Relay: placing it opens the setup screen first.
- [ ] Back out of setup: no widget is added.
- [ ] Save a digital widget: it shows the time; the minute changes on time.
- [ ] Place a second, analogue widget for another city: both show their own time and style.
- [ ] Background: transparent, colour, gradient; corners: sharp, rounded, pill; a border: all show on the widget.
- [ ] Long-press a widget → Widget settings: change it; the widget updates.
- [ ] Tap a widget: Relay opens on the Clock pane. Then change the language in Settings: it stays on Settings (the old app jumped back to Clock).
- [ ] Leave the phone locked for 10 minutes, unlock: the widget shows the right time straight away.
- [ ] Switch 24-hour clock off in Settings: widgets change to 12-hour.
