# N6 worker run — 2026-10-28 (worktree agent-ae5160c5e7a2c2035)

Branch: `worktree-agent-ae5160c5e7a2c2035`. This file is the only
progress/ write from this run; shared task files were not edited.

Environment (all unique to this run, per task setup):

- `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-n6`,
  `WAYLAND_DISPLAY=wayland-1`, `XDG_RUNTIME_DIR=/tmp/isakai-wayland-n6`
- `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-n6`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-n6-stage`
- Private Sway fixture from this worktree's
  `.cache/isakai/compositor` (copied from main per setup) plus
  `isakai-vinput` daemon; output HEADLESS-1 1000x1000.
- App work dir `/tmp/isakai-n6-work` (fresh install of pinned
  `sgtpuzzles_149.apk`, sha256 `6b36d5…3d652e4`).
- `zig build` exit 0 before and after; `git diff --check` clean;
  `python3 tools/progress-lint.py` 0 violations (rerun at end).

## 0. Build prerequisite: bionic artifacts (reuse, not fixtures)

`zig build` initially warned `native-app loading stays disabled` because
`.cache/n6-bionic-build` did not exist in this worktree. Copied the four
pinned ATL `*_bio.so.0` files from main's
`/home/tenzin/development/isakai/.cache/n6-bionic-build` (pinned
`bionic_translation@6253dec`, same build N6 step 1 verified) as real
files — a relative symlink and `-Dbionic-dir=<absolute>` both fail the
build probe (`b.path` rejects absolute paths; the probe needs the exact
`.so.0` basenames). Hashes match main bit-for-bit:

- `libdl_bio.so.0 bd8b18a8…`, `libc_bio.so.0 7a6f61ed…`,
  `libpthread_bio.so.0 b33691cc…`, `libstdc++_bio.so.0 23cdd16a…`

After `rm -rf .zig-cache` (the build graph cached the first
`have_bionic=false` configure) + rebuild, `zig-out/framework/lib/bionic/`
holds all four libs + `libisakai_jni.so` + `xdg/…cfg.d`. `.cache/` is
gitignored, so the tree diff stays clean. No probes left in tree.

## 1. Fix: Activity array/Bundle dispatch (trampoline was silently dropped)

Symptom: every puzzles launch died instantly, exit 0, no window, no JNI:

```
isakai: activity name.boyle.chris.sgtpuzzles.SGTPuzzles runWindowed
isakai: new root android.view.View bounds=0,0,0x0
isakai: activity name.boyle.chris.sgtpuzzles.SGTPuzzles destroyed
isakai: run complete
```

No `… start` line for any second activity, with or without `--data`.

Root cause (proven, not guessed): translated `SGTPuzzles.onCreate`
always trampolines via app-bundled androidx `TaskStackBuilder`, whose
`startActivities()` calls `Context.startActivities(Intent[], Bundle)` on
the Activity. Since 4f961fb made `Activity extends ContextThemeWrapper`,
the inherited `ContextWrapper.startActivities` delegates to the **base**
Application, never dispatching back to `Activity.startActivity(Intent)` —
the only override that routes explicit components to
`Navigator.launch`. Bytecode read of translated `SGTPuzzles.onCreate`
and `TaskStackBuilder.startActivities`, plus the framework chain
`ContextWrapper.startActivities → base… → Context` no-op, confirm the
drop. `finish()` (trampoline) then set `backPressed` and `runWindowed`
broke out of its loop before any window/input.

Fix (`framework/android/app/Activity.java`, +13 lines): override the two
entry points and keep the single-intent rule:

```java
@Override
public void startActivity(Intent intent, android.os.Bundle options) {
    startActivity(intent);
}
@Override
public void startActivities(Intent[] intents, android.os.Bundle options) {
    if (intents == null) return;
    for (Intent intent : intents) startActivity(intent);
}
```

Implicit-intent behavior (VIEW/picker/file) is untouched — same method.
Proven by the drives below; C7 regression (calculator same-UID flow via
`verify-isakai/sway-flow.py`) still PASSes (settings persist, back
navigates, exit 0, 31 s liveness) — see
`evidence/N6/2026-10-28/n6/calc-regression/`.

## 2. Fix: loader re-entry guard (review P1, preventive)

`src/native/bionic_shim.zig`: thread-local `in_bionic` set around the
`bionic_dlopen` call; a re-entrant `dlopen` on the same thread (ATL's own
missing-dependency fallback, which calls host `dlopen` and re-enters the
interposer) takes the host path only instead of recursing into the
loader. The recursion itself was never reproduced (N6 already records it
as source-based risk) — this is hardening, verified by no-regression:
the drives below map/relocate `libpuzzles.so` (~1900 relocations),
resolve `JNI_OnLoad`, and run the full JVM without loader faults.

Deliberately NOT changed: `android_dlopen_ext` still routes through the
same `dlopen` with extinfo ignored (single-namespace loader; namespaces
cannot be honored — handoff), and glibc-first routing stays try-fail
rather than path-classified (correct on edge cases: host JVM libs and
guest Android sonames coexist — the IsakaiWindow natives resolve via the
host handle while `libpuzzles.so` resolves via bionic in the same
process; explicit path classification would add misrouting edge cases —
handoff for a loader-owner decision).

## 3. Drive A (chooser, no intent): mapped window, input, clean exit

Command (background, log `evidence/N6/2026-10-28/n6/puzzles-chooser.log`):

```
ISAKAI_NO_PRIVILEGE=1 ISAKAI_FRAMEWORK=$PWD/zig-out/framework \
ISAKAI_WINDOW=1 ISAKAI_NO_BRIDGE=1 \
ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-n6 ISAKAI_STAGE_DIR=/tmp/isakai-1000-n6-stage \
ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-n6 WAYLAND_DISPLAY=wayland-1 \
XDG_RUNTIME_DIR=/tmp/isakai-wayland-n6 \
zig-out/bin/isakai --work /tmp/isakai-n6-work launch name.boyle.chris.sgtpuzzles
```

Result: `GameChooser start`, `GamePlay start`,
`D/GamePlay: no state, starting chooser`, mapped window
`name.boyle.chris.sgtpuzzles | Puzzles` (sway tree), screenshot
`chooser-mapped.png` shows the rendered instruction text
("Long press to star a game"); game tiles blank (known rendering
limitation, unchanged from 2026-10-26). jstack showed main parked in
`IsakaiWindow.nRun` event loop with normal JVM threads. vinput
`click 500 300 80` + 800 ms long-press both `ok`; `chooser-after-input.png`
unchanged, process alive, no crash. Compositor close:
`swaymsg '[app_id="name.boyle.chris.sgtpuzzles"]' kill` →
`SGTPuzzles destroyed`, `run complete`, `exited with code 0`.

## 4. Drive B (Cube intent): precise abort chain, game never starts

Same command + `--data "sgtpuzzles:cube:c4x4:5B10,2"`
(log `puzzles-cube-full.log`, and `puzzles-jni.log` with
`_JAVA_OPTIONS="-verbose:jni"`). Loader evidence: `libpuzzles.so`
mapped/relocated via `bionic_dlopen`, `bionic_dlsym(JNI_OnLoad)` FOUND in
`libpuzzles.so`. `GameChooser start`, `GamePlay start`,
`D/GamePlay: URI is: "sgtpuzzles:cube:c4x4:5B10,2"` — then:

```
Exception in thread "main" java.lang.VerifyError:
(class: androidx/appcompat/widget/AlertDialogLayout, method: onMeasure) Bad access to protected data
at GamePlay.messageBox <- GamePlay.abort <- GamePlay.onNewIntent <- GamePlay.onResume
```

Diagnosis (each step reproduced, no guessing):

- Our `android.net.Uri` is innocent: jshell on the built framework
  gives `scheme=sgtpuzzles`, `ssp=cube:c4x4:5B10,2` for the exact string.
- No `bionic_dlsym` for any `Java_…GameEngineImpl_…` static native occurs
  in the whole run — HotSpot never attempted a puzzles native call — so
  the abort precedes all native engine work.
- Translated `GamePlay.onNewIntent` aborts via the pure-Java
  `"Unrecognised game in URI:"` path: `BackendName$Companion.byLowerCase`
  returned null. jshell against the freshly translated classes proves it:
  `byLowerCase("cube") -> null`, `getAll() -> []`.
- Cause: the app builds its game map via Kotlin
  `KClass.getSealedSubclasses()` on sealed `BackendName`
  (`BackendName$$ExternalSyntheticLambda0`). The translated
  `BackendName.class` carries **no `kotlin.Metadata` annotation and no
  sealed/`PermittedSubclasses` info** (`javap -v`: only
  `ACC_PUBLIC, ACC_SUPER, ACC_ABSTRACT`; subclasses like `CUBE` exist as
  relocated top-level classes). Kotlin reflection therefore finds zero
  subclasses. The translator never emits class annotations/sealed
  metadata (`grep -ri sealed|Metadata src/ tools/` empty) — translator
  territory, not touched per scope.
- The `VerifyError` that hides the abort message is emitted JVM bytecode
  verification of a translated androidx class (`AlertDialogLayout`) —
  likewise translator output, not touched per scope.

Unblocks B1/B2: translator preserves Kotlin `Metadata`/sealed-subclass
info through translation (translator owner), or an app-side game-lookup
that does not depend on sealed reflection. Repro commands are above
(jshell one-liners); no new files were created for any probe.

## 5. Remaining blockers (nothing else is actionable in scope)

- **Default-isolated useful actions**: blocked on operator privilege.
  `sudo -n true` → `sudo: a password is required`, exit 1. Same-UID
  evidence above is not a privileged pass. Unblock: root/pkexec session
  running the privileged gate + puzzles under default isolation.
- **Solved puzzle + repeat thread/callback/GC/exception work**: blocked
  behind game start (translator metadata, §4). Proven present but not
  stressable: JVM threads, event loop, input dispatch, orderly teardown.
  The 2026-10-26 repeat-callback evidence stands as history, not current
  proof. Unblock: translator fix, then drive moves + timer callbacks.
- **Loader follow-ups for the loader owner**: extinfo namespaces
  (intentionally unsupported, single namespace) and explicit
  host/guest classification (try-fail kept deliberately — §2).
- **Handoffs, not edits**: review P2 `navigateUpTo` multi-pop and P2
  `ResEngine` density override (framework/app + N5 resource code —
  untouched per scope); `docs/ARCHITECTURE.md:64-65` +
  `docs/HOST_REQUIREMENTS.md:81` still say "fixed 9-slot envp" while
  `src/uid_helper.zig:948` builds a 10-slot buffer (up to 9 entries +
  terminator) — docs-only, left for its owner; N18 NDK-asset fd/offset
  acceptance (needs N6/N15).

## 6. Self-review of this run's diff

- `Activity.java` override: only adds virtual-dispatch restoration for
  the two entry points `ContextWrapper` was swallowing; all routing
  decisions stay in the existing 1-arg method. C7 calculator flow PASS
  is the regression proof; puzzles chooser/game-start is the fix proof.
- `bionic_shim.zig` guard: thread-local, cleared immediately after the
  `bionic_dlopen` call returns (no `defer`-on-assign); only the nested
  same-thread fallback path changes behavior. Loader drive shows no
  fault, no fallback loop, exit 0.
- No test files, scripts, or fixtures added to the tree; `/tmp`
  scratch (drive script, javap dumps, build logs) deleted; fixture
  (sway, vinput, app) stopped at end. `zig build` 0, `git diff --check`
  clean, `tools/progress-lint.py` 0 violations.

## Evidence index

`evidence/N6/2026-10-28/n6/`: `chooser-mapped.png`,
`chooser-after-input.png`, `puzzles-chooser.log` (exit 0),
`puzzles-cube-full.log` (abort + VerifyError, exit 1),
`puzzles-jni.log` (`-verbose:jni`, single `JNI_OnLoad` dlsym, zero
`GameEngineImpl` resolutions), `calc-regression/` (calculator PASS).
Earlier `puzzles-cube*.log`/`puzzles-nointent.log` are pre-fix
instant-death records kept for contrast.
