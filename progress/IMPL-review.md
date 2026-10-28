# Independent review: PR1–PR4 on main HEAD (a531f55)

Lane: independent, read-only. No source modified; this report is the only file.
Base for all diffs: `22d5ad8`. Method: full source diff read for each PR head
(`55c656c` PR2, `d0b4957` PR3, `d68ccb6` PR1, `7c103bf` PR4) plus targeted reads
of the surrounding code each diff touches (dlerror fallthrough, Resources.getDrawable,
EditText internal edit paths, BaseInputConnection.getEditable). No new harnesses,
probes, or flow runs were created; evidence cited below is the PRs' own recorded
artifacts, cross-checked against the code. Checks per PR: gate semantics hold,
no double behavior (double-fire / double-delivery), no swallowed errors hiding failures.

Records read: `progress/reports/IMPL-pr1-sealed.md`, `IMPL-pr2-loader.md`,
`IMPL-pr3-iconnav.md`, `IMPL-pr4-watchers.md`.

## PR1 — translator sealed metadata (`src/translate.zig`, +424/-1) — SHIP

- Gate semantics: `RuntimeVisibleAnnotations` is emitted only from runtime-visibility
  (1) DEX class annotations; BUILD/system visibilities stay out (`readRuntimeClassAnns`
  skips `vis != 1`, rejects `vis > 2` as `BadDescriptor`). Malformed input stays a
  `TranslateError`; only genuinely unrepresentable values (field/method refs, null)
  drop one annotation via `SkipAnnotation` with a debug print. That is the only
  non-failing lossy path, and the alternative (fail the whole class, or emit a
  malformed annotation that throws `IncompleteAnnotationException` at first read)
  is strictly worse. Not a hidden failure: the print names the event, and the
  PR's own census shows 0 failures with counts matching the DEX census.
- No double behavior: pool registration happens in `buildRuntimeAnns` before
  `emitPool`; the body flushes once after methods with attribute count 1/0.
  The previous code always wrote 0 class attributes, so nothing is clobbered.
- No swallowed errors: `depth > 32` and count caps (`1<<16`, `1<<20`, 65535)
  return errors; `errdefer` frees owned IR on partial-array failure paths.
- Spot-checked decoding: enum-via-`field_idx`, `ACC_ANNOTATION` 0x2000 mapping,
  sign-extension in `annInt`/`annLong`, and width guards are all consistent with
  the claimed empirical findings. The `bv=[1,0,3]` completion default is
  version-pinned (kotlinc 2.4.21) and therefore could misdescribe a future
  compiler's omitted default — but an omitted `bv` is unreadable without it
  (read throws), so completing is strictly better than the status quo. Note only.
- Evidence ties out: `javap` shows the annotation present, jshell goes
  `byLowerCase=null → cube` and `getAll 0 → 40`, calculator flow PASS on a
  retranslated tree. The `isSealed()`/native-sealed limitation is honestly recorded.

## PR2 — loader contracts (`src/native/bionic_shim.zig`, +94/-11) — SHIP

- Gate semantics hold, and this is the strongest of the four diffs on error rules:
  every `android_dlopen_ext` check runs before any host attempt, so a request the
  host would satisfy (e.g. `USE_NAMESPACE`) still fails loudly per contract.
  `NULL` extinfo keeps plain-dlopen behavior; dlopen-flag mask `0x1107` matches
  bionic's six bits; retired ART bits stay rejected.
- dlerror follows the thread rules end to end (verified against the current file,
  not just the diff): `err_buf` is `threadlocal` 512B; `setError` drains the host
  error first so the shim message is current; success paths never clear; failure
  paths write (with a loud generic fallback when bionic supplies no text, never a
  silent null); `dlerror()` returns-then-clears with host fallthrough. Host-failure
  paths `clearError()` the stale shim buffer so the fresh host message surfaces.
  The 11-case matrix showing message-then-NULL is consistent with this code.
- No double behavior: one `setError` per failure path; early `return null` after
  each guest-error write (the diff even fixes two paths that previously fell
  through after writing the error).
- No swallowed errors: the only silent-forward cases are `USE_LIBRARY_FD` /
  `FORCE_LOAD` falling through by filename, which is documented in a comment.
  Residual risk (flag-only FD load resolving an unrelated same-name host library)
  has no pinned caller — the app-lib extinfo grep stays open per the record —
  so this is a noted limitation, not a block.
- `AndroidDlextinfo` extern layout matches NDK field order; only `flags` is
  dereferenced, reserved values without flag bits are ignored as on platform.

## PR3 — icon plus navigation (`ImageView.java`, `Activity.java`) — SHIP

