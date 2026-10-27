# Worker C7 report — 2026-10-27 (agent-a20b40d2c32ad08e3)

Status: privileged fixture gate **PASS** with fresh evidence; pinned Calculator and Notes flows **FAIL** on fresh data (2/2 each, systematic). C7 stays open; this report is the new evidence + operator runbook. No shared `progress/*.md` or `.audit/*` files touched; no `framework/`, `src/`, or `verify-isakai/` code changed.

## Environment

- Worktree: `agent-a20b40d2c32ad08e3` (own branch). `zig build` exit 0 before and after all runs.
- Setup per instructions: `.cache/isakai/{apks,compositor}` copied from pinned main-repo cache; unique env `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-c7 WAYLAND_DISPLAY=wayland-1 XDG_RUNTIME_DIR=/tmp/isakai-wayland-c7 ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-c7 ISAKAI_STAGE_DIR=/tmp/isakai-1000-c7-stage` (live-gate run kept the live `WAYLAND_DISPLAY`/`XDG_RUNTIME_DIR` since `window-flow.py` drives the live Hyprland session via `hyprctl`/`grim`/`wtype`; overriding them breaks the gate — documented deviation, everything else used the unique paths).
- Privilege: `sudo -n true` → `a password is required` (exit 1, sudo path blocked). `pkexec whoami` → `root` exit 0 (polkit authorizes non-interactively, same as the 2026-10-19/23 record).

## 1. Required privileged gate — PASS (fresh, 2026-10-27)

- Attempt 1 (`evidence/C7/2026-10-27/gate-attempt1-uidtaken.stdout.txt`): `tools/e2e-privileged.sh --required` failed at the first fixture install with `HelperFailed` → cleanup `NotInstalled`. Root cause: this worktree has no `.cache/isakai/priv/registry.json`, so `reserve_global_uids` reserved nothing and the fresh registry tried uid 200000, which is globally held by the main-repo pinned OpenCalc install (`isakai-f05cf53…`, verified `sha256(package) == account hash`). A diagnostic `pkexec …/isakai-allocate-uid` of the peer package at 200004 succeeded (uid was free), proving elevation works and the failure is uid-collision only. The diagnostic account was removed afterwards (`pkexec …/isakai-remove-uid`, `getent` confirms 200004 free).
- Fix within scope (no code change): copied the real main-repo `.cache/isakai/priv/registry.json` (opencalc 200000, calculator 200001, notes 200002 — all verified live in `getent`) to this worktree's `.cache/isakai/priv/registry.json` (git-ignored scratch; the gate only reads uids from it to seed throwaway reservations). Second run printed `RESERVE: uids held globally: [200000, 200001, 200002]`.
- Attempt 2 (`evidence/C7/2026-10-27/gate-pass.stdout.txt`, artifacts `evidence/C7/2026-10-27/gate/`): `tools/e2e-privileged.sh --required` → **`PASS: privileged install/isolation/window/lifetime/close/back/uninstall`, exit 0.** Fixtures installed at uids 200003/200004 (both free), each iteration proved real uid, empty supplementary groups, `NoNewPrivs: 1`, `Seccomp: 2`, mount namespace distinct from harness, `bridge-connected-at-open: True`, settled-geometry `opened-*.png`, 31 s + 1 s liveness, compositor close + back close both exit 0, both packages uninstalled, socket ACL restored. `isolation.log` retains the honest `text-input: not asserted` note. `getent` after: only the three pinned accounts remain. `/tmp/isakai-e2e-*` removed.

## 2. Pinned flows, same-UID, fresh data (private headless sway in /tmp/isakai-wayland-c7)

Fresh trees pre-staged with `ISAKAI_NO_PRIVILEGE=1 isakai --work .cache/isakai/recheck/c7fresh-<app> install .cache/isakai/apks/<pinned apk>` (empty data dirs at start).

