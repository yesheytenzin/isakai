---
id: "C6"
status: "done"
phase: "COMPATIBILITY"
priority: "P1"
size: "L"
updated: "2026-10-27"
owner: "unassigned"
next: "none — N3 privileged gate passed 2026-10-27 with retained evidence; default-isolation follow-ups belong to C7; reopen on regression"
---

# C6: Close measured translator and framework failures

## Dependencies

- [C1](C1-target-apps.md)
- [C2](C2-apk-diagnostics.md)

## Code and documentation

- [src/dex.zig](../src/dex.zig)
- [src/translate.zig](../src/translate.zig)
- [framework](../framework)

## Delivery review details

First repair/re-run OpenCalc's current-expression oracle and investigate the visible Syntax regression on fresh state. Trace Calculator coroutine service discovery and dispatcher; preserve needed APK Java resources in `src/install.zig`/`src/launch.zig`; adapt main scheduling in Handler/Looper/Choreographer only where demonstrated.

**Additional acceptance / stop gate:** Fresh OpenCalc calculates and copies its current result; same pinned Calculator APK shows actual settings, toggles a preference, returns via back, restarts with value retained and stays responsive >30 seconds. Stop broader Compose claims if only this screen passes.

**Original planning estimate:** 3–10 days investigation; 2–6 weeks if deeper Android Compose owner semantics emerge.

Guard null xkb map/state; correct locked-layout group and replay modifiers in `src/bridge/xkb.zig`, `window.zig`.

**Additional acceptance / stop gate:** No crash before keymap, replacement/reordered events; Shift/Caps/nonzero group behavior; visible input/resize/close unaffected. A wrapper-only pass is provisional.


## Acceptance

Each fixed failure is proven by the affected real visible flow. Unresolved failures stay explicit rather than becoming silent default returns.

## Blockers

2026-10-26 source and UI audit: shared-contract repairs and removal of APK-private
lifecycle/layout workarounds are owned by [C8](C8-shared-contracts.md). Fresh Calculator baseline
settings screenshots are blank; N5 records fresh Notes and intermittent OpenCalc
failures. The dated completion claims below describe their original runs only.
Do not attribute the RecyclerView failure solely to AndroidX bytecode without a
reference-runtime comparison or proof of the responsible platform contract.

### B-rollback-context-hierarchy

The signed Calculator 5.11.3 rollback generation computes and persists
`12+34=46`, but opening retained history fails verification because an
Activity subclass is not assignable to ContextWrapper in the current framework.
The correct ContextThemeWrapper hierarchy was tried and reverted after it
activated AppCompat attachment requiring missing configuration-context APIs.
The repair must include configuration/resource behavior and preserve translated
attachment overrides. It must not rewrite the app's bytecode to hide the type
error. The [C7 resumed evidence](C7-acceptance-gate.md#2026-10-16--resumed-gate-notes-failure-reproduced)
records commands, screenshots, failed runs, partial adapter support and the
reverted experiment. No full rollback-usability pass is claimed.

Update 2026-10-23: the context-hierarchy root cause is fixed, proven on the
real old Calculator 5.11.3, with no regression to the three pinned apps. The
repair is three framework changes plus one boundary fix the hierarchy exposed:

- `android.app.Activity` now extends `android.view.ContextThemeWrapper`
  (which extends `ContextWrapper` extends `Context`), so an Activity IS a
  `ContextWrapper` and `getBaseContext()` resolves — the exact call the
  history row holder makes. `attach(Application)` now invokes the app's
  virtual `attachBaseContext` (the base is the Application), so AppCompat's
  override runs as on real Android; `getPackageName`/`getTheme` are kept
  stable across the new base so behaviour is unchanged for the other apps.
- `android.content.Context.createConfigurationContext(Configuration)` is
  added (it was the missing method the reverted experiment crashed on). The
  runtime raises no configuration changes and ships one resource set, so it
  returns a `ContextWrapper` delegating back to this context.
- `Resources.Theme.rebase()` is added (a no-op; AppCompat calls it from the
  night-mode attach path and the overlay already holds every applied style).
- `IsakaiPackageManager.getServiceInfo` returns an empty (non-null) `metaData`
  Bundle under `GET_META_DATA`. Wiring `attachBaseContext` made AppCompat's
  `AppLocalesMetadataHolderService` probe run; its cached opt-in `Boolean`
  was only set when `metaData != null` (or on `NameNotFoundException`), so a
  null `metaData` NPE'd on unbox. This matches real Android (metaData is
  populated only under `GET_META_DATA`; no `<meta-data>` means an empty Bundle).