- Icon: storing both drawables directly to the field instead of the virtual
  `setImageResource`/`setImageDrawable` matches the diagnosed hazard (AppCompat
  override dereferences not-yet-created delegates; the old surrounding catch
  swallowed the NPE into the blue-blob symptom). `srcCompat` applied last wins,
  which is the AppCompat precedence. `Resources.getDrawable` was confirmed to
  throw `missing(id)` (`NotFoundException`) on unknown ids, so the "unknown ids
  throw" claim holds and the loud-failure path is real.
- Navigation: found path now finishes intermediaries AND delivers exactly one
  `onNewIntent` to the target — the old code never delivered. No in-tree callers
  of `navigateUpTo`/`onNavigateUp`/`getParentActivityIntent` exist, so blast
  radius is translated-app behavior only. `onNavigateUp` reuses the existing
  `startActivities` loop instead of adding a `TaskStackBuilder` type: smallest
  change, no dead scaffolding.
- Notes (not blockers):
  - The not-found branch (finish-to-root, return false, no launch) is unexercised
    — no pinned flow reaches it and no driving app surface exists. Whether
    finish-to-root vs launch-the-parent is the more faithful platform behavior
    there is genuinely ambiguous in this custom-navigator tree, and the old code
    (no-op `return true`) was worse (caller told success, nothing happened).
    Needs a driving app before it can be proven; do not "fix" it blind.
  - `android:src` keeps its pre-existing empty `catch (RuntimeException)`, so a
    bad `android:src` still renders silently empty while a bad `srcCompat` throws.
    Pre-existing inconsistency, out of this PR's scope; left as-is deliberately.
- Evidence ties out: FAB plus visible in `simple-04-note-picker.png`, notes and
  calculator flows PASS, found-path Up drive returns to MainActivity alive.

## PR4 — watcher parity (`TextView.java`, `EditText.java`, `BaseInputConnection.java`) — SHIP

- Single-triple-per-mutation was verified path by path against the current files,
  not taken on trust:
  - `setText`: raw `replaceAll` (no fire — raw buffer has no dispatch) + one
    `super.setText` diff-funnel triple. Single.
  - `replaceSelection` (keys, paste, ENTER, cut, DEL): raw `delete`+`insert` on
    the raw field + `refresh()` → one diff-funnel triple. The wrapper is bypassed
    because these paths touch the `editable` field, and any already-synced state
    diffs to empty (no second fire). Single.
  - Key-listener path passes the raw field then `notifyEditableChanged()` →
    one funnel triple. Single.
  - Direct `getText()` mutation: the `WatchfulEditable` wrapper publishes exactly
    one measured triple. Single.
  - IME `commitText`/`setComposingText`: the `delete`+`insert` → single `replace`
    change is REQUIRED, not cosmetic — through the new wrapper the old form would
    have fired two triples; now it fires one `(a, b-a, value.length())`. The diff
    fixes a double-fire its own PR would otherwise introduce. `deleteSurroundingText`
    keeps one triple per disjoint removal, matching the two deletions it performs.
  - `BaseInputConnection.getEditable` returns the wrapper for EditText targets, so
    IME edits actually notify now (previously zero triples) — the bug being fixed.
- Gate semantics: `publishTextChange` skips only the true no-change case
  (`before == 0 && count == 0`, sync + invalidate, no fire), which also collapses
  the old wrapper-plus-refresh double notification. Span-only ops and filters
  delegate silently, correctly. `diffSpans` guards prefix/suffix overlap.
- No swallowed errors: no catch blocks added; no failure path silenced. The
  before/after series is honest (5/6 PASS both sides, the one FAIL classified from
  the editor screenshot as input-delivery flake on both sides, 0 gate-level save
  misses). Calculator regression PASSes.
- Residual notes: `afterTextChanged` hands the live wrapper to EditText watchers,
  so a watcher that mutates inside `afterTextChanged` can re-enter — but that
  re-entrancy predates this PR (the raw buffer was equally live). No in-tree
  `TextWatcher` consumers or `getText()` mutators exist. The app-layer explicit
  flush recommendation is correctly recorded as out-of-boundary, not implemented.

## Verdicts

- PR1 sealed metadata: **ship**.
- PR2 loader contracts: **ship**.
- PR3 icon plus navigation: **ship** (not-found Up branch unproven but unreachable
  in pinned flows; needs a driving app, not a blind fix).
- PR4 watcher parity: **ship**.

No blockers. No shown problem in any PR rises above note-level: each diff stays
inside its file boundary, each failure mode is louder than before, and each
double-behavior hazard was either absent or fixed within the diff itself.
