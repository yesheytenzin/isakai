# Findings inbox

Raw findings from audits, reviews, and probes land here with date and source,
then get triaged into an owning task or into debt. Nothing with a file and
line number stays in chat only. A finding leaves this file when its owning
task records it as a filed finding with a verdict.

Triage targets: N3 (privileged boundary, installer, registry, upgrade),
C6 (translator, framework), C7 (acceptance regressions), N6 (native runtime),
N5 (resources), N2 (ABI selection). Debt ledger: `PONYTAIL-DEBT.md`.

## 2026-10-26 ponytail review of HEAD `0ce08e8` (19 findings, all applied)

- RV1 privileged launch aborts (`src/uid_helper.zig`). Triaged N3. Applied, build evidence.
- RV2 up-navigation lands on wrong screen (`Activity.java`). Triaged C6. Applied, flow evidence.
- RV3 one-byte `_r_debug` (`bionic_shim.zig`). Triaged N6. Applied, build evidence.
- RV4 activity metadata lost (`IsakaiPackageManager.java`). Triaged C6. Applied, flow evidence.
- RV5 install checks one file, keeps another (also AU3). Triaged N3. Applied, build evidence.
- RV6 dangling metadata refs, dropped sizes. Triaged C6. Applied, flow evidence.
- RV7 `contains()` misses relative names. Triaged C6. Applied, flow evidence.
- RV8 night-mode theme and chaining. Triaged C6. Applied, flow evidence.
- RV9 stale dlerror, missing Android errors. Triaged N6. Applied, build evidence.
- RV10 64-slot library table. Triaged N6. Applied, build evidence.
- RV11 dialog buttons, missing Builder. Triaged C6. Applied, build evidence.
- RV12 keyboard-hidden flag never packed. Triaged N5. Applied, build evidence.
- RV13 native `Theme.setTo(null)` ignored. Triaged N5. Applied, build evidence.
- RV14 `reverse()` skips non-floats. Triaged C6. Applied, build evidence.
- RV15 press/release toggles. Triaged C6. Applied, build evidence.
- RV16 bare-hex resource ids. Triaged C6. Applied, flow evidence.
- RV17 asset reset without mark. Triaged C6. Applied, flow evidence.
- RV18 uncapped `--data` on unprivileged path. Triaged N3. Applied, build evidence.
- RV19 empty code dir yields host library path. Triaged N2. Applied, build evidence.

## 2026-10-26 ponytail audit, whole repo (20 findings, applied except one)

- AU1 privileged launch aborts (same defect as RV1). Triaged N3. Applied, build evidence.
- AU2 missing confinement warns and continues. Triaged N3. Applied, build evidence.
- AU3 signature checked on one file, bytes copied from another (same as RV5). Triaged N3. Applied, build evidence.
- AU4 uninstall fuzzy-matches. Triaged N3. Applied, build evidence.
- AU5 settings report saved when thrown away. Triaged C7. Applied, flow evidence.
- AU6 uninstall can kill the wrong process. Triaged N3. Applied, build evidence.
- AU7 native write past Java array. Triaged N6. Applied, build evidence.
- AU8 upgrade installs older builds. Triaged N3. Applied, build evidence.
- AU9 interrupted install bricks the APK. Triaged N3. Applied, build evidence.
- AU10 registry save loses data, then jams. Triaged N3. Applied, build evidence.
- AU11 uninstall deletes code before data. Triaged N3. Applied, build evidence.
- AU12 helper trusts path shapes. Triaged N3. Applied, build evidence.
- AU13 root fallback runs the dev binary. Triaged N3. Applied, build evidence.
- AU14 translator fails open. Triaged C6. Applied, build plus fresh-tree flow evidence.
- AU15 no method-size limit. Triaged C6. Applied, same evidence.
- AU16 native-library check reads 64 bytes. Triaged N2. Applied, build evidence.
- AU17 sqlite reads can't tell broken from empty. Triaged N6. Handles fixed; transaction wrap BLOCKED on the ContentProvider.java owner with recipe in `.audit/fixall.tsv`.
- AU18 input flood sticks keys. Triaged N6. Applied, build evidence.
- AU19 graphics buffer outlives memory, clipboard lies. Triaged N6. Applied, build evidence.
- AU20 two resource engines ship. Triaged N5. Gate failed honestly: pinned evidence runs on the staged parser, so the legacy engine stays. Decision recorded, not a fix.

## Debt watch

- `AnimatorInflater.loadStateListAnimator` shortcut marker with trigger intact. Ledger: `PONYTAIL-DEBT.md`.