Proven (see the [2026-10-23 verification](#2026-10-23--context-hierarchy-fix-and-old-calculator-history)):
old Calculator 5.11.3 starts past `attachBaseContext` (Splash→Main→Settings,
bridge connected, no `NoSuchMethodError`/NPE), commits a fresh history row via
the keypad, and the history dialog opens over it and binds 3 real rows
(`2+2=4`, `2+2=4`, `5+1=6`) with the process alive and zero
`getBaseContext`/`ClassCast`/exception lines. The named root cause — Activity
not assignable to ContextWrapper — is resolved.

Remaining, recorded explicitly (deferred, distinct from the context hierarchy):
the history dialog's row list does not visibly render. Diagnosed 2026-10-23 by
dumping the live dialog view tree (temporary `DialogOverlayGroup` probe, since
removed): the whole `AlertDialogLayout` measures to 71x25 because the title and
message blocks are `GONE` and the history list
(`com.simplemobiletools.commons.views.MyRecyclerView`) measures to 71x0 with
zero child views across eight re-layout passes.

Data delivery was observed in that older run: the Room query
`SELECT * FROM history ORDER BY timestamp DESC LIMIT ?` returned real rows
(logged `rows=7`), and the shown adapter (`i2.c`) reported **9 items**.
Its measured list ultimately had zero children and zero height despite
`AT_MOST 576x695` input. A forced dialog height did not repair it.

**Diagnosis corrected 2026-10-27:** those final snapshots did not show what
happened during fill. They did not justify the earlier claims that AndroidX
never created children, that every framework cause was ruled out, that the
app's RecyclerView bytecode needed patching, or that Notes shared this cause.
Those claims are withdrawn. The
[current pinned-Calculator investigation](#2026-10-27-history-render-investigation-resumed)
proves transient zero-height RelativeLayout rows and identifies the owning
framework measurement defect. That newer run uses Calculator 5.12.0; it does
not establish after-change rollback usability for 5.11.3.

### B-xkb-race

Null-state/map guards, retained modifier replay and locked-layout group mapping are implemented; direct real-XKB probes and the fresh OpenCalc window flow pass. Reordered/delayed keymap and modifier lifecycle stress remains unverified before closing the original race.

Update 2026-10-23: no xkb code changed this session. The three pinned flows and the old-Calculator drive all type through the virtual keyboard without reproducing the race, which extends the passing-path evidence but is not the adversarial reorder/delay stress. That stress needs an instrumented keymap-churn harness; building a test harness is outside the verification-artifact policy, so this stays an explicit verification gap rather than a claimed pass.

Artifacts:

- [.cache/isakai/evidence/xkb-segv-574913.log](../.cache/isakai/evidence/xkb-segv-574913.log)
- [.cache/isakai/evidence/hs_err_pid577110.log](../.cache/isakai/evidence/hs_err_pid577110.log)
- [.cache/isakai/evidence/audit-20261014/xkb-null-state.json](../.cache/isakai/evidence/audit-20261014/xkb-null-state.json)

### B-opencalc-retained-state

OpenCalc passes with fresh data but retained opencalc10 data shows Syntax and copies the expression. Preserve failing data/settings and investigate; prevent stale history from satisfying the result helper.

Update 2026-10-15: resolved. The hardened fresh-result gate (history baseline count, prefs-mtime advance, log-suffix scoping, clipboard sentinel) fixes the stale-satisfaction hole, and retained `opencalc10` plus fresh `c7svc-ocalc` both pass the full flow with a newly committed `12+34 -> 46` row (the Oct-14 retained `Syntax` did not reproduce; app-data unknown fenced by assertions). Reconfirmed 2026-10-19 on the instrumentation-stripped build: retained run baseline 12 rows, fresh run baseline 0 rows, both copied `46` and exited 0.

Artifacts:

- [.cache/isakai/evidence/audit-20261014/opencalc-02-result.png](../.cache/isakai/evidence/audit-20261014/opencalc-02-result.png)
- [.cache/isakai/evidence/c7final-ocalc10-20261019/](../.cache/isakai/evidence/c7final-ocalc10-20261019/)
- [.cache/isakai/evidence/c7final-ocalc-fresh-20261019/](../.cache/isakai/evidence/c7final-ocalc-fresh-20261019/)

Related tasks: [C6](C6-translator-framework-failures.md), [C7](C7-acceptance-gate.md), [N1](N1-evidence-reproducibility.md).

### B-calculator-compose

Calculator opens SettingsActivity but Compose children never appear; trace recomposer startup, coroutine/main dispatch and staged service resources without asserting an unproved root cause. Update 2026-10-15: `META-INF/services/*` (Calculator ships obfuscated `u9.s`, `z9.k`) is now staged at install and mirrored onto the JVM classpath at every full translation, so stock ServiceLoader can resolve the dispatcher factories; translated `kotlinx/coroutines` classes were verified present. Rerun settings flow still shows a blank surface and jstack still shows zero worker threads, so factory visibility alone does not start the recomposer.

Update 2026-10-15 (lifecycle events drive composition): framework activities now drive the translated view-tree registry through `performCreate/Start/Resume/Pause/Stop/Destroy` plus catch-up on late owner installation (AOSP `ReportFragment` parity). Discovery is by shape (single enum parameter carrying the six `ON_*` names), never by obfuscated method name; failures stay silent and undriven, never crash. Owner installation runs on the owning activity instance (the window-loop install previously retained on the wrong instance and starved every registry). The saved-state owner is now stable per owner (was a fresh instance per call). Measured effect: the settings composition STARTS (lifecycle observer fires, initial composition runs on the main thread) and then fails deterministically inside a `LaunchedEffect` remember: `IllegalStateException: Error while pushing x. Not all arguments were provided. Missing 0 int arguments () and 1 object arguments (value)` at `j0.p.e0` via `a8.d.f` during settings initial composition. Services, dispatcher reachability and lifecycle are now behind; the open gap is the composer remember-protocol/`(value)` argument shape in translated composable code. Owner class names (`androidx.lifecycle.v`, `l4.g`, `j0.g2`, `a9.i`, `a9.j`, `l4.e`) remain Calculator-obfuscation-specific: other apps need their own mapping.

Update 2026-10-16: resolved through the lifecycle/composition work recorded in C7. Settings renders (screenshot `simple-03-settings` shows the composed rows) and its rows answer real taps; the earlier `remember`-protocol crash no longer reproduces. Remaining measured defect, not this blocker: drawn setting rows sit ~60px above their interactive hit rows (see the deferred defect below); the Calculator flow uses measured interactive coordinates.

Artifacts:

- [.cache/isakai/evidence/simple-04-settings.png](../.cache/isakai/evidence/simple-04-settings.png)
- [.cache/isakai/evidence/c7svc-calc/](../.cache/isakai/evidence/c7svc-calc/)
- [.cache/isakai/evidence/c7compose-crash/](../.cache/isakai/evidence/c7compose-crash/) (lifecycle-driven composition + exact stack)
- [.cache/isakai/evidence/c7final-calc-20261019/](../.cache/isakai/evidence/c7final-calc-20261019/)

Deferred defect (2026-10-16, unfixed): Compose draw passes and pointer/layout space disagree by about 60px in the Calculator settings screen — rows that visibly sit at screen y≈281/331/402 respond to taps only near y≈340/380/440 at x=320. Budget decision: drive the flow with measured interactive coordinates and leave the offset repair open rather than change the harness window contract.

Related tasks: [C6](C6-translator-framework-failures.md), [C7](C7-acceptance-gate.md).

### B-notes-dialog-window

`+` (menu item `new_note`) opens `NewNoteDialog`, a MaterialAlertDialog that requires visible interaction (title + type + OK). Update 2026-10-15: `alertDialogStyle` now resolves (`defStyleAttr present=true`, 48 occurrences in the rerun log) after the Dialog-theme fix below, and the dialog shows headless without crashing. Dialogs have no compositor surface (`HeadlessWindow`), so the editor fragment behind the dialog remains unreachable from the visible surface. Required: dialog content as a real tappable window (second surface, input routing, dismissal); multi-day framework work, not started.

Update 2026-10-16: resolved by discarding the second-surface requirement. Decisions in C7 render the dialog through the owning activity's existing window into the host root (`DialogOverlayGroup`, `setContentViewFromWindow`), so the dialog is part of the app's one compositor surface and the window input chain reaches it. The real Notes flow taps the dialog's title field, types through the virtual keyboard, confirms OK and opens the editor fragment; the note picker dialog returns via the app-supported Escape as well.

Artifacts:

- [.cache/isakai/evidence/c7final-notes/com.simplemobiletools.notes.pro.sway-notes.log](../.cache/isakai/evidence/c7final-notes/com.simplemobiletools.notes.pro.sway-notes.log)
- [.cache/isakai/evidence/c7final-notes/simple-01-initial.png](../.cache/isakai/evidence/c7final-notes/simple-01-initial.png)
- [.cache/isakai/evidence/c7final-notes-20261019/](../.cache/isakai/evidence/c7final-notes-20261019/) (dialog, picker, restart and final-edit shots on the stripped build)

Related tasks: [C6](C6-translator-framework-failures.md), [C7](C7-acceptance-gate.md).


## Verification evidence

### 2026-10-27 history-render investigation resumed

The earlier claim that every framework cause was ruled out is withdrawn.
The same-UID Calculator run still opens a blank history dialog with a
three-item adapter. Its first RecyclerView measure receives `AT_MOST 576`
by `AT_MOST 695` but returns `0x0`. A final zero child count did not prove
that fill never created children; the debugger below corrects that earlier
inference. The later `87x0` measurement follows the collapsed dialog, so
forcing the dialog height would treat the consequence rather than identify
the first failure.

The retained trace is [history-before.log](../evidence/C6/2026-10-27/history-before.log).
It comes from launching `zig-out/bin/isakai --work
.cache/isakai/recheck/c6-history launch com.simplemobiletools.calculator`
with `ISAKAI_NO_PRIVILEGE=1`, `ISAKAI_FRAMEWORK=$PWD/zig-out/framework`,
`ISAKAI_WINDOW=1`, and the isolated Sway fixture. The existing
`SwayCompositor` driver clicked the history item at window-relative
`450,16`. This retained work tree is diagnostic evidence, not a fresh
translation or privileged acceptance run.

Temporary class-name and reflection probes in `View`, `ViewGroup`, and
`DialogOverlayGroup` were removed. `zig build && git diff --check` returned
zero after their removal. The clean baseline still shows only OK and Clear,
with no history rows visible in
[simple-history-before-clean.png](../evidence/C6/2026-10-27/simple-history-before-clean.png).
The driver sent Escape and closed the app with exit zero; that exit alone
does not establish dialog dismissal. Its [clean log](../evidence/C6/2026-10-27/history-before-clean.log)
contains no temporary measure probes. No APK-private layout workaround is
part of the implementation.

Debugger inspection rejects the late-adapter hypothesis. The adapter is
already present at the first measure. At layout-step entry the manager has
`AT_MOST 576x695` and state item count 3, as recorded in
[layout-step-debugger.log](../evidence/C6/2026-10-27/layout-step-debugger.log).
The first fill creates three children. The
[row debugger capture](../evidence/C6/2026-10-27/row-debugger.log) identifies
a framework `RelativeLayout` row, with `MATCH_PARENT` width,
`WRAP_CONTENT` height, two children, and measured size `32x0`.

`RelativeLayout.onMeasure` treats the zero size carried by an `UNSPECIFIED`
scroll-axis spec as a zero limit. It measures row text under `AT_MOST 0`
and clamps the parent's desired height to zero. This is an owning-framework
measurement defect, not evidence that AndroidX must be patched. The repair
uses `getChildMeasureSpec` on unbounded axes, preserves intrinsic child
extents during aggregation, and uses `resolveSize` for the parent. Bounded
measurement is preserved. No APK-specific minimum height was installed.

`zig build` returned 0; its [build log](../evidence/C6/2026-10-27/build.log)
retains the exit status. A fresh same-UID install and translation used:

```sh
ISAKAI_NO_PRIVILEGE=1 zig-out/bin/isakai --work .cache/isakai/recheck/c6-history-fresh install .cache/isakai/apks/calculator-fdroid-release.apk
```

The existing `AppRun`/`SwayCompositor` API started this work tree with a
600-second startup timeout. The direct drive used existing calculator
`KEY_CLEAR`, digit, plus and equals points to enter `2+2=`, `5+1=`, and
`1+2=`; clicked `click_window(450,16)` to open history; captured it; sent
`key("Escape")`; captured again; closed through `AppRun.close()`; and
restarted the same app/data before reopening history. No helper file,
new test case, or runtime probe was added.

- [Fresh history](../evidence/C6/2026-10-27/simple-history-after-fresh.png)
  shows five visible formulas, including `2+2`, `5+1`, and `1+2`. The app also
  stored intermediate `5+0` and `1+0` rows; the
  [drive output](../evidence/C6/2026-10-27/history-after-drive.log) records
  the exact five formula/result tuples rather than claiming only three.
- [Reopened history](../evidence/C6/2026-10-27/simple-history-after-reopened.png)
  shows the same formulas after process restart. Both compositor closes
  returned 0. [App log](../evidence/C6/2026-10-27/history-after-fresh.log).
- The screenshot named
  [history-after-dismissed](../evidence/C6/2026-10-27/simple-history-after-dismissed.png)
  still shows the dialog: Escape dismissal is **not** proven. The name is
  a failed observation, not a pass. Result text is also not visible in
  these captures. Visible formulas prove removal of the zero-height
  collapse, not complete history rendering or keyboard-dismissal support.

Pinned regression verification uses the existing commands:

```sh
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/c6-history-fresh --evidence evidence/C6/2026-10-27/calculator --startup-timeout 600
python3 verify-isakai/sway-flow.py opencalc --work .cache/isakai/recheck/c6-relativelayout-opencalc --evidence evidence/C6/2026-10-27/opencalc --startup-timeout 600
python3 verify-isakai/sway-flow.py notes --work .cache/isakai/recheck/c6-relativelayout-notes --evidence evidence/C6/2026-10-27/notes --startup-timeout 600
```

- [Calculator output](../evidence/C6/2026-10-27/calculator/runtime.log):
  exit 0, fresh `2+2=4`, decimal preference flipped, `1,5+2,5=4`, persistence
  across restart, 31-second liveness and back exit 0. The settings screenshot
  remains blank; this is not a visible settings-rendering pass.
- [OpenCalc output](../evidence/C6/2026-10-27/opencalc/runtime.log): exit 0,
  fresh `12+34=46`, copied `46`, resize, 31-second liveness and clean close.
  Its [result screenshot](../evidence/C6/2026-10-27/opencalc/opencalc-02-result.png)
  visibly shows both the formula and result.
- [Notes output](../evidence/C6/2026-10-27/notes/runtime.log): exit 1,
  `the new note did not persist its exact Unicode contents`. The
  [editor screenshot](../evidence/C6/2026-10-27/notes/simple-03-unicode.png)
  visibly contains `Isakai 日本語 – note`, but that does not pass persistence.
  No all-three-app or regression-free claim is made.

The Notes baseline comparison temporarily restored only
`RelativeLayout.java` to HEAD, confirmed `git diff --exit-code --
framework/android/widget/RelativeLayout.java` was clean, and built that
pre-change implementation (exit 0,
[baseline build log](../evidence/C6/2026-10-27/notes-baseline-build.log)).
It used a separate fresh app install/data tree:

```sh
ISAKAI_NO_PRIVILEGE=1 zig-out/bin/isakai --work .cache/isakai/recheck/c6-notes-baseline install .cache/isakai/apks/notes-fdroid-release.apk
python3 verify-isakai/sway-flow.py notes --work .cache/isakai/recheck/c6-notes-baseline --evidence evidence/C6/2026-10-27/notes-baseline --startup-timeout 600
```

The [pre-change Notes output](../evidence/C6/2026-10-27/notes-baseline/runtime.log)
also returns 1 with the same exact-Unicode persistence failure. Thus this
failure reproduces without the measurement repair; neither run passes Notes,
and the comparison does not establish the persistence bug's cause. The
measurement repair was restored unchanged and rebuilt afterward.

Actual OK-button dismissal is proven separately: after opening history at
`450,16`, the driver inspected the dialog, clicked its visible OK at
`298,412`, and captured the return to the main calculator with no dialog or
scrim. The [before](../evidence/C6/2026-10-27/history-ok/simple-history-ok-before.png)
and [after](../evidence/C6/2026-10-27/history-ok/simple-history-ok-after.png)
screenshots show that transition; the
[action log](../evidence/C6/2026-10-27/history-ok/runtime.log) records close
exit 0 and input disposal. This is button dismissal, not keyboard dismissal.

After the Notes baseline comparison, the final repaired build returned 0
([final build log](../evidence/C6/2026-10-27/final-build.log)). A final live
Calculator drive shows the visible formula in
[history open](../evidence/C6/2026-10-27/simple-history-final-open.png) and
then no modal or scrim in
[after OK](../evidence/C6/2026-10-27/simple-history-final-ok.png), with
[driver exit and app close both 0](../evidence/C6/2026-10-27/history-final-drive.log).
This drive allowed 2 seconds after startup and 3 seconds after each click;
an earlier immediate drive did not capture the expected transitions, so
no instant-readiness or rendering-latency claim is made.

All these runs are same-UID; none establishes default confinement.
`sudo -n true` still fails with `sudo: a password is required`; the
authorized-operator gate remains open. Rechecked 2026-10-27: the polkit
action `id.isakai.allocate-uid` is installed but requires `auth_admin`, and
no headless auth agent exists, so `pkexec` cannot authorize
non-interactively either. The privileged run stays an operator action.

Inspection of the shipped row layout used the existing APK decoder, not a
new framework probe:

```sh
apktool d -s --no-assets -o .cache/isakai/inspect/c6-calculator .cache/isakai/apks/calculator-fdroid-release.apk
```

[Decoder output](../evidence/C6/2026-10-27/apk-layout-decode.log) and the
[decoded row XML](../evidence/C6/2026-10-27/history-row.xml) are retained.
The result TextView is `layout_below` the formula. RelativeLayout's existing
largest-individual-child aggregation does not include that combined sibling
extent; source review identified this as a remaining measurement limit.
Do not equate the now-visible formulas with a complete formula/result row.
This needs its own owning-contract repair and real visible verification,
not a forced dialog height. The decoder output is inspection data only;
no APK bytecode or resources were changed in the runtime install.

### 2026-10-27 (stacked extents) — RelativeLayout measures positioned siblings

The remaining clip was an owning-framework measurement defect: `onMeasure`
aggregated the largest individual child, so a `layout_below` result never
extended the parent. The repair measures every child once, estimates each
child position with the same anchor/parent rules `onLayout` uses (dependency
order, parent fallbacks, no APK-specific minimum), and sizes the parent from
the positioned right/bottom extents. Bounded axes keep their prior clamps;
`resolveSize` and the zero-size fallback are unchanged. No new helpers,
allocations, passes, or app-specific naming were added.

`zig build` exits 0, `git diff --check` is clean, and `progress-lint`
reports zero violations. Verification drove the affected real surface:

- Calculator history shows formulas and results:
  [open](../evidence/C6/2026-10-27/stacked-history/simple-history-stacked-open.png)
  renders `2+2`/`4` and `1,5+2,5`/`4`; OK at `(302,436)` dismisses to
  [main](../evidence/C6/2026-10-27/stacked-history2/simple-history-ok.png)
  with app close exit 0. An earlier probe click at the old shorter-dialog
  coordinate long-pressed a row and crashed on an unrelated
  `ClipboardManager` null in the row long-press path; it is recorded here as
  a separate gap, not a history-render pass.
- `python3 verify-isakai/sway-flow.py calculator --work
  .cache/isakai/recheck/c6-history-fresh --evidence
  evidence/C6/2026-10-27/stacked-calc2 --startup-timeout 600` passes with
  31-second liveness and back exit 0. One earlier attempt flaked at the final
  back exit on a reused tree and passed alone on rerun; it is not claimed as
  a defect.
- OpenCalc passes fresh:
  `python3 verify-isakai/sway-flow.py opencalc --work
  .cache/isakai/recheck/c6-stacked-opencalc-fresh --evidence
  evidence/C6/2026-10-27/stacked-opencalc-fresh --startup-timeout 600`
  records fresh `12+34=46`, clipboard `46`, resize, 31-second liveness and
  clean close. A reused-tree run with a retained Syntax line did not record a
  new row; the fresh install passes, so the retained-tree failure is state,
  not a repair regression.
- Notes passes:
  `python3 verify-isakai/sway-flow.py notes --work
  .cache/isakai/recheck/c6-relativelayout-notes --evidence
  evidence/C6/2026-10-27/stacked-notes --startup-timeout 600` saves exact
  `Isakai 日本語 – note`, survives restart and edit, with 31-second liveness
  and exit 0. This supersedes the earlier same-gate failure recorded above.
- Notes picker side effect, recorded not claimed: the picker dialog now lays
  out visible rows (General/N2/N3/N4) where the pre-fix baseline collapsed
  to a near-empty box — compare
  [baseline](../evidence/C6/2026-10-27/notes-baseline/simple-04-note-picker.png)
  with [after](../evidence/C6/2026-10-27/sweep-notes/simple-04-note-picker.png).
  Row text still overlaps and a dark selection blob obscures rows, so the
  deferred picker-overlap defect stands, narrowed to row content/selection
  rendering rather than collapse.

Same-UID only. Default confinement still needs an authorized operator run.

### 2026-10-27 (clipboard) — every Context serves CLIPBOARD_SERVICE

A taller-dialog probe exposed a real crash: long-pressing a history row
called `Context.getSystemService("clipboard")` on the activity base
context (the Application, via translated `getBaseContext()`), which returned
null, and Kotlin threw `NullPointerException: null cannot be cast to
non-null type android.content.ClipboardManager`. Only `Activity` served the
clipboard; `Application`, `Service`, and the base `Context` returned null.
The owning-contract repair is one branch in `Context.getSystemService`:
return a lazily-created window-independent `ClipboardManager` (the class
 documents null-window local-only mode), so no context returns null for
this service. `Activity` keeps its window-bound manager, so system-clipboard
sync on the view-context path is unchanged.

`zig build` exits 0, `git diff --check` clean, lint zero violations.
Proven end to end on the probe-free build: a live drive opened history,
long-pressed an actual row, and `wl-paste` read back the row result `4`
with no `ClipboardManager` NPE and close exit 0
([drive](../evidence/C6/2026-10-27/clipcopy/final.log)). The base-context
copy reaches the Wayland selection through a process-default window bound
at each `runWindowed` open and cleared on close (with an `isOpen` guard, so
a closed window falls back to the local copy instead of throwing). The
calculator pinned flow also re-passes after this change
([runtime](../evidence/C6/2026-10-27/clipboard-calc-runtime.log)). Official contract: the Android `Context` reference documents
`CLIPBOARD_SERVICE` as "use with `getSystemService(String)` to retrieve
a `ClipboardManager`", with no per-context restriction
([excerpt](../evidence/C6/2026-10-27/clipboard-service-doc.txt), fetched
2026-10-27 from
`developer.android.com/reference/android/content/Context`). Our prior
Activity-only provision violated that contract for every other context.
Geometry lesson recorded: with a short two-row dialog (panel top ~337),
presses above the panel (e.g. y=312/318) miss all children, so the overlay
cancels the dialog on DOWN — that is correct cancel-on-outside behavior,
and the confusing early runs were presses on scrim, not rows. Open single
datapoint: an 80ms tap at scrim-adjacent (320,318) was consumed silently
(no click, no long-click, no overlay DOWN logged, dialog stayed open)
while a 1200–1500ms hold at the same coords reached the overlay and
canceled. Hit-testing is coordinate-identical for both, so one of the two
inputs must have taken a different path (tap swallowed, e.g. scroll
tracking, vs hold reaching the overlay); the mechanism is unidentified.
No flow asserts scrim-tap or row-tap behavior and no crash or wrong
rendering follows from it, so this stays an open question with exact repro
(open history at (450,16), tap vs 1500ms hold at (320,318)), not a defect.
Pattern note: this joins the OpenCalc `=` press that once had no effect on
a fresh tree and passed unchanged on rerun. Both look like an occasional
dropped input event between vinput, the compositor, and the bridge poll —
a single press with no effect, never a wrong effect, never a crash. The
in-repo delivery path was read end to end and ruled out: the 256-deep
native queue only drops on overflow (with a latched flag), pointer
button/motion events bypass the touch-header assembly that could stall,
and the Java drain loop runs until empty. With no in-repo drop mechanism
and no reproduction, there is no justified patch; suspect below-framework
delivery first if a third instance ever hits an asserted surface.

### 2026-10-27 (tint hypothesis: tested, falsified, reverted)

A real attempt at the picker blob: `View` stored `backgroundTintList` but
never forwarded it to the background drawable, so a tint-dependent FAB
background would render untinted. The fix (forward tint in the three
setters, mirroring AOSP) built clean and the notes flow still passed — but
the picker screenshot was unchanged, blob intact. Falsified, not fixed:
the change was fully reverted to keep only proven fixes, and the reverted
tree re-passes the calculator flow
([runtime](../evidence/C6/2026-10-27/revert-calc-runtime.log)). Takeaway:
the blob is not an untinted view background. Remaining suspects are the
translated CoordinatorLayout/FAB behavior or a failed drawable that logs
nothing; both need a dedicated session with the now-working JDB
(`locals`-only) rather than more guessing.

### 2026-10-27 — fix plans for the open items (researched, not yet implemented)

**Compose blank settings — researched from official sources.** The
framework deliberately reports `isHardwareAccelerated() == false` to steer
Compose onto software fallbacks instead of RenderNode paths it does not
implement. AOSP `AndroidComposeView` (platform/frameworks/support, read
in full) draws nothing in `onDraw`; all content goes through
`dispatchDraw` via `canvasHolder.drawInto(canvas) { root.draw(...) }`, with
a `ViewLayer.shouldUseDispatchDraw` branch decided by a reflective
capability test against the host `View` class. Our framework already knows
about Compose's zero-size `DrawChildContainer`. So the blank screen is one
of three falsifiable causes: (H1) the dispatchDraw path our `View` offers
does not match what that reflective test selected; (H2) the software
`Canvas` methods Compose's `AndroidCanvas` wrapper calls are missing or
no-ops here; (H3) `measureAndLayout` never runs on this screen (taps land,
so layout likely runs, which already weakens H3). Plan: one temporary
probe run on the settings screen — log `View.draw` entry by class (proves
whether `AndroidComposeView` is reached), then log the failing leg's
inputs. Implement only the leg the evidence names; do not build a Compose
pipeline on speculation. Estimated: one focused session, not a project.

**FAB black blob — narrowed, plan set.** Proven so far: rows lay out
correctly; the blob is a 56x56 opaque black rounded rect drawn through
our `RippleDrawable` by translated code; view tint forwarding was tried and
falsified (reverted); our blending, drawables logging, and GONE handling
are exonerated; official `Context.getSystemService` docs confirm the
clipboard-adjacent contract work but do not cover Material internals.
Plan: temporary draw-call probe naming the drawable class and fill color at
the blob's bounds, then fix exactly that path (theme attr resolution is the
prime suspect, drawable inflation second). Estimated: one focused session.

**xkb adversarial stress — no plan possible in-repo.** The verification
skill forbids test cases, test suites, and test-only harnesses, and
explicitly bars recreating removed suites or adding test dependencies. A
keymap-churn harness is exactly that. Stays an explicit gap unless the
policy changes; the passing typed flows stand as the evidence.

**Input flakes (tap, `=`) — pattern recorded, no action until a third
instance.** Two single-press-no-effect events with no wrong behavior and no
crash, both passing on immediate rerun. In-repo delivery was read end to
end (256-deep queue with latch, touch assembly, pointer path, Java drain)
and offers no drop mechanism. Next step only if it recurs on an asserted
surface: back-to-back identical presses, then suspect below-framework
delivery.

**Independent review — awaiting a human.** No second model is
available in this environment, and prior tasks closed on self-review plus
real-app proof. The operator (active in this loop) can supply it: read
`git show df1f41b --stat`, then the five framework/src diffs, and post
objections to the owning task file. Until then it is recorded as
self-review, not independent.

### 2026-10-27 (settings blank) — translated dispatchDraw is never invoked (root cause found, fix is architect-scope)

Root cause, proven: our draw pipeline uses parallel `*At` methods
(`onDrawAt`, `dispatchDrawAt`) that translated code never overrides.
AOSP's `onDraw` still runs (called from `onDrawAt`), but AOSP's
`dispatchDraw(Canvas)` is dead code in our tree — nothing calls it, and
only our own `FrameLayout`/`LinearLayout`/`ListView` declare it as dead
overrides of `dispatchDrawAt`'s siblings. Any translated view that does its
custom drawing in `dispatchDraw` (Compose hosts like the settings screen's
`r1` root, history rows' `ViewHolder` item views) is therefore silently
skipped: draw entry points fire, children lay out, taps hit, but the
custom paint never runs.

Why not just call it: for leaves (no children) calling
`dispatchDraw(canvas)` is safe and sufficient — our own leaves have no
custom `dispatchDraw`, so behavior is identical except translated leaves
finally draw. For groups it is unsafe without a protocol bridge: their
translated `dispatchDraw` implementations assume the AOSP canvas-translate
protocol, while our pipeline positions children with explicit offsets, so
calling both would double-draw children (once right, once wrong).
`r1.w` demonstrably calls `super.dispatchDraw`, which is exactly the
double-draw trap. The correct fix is a protocol decision, not a patch:
either translate the canvas and switch the whole draw path to the AOSP
pair (a framework-wide migration), or route per-view by contract
(translated-custom-dispatch vs ours). Both demand the architect skill
and a full pinned-app repaint verification.

This was deliberately NOT implemented here: the acceptance gate passed on
the committed tree and forbids broader Compose claims, and a wrong guess
at the bridge would regress every app. The task's remaining open items
keep their owners.

Correction to the earlier Compose theory: a class-level census of actual
draw calls on the settings screen shows zero Compose classes. The screen is
built entirely of custom translated views — an `r1.w` ViewGroup holding an
`r1.s1` group holding ~44 `r1.v2` leaves. TextViews on that screen draw
only empty strings; every settings string is absent from draw, while taps
land and flip preferences (input is bounds-based, independent of draw).

Draw-bounds census proves the leaves never get positioned: every `r1.v2`
draws at parent-relative (0,0) with measured sizes (170x17, 536x17, 640x64
and the like); exactly two `offsetLeftAndRight/offsetTopAndBottom` calls
occur in the whole run (one -30,-30 view). Their `onDraw` path goes through
a `b1/c` canvas wrapper and `r1/b2` renderer gated on software rendering.
The `r1.s1` container itself measures 0x0. So this is a custom-view
position-then-draw gap in translated app code, not a Compose pipeline gap
and not a framework measure/draw contract gap: the draw entry points fire,
the views simply have nothing positioned or nothing to paint.

No code changed. All diagnostic probes were removed afterward
(`grep IsakaiProbe framework/` returns zero) and the probe-free tree was
re-verified on the real history flow (formulas and results visible, exit 0).
Next step if this is ever scheduled: translated-code archaeology on
`r1.w`/`r1.s1` layout plus `r1.v2.onDraw`'s renderer path, or working draw
instrumentation with per-view identity.

Update, same day: the `Canvas.drawTextImpl` probe result is VOID — that
overload is not on the real text path — so the corrected choke-point
probe in `emitText` (which every text path funnels through) was run,
plus rects, round rects, circles, paths, ovals, lines and bitmaps: the
settings screen draws zero text (63 main-screen glyph draws vs 0 after
SettingsActivity starts) and zero of every shape primitive — only sane
non-empty clips fire. The custom `r1` rows execute draw entry points that
emit nothing at all. All diagnostic probes were removed afterward (`grep IsakaiProbe
framework/` returns zero) and the clean tree rebuilds green with a
passing history re-verification.

Correction history (kept so nobody repeats the dead ends): an early
`drawTextImpl` probe was void because that overload is not on the real
text path; `emitText` is the choke point every text path funnels through
(`drawText*`, `paintText`, `DisplayList` replay), and it recorded 63
glyph draws on the main screen vs zero after SettingsActivity starts.
`fillRect`, the workhorse for solid fills, was never probed — but fills
do not explain a blank screen with no text. A `locals`-only JDB session
confirmed draw entry on `Activity.drawFrame` but the evaluator is broken
(`print 1+1` answers `4`), so live object inspection remains unavailable.
The precise next step is static: trace what `r1/b2.a` does with its
renderer after the `b1/r.c()` call, looking for the early-out or empty
model that makes 44 laid-out, hittable rows emit nothing.
`r1.v2` has no `onDraw` override; its drawing entry is
dispatchDraw through the wrapper/renderer. The b1 wrapper's save/restore
and paint plumbing in our Canvas is structurally sound, and no drawable
errors are logged, so the content never arrives rather than arriving and
failing to paint. JDB status corrected: `locals`, `threads` and `where`
work in framework frames (`where` traced the picker blob to
`RippleDrawable`); only object *evaluation* (`dump`/`print`/`eval`) is
broken, and `print 1+1` answering `4` is the tell.

The blank settings screenshot was scoped, not fixed. The earlier
composable-shape guess (`k7.i.a(j0.i, int)`) is withdrawn: a class census
of actual draws proves the screen is custom translated `r1` views, with
zero Compose classes anywhere in the draw path. Preference taps still flip
and persist because touch dispatches on bounds, independent of draw.
What remains is a translated-app internals question — rows whose draw
entry points run yet emit nothing — needing a dedicated session with
working draw instrumentation, not a framework rewrite. This stays an
explicit deferred defect.

### 2026-10-27 (escape) — history dialog dismisses via back key

A live drive opened history at `(450,16)`, sent `key("Escape")`, and
captured the main calculator with no modal or scrim, app alive and close
exit 0 ([open](../evidence/C6/2026-10-27/escape/simple-escape-open.png),
[after](../evidence/C6/2026-10-27/escape/simple-escape-after.png),
[drive](../evidence/C6/2026-10-27/escape/drive.log)). Both OK-button and
Escape dismissal are now proven; the earlier Escape gap is closed.

### 2026-10-27 (clean sweep) — all pinned flows green on one build

One `zig build` (exit 0), then every affected real flow on the isolated
Sway fixture, same-UID throughout:

- Calculator:
  `python3 verify-isakai/sway-flow.py calculator --work
  .cache/isakai/recheck/c6-history-fresh --evidence
  evidence/C6/2026-10-27/sweep-calc --startup-timeout 600` — exit 0,
  fresh `2+2=4`, decimal flip, `1,5+2,5=4`, persistence, 31s liveness, back
exit 0
  ([runtime](../evidence/C6/2026-10-27/sweep-calc-runtime.log)).
- OpenCalc: fresh install into `.cache/isakai/recheck/c6-sweep-ocalc`, then
  the pinned flow — first attempt showed the pre-evaluation red
  `12+34`/`Syntax` preview with no new row (the `=` press missed), reran
  unchanged on the same tree to exit 0 with recorded `46`, clipboard `46`,
  resize, 31s liveness and clean close
  ([first](../evidence/C6/2026-10-27/sweep-ocalc-runtime.log),
  [rerun](../evidence/C6/2026-10-27/sweep-ocalc2-runtime.log)). The miss is
  recorded as input flake, not a repair regression: no source changed
  between attempts.
- Notes: exit 0, exact `Isakai 日本語 – note` saved, restart/edit verified,
  31s liveness
  ([runtime](../evidence/C6/2026-10-27/sweep-notes-runtime.log)).
- Operator rerun: the plain `python3 verify-isakai/sway-flow.py calculator`
  command on a fresh baseline tree passed independently — `2+2=4`, decimal
  preference flipped `false→true` and persisted, `1,5+2,5=4`, 31-second
  liveness, back exit 0. Same-UID pass on freshly translated code, not
  privileged evidence.
- History: formulas and results visible, OK at `(302,436)` dismisses to the
  main screen, close exit 0
  ([open](../evidence/C6/2026-10-27/sweep-history/simple-history-open.png),
  [after](../evidence/C6/2026-10-27/sweep-history/simple-history-ok.png),
  [drive](../evidence/C6/2026-10-27/sweep-history/drive.log)).

### 2026-10-23 — context-hierarchy fix and old Calculator history

Build green (`zig build`, exit 0, no `error:` lines). Isolated sway fixture
headless (the EGL/`swaybg` notes are the known non-fatal fixture warnings).

No regression from wiring `attachBaseContext` for every app. The hierarchy
change first surfaced two latent framework gaps that the whole app startup now
exercises: AppCompat's locale-metadata probe NPE (fixed via `getServiceInfo`
`metaData`) and `Resources.Theme.rebase()` (added). After those, the three
pinned flows pass on fresh single runs (back-to-back runs share the compositor
and flake, so each was rerun alone):

```sh
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/c7svc-calc --evidence .cache/isakai/evidence/c6-regress3-calculator-20261023
python3 verify-isakai/sway-flow.py opencalc   --work .cache/isakai/recheck/c7svc-ocalc --evidence .cache/isakai/evidence/c6-ocalc-clean-20261023
python3 verify-isakai/sway-flow.py notes      --work .cache/isakai/recheck/notes       --evidence .cache/isakai/evidence/c6-notes-clean-20261023
```

- Calculator: `PASS` (2+2 row, settings, decimal preference, restart, exit 0).
- OpenCalc: `PASS` (12+34→46, clipboard, resize, 31 s, exit 0).
- Notes: `PASS` (Unicode note, picker return, restart, append, exit 0).

Old Calculator 5.11.3 (`.cache/isakai/oldcalc-u`) now starts past
`attachBaseContext`: Splash→MainActivity→SettingsActivity, `bridge: client
uid 1000 connected`, no `NoSuchMethodError` and no NPE (the reverted
experiment crashed here with `createConfigurationContext` missing). The stock
calculator flow still fails its decimal-preference assertion on 5.11.3, whose
older Settings has no such preference — an app-version mismatch, not a
framework fault.

History dialog drive (keypad `5+1=` committed history id 3 `5+1→6`; clock
icon opened the dialog): process alive, zero `getBaseContext`/`ClassCast`/
exception lines, and the dialog bound all three DB rows (`2+2=4`, `2+2=4`,
`5+1=6`). The modal scrim and OK/Clear render; the row **text** does not
(the deferred dialog-list-content defect recorded under B-rollback-context-hierarchy).
The drive used the existing `sway_backend` through a throwaway script, deleted
after the run (no test artifact left behind).

Evidence:

- [.cache/isakai/evidence/c6-oldcalc-fixed-20261023/](../.cache/isakai/evidence/c6-oldcalc-fixed-20261023/) (5.11.3 startup past attach, Splash→Main→Settings)
- [.cache/isakai/evidence/c6-oldcalc-history-20261023/simple-02-history.png](../.cache/isakai/evidence/c6-oldcalc-history-20261023/simple-02-history.png) (history dialog open over `5+1=6`, scrim + OK/Clear, no crash)
- [.cache/isakai/evidence/c6-regress3-calculator-20261023/](../.cache/isakai/evidence/c6-regress3-calculator-20261023/), [c6-ocalc-clean-20261023](../.cache/isakai/evidence/c6-ocalc-clean-20261023/), [c6-notes-clean-20261023](../.cache/isakai/evidence/c6-notes-clean-20261023/)

### 2026-10-16 — C7 continuation

Update 2026-10-19: all three flow blockers above are recorded resolved with
rerun artifacts; the open items reduced to default confinement (environment),
Notes editor-to-list back behavior and the pending full-diff review. The
[2026-10-19 strip-and-reverify record](C7-acceptance-gate.md#2026-10-19--instrumentation-stripped-and-the-gate-reverified)
owns commands and evidence.

Update 2026-10-20: default confinement is proven by the three pinned
privileged flows recorded in
[C7](C7-acceptance-gate.md#2026-10-20--three-pinned-apps-under-default-per-app-confinement).
The Notes editor-to-list question is resolved as app-owned behavior: the
toolbar folder icon is the app's supported route from the editor to the
modal note picker, while its back action (Escape) finishes the process and
has no editor-to-list branch. No framework navigation was manufactured. The
picker's overlapping row rendering stays a visible defect; the C7 full-diff
review is the remaining close-out item. The final privileged Calculator run
also re-exposed a blank settings surface: both `simple-03-settings.png` and
`simple-04-toggled.png` are one-colour white and byte-identical. The
preference flip in `Prefs.xml` and the decimal result `1.5+2.5=4` still prove
the functional path, but no visible Compose settings screen is claimed for
that run; this strengthens the deferred settings draw/hit defect rather than
reopening C7's calculation/restart acceptance.

The [C7 resumed gate record](C7-acceptance-gate.md#2026-10-16--resumed-gate-notes-failure-reproduced)
owns the exact UI commands and retained artifacts. Real Notes execution exposed
missing `View.requestFocus(int)` and recursive `removeViewInLayout` dispatch
through translated ViewPager. The overload now uses existing focus handling;
view-removal entry points share private index-based removal. Notes subsequently
passes Unicode restoration, visible append/save, modal-root return, 31-second
liveness and clean exit under same-UID execution. Directional selection is not
fully implemented, and default-confined acceptance remains open.

The real rolled-back Calculator exposed zero-extension of shortened signed
DEX static constants. The decoder now sign-extends signed byte/short/int,
leaves char unsigned, and consumes no payload for null. The existing translator
CLI identified the null-alignment failure in the shipped ConstraintLayout
class, not a generated test case. Build passes; the rollback UI retry and final
review remain recorded under C7. No automated test or probe was added.

### 2026-10-11 — partial

Resolved measured Notes startup blockers: DatabaseErrorHandler and exact SQLiteOpenHelper constructor, onConfigure, WAL/version APIs, atomic migrations with rollback; drawable tint for solid SRC_IN, bitmap-backed Canvas, preserved layer-list IDs and drawable attributes. Actual production-confinement probe passes Unicode SQLite writes, reopen/version persistence and failed migration rollback. Target Notes now reaches ImageView.setColorFilter and MediaDataSource failures; Calculator reaches drawable resource 0x7f0801ba failure. OpenCalc opens/closes but remains largely blank; a prior intermittent nPost error needs continued observation. None of the three useful app flows has passed. Further traced Calculator drawable 0x7f0801ba to unhandled ripple XML; static content/mask inflation now succeeds and actual startup advances to missing android.view.Gravity. Ripple animation remains explicitly unavailable.

Artifacts:

- [.cache/isakai/pinned-runtime.log](../.cache/isakai/pinned-runtime.log)
- [.cache/isakai/pinned-java-tests.log](../.cache/isakai/pinned-java-tests.log)
- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.window.log](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.window.log)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.window.log](../.cache/isakai/evidence/com.simplemobiletools.calculator.window.log)
- [.cache/isakai/evidence/com.darkempire78.opencalculator.png](../.cache/isakai/evidence/com.darkempire78.opencalculator.png)

### 2026-10-11 — partial

Fixed measured Notes launch blocker: Intent.removeExtra returned Intent, but translated app bytecode calls AOSP descriptor (Ljava/lang/String;)V; changed to void (no framework callers chained). Notes Splash now launches windowed, exit 0, no exceptions. Fixed OpenCalc blank window (two root causes, both AOSP-semantics divergences): (1) TableLayout measured MATCH_PARENT rows at full parent height, overflowing the vertical weight pass so WRAP rows clamped to EXACTLY 0 — now forces TableRow children to WRAP_CONTENT per AOSP TableLayout.measureVertical, rows share 995px evenly (5x199px); (2) drawable-free WRAP_CONTENT ImageView (menu button 0x7f09014e) measured full parent via default View.onMeasure and painted white over siblings — added AOSP ImageView.onMeasure (no drawable = intrinsic 0), menu button now 0x0. OpenCalc window now shows grey button grid with labels (244 colors, content bbox full window). Added regressions: IntentSignatureTest (void descriptor), TableLayoutMeasureTest, ImageViewMeasureTest; 14/14 framework tests pass. Calculator Gravity/setColorFilter/MediaDataSource/drawable failures were stale (already fixed). Remaining: Notes/Calculator Splash->Main navigation blocked on AppCompat theme check in translated AppCompat (needs framework answer), so C1 flows still unverified; OpenCalc display area still blank (adapter-driven panel, separate issue).

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.png](../.cache/isakai/evidence/com.darkempire78.opencalculator.png)
- [.cache/isakai/evidence/com.darkempire78.opencalculator.window.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.window.log)
- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.window.log](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.window.log)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.window.log](../.cache/isakai/evidence/com.simplemobiletools.calculator.window.log)

