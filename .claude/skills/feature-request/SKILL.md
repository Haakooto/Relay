---
name: feature-request
description: Turn a short description of a Relay feature idea into a GitHub issue, after checking existing issues and the roadmap for a match. Use when the user describes something Relay should do, asks to file or log a feature, or says "feature request".
argument-hint: <what the feature should do>
---

# Feature request

Turn the user's idea into one well-placed GitHub issue. The issue captures **the idea and why it
matters**, not the exact design: the detailed spec is written later by `/implement`.

Input: `$ARGUMENTS` (if empty, ask the user for a sentence or two about the idea).

## 1. Understand it

Restate the idea to yourself as: *who* wants to do *what*, and what gets in the way today.
Ask the user one short question only if the core problem is unclear. Don't ask about details
(layout, settings, exact behaviour): those belong to `/implement`.

## 2. Look for a match

```bash
gh issue list --state all --limit 200 --json number,title,state,labels
gh issue list --state all --search "<2-3 key words>" --json number,title,state
```

Then read the bodies of anything plausibly related (`gh issue view <n>`), and the wiki
Roadmap and spec section for the area (`.claude/scripts/wiki.sh` prints the wiki path; read
`Roadmap.md`, `Feature-Map.md` and the relevant spec page). Also check the non-goals and
declined items in `Roadmap.md` and in `docs/principles.md`.

Decide one of:

| Finding | Action |
|---|---|
| An open issue already covers it | Don't create. Offer a comment on that issue with what is new in this request. |
| A closed issue covers it | Say whether it was built (and in which release) or declined, and why. Offer to reopen only if it was declined for a reason that no longer holds. |
| It overlaps an issue but is a different idea | Create a new issue that links the other one ("Related: #n"). |
| It conflicts with a principle or non-goal | Say which one, and ask whether to file it anyway (with the conflict noted) or drop it. |
| Nothing matches | Create a new issue. |

## 3. Draft the issue

Title: a short noun phrase for the capability, sentence case, no trailing full stop
("Export stopwatch laps as CSV", "Snooze from the upcoming-alarm notification").

Body, in this shape (keep it under ~25 lines; British English):

```markdown
## The problem

What the user wants to do, and what gets in the way today (or what the stock Clock does
badly). One short paragraph.

## The idea

The capability in plain words: what becomes possible, not how the screen looks. One or two
paragraphs. Mention the existing primitive it would build on (schedules, runs, the firing
module, the relay language, widgets) if one obviously fits.

## Why it fits Relay

One to three bullets tying it to `docs/principles.md` or spec §01 goals.

## Open questions

Bullets for the big unknowns only (scope, where it lives, what it should not do). These are
the starting points for `/implement`.

## Related

Links to related issues and the spec section, e.g.
[Spec §05, timers and relays](https://github.com/Haakooto/Relay/wiki/Panes-Spec#05--timers--relays).
```

Labels: `enhancement` plus one or more `area: ...` labels (`gh label list`). Add `i18n` or
`accessibility` when that is the point of the request. Do **not** add `roadmap` or `deferred`:
placing it on the roadmap is the user's call.

## 4. Confirm, then create

Show the user the title, labels and body, plus which existing issues you checked and why none
matched. Create it only after they agree (or after they adjust it):

```bash
gh issue create --title "..." --label "enhancement" --label "area: ..." --body-file <tmpfile>
```

Write the body to a temp file first (in `$CLAUDE_JOB_DIR/tmp` if that is set, else `mktemp`),
so quoting can't mangle it. Report the issue URL.

## 5. Afterwards

Offer, don't do: adding it to the wiki `Roadmap.md` (a separate commit in the wiki repo,
pushed by the user unless told otherwise) and to `Feature-Map.md` as `Planned`.

Never paste secrets, tokens, personal data or log excerpts with personal data into an issue.
