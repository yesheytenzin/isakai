# Pinned Android applications

This JSON block is the only authored APK-pin manifest. Fetch and verification tools read it through `tools/pinned_apps.py`; keep its order stable because the observer uses app indices. Task status and app acceptance evidence belong to [C7](C7.md), not this manifest.

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
  }
]
```

## Acceptance and verification

See [C1](C1.md) for pinning requirements and [C7](C7.md#app-specific-acceptance) for useful flows, failures and retained evidence.
