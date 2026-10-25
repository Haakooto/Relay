# Relay: usage guide

Relay replaces the stock Clock app: alarms, timers, stopwatches, a world clock and home-screen
widgets. What sets it apart: timers can be chained into **relays**, alarms can **start** a timer,
relay or stopwatch instead of just ringing, and stopwatches keep **tagged, correctable laps**.

The five panes along the bottom are Alarms, Calendar, Timers, Stopwatch and Clock. Relay opens
on the pane you used last. **Settings** is the gear at the top right of every pane.

---

## Alarms

**Make one.** Tap **+**. The time picker opens first, then the rest of the alarm:

- **Label**: shown when it rings.
- **Repeat**: tick weekdays. With none ticked it's a one-off: pick the date (default: the next
  time that clock time comes round), and whether it is **deleted** or **kept, switched off**
  afterwards.
- **Ends** (repeating only): never, on a date, or after a number of times.
- **Sound, vibration, gradual volume increase**, **snooze length** (any number of minutes) and
  **max snoozes** (None, a number, or unlimited).

**Save** sits next to the time. A toast tells you how long until it rings, and the list is
sorted by what rings next.

**When it rings** it takes over the screen, even when the phone is locked, and keeps ringing
until you act: **Snooze** or **Dismiss**. Several alarms at once stack on the same screen. An
alarm nobody answers stops after 10 minutes (Settings → Stop ringing after) and leaves a
notification.

**Exceptions** (on a saved alarm): skip a day or a date range, or ring at another time on those
days. "Also apply to" adds the same exception to other alarms, which is handy for a holiday week.

**Before it rings**, an "Upcoming alarm" notification appears (2 h ahead by default, set in
Settings). Its **Dismiss now** skips just that one occurrence.

**If the phone was off** when an alarm was due, it rings when the phone is back on, as long as
that's within an hour, and says "Delayed from …". Older misses leave a notification instead.

**The red banner** at the top of Alarms appears only when something would stop alarms from
working (notifications off, full-screen blocked, alarm volume at zero, battery optimisation).
Each line has a button to fix it.

### Alarms that start things

Under **When it goes off**, an alarm can do more than ring:

| Choice | At the alarm time |
|---|---|
| **Ring** | A normal alarm. |
| **Notify** | Shows a notification with its usual sound. Nothing rings, and there is no snooze. |
| **Start a timer** | Starts a saved timer, or a **New** one you define right there. |
| **Start a relay** | Starts a saved relay, or a **New** one written right there. |
| **Start a stopwatch** | Creates a stopwatch with the name, start offset and tags you give, and starts it. |

These start quietly, with just a short **cue** (pick it under "Cue when it starts"), and then
behave as if you'd started them by hand. If the target can't start, for example because the
saved relay was deleted, the alarm **rings and tells you why** instead. **Save to library**
turns a new timer or relay into a saved one.

Example: a repeating weekday alarm at 06:30 that starts your *Wake* relay (below) gives you a
snooze-less wake-up routine.

---

## Calendar

The Calendar pane is for planning: **events** (with a start, an optional end and a note) and
**reminders** (a single moment). Tap **+** for either; it starts on the day you have selected.

**The month.** Days with something on have a dot in the item's colour; something that lasts
several days is one bar across them. Tap a day to list it below the grid; swipe sideways or use
the arrows to change month, and **Today** to come back. Tap the month's name to jump to any month
and year.

**Calendars.** Every item, and every alarm, belongs to a calendar: Personal, Birthdays and Alarms
to start with. The layers button at the top lists them: tick the ones to show (alarms show from
today on), tap the pencil to change a calendar's name, colour and the defaults its new items get
(reminder, what it does, repeat), or add a new one. One calendar is where new alarms go. Deleting
a calendar asks where its items and alarms should move. In the editor, pick an item's calendar,
and give it its own colour if it shouldn't take the calendar's.

**Google and other calendars.** Under **On this phone** in the same list, **Allow calendar access**
lets Relay show the calendars already on your phone (Google, shared and holiday calendars), each
with its own tick and colour. Their events are read-only here; tapping one opens it in its own
calendar app. A Relay calendar can also **copy its items** to one of them (in its edit dialog):
new and changed items are copied, deleted ones removed, and the copies don't show twice. Relay
reads and writes them on the phone itself; your calendar app syncs them, Relay uses no network.