- **OpenCalc: PASS** (`evidence/C7/2026-10-27/opencalc/`). First attempt flaked: app logged `12+348` (spurious extra `8`) and the fresh-result gate correctly rejected it. Fresh reinstall + rerun: `12+34 → 46` committed, clipboard `46`, resize, 31 s liveness, exit 0. Same transient-input family as the C7-recorded `Syntax` flakes; the gate's fresh-result assertion held in both directions.
- **Calculator: FAIL 2/2** (`evidence/C7/2026-10-27/calculator/`, `/tmp` logs removed after capture). `2+2=4` computes (screenshot + history row), but the `SETTINGS_ITEM=(580,16)` tap opens `UnitConverterPickerActivity` instead of `SettingsActivity` (log line present; `SettingsActivity` absent). Fresh reinstall reproduced identically. The flow's own comment already notes hitboxes sit apart from glyphs (560→converter, 580→settings, 592→about); on current HEAD 580 resolves to the converter.
- **Notes: FAIL 2/2** (`evidence/C7/2026-10-27/notes/`). `+` tap + title typing reach the titled dialog (`N2` visible), but the confirm tap at (282,461) adds no editor fragment within the 15 s poll and no `notes.db` row (only the `General note` seed). Fresh reinstall reproduced identically (same stuck-dialog state in `02-create-failed`, no fragment, no db row).

Common thread (hypothesis for the C6/framework owner, not a fix — code is out of scope): both failures are taps resolving to the wrong target (converter is left of the gear; the dialog confirm lands ineffectively). Smells like a touch hit-test/coordinate offset on HEAD, not flow timing: calculator fails before any wait, notes fails after a 15 s poll with zero fragment/log activity.

## 3. Operator runbook (exact commands, from worktree root)

```sh
export ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-c7 WAYLAND_DISPLAY=wayland-1 \
  XDG_RUNTIME_DIR=/tmp/isakai-wayland-c7 ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-c7 \
  ISAKAI_STAGE_DIR=/tmp/isakai-1000-c7-stage   # live-gate only: keep live WAYLAND_DISPLAY+XDG_RUNTIME_DIR
zig build                                       # must exit 0
tools/e2e-privileged.sh --required              # expect PASS ... exit 0; artifacts path printed as ARTIFACTS:
# copy $ARTIFACTS to evidence/C7/<date>/gate before /tmp cleanup
```

- If the gate fails at install with `HelperFailed`/`UidTaken`: some other worker holds low uids. Check `getent passwd | grep isakai` and for live processes (`ps -u 200003` etc.). Do NOT remove accounts with live processes. The `.cache/isakai/priv/registry.json` seed in this worktree is a copy of the true global owners as of 2026-10-27; re-copy from the main repo if stale.
- Pinned rehearsal (private sway): start `XDG_RUNTIME_DIR=/tmp/isakai-wayland-c7` (unset `WAYLAND_DISPLAY` for sway itself — it creates `wayland-1`) `…/compositor/root/usr/bin/sway -c …/compositor/sway.conf`; pre-install fresh trees as above; run `python3 verify-isakai/sway-flow.py {opencalc,calculator,notes} --work .cache/isakai/recheck/c7fresh-<app> --evidence evidence/C7/<date>/<app>`. Stop sway + app processes when done; remove `/tmp/isakai-wayland-c7 /tmp/isakai-1000-c7*`.

## Remaining items (not closable from this worker)

1. Calculator settings-tap routing and Notes dialog-confirm no-op reproduce 2/2 on fresh data — needs C6/framework owner with code access; the tap-offset hypothesis is untested.
2. The C8-noted blank Compose settings captures could not be re-checked (flow never reaches settings). C8 owns the rerun.
3. `--privileged` pinned runs (real per-app confinement) were not attempted: main-repo pinned installs own 200000-200002 and sibling workers were actively driving flows; a privileged pinned run here would contend for the same uids/bridge. The privileged *fixture* gate above is the fresh default-isolation evidence.
4. No probes/test files added to the tree; `/tmp` scratch and my sway removed (one zombie `[sway]` pid 73725 remains for init to reap — dead, no socket).

## Verdict

**Gate maximally advanced, not closed.** The required default-isolation gate passes with fresh 2026-10-27 evidence (attempt-1 failure fully explained and recovered same-session). Pinned acceptance is red on fresh data for Calculator and Notes with a precise, reproducible defect record pointing at tap routing — C7 cannot be closed until those pass.