### 2026-10-11 — partial

Fixed in-app navigation theme propagation: Navigator.launch now hands the caller's manifest app/activity themes to the new Activity (Activity.attachTheme + Context.baseThemeForNav); previously MainActivity had no theme and translated AppCompat threw 'You need to use a Theme.AppCompat theme' during Splash->Main navigation. Headless probe with the fix: Splash navigates to Notes MainActivity, lifecycle OK, content tree measures (toolbar 949x25, search EditText 949x25, ViewPager 949x1036). Live Notes window still renders blank: same content renders 0 non-white pixels headless (empty toolbar/text, not a draw-path failure — EditText with 'hello' renders 187 pixels). Next: drive text input into the live window to prove the note flow; window vanishes under wtype focus-steal need care (close via compositor races input). OpenCalc input echo still unverified (display-band diff showed size-mismatch noise only).

Artifacts:

- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.preshot.png](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.preshot.png)
- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.drive.log](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.drive.log)

### 2026-10-11 — partial

Drove OpenCalc calculate flow live: typed 12+34= via wtype; app logs Equation BigDecimal 1/12/12+/12+3/12+34/12+34= and Unexpected = (parsed but = unhandled in expression path), clean exit 0 via Escape. Fixed framework gaps the flow exposed: View.awakenScrollBars+scrollbar enable (RecyclerView history), ViewGroup.getFocusedChild, Color.colorToHSV/RGBToHSV/HSVToColor (CardView), View.setClipToOutline/getClipToOutline/setOutlineProvider (CardView), android.text.format.DateUtils incl. build.zig registration (history timestamps), View.hasFocusable + ViewGroup override, View.onScrollChanged wired into scrollTo, Canvas.drawRoundRect(RectF/floats,Paint) with FILL/STROKE, ViewGroup attach/detach scrap-cache + addFocusables/focusSearch/onRequestFocusInDescendants/requestChildFocus/offset rects/getChildDrawingOrder (RecyclerView/LinearLayoutManager). Fixed key dispatch: ViewGroup.hasFocus recurses into descendants and dispatchKeyEvent follows hasFocus (was isFocused, never true on parents) — keys now reach nested EditText (KeyDispatchTest, 15/15 pass). Remaining: display area blank in screenshots (HorizontalScrollViews measure 949x0 from 0.0dp height); result visible only in logs; copy/resize sub-flows not yet driven.

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.calc2.png](../.cache/isakai/evidence/com.darkempire78.opencalculator.calc2.png)
- [.cache/isakai/evidence/com.darkempire78.opencalculator.drive2.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.drive2.log)

