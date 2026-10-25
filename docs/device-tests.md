# Device tests

What to try on the phone after each build. Tick a box when it works; write a note under it when
it doesn't (a line starting with `>` is enough). Claude reads this file, updates the wiki feature map
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
- [x] Two alarms set to the same minute: both show as stacked cards; dismiss them one at a time.
- [ ] Let one ring without touching it: it stops after 10 minutes and leaves a notification saying so.
- [x] Gradual volume increase (set 30 s on an alarm): starts quiet, gets loud.
- [!] Vibrate off on an alarm: no vibration. On: vibrates.
    Doesnt vibrate if no nor off.
- [x] Pick a different sound in the editor: that sound plays.

### Snooze
- [x] Max snoozes 1: after one snooze, the Snooze button is gone on the next ring.
- [x] Max snoozes None: no Snooze button at all, and the editor hides the snooze length.
- [x] Snooze length Custom (e.g. 2 min): the alarm comes back after 2 minutes.
- [x] While snoozed, the "Snoozed until" notification's Dismiss cancels the snooze.

### Creating and editing
- [x] New alarm opens straight into the time picker.
- [x] After Save, a toast says how long until it rings.
- [x] The list is sorted by next ring time and shows "Rings in …".
- [x] One-off with "Delete" after ringing: gone from the list after you dismiss it.
- [x] One-off with "Keep, switched off": stays, toggled off.
- [x] Repeating on chosen days: the list shows the days in your first-day-of-week order.
- [ ] Repeating, "After a number of times" = 2: switches itself off after ringing twice.
- [x] Toggle an alarm off and on from the list: toast on enable, no ring while off.
- [x] Delete from the editor.

### Exceptions
- [ ] On a saved repeating alarm, add a Skip for tomorrow: "Rings in" jumps past tomorrow.
- [ ] Add "Ring at another time" for a day: it rings at that time that day only.
- [ ] Add one exception with "Also apply to" ticked for a second alarm: both show it.
- [ ] Delete an exception with its ✕.

### Upcoming, missed, restarts
- [ ] About 2 hours before an alarm, an "Upcoming alarm" notification appears.
- [ ] Its "Dismiss now" skips just that occurrence; the alarm still rings the next day.
- [x] Restart the phone, unlock: the alarm is still set and rings. (Known gap: not before the first unlock.)
- [ ] Turn the phone off across an alarm time, turn it on within the hour: it rings, saying "Delayed from …".
- [ ] Change the phone's time zone: alarms keep their local time.

### Warnings banner
- [!] Alarm volume at 0: a red banner on the Alarms pane with "Turn up".
    Not at all.
- [x] Deny notifications (phone settings): the banner asks to allow them.

## Uncommitted: snooze tweaks

- [x] Alarm editor → Snooze length → Custom → 2: saves, and the chip shows "2 min".
- [x] Max snoozes → None: the snooze length row disappears, and the alarm rings with no Snooze button.
- [x] Settings → Default snooze length → Custom works the same way.

## Uncommitted: timers and relays (build steps 3 and 4)

### Quick timers and runs
- [x] Timers → + → tap "1m": a card appears at the top and counts down; a notification shows the countdown too.
- [x] At zero it rings full screen (like an alarm) with Stop / Restart / +1 min. Each does what it says.
- [x] Start two quick timers at once: two independent cards, two notifications.
- [x] Pause, then Resume from the card: the time left is kept.
- [x] Pause from the notification: the card updates too.
- [x] Reset (while paused): back to the full time, still paused.
- [x] +1 min while running adds a minute.
- [x] Lock the phone during a 1-minute timer: it still rings on time.
- [x] Settings → Timers → tap a quick-start chip, change it to 3: the + menu shows 3m.

### Saved timers
- [x] + → New timer, label `Tea`, duration `4m`, Save: appears in the library as "Timer · 4m".
- [x] Try saving a label with a space (`Green tea`): refused with an explanation.
- [x] New timer with a label but no Save, tap Start: it runs with that label and isn't saved.
- [x] ▶ on Tea twice: two Tea runs at once.
- [x] Open Tea, change the duration, then back out without saving: the library still shows 4m.

