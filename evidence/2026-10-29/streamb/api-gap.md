# Stream B API gap list (2026-10-29)

What the spike covers: `MediaCodec` sync decode for `video/avc` and
`audio/mp4a-latm`, `MediaExtractor` track discovery, `MediaDrm`
unavailable. Everything below is still missing.

1. Codec breadth: only `video/avc` and `audio/mp4a-latm` construct.
   HEVC, VP9, AV1, MP3, Opus, Vorbis, FLAC all throw
   `IllegalArgumentException` at `createDecoderByType`, although host
   ffmpeg ships software decoders for every one (see `codecs.txt`).
2. Surface rendering: `configure` with a non-null `Surface` throws.
   `releaseOutputBuffer(idx, true)` throws. No path from decoded frame
   to a compositor surface; frames can only be dumped.
3. Streaming decode: input accumulates until end-of-stream, then one
   ffmpeg run decodes everything. No per-NAL latency, no bounded
   memory, no hung-decoder timeout on the decode leg (the 15 s process
   supervisor from `IsakaiMedia` still applies).
4. Extractor sample transport: `readSampleData`, `getSampleTime`,
   `getSampleTrackIndex`, `advance` do not exist. Codec input for the
   spike comes from elementary-stream files, not from the container
   through the extractor.
5. Seek: `MediaExtractor.seekTo` is a no-op; `MediaCodec.flush` clears
   state but there is no keyframe-accurate seek behind it.
6. Async API: `setCallback`, `signalEndOfInputStream`,
   `setOnFrameRenderedListener`, persistent-surface and tunneled
   playback are absent.
7. Encoder path: `CONFIGURE_FLAG_ENCODE` throws. No
   `createEncoderByType`.
8. DRM: no `MediaDrm` class at all, so ClearKey is unwired too.
   `MediaCrypto` construction always throws. This matches the plan
   boundary (MediaDrm unavailable outside ClearKey) with the stricter
   spike reading that it is unavailable inside ClearKey as well.
9. Timestamps: output `presentationTimeUs` is byte-proportional
   interpolation across the queued range, exact only for CBR input.
10. Output detail: video buffers are bare yuv420p with no stride,
    crop, color-space or HDR metadata; audio is fixed S16LE stereo
    44100 regardless of source layout.
