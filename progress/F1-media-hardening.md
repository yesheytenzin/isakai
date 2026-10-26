---
id: "F1"
status: "deferred"
phase: "DEFERRED"
priority: "P2"
size: "L"
updated: "2026-10-14"
owner: "unassigned"
next: "no action while deferred; revisit only if the task is un-deferred"
---

# F1: Harden media for a selected app

## Dependencies

None.

## Code and documentation

- [framework/android/media/MediaPlayer.java](../framework/android/media/MediaPlayer.java)
- [framework/org/isakai/bridge/IsakaiMedia.java](../framework/org/isakai/bridge/IsakaiMedia.java)

## Delivery review details

Introduce asynchronous bounded playback beneath MediaPlayer/IsakaiMedia using GStreamer or FFmpeg plus host audio.

**Additional acceptance / stop gate:** 30-minute stream, pause/seek/loop/EOS/errors, timeout/cancel, bounded memory and confined access. Stop “MediaCodec supported” unless its own contract passes.

**Original planning estimate:** 3–6 weeks for a selected audio/video app.


## Acceptance

Long audio plays with bounded memory; looping repeats; pause/seek/resume work; a hung decoder times out; errors propagate; the same flow passes under default isolation.

## Verification evidence

### 2026-10-12 — partial

Confirmed defect fixed: IsakaiMedia read stdout and stderr to EOF before waitFor, so a decoder that never wrote could not be timed out. All host processes now go through one supervisor with the deadline started before the wait, concurrent bounded pipe draining (stdout capped and fatal on overflow, stderr keeps the tail), forced kill plus reap on timeout/interrupt, and stdin closed so a decoder cannot block on input. Regression: org.isakai.bridge.HostProcessTest (hung child times out, pipes separated, stderr tail cap, exit text, overflow kill, stdin EOF). Streaming PCM playback/state machine and content URIs remain open.

Artifacts:

- `/tmp/isakai-checks-final/java.log`

Command:

```sh
zig build test-java
```


## Code review

Not recorded.
