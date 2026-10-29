# IMPL-genexec2: Puzzles generator executes, Solo board generates, Error dialog gone

Date: 2026-10-29. Branch: `worktree-agent-a67935da795aa696d`. Scope:
continued the stopped generator worker (`f46ef6a` on
`worktree-agent-a6e268b0ec277e3e5`, cherry-picked, not restarted).

## Goal, in my own words

Make the puzzles generator binary execute so the game board generates
and the Error dialog goes away. Smallest change: finish the
in-progress bionic-exec design plus whatever small framework piece the
exec path needs. If the design proves wrong, redesign from first
principles rather than bolting on.

## Start: read the WIP diff and evidence

`git show f46ef6a` is an `isakai-bionic-exec` runner
(`src/bionic_exec.zig`, new, wired in `build.zig`), an exec/spawn
interposer in `src/native/bionic_shim.zig`, `ISAKAI_BIONIC_EXEC`
advertisement in `src/launch.zig`, plus `View.isShown()` in
`framework/android/view/View.java`. Its evidence
(`evidence/2026-10-28/genexec/`) shows chooser maps, Solo starts,
`GameGenerator exec` fires, and the game screen still ends on the
Error dialog (`game-after-tap.png` 9467 bytes, same dialog size class
as pixels5). Zero `isakai-bionic-exec` lines in all four drive logs:
the redirect never observably fired there, and no errno was recorded.

Cherry-picked first: `src/bionic_exec.zig`, `src/launch.zig`,
`src/native/bionic_shim.zig`, `build.zig`. Deliberately left out:
`View.java isShown()` (outside the file boundary, not the exec path)
and all `genexec/` evidence (stays in the other worktree). The
isShown verdict below reverses that call with a reason.

## The design is sound, proved directly

The premise under test: the host kernel cannot exec the generator
because its PT_INTERP is `/system/bin/linker64` (absent on the host),
so plain execve fails with ENOENT and the app shows Cannot run
program. `readelf` on the APK's `lib/x86_64/libpuzzlesgen.so`
confirms ELF PIE executable, interpreter `/system/bin/linker64`, NEEDED
`libpuzzles.so`. Direct run of the fresh-built helper with the app lib
dir on the loader path:

```
BIONIC_LD_LIBRARY_PATH=/tmp/genexec2-src/lib/x86_64
ISAKAI_BIONIC_LOADER=$PWD/zig-out/framework/lib/bionic/libdl_bio.so.0
zig-out/bin/isakai-bionic-exec .../libpuzzlesgen.so solo 3x3
```

exits 0, stdout is pristine generator output (`GAME :4:Solo`,
`PARAMS :3:3x3`, `DESC`, `AUXINFO`), stderr empty. Without the lib dir
it fails cleanly via `bionic_dlerror` on stderr. So the runner works;
kept as is. Static review of the kept code found no new defects: the
`blk[1] = sp + 16` argv slot is correct (argv array starts at
`blk[2]`), stack-built env is consumed by the kernel before return,
and the launch.zig env strings outlive the spawn (frees run at
function return, after the child copies its env).

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from
  the main checkout cache, plus `.cache/n6-bionic-build` from main
  followed by `rm -rf .zig-cache` plus rebuild (`.cache` is
  gitignored, tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-genexec2`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-genexec2`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-genexec2-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build, never
  the stale system tree), `ISAKAI_NO_PRIVILEGE=1`, `ISAKAI_WINDOW=1`,
  `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (HEADLESS-1 1000x1000 confirmed via
  swaymsg; swaybg warning only, known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-genexec2-work`.
