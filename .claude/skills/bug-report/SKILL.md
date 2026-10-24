---
name: bug-report
description: Turn a description of something broken in Relay into a GitHub bug issue, after checking existing issues for a duplicate and looking at the likely code. Use when the user reports a bug, a crash, something not ringing, or a failed device-test line.
argument-hint: <what went wrong>
---

# Bug report

Turn the user's report into one clear bug issue, or add it to the issue that already covers it.
The issue says **what is wrong and how to see it**; the fix is for `/implement`.

Input: `$ARGUMENTS` (if empty, ask what happened).

## 1. Get the facts

A good bug has: what the user did, what they expected, what happened, the version and whether
it was the debug build, and the phone/Android version. Fill in what you can from context
(`docs/device-tests.md` lines marked `[!]` have notes; `git describe --tags` gives the latest
version). Ask only for what is missing **and** matters for reproducing it; one short message.

Severity, highest first:

1. **Something that should ring didn't, or rang late or silently.** Always `area: reliability`.
   Put "does not ring" (or similar) in the title.
2. Data loss or corruption (an alarm, timer or lap gone or wrong after an update or reboot).
3. Crash or a stuck screen.
4. Wrong behaviour with a workaround.
5. Cosmetic, text or translation.

## 2. Look for a duplicate

```bash
gh issue list --state all --label bug --json number,title,state,labels
gh issue list --state all --search "<2-3 key words>" --json number,title,state
```

Read any plausible match (`gh issue view <n>`). Also check `docs/device-tests.md` for a line
that already describes it.

| Finding | Action |
|---|---|
| Open issue, same bug | Don't create. Offer a comment with the new details (version, device, steps). |
| Closed issue, same bug | It has regressed. Create a new issue that links it ("Regression of #n"). |
| Not a bug (works as designed in the spec) | Say so with the spec section, and offer `/feature-request` if the user wants it changed. |
| Nothing matches | Create a new issue. |

## 3. Take a quick look at the code

Spend a few minutes, not an investigation: find the code path that most likely owns the
behaviour (`schedule/`, `run/`, `firing/`, the pane's ViewModel or repository) and note it in
the issue as a lead, worded as a guess. Don't fix anything here.

## 4. Draft the issue

Title: the symptom, plainly ("Alarm does not ring after a reboot before the first unlock").

Body (British English), matching the bug form in `.github/ISSUE_TEMPLATE/bug_report.yml`:

```markdown
**Version:** v1.1 (release) · **Phone:** Pixel 8, Android 15

## Steps to reproduce

1. ...

## Expected

...

## Actual

...

## Notes

Severity and why. Anything relevant: battery settings, reboot, relay script involved,
how often it happens.

## Lead

Where in the code this probably lives, as a guess: `run/RunRepository.kt` `rearmAll()`.

## Related

Linked issues, device-test section, spec section.
```

Labels: `bug` plus `area: ...`; add `area: reliability` for anything about ringing, re-arming,
boot or Doze. Add `needs-device-test` only when a fix is built and waiting.

## 5. Confirm, then create

Show the draft and the issues you checked. Create only after the user agrees:

```bash
gh issue create --title "..." --label "bug" --label "area: ..." --body-file <tmpfile>
```

Report the URL. If a device-test line was `[!]`, offer to add the issue number to its note.

Strip personal data from anything pasted in (names in labels, phone numbers, account names in
logs) and never include tokens or keys.