**Calendar files (.ics).** Open or share an .ics file into Relay (from Files, an email or a
browser), or use **Import file** in the calendars list: Relay shows what it found and asks which
calendar to put it in. Importing the same file again updates what it imported before instead of
adding it twice. A repeat Relay can't follow comes in as its first date, with a line in the note
saying so. To send items elsewhere, **Export** in a calendar's edit dialog shares the whole
calendar, and the share button in an item's editor shares just that item.

**An event** is all day or has a start time, and can end later that day or on a later day.
Under **Reminder**, pick when to be reminded (at the start, minutes or a day before; for an
all-day event, 09:00 on the day or the day before) and what happens then. **Notify** is the
default; Ring and starting a timer, relay or stopwatch work as for alarms.

**A reminder** goes off at its own time and notifies you by default.

**Repeat**: daily, weekly on chosen days, monthly or yearly, **every** one, two or more days,
weeks, months or years. A monthly or yearly repeat lands on the same date, or, depending on the
item's date, on the same weekday ("the second Tuesday"), the last such weekday ("the last
Friday") or the last day of the month. A month without the date is skipped (a monthly 31st skips
April; a yearly 29 February only comes in leap years), and the editor warns you. **Ends**: never,
on a date, or after a number of times.

A reminder whose time has already passed is saved but won't go off. Deleting an event deletes
its reminder too. Reminders don't appear in the Alarms list.

---

## Timers

**Quick timers.** The small buttons left of **+** start a 1, 5, 10 or 15 minute timer in one
tap. You can change the four durations in Settings.

**New timer.** It has a **duration**, a **label**, an optional **warning** and a **sound**.
The duration comes first, on a keypad: digits, `h`, `m`, `s`, and **+1m**, **+5m**, **+10m**.
Long-press ⌫ to clear. The keyboard icon switches to the full keyboard for anything else the
relay language can say.
- **Start** runs it once without saving. The label still shows on the countdown and when it
  rings.
- **Save** puts it in the library. Saved labels must be letters, digits and `_` only (for example
  `Tea`, `Work_25`), because relays refer to timers by that label.

Durations are written like `25m`, `1h30m`, `45s` or `90`. A plain number means minutes.

**Warning.** "Warn before the end" plays a short cue that long before the timer rings: a
20 minute timer with a 1 minute warning cues at 19 minutes. The countdown still runs to the
real end. Inside a relay, a timer's warning is ignored.

**Running.** Every start gets its own card at the top of the pane and its own notification with
a live countdown. You can run the same timer several times at once. The controls are **Pause**
/ **Resume**, **+1 min**, **Reset** (back to the start, paused) and **Stop**. When it ends it
rings like an alarm, with **Stop**, **Restart** and **+1 min**. Timers keep running when the app
is closed, and after a phone restart too.

**Library.** Saved timers and relays are listed below the running ones. **▶** starts one, and
tapping it opens it for editing. Editing only takes effect when you tap Save. **+ → New header**
groups items under a header: long-press an item and drag it under a header, and use the arrows
to reorder headers.

---

## Relays

A relay is a chain of steps that run one after another. A step is a plain duration or a saved
timer or relay. You write relays as a short script.

### The script

```
(
  Work, Break,          # saved timers, by label
  Work, Break,
  15m "long break",     # an unnamed step with a label
  repeat(4, (Work, Break))
)
```

| You write | Means |
|---|---|
| `25m`, `1h30m`, `45s`, `20` | A step of that length. A plain number is minutes. |
| `Work` | A saved timer or relay, run as a step (or several), with its own sound. Changing *Work* later updates every relay that uses it. |
| `Work - 2` | One step 2 minutes shorter than *Work*, with Work's sound. A name inside arithmetic stands for its length. |
| `5m "stretch"` | A label for an unnamed step, shown while it runs and when it rings. |
| `repeat(4, (Work, Break))` | Repeats anything, names included. |
| `range(1, 3)` | Steps of 1, 2 and 3 minutes. `range(1, 4, 2)` gives 1 and 3. `range(1, 3, 0.5)` counts in half minutes. The end is included when a step lands on it. |
| `MINUTE(NOW)`, `RANDOM(5, 10)` | Worked out once, when the relay starts. |
| `# …` | A comment: the rest of the line is ignored. |

Steps are separated by commas or new lines. The **?** in the editor shows this table.

**The editor helps.** It closes brackets and quotes, indents on Enter, and suggests saved names
as you type (it never offers names that would make a relay contain itself). Below the script, a
live preview lists the steps and the total, and points at mistakes with line and column. Start
and Save stay greyed out until the script is valid. Save also needs a valid label.

### Between steps

