---
id: "N18"
status: "pending"
phase: "NATIVE_ASSETS"
priority: "P1"
size: "M"
updated: "2026-10-26"
owner: "unassigned"
next: "find a suitable existing NDK asset caller and await N6's verified loader contract; do not author a probe app"
---

# N18: Provide NDK asset ownership and fd/offset access

## Dependencies

- [N5](N5-resource-engine.md)
- [N6](N6-bionic-jni.md)

## Scope

Audit and complete the existing NDK AAssetManager and AAsset exports over the
resource engine. Define Java-to-native manager ownership, asset lifetime,
stream operations and uncompressed archive descriptors with real fd, offset
and length ownership. Existing exports do not by themselves establish caller
compatibility.
Keep graphics and native-window adoption in [N15](ROADMAP.md#N15). This task does not
depend on Skia or GPU support.

## Acceptance

- An existing same-ISA Android NDK application visibly uses the asset interface.
- Asset streams return the expected bytes. Uncompressed descriptor access returns
  a real owned fd with the correct APK offset and length. Compressed descriptor
  access reports the documented failure rather than fabricated availability.
- Repeated open, read and close operations preserve manager lifetime and release
  resources. The selected app passes under default isolation.
- Record the exact existing APK, ABI, actions, screenshots and limits. Java
  AssetFileDescriptor behavior alone does not establish NDK C ABI support.

## Planning boundary

On 2026-10-26 this scope was separated from N5 and N15. N5's former third
acceptance bullet required this native caller, while N15 depended on N5 and
N14. The resulting completion cycle delayed a resource adapter behind graphics
work. The acyclic order is resource engine and loader, then NDK assets, then
native windows and graphics. Earlier N5 investigations remain historical evidence.

## Verification evidence

Not recorded. Existing Puzzles libraries do not import the required AAsset APIs.

## Blockers

Requires an existing suitable NDK asset caller and N6's verified loader contract.
Do not author a substitute probe app or weaken the native acceptance to a Java
asset lookup.

## Code review

Pending implementation.
