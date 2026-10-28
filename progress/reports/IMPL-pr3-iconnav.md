# PR3: FAB plus icon plus single-intent Up navigation

Goal in my own words: make the FAB show its plus icon and make Up
navigation follow the single-intent rule, so the settings screen and
task stack behave like the platform. Smallest change delivered: read
srcCompat, deliver the up intent, keep one density snapshot.

Branch: `pr3-iconnav` from `main`. Files touched (boundary):
`framework/android/widget/ImageView.java`,
`framework/android/app/Activity.java`. Nothing else.

## Why

The notes folder picker showed a solid blue rounded blob where the
FAB belongs, and `Activity.navigateUpTo` launched instead of
delivering on the not-found path while never delivering on the found
path. Both are contract completions from
`reports/Remaining N6 N18 fix plan.md` (icon plus nav plus density
sections) and
`research_notes/Remaining N6 N18 fix plan/appcompat-icon-nav.md`.

## What changed

ImageView constructor: after `android:src`, resolve `app:srcCompat`
by tag-literal name through the raw binary type (`@ref` loads
directly, `?attr/` resolves through the theme like TintTypedArray,
unknown ids throw `NotFoundException` out of `getDrawable`), applied
last so it wins. Both drawables are stored to the field directly
instead of calling the virtual `setImageResource`/`setImageDrawable`.

Activity: `navigateUpTo` takes exactly one up intent (found path
finishes intermediaries plus `onNewIntent` delivery, not-found path
finishes to root and returns false), `getParentActivityIntent`
resolves `parentActivityName` from the manifest via the package
manager, plus `shouldUpRecreateTask` (default false) and
`onNavigateUp` which stacks the manifest parent chain oldest-first
through the existing `startActivities` loop for cross-task Up.

Density: no code change. The `setMetrics` plus `applyMetrics` shape
already feeds one compositor snapshot to both drawable selection and
layout; the calculator repaint below proves no shift.

## Root-cause trail (read before reviewing the diff)

The first hypothesis (srcCompat never read) was necessary but not
sufficient, and each step below was observed, not guessed:

1. The FAB (`MyFloatingActionButton`) uses `android:src`, not
   `app:srcCompat`. A temporary probe showed its drawable
   `0x7f080141` load throwing `NullPointerException` inside the
   translated `AppCompatImageButton.setImageResource` override: the
   base constructor's virtual call ran before the AppCompat delegates
   existed, and the surrounding catch swallowed it, leaving the blue
   container with no plus. AOSP assigns the drawable to the field in
   the constructor; the fix does the same, for both `android:src`
   and `srcCompat`.
2. The pinned `srcCompat`layouts (`bt.xml`, `iZ.xml`) carry `?attr/`
   values whose recorded attr id is 0 in obfuscated builds (resource
   map too small), so styled lookup by id cannot match the tag; the
   index-based raw type/data read handles both the native binary
   parser and the decoded-text fallback. A live probe confirmed no
   inflated ImageView in these flows carries a literal `srcCompat`
   name through any other path, so the kept `srcCompat` tier only
   fires where the attribute is really present.
3. All probes were removed after use; `grep probe` on both files is
   empty and the final tree holds no temporary code.

## Verification evidence (real artifact, isolated sway fixture)

Setup: `.cache/isakai/{compositor,apks}` copied from the pinned
main-repo cache; `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pr3`,
`XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pr3`,
`ISAKAI_STAGE_DIR=/tmp/isakai-1000-pr3-stage`; headless sway fixture,
stopped after the runs.

- `evidence/2026-10-28/pr3-iconnav/notes/simple-04-note-picker.png`:
  picker rows render cleanly and the FAB shows its intended
  container blue with a visible white plus. Before the direct-store
  fix the same shot showed a solid blue blob with no glyph.
- `evidence/2026-10-28/pr3-iconnav/notes/`: full `sway-flow.py notes`
  PASS on the PR3 work tree (save, picker, back-to-editor, 31s
  liveness, exit 0).
- `evidence/2026-10-28/pr3-iconnav/calculator/`: full
  `sway-flow.py calculator` PASS on a fresh install
  (2+2 commit, settings preference flip, comma-decimal commit,
  reopen persistence, 31s liveness, exit 0).
  `simple-02-result.png` vs `simple-05-decimal-result.png`: white
  keypad, dark glyphs, toolbar icons identical, no color shift
  across the settings repaint.
- `evidence/2026-10-28/pr3-iconnav/upnav/`: manual Up drive
  (`/tmp/pr3-up.py`, since deleted): settings opened, toolbar Up
  tapped, `up-03-back-main.png` shows the MainActivity keypad and
  the app stayed alive. Single-intent found path (destination in
  history, intermediaries finished, intent delivered).

Gates: `zig build` exit 0 before and after; `git diff --check`
clean; `python3 tools/progress-lint.py` 0 violations.

## Code review (self-review)

Scope: diff touches only the two boundary files; no new class (no
`TaskStackBuilder.java`: the recreate branch reuses the existing
single-intent `startActivities` loop). No callers of the changed
constructors needed updates; `getParentActivityIntent` null-cases
(no manifest parent, unknown component) return null as on the
platform. `onNewIntent` delivery defaults to `setIntent`, matching
the base contract. No dead code, no new imports, comments state
only the non-obvious why (virtual-dispatch hazard, application
order). Remaining limits: the Up not-found branch (finish-to-root,
false) has no driving app surface in the pinned flows and is
unexercised; `onCreateNavigateUpTaskStack`/
`onPrepareNavigateUpTaskStack` hooks do not exist because there is
no `TaskStackBuilder` type in tree; tint-attribute ordering still
waits on an AndroidX source read per the research note. No material
findings left open.