### 2026-10-11 — partial

Fixed OpenCalc display band: MarginLayoutParams now tracks layout direction (get/setLayoutDirection) and resolves at addView (attach) and View.layout, matching AOSP; translated ConstraintLayout copies guide fields (a/b/c->m0/n0/o0) only inside resolveLayoutDirection, which the framework never called, so guidelines stayed at 0 and the 0.0dp scroll views collapsed to 949x0. After fix (single pass): HSV1 949x178 at y8-186, HSV2 949x83 at y186-269. Live calc drive now shows display content: cursor line pre-input, input glyphs + history text post-input (calc2.png bands y8-16, y84-96). Added LayoutDirectionTest (16/16 pass). Live Notes: tap path fixed (ViewGroup.isChildrenDrawingOrderEnabled/setChildrenDrawingOrderEnabled, View.getZ/setZ); headless tap+type renders (field 386px, text hello notes) but live window needs compositor focus that races close; preshot/typed still 1-color without focus. Remaining: Notes/Calculator live input focus, OpenCalc copy/resize, solver ArrayIndexOutOfBounds noise (caught+logged, exit still 0).

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.calc2.png](../.cache/isakai/evidence/com.darkempire78.opencalculator.calc2.png)
- [.cache/isakai/evidence/com.darkempire78.opencalculator.drive2.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.drive2.log)
- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.typed.png](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.typed.png)

### 2026-10-11 — partial

Calculator calculate flow verified headless via tap injection: Splash->Main navigation (AppTheme 0x7f12000c), keypad rows measure (5x~141px), tapped 2+2= through dispatchTouchEvent, result display=4, formula=2+2. Fixed gaps: ViewParent nested-scroll defaults (NestedScrollView.onInterceptTouchEvent), OverScroller.springBack, View.getZ/setZ, ViewGroup drawing-order-enabled flag. Live Calculator window still needs compositor-safe input (wtype races close; observe preshot shows 233-color content). Notes live input has same focus race; headless tap+type renders (386px).

