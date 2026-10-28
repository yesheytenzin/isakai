# PR1: sealed Kotlin discovery via RuntimeVisibleAnnotations (translator)

Goal in my own words: make reflective Kotlin sealed lookups work in translated
apps, so game discovery stops returning empty. Smallest change: emit
`RuntimeVisibleAnnotations` in the translator.

Branch: `pr1-sealed` (from main `22d5ad8`). File boundary honored:
`src/translate.zig` only, +424/-1. Nothing else in the tree is modified
except this record and `evidence/2026-10-28/pr1-sealed/`.

## Why (brief)

`BackendName.byLowerCase("cube")` returned null and `getAll()` returned `[]`
because translated `BackendName.class` carried zero class-level attributes, so
kotlin-reflect's `KClass.getSealedSubclasses()` found no `kotlin.Metadata` and
no JDK fallback. Sources: `reports/Remaining N6 N18 fix plan.md` (sealed
section) and `research_notes/Remaining N6 N18 fix plan/kotlin-sealed-metadata.md`.

## Diagnostic ladder (all executed, pre-fix)

1. `javap -v` on translated `BackendName.class`: only
   `ACC_PUBLIC, ACC_SUPER, ACC_ABSTRACT`; no `RuntimeVisibleAnnotations`, no
   `PermittedSubclasses`, no `InnerClasses`.
2. jshell on the translated classes: `isSealed()=false`,
   `getPermittedSubclasses()=null`, `getAnnotation(Metadata.class)=null`,
   `byLowerCase("cube")=null`, `getAll().size()=0`.
3. Source DEX (no `dexdump` on host; used a throwaway `/tmp` Python DEX
   reader, since deleted): `BackendName` has `annotations_off=0x3f33e8` with
   class annotations `Ldalvik/annotation/MemberClasses;` (visibility 2, BUILD)
   and `Lkotlin/Metadata;` (visibility 1, RUNTIME). Conclusion: translator
   bug, not an R8 keep-rule. Raw output saved as
   `evidence/2026-10-28/pr1-sealed/dexdump-substitute.txt`.

## Attacked premise: name relocation

The brief expected relocated nested names needing d1/d2 rewriting. Checked
directly instead of assuming: `descriptorToInternal` copies DEX descriptors
verbatim (`$` preserved), and the sealed subclasses live as same-package
top-level types in the DEX already (`backend/CUBE`, not `BackendName$CUBE`).
The emitted d2 names match the emitted class files exactly, so byte-faithful
copy needs zero name rewriting. No d1/d2 surgery was implemented.

## The change (src/translate.zig)

1. Emit `RuntimeVisibleAnnotations` on translated classes, carrying every
   runtime-visibility (1) DEX class annotation re-encoded to JVM form.
   Build/system visibilities (dalvik `MemberClasses`/`EnclosingClass`/
   `InnerClass`/`Signature`) stay out. Unrepresentable values (field/method
   refs, null) drop just their annotation with a debug print; malformed data
   is a translation error as elsewhere.
2. Found empirically: DEX enum values encode as the constant's `field_idx`
   (proven: `Retention(RUNTIME)` bytes resolve to field 9395 =
   `RetentionPolicy.CLASS`). First version read them as type+name and broke
   `Landroidx/annotation/Keep`; fixed and verified via `javap` showing
   `Retention(RUNTIME)` plus the six `ElementType` targets.
3. Found empirically: translated `kotlin.Metadata` failed `isAnnotation()`
   because `dexFlagsToJvm` dropped DEX `0x2000` (JVM `ACC_ANNOTATION`, same
   bit). Mapped it. Without this, `getAnnotation` silently drops every
   Metadata annotation whenever the translated stdlib shadows a host jar.
4. Found empirically: the DEX `Metadata` carries only (d1, d2, k, mv, xi);
   first reflective read throws `IncompleteAnnotationException: missing
   element pn`. The DEX holds no interface defaults (`methods_size=0` on
   `kotlin/Metadata`), so the copy is completed with upstream's documented
   defaults (read off kotlinc 2.4.21 bytecode: k=1, mv=[], bv=[1,0,3], d1=[],
   d2=[], xs="", pn="", xi=0). Compiler-emitted values are never altered.
5. New constant-pool `float` entry (JVM tag 4) for float annotation values.
   Recursion depth capped at 32; counts capped per repo convention.

Not done by design: `PermittedSubclasses`/`InnerClasses` synthesis (needs the
subclass list from the protobuf payload; not trivial, and unneeded since the
Kotlin primary path now works). `isSealed()` stays false;
`getPermittedSubclasses()` stays null. The `AlertDialogLayout` VerifyError is
a different PR.

## Verification evidence (real artifacts, final binary)

- `javap-BackendName.txt`: `RuntimeVisibleAnnotations` present,
  `kotlin.Metadata` with k=1, mv=[2,2,0], xi=48, bv=[1,0,3], xs="", pn="",
  and all 40 sealed subclass descriptors in d2.
- `jshell-sealed.txt`: `metadata=true`, `byLowerCase(cube)=cube` (was null),
  `getAll-size=40` with the full game set (was `[]`).
- `translate-summary.txt`: puzzles 118/3978 classes annotated, 0 failures;
  calculator flow tree 20/5080, matching the DEX census (21 runtime
  annotations on 20 classes; 10 `android/` platform classes skipped by
  design).
- `calculator-flow-pass.txt`: `PASS: calculator flow (isolated sway)` on a
  tree whose classes were retranslated at launch by the final binary (fresh
  app data, same-UID mode; settings/persistence/back/exit all asserted).
- `gates.txt`: `zig build` exit 0, `git diff --check` clean,
  `tools/progress-lint.py` 0 violations.

## Deviations and incidents (honest record)

- Fresh-tree install was impossible here: install/upgrade allocate system
  users via a pkexec helper and this environment has no privilege (baseline
  `git stash` build fails identically with `HelperFailed`). Regression used
  a copy of the existing `calc-u` tree with wiped code/data, retranslated at
  launch by the final binary. Same limitation applies to a fully fresh tree.
- Mid-task I ran `git stash` + baseline build to prove the install failure
  was environmental, then `stash pop` without rebuilding. The next flow PASS
  and scans silently used the stale baseline binary (0 annotations). Caught
  by the calculator census mismatch (expected 21, found 0), root-caused, and
  the full ladder plus the calculator flow were re-run on the final binary.
  Evidence files above are all from final-binary runs.
- Concurrency: another agent (PR4 notes) runs flows on this host; all my
  paths are uniquely suffixed (`-pr1`, `pr1-*`) and I touch only them.

## Review (self)

Scope kept to one file; no protobuf/Kotlin decoding beyond the eight
documented Metadata defaults; no caller changes needed (annotation emission
is per-class, pool registration precedes `emitPool`); error paths mirror
existing `Truncated`/`BadSection`/`BadDescriptor` conventions; owned IR is
freed on all paths including partial-array failures. No material findings
remain. No independent review was run.

Status: fixed, verified as above. Remaining known limitation: JDK-native
sealed path (`isSealed`/`getPermittedSubclasses`) still absent; Kotlin
reflective discovery, the broken path, is restored.
