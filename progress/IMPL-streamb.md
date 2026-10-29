# Stream B: host-backed media decode spike (2026-10-29)

Goal in my own words: prove host-backed media decode through a
MediaCodec-shaped API on one real format, spike scope, one format end
to end. No product code, no per-app branches, MediaDrm stays closed.

## Contract surface mapped

- `android.media.MediaCodec`: did not exist. Added as a synchronous
  software decoder fronting host ffmpeg.
- `android.media.MediaExtractor`: was a shell returning zero tracks.
  Now reports ffprobe-discovered tracks. Sample transport still absent.
- `android.media.MediaFormat`: map shell; gained `getLong`/`setLong`
  for `durationUs`.
- `android.media.MediaMetadataRetriever`: already real via
  ffprobe/ffmpeg. Untouched.
- `android.media.MediaPlayer`: already audio-only via ffmpeg.
  Untouched.
- `android.media.MediaDrm`/`MediaCrypto`: no `MediaDrm` class exists
  and none was added. `MediaCrypto` added as a constructor-throwing
  shell so the DRM boundary fails closed and loudly.
- `android.view.Surface`: did not exist; added as a non-instantiable
  shell so `MediaCodec.configure` keeps its real signature.
- `org.isakai.bridge.IsakaiMedia`: gained a shared stdin-feeding
  process supervisor plus `probeTracks`, `decodeVideoEs`,
  `decodeAudioEs`. Existing audio/frame/probe behavior untouched.

## What decodes, with proof

- H.264: an 11,507-byte annex-B stream (320x240, 60 frames, generated
  by ffmpeg testsrc) queued through `createDecoderByType("video/avc")`,
  `configure`, `start`, `dequeueInputBuffer`/`queueInputBuffer`,
  `dequeueOutputBuffer` yields 60 yuv420p frames of 115,200 bytes each
  followed by end-of-stream. First call returns
  `INFO_OUTPUT_FORMAT_CHANGED` and `getOutputFormat` reports
  `video/avc` 320x240.
- AAC: a 31,369-byte ADTS stream through
  `createDecoderByType("audio/mp4a-latm")` yields 360,448 PCM bytes
  (S16LE stereo 44100), byte-identical to the direct ffmpeg reference
  run, followed by end-of-stream.
- Discovery: `MediaExtractor` reports the mp4 as one `video/avc`
  320x240 track with duration 2,000,000 us and the m4a as one
  `audio/mp4a-latm` 44100 Hz track.
- DRM: `new MediaCrypto(uuid, initData)` throws
  `UnsupportedOperationException`.

Evidence: `evidence/2026-10-29/streamb/` holds `codecs.txt` (ffmpeg
n9.0.2, ffprobe n9.0.2, GStreamer 1.28.8 plus decoder inventories),
`decode.log` (full driver transcript ending in `DRIVE_OK`),
`frame-h264-0.png` and `frame-h264-30.png` (320x240 each, distinct
md5, converted from dumped output buffers 0 and 30), and `api-gap.md`
(the ten remaining gaps).

## Spike limits (kept, documented in code)

Whole-input decode on end-of-stream (no streaming latency or memory
bound beyond the 15 s supervisor and 256 MB cap), no Surface render,
no extractor sample reads (codec fed from elementary-stream files),
byte-proportional output timestamps, fixed audio output layout.

## Verification evidence

- Driver: `/tmp/streamb/Drive.java` (throwaway, deleted after the run
  per the no-probes rule; flow reproducible from the commands below).
  Compiled against `zig-out/framework`, ran on the host JVM with the
  stream-B env dirs set, exit 0, transcript in `decode.log`.
- Reproduce: generate samples with `ffmpeg -f lavfi -i
  testsrc=size=320x240:rate=30:duration=2 -c:v libx264 -pix_fmt
  yuv420p -f h264 sample.h264` (mp4 container likewise) and `ffmpeg
  -f lavfi -i sine=frequency=440:duration=2 -c:a aac -f adts
  sample.aac`, then drive the `MediaCodec`/`MediaExtractor` call
  sequence above and compare frame counts, sizes, and the audio byte
  count against `decode.log`.
- Gates: `zig build` exit 0 before (main) and after (twice, final run
  silent exit 0); `git diff --check` clean; no sleeps; no compositor
  fixture started; `/tmp/streamb`, `/tmp/streamb-build*.log` removed.
- Why no app flow: `verify-isakai/SKILL.md` flows drive windowed apps
  through the compositor; this spike has no UI surface, so proof is the
  real framework classes decoding real bytes on the host JVM, not a
  window screenshot. No test files or helpers remain in the tree.

## Code review (self)

Scope checked: five framework files plus three `build.zig` lines; no
caller of the old zero-track extractor existed, so discovery changes
break nothing; `MediaPlayer`/`MediaMetadataRetriever` paths untouched.
One real bug found by running, not by reading: ffprobe csv drops empty
columns, so positional parsing silently found zero tracks; fixed by
switching `probeTracks` to flat-format parsing and re-proving with the
same driver. No material findings remain; independent review still
wanted per program rules.

## Status

Done on branch `streamb`. Remaining work is the ten items in
`evidence/2026-10-29/streamb/api-gap.md`.
