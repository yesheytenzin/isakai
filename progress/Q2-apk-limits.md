---
id: "Q2"
status: "done"
phase: "VERIFICATION"
priority: "P2"
size: "S"
updated: "2026-10-14"
owner: "unassigned"
next: "none — done; reopen only if a regression invalidates acceptance"
---

# Q2: Align APK limits and test ABI selection

## Dependencies

None.

## Code and documentation

- [src/install.zig](../src/install.zig)

## Acceptance

Boundary cases around 256 MiB and 1 GiB behave as documented; oversized entries reject. A mixed-ABI fixture stages only host libraries; ARM-only input reports NativeUnsupported. Verified via real install attempts and UI launches, not automated boundary harnesses.

## Verification evidence

### 2026-10-11 — passed

Streaming SHA-256 and consistent 256 MiB APK limit. Real CLI boundary tests cover sparse files around 256 MiB and 1 GiB, oversized entries/expanded totals, ARM-only rejection and mixed-ABI host-only staging.

Artifacts:

- [.cache/isakai/apk-limits.log](../.cache/isakai/apk-limits.log)

Command:

```sh
python3 verify-isakai/check-apk-limits.py
```

### 2026-10-14 — re-verified after review fixes (historical command retired)

Re-reviewed `src/install.zig` against current code. Limits remain inclusive as documented (exactly 256 MiB passes; exactly 1 GiB total passes), streaming SHA-256 aborts mid-stream, mixed-ABI stages host-only libraries and ARM-only input reports `NativeUnsupported`. Fixed material resource bugs: per-entry buffers in `inspect`/`stage` are now freed per iteration instead of held to function return, and `listEntries` validates sizes before duplicating the entry name so oversized inputs no longer leak the pending key.

Verified with existing commands only: `zig build` passes; `inspect-test` and `verify-test` read the pinned OpenCalc APK and report its package and signer fingerprint. The `check-apk-limits.py` command above is historical provenance (the harness was removed with the test suites); no new boundary harness was authored per the artifact policy.


## Code review

### 2026-10-14 — self-review complete

Reviewed `src/install.zig` bounds, hashing, ABI gating and staging for correctness, duplication, cleanup and error quality.

- Confirmed inclusive 256 MiB/1 GiB boundaries, streaming hash, host-only staging and loud ARM-only rejection.
- Fixed the `defer`-in-loop retention in `inspect`/`stage` and the `listEntries` error-path leak.
- Noted without change: oversized-entry vs expanded-total overflow share `EntryTooLarge` (no distinct `TotalTooLarge` variant added); errors carry no offending entry/ABI identity beyond the existing `appComponentFactory` context log; `stage` re-scans the ZIP after `inspect` and `install` inspects twice (correct but O(n²) with a post-stage re-hash mitigating the mutation window); lying-header over-limit decompression surfaces as `Truncated` rather than `EntryTooLarge`.

No material findings remain for this task.
