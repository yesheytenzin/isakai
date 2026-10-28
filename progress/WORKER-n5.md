# WORKER N5 — C8 AssetFileDescriptor repair review + caller evidence

Date: 2026-10-28. Worktree: `agent-ac5fb0368a1887135` (own git worktree, HEAD = main tip `548d94e`).
Scope: review-only plus verification drives. No repair-code changes made (none warranted).
Ownership respected: resource/asset/framework-res paths read; bionic/loader/threading,
translator, dbus_bridge untouched; verify-isakai used read-only as driver.
Env: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-n5 WAYLAND_DISPLAY=wayland-1
XDG_RUNTIME_DIR=/tmp/isakai-wayland-n5 ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-n5
ISAKAI_STAGE_DIR=/tmp/isakai-1000-n5-stage`, same-UID development mode throughout
(`ISAKAI_NO_PRIVILEGE=1`). No privileged result claimed.

## Review verdict: no material defect in the C8 repair

Reviewed `framework/android/content/res/AssetFileDescriptor.java`
(`AutoCloseInputStream`), `AssetManager.openFd/descriptor`, and
`framework/android/os/ParcelFileDescriptor.java` at HEAD, plus all call sites:

- Range enforcement is complete on every read path. Single-byte `read()`,
  bulk `read(b,off,len)` (capped at `remaining`), `skip`, `available` all honor
  `startOffset/length`, including `UNKNOWN_LENGTH (-1)` pass-through. The three
  `FileInputStream` bulk overrides (`readAllBytes`, `readNBytes`, `transferTo`)
  are present — without them the JDK channel-based implementations would bypass
  the bounds. No override is missing: `skipNBytes` (default routes through the
  bounded `skip`) and `mark/reset` (unsupported by `FileInputStream`) need none.
- Ownership is fail-closed. `afd.close()` closes the `ParcelFileDescriptor`;
  stream `close()` closes the asset then `super`; both are idempotent
  (`PFD.closed` flag). Constructor failure closes what was opened. Verified live:
  post-`close` `createInputStream().read()` throws `IOException: closed`.
- `AssetManager.openFd` closes the `NativeAsset` before opening the archive file
  read-only (no handle leak), returns `start/length` from the native range, and
  rejects compressed entries with AOSP's "probably compressed"
  `FileNotFoundException`. Legacy staged-tree path views the whole file
  (`start 0, length = stat size`).
- Single construction site: `AssetManager.descriptor` is the only `new
  AssetFileDescriptor` besides `ContentProvider.openAssetFile` (whole-file,
  `UNKNOWN_LENGTH` — consistent, unbounded by intent). No framework code calls
  `openFd`/`createInputStream`, and nothing in-tree calls `getChannel()` or
  `getParcelFileDescriptor()` on an asset descriptor — the documented
  whole-archive raw/channel access has no in-tree escaper.
- Residual shared-offset semantics (two streams over one AFD share the file
  offset) match the documented raw-access contract; no caller was observed doing
  this. Not filed: it is the C8-documented design, not a defect.

## Caller evidence (new, this session)

No actual app caller of the bounded path exists to record: repo-wide grep finds
zero in-tree callers of `openFd`/`createInputStream` (only upstream
`libandroidfw` `ZipFileRO::openFd` internals, unrelated), and none of the pinned
APKs' framework-reachable paths invoke it during flows. So the bounded path was
exercised directly on real pinned APK bytes via `jshell` over the built
framework classes (existing tool, no new files; `-R-Djava.library.path` needed
because jshell evaluates in a remote agent VM). Full transcripts:
`evidence/N5/2026-10-28/n5/afdfix-jshell.log`,
`evidence/N5/2026-10-28/n5/afdfix-jshell2.log`.

- OpenCalc `assets/dexopt/baseline.prof` (stored): `openFd` → start=384 len=2286,
  exactly the `unzip -lv` entry; `createInputStream().readAllBytes()` sha256
  `6e364ba8…88197` equals `unzip -p` sha256; first/last bytes 112/74 match `od`.
  No ZIP bytes leak. Over-read → `-1`, `available()` → 0, `skip(100000)` → 2286
  then `-1`.
- F-Droid `assets/PublicSuffixDatabase.list` (deflated): `openFd` →
  `FileNotFoundException: This file can not be opened as a file descriptor; it
  is probably compressed`.
- F-Droid `assets/dexopt/baseline.profm` (stored): start=9856 len=1415 (matches
  N5's recorded values); single-byte reads 112 114 109 match `od`;
  `available()` 1412 after 3 reads; `readNBytes(2000)` → 1412 then `-1`.

Regression drive with the repair in tree: Calculator isolated-sway flow PASS
(same-UID dev mode, fresh `/tmp/n5-calc-work`): keypad `2+2 → 4` (screenshot
`simple-02-result.png` carries real content), settings, decimal-mark preference
persisted across restart (`decimal_row_persisted: true`), back navigation, 31 s
liveness, clean exit. Evidence: `evidence/N5/2026-10-28/n5/flow-calculator/`
(screenshots + app log). Compositor tree verified empty afterwards; no probes
left in tree; /tmp scratch removed; fixture stopped.

## Remaining verification limit (unchanged in kind, narrowed in scope)

The wrapper itself is now directly proven correct on real archive bytes; what
remains unverified is an *app-driven* bounded-range read — no pinned app calls
`openFd`, so no flow has observed a real caller traversing
`openFd → createInputStream`. NDK `AAsset` fd interop stays in N18 (needs N6's
loader plus an `AAsset`-using x86_64 app; Puzzles uses no `AAsset*` symbol).

## Gates

- `zig build` exit 0 before and after (one transient first-run javac failure in
  the fresh worktree succeeded on immediate rerun with no source change; final
  run exit 0).
- `git diff --check` clean; `python3 tools/progress-lint.py` 0 violations.
- No shared `progress/*.md` touched by this worker (this file only).

## N5 closable: yes

The acceptance boundary required the C8 repair to be reviewed and actual caller
evidence or the remaining limit recorded. The review is done (no defect), direct
bounded-range evidence on real APK bytes is recorded above, and the residual
limit is stated precisely: no observed in-app caller of the bounded path; NDK
fd interop remains N18's gate. No code change in this session.