### Relays
- [x] + → New relay, label `Wake`, steps `(1, 1, 1)`, Wait for me, Save.
- [x] Start Wake: after 1 minute it rings with Continue / Stop relay; Continue starts the next minute.
- [x] Change Wake to Continue by itself: steps pass with a short beep, and it rings only at the end.
- [x] Pick each cue (Beep, Chime, ...) in the editor: you hear it as you pick it.
- [x] Type `(Te` in the steps: a `Tea` suggestion appears; tapping it completes the name.
- [x] Type `(` : `)` appears after the cursor. Enter between them: indented new line.
- [x] `(Tea - 1)`: preview shows one 3-minute step.
- [x] `(range(1, 3, 0.5))`: preview shows 1m, 1m 30s, 2m, 2m 30s, 3m.
- [x] `(5m "stretch")`: the step label shows while it runs and when it rings.
- [x] A relay that includes itself (`(Wake)` inside Wake): Save is refused, showing the loop.
- [x] A broken script (`(1,`): the error shows its line and column, and Start is disabled.
- [x] Tap a step in the preview, set "Wait for me" after it: only that boundary waits.
- [x] Rename Tea to `Chai`: relays using Tea now say Chai.
- [x] Delete a timer a relay uses: the dialog names that relay.

### Library
- [x] + → New header "Morning"; long-press a timer and drag it under the header.
- [x] Move headers up and down; rename and delete one (its timers drop back to the top).

### Restarts
- [x] Start a 5-minute timer, swipe the app away from recents: it still rings on time.
- [ ] Start a 10-minute timer, restart the phone, unlock: the timer is still there and rings on time.

## Uncommitted: languages and editors

- [x] Settings → Language → Norsk: everything is Nynorsk ("Innstillingar", "Tidtakarar", "Nytt relé").
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

- [x] Stopwatch → + → no name, no start time → Start: it counts, with hundredths.
- [x] + again with name "Run" and start time 1 m: shows 01:00.00 and counts up; "Started from" shows under it.
- [x] Lap at about 01:05: the lap shows 00:05 (measured from the start time) and 01:05 total.
- [x] Tap a lap: add a note; it shows under the lap. The lap time can't be changed.
- [x] Pause and Resume keep the time; Restart goes back to the start time and clears the laps.
- [x] While running, the notification counts up; its Lap and Pause work.
- [x] Swipe the app away, reopen: the stopwatch kept counting.
- [x] Rename and delete work.

## Uncommitted: clock and widgets (build step 7)

### Clock pane
- [x] A big clock on top, ticking every second, in the display font.
- [x] Tap it (or the palette icon): the style editor opens without the bottom bar, with a live preview.
- [x] Try each font, including 7-segment with seconds: the clock always fits the width.
- [x] Analogue: thick, thin and dot hands; each mark style; ring, filled and numeral faces; second hand on and off.
- [x] Change a colour (palette, then a custom one): the preview follows; Save keeps it.
- [x] + → search "tokyo" → pick: Tokyo shows "Tomorrow" or "Today" with "+7 h" or "+8 h", and the time.
- [x] Swipe a city away to delete; long-press and drag to reorder.
- [x] Settings → Clock → Home time zone → pick a city in another zone: a "Home" row appears at the top of the cities.

### Widgets
- [x] Long-press home screen → Widgets → Relay: placing it opens the setup screen first.
- [x] Back out of setup: no widget is added.
- [x] Save a digital widget: it shows the time; the minute changes on time.
- [x] Place a second, analogue widget for another city: both show their own time and style.
- [x] Background: transparent, colour, gradient; corners: sharp, rounded, pill; a border: all show on the widget.
- [x] Long-press a widget → Widget settings: change it; the widget updates.
- [x] Tap a widget: Relay opens on the Clock pane. Then change the language in Settings: it stays on Settings (the old app jumped back to Clock).
- [x] Leave the phone locked for 10 minutes, unlock: the widget shows the right time straight away.
- [x] Switch 24-hour clock off in Settings: widgets change to 12-hour.

## Uncommitted: relay language and editor fixes

- [x] `(range(1, 3))` previews 1m, 2m, 3m; `(range(1, 4, 2))` previews 1m, 3m.
- [x] `(25m  # focus` on one line, `5m)` on the next: two steps; the comment shows greyed and italic.
- [x] Typing `(` inside a comment adds no `)`; no name suggestions appear inside a comment.
- [x] "Wait for me": the "Cue between steps" row disappears. Back to "Continue by itself": it returns.
- [x] A broken script: Start and Save are both greyed out.
- [x] A valid script with no label: Start works, Save is greyed out.
- [x] A label with a space: the label field turns red and explains why; Save stays greyed out.
- [x] Timer editor: Save is greyed out without a valid label or duration.

## Uncommitted: stopwatch tags, corrections and chart