- Read first: `progress/reports/IMPL-pixels5.md`,
  `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 before the first drive, after adding
  `View.isShown()`, and final (transient first-run javac failure each
  time after a graph change; immediate rerun exit 0, helper at
  `zig-out/bin/isakai-bionic-exec`, bionic libs present).
- `git diff --check` clean.
- `python3 tools/progress-lint.py` 0 violations.
- `verify-isakai/` driven read-only (vinput taps, grim captures,
  `sway-flow.py` calculator) and never modified.

## Drive 1: generator runs, new fault isShown (evidence it ran)

One launch, chooser with no intent
(`evidence/2026-10-28/genexec2/puzzles-drive.log`):

- Chooser maps (`chooser-full.png`, full grid, same bytes as the prior
  worker's captures). Tap Solo (vinput `click 355 310 80`):
  `GamePlay start`, `URI sgtpuzzles:solo`, `Using default params 3x3`,
  `D/GameGenerator: exec: [.../lib/x86_64/libpuzzlesgen.so, solo, 3x3]`.
- Then the generator-completed path runs: `GamePlay.startGameConfirmed`
  via the Handler callback (only reachable after the generator child
  returns its board), into `ToolbarActionBar.setTitle` →
  `ViewCompat.setAccessibilityPaneTitle` → crash, twice in the log:

```
Exception in thread "main" java.lang.NoSuchMethodError: 'boolean android.view.View.isShown()'
```

The process dies, window gone. Reaching `startGameConfirmed` proves
the exec redirect works end to end: the ENOENT is gone, the board was
generated. The missing `isShown()` is the small framework piece the
exec path needs, which reverses the earlier discard: cherry-picked
`View.java` from `f46ef6a` unchanged (AOSP parity walk over ancestor
visibility), rebuilt exit 0.

## Drive 2: board generates yes, Error dialog gone

Relaunch (same install, `puzzles-drive2.log`, zero `Exception` lines),
chooser maps (`chooser2-full.png`), same Solo tap:

- Same exec line, then past `startGameConfirmed` with no fault.
  `D/GameView: onSizeChanged: 1000, 944`.
- `game2-after-tap.png` (22409 bytes) shows the generated Solo 9x9
  board with givens, back arrow, `Solo` title, and no Error dialog.
  Verdict on the task question: board generates yes.

Cosmetic note, not a fault: given digits render clipped at cell row
boundaries (text baseline vs cell geometry). Board state is fully
legible; left for whoever owns game-view text layout.

## Regression: calculator flow PASS

`python3 verify-isakai/sway-flow.py calculator --work
/tmp/isakai-calcreg-genexec2 --evidence
evidence/2026-10-28/genexec2/calcreg` on a second private fixture:
PASS, exit 0, settings persist plus reopen (`decimal_row_persisted`,
`reopened`, `back_exit_code 0`). Calculator APK installed fresh to
that work dir (sha256 matches `progress/APPS.md`).

## Cleanup

No probes in tree (`git status` shows only the five source files plus
`evidence/2026-10-28/genexec2/` plus this file). No `/tmp` scripts
created; `/tmp/genexec2-src` plus helper stderr deleted. App
processes killed, both sway fixtures stopped, unique env dirs
(`/tmp/isakai-wayland-genexec2`, `/tmp/isakai-1000-genexec2*`,
`/tmp/isakai-wayland-1000`, `/tmp/isakai-1000-isosway*`) removed;
`/tmp/isakai-genexec2-work` plus `/tmp/isakai-calcreg-genexec2` kept
because the evidence logs reference their paths. No sleep over 20s
(polling loops only). One evidence edit: drive 1's second capture
showed the operator host desktop (grim read the wrong display after
the window died) and was deleted to avoid confusion; the genuine
black pre-fix frame is kept.

## Code review (self)

Scope kept to the boundary: runner, shim interposer, env
advertisement, build wiring, plus `View.isShown()`. No translator,
input, or `verify-isakai/` changes. No material findings remain: the
redirect is transparent (argv/env shape preserved, passthrough when
the helper is absent or the target is not an Android PIE), stdout
stays pristine for the app, failures surface through the original
exec's errno. Remaining limit: digits clipping noted above; loader
log chatter (`failed to link ld-linux-x86-64.so.2` and friends at
startup) predates this change and is harmless.

## Verdict

Board generates yes: `evidence/2026-10-28/genexec2/game2-after-tap.png`.
Error dialog gone. Calculator regression PASS.
