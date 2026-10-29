# Stream A: storage-phase pins plus grades (2026-10-29)

Goal in my own words: start the storage phase by pinning real apps and
grading their contract flows. Smallest useful unit is three graded apps,
with no framework changes unless one shared-contract defect blocks two apps.

## Pins

Three FOSS apps from three different authors, covering file, preference,
clipboard, and share-contract behavior. APKs verified by sha256 after
download and staged at `.cache/isakai/apks/` (gitignored). Pins live only
in this file. `progress/APPS.md` was left untouched on purpose: it drives
`tools/pinned_apps.py` observer indices, and promoting red apps there would
break other streams. Promotion belongs to the program owner when an app
goes green.

| App | Author | Version | Package | Contract exercised |
|---|---|---|---|---|
| Markor | gsantner | v2026.10.0 (v164 flavorDefault) | net.gsantner.markor | file edit plus save, preferences, share-receive |
| KeePassDX | Kunzisoft | 4.5.6 libre | com.kunzisoft.keepass.libre | database file open, clipboard, preferences |
| Amaze File Manager | TeamAmaze | v3.11.4 fdroid | com.amaze.filemanager | file browse, preferences, share |

Sources plus checksums:

- Markor `https://github.com/gsantner/markor/releases/download/v2026.10.0/net.gsantner.markor-v164-2026.10.0-flavorDefault-release.apk` sha256 `c105bf5c...da3d8440` (full: `c105bf5cc9530f7486b984323a185c17086b459a56ec936a5990ceb8da3d8440`)
- KeePassDX `https://github.com/Kunzisoft/KeePassDX/releases/download/4.5.6/KeePassDX-4.5.6-libre.apk` sha256 `a77b42f52de4adf2e74e88b0948ef2a681a064a3dbab24e5ff0869a091dbe581`
- Amaze `https://github.com/TeamAmaze/AmazeFileManager/releases/download/v3.11.4/app-fdroid-release.apk` sha256 `2462dc19c75db5eae0629ac40a5f11baecb84afde1565602de5712f6260577e5`

Native slices checked before driving: Markor is pure bytecode (multidex,
no `.so`). KeePassDX and Amaze both ship x86_64 slices, so no ARM wall
applies on this host.

## Grades

Fixture: private headless sway on `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-streama`
(`XDG_RUNTIME_DIR` identical), `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-streama`,
`ISAKAI_STAGE_DIR=/tmp/isakai-1000-streama-stage`, same-UID
(`ISAKAI_NO_PRIVILEGE=1`) work trees under `.cache/isakai/streama/`.
`zig build` exit 0 before driving. Each app was installed, then launched
with a 180s window wait against the sway tree.

| App | Install | Launch | Flow-complete |
|---|---|---|---|
| Markor | pass (uid 200000) | FAIL | not reached |
| KeePassDX | pass (uid 200000) | FAIL | not reached |
| Amaze | pass (uid 200000) | FAIL | not reached |

No window ever mapped for any of the three, so no screenshots exist. The
launch logs under `evidence/2026-10-29/streama/` are the evidence. That
absence is recorded here instead of faked.

## Root causes (one layer each, no shared-contract defect)

- Markor: `NoClassDefFoundError: android/webkit/MimeTypeMap` thrown from
  `AppSettings.<init>` during `ApplicationObject.onCreate`. The framework
  `android.webkit` package ships 9 files and has no `MimeTypeMap` at all
  (confirmed by grep over `framework/` and `src/`). Framework-class gap.
- KeePassDX: launch aborts with `launch failed: BadSection` from the DEX
  parser (`src/dex.zig`). All three of its dex files are valid dex version
  035 with sane headers and matching file-size fields, so the parser
  misreads a 035 section layout. DEX-parser gap, distinct from Markor.
- Amaze: `java.lang.VerifyError: Incompatible argument to function` in
  `AppConfig.onCreate` at `IsakaiMain` class-load time. Bytecode the
  translator emitted fails host verification. Translator-codegen gap,
  distinct from both above.

The plan counts a failure as shared-contract only when two pinned apps from
different authors hit the same layer. These are three different layers
(framework class, dex parse, translator codegen), so no shared-contract
defect blocks and no framework change was made. No per-APK runtime branch
was added anywhere.

## Handoffs

- Framework webkit owner: add `android.webkit.MimeTypeMap` (AOSP static
  mapping API). Markor is the motivating app. Vanilla retest is
  `isakai launch net.gsantner.markor` reaching a mapped window.
- DEX owner: `BadSection` on valid dex-035 inputs; KeePassDX
  (`classes.dex`/`classes2.dex`/`classes3.dex`, all `dex\n035`) is the
  reproducer. Suspect a 037-plus section assumption.
- Translator owner: `VerifyError: Incompatible argument to function` for
  Amaze `AppConfig.onCreate`. Amaze is the reproducer.

## Verification limits

Same-UID runs only, not default-isolation privileged runs. Contract flows
(file save, clipboard, share) were never driven because no app reached a
window. Phase exit (top grades across two consecutive runs) is not claimed.
`git diff --check` clean. Post-drive `zig build` exit 0. No test files, no
tree debris, /tmp drivers deleted, fixture stopped.
