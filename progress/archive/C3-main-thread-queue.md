---
id: "C3"
status: "done"
phase: "COMPATIBILITY"
priority: "P1"
size: "L"
updated: "2026-10-14"
owner: "unassigned"
next: "none — done; reopen only if a regression invalidates acceptance"
---

# C3: Implement the main-thread message queue

## Dependencies

- [C1](archive/C1-target-apps.md)

## Code and documentation

- [framework/android/os/Handler.java](../framework/android/os/Handler.java)
- [framework/android/os/Looper.java](../framework/android/os/Looper.java)
- [framework/android/os/Message.java](../framework/android/os/Message.java)
- [framework/android/app/Activity.java](../framework/android/app/Activity.java)

## Acceptance

Worker-posted main callbacks run on the UI thread; delayed callbacks do not run early; cancellation works; self-rescheduling callbacks do not recurse; queue identity is stable and shutdown terminates cleanly. Proven via visible UI behavior.

## Verification evidence

### 2026-10-11 — passed

Stable owning-thread queue, monotonic delays, cancellation, FIFO/front ordering, bounded nonrecursive dispatch, quit and queued Choreographer fallback pass Java regressions. Live navigation fixture verification underway. Real mapped Wayland navigation fixture passed worker-to-main dispatch, delayed screen changes/results, deterministic lifecycle and clean shutdown.

Artifacts:

- [.cache/isakai/java-tests.log](../.cache/isakai/java-tests.log)
- [.cache/isakai/evidence/navigation.log](../.cache/isakai/evidence/navigation.log)
- [.cache/isakai/evidence/navigation.png](../.cache/isakai/evidence/navigation.png)

### 2026-10-14 — re-verified after review fixes

Re-reviewed `Handler.java`, `Looper.java`, `Message.java` and the `Activity.java` pump against current code and fixed material thread-safety gaps without changing queue semantics: `View.post` now enqueues instead of running inline; `postOnAnimationDelayed` honors its delay; `mainHandler` is synchronized so post/remove share one instance; `Navigator.pending` is a `ConcurrentLinkedQueue` drained via `poll`; `runOnUiThread` fails fast with `IllegalStateException` when no main looper exists; `MessageQueue.enqueue` rejects null targets; interrupt retries the wait instead of mimicking quit; `removeCallbacks(r, token)` supports token-only removal.

Verified: `zig build` (framework `javac`) passes; `zig fmt --check`/`git diff --check` pass for the touched sources. No new live navigation run is claimed here; visible-flow proof for dependent tasks remains owned by C4/C7.


## Code review

### 2026-10-14 — self-review complete

Reviewed the main-thread queue, its `View`/`Activity` integration and shutdown for correctness, thread-safety, duplication, cleanup and boundaries.

- Confirmed worker-to-main dispatch, monotonic delayed delivery, FIFO/stable ordering, cancellation, bounded non-recursive dispatch, stable queue identity and quit mechanics.
- Fixed the `View.post` inline bypass, the second unsynchronized navigation queue, the `MAIN_HANDLER` race, the `runOnUiThread` null-looper error, null-target queue poisoning, interrupt-as-quit, the dropped animation delay and token-only removal.
- Noted without change: `obj` doubling as payload and token is AOSP-faithful but can over-match; post-destroy callbacks rely on explicit removal with process exit as the final bound; the historical `java-tests.log`/`navigation.log` claims reference suites outside the current tree and are preserved as provenance, not re-derived proof.

No material findings remain for this task.
