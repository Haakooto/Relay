---
name: implement
description: Build a Relay feature or fix a bug from a GitHub issue, end to end. For features it first works out a detailed spec with the user (design, sub-features, requirements) and proposes improvements; then it plans, refactors if needed, builds with tests, updates docs and opens a PR. Use when the user says implement, build, fix, or "do #n".
argument-hint: <issue number, or a short description>
---

# Implement

Build one issue to the standard in `docs/principles.md`, leaving the code easier to extend than
it was. Input: `$ARGUMENTS`, normally an issue number.

If there is no issue yet, run the `feature-request` or `bug-report` flow first (CLAUDE.md: every
feature and fix has an issue that its PR closes).

## 0. Load the context

Read, in this order, and keep notes short:

1. The issue and its comments: `gh issue view <n> --comments`.
2. `docs/principles.md` and `CLAUDE.md`.
3. The wiki (`.claude/scripts/wiki.sh` prints its path): the spec section the issue links,
   the `Feature-Map.md` rows for the area, `Roadmap.md`.
4. The code in the area: the repository, its DAO and entities, the ViewModel and screen, and
   their tests. `git log --oneline -- <paths>` shows how it got here.
5. `docs/usage-guide.md` for the pane, and the newest sections of `docs/device-tests.md`.

Then tell the user in two or three lines what exists today that the work builds on.

## 1a. Feature: work out the spec with the user

Issues are deliberately high level. Before any code, turn the idea into a spec the user has
agreed to. Use [spec-template.md](spec-template.md) as the shape.

1. **Draft first, ask second.** Write a first draft of the spec yourself from the issue and the
   code: user stories, the sub-features split into *must* and *later*, behaviour including the
   edge cases (reboot, Doze, time-zone change, two at once, empty state, very long labels,
   Nynorsk text length), data changes, and what it must not do.
2. **Bring ideas.** Add a short "Suggestions" list of 3 to 6 concrete ideas that would make the
   feature more useful or safer, each tied to a principle: a better default, reusing an existing
   primitive instead of a new one, an accessibility or i18n point, a privacy or permission
   concern, a reliability risk and how to avoid it, a simpler cut that delivers most of the
   value. Mark each as "suggest for now" or "later". Say plainly when an idea is a bad fit.
3. **Ask only real decisions.** Use AskUserQuestion for the choices that change what gets built
   (scope cut, where it lives in the UI, a default, which suggestions to take). Put your
   recommendation first. Up to four questions per round; usually one or two rounds is enough.
   Don't ask what the code, the spec or a principle already answers.
4. **Show the final spec** and get a clear yes. Then record it:
   - the wiki spec page for the area gets the design (edit in `.wiki/`, commit there; the user
     pushes the wiki unless told otherwise);
   - the issue gets a comment with a short summary and a link to the spec section, posted after
     the user agrees to it.

## 1b. Bug: find the cause

1. Reproduce it in a JVM unit test where possible (repositories take fakes and an injected
   `now()`; see `ScheduleRepositoryTest`, `RunRepositoryTest`). The test must fail before the
   fix.
2. Find the root cause, not the symptom. Check whether the same mistake exists elsewhere.
3. If the right fix changes designed behaviour, it is a spec change: say so and ask.
4. For anything in `firing/`, schedule arming or run scheduling: keep the fix narrow and
   additive, list the edge cases you checked, and flag it for an on-device test.

## 2. Plan

Write a short plan for the user before coding:

- files to add or change, layer by layer (data, repository, ViewModel, screen), following the
  architecture in `docs/principles.md` §3;
- Room changes: new version, the `Migration`, the `MigrationTest` case;
- new strings (English and Nynorsk), new permissions or settings keys (only if needed now);
- tests to add;
- **refactors**: if the area would make the feature messy (logic in a ViewModel, a file that
  holds two ideas, a second copy of something that exists, a missing seam for testing), propose
  the refactor. Do it as its **own commit before the feature**, with no behaviour change and
  with tests passing on both sides. If it is large, suggest a separate issue and PR instead.

Wait for a go-ahead if the plan contains a refactor, a schema change, a new permission or a
new dependency. Otherwise continue.

## 3. Build

- Branch: `git switch -c feature/<short-name>` from an up-to-date `main`.
- Follow `docs/principles.md`. Match the surrounding code. No em dashes in user-facing text.
- Strings: edit `values/strings.xml` (British English) and `values-nn/strings.xml` (Nynorsk),
  then copy `values-nn/strings.xml` over `values-nb/strings.xml`.
- Room: bump the version, add the `Migration` to `RelayDatabase.MIGRATIONS`, add a
  `MigrationTest` case. The schema JSON comes back from CI as the `room-schemas` artifact;
  commit it.
- Tests next to the code they cover (`app/src/test/...`, `chainlang/src/test/...`).

## 4. Docs, in the same PR

- `docs/usage-guide.md` and `docs/usage-guide.nn.md` when behaviour the user sees changes
  (short; keep the `##` sections in `GuideSection` order).
- `docs/device-tests.md`: a new section for this build, one action and one expected result per
  line, covering the happy path and the edge cases from the spec.
- Wiki (separate repo, separate commit in `.wiki/`): the `Feature-Map.md` row set to `WIP` with
  a one-line note and the header counts updated; `Roadmap.md` in step; the spec page if the
  design moved during the build.

## 5. Verify and hand over

If this machine can build (`java` and an Android SDK, see CLAUDE.md), run
`./gradlew :chainlang:test :app:testDebugUnitTest` first and fix failures locally. There is no
emulator either way. Before pushing, ask the user to confirm the commit, push and PR (one
question covering all three). Then:

```bash
git add <files> && git commit        # one commit per feature (plus any refactor commit before it)
git push -u origin feature/<short-name>
gh pr create --title "..." --body-file <tmpfile>   # body ends with "Closes #n"
gh run watch                          # then gh run view --log-failed on failure
```

Fix CI failures and push again. When green, report: the PR link, the dev APK page
(`dev-pr-<n>`), what the user should test on the device (point at the new `device-tests.md`
section), and what you could not verify. Say explicitly that UI, gestures, sound and anything
hardware-dependent are untested until the user tries them. Never merge the PR, tag a release
or push the wiki without being asked.