- **Continue by itself**: each step ends with a short cue (Beep, Chime, …, or Silent), and the
  next step starts at once. Only the end of the relay rings.
- **Wait for me**: each step end rings until you tap **Continue** (or **Stop relay**).

**Per step:** tap a step in the preview to give it its own sound, or a different "after this
step" choice. For example, the relay can run on by itself into *Break* but wait for you before
*Work* starts again.

A useful wake-up relay is five short timers with louder and louder sounds, set to "Wait for me".
Each tap on Continue buys five more minutes, and **Stop relay** ends it when you're up.

---

## Stopwatch

Tap **+** to start one. You can give it:
- a **name**,
- a **start offset**: start at 1:00, or tick **−** to start below zero. −2 m counts up from
  −2:00 through zero and keeps going,
- **tags** (for example `run` and `break`). Each stopwatch has its own tags.

You can run as many stopwatches as you like. They keep counting when the app is closed or the
phone restarts. The first line of controls is **Lap**, **Pause** and **Restart** (back to the
start offset, laps cleared). If the stopwatch has tags, a second line has a **Lap: run** button
per tag, which takes a lap and tags it in one tap.

**Laps** show their own time and the running total. The first lap is measured from the start
offset. Tap a lap to add a **note** or change its **tags**: tags show as small boxes, notes in
italics.

**Fixing mistakes.** The pencil on a lap offers three corrections. None of them change the total
time:
- **Remove lap**: merges it into the lap before (lap 1 merges into lap 2).
- **Insert lap**: splits the lap at the time you give. A 20 s lap split at `5` becomes 5 s, then
  15 s.
- **Adjust lap**: moves the lap's end by an amount like `-5s`, `2m`, `1:30` or `5`. A plain
  number means seconds here. The next lap takes or gives the difference.

A lap can never end up shorter than zero. Each correction can have a reason. Corrected laps get
a **★** (tap it to see the history). The arrows in the card header **undo** and **redo**, as far
back as you like, and **Corrections** lists every change.

**Chart.** The chart icon shows one bar per lap, with count, average, fastest and slowest. Tap a
bar for its exact time, or tick tags to only see, say, the `run` laps.

---

## Clock

The Clock pane shows a big clock with your cities below it, each with its time and how far ahead
or behind it is ("Tomorrow, +9 h"). **+** searches for a city. Swipe a city away to remove it,
and long-press to drag it.

**Style.** Tap the clock (or the palette icon) to style it, with a live preview:
- **Digital**: font (System, Serif, Mono, 7-segment, Display), 12/24-hour, seconds, date.
- **Analogue**: hands (thick, thin, dots), marks (minutes, hours, quarters, none), face (ring,
  filled, numerals, none), second hand.
- **Colours** for each part: Auto (follows the theme), the palette, or any custom colour.

The font applies to the digits, the numerals and the date.

**Home clock.** Set Settings → Clock → Home time zone. When you travel, a "Home" row appears at
the top of the cities.

## Widgets

Long-press the home screen → Widgets → Relay. Each widget gets its own setup:
- the **style** (all the Clock options above),
- a **city** (or this phone's time),
- a **background** (theme, transparent, colour or gradient),
- **corners** and a **border**.

Every widget can look different. To change one later, long-press it and pick Widget settings.
Calendar widgets have their own calendar ticks, separate from the pane's, phone calendars included.
Tapping a clock widget opens Relay on the pane you used last.

**Calendar widgets.** Two more, each with its own setup: which **calendars** to show, and the
same background, corners and border as the clock.

- **Calendar: next 7 days** lists what's coming, day by day, with each item's colour; something
  lasting several days is listed once, with its dates. Tap a row to open it, a day to open
  the Calendar pane there, and **+** for a new event.
- **Calendar: month** shows the month with each day's plans as small labels in their colours,
  bars across the days for things that last longer, and "+n" when they don't all fit; a taller
  widget fits more.
  **‹** and **›** change month right on the home screen; tapping a day or the month's name opens
  the Calendar pane there.

They update when you change something in Relay and at midnight.

---

## Settings

- **Display**: theme (System, Light, Dark, AMOLED), 24-hour clock, first day of the week,
  language (English or Norsk, which is Nynorsk).
- **Alarms**: default snooze, default sound, default volume increase, how long a ring lasts,
  and the upcoming-alarm notice.
- **Clock**: home time zone.
- **Timers**: the four quick-start durations, default timer sound, max steps in a relay, and when
  to warn about very long relays.
- **General**: haptics, app version.
