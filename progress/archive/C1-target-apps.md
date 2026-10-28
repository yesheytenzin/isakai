---
id: "C1"
status: "done"
phase: "COMPATIBILITY"
priority: "P1"
size: "S"
updated: "2026-10-23"
owner: "unassigned"
next: "none — done; reopen only if a regression invalidates acceptance"
---

# C1: Pin three target apps and useful flows

## Dependencies

None.

## Code and documentation

- [progress/APPS.md](../progress/APPS.md)
- [tools](../tools)

## Acceptance

All three app records have immutable APK identity and repeatable visible interaction plus persistence assertions. At least one includes internal screen navigation and one includes Unicode editing.

## Verification evidence

### 2026-10-11 — partial

All three pinned apps launch windowed exit 0 with real content paths: OpenCalc keypad+display render, input reaches evaluator (log-visible); Calculator computes 2+2=4 via tap injection; Notes Main renders editable field with typed text (headless). No flow fully passes live: OpenCalc equals/copy blocked app-side (Gson store/Syntax error), Notes/Calculator live input blocked on compositor focus race.

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.calc2.png](../.cache/isakai/evidence/com.darkempire78.opencalculator.calc2.png)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.preshot.png](../.cache/isakai/evidence/com.simplemobiletools.calculator.preshot.png)
- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.typed.png](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.typed.png)

### 2026-10-12 — partial

Desktop-bridge endpoint moved out of the work tree: <work>/run/<pkg>/bridge.sock exceeded sun_path for deep work dirs (111/134 bytes in the OpenCalc runs), so the daemon failed to bind and desktop integration silently disabled itself. The endpoint is now /tmp/isakai-<uid>-<digest16> (mode 0711, launcher-owned, verified not group/other writable) shared by launcher, privileged helper, Landlock grant and the in-sandbox JNI client (src/bridge/path.zig). Launcher logs bridge readiness and the daemon logs accepted client uids. Verified: deep-work-dir OpenCalc launch now shows "desktop bridge ready" plus "bridge: client uid 1000 connected"; runtime probe connects under Landlock. A real flow harness (verify-isakai/flow-drive.py) now drives pinned apps with per-action window-focus verification.

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.window.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.window.log)
- `/tmp/isakai-checks-final/runtime.log`

Command:

```sh
python3 verify-isakai/check-runtime.py; python3 verify-isakai/observe-app.py 0 --work .cache/isakai/recheck/opencalc10
```

### 2026-10-12 — partial

Flow harness hardened from live runs: compositor focus is re-verified before every keystroke and press, pointer placement is self-calibrating (the uinput absolute axis is ~2x the screen and the model drifts, so each observation updates the estimate), presses verify the cursor did not move during the hold, the keypad boundary is detected from the captured frame so blind presses stay in the display area, the expected result is read from the app's own stored history, and foreign input (the user's keyboard) aborts the run instead of corrupting evidence. Workspaces are made visible via hyprctl dispatch (the eval form silently no-ops).

Artifacts:

- [verify-isakai/flow-drive.py](../verify-isakai/flow-drive.py)

Command:

```sh
python3 verify-isakai/flow-drive.py opencalc --work .cache/isakai/recheck/opencalc10
```

### 2026-10-14 — partial

Harness fixes from live runs: copy-sweep guard now accepts the recorded result as legitimate input state (the app logs its own result as an equation line); isolated-env SWAYSOCK resolves the live sway IPC socket instead of a stale PID-suffixed path. Calculation is verified end to end repeatedly (typed 12+34, app history records 12+34->46, visible result screenshots, exit paths clean).

Artifacts:

- [.cache/isakai/evidence/opencalc-01-initial.png](../.cache/isakai/evidence/opencalc-01-initial.png)
- [.cache/isakai/evidence/opencalc-02-result.png](../.cache/isakai/evidence/opencalc-02-result.png)
- [.cache/isakai/evidence/com.darkempire78.opencalculator.sway-opencalc.log](../.cache/isakai/evidence/com.darkempire78.opencalculator.sway-opencalc.log)

Command:

```sh
python3 verify-isakai/sway-flow.py opencalc --work .cache/isakai/recheck/opencalc10
```


### 2026-10-23 — close-out

Acceptance met and reverified on the current build. All three apps are pinned
with immutable sha256 identity in [APPS.md](REFERENCE.md) (OpenCalc, Simple
Calculator, Simple Notes). The three isolated-sway flows pass fresh single runs
today, each asserting repeatable visible interaction plus persistence:

```sh
python3 verify-isakai/sway-flow.py opencalc   --work .cache/isakai/recheck/c7svc-ocalc --evidence .cache/isakai/evidence/c6-ocalc-clean-20261023
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/c7svc-calc  --evidence .cache/isakai/evidence/c6-regress3-calculator-20261023
python3 verify-isakai/sway-flow.py notes      --work .cache/isakai/recheck/notes       --evidence .cache/isakai/evidence/c6-notes-clean-20261023
```

- Persistence: Calculator history row + preference survive restart; OpenCalc
  commits a new history row; Notes restores its exact Unicode note after
  restart and append.
- Internal screen navigation: Calculator opens SettingsActivity and returns via
  back (the one-navigation requirement; see [C4](archive/C4-activity-navigation.md)).
- Unicode editing: Notes creates and persists `Isakai 日本語 – note`.

The fuller default-confinement proof is recorded in
[C7](C7-acceptance-gate.md#2026-10-20--three-pinned-apps-under-default-per-app-confinement).

## Code review

### 2026-10-23 — close-out (self-review)

C1 owns the pinned-app records ([APPS.md](REFERENCE.md)), not production code. The
three records carry immutable sha256 identity matching the installed APKs, and
each flow asserts against app-owned persisted state (Room rows, prefs, the note
DB) rather than screenshots alone. The one-navigation and one-Unicode-editing
requirements are satisfied by the Calculator and Notes flows respectively. No
production code is owned here; the flow harness is reviewed under C7/N1. Self-
review, no material findings. Acceptance met; status set to `done`.