### Tags
- [ ] New stopwatch with tags "run" and "break": while running, buttons "Lap", "Lap: run" and "Lap: break" show.
- [ ] "Lap: run" makes a lap tagged run (shown under the lap).
- [ ] Tap a lap: edit its note and toggle its tags.
- [ ] Edit (pencil in the header): rename and add or remove tags.

### Corrections
- [ ] The lap list has faint column headers: Lap, Lap time, Total.
- [ ] Lap's pencil → Remove lap: it merges into the previous lap; the total doesn't change; ★ on the merged lap.
- [ ] Remove lap 1: merges into lap 2 instead.
- [ ] Insert lap into a 20 s lap at `5`: it becomes 5 s, then a new 15 s lap; later laps renumber.
- [ ] Adjust lap by `-5s`, then by `2m` (too much): the second is refused with "shorter than zero".
- [ ] `5min` isn't accepted; `5m` and `1:30` are.
- [ ] Undo and Redo (header arrows) step back and forth; ★ comes and goes with them.
- [ ] Corrections (n) lists every correction with its label; undone ones are greyed. A new correction removes the greyed ones.
- [ ] Take a new lap after a correction, then undo the correction: the new lap is kept.

### Chart
- [ ] The chart icon shows bars per lap, in order, with count, average, fastest, slowest.
- [ ] Tap a bar: its lap number and time show above the chart.
- [ ] Tick "run": only run-tagged laps show, and the stats follow.
- [ ] Running: first line is Lap, Pause, Restart; the tag-lap buttons are on a second line.
- [ ] Lap tags show as small outlined boxes; the note is on its own line in italic.
- [ ] New stopwatch with − ticked and 2 m: counts -02:00.00 up through 00:00.00 and on; the notification shows a minus too.

## Uncommitted: alarms that start things (build step 5)

- [ ] New alarm → "When it goes off" → Start a timer → pick a saved timer: the list says "Starts timer …". At the time, it starts quietly (no ringing screen) and its countdown notification appears.
- [ ] Start a timer → New → `2m`: starts a 2-minute timer at the alarm time, which rings 2 minutes later.
- [ ] Start a relay → pick a saved relay (e.g. Wake with "Wait for me"): starts at the alarm time; rings at its first boundary.
- [ ] Start a relay → New → write a script; the total shows; a broken script greys out Save.
- [ ] New inline timer with a script-safe label → Save to library: it appears in the Timers library and the alarm now uses it.
- [ ] Start a stopwatch with name "Run", start −1m and tag "run": at the alarm time it appears on the Stopwatch pane counting from −01:00, with a "Lap: run" button.
- [ ] Ringing options (sound, snooze, …) are hidden unless "Ring" is picked.
- [ ] Delete the saved timer an alarm uses, then let the alarm go off: it rings with "Couldn't start: …".
- [ ] A one-off "Delete after" alarm that starts a timer: gone from the list once it has started the timer.
- [ ] Picking a saved timer shows its length (on the chip and below); a saved relay also shows its step count.
- [ ] "Cue when it starts": each choice plays as you pick it; at the alarm time the chosen cue plays as the target starts (Silent plays nothing).

## Uncommitted: in-app guide

- [ ] Settings → How to use Relay: the guide opens from the top, with no bottom bar; back returns.
- [ ] The ? on Alarms, Timers, Stopwatch and Clock opens the guide at that pane's section; the ? in the relay editor opens the Relays section.
- [ ] Headings, lists, the code example and the tables read well; `code` bits are monospace.
- [ ] In Norsk, the guide is the Nynorsk one.

## v1.1 requests (PR #1, merged)

> Accepted on-device as a whole ("Testing it, I am happy"), not line by line. The boxes left
> open are worth a look when convenient; a broken one becomes a bug issue.

### Last pane
- [ ] Open Timers, leave the app (home), swipe it away from recents, open it again: it opens on Timers.
- [ ] Same with Stopwatch, then tap a clock widget: it opens on Stopwatch.
- [ ] Open Settings last, close and reopen: it opens on the pane you used before Settings.
- [ ] Change language in Settings: you stay on Settings.

