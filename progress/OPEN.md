# Open blockers and constraints

Only what is not done. Completed tasks are not listed here; their files
remain the record. Causes and next actions live in the owning task —
this page only points at them.

## Blocked or in progress

- [N5](N5-resource-engine.md#blockers) (`in_progress`) — acceptance
  boundary is the C8 AssetFileDescriptor repair review plus actual caller
  evidence or a recorded verification limit.
- [N6](N6-bionic-jni.md#blockers) (`blocked`) — needs default-isolated
  verification of thread/callback/GC/exception behavior plus the
  loader-contract follow-ups.

## Constrained (cannot start yet)

- [N18](N18-ndk-assets.md#blockers) (`pending`) — needs an existing NDK
  asset caller and N6's verified loader contract; no probe apps.
- N7–N17 except N18 (`pending`) — start per acceptance criteria when
  their phase opens; no action now.
- F1–F5 (`deferred`) — revisit only if explicitly promoted.

## Open follow-ups inside done tasks

- Translated-runtime arrow + stale duplicates: exact emitters recorded,
  needs instrumented runtime work —
  [C6](C6-translator-framework-failures.md), [WORKER-comprun](WORKER-comprun.md).
- FAB `srcCompat` plus icon: res-layer ownership —
  [C6](C6-translator-framework-failures.md), [WORKER-canvasfilter](WORKER-canvasfilter.md).
- Notes body-save residual flake: gate fixed, timing flake remains —
  [C6](C6-translator-framework-failures.md), [WORKER-notesave](WORKER-notesave.md).
- File-picker cancel end-to-end proof: needs a portal-capable fixture —
  [C6](C6-translator-framework-failures.md), [WORKER-compose](WORKER-compose.md).
- Privileged per-app pinned runs: optional, gate already met —
  [C7](C7-acceptance-gate.md).
- Non-self review and XKB stress: need a human / policy exception —
  [C6](C6-translator-framework-failures.md).
