# IMPL-gameinput: Game-screen input reaches the engine, played moves persist

Date: 2026-10-29. Branch: `worktree-agent-ab89bd2920142d7d0`.
Owner handoff: [IMPL-stress](reports/IMPL-stress.md) (GamePlay input fault).

## Goal, in my own words

Make game-screen input reach the engine. Smallest fix at the owning
layer, proved by played moves changing board pixels plus log response
on screenshot and save-file deltas, with the calculator flow PASS as
regression.

## Principles applied

Prove It Works shaped every verdict: each claim ties to a screenshot
pixel diff, a log line, or a state.xml delta, never to build success.
Fix Root Causes shaped the investigation: I instrumented pollInput
before and after the GamePlay root swap as the handoff named, ruled
out the queue, the focus guard, and the Java dispatch chain with
direct traces, and fixed where the trace ended (the native seat
layer), not around it.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from
  the main checkout cache, plus `.cache/n6-bionic-build` from main
  (without it the bionic loader .so is absent and the app dies in
  `GamePlay.<clinit>`; first launch here proved that dependency).
- Unique env for all commands:
  `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-gameinput`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-gameinput`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-gameinput-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build, never
  the stale system tree), `ISAKAI_NO_PRIVILEGE=1`, `ISAKAI_WINDOW=1`,
  `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (HEADLESS-1 1000x1000; swaybg warning
  only, known non-fatal).
- Pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`), fresh work dirs per drive.
- Read first: `progress/reports/IMPL-stress.md`,
  `progress/reports/IMPL-genexec2.md`, `verify-isakai/SKILL.md`.
- `verify-isakai/` driven read-only (one-shot and persistent
  `isakai-vinput` invocations, grim captures) and never modified.

## Gates

- `zig build` exit 0 before the first drive, after each probe/fix
  cycle, and final on the probe-free tree (the known transient
  first-run javac failure appeared after the cache wipe; immediate
  rerun exit 0).
- `git diff --check` clean. `python3 tools/progress-lint.py`
  0 violations.
- No sleep over 20s (polling loops and short settles only).

## Fault reproduced (evidence `puzzles-drive.log`, `game-initial.png`)

Fresh install, chooser maps, Solo tap navigates, board generates.
Tap empty cell plus `key 7`: screenshot diff `bbox None`, zero new
log lines. Same silent loss the stress drive filed.

## Instrument: pollInput around the root swap (probes fully reverted after)

Temporary stderr probes in `Activity.runWindowed` (pollInput counts,
per-event kind/coords/handled, view-tree dump on unhandled) plus
temporary `std.debug.print` in `src/bridge/window.zig`
(`onSeatCaps`, pointer motion/button). All probes reverted before the
final build; none remain in the tree.

## Stall point, exactly

Events do NOT stall in Java. After the GamePlay root swap, pollInput
keeps returning chooser-tap motion/DOWN/UP and dispatch reports
`handled=true`. The stall is one layer down: once the seat's last
input device vanishes (every one-shot `isakai-vinput` quit drives
seat caps 3 to 0) and a new device appears (caps 0 to 3), the
compositor never again routes pointer/keyboard events to the seat
objects bound while devices were present. Native trace proves it:
after the flap, caps events arrive but zero motion/button/key lines
follow, deterministically, even with 5s settle and overlapping
daemons. The client kept its stale ids forever (`onSeatCaps` never
destroyed on loss), so every later tap and keypress was silently
lost. The stress drive's fifteen dead inputs are this artifact: its
first (chooser) tap used the first device, every later one-shot raced
a wedged seat. A physical keyboard unplug/replug would wedge the app
identically, so this is a genuine framework bug, not a drive quirk.

## Fix (owning layer: native seat handling, `src/bridge/window.zig`)

- `onSeatCaps` now releases the keyboard/pointer/touch object when
  its capability is lost (version-guarded exactly like `close()`)
  and zeroes the id, so re-gain re-creates the object and re-links
  the compositor route. Same-file, same-idiom change.
- Same fix pass corrected the `close()` pointer release opcode to 1.
  `wl_pointer.release` is opcode 1 (`set_cursor` is 0); the old code
  sent an empty `set_cursor`, a protocol error that made the
  compositor kill the client. Verified against
  `/usr/share/wayland/wayland.xml`. First boot of the seat fix
  exposed this by exiting cleanly (code 0) right after the first
  device flap.
- Two small framework gaps the now-working input path exposed, both
  AOSP-parity and on the exercised path:
  `KeyEvent.getMatch(char[])` plus the `(char[], int)` overload
  (GameView calls it for non-digit keys; BACK exited via
  NoSuchMethodError without it), and
  `ScrollView.requestChildRectangleOnScreen` (chooser focus-scroll
  during back-nav crashed without it).

## Proof: played moves, pixels, logs, save deltas

Final probe-free build, fresh work dir, persistent vinput drive
(`proof-drive.log`, `proof-moves.png`):

- Tap cell plus `key 3`: green `3` entered with selection highlight;
  tap second cell plus `key 9`: red `9` entered with highlight moved.
  Both digits visible in `proof-moves.png`.
- BACK exit: chooser re-renders (`proof-chooser-back.png`), full exit
  `destroyed` plus `run complete`, zero `Exception` lines
  (`proof-exit.log`).
- Save delta in
  `/tmp/isakai-gameinput-work5/.../shared_prefs/state.xml` (valid
  XML): `NSTATES :1:3`, `STATEPOS:1:3`,
  `MOVE :6:R1,1,3`, `MOVE :6:R6,2,9`. Both played moves persisted.

## Drive-pattern note (not a tree change)

One-shot vinput (connect, act immediately, quit) can additionally
race server-side object creation: a key DOWN fired ~ms after connect
never arrives while the later UP does; a 3s settle (or the persistent
`VInput` every real flow already uses) delivers both. That race is
compositor-side resource timing, distinct from the permanent wedge
this task fixed. Proof drives above use the persistent pattern.

## Calculator regression: PASS

`python3 verify-isakai/sway-flow.py calculator --work
/tmp/isakai-gameinput-calc --evidence
evidence/2026-10-28/gameinput/calc` prints
`PASS: calculator flow (isolated sway)` (settings persist, decimal
result, back exit 0, same-UID development mode).

## Cleanup

Probes fully reverted (`grep` for both probe markers is empty;
final diff is 3 files, +51/-1). `/tmp` one-shot scripts, env marker
files, and vinput holder logs deleted. Fixture stopped, no
isakai/java processes remain. Unique runtime dirs
(`/tmp/isakai-wayland-gameinput`, `/tmp/isakai-1000-gameinput*`)
removed; `/tmp/isakai-gameinput-work*` kept because evidence logs
reference their paths. Final `zig build` exit 0, `git diff --check`
clean, `python3 tools/progress-lint.py` 0 violations.

## Verdict

Input lands yes. The GamePlay input fault from IMPL-stress is fixed
at the native seat layer: device-flap re-acquire plus the pointer
release opcode, with two small input-path framework gaps closed.
Played moves change board pixels, persist to the save file, and the
app exits cleanly; calculator still passes.