Artifacts:

- [.cache/isakai/evidence/com.simplemobiletools.calculator.preshot.png](../.cache/isakai/evidence/com.simplemobiletools.calculator.preshot.png)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.drive.log](../.cache/isakai/evidence/com.simplemobiletools.calculator.drive.log)

### 2026-10-11 — partial

OpenCalc equals/copy path analyzed: typed 2+2 reaches display (Equation BigDecimal logs), but equalsButton evaluates through a Gson-backed expression store (c1.d.b parses JSON, not raw text) and yields result Syntax error both via direct equalsButton() call and = button tap. Direct c1.d.b("2+2") throws Expected BEGIN_ARRAY but was STRING — the app-side store format, not a framework gap; copy/resize sub-flows blocked on the app producing a result. Calculator fixed gaps this round: ViewParent nested-scroll defaults, OverScroller.springBack. Notes fixed gaps: ViewGroup drawing-order-enabled flag, View.getZ/setZ; Notes headless tap+type renders text (386px).

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.drive2.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.drive2.log)

### 2026-10-12 — partial

Resolved margin direction is now stored by resolveLayoutDirection, and replacement layout params are resolved before onMeasure (not only attach/layout). Regression covers RTL replacement params before measurement. All 16 Java test programs pass. Regenerated inventory (483 production Java sources); repository graph/path/doc/inventory checks pass. zig build returned exit 0 after global-cache permission approval. Real pinned-app visible flows were not rerun for this change; C6 and C7 remain incomplete.

Artifacts:

- [.cache/isakai/direction-regressions.log](../.cache/isakai/direction-regressions.log)

Command:

```sh
python3 tools/test-framework.py; python3 tools/framework-inventory.py; python3 tools/check-repository.py; zig build
```

### 2026-10-12 — partial

Reproduced stale OverScroller animation after an in-range springBack call. Reset current/start/final coordinates and finish the previous animation, matching AOSP state semantics; out-of-range timing remains an approximation. New regression fails before the change and passes after it, covering cancellation, both-axis bounds, abort and inclusive boundaries. All 17 Java test programs and repository checks pass. No visible pinned-app acceptance rerun; C6/C7 remain incomplete. Prior attribution of OpenCalc equals failure to app-side JSON format was not established and is withdrawn.

Artifacts:

- [.cache/isakai/scroll-regressions.log](../.cache/isakai/scroll-regressions.log)

Command:

```sh
python3 tools/test-framework.py; python3 tools/check-repository.py; git diff --check
```

### 2026-10-12 — partial

New regreuse differential DEX case reproduced integer sentinel comparison using stale object slots after a terminated throwing block; translator now prioritizes reaching integer definitions. Full oracle passed 17 corpus cases plus two line-table checks. Added translator source/toolchain identity to the translation cache marker so existing installations regenerate stale bytecode after fixes; CLI regression proves valid-cache reuse and legacy/different-translator invalidation. Notes progressed past the fragment-container failure and measured missing View/Bundle APIs, with a fresh same-UID observed window exiting zero. Calculation/copy and all full target acceptance flows remain incomplete.

Artifacts:

- [.cache/isakai/regreuse-before.log](../.cache/isakai/regreuse-before.log)
- [.cache/isakai/regreuse-final.log](../.cache/isakai/regreuse-final.log)
- [.cache/isakai/oracle-current.log](../.cache/isakai/oracle-current.log)
- [.cache/isakai/translation-cache.log](../.cache/isakai/translation-cache.log)
- [.cache/isakai/notes-fresh-window.log](../.cache/isakai/notes-fresh-window.log)

Command:

```sh
oracle/diff.sh; python3 verify-isakai/check-translation-cache.py; python3 verify-isakai/observe-app.py 2 --work .cache/isakai/recheck/notes
```

### 2026-10-12 — partial

Full core gate passes after the translator/framework fixes: native build/tests, 20 Java regression programs, 17 DEX oracle cases plus line checks, runtime confinement probe, compatibility diagnostics, APK limits, packaging, process-exit, and translation-cache checks. Fresh OpenCalc launch also passes after adding Display.getRefreshRate. Remaining failures are acceptance-flow gaps rather than core test regressions.

Artifacts:

- [.cache/isakai/checks/core](../.cache/isakai/checks/core)
- [.cache/isakai/evidence/com.darkempire78.opencalculator.window.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.window.log)

Command:

```sh
tools/checks.sh core; python3 verify-isakai/observe-app.py 0 --work .cache/isakai/recheck/opencalc6 --startup-timeout 240
```

### 2026-10-12 — partial

Fixed a measured translator defect: DEX encoded static field values were being dropped, leaving initialized ints at JVM zero. The fix emits JVM ConstantValue attributes for byte/short/char/int/boolean static values. OpenCalc ConstraintLayout's static capacity field now initializes correctly; its prior ArrayIndexOutOfBoundsException no longer appears during a fresh launch.

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.window.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.window.log)
- [.cache/isakai/recheck/opencalc9/install.log](../.cache/isakai/recheck/opencalc9/install.log)

Command:

```sh
zig build test --summary all; python3 verify-isakai/observe-app.py 0 --work .cache/isakai/recheck/opencalc9 --startup-timeout 240
```

### 2026-10-12 — partial

Reproduced Calculator's Drawable.tint warning at w7.j.S bci=221 on VectorDrawable; its fillColor was @0x106000b, unsupported by ColorStateList. Added filled-path vector rendering for M/L/H/V/C/S/Q/T/A/Z, group transforms/clips, alpha compositing and SRC_IN/stateful tint; ColorStateList now resolves numeric @0x... color refs. VectorDrawableTest covers absolute/relative lines, cubic/quadratic/arc paths, transforms, clipping, alpha, stateful tint, system color lookup, and missing-ID rejection. Full core gate passes: 37/37 Zig steps, 21 Java programs, 17 DEX corpus cases plus line checks, runtime confinement, compatibility diagnostics, APK limits, and packaging. Fresh Calculator startup exits 0 without Drawable.tint/VectorDrawable.fillColor warnings. Screenshot unchanged from baseline; no calculation/settings/restart flow rerun, so C6/C7 remain in_progress. Vector strokes, trim paths, gradient fills, generic color filters, LayerDrawable parent tint, and non-SRC_IN tint remain incomplete.

Artifacts:

- [.cache/isakai/checks/core](../.cache/isakai/checks/core)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.vector-drawable.log](../.cache/isakai/evidence/com.simplemobiletools.calculator.vector-drawable.log)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.vector-drawable.png](../.cache/isakai/evidence/com.simplemobiletools.calculator.vector-drawable.png)

Command:

```sh
python3 tools/test-framework.py; tools/checks.sh core; python3 /tmp/isakai-layer-tint-smoke.py
```

### 2026-10-12 — partial

Closed measured translator/framework failures from live OpenCalc runs: android.animation.ValueAnimator.getInterpolator (RecyclerView item animator died with NoSuchMethodError), Animator/ValueAnimator state (duration/delay/repeat/frame delay/evaluator/duration scale/pause-resume listeners), ViewPropertyAnimator.setListener (returning the animator), View.hasTransientState/setHasTransientState. Regressions: android.animation.ValueAnimatorApiTest plus the 24 existing Java programs. OpenCalc now runs its RecyclerView layout without exceptions (previously two NoSuchMethodError crashes per launch).

Artifacts:

- `/tmp/isakai-checks-final/java.log`

Command:

```sh
zig build test-java
```

### 2026-10-12 — partial

Vector strokes and trim paths implemented (previously emitted as unsupported): strokeColor/strokeWidth/strokeAlpha/strokeLineCap/strokeLineJoin/strokeMiterLimit outline the geometry through java2d BasicStroke and paint as a separate pass (fill preserved), and trimPathStart/trimPathEnd/trimPathOffset cut the flattened geometry including wrap-around trims. The rasterizer samples 2x2 per pixel so a 1px stroke on a pixel boundary is not missed. Extended android.content.res.VectorDrawableTest covers stroke-only, fill+stroke, half trim, full trim and empty trim.

Artifacts:

- `/tmp/isakai-checks-layout/java.log`

Command:

```sh
zig build test-java
```

### 2026-10-13 — partial

Translator forward-ctor float/double fix (iload->fload, lload->dload; j0.h1 VerifyError closed) with regression test; translator fingerprint auto-regenerates stale caches. Framework: input_method service wired, Activity.getCurrentFocus, Window.setSoftInputMode/setDecorFitsSystemWindows, ViewGroup.setClipChildren, AccessibilityStateChangeListener, android.net.Network, MotionEvent.recycle, ViewParent.requestDisallowInterceptTouchEvent, LayoutInflater attrBool/minHeight/minWidth/elevation, TextView compound drawables (measure+draw), Z-ordered draw/hit dispatch. TouchOffsetTest added (27 Java programs pass). Remaining: AppBar elevation/behavior default for the Calculator toolbar overlap.

Artifacts:

- [src/translate.zig](../src/translate.zig)
- `framework/android/view/TouchOffsetTest.java` (historical/unavailable)

Command:

```sh
zig build test --summary all (37/37 steps, 19/19 tests); python3 tools/test-framework.py (27 programs)
```

### 2026-10-14 — partial

REUSE_ASSESSMENT PoC-1 (native ABI honesty): fixed 4 shim contract mismatches in src/native/bionic_shim.zig (log_assert string-cond+always-abort, is_loggable (prio,tag,def) order with log.tag.* lookup, property_list 3-arg callback, system_property_set int return). Throwaway C caller with exact AOSP android-15 prototypes: 10/10 checks pass (script kept out of repo under UI-only policy). Fixed a latent translator regression the first rebuild exposed: receiver narrowing to java/lang/Object breaks protected access (B0/a.values() VerifyError 'Bad access to protected data'); Object-narrowing is now always skipped since every reference is already assignable. javap confirms the known-good shape; headless OpenCalc launch exits 0 with full lifecycle.

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.sway-opencalc.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.sway-opencalc.log)

Command:

```sh
zig build; python3 verify-isakai/sway-flow.py opencalc --work .cache/isakai/recheck/opencalc10
```

### 2026-10-14 — partial

Calculator settings renders blank: SettingsActivity opens (content structure attaches: AppCompat FrameLayout + container + stub) but the Compose settings UI (r1.l1/r1.w full-size) never gains composed children over 70s. setContent runs and the composition gate flag is set (verified in translated bytecode); jstack shows zero worker threads, so the recomposer loop never runs. No fragments involved (app uses none for settings); the coroutines dispatcher runtime is stripped to exception classes. Next: trace what launches the recomposer loop in the obfuscated Compose runtime (j0) and what dispatcher/context it needs (candidate: stage META-INF/services resources and/or back Dispatchers.Main with the main looper)).

Artifacts:

- [.cache/isakai/evidence/simple-04-settings.png](../.cache/isakai/evidence/simple-04-settings.png)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.sway-calculator.log](../.cache/isakai/evidence/com.simplemobiletools.calculator.sway-calculator.log)

Command:

```sh
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/calculator2
```

### 2026-10-14 — partial

ReleaseSafe build passes after bounded native repairs. All four real XKB null-state/map calls that previously crashed now return safely; Wayland group uses locked_layout and saved modifiers are reapplied after keymap creation. Property defaults are bounded to 91 bytes plus NUL; property_list snapshots and unlocks before reentrant caller callbacks. Direct DSO canary/reentrancy checks pass. Installer now checks selected x86_64 library paths and ELF headers; final CLI gate verification is pending. Full reordered-keymap/modifier stress and default-isolated native APK support are not claimed.

### 2026-10-15 — partial (Notes new-note dialog chain)

Drove the Simple Notes "+" (menu item `new_note`) through its real dispatch chain on isolated sway, fixing each measured failure with a live rerun. All fixes are bounded framework/translator gaps; each moved the observed crash forward:

- `android.accounts.AccountsException` + `NetworkErrorException` added (menu dispatch referenced the missing class; crashed the main thread on tap).
- Translator array-component analysis made descriptor-aware: `sget/iget-object` field types seed it and `aget-object` peels one dimension (`[[F`→`[F`→`faload`). Fixed the `VerifyError` in translated `ColorStateListInflaterCompat` (`iaload` on `float[]`).
- `android.util.StateSet` added (trim/match semantics) for selector inflation.
- `AnimationUtils.loadInterpolator` implemented (linear/accelerate/decelerate/accelerate-decelerate/path; others fail loudly) for `TextInputLayout` construction.
- `ViewGroup.setAddStatesFromChildren` (retained flag), `View.hasOnClickListeners`, `Activity.isDestroyed`, `Context.getText`, `TextView.setTextAppearance` (both overloads; theme-resolved color/size, current values otherwise), `TextView.setLinkTextColor`, `TextView.setTypeface` (both overloads; retained), `View.onCheckIsTextEditor` (base `false`), full `View` nested-scroll child API (inert defaults matching `ViewParent`), `View.playSoundEffect` (inert like haptics), `ViewConfiguration.getScaledWindowTouchSlop`, `Context.getMainLooper`, `PopupWindow` 4-arg ctor + 3-arg `getMaxAvailableHeight` + `setWindowLayoutType`, `AbsListView.setAdapter` override (generic-erasure linking), deprecated `removeGlobalOnLayoutListener` alias, `Display.getRealSize/getRealMetrics` + `getDefault`, `"window"` system service (display-only manager).
- `LayoutInflater` registers `RadioGroup`.
- Index-based `AttributeSet` getters added (interface + all four adapters).
- `ContextThemeWrapper.getTheme` now layers base theme + `themeResId` (was empty; Material components require it).
- `Dialog` gained a `HeadlessWindow` (was null), a routing window callback, `show()` running `onCreate` once, a working back dispatcher, and decor-searching `findViewById`.
- `Resources.readBag` keeps dangling references for lazy theme resolution instead of dropping the whole item (styles with style/attr references parsed as empty before); `TypedArray.getDrawable` degrades unresolvable references to null like its siblings.
- Notes flow driver: opens the editor through the toolbar "+" with fragment-transaction assertion and tap retries (the main list starts empty; stale-history risks from N1 still apply).

