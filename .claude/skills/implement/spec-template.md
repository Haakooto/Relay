# Spec template

The shape of a feature spec agreed in `/implement` step 1a. Keep it as short as the feature
allows; a small feature may only need sections 1, 3, 4 and 8.

## 1. Summary

One paragraph: what the user can do afterwards that they can't today. Link the issue.

## 2. User stories

- As someone who ..., I want ..., so that ...
- Two to five of these. The first is the main one.

## 3. Scope

| Sub-feature | Now / Later | Note |
|---|---|---|
| ... | Now | ... |

Out of scope, explicitly: the things people might expect that this does not do.

## 4. Behaviour

What happens, step by step, for the main story. Then the edge cases that apply:

- reboot, force-stop, app update, Doze, time or time-zone change
- two of the same thing at once; this feature colliding with an alarm or a running relay
- empty state, first run, very long labels, Nynorsk text (often longer), large font size
- permission denied or revoked
- undo, delete, and what happens to things that reference it

## 5. Design

Which pane and where. Sketch the screen or the change in words or ASCII. Reuse existing
components (`ui/common/`, the editor pattern, `DiscardGuard`). Note what the stock Clock does,
if it does this, and whether to copy it.

## 6. Data and architecture

- New or changed entities, settings keys, Room version and migration.
- Which repository owns the commands; which existing primitive it builds on (schedule, run,
  firing module, relay language).
- Any refactor needed first.

## 7. Requirements

- **Reliability:** anything that rings goes through the firing module; what re-arms on boot.
- **Privacy and security:** permissions, exported entry points, data leaving the phone (should
  be none), input validation.
- **Accessibility:** content descriptions, contrast, touch targets.
- **i18n:** strings in English and Nynorsk; date, time and number formats.

## 8. Testing

- Unit tests (what rules they pin down).
- Migration test, if the schema changes.
- Device-test lines for `docs/device-tests.md`.

## 9. Suggestions

Ideas Claude raised, with the user's decision on each (taken now, later, or declined).

## 10. Open questions

Anything still undecided. Empty before building starts.
