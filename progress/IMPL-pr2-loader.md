# PR2: loader shim enforces platform load contracts (extinfo validation + per-thread dlerror)

Date: 2026-10-28. Branch: `pr2-loader` (from `main` 22d5ad8).
Goal in my own words: make the loader shim enforce platform load contracts
instead of silently ignoring them, so bad loads fail loudly and error
reporting follows the thread rules.

## Setup

- Read the brief `reports/Remaining N6 N18 fix plan.md` (loader section) and
  `research_notes/Remaining N6 N18 fix plan/bionic-loader-contracts.md` from
  the main checkout, plus `verify-isakai/SKILL.md`, before editing.
- `mkdir -p .cache/isakai` + copied `compositor` and `apks` from
  `/home/tenzin/development/isakai/.cache/isakai/`; also reused the four
  pinned ATL `*_bio.so.0` files from main's `.cache/n6-bionic-build`
  (hashes match the WORKER-n6 record: `bd8b18a8…`, `7a6f61ed…`,
  `b33691cc…`) into `.cache/n6-bionic-build` + `rm -rf .zig-cache` +
  rebuild, since puzzles needs the bionic loader staged. `.cache/` is
  gitignored; tree diff stays one source file.
- Unique env for all runs: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pr2`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pr2`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pr2-stage`.

## Change (one file: `src/native/bionic_shim.zig`, +94/-11)

Principles applied (leaf skills read): laziness-protocol (smallest diff, one
error source of truth in `setError`), boundary-discipline (all validation at
the `android_dlopen_ext` entry point, nothing re-checked inside).

- `android_dlopen_ext` now validates before any host attempt: NULL extinfo
  forwards as today; unknown flag bits (mask `0x67f`, retired ART `0x80`/
  `0x100` stay rejected), OFFSET-without-FD, any RELRO/reserved-address bit
  (`0x40f`), and any USE_NAMESPACE fail loudly via the shim thread-local
  buffer. `USE_LIBRARY_FD`/`FORCE_LOAD` fall through by filename (documented
  in a comment). dlopen flags validated against bionic's six bits (`0x1107`).
  Plain interposed `dlopen` keeps host semantics untouched.
- dlerror follows the thread rules: per-thread 512-byte buffer, written on
  failure only (guest dlsym/dlclose failures write it, with a generic
  fallback when bionic supplies no text), success preserves stale errors
  (all `clearError()`-on-success removed), read returns-then-clears with
  host fallthrough. `setError` drains the stale host error so the shim
  message is current and the next read is NULL; host-failure paths drop a
  stale shim error so the fresh host message surfaces.
- Host-first ordering documented as an isakai-only rule in the N6 comment;
  stale header line ("extinfo ignored") updated.

## Verification (real artifacts, observed)

- Gates: `zig build` exit 0 before and after; `git diff --check` clean;
  `python3 tools/progress-lint.py` 0 violations.
- Targeted matrix against the built `zig-out/framework/lib/
  libisakai_bionic_shim.so` via a throwaway `/tmp` harness (deleted after;
  nothing added in-tree): all 11 checks pass, each failure case observed as
  message-then-NULL — bad bits, OFFSET-without-FD, USE_NAMESPACE null and
  non-null, RELRO, reserved-address, bad dlopen flags; NULL extinfo loads
  libm via host as before; post-success dlerror NULL; FD-bit forwards by
  filename; host dlsym failure reports then NULL. Full output:
  `evidence/2026-10-28/pr2-loader/dlerror-matrix.txt`.
- Puzzles chooser maps: fresh install of pinned `sgtpuzzles_149.apk`,
  private headless sway fixture; log shows `libpuzzles.so` loading through
  the modified shim, `JNI_OnLoad` resolved, `GameChooser start` /
  `D/GamePlay: no state, starting chooser`, sway tree
  `name.boyle.chris.sgtpuzzles | Puzzles visible=True`,
  `chooser-mapped.png` renders "Long press to star a game", vinput click ok,
  compositor close → `SGTPuzzles destroyed`, `run complete`, process gone.
- Calculator flow PASSes: `sway-flow.py calculator` on a fresh install tree
  → `PASS: calculator flow (isolated sway)` (settings persist, history
  commits, screenshots under `evidence/2026-10-28/pr2-loader/`).

## Notes and limits

- A copied main-baseline calculator work tree failed `2+2= commit` twice
  with the fix AND once with the fix stashed (pristine-main rebuild):
  pre-existing stale-tree behavior, not a PR2 regression. Fresh install
  passes with the fix. Stale tree kept out of the way (`.cache/`, ignored).
- Guest (tracked-handle) dlsym failure could not be driven live: no Android
  DSO handle is obtainable without a full app load, and the puzzles run does
  not fail a guest lookup. The path mirrors the proven dlopen write path
  line-for-line; marked as coverage gap, not claimed.
- `RTLD_DEFAULT`/`RTLD_NEXT` across the host/guest boundary and the
  app-lib extinfo grep stay open per the research notes; both need N6
  decisions, unchanged by this PR.
- Cleanup done: fixture stopped, `/tmp/pr2-*` and `/tmp/isakai-*-pr2*`
  removed, no probes in tree. File boundary honored: only
  `src/native/bionic_shim.zig` + this record + evidence committed.