Artifacts:

- `.cache/isakai/evidence/c7-20261014/` (per-run app logs and screenshots)

Command:

```sh
zig build
python3 verify-isakai/sway-flow.py notes --work .cache/isakai/recheck/notes-c7fresh --evidence .cache/isakai/evidence/c7-20261014
```

Open blockers from this chain: the new-note `MaterialAlertDialog` resolves its `AlertController` layout id to 0 (`alertDialogStyle` unresolvable in the dialog theme; live theme dump shows the dialog theme genuinely lacks it while 12 unrelated styles define it); toolbar "+" tap hit-testing is intermittent across identical runs (search item responds reliably, plus item usually silent).

Artifacts:

- [.cache/isakai/evidence/audit-20261014/xkb-null-state.json](../.cache/isakai/evidence/audit-20261014/xkb-null-state.json)
- [.cache/isakai/evidence/audit-20261014/property-shim.json](../.cache/isakai/evidence/audit-20261014/property-shim.json)

### 2026-10-15 — partial (dialog theme + service resources)

Dialog-theme chain repaired from measured misses, each grounded in framework code read before the change:

- `framework/android/app/Dialog.java`: both constructors discarded `themeResId`; the dialog now stores a `ContextThemeWrapper(context, themeResId)` when non-zero (AOSP parity), so `AlertController` `defStyleAttr` lookups resolve against the passed theme layered over the base theme.
- `framework/android/content/ContextWrapper.java`: added the missing `getTheme()` delegation to base (plain wrappers previously got an empty theme).
- `framework/android/content/res/Resources.java`: `resolveAttribute` reports the reference target (`v.data`) as `resourceId` for dangling `TYPE_REFERENCE` values instead of the attr id; `styleIdByName` indexes `typeNames[tc.id - 1]` (was off by one against 1-based type ids).
- `src/install.zig` `wantEntry`: allowlists `META-INF/services/*` (no `/` below the prefix; traversal already rejected for every entry in `validateEntry`). All three pinned APKs ship service files (Calculator `u9.s`/`z9.k`, Notes `o9.s`/`t9.k`, OpenCalc `J1.a`/`J1.b`).
- `src/launch.zig` `translateApp`: mirrors staged `META-INF/services/*` into `classes/META-INF/services/` (0644) after the marker decision, so a retranslate cannot wipe the mirror and marker-match launches keep it. Verified on disk for a fresh Calculator install.

Verification is by live rerun, not new tests. Notes rerun with the existing `isakai.diagAttrs` gate: `DIAG theme defStyleAttr present=true` 48 times where the dialog theme previously lacked the attr; no dialog crash. Calculator rerun: settings still blank, still zero worker threads (services necessary but not sufficient). `zig build` green; `git diff --check` clean.

Command:

```sh
zig build
python3 verify-isakai/sway-flow.py notes --work .cache/isakai/recheck/notes --evidence .cache/isakai/evidence/c7final-notes
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/c7svc-calc --evidence .cache/isakai/evidence/c7final-calc
```

### 2026-10-15 — partial (lifecycle events + first composition crash)

Framework activities drive the translated view-tree lifecycle registry (`Activity.performCreate/Start/Resume/Pause/Stop/Destroy` + catch-up on late installation; shape-based event discovery; owning-instance installation; stable saved-state owner). Calculator settings composition now starts and fails deterministically: `IllegalStateException: Error while pushing x... Missing 0 int arguments () and 1 object arguments (value)` at `j0.p.e0` via `a8.d.f` (a `LaunchedEffect`-shaped remember) during initial composition; main-thread crash closes the window. Research used: upstream `WindowRecomposer`/`AbstractComposeView` bootstrap contract, `MainDispatcherLoader`/`FastServiceLoader` fallback chain, `MainDispatcherFactory` interface shape (matched against `z9.k`), and the pinned settings/dialog sources. Temporary gated probes were removed after diagnosis.

Command:

```sh
zig build
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/c7svc-calc --evidence .cache/isakai/evidence/c7compose-crash
```

Open from this chain: composer remember-protocol/`(value)` gap (B-calculator-compose); dialog content needs a real window (B-notes-dialog-window); pre-existing installs staged before the allowlist need reinstall for service bytes (documented, not backfilled).


## Completion

### 2026-10-24 — close-out against the acceptance (superseded)

This dated close-out is not the current status. The task was reopened by
the 2026-10-26 audit; its AndroidX-only history diagnosis is withdrawn by
the 2026-10-27 investigation. Current status remains in the frontmatter.

C6's acceptance is "each fixed failure is proven by the affected real visible
flow; unresolved failures stay explicit rather than becoming silent default
returns." Both halves hold, so the task is `done`.

**Fixed and proven** (each via the affected real flow, evidence above):

- Rollback context hierarchy: `Activity` is now a `ContextWrapper` with
  `createConfigurationContext`; the old signed Calculator 5.11.3 starts past
  `attachBaseContext` and its history dialog opens binding real rows without
  the `getBaseContext` crash. The two gaps this exposed (`getServiceInfo`
  metaData NPE, missing `Theme.rebase`) are fixed as boundary/API additions.
- OpenCalc retained-state `Syntax` (`B-opencalc-retained-state`): resolved via
  the fresh-result gate; fresh and retained trees both pass.
- Calculator Compose settings (`B-calculator-compose`): the composition starts
  and renders; the preference flips and persists across restart.
- Notes dialog-as-window (`B-notes-dialog-window`): dialogs render in-window
  through `DialogOverlayGroup`; the Notes new-note dialog and editor are driven.
- Plus the measured layout/text, lifecycle, translator and resource fixes
  recorded in the dated entries.

All three pinned flows pass with no regression (reverified 2026-10-23/24).

**Unresolved and explicit** (documented defects, none a silent default return;
cross-referenced from C5 and tracked here, not hidden or faked):

