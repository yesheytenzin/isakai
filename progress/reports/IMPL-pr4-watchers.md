# PR4: TextWatcher parity on every edit path (TextView/EditText/BaseInputConnection)

Goal in my own words: make text change notifications fire on every edit path
the platform promises, so app save logic never misses a typed or pasted body.
Smallest change: notify watchers from all mutation paths with correct spans.

Branch: `pr4-watchers` (from main). Status: fixed, verified, gates green.

## Why

The save-trigger research
(`progress/research-notes/isakai open defects fix plan/notes-save-trigger.md`)
shows this repo fires the watcher triple only from `TextView.setText`, always
with full-replace spans, and nothing at the `Editable` level. Direct
`getText()` mutations bypass watchers entirely and leave `TextView.text`
stale. Any span-sensitive consumer mis-fires, and any consumer trusting a
watcher-fed model over a direct-buffer edit goes stale. The pager gate from
`progress/reports/WORKER-notesave.md` routes taps to the right fragment; this
change is the watcher parity underneath it, inside the allowed three files
only (`framework/android/widget/TextView.java`,
`framework/android/widget/EditText.java`,
`framework/android/view/inputmethod/BaseInputConnection.java`).

## What changed

- `TextView.setText` now diffs old vs new with a common prefix/suffix scan
  and fires one triple with the changed region instead of a full replace.
  A no-change call syncs without firing. All dispatch funnels through the new
  package-private `publishTextChange(old, new, start, before, count)`.
- `EditText.getText` now returns a forwarding `WatchfulEditable` over the
  live buffer. Every `insert`/`delete`/`replace`/`append`/`clear` on it syncs
  `TextView.text` and fires one triple with measured spans (range from the
  call, count from the length delta so `InputFilter`s stay exact), and
  re-clamps the cursor. Span-only ops and filters delegate silently.
  Internal paths (`replaceSelection` for keys/paste/ENTER, `setText`,
  key-listener `notifyEditableChanged`) still mutate the raw buffer and go
  through the diff funnel, so each stays exactly one triple.
- `BaseInputConnection.replace` (IME `commitText`/`setComposingText`) does one
  `editable.replace(a, b, value)` instead of delete-plus-insert, so an IME
  commit is one replace triple with `(a, b-a, value.length())` rather than
  two. `deleteSurroundingText` keeps one triple per removal, which matches
  the two disjoint deletions it performs.

Deliberately unchanged: `SpannableStringBuilder` gets no dispatch (outside
the file boundary); `afterTextChanged` still hands the live `Editable` to
`EditText` watchers and a copy to plain `TextView` watchers; `setText` with
identical content no longer fires (the contract fires "when the text is
changed", and the skip also collapses the wrapper-plus-refresh double
notification into zero).

## Verification evidence (DB plus screenshots only, never the log tail)

Pinned `sway-flow.py notes` series, one fresh `--work` tree per run, same-UID
development mode, headless 1000x1000 fixture. Raw tables:
`evidence/2026-10-28/pr4-watchers/series-before.tsv` and
`series-after.tsv`; per-run screenshots, app logs and flow logs under
`evidence/2026-10-28/pr4-watchers/{before,after}{1..6}/`.

- BEFORE (clean main tree, pager gate already landed): 5/6 full PASS, each
  DB `(1,'General note',''), (2,'N2','Isakai 日本語 – noteX')`. Run 6 FAILs
  with DB `(2,'N2','')`, but its `simple-03-unicode` screenshot shows the
  empty editor ("Insert text" hint, paste never landed), so the gate
  correctly skipped: an input-delivery flake, not a save miss. Save-gate
  misses with text present in the editor: 0/6.
- AFTER (watcher fix): 5/6 full PASS with identical marker rows. Run 4 FAILs
  with DB `(2,'N2','')` and the same empty-editor screenshot: the same
  input-delivery flake, correctly skipped. Save-gate misses: 0/6.
- Miss counts: before 1 flow-level FAIL (0 gate-level) vs after 1 flow-level
  FAIL (0 gate-level). No regression, parity held on typed and pasted bodies.
- Calculator regression on the fixed build: PASS
  (`evidence/2026-10-28/pr4-watchers/regcalc/`), history row `2+2 = 4`,
  settings opened, decimal preference persisted, clean close.

Gates: `zig build` exit 0 before and after; `git diff --check` clean;
`python3 tools/progress-lint.py` 0 violations. No probes in tree
(`grep -rn ISAKAI_ framework/` empty of diagnostics); no shared progress
files touched.

## Code review (self-review)

- Scope is exactly the three allowed files (+109/-7). No other file needed,
  so no handoff.
- One funnel (`publishTextChange`) owns every fire; spans are measured, not
  threaded as flags. No new allocations beyond short strings the funnel
  already built per keystroke (`refresh()` always did `editable.toString()`).
- No in-tree `TextWatcher` consumers exist, and no in-tree caller mutates via
  `getText()`, so the blast radius is app-visible behavior only, now closer
  to the platform contract.
- Honest counts above: both flakes classified from the editor screenshot,
  not hidden. The flake family (paste lost between click, CTRL+A, wl-copy,
  CTRL+V) is harness input delivery, untouched by this diff.
- No material review findings remain. Comment-sicko pass: 3 KILLs on the new
  comments, all accepted and deleted (diff +109/-7 to +98/-7).

## Remaining recommendation for the app layer

The framework half is done, but the app layer should still flush explicitly:
read `getText()` and issue the UPDATE synchronously before `dialog.show()`
on the picker-open path (and keep the pause/page-change save as backstop).
Dialog-open fires no lifecycle or page callback on any platform, so a
watcher-fed model alone is never sufficient there. That flush lives in app
code outside this boundary and is recorded here, not implemented here.

## Cleanup

Fixture stopped, `/tmp/pr4-*` scripts/logs/snapshots and work dirs removed,
stale locks removed. Evidence retained only under
`evidence/2026-10-28/pr4-watchers/`.
