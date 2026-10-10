# M0-M2 limitations fix plan + real-APK ladder

M3+ work is tracked in `progress.json`; this doc is the M0-M2 design log.

Researched 2026-10-09, all items landed 2026-10-09. Every item fits Zig 0.17 +
host JDK 17+ with zero Android binaries. Nothing is BLOCKED.

## Phase 0 — M0 correctness quick wins (~1 week, all independent)

1. **Registry TOCTOU — DONE.** sidecar `isakai.lock`, `std.Io.File.lock`
   (flock): exclusive for writers, shared for readers. Same pattern as
   Android's MultiDexExtractor (exclusive FileLock held across
   extract-then-publish).
   - https://man7.org/linux/man-pages/man2/flock.2.html
   - https://android.googlesource.com/platform/frameworks/multidex/+/b4b29e186f614cc9184777e2d5d18c5d993d4b43/library/src/androidx/multidex/MultiDexExtractor.java
2. **Running PID tracking — DONE.** record `child.id` after spawn ("On POSIX this
   is the pid"), liveness via `kill(pid, 0)` (ESRCH = dead).
   - https://github.com/ziglang/zig/blob/master/lib/std/process/Child.zig
3. **JVM discovery — DONE.** order: `$JAVA_HOME/bin/java` → PATH → scan
   `/usr/lib/jvm/*/bin/java`; validate each via `java -version` parse
   (JEP 223 grammar, handles legacy `1.8`).
   - https://openjdk.org/jeps/223
4. **Multi-DEX — DONE.** enumerate `classes.dex`, `classes2.dex`… (stop at first
   gap, warn on non-contiguous extras); validate by DEX magic, not name;
   per-dex ID namespaces + global class map (first-wins, duplicates are a
   loud error).
   - https://android.googlesource.com/platform/frameworks/multidex/+/b4b29e186f614cc9184777e2d5d18c5d993d4b43/library/src/androidx/multidex/MultiDex.java
   - https://github.com/JesusFreke/smali/blob/master/dexlib2/src/main/java/org/jf/dexlib2/dexbacked/ZipDexContainer.java
   - https://developer.android.com/build/multidex
5. **Native `.so` gate — DONE.** explicit install-time rejection when `lib/` is
   present (until a Bionic-bridge milestone exists). One-gate change in
   `inspect()`.

## Phase 1 — M0 security (~2–3 weeks)

6. **APK Signature Scheme v2 verification (M), v2-ONLY — DONE 2026-10-09.**
   Never v1: Android 11+ requires v2+, and the spec mandates fail-closed.
   No CA/PKIX chain validation — leaf-pin model only (parse leaf DER,
   extract SPKI, byte-compare to signer key, SHA-256 fingerprint). Port
   structure from apksig-go; RSA/ECDSA via Zig std, DSA via FIPS 186-4
   over std Managed ints (square-and-multiply powmod). Coverage:
   RSA-PKCS1v15 + PSS (1024–4096; >4096 → UnsupportedAlgorithm),
   ECDSA P-256/P-384 × SHA-256/SHA-512 (hash decoupled from curve;
   P-521 → UnsupportedAlgorithm), DSA 0x0301 (1024/2048/3072, q 160/224/256
   guard). Per-entry tolerance: unknown/unsupported sig algs are skipped
   (upstream V2_SIG_UNKNOWN_SIG_ALGORITHM behavior) with full-record
   anti-strip comparison and digest re-hash over the verified subset only;
   0x04xx verity stays skipped; verified==0 with only-unsupported skips →
   UnsupportedAlgorithm, else SignerFailed. Strict DER minimal-length pass
   on the leaf cert (std accepts non-minimal long forms Android rejects).
   Multi-signer APKs rejected (single-identity pin; documents divergence
   from upstream per-signer verify). Golden suite: 35 vectors vendored in
   testdata/apksig/ (GrapheneOS platform_tools_apksig@17, see README +
   tools/fetch-apksig-vectors.sh), table-driven @embedFile test
   (offline, pure-bytes path via verifyBytes); src/testdata symlinks to
   ../testdata (Zig @embedFile is package-confined to src/). Must run
   before any privilege step.
   - https://source.android.com/docs/security/features/apksigning/v2
   - https://github.com/agusibrahim/apksig-go (Apache-2.0, port base)
   - https://github.com/shuxin/apk-signature-verify (crypto reference)
   - https://android.googlesource.com/platform/tools/apksig/ (authoritative)
7. **`uid-helper` + polkit (S–M) — DONE 2026-10-09.** pkexec + narrow-helper
   (matches polkit best practice). One action covers allocate/remove/run;
   `auth_admin` on all three defaults (no `_keep`), message names
   `$(command_line)`, build.zig stages the .policy to
   share/polkit-1/actions/ (packagers copy it; exec.path MUST be
   /usr/bin/isakai). `useradd --system --uid` from the helper; DynamicUser
   and bare setuid rejected. UID range 200000–299999 (inside
   documented-unused 65536–524287), `/etc/passwd` file-parse collision
   check (NSS-free; our range is file-backed by useradd).
   Data-dir handling is dirfd-anchored throughout: NOFOLLOW statx
   validation, openat(NOFOLLOW)/mkdirat/fchown/fchmod construction, 0700
   chmod on every subdir after chown, dirfd-anchored recursive unlinkat
   removal (S_IFDIR required, symlinks unlinked never followed). Strict
   package validator unified in install.zig + uid_helper.zig (segment must
   start with a letter; kept in sync by unit tests in both files).
   install.zig re-hashes the APK after staging and compares to the code-dir
   digest. Reserve → helper → commit transaction stays.
   - https://polkit.pages.freedesktop.org/polkit/polkit-apps.html
   - https://systemd.io/UIDS-GIDS/
   - https://man7.org/linux/man-pages/man8/useradd.8.html
8. **`spawnAsUser` (S) — DONE 2026-10-09.** Custom fork → close fds >= 3
   (close_range, loop fallback) → `setgroups(0)` → `setresgid` →
   `setresuid` → chdir(data_dir) → umask(0o077) → exec, every return
   checked, `_exit(127)` on failure. runAs allowlist: argv[0] absolute +
   world-executable + basename `java`, argv contains
   `org.isakai.runtime.IsakaiMain`. Per-app env: `TMPDIR` → `<data>/cache/tmp`,
   fresh 0700 `XDG_RUNTIME_DIR` → `<data>/run`, `HOME` → data dir. launch.zig
   reaps stale `running` (demote when !isAlive under lock, warn);
   translateApp chmods class files 0644 + dirs 0755.
   - https://man7.org/linux/man-pages/man2/setuid.2.html
   - https://man7.org/linux/man-pages/man7/credentials.7.html
   - https://man7.org/linux/man-pages/man3/posix_spawn.3.html
   - https://source.android.com/docs/security/app-sandbox
   - https://man.openbsd.org/sshd (privsep precedent)

## Phase 2 — M1 translator robustness (~3–4 weeks)

9. **Differential oracle FIRST (M) — DONE.** No maintained 2024–26 DEX→JVM
   translator exists (enjarify archived 2022). Diff against dex2jar v2.4
   (byte-level) + Storyyeller/enjarify (exception/typing logic) + jadx
   (semantic spot-checks). Cheapest setup, no Java coding: smali-text
   corpus → `smali.jar` → DEX → dual-translate → `javap -c` compare,
   reusing enjarify's `tests/testN/` layout.
   - https://github.com/pxb1988/dex2jar
   - https://github.com/Storyyeller/enjarify
   - https://github.com/skylot/jadx
10. **try/catch (S–M) — DONE.** Parse `try_item` + `encoded_catch_handler`,
    expand to JVM `exception_table` entries (typed + `catch_type=0`
    catchalls) via the code-unit→byte offset map. Port enjarify
    `jvm/writeir.py` (~167–243, ~586–630).
    - https://source.android.com/docs/core/dalvik/dex-format
    - https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html
11. **Opcode expansion (ongoing, oracle-driven) — DONE 2026-10-09 (cmp, filled-new-array, const-pool).** Order by real-APK
    demand: arrays, casts, monitor-enter/exit, switches, fill-array-data,
    filled-new-array, float/long/double arithmetic, wide invoke args,
    jumbo. Each: smali case → translate → dex2jar diff → JVM execute.
    Landed: cmpl/cmpg-float (fcmpl 0x95/fcmpg 0x96), cmpl/cmpg-double
    (dcmpl 0x97/dcmpg 0x98), cmp-long (lcmp 0x94); filled-new-array +
    range (per-element xastore lowering, J/D rejected as BadDescriptor);
    const_/const-high16 large literals via ldc_w Integer pool entries.
    Float/double operands with non-float tracked types (d8 `const/4 v,0`
    zero idiom) are bit-reinterpreted via Float.intBitsToFloat /
    Double.longBitsToDouble. Corpus: cmp, filledarray, consts (+drivers); later suites constclass,
    moves, widefill, widedouble, jumbostr, goto32; full list is 16 suites
    (see docs/TESTING.md; `diff.sh` auto-discovers + `lines.sh` = 18
    assertions). `zig build test` has the J/D rejection unit test.
12. **LineNumberTable (M) — DONE 2026-10-09.** Decode the `debug_info_item` state machine →
    `(jvmOffset → line)` emission hook; golden-test vs baksmali.
    `dex.readDebugLines` decodes the full opcode set; `TranslatedMethod.lines`
    is sorted/deduped; Code attr carries LineNumberTable when non-empty;
    `translate-test --dump-lines` prints triples; `oracle/lines.sh`
    (wired into `diff.sh`) checks dump-lines == javap -l -c ==
    baksmali .line on the lines.smali corpus.
13. **v50 verdict: LOW RISK, no action.** No JEP proposes removing
    old-version loading; HotSpot still accepts 45.3+; the "limited time"
    fallback wording is 15+ years old and never acted on. Only cost: no
    invokedynamic/nestmates. If a bump is ever needed: ASM
    `COMPUTE_FRAMES` as oracle now, native frame emitter later.
    - https://openjdk.org/jeps/182 (compile flags only, not loading)
    - https://asm.ow2.io/javadoc/org/objectweb/asm/ClassWriter.html

## Phase 3 — Real-APK ladder

- Smoke: SmallestAPK — NO RELEASES published (repo has none as of
  2026-10-09); dropped in favor of OpenCalc below.
  https://github.com/krossovochkin/SmallestAPK
- First real: OpenCalc v3.2.1 — VERIFIED 2026-10-09 via `verify-test`
  (fingerprint 21050fd6…c0959, exit 0); 1-byte tamper at offset 10000 →
  `DigestMismatch`, exit 1. https://github.com/clementwzk/OpenCalc
- Second: KeePassDroid v2.6.9.2 — DONE 2026-10-09: 11953/11953 classes
  translate (classes.dex 7198 + classes2.dex 4755), 0 VerifyError, 0
  ClassFormatError on JVM load (1088+9 NoClassDefFoundError = missing
  android.jar, expected). Findings fixed: debug line_start=0 (legal DEX,
  skip line-0 entries), zero-register methods, const-class (ldc_w +
  Wrapper.TYPE for primitives), move/from16+/16 (6 forms), narrow array
  variants (8 opcodes), invoke-argcount wide counting, 3rc argc to 256,
  CESU-8 surrogate combine + MUTF-8 pool emission, Long/Double pool +
  ldc2_w (deleted 25-byte const synth; fixes >64KB methods), full flag
  mapping (abstract/native/interface/enum, SUPER gated off interfaces),
  wide fill-array-data. New oracle suites: constclass, moves, widefill.
  Note: install still (correctly) rejects the APK itself (NativeUnsupported
  — 8 bundled .so files); the ladder exercised verify + translate + load.
  https://github.com/bpellin/keepassdroid
- Excluded for now: ApiDemos (later corpus), AntennaPod/K-9 (network /
  background services — multidex itself is proven by KeePassDroid).

## Phase 4 — M2 window hardening — DONE 2026-10-09

14. **Resize/configure.** `size_gen` + `takeResize` + `isClosed`; JNI
    `nPollSize`/`nSize` (packed jlong, no new slots); Activity redraw loop
    (5000ms budget) renders at live `window.size()`; Canvas recreated per
    resize; SHM pool remade via existing `remakePool`. Deleted v1-illegal
    `wl_compositor.release`/`wl_shm.release` sends from `close()`.
15. **Zero-copy post.** `freeBuffer`/`commitSlot` split; `nMapBuffer`
    (slot 229 NewDirectByteBuffer) + `nCommit`; Canvas IntBuffer backing
    (nativeOrder); stale-buffer contract documented; `ISAKAI_ZEROCOPY=0`
    forces the copy path; `nPost` returns int status (0/-1).
    `jni_table_len` bumped to 236 (true JDK 27 count).
16. **Manifest labels.** `Manifest.app_label`/`launcher_label`
    (literal `android:label` only; `@string` refs ignored until
    resources.arsc lands); title = launcher ?? app ?? package; app-id
    stays the package name. Fixture manifest carries literal labels.
17. **`--enable-native-access=ALL-UNNAMED`** appended unconditionally
    (valid 17+, required 24+ per JEP 412/472); `buildJvmArgv` helper +
    unit test; windowed && privileged prints a stderr hint (privileged
    windowed needs socket brokering — M4+).
18. **Canvas/TextView.** Clip stack (`save`/`restore`/`clipRect`), View
    bounds respected in `onDraw`, TextView view-relative origin + greedy
    word-wrap + `measureText`. Deferred to M3+: ViewGroup/XML inflation,
    Bitmap/PNG, ellipsize/gravity, Paint/alpha, input, animation loop,
    partial damage.

## Phase 5 — M4 app-services gate — DONE 2026-10-10

Gate: OpenCalc + Simple Calculator + Simple Notes complete headless
(onCreate→destroyed, exit 0); fixture headless + windowed green; oracle
18/18; `zig build test` green. Six parallel slices + integration:

19. **Resources (arsc/XML/assets).** `src/arsc.zig` table parse; staged
    res/ + assets/ serving; `Resources` id lookups fail-closed; binary-XML
    decode feeding parsers; `@string` manifest labels resolved.
20. **Layout inflation.** `LayoutInflater` factory chain + widget factory,
    `ViewGroup.findViewById` tree search, `MenuInflater`, theme/style bags
    with parent chains, `TypedArray` attr-id matching (resMap indexed by
    string-pool order per aapt2 XmlFlattener), StAX order-independent parse,
    real parser depth (was flat-cursor).
21. **Graphics decode.** ImageIO PNG/JPEG `BitmapFactory`, drawable XML
    incl. `VectorDrawable` shell, `ColorStateList`, color parsing.
22. **Platform.** Wayland socket brokering (`--wayland-sock` + helper
    bind-mount, privileged E2E env-blocked); wl_output→DisplayMetrics;
    atomic writes + anchored symlinks + arch record; NO_NEW_PRIVS +
    mount-ns confinement; per-dex arena (launch translation 400s→77s).
23. **Services.** Provider hosting + resolver routing, `Parcel`
    round-trip, save-picker writeback, clipboard text/URI/HTML, audio
    format allowlist, gesture stack.
24. **Translator hardening (integration).** 21t/22c/23x reg extracts
    (`if-eqz` byte-reg, `new-array` nibble, 23x byte-A); inherited-ctor
    forwards incl. super-calls + this-alias + self-named; try-end
    shrinking (Dalvik try-start handler semantics vs JVM body-join);
    reaching-def method-entry fallback; array-comp param seeding;
    manifest activity-alias + theme plumbing.
25. **Framework.** ~150 stubs (views, managers, xmlpull, os, util,
    spans, a11y, inputmethod, MediaRouter peers); AOSP hierarchy fixes
    (`AccessibilityEvent`→record, `SeekBar`/`RatingBar`→`AbsSeekBar`).

## Explicitly deferred

- v1 JAR verification — never; fail closed, modern APKs only.
- StackMapTable native emitter — v50 is safe; ASM oracle if ever needed.
- Landlock/seccomp — LANDED 2026-10-10 (M5: confine.zig, live-kernel verified).
- Replace/upgrade flow — LANDED 2026-10-10 (M5: upgrade/rollback + signer continuity via vendored vectors).
- Annotations beyond LineNumberTable — line numbers landed; annotations next.
- ECDSA P-521 curve — zero real-world keys; explicit `UnsupportedAlgorithm`
  with golden rejection tests; revisit only for 100% coverage.
- RSA > 4096 bit, multi-signer APKs — intentional fail-closed rejections.
- Per-request `$(package)` polkit message — impossible via pkexec
  (needs a D-Bus mechanism); `$(command_line)` compromise shipped.
- Native `.so` apps — install rejects (no Bionic bridge milestone).
- WebView — no engine milestone; apps touching it die at NoClassDefFoundError.
- MediaPlayer/codecs/camera/sensors/location/bluetooth/NFC — zero classes;
  SoundPool (WAV playback) only. Driven by app demand.
- Privileged windowed E2E — code landed (socket brokering + helper mount),
  unverified here (no passwordless elevation on this machine).
- Async event loop (epoll/timerfd/eventfd) + JNI upcall callbacks —
  superseded by the poll pump (nPollInput slices); no benefit to switching.
- resources.arsc parsing — LANDED 2026-10-10 (M4).
- Privileged windowed runs (Wayland socket brokering) — LANDED 2026-10-10;
  unprivileged windowed E2E verified 2026-10-10 (real Wayland surface:
  Hyprland client class `org.isakai.fixture`, pixel screenshot). The
  privileged variant (helper bind-mount path) stays E2E-unverified on
  this machine (no passwordless elevation).


## Dependency order

```
Phase 0 (1,2,3,4,5) — all independent, any order
Phase 1: 6 (apksig) ──┐ independent of each other
         7 (uid-helper)┴→ 8 (spawnAsUser needs helper + range)
Phase 2: 9 (oracle) → 10 (try/catch) → 11 (opcodes) → 12 (lines)
Phase 3: SmallestAPK (dropped, no releases) → OpenCalc VERIFIED → KeePassDroid DONE (11953/11953, 0 VerifyError)
Phase 4 (14–18): resize → zero-copy → labels → native-access → canvas (landed together)
```