### Timer length keypad
- [ ] + → New timer: the length field is empty and the keypad is already showing.
- [ ] Type `2`, `5`, `m`: the field shows `25m` and "= 25m" below it. Your phone's keyboard doesn't open.
- [ ] Type `1`, `h`, `3`, `0`, `m`: "= 1h 30m".
- [ ] Tap +5m on `25m`: it becomes `30m`. Tap +10m on an empty field: `10m`.
- [ ] ⌫ deletes one character; holding ⌫ (or C) clears the field.
- [ ] Tap the label field: the keypad hides and the keyboard opens. Tap the length field again: the keypad is back.
- [ ] Keyboard icon on the length field: the full keyboard opens; type `RANDOM(5, 10)` and it's accepted. The dialpad icon brings the keypad back.
- [ ] Edit an existing saved timer: the keypad stays hidden until you tap the length.

### Warned timer
- [ ] New timer `20m`, Warn before the end → 1m, cue Chime: at 19:00 the cue plays and the countdown keeps going (it doesn't restart at 1:00); at 0:00 it rings as usual.
- [ ] Short test: `2m` with a 1m warning: cue at 1:00 left, ring at 0:00.
- [ ] Pause during the last minute, then resume: the countdown carries on from where it was.
- [ ] Warning 5m on a `3m` timer: a red note says the warning won't sound; it just rings at the end.
- [ ] Custom → `90`: the chip shows "1m 30s".
- [ ] Save it, reopen: warning and cue are kept. Start from the library: same behaviour.
- [ ] Use the warned timer inside a relay: no warning cue there, just the relay's own boundaries.
- [ ] Installing this build over v1.0 keeps all your saved timers (database v6 → v7).

### Quick starts beside +
- [ ] Timers pane: four small buttons (1m 5m 10m 15m) sit left of the + button.
- [ ] One tap starts that timer. The + menu now only has New timer / New relay / New header.
- [ ] Change a quick duration in Settings (e.g. to 1h30m): the button shows it and the row still fits.

### Running notifications
- [ ] Start a timer and pull down the shade: its notification sits with the normal ones, not under "Silent", and it made no sound.
- [ ] Its countdown and buttons (Pause, +1 min, Stop) still work.
- [ ] Same for a running stopwatch.
- [ ] Settings → Apps → Relay → Notifications: no old "Timers"/"Stopwatch" duplicates are left.

## PR: Calendar pane, events and reminders (#14)

> 2026-10-25: the user liked the build; lines not ticked one by one.

### Navigation and Settings
- [ ] The bottom bar shows Alarms, Calendar, Timers, Stopwatch, Clock; Settings is gone from it.
- [ ] The gear at the top right of every pane opens Settings; its back arrow returns to that pane.
- [ ] Installing this build over the last one keeps every alarm, timer, relay and stopwatch (database v7 → v8).

### Month grid
- [ ] Calendar opens on this month with today circled. Arrows and a sideways swipe change month.
- [ ] Tap a day: it fills in, and its items list below. "Today" brings you back.
- [ ] With TalkBack, a day reads its date and how many items it has.
- [ ] Settings → first day of the week → Sunday: the grid starts on Sunday.

### Events
- [ ] + → New event on a selected day: the editor opens on that day. Save with a title, 10:00 to 11:00: it shows on that day as "10:00–11:00" with a dot in the grid.
- [ ] Set the end before the start: a red note appears and Save is disabled.
- [ ] All day on: the times disappear; the reminder offers "09:00 on the day" and "09:00 the day before".
- [ ] Last day two days later: the event shows on all three days, marked "Continues" after the first.
- [ ] Back with unsaved changes asks to save or discard.
- [ ] Delete asks first and says the reminder goes too; afterwards the day is empty.

### Reminders and Notify
- [ ] New reminder 2 minutes out (Notify, the default), lock the phone: a notification arrives with its title and time and a sound. No ringing screen.
- [ ] Tap the notification: Relay opens on Calendar.
- [ ] Event 20 minutes out with "15 min before": the notification comes 5 minutes from now and says "Today · HH:MM".
- [ ] Change a reminder to Ring: it rings like an alarm, with snooze.
- [ ] A reminder never shows in the Alarms list.
- [ ] Save a reminder in the past: the toast says the time has passed, and nothing goes off.
- [ ] Reboot with a reminder due in 5 minutes: it still notifies on time.
- [ ] An alarm with "When it goes off: Notify" shows "Shows a notification" in the Alarms list and notifies instead of ringing.

### Repeats
- [ ] Monthly on the 31st: the editor warns it skips months without day 31; the grid skips 30 November.
- [ ] Yearly on 29 February: the editor says "Only in leap years".
- [ ] Weekly on Mon and Wed, ends on a date: dots only on those days, up to that date.
- [ ] A monthly reminder still goes off the next month after it first notified.

### Everything scheduled
- [ ] The "Everything scheduled" chip adds your alarms (from today on) to the grid and the list; tapping one opens the alarm editor.
- [ ] Switch back to "Events and reminders": alarms disappear. Leave and come back: the choice is kept.

## PR: Calendar widgets and month picker (#15)

> 2026-10-25: after the layout fixes, both widgets "look right now".

### Month picker (app)
- [ ] Calendar pane: tap "October 2026": a dialog with the year and twelve months opens.
- [ ] Arrows change the year; pick a month two years ahead: the grid shows it, on its first day.
- [ ] "Today" in the dialog brings back this month with today selected.

### Agenda widget
- [ ] Long-press home → Widgets → Relay: "Calendar: next 7 days" and "Calendar: month" are listed next to the clock.
- [ ] Place the agenda: its setup opens (Show, Background, Corners, Border); Back leaves no widget behind.
- [ ] Save with Theme background: today's and the coming days' items are listed under day headings.
- [ ] Tap an item: Relay opens on its editor. Tap a heading: the Calendar pane opens on that day.
- [ ] Tap +: a new event opens on today.
- [ ] Add an event in the app, go home: the widget shows it within a second or two.
- [ ] Nothing planned this week: "Nothing in the next 7 days".
- [ ] Long list: it scrolls inside the widget.
- [ ] Show → Everything scheduled: your alarms appear too; tapping one opens the alarm editor.

### Month widget
- [ ] Place it (3×3): all seven weekday columns show their dates; today's number is in a filled pill.
- [ ] Days with plans show a label per item with its title: events, reminders and alarms in three colours.
- [ ] A day with more than fits shows "+n" in its last slot. Make the widget taller: more labels fit.
- [ ] Tap › and ‹: the month changes in the widget without opening Relay.
- [ ] Tap a day: Relay opens on that day. Tap the month name: Relay opens on that month.
- [ ] Settings → first day of week → Sunday: the widget's grid starts on Sunday after a moment.
- [ ] Agenda and month: the text sits inside the background, not past its left edge.

### Looks and updates
- [ ] Solid light colour background: the text turns dark; dark colour: light text. Gradient and border look like the clock's.
- [ ] Transparent background: light text over the wallpaper.
- [ ] Leave a widget overnight (or change the date in system settings): "Today" moves on.
- [ ] Reboot: the widgets still show and update.
- [ ] Widget settings from the launcher: changes apply when you save.
- [ ] A clock widget placed before this build looks exactly as before.

## PR: Calendars and multi-day items (#34, #35)

> 2026-10-25: the user reports it all works, update included.

### Update
- [ ] Install over the previous Relay Debug: every alarm and calendar item is still there (database v8 → v9).
- [ ] Calendar pane → layers button: Personal, Birthdays and Alarms are listed. Your items are in Personal, your alarms in Alarms.
- [ ] If you had "Everything scheduled" on before, Alarms is ticked; otherwise it isn't.

### Calendars
- [ ] Untick Personal: its items disappear from the grid and the list. Tick it again: they're back.
- [ ] Pencil on Birthdays: change the colour; its items' dots and rows change colour.
- [ ] + New calendar "Work", pick a colour, default reminder 1 hour: a new event in Work starts with "1 hour before".
- [ ] New event: it starts in the calendar used last. Switching calendar in the editor applies that calendar's defaults.
- [ ] Give one event its own colour: only that event changes colour.
- [ ] Birthdays: a new item there defaults to yearly.
- [ ] Mark Work as "New alarms go here": a new alarm's editor shows Work picked.
- [ ] Alarm editor: move an alarm to Personal; the Alarms list shows Personal's colour on its row.
- [ ] Delete Work: asked where to move its items and alarms; after picking Personal they're all there.
- [ ] Names show in Nynorsk with the app in Norsk (Personleg, Bursdagar, Alarmar), until you rename one.

### Multi-day items
- [ ] An event Friday to Monday: one bar across Fri–Sun, continued on Monday's row of the next week.
- [ ] Tap the bar: its editor opens. Select Saturday: the list shows it once as "Fri 30 – Mon 2".
- [ ] Agenda widget: it's listed once, under the first day it's on in the coming week.
- [ ] Month widget: a bar across its days; several at once stack, and any beyond two show in "+n".

### Widgets
- [ ] Widget settings: calendar checkboxes instead of Show. A widget made before keeps showing what it did.
- [ ] Untick a calendar in one widget only: that widget hides it, the pane and other widgets don't.
- [ ] Agenda rows have a colour bar; month labels use each item's colour.

