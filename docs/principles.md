# Relay: design and engineering principles

The rules every feature, fix and refactor is held to. The `/feature-request`, `/bug-report` and
`/implement` skills check work against this page, and a PR that breaks a rule says why. Product
goals and non-goals live in the wiki ([Design spec §01](https://github.com/Haakooto/Relay/wiki/Design-Spec#01--goals--principles));
this page repeats the ones that decide day-to-day work and adds how the code is built.

Change a rule here only on purpose, in its own commit, and say why in the message.

## 1. Product

1. **Nothing that should ring may fail to ring.** Reliability beats every feature. A change that
   could delay, drop or silence a ring is wrong even if the feature is nice. A ring the user
   chose to make silent is not a failure: Relay never goes silent on its own (a missing sound
   falls back to the default), but it honours a silent choice the user made on purpose.
2. **One app, no companion needed.** Relay replaces the stock Clock. Where the stock app is good,
   copy it closely; spend novelty only where it is limited.
3. **Local and private.** No account, no sync, no analytics, no ads, no network by default.
   The app has no `INTERNET` permission today; a feature that needs one (webhooks, #19) makes it
   opt-in, says so in the UI, and keeps working with it denied.
4. **One primitive per idea.** Anything timed is a schedule; anything that runs is a run of a
   definition; anything that makes noise goes through the firing module (spec §13). A new
   feature reuses these before it invents a parallel path.
5. **Definitions vs. runs.** What you saved is never mutated by running it. Runs can be many.
6. **Facts stay facts.** Recorded history (laps, a fired alarm) is never silently rewritten.
   Corrections are explicit and undoable.
7. **Small surface.** A setting is added only when two reasonable people would want different
   behaviour. Prefer a good default to an option.

## 2. Interaction

1. **Fast for the common case.** The thing most people do on a pane is one tap away
   (quick-start, new alarm opens on the time picker).
2. **Confirm in words, not just state.** After a change that affects the future, say what will
   happen ("Rings in 7 h 12 min").
3. **No dead ends, no silent failures.** A start that can't happen explains why; a broken relay
   reference is shown, not swallowed. Error text may stay English only (not translated).
4. **Editors are modal.** Leaving means Save or Discard (`DiscardGuard`). No half-saved state.
5. **Destructive actions are reversible or confirmed.** Undo where it is cheap, a dialog where
   it isn't, and say what else is affected (a relay that references the timer you delete).
6. **Readable at a glance.** Tabular numerals wherever digits line up; times follow the
   locale and the 24 h setting (`util/TimeText.kt`, `util/AppLocale.kt`).
7. **Accessible.** Every control has a content description, touch targets are at least 48 dp,
   text meets 4.5:1 contrast in every theme (spec §15), and nothing depends on colour alone.
8. **Bilingual from the first screen.** Every user-facing string is in `values/strings.xml`
   (British English) and `values-nn/strings.xml` (Nynorsk), copied to `values-nb`.
9. **The guide follows the app.** A behaviour change updates `docs/usage-guide.md` and
   `docs/usage-guide.nn.md` in the same PR.

## 3. Architecture

The app is layered. Each layer only talks to the one below it.

```
ui/<pane>/        Compose screens + one ViewModel per screen (state as StateFlow, events in)
<domain>/         Repositories: the command layer. All writes, all invariants, all re-arming
data/<domain>/    Room entities and DAOs, DataStore settings. No logic beyond queries
firing/           The firing module: the only code that plays sound or shows a ringing screen
chainlang/        The relay language, plain JVM, no Android
```

1. **Repositories are the command layer.** UI, receivers, widgets and (later) automation all go
   through the same repository method, so every entry point gets the same invariants and
   re-arming. No DAO calls from UI or receivers.
2. **Writes are serialised.** A repository that arms alarms holds one `Mutex` for writes, so a
   fire, a snooze and an edit can't interleave.
3. **Android stays at the edges.** Repositories depend on small interfaces (`ScheduleArming`,
   `ScheduleNotices`) and on injected `now()` and `zone()`, so they are unit-tested on the JVM
   with fakes. New repositories follow the same shape.
4. **One container.** Dependencies are wired in `AppContainer`, lazily, one per process. No
   service locators elsewhere, no new DI framework.
5. **Pure logic in pure functions.** Time math (`NextFire`), plans (`PlanBuilder`), lap edits
   (`LapEdits`) are plain functions or objects with no Android types, and have tests.
6. **ViewModels are thin.** They map repository flows to UI state and forward intents. Business
   rules in a ViewModel are a smell; move them down.
7. **Composables are stateless where possible.** Screen-level composables take state and
   lambdas; previews and reuse follow. Shared widgets live in `ui/common/`.
8. **Firing is append-only territory.** Changes to `firing/`, `schedule/` arming and `run/`
   scheduling are narrow and additive, come with tests for the edge (reboot, Doze, late fire,
   duplicate fire), and are called out in the PR for an on-device test.

## 4. Code

1. **Read like the surrounding code.** Same naming, same comment density, same idioms.
2. **Comments say why**, not what. KDoc on public types and on anything with an invariant.
   Spec references look like `spec §13`.
3. **Small files, one idea each.** Around 300 lines is the point to ask whether a file holds two
   ideas. Split by responsibility, not by line count.
4. **No dead code, no speculative abstraction.** Build what the feature needs; generalise on
   the second real use, not the first imagined one.
5. **Names from the domain.** In the app it is *relay* (`RelayDef`), never *chain*; the
   `chainlang` module keeps its name internally.
6. **Errors are written for the user.** A message that reaches the UI says what went wrong in
   plain words. English only is fine; a stack-trace-style message is not.
7. **Dependencies are a cost.** A new library needs a reason it can't be twenty lines of our
   own, an active maintainer, and a compatible licence. Never add one for a single helper.
8. **Tests come with the change.** Every repository rule, time calculation and parser change
   has a unit test. Bugs get a failing test first when the bug is testable on the JVM.

## 5. Data

1. **The user's device carries real data.** Every Room version bump has a real `Migration` and
   a `MigrationTest`. Never destructive fallback. Commit the exported schema JSON.
2. **Additive schema changes.** Prefer a new nullable column or a new table over reshaping an
   old one. Renames are a migration, not a delete-and-add.
3. **JSON columns are versioned by shape.** `@Serializable` types stored in a column only gain
   optional fields with defaults, so old rows still decode.
4. **Settings keys are added when first needed**, never ahead.

## 6. Security and privacy

1. **Least privilege.** Add a permission only in the change that needs it, explain it in the PR,
   and degrade gracefully when it is denied or revoked.
2. **Exported components are few and validated.** Only the launcher, widget and system
   receivers are exported. An exported entry point (an intent, an AppFunction) validates every
   extra and never trusts the caller.
3. **PendingIntents are immutable** unless a system API requires otherwise, with a comment why.
4. **No user content in logs.** Labels, scripts and notes are not logged.
5. **Secrets never enter the repo or a prompt.** `keystore.properties`, `*.jks`, `.env/` and
   tokens are never read, printed or committed. CI gets them from repository secrets.
6. **Imported data is untrusted.** Batch import, restore and inbound intents are parsed with
   size limits and safety checks, like the relay language (`SafetyLimits`).
7. **Outbound data is explicit.** If a feature sends anything off the phone, the user set it up,
   can see what goes where, and can turn it off.