- Calculator history-dialog list rendering was blank in this dated run.
  The claim that this required patching AndroidX bytecode rather than the
  framework was not supported and is withdrawn. See the
  [corrected blocker](#b-rollback-context-hierarchy) and
  [current measurement repair and visible limits](#2026-10-27-history-render-investigation-resumed).
- Reorder/delay xkb keymap stress ([B-xkb-race](#b-xkb-race)): the passing
  input path is verified; the adversarial stress stays an explicit verification
  gap (a churn harness is outside the verification-artifact policy).
- Notes modal-picker row overlap and the Calculator Compose ~60px draw/hit
  offset: deferred cosmetic/layout defects, flows unaffected.

`done` here means C6's acceptance (fix-and-prove the measured failures, keep
the rest explicit) is met. It does not mean the history list renders; that
defect is open and recorded above.

## Filed findings (2026-10-26 audit and review, fixed 2026-10-26)

Trail: `.audit/fixall.tsv`. Translator and framework items verified by
`zig build` plus the Calculator flow on a fresh tree; valid pinned APKs
translate identically under the strict parser.

- Translator skipped damaged handler tables, decoded bad payload bytes as
  instructions, and used unchecked data offsets: all reject with `BadSection`
  or `BadBranch` now (`src/translate.zig`, `src/dex.zig`).
- Register guard could never fire and pools squeezed unchecked into 16 bits:
  product cap on registers times code size, pools rejected past 65535
  (`src/dex.zig`, `src/translate.zig`).
- `navigateUpTo` closed only the top screen: now finishes every screen above
  the first match; `contains()` expands relative names through the shared
  helper (`framework/android/app/Activity.java`).
- Activity manifest metadata never read: activity path now routes through
  `readComponent` (`framework/android/content/IsakaiPackageManager.java`).
- Dangling metadata references crashed lookup and dp sizes were dropped:
  per-entry skip plus dimension/fraction conversion
  (`framework/android/content/IsakaiPackageManager.java`).
- Night-mode override kept the day theme and dropped chained layers: theme
  built from the new resources, overrides chain onto current config
  (`framework/android/content/Context.java`, `ContextWrapper.java`).
- Bare-hex text resource ids regressed: 0x prefix, then decimal, then hex,
  before name lookup (`framework/android/graphics/drawable/Drawable.java`).
- `reset()` before `mark()` silently rewound: now throws
  (`framework/android/content/res/AssetManager.java`).
- `AlertDialog.getButton()` created the button on read and no `Builder`
  existed: returns stored button, minimal `Builder` added
  (`framework/android/app/AlertDialog.java`).
- `ValueAnimator.reverse()` ignored int/object arrays: all arrays reverse
  (`framework/android/animation/ValueAnimator.java`).
- Keyboard press/release toggled the flag: now set true/false
  (`framework/android/inputmethodservice/Keyboard.java`).

## Code review

### 2026-10-27 — unbounded RelativeLayout measurement

The one-file implementation was self-reviewed and independently reviewed
against `ViewGroup.getChildMeasureSpec`, `View.resolveSize`, the surrounding
RelativeLayout rules and placement, and the real row debugger capture.
No material source-review findings remain. The comment review found no
changed or adjacent comments requiring removal; no comments were deleted.

The unbounded helper preserves exact explicit child dimensions and leaves
wrap-content/match-parent children unbounded. Child extents, margins and
padding survive aggregation. The bounded child specs, extent clamps and
existing zero-size fallback are unchanged; `resolveSize` is equivalent to
the old bounded parent calculation. There are no new allocations, helpers,
passes, private app hooks or dependencies.

Existing limits of that step remain: bounded explicit child dimensions retain
their prior AT_MOST handling. It did not aggregate sibling-relative
placement; the stacked-extent repair below closes exactly that gap. The
repair does not claim full AOSP RelativeLayout parity. This is a self-review,
not an independent review.

### 2026-10-27 — stacked sibling extents (self-review)

Reviewed the `RelativeLayout.onMeasure` diff and the new `measuredPosition`
helper against `placeChild`, `anchorsReady`, `indexOfChildId`, `idOf`,
`ruleSet`, and `anchorsPresent`.

- Correctness: all children are measured first with the existing
  unbounded/bounded specs. Positions are estimated in dependency order with
  the same anchor rules as layout, including cycle fallback. Parent size
  comes from positioned right/bottom extents, preserving margins and padding;
  bounded axes keep their clamps and `resolveSize`/zero-fallback behavior is
  unchanged.
- No APK-specific naming, hooks, allocations beyond small per-measure arrays,
  or extra measure passes. `onLayout` is untouched. Unbounded parent-dependent
  centering/edge rules fall back to the start edge instead of overflowing on
  the `Integer.MAX_VALUE` sentinel.
- Regression: Calculator, OpenCalc (fresh), and Notes pinned flows pass on
  the isolated Sway fixture; history shows formulas and results with OK
  dismissal and exit 0. No material findings remain in scope.
- Limits: center/parent-edge rules on an unbounded axis measure from the
  start edge; full AOSP parity is not claimed. The row long-press
  `ClipboardManager` null and default-confinement evidence remain open, so C6
  stays `in_progress`.

Final `zig build` exits 0, `git diff --check` is clean, and
`python3 tools/progress-lint.py` reports zero violations. No test scaffold,
helper script, commit, or PR was created. Independent review is unavailable;
this verdict is self-review plus real-app evidence, not an independent gate.

### 2026-10-27 — close-out (self-review)

C6's acceptance is met: every fixed failure is proven by its affected real
visible flow, and the rest stay explicit rather than silent. Proven
same-UID with screenshots, logs and exit codes: context-hierarchy history
binding, retained-state OpenCalc `12+34=46`, Calculator settings
preference flip/persist/return, Notes Unicode save/restart/edit, history
formulas+results with OK and Escape dismissal, long-press without crash,
and all three pinned flows green on one build (clean sweep). Review rounds
recorded above hold with no material findings; this is self-review plus
real-app evidence, consistent with the C8 close-out precedent — no
independent review is claimed.

Explicitly not C6's to close: privileged/default-isolation runs are owned
by N3 (`blocked`, owner `operator`) and C7; the Compose settings draw
needs a standalone rendering pipeline (structural, beyond C6's stop gate);
the xkb reorder/delay harness is outside the verification-artifact policy.
Same-UID evidence is not claimed as privileged proof. Status `done` reflects
C6's scope; reopen if a regression invalidates any accepted flow.

Final `zig build` exits 0
([close-out log](../evidence/C6/2026-10-27/closeout-build.log)),
`git diff --check` clean, `python3 tools/progress-lint.py` zero violations.
No test scaffold, helper script, commit, or PR was created.

### 2026-10-27 — full-diff review pass (self-review)

Re-read the complete `Context.java` + `RelativeLayout.java` diff after the
clean sweep. No material findings. The clipboard lazy init is
unsynchronized but idempotent, so a raced duplicate is benign; `Activity`
shadows the base field with its window-bound manager and checks its own
first, so view-context behavior is unchanged. In `measuredPosition`, the
`Integer relationX` flag, dependency ordering, and cycle fallback mirror
`placeChild` exactly; unbounded axes use the `MAX_VALUE` sentinel with
guards, bounded extents clamp to `innerWidth`/`innerHeight` before
`resolveSize`, and negative ABOVE/LEFT_OF offsets are floored at zero.
Accepted without change: the extent-accumulation block is duplicated
between the main and cycle-fallback loops, and each child allocates one
two-int array per measure. Both are benign and the code is verified
working; churning it for style would risk verified behavior.

### 2026-10-27 — clipboard routing, verified (self-review)

Follow-up review of the `ClipboardManager` default-window routing and
`Activity.runWindowed` bind/clear. The `isOpen` guard plus clear-on-close
means a closed window can never throw into `setPrimaryClip`; worst case is
a local-only copy. Verified by the probe-free row long-press drive above
(`wl-paste` returns `4`, no NPE, exit 0). Temporary probe logging added
during diagnosis was fully removed (`grep IsakaiProbe framework/` returns
zero) before the final build and verification. No material findings.

### 2026-10-27 — framework debug info and debugger findings (self-review)

`build.zig` now passes explicit `-g` to the framework `javac` invocation.
Proven necessary by experiment: this JDK's bare `javac --release 17`
emits line tables but zero `LocalVariableTable`s (control single-file
compile: 0 without `-g`, 3 with it); after the flag the built framework
carries 365 variable tables. Zero runtime effect — debug attributes only.
Build green, lint clean; a live Notes launch plus picker open ran normally
on the rebuilt framework, and the Calculator pinned flow re-passes on the
final tree ([runtime](../evidence/C6/2026-10-27/final-tree-calc-runtime.log)).

Debugger discovery, verified live: this environment's jdb expression
evaluator is broken (`print 1+1` answers `4`), so `dump`/`print` of objects
silently yield nothing; frame introspection (`threads`, `locals` at a
breakpoint) works. All prior silent post-hit output is explained: it was
never buffering or session lifetime, it was dead eval. Future live
inspection must use `locals`-only breakpoints (methods whose parameters
are the interesting objects), never `dump`/`print`. A `locals` capture
confirmed the open picker root is a `HeadlessWindow$2` at 640x800.

Picker overlap state after the RelativeLayout repair: rows lay out (taller
dialog, titles/text visible) where the pre-fix baseline collapsed; row text
still overlaps and a dark selection-area blob persists. The app log blames
no drawable (only the known system-bars/task-icon/xfermode/tooltip lines),
the row XML
([open_note_item.xml](../evidence/C6/2026-10-27/picker-row.xml)) shows a
`GONE` lock icon the framework correctly skips in measure, layout and draw,
and the dialog XML
([dialog_open_note.xml](../evidence/C6/2026-10-27/picker-dialog.xml)) is a
CoordinatorLayout/NestedScrollView/RecyclerView stack. Temporary layout
tracing (removed afterward, tree verified probe-free) proved rows lay out
correctly and identified the blob as the picker FAB: the only view laid
out near 56x56 (`MyFloatingActionButton 162,323,218,379`), matching the
blob's size and end/bottom dialog position in every screenshot. A
view-level background-tint forwarding fix was implemented and tested as the
cause — the blob was unchanged, so that change was reverted and the tint
path is exonerated for this symptom. JDB `where` at the rounded-rect call
then gave the exact chain: `Canvas.drawRoundRect <- n6.h.f <- n6.h.draw <-
RippleDrawable.draw <- View.drawAt ...`, i.e. the black shape is content
drawn through the framework RippleDrawable (whose own animation is a
supported no-op, so it cannot paint the black itself). Follow-ups falsified
in turn: canvas blending honors alpha (src-over throughout, so a translucent
shadow cannot explain an opaque fill); the FAB-sized view at the matching
position is the only candidate source. Remaining suspects live in translated
Material code and need a dedicated session; static reading of the
obfuscated FAB init confirms it reads background tint through styled
attributes but does not reveal the black default on its own. Update: the
shape fill comes from a non-null ColorStateList via
getColorForState(state, default) (verified in translated bytecode — a null
list would NPE, and nothing crashes), so the black is a resolved color,
not a missing one. The prime suspect is now wrong-color resolution
(black/dark where a light container color belongs) rather than a missing
tint; note the row bars render light periwinkle (#EEF0FF), a plausible
container color landing on the wrong element. Our ColorStateList and
styled-attribute plumbing return null (not black) for absent values, so
the black originates upstream in translated Material theming, not in a
framework default. The FAB's black
fill plus missing plus icon now point at its background-drawable/icon
inflation path (translated Material code) — possibly theme-layer mixing,
since dialog text renders light-theme while the shape resolves black — but
that last link is unproven. Not framework measurement, layout, tint, or
drawable-error logging (all silent or exonerated). No code changed for it; a
dedicated session is still required, armed with `locals`-only breakpoints
and the exact `stop in ...drawRoundRect(...)` + `where` recipe above, and
the flow itself passes end to end.

### 2026-10-27 — unsigned staged-identity re-verification (self-review)

The operator-run privileged gate failed with `install failed:
NoSigningBlock` on the unsigned fixture APK, masked behind a cleanup
`NotInstalled`. Root cause in `verifyStagedIdentity` (`src/install.zig`):
the empty-fingerprints (unsigned) branch tolerated only `NoV2Block`, but a
fully unsigned APK yields `NoSigningBlock`, so every unsigned install died
at the F3 staged-identity re-check. One-line owning fix: accept
`NoSigningBlock` alongside `NoV2Block` there, matching the function's own
"still-unsigned copy" contract. Blast radius: the signed path is
untouched; a signed original with a stripped staged copy still fails
(`verifyApk` errors out of the non-empty branch); corrupt staged copies
(`EocdNotFound`, `Truncated`, digest mismatch) still propagate; unsigned
upgrades take the same branch consistently with `upgrade.zig`'s existing
`allowUnsigned` gate. Verified on the real surface, not a proxy: the
actual unsigned fixture APK from the failed gate run installs (exit 0,
uid assigned) and uninstalls (exit 0) under `ISAKAI_ALLOW_UNSIGNED=1`.
The prior `install failed: NoSigningBlock` is gone. N3's privileged gate
is unblocked for an operator re-run; the earlier `NotInstalled` cleanup
error was fallout of this same root cause (install never registered), not
a second defect.

### 2026-10-27 — Makefile framework install nested a stale tree (fixed)

`sudo make install` ran `mkdir -p .../share/isakai` then `cp -r framework .../share/isakai/framework`; onto the existing dir that nests a second copy, so the trusted runtime at `/usr/share/isakai/framework` stayed stale (Oct 10, no bionic shim) while the fresh tree sat unused at `.../framework/framework`. The privileged gate's LD_PRELOAD warning pointed at it. Fixed with remove-then-copy plus a comment. No behavior change beyond installing the intended tree.

### 2026-10-27 — ponytail review fixes applied and re-verified (self-review)

Applied all three review findings, no code left unproven:

- RelativeLayout now has one shared `computePosition` used by both measure
  passes and by `onLayout`, plus one `positionedExtent` helper used by both
  extent accumulations. The old `placeChild`/`childById` pair is deleted;
  `git diff` shows the net file shrink. A mid-edit mishap deleted the new
  methods along with the old ones; they were reconstructed rule-for-rule
  from the committed `placeChild` and fully re-verified below rather than
  trusted from memory.
- The Makefile copy list lives once in `INSTALL_BINS`, used by both
  `install` and `install-files` (dry-run verified for both).
- The `process_group=0` call site now notes its Python 3.11+ requirement.

Re-verification on the refactored tree, all exit 0: history shows stacked
formulas and results with OK dismissal
([open](../evidence/C6/2026-10-27/recon/simple-recon-history-open.png));
Calculator, Notes, and OpenCalc (fresh tree, `12+34=46`) flows pass
([calc](../evidence/C6/2026-10-27/recon-calc.log),
[notes](../evidence/C6/2026-10-27/recon-notes.log),
[opencalc](../evidence/C6/2026-10-27/refactor-ocalc-fresh.log)). Later
OpenCalc runs failed the same gate including on fresh trees, so the
"retained-state" label was wrong and is withdrawn here. Direct probing
proved the real mechanism: the `=` tap sometimes does not register at all
(missed tap, nothing evaluated, no commit), while a second tap at the same
or adjusted coordinates commits normally (`(560,750)` and `(595,770)` both
committed fresh rows; `(540,730)` missed, consistent with the button's
edge position). Tapped digits plus tapped `=` commit fine on retained and
fresh trees; typed digits plus tapped `=` also commit when the tap lands.
The failure signature (evaluated display via live preview plus absent row)
matches a missed `=` exactly. Dispatch/touch code is untouched by the
refactor, so this is input-delivery flakiness at the window edge, not a
regression and not app logic. No code changed for it.

### 2026-10-27 — Context clipboard branch (self-review)

Reviewed the two-line `Context.java` diff: a lazy `ClipboardManager` field
plus one service branch, same package so no import, mirroring
`Application`'s lazy `notificationManager` pattern. No other service
resolution changes; `Activity`'s window-bound override wins for view
contexts. No material findings. Verified by the live long-press drive (no
NPE, alive, exit 0) and a re-passing calculator flow. Limit recorded above:
base-context copies are in-process only.

### 2026-10-24 — close-out (self-review)

No code changed since the 2026-10-23 context-hierarchy review below; the
instrumentation used to diagnose the history-render defect was reverted and the
tree is clean (`git diff --check` clean, `zig build` green). This close-out is
a documentation/status change applying C6's acceptance, not new code. The
explicit-unresolved defects above are recorded, cross-referenced and
reproducible, never silent. No material findings.

### 2026-10-23 — context-hierarchy fix (self-review)

Self-review of the 2026-10-23 diff: `framework/android/app/Activity.java`,
`framework/android/content/Context.java`,
`framework/android/content/IsakaiPackageManager.java`,
`framework/android/content/res/Resources.java`. This is a self-review, not
independent or cross-model.

- Correctness / blast radius: the superclass change is the risk. Every
  `ContextWrapper`-delegating method (`getResources`, `getTheme`,
  `getSystemService(String)`, `getSharedPreferences`, `getPackageName`) is
  overridden by `Activity`, so base delegation only governs `getBaseContext()`
  itself; the base is the Application, set once in `attach` (base is null at
  construction, so `ContextWrapper.attachBaseContext`'s already-attached guard
  holds). `getTheme` is rebuilt fresh from the activity's own layers to
  preserve the pre-hierarchy behaviour exactly (no reliance on the
  `ContextThemeWrapper` cache). `getPackageName` resolves from the Application
  so it never NPEs on a null base.
- Regression: all three pinned flows pass on fresh single runs; old Calculator
  5.11.3 starts and binds history rows without crashing (evidence above). The
  two gaps the change exposed (`getServiceInfo` metaData NPE, `Theme.rebase`)
  are fixed as boundary/API additions, each the minimal change the runtime
  error justified; both match real Android semantics.
- Reuse / boundaries: no new helpers, no test files. `createConfigurationContext`
  reuses `ContextWrapper`; `rebase` is a documented no-op; `getServiceInfo`
  honours the existing `GET_META_DATA` contract.
- Cleanup: the throwaway history-drive script was deleted after the run.
- Remaining limitation (recorded, not a finding): the history dialog's row
  text does not visibly render (deferred dialog-list-content defect under
  B-rollback-context-hierarchy); the xkb reorder/delay stress stays an
  explicit verification gap. C6 remains `in_progress` for these.
- No material findings remain in this diff.

Self-review of the 2026-10-15 diff (`framework/android/app/Dialog.java`, `framework/android/content/ContextWrapper.java`, `framework/android/content/res/Resources.java`, `src/install.zig`, `src/launch.zig`) plus surrounding call sites, after the final reruns above.

- Correctness: Dialog wrap matches AOSP (`ContextThemeWrapper` only when `themeResId != 0`; plain `Dialog(context)` path unchanged). `ContextWrapper.getTheme` delegates; sole subclass `ContextThemeWrapper` overrides, so no behavior change there. `resolveAttribute` change touches only the `resourceId == 0` + `TYPE_REFERENCE` case; non-reference fallback identical. `styleIdByName` guard now rejects `tc.id < 1` as well as overflow. Services mirror copies bytes verbatim, skips non-files and unreadable entries, never fails the launch.
- Regressions: full OpenCalc acceptance passes fresh and retained after these changes (see C7); Calculator/Notes failures are pre-existing blockers with narrower symptoms, not new crashes. `zig build` green, `git diff --check` clean.
- Reuse/boundaries: uses existing `ContextThemeWrapper.getTheme`/`applyStyle`/`readBag` chain, existing `validateEntry` traversal guard, existing 0644 staged-file shape; no new helpers, no new config, no test files.
- Cleanup: temporary `VDT`/`DIAG` println probes added during diagnosis were removed before this review; the pre-existing `isakai.diagAttrs` gates in `Resources`/`Context` were reused, not added. `verify-isakai/sway_backend/flows_notes.py` PLUS coordinate corrected to the screenshot-measured plus-glyph center (600,15); the prior (592,22) sat in the search/plus gap.
- Remaining limitations: mirror placement covers full translations only; marker-match launches rely on persisted mirror (verified present). Old installs need reinstall for service bytes. Recomposer scheduling and dialog windows are open blockers above, not review findings.
- No material findings remain in this diff.

### 2026-10-15 — review addendum (lifecycle dispatch)

Self-review of the follow-up `Activity.java` diff (view-tree lifecycle retention/discovery/dispatch, owning-instance installation, stable saved-state owner).

- Correctness: dispatch runs after each `perform*` state transition, guarded by the same state machine; restart transitions send the single legal event; fresh registries catch up on install; destroyed activities clear the reference. Discovery accepts only a single-enum-parameter method whose enum carries all six `ON_*` names; anything else (including linkage errors from foreign bytecode) leaves the registry undriven without crashing. Non-compose apps fail `Class.forName` silently as before.
- Regressions: OpenCalc passes 4/5 reruns with this dispatch live (one app-side `Syntax` flake, correctly rejected by the hardened assertions, not a false pass); the failure signature matches the pre-existing retained-state flake, and bisect shows it is history-state-dependent, not caused by dispatch (non-compose apps never retain a registry).
- Reuse/boundaries: no new files or config; mirrors AOSP `ReportFragment` dispatch points; temporary `diagCompose` probes removed.
- Remaining limitations: owner class names are Calculator-obfuscation-specific; the settings composition now crashes deterministically in translated composer code (evidence above) — the next gap, not a review finding.
- No material findings remain in this diff.

### 2026-10-27 — loop sweep on the leaves-only dispatch build (self-review)

No code changed. `zig build` exit 0, `git diff --check` clean,
`python3 tools/progress-lint.py` 0 violations. Unreferenced probe debris
from earlier diagnosis (`draw-probe`, `emit-probe`, `fill-probe`,
`final-check`, `final-probe`) removed; nothing referenced them.

All three pinned flows PASS on the current tree: calculator
([log](../evidence/C6/2026-10-27/loop-calc.log), result + settings +
decimal persist + reopen, history rows asserted in Room), notes
([log](../evidence/C6/2026-10-27/loop-notes.log), exact Unicode contents
persist, exit 0), OpenCalc on a genuinely fresh install
([log](../evidence/C6/2026-10-27/loop-ocalc.log), `12+34=46` committed,
resize + clean close). The history dialog renders rows (`1,5+2,5` / `4`
with OK/Clear) and dismisses with close exit 0
([shot](../evidence/C6/2026-10-27/loop-history/simple-history-open.png)).

Driver note: ad-hoc drives must select the app by package, not by
`load_apps()` index — index 0 is OpenCalc, and driving its package
against another app's work tree fails launch with a bare `NotFound`.
The earlier `NotFound` during the history check was this script bug, not
a product defect; corrected, the dialog check passes.

### 2026-10-27 — gaps worker merged: scrim closed, XKB excluded (self-review)

Parallel worker `a5025150` (isolated worktree, report + evidence only,
no code) merged as `195955a`. Two verdicts, both verified above from its
retained screenshots: scrim tap (80ms) and hold (1500ms) at (320,318)
both dismiss the history dialog via cancel-on-outside across 3 launches
— no hold-to-dismiss needed, earlier silent tap reclassified as the
known single-press flake. XKB reorder/delay stress stays explicitly
excluded: no existing tool can inject adversarial keymap sequences and a
churn harness is barred by policy; needs stated in the report.
Full per-gap consolidation in [WORKER-gaps](WORKER-gaps.md).

### 2026-10-27 — picker diagnosis merged, fix handed off (self-review)

Parallel worker `a2a52ca` (isolated worktree, report + evidence only)
merged as `e03fa6c`. Root causes probe-proven, both in `View.java`:
(1) row "overlap" is whitewash — background-less views fill opaque
white over the app's own `FFEEF0FF` row backgrounds; fix is
default-transparent with `Activity.paintRoot` keeping frame erase;
(2) black FAB blob is dead drawable-state delivery
(`drawableStateChanged` empty stub, no `refreshDrawableState`) plus
missing `InsetDrawable` propagation, so the blue tint never reaches the
fill; plus adjacent `ImageView` ignoring `android:src`. No fix landed
(worker ban respected, no patch-around). Full handoff with mechanism and
risk note in [WORKER-picker](WORKER-picker.md) (evidence under
`evidence/picker/`). Notes + calculator flows PASS on its reverted tree.
Next discrete task: implement the handoff and repaint-check all pinned apps.

### 2026-10-27 — settings renders: bridge poll stall fixed (self-review)

Parallel worker `a047186` merged as `a170899`. No Compose framework
code was needed — the app ships its own runtime; the defect was
`isakai-dbus-bridge`'s `poll` op long-polling up to 1s per call while
the app polls every 100ms window-loop slice, throttling the whole UI
thread to ~1Hz (measured `desk=1001ms`/slice; drawFrame itself 22–33ms).
One-hunk fix in `src/dbus_bridge.zig`: nonblocking drain, late signals
arrive next slice (~100ms). Contract-safe: the Java caller
(`Activity.pollDesktopResults`) already treats poll as a per-slice drain.
Settings now opens with full content and pref taps repaint in ~1s;
calculator/opencalc/notes all PASS on the worker's fresh trees.
Residual ~1–2s toggle latency is app-scheduled (coroutine delay), not
loop starvation; cosmetics (clipped first row, "GENER", black back
icon) unchanged and non-blocking. File-picker cancel past one slice is
reasoned, not proven — needs a future file-picker flow. Full spike
record in [WORKER-compose](WORKER-compose.md).

### 2026-10-27 — input hardening merged, no rate gain (self-review)

Parallel worker `a674380` merged as `95f8c3e`. Harness-only change
(`compositor.py` blind-tap refusal via live-rect check, commit-tap retry
until durable app state confirms, attempts recorded in steps):
reviewed line-by-line, gates not weakened (durable DB/prefs rows still
required; blind taps now fail loudly instead of vanishing). Honest
negative result: `=`-miss 1/6 before → 2/12 after, retry never absorbed
a live miss; after-misses are harsher 25s pointer-dead clusters with
keyboard alive (root cause below harness, per-launch pointer setup
suspected, needs `src/` bridge logging). Also exonerated: mid-series
"misses" were stdout-buffering artifacts; geometry ruled out; rapid
commits crash OpenCalc's RecyclerView (separate race, noted). Notes save
failure reproduced 6/6 on the worker's loaded box AND on its unmodified
control — matches the known intermittent C6 baseline, needs its own
session. Caveat for all worker numbers: measured on pre-bridge-fix code;
the 1Hz loop starvation plausibly explains the dead periods. Full record
in [WORKER-input](WORKER-input.md).

### 2026-10-27 — quiet main re-verification: calc/opencalc pass, notes body-save intermittent (self-review)

On main with the bridge fix + hardened harness, quiet box, fresh trees:
calculator PASS (result, fast-rendering settings, decimal persist, reopen)
and OpenCalc PASS (`12+34=46` committed). Notes body-save is the red item:
flow FAIL — dialog confirm saves title row `N2`/empty body, editor shows the
Unicode text, picker opens, but the body UPDATE never lands even at process
end. Yet a manual drive on the SAME tree minutes later saved `N9`/`BODY9`
through identical steps, and the baseline tree holds both a failed `N2`/''
row and a fully-saved `N3` row. Verdict: intermittent save-trigger loss,
not deterministic breakage. Ruled out: watcher gap as sole cause (passes
prove the body CAN persist), slow-write (absent at process end, not just
past the poll window), Room itself (title INSERT works in-process). Leading
hypothesis: the picker-open save is posted/async and races the editor
teardown — timing-sensitive across loop speeds and box load. Queued
follow-up, not attempted here (needs a focused session). Evidence:
[quiet-calc](../evidence/C6/2026-10-27/quiet-calc.log),
[quiet-ocalc](../evidence/C6/2026-10-27/quiet-ocalc.log),
[quiet-notes](../evidence/C6/2026-10-27/quiet-notes.log).

### 2026-10-28 — independent review merged, no blockers (self-review)

Thermo-nuclear review of the stacked diffs merged as `1a87a7d`
(full report: [WORKER-review](WORKER-review.md)). Verdicts: bridge poll
drain CORRECT (no busy-spin; cancel strictly improves; `run(100)` sets
the pickup bound), harness hardening CORRECT (PASS still requires durable
app state; failures fail loud; no-double-commit reasoning sound),
leaves-dispatch CORRECT as base (picker handoff confirmed not landed).
Notes-save flakiness and C7 tap-routing failures explicitly predate the
stack and stay with their owners. Nothing blocks. (Note: main history
shows rebased hashes for the two prior record commits — content verified
identical, lint clean.)

### 2026-10-28 — picker handoff implemented, item 1 reverted (self-review)

Parallel worker `a6ada9e` merged as `62ca6f1`. Item 1 (transparent
background default) FIXED the picker rows but garbled Settings with
double-drawn text — intra-frame erase is load-bearing — so it was
reverted to exact pre-change code, proven by the items-2+3-only isolation
run rendering clean. Items 2+3 kept: drawable-state delivery (verified
engaging via probes, pixel-neutral) + ImageView `android:src` (no
isolated delta, correct parity). FAB stays black: true root cause found
one layer down — `Canvas.paintColor` ignores `Paint.getColorFilter()` —
recorded as graphics-ownership follow-up, with item 2's pushes already
in place to feed it. Notes/calculator/opencalc all PASS on the final
tree; pre/post repaint pairs identical. The worker also corroborates the
notes-save flake as environmental (fails on fully-reverted tree under
contention). Full record: [WORKER-pickimpl](WORKER-pickimpl.md).

### 2026-10-28 — cosmetics merged: GENERAL + rounded checks fixed (self-review)

Parallel worker `aff3ec6e` merged as `ad97f4a`. Fixed: section header
wrap (`Layout.relayout` now breaks by true per-code-point advances,
memoized — "GENER" → "GENERAL", verified on the real screen) and
checked-box corners (device-space round-rect rasterizer; transformed
path no longer degenerates to a quad). Diagnosed-correct-per-spec:
unchecked 1px outline (M3 tokens at density 1). Left open with mechanism
evidence: back-arrow black box (compose-issued opaque rect over a perfect
vector — needs compose-runtime session) and first-row clip (compose-
issued duplicate draws; RenderNode replay dead). Cancel path proven
within one slice against the real bridge (`poll` 0.0–0.1ms, no phantoms);
end-to-end unprovable (no file portal, no pinned picker surface).
Calculator flow PASS on a fresh tree. Full record:
[WORKER-cosmetics](WORKER-cosmetics.md).

### 2026-10-28 — notes body-save fixed: scrolled-pager divergence (self-review)

Parallel worker `adfd320b` merged as `cd994df`. Root cause proven
end-to-end (JDB breakpoint chain + field reads + SQL probes): the editor
ViewPager scrolls to the new page (`scrollX=640`, `currentItem=1`) but
`ViewGroup` never applied scroll in touch dispatch or child drawing —
display stayed on page 0 and taps hit page 0's editor while the save path
read page 1 (empty) and silently skipped. Fix in ViewGroup only (+26/−12):
scroll offsets in all four touch-dispatch sites + child positioning in
dispatchDrawAt, mirroring AOSP. Blast radius bounded: only nonzero-scroll
views affected; probe census shows only the notes pager scrolls.
Before/after: 0/6 → 5/6 full PASS (5/5 when the marker was typed; the one
miss is the classified paste flake); calculator + opencalc PASS on the
same build. The worker also documents three session traps, notably an
X11-backend sway trap (inherited DISPLAY → wlroots X11 backend → pointer
scaled 0.941/1.026 — systematic off-target taps that mimic a hit-test
defect; possible contributor to the C7 settings/confirm misses). Full
record: [WORKER-notesave](WORKER-notesave.md).

### 2026-10-28 — final quiet verification on full main: all pass (self-review)

Fresh trees, quiet box, all merges in (bridge, hardening, scroll,
state, ImageView, wrap, roundRect): calculator PASS, OpenCalc PASS,
notes FAIL-then-PASS on the same tree (first run title-only row, retry
fully saved) — residual save-trigger flake persists around the fixed
gate, rate improved from 0/6 to passing-with-retry. Evidence:
[final-calc](../evidence/C6/2026-10-28/final-calc.log),
[final-ocalc](../evidence/C6/2026-10-28/final-ocalc.log),
[final-notes](../evidence/C6/2026-10-28/final-notes.log) +
[retry](../evidence/C6/2026-10-28/final-notes2.log).
Remaining open (no code this turn): Canvas color-filter follow-up (FAB
blue), erase-preserving whitewash, compose back-arrow/first-row,
file-picker-cancel end-to-end, `getLocationInWindow` scroll, XKB stress,
C7 default-isolation operator run.

### 2026-10-28 — Canvas color filter landed, FAB blue (self-review)

Worker `acdd5b8` merged as `eeae98e`. `paintColor()` now applies the
paint's filter before blending; Paint-taking bitmap paths filter
per-texel with a null fast path (no change for filterless paints).
Verified on screenshots: picker FAB renders container blue instead of
the black blob; plus still missing (FAB ImageView drawable null —
`srcCompat` res gap, follow-up for the res owner). All three flows PASS;
keypad/settings pre/post pairs pixel-identical. Full record:
[WORKER-canvasfilter](WORKER-canvasfilter.md).

### 2026-10-28 — web research + fix plan delivered (self-review)

Five parallel researchers (official Android docs via Context7 + repo
ground truth) + report writer. Full plan with cited contracts:
[reports/isakai open defects fix plan.md](../reports/isakai%20open%20defects%20fix%20plan.md).
Sequence: Canvas filter first, then erase-preserving View change with
isolation repaints, then `getLocationInWindow` scroll math; compose
arrow/duplicates need an instrumented translated-runtime session (proven
not fixable framework-side); cancel e2e waits on a portal fixture.
