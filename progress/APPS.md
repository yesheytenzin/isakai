# Pinned Android applications

This JSON block is the only authored APK-pin manifest. Fetch and verification tools read it through `tools/pinned_apps.py`; keep its order stable because the observer uses app indices. Task status and app acceptance evidence belong to [PROGRESS.md](../PROGRESS.md), not this manifest.

```json
[
  {
    "name": "OpenCalc",
    "role": "candidate",
    "version": "v3.2.1",
    "package": "com.darkempire78.opencalculator",
    "source_url": "https://github.com/clementwzk/OpenCalc/releases/download/v3.2.1/OpenCalc.v3.2.1.apk",
    "sha256": "43b6befd1fecc686ba1611bbda18896cc26d966047e788be995def569a4510f3",
    "license": "GPL-3.0",
    "license_url": "https://github.com/clementwzk/OpenCalc/blob/main/LICENSE",
    "artifact": ".cache/isakai/apks/OpenCalc.v3.2.1.apk"
  },
  {
    "name": "Simple Calculator",
    "role": "candidate",
    "version": "5.12.0",
    "package": "com.simplemobiletools.calculator",
    "source_url": "https://github.com/SimpleMobileTools/Simple-Calculator/releases/download/5.12.0/calculator-fdroid-release.apk",
    "sha256": "092629deaeaaea824dbf0cd824989e80b498b1fa07ad5b4c3e744fc64ce5ab2f",
    "license": "GPL-3.0",
    "license_url": "https://github.com/SimpleMobileTools/Simple-Calculator/blob/master/LICENSE",
    "artifact": ".cache/isakai/apks/calculator-fdroid-release.apk"
  },
  {
    "name": "Simple Notes",
    "role": "candidate",
    "version": "6.17.0",
    "package": "com.simplemobiletools.notes.pro",
    "source_url": "https://github.com/SimpleMobileTools/Simple-Notes/releases/download/6.17.0/notes-fdroid-release.apk",
    "sha256": "d3064ad405e3488d0b4676dd547d9ea2d337fcc5def2a91dd09974ece50b0575",
    "license": "GPL-3.0",
    "license_url": "https://github.com/SimpleMobileTools/Simple-Notes/blob/master/LICENSE",
    "artifact": ".cache/isakai/apks/notes-fdroid-release.apk"
  },
  {
    "name": "Simon Tatham's Puzzles",
    "role": "native-candidate",
    "version": "149 (2025-09-12-1919-23762278-fdroid)",
    "package": "name.boyle.chris.sgtpuzzles",
    "source_url": "https://f-droid.org/repo/name.boyle.chris.sgtpuzzles_149.apk",
    "sha256": "6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4",
    "license": "MIT",
    "license_url": "https://github.com/chrisboyle/sgtpuzzles/blob/main/LICENCE",
    "artifact": ".cache/isakai/apks/sgtpuzzles_149.apk"
  }
]
```

## Acceptance and verification

See [PROGRESS.md](../PROGRESS.md) for useful flows, failures and retained evidence.

Indices 0–2 are the M1 three-app acceptance set (C7). Index 3, Simon Tatham's
Puzzles (`role: native-candidate`), is the same-ISA native target for
the native and resource-engine work: a clean x86_64 NDK app whose `lib/x86_64/libpuzzles.so`
is a genuine JNI library (`JNI_OnLoad` + 37 `Java_*` natives, native→Java 2D
Canvas callbacks, no EGL). It is not part of the C7 gate.
