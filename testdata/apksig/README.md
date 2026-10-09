# testdata/apksig — vendored v2 golden vectors

Source: GrapheneOS `platform_tools_apksig`, branch 17 (AOSP apksig test
resources):
https://github.com/GrapheneOS/platform_tools_apksig/tree/17/src/test/resources/com/android/apksig

(Re)populate with `tools/fetch-apksig-vectors.sh` (one-time network use).
The Zig suite (`src/apksig.zig`, "v2 golden vectors" test) reads these files
via `@embedFile` and is fully offline.

Positive vectors must verify; negatives must fail with the exact `SigError`
asserted in the test table. Known divergences from upstream apksig behavior:
- `v2-only-two-signers.apk` → `SignerFailed`: Isakai pins a single identity
  per app and rejects multi-signer APKs instead of verifying each signer.
- RSA-8192 / ECDSA P-521 vectors → `UnsupportedAlgorithm` (rather than a
  generic verify failure) so callers can tell "unsupported crypto" apart
  from "forgery".

Note: `src/testdata` is a symlink to `../testdata` so the `@embedFile`
suite in `src/apksig.zig` stays inside its package path. Do not replace
it with a real directory.
