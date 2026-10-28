# Kotlin sealed-class metadata preservation for bytecode translators

## What does kotlin.Metadata contain for sealed classes, and how does the Kotlin runtime use it for getSealedSubclasses()?

### Takeaway
The sealed-subclass list is stored inside the `kotlin.Metadata` annotation payload (protobuf field `sealed_subclass_fq_name`), and `kotlin-reflect`'s `KClass.getSealedSubclasses()` reads it from there first — so dropping `kotlin.Metadata` removes the primary discovery path entirely.

### Cited Findings
- `kotlin.Metadata` is a `@Retention(RUNTIME)`, `@Target(CLASS)` annotation with short JVM element names (`k`=kind, `mv`=metadata version, `bv`=bytecode version, `d1`/`d2`=payload strings, `xs`=extra string, `pn`=package name, `xi`=extra int); kind=1 means Class — [Metadata.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/libraries/stdlib/jvm/runtime/kotlin/Metadata.kt)
- The metadata protobuf `Class` message carries `repeated int32 sealed_subclass_fq_name = 16`, i.e. the fully-qualified names of direct sealed subclasses are part of the `d1`/`d2` payload — [metadata.proto](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/metadata/src/metadata.proto)
- `kotlin-reflect` is "based on `kotlin-metadata-jvm` and Java reflection objects. It reads Kotlin metadata from the classes being inspected" — [reflection.jvm ReadMe](https://github.com/jetbrains/kotlin/blob/master/core/reflection.jvm/ReadMe.md)
- `KClassImpl` resolves `sealedSubclasses` as: if `kmClass != null` (parsed from `@Metadata`), use `kmClass.sealedSubclasses.mapNotNull(classLoader::loadKClass)`; else if `Java16SealedRecordLoader.loadIsSealed(jClass)` then JDK `getPermittedSubclasses`; else `emptyList()` — [KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt)
- `kmClass` itself is null when the class has no `@Metadata` annotation (`jClass.getAnnotation(Metadata::class.java)` returns null and the class is neither synthetic nor builtin) — [KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt)
- The supported entry point for parsing the annotation is `KotlinClassMetadata.readStrict` / `readLenient` on the `kotlin.Metadata` annotation instance — [kotlinx-metadata ReadMe](https://github.com/jetbrains/kotlin/blob/master/libraries/kotlinx-metadata/jvm/ReadMe.md)
- Language-level contract: all direct subclasses of a sealed class are known at compile time and must live in the same package and module — [Kotlin docs](https://kotlinlang.org/docs/sealed-classes.html)

### Inferences
- With `kotlin.Metadata` dropped, `kmClass` is null, so `getSealedSubclasses()` can only succeed via the JDK-16 fallback; if that also fails, it returns `emptyList()` (not an exception), which is why `getAll()` silently returns `[]` and `byLowerCase()` returns null.
- A `when` over the hierarchy still works without metadata (plain `instanceof` chain), which is why only the reflective `getSealedSubclasses()` path breaks.

### Gaps
- The exact `Java16SealedRecordLoader` source file was not located (name absent from `core/reflection.jvm/.../internal/` listing); its reflective use of `Class.isSealed`/`getPermittedSubclasses` is inferred from call sites, not read.
- Whether `KmClass.sealedSubclasses` includes indirect subclasses or only direct ones was not verified against source (kotlinx-metadata repo layout has moved; raw-file guesses 404'd).

## What class-file attributes/annotations must be present (RuntimeVisibleAnnotations, InnerClasses, PermittedSubclasses for Java 17+ sealed)?

### Takeaway
Three independent pieces matter: `RuntimeVisibleAnnotations` carrying `kotlin.Metadata` (the Kotlin path), `InnerClasses`/`EnclosingMethod` (nested-class structure), and `PermittedSubclasses` (the JVM-17 native sealed path that backs `Class.isSealed()`/`getPermittedSubclasses()`). kotlinc only emits `PermittedSubclasses` when targeting JVM 17+.

### Cited Findings
- kotlinc's JVM backend emits the attribute per subclass via `visitPermittedSubclass`, and only when the language feature `JvmPermittedSubclassesAttributeForSealed` is enabled, the class modality is SEALED, and the target is `>= JVM_17` — [ClassCodegen.kt](https://github.com/jetbrains/kotlin/blob/master/compiler/ir/backend.jvm/codegen/src/org/jetbrains/kotlin/backend/jvm/codegen/ClassCodegen.kt)
- JVMS §4.7.31: `PermittedSubclasses` is a ClassFile attribute (`attribute_name_index` = "PermittedSubclasses") whose `classes[]` entries are `CONSTANT_Class_info` indexes authorizing direct subclasses; it "is consulted when a class or interface is created that attempts to directly extend or implement" the sealed class (§5.3.5) — [JVMS SE 17, §4.7](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html)
- `Class.getPermittedSubclasses()` returns "an array ... if it is sealed", "empty if this sealed class or interface has no permitted subclass", and `null` "if ... `isSealed()` returns `false`" — [Class javadoc (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- `Class.isSealed()` "returns true if and only if this Class object represents a sealed class or interface" — [Class javadoc (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- `KClassImpl.modality` also falls back without metadata: `Java16SealedRecordLoader.loadIsSealed(jClass) == true -> Modality.SEALED`, so `isSealed` on the Kotlin side follows the same attribute — [KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt)

### Inferences
- For a JVM-17 translator target, emitting `PermittedSubclasses` (with the relocated subclass binary names) plus `InnerClasses` restores the *fallback* path even without `kotlin.Metadata`; but copying `kotlin.Metadata` restores the *primary* path and is version-independent (annotations work at any class-file version).
- `InnerClasses` is likely needed for full fidelity (nested `BackendName$CUBE` structure, `getDeclaringClass`), but loading subclasses by binary name via the metadata FQ-name list does not strictly require it — inference, unverified.
- Our translator emits major v49 with **zero** class-level attributes (`writeU16(0); // class attributes` in `src/translate.zig`), so none of the three pieces survive today (project observation, `src/translate.zig` near class emission; `grep -ri sealed|Metadata src/ tools/` empty per `progress/reports/WORKER-n6.md` §4).

### Gaps
- Whether HotSpot honors `PermittedSubclasses` on a pre-61 (e.g. v49/v61-mismatch) class file was not verified — JVMS ties the attribute to Java 17 but does not explicitly state a minimum class-file version for parsing it. If the translator stays at v49, this must be tested empirically.
- How the source DEX encodes the data needed to rebuild `InnerClasses` (vs. ART's own inner-class representation) was not checked against the DEX spec — needs a `dexdump` of the app's `BackendName` DEX entry.

## What minimal translator change restores sealed discovery without full Kotlin support?

### Takeaway
Copy the `kotlin.Metadata` annotation bytes from the DEX class annotations to the output `RuntimeVisibleAnnotations` — a byte-preserving copy needs no Kotlin knowledge and restores the primary `getSealedSubclasses()` path; optionally synthesize `PermittedSubclasses` for the JDK-native path.

### Cited Findings
- Since `getSealedSubclasses()` prefers `kmClass.sealedSubclasses` whenever `@Metadata` parses, a faithful annotation copy alone is sufficient for the Kotlin path — no protobuf decoding required — [KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt)
- `KotlinClassMetadata.readLenient` (used by `KClassImpl`, not strict) tolerates version skew, so copied metadata stays readable even if the runtime's Kotlin version differs slightly from the app's — [KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt) with lenient/strict distinction per [kotlinx-metadata ReadMe](https://github.com/jetbrains/kotlin/blob/master/libraries/kotlinx-metadata/jvm/ReadMe.md)
- Subclass entries in the metadata are resolved with `classLoader::loadKClass`, so the copied payload works only if the subclass names it names are the *relocated* names the translator actually emits — [KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt)
- If annotation copying is infeasible, the fallback needs `isSealed()==true` + `getPermittedSubclasses()`, i.e. a real `PermittedSubclasses` attribute naming each direct subclass — [Class javadoc (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)

### Inferences
- Minimal change, in order: (1) emit `RuntimeVisibleAnnotations` on classes, copying all runtime-visibility DEX annotations verbatim (fixes `kotlin.Metadata` and any other Kotlin/Java annotation in one move); (2) if names are relocated, the `d1`/`d2` string payload must be rewritten to the relocated binary names, otherwise `loadKClass` fails and the entries are skipped via `mapNotNull` (silent empty again); (3) optionally emit `PermittedSubclasses` + `InnerClasses` for the JDK-native path.
- Step (2) is the subtle one: `mapNotNull` means stale names fail silently, reproducing the exact symptom. Name rewriting requires parsing the annotation's string arrays but not full protobuf semantics.
- Emitting `PermittedSubclasses` without also making the class `abstract`+matching JVM sealed-enforcement rules (§5.3.5) could cause linkage errors if a listed subclass is missing; copying metadata has no such verification side effects.

### Gaps
- Not verified whether the app's DEX still contains `kotlin.Metadata` (R8/full-mode can strip it with `-keepattributes` missing). If the DEX itself lacks it, the translator cannot copy it and the fix moves to keep-rules or attribute synthesis — first diagnostic step.
- The translator's annotation pipeline does not exist yet (`src/translate.zig` writes `0` class attributes; `src/dex.zig` annotation parsing coverage unknown) — effort estimate needs the translator owner's read.

## How to diagnose: byLowerCase returns null + getAll returns empty — what to inspect in the translated classes?

### Takeaway
Run `javap -v` on the translated `BackendName.class` and check for the three missing pieces (`kotlin.Metadata` in `RuntimeVisibleAnnotations`, `InnerClasses`, `PermittedSubclasses`); confirm with jshell probes of `getAnnotation(Metadata)`, `isSealed()`, and `getPermittedSubclasses()`.

### Cited Findings
- Known-bad signature from this repo: translated `BackendName.class` shows only `ACC_PUBLIC, ACC_SUPER, ACC_ABSTRACT`, "no `kotlin.Metadata` annotation and no sealed/`PermittedSubclasses` info", with subclasses "relocated top-level classes"; jshell proves `byLowerCase("cube") -> null`, `getAll() -> []` (`progress/reports/WORKER-n6.md` §4).
- Each runtime question maps to one official API: `@Metadata` presence → `Class.getAnnotation(Metadata.class)`; JVM sealed status → `Class.isSealed()` ([javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)); permitted list → `Class.getPermittedSubclasses()` (null = not sealed, empty = sealed with none) ([javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)); Kotlin view → `KClass.getSealedSubclasses()` ([KClassImpl.kt](https://raw.githubusercontent.com/JetBrains/kotlin/master/core/reflection.jvm/src/kotlin/reflect/jvm/internal/KClassImpl.kt)).
- `InnerClasses` vs flat relocation is visible in `javap -v` (`InnerClasses:` table vs absent) and in whether subclass files are named `BackendName$CUBE.class` or relocated top-level names.

### Inferences
- Diagnostic ladder: (1) `javap -v BackendName.class | grep -A5 'RuntimeVisibleAnnotations\|PermittedSubclasses\|InnerClasses'` — if all absent, cause found; (2) jshell: `isSealed()` false + `getPermittedSubclasses()` null ⇒ JDK path dead; (3) `dexdump` the source DEX `BackendName` class for `Lkotlin/Metadata;` — present in DEX but absent in class ⇒ translator dropped it; absent in DEX too ⇒ R8 stripped it, fix is a keep-rule, not the translator; (4) if `@Metadata` IS present but subclasses still empty, compare the `d1` FQ names against actual emitted class names (relocation mismatch → `mapNotNull` drops them).
- `BackendName$$ExternalSyntheticLambda0` (seen in the worker report) is a D8-synthetic lambda, not a sealed subclass — do not count it when enumerating expected subclasses.

### Gaps
- No verified dump of the app's DEX `BackendName` annotations exists yet; step (3) above is unexecuted.
- The exact expected subclass set (CUBE, etc.) and their original nesting (nested `$` vs same-package top-level, cf. [Kotlin docs](https://kotlinlang.org/docs/sealed-classes.html) allowing both) was not enumerated from the APK.
