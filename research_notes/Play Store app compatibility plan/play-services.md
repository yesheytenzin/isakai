# Google Play Services as compatibility gatekeeper

## What share of Play apps hard-require GMS, what breaks without it

### Takeaway
No public stat splits top apps into hard-require vs degrade-vs-crash; tracker prevalence (Firebase Analytics ~59%, AdMob ~43%, Crashlytics ~36% of analyzed apps) is an upper-bound exposure measure, not a breakage rate. Without GMS, push, Google sign-in, Maps/location, Play Billing, ads, and Integrity-gated features fail, with behavior ranging from graceful degradation (when apps check `isGooglePlayServicesAvailable()`) to crashes or refusals to run.

### Cited Findings
- Google Firebase Analytics is present in 182,079 analyzed apps (59%) — [Exodus Privacy](https://reports.exodus-privacy.eu.org/trackers/49)
- Google AdMob is present in 133,567 apps (43%) — [Exodus Privacy](https://reports.exodus-privacy.eu.org/trackers/312)
- Google CrashLytics is present in 112,863 apps (36%) — [Exodus Privacy](https://reports.exodus-privacy.eu.org/trackers/27)
- Google Analytics (legacy/mobile) is present in 31,767 apps (10%); Google Tag Manager in 26,436 apps (8%) — [Exodus Privacy](https://reports.exodus-privacy.eu.org/trackers/48); [Exodus Privacy](https://reports.exodus-privacy.eu.org/trackers/105)
- GMS architecture is a lightweight client library in the app plus the full implementation in the system Play services app, auto-updated independently of the OS — [Google](https://developers.google.com/android/guides/overview)
- Devices without the Play Store do not have Play services installed; Google's docs mandate checking `isGooglePlayServicesAvailable()` before using Google APIs — [Google](https://developers.google.com/android/guides/setup)
- Client libraries include helpers to gracefully handle missing/disabled/out-of-date service — [Google](https://developers.google.com/android/guides/overview)
- Without Play services, FCM token delivery fails and there is no push — [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client)
- Firebase Android SDKs have an explicit documented dependency on Google Play services; setup requires `google-services.json` plus the google-services Gradle plugin — [Firebase](https://firebase.google.com/docs/android/setup)
- Firebase "Sign in with Google" on Android is a GMS flow; without GMS apps must fall back to password/phone/OAuth/custom auth — [Firebase](https://firebase.google.com/docs/auth/android/google-signin)
- play-services-location, maps, ads, and ads-identifier are documented GMS runtime dependencies — [Google](https://developers.google.com/android/guides/setup)
- AdMob requires an App ID in `AndroidManifest.xml` plus `MobileAds.initialize()` at launch — [Google](https://developers.google.com/admob/android/quick-start)
- Play-distributed apps charging for downloads or in-app digital goods/subscriptions must use Play Billing (narrow exceptions); steering users to other payment methods is banned — [Google](https://support.google.com/googleplay/android-developer/answer/9858738)
- Play Integrity provides integrity verdicts (standard/classic requests, device recall, remediation dialogs); hard-gate apps refuse to run on failing verdicts, others degrade — [Google](https://developer.android.com/google/play/integrity)
- AppBrain aggregates library presence via its on-device detector app, split by category tags (Billing, Maps, Auth, Crash Reporting) — [AppBrain](https://www.appbrain.com/stats/libraries)

### Inferences
- Exodus/AppBrain percentages overstate hard dependence: embedding a tracker SDK is not the same as crashing without GMS. Treat ~59% Firebase / ~43% AdMob as the ceiling of GMS-exposed apps, not the share that break.
- Categories most likely to hard-fail without GMS: banking/finance (Integrity gates), apps using Google One-Tap/sign-in as sole login, paid/subscription apps (license checks via Play), and anything keyed to FCM for core function (ride-share, messaging).

### Gaps
- No reliable public stat for top-N Play apps split as hard-require vs check-and-degrade vs crash-without-GMS; Exodus measures corpus tracker prevalence, not runtime dependence.
- No citable aggregate for Play Integrity/SafetyNet adoption specifically in banking apps from primary sources.
- Current status of User Choice Billing / alternative-billing programs could not be confirmed (enrollment page 404s; policy page still references it) — needs Play Console or regional policy check.
- SafetyNet deprecation-timeline pages now 404; migration to Play Integrity is the stated path but the timeline must be cited from Play Integrity release notes.

## What microG covers vs not (push, maps, auth, integrity, billing)

### Takeaway
microG (latest stable v0.3.17.252432, 2026-09-29) fully covers push (FCM) and fused location, mostly covers Maps v2 (Mapbox build), account auth and Firebase Auth partially, billing with ongoing fixes — but Play Integrity/SafetyNet is effectively broken (Standard Integrity does not work; SafetyNet servers are offline), and ads/analytics are deliberately not implemented.

### Cited Findings
- Latest stable is v0.3.17.252432 published 2026-09-29 (security-fix release; parallel patch v0.3.16.1 the same day) — [GitHub API](https://api.github.com/repos/microg/GmsCore/releases/latest); [Wikipedia](https://en.wikipedia.org/wiki/MicroG)
- Firebase Cloud Messaging and fused location are rated Full with no crashing; UnifiedNlp handles network location — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status)
- Push needs no Google account; persistent `mtalk.google.com` connection with daily device registration — [microG wiki](https://github.com/microg/GmsCore/wiki/Helpful-Information); [microG wiki](https://github.com/microg/GmsCore/wiki/Google-Network-Connections); [CalyxOS](https://calyxos.org/docs/guide/microg/)
- Maps API v1 is Full; Maps API v2 (Mapbox build) is Mostly with minor glitches; the OSS Vtm build is Partial with many unimplemented APIs — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status)
- v0.3.17 Maps changes include tile overlays and Huawei Maps location fixes; on-device Maps Timeline history (opt-in) was added in v0.3.16 — [microG releases](https://github.com/microg/GmsCore/releases/tag/v0.3.17.252432); [microG releases](https://github.com/microg/GmsCore/releases/tag/v0.3.16.252432)
- Google account auth is Partial (regressions in 0.2.7 fixed in 0.2.8; reauth-handling fixes still landing in 0.3.17) — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status); [microG releases](https://github.com/microg/GmsCore/releases/tag/v0.3.17.252432)
- Firebase Auth is Partial: Anonymous, Email/Password, Phone, and Custom-Token login supported; GoogleSignIn and FirebaseUI NOT supported — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status)
- Play Integrity / SafetyNet is rated "Mostly" in the wiki table but contradicted by open issues: Standard Integrity does not work, only Classic checks — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status); [GmsCore issue #2856](https://github.com/microg/GmsCore/issues/2856)
- Further open Integrity breakage: "Play Integrity is broken", API error -100, "completely gone with Companion" — [GmsCore issue #2855](https://github.com/microg/GmsCore/issues/2855); [GmsCore issue #2729](https://github.com/microg/GmsCore/issues/2729); [GmsCore issue #3198](https://github.com/microg/GmsCore/issues/3198)
- Remote-DroidGuard fix for Integrity is unverified: Integrity uses multi-step DroidGuard vs microG's implemented single-step; possible fix in PR #3471 is NOT VERIFIED — [GmsCore issue #2851](https://github.com/microg/GmsCore/issues/2851)
- SafetyNet is effectively dead: formerly-responding servers are offline, self-test fails with quota errors — [GmsCore issue #3034](https://github.com/microg/GmsCore/issues/3034)
- In-app billing gets continuous fixes (protocol, callbacks, subscriptions, license verification v2) but billing failures remain open from 2024–2026 — [microG releases](https://github.com/microg/GmsCore/releases/tag/v0.3.17.252432); [GmsCore issue #3648](https://github.com/microg/GmsCore/issues/3648)
- Games is Partial; FIDO2/U2F is Mostly (USB/NFC keys only); Split Install is Partial; SMS Retriever and Play Asset Delivery are Mostly — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status)
- Deliberately not implemented: Analytics, Firebase Analytics, Mobile Ads — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status)
- No support: Android Auto, Fitness, downloadable Fonts, Geofencing, In-app updates, Wearable, Nearby (Nearby Share/Quick Share PRs #3933/#3940 still open/unmerged as of Oct 2026); Vision covers barcode scanning only — [microG wiki](https://github.com/microg/GmsCore/wiki/Implementation-status); [GmsCore issue #1272](https://github.com/microg/GmsCore/issues/1272)
- microG requires signature spoofing so GmsCore can stand in for official Play Services — [microG wiki](https://github.com/microg/GmsCore/wiki)
- microg.org now redirects to the GmsCore wiki; LineageOS for microG describes itself as feature-complete/maintenance mode — [microg.org](https://microg.org/) (verified 2026-10-29); [LineageOS for microG](https://lineage.microg.org/)

### Inferences
- microG is a working setup for push, location, maps display, Drive, and account-light apps, but aspirational for anything gated on Integrity verdicts, Google One-Tap sign-in, Cast, Auto, Wear, and Nearby Share.
- Apps embedding Firebase Analytics/AdMob will mostly still run under microG (those SDKs degrade), but anything enforcing a license or integrity verdict server-side will refuse.

### Gaps
- ML Kit support beyond barcode/face rows: no wiki row and no citable statement found.
- Firebase Analytics runtime behavior under microG ("Maybe crashing" in wiki with no linked issue): undocumented.
- Modern Google Sign-In/OAuth flows beyond the Firebase-Auth row: no dedicated primary-source statement found.

## Legal and technical requirements for real Play Services / passing Integrity on non-certified builds; sandboxed Play as current best practice

### Takeaway
Shipping genuine GMS requires an Android-compatible build (CDD + CTS pass) plus Google's proprietary license and Play Protect certification — unavailable to aftermarket OSes. Integrity's DEVICE/STRONG verdicts require certified images and (on Android 13+) locked bootloaders with hardware attestation, which cannot be spoofed durably. GrapheneOS's sandboxed Play — official Play binaries running as unprivileged apps — is the working best practice and achieves near-complete compatibility except inherently privileged functions and Integrity-gated verdicts.

### Cited Findings
- Android-compatible path: implement AOSP, comply with the CDD, pass CTS; then eligible to license GMS/Play Store and use the Android trademark — [AOSP](https://source.android.com/docs/compatibility/overview) (updated 2025-03-11)
- CTS is a free self-test suite run from a desktop against the device, meant for continuous build integration — [AOSP](https://source.android.com/docs/compatibility/cts)
- Play Protect certification means licensed proprietary Google apps plus passed compatibility testing; only certified devices may include Google apps including the Play Store — [Google](https://support.google.com/googleplay/answer/7165974)
- Google lists uncertified-device consequences: possibly insecure, missing updates, unlicensed/non-genuine Google apps, malfunctioning features, insecure backup — [Google](https://support.google.com/googleplay/answer/7165974)
- Certification is checked via Play Store profile > Settings > About; fix paths are locking the bootloader, unrooting, flashing a factory-signed build, updating Play services/Android — [Google](https://support.google.com/googleplay/answer/7165974)
- Play Integrity verifies a genuine Play-installed app on a genuine certified device, with server-side anti-abuse verdicts — [Google](https://developer.android.com/google/play/integrity/overview)
- Default device label `MEETS_DEVICE_INTEGRITY` means genuine + certified; on Android 13+ it requires hardware-backed locked-bootloader plus certified manufacturer image; empty means signs of attack/compromise or a non-passing emulator — [Google](https://developer.android.com/google/play/integrity/verdicts)
- Opt-in `MEETS_BASIC_INTEGRITY` is weak (bootloader may be unlocked; uncertified devices carry no assurances); `MEETS_STRONG_INTEGRITY` requires genuine + certified + recent patches (on 13+, DEVICE plus <1yr OS/vendor patches) — [Google](https://developer.android.com/google/play/integrity/verdicts)
- On Android 13+, BASIC/STRONG labels are returned only if the app itself is Play-licensed — [Google](https://developer.android.com/google/play/integrity/verdicts)
- Two request modes, same verdict format: Standard (~hundreds of ms, cached) vs Classic (seconds, fresh each time, for high-value actions) — [Google](https://developer.android.com/google/play/integrity/overview)
- Spoofing reality check: on Android 13+ ROMs even DEVICE integrity relies on locked-bootloader checks; recent-device fingerprints are only allowed with full hardware-backed attestation, and widely shared fingerprints get banned — [PlayIntegrityFork](https://github.com/osm0sis/playintegrityfork/blob/main/README.md)
- Consequence for non-certified/custom OS: fails MEETS_DEVICE/MEETS_STRONG (and the SafetyNet-era `ctsProfileMatch`) — synthesis of [Google verdicts](https://developer.android.com/google/play/integrity/verdicts) and [GrapheneOS](https://grapheneos.org/usage#banking-apps)
- Sandboxed Play is a compatibility layer running official Play releases in the standard app sandbox with zero special privileges, per-profile, opt-in — [GrapheneOS](https://grapheneos.org/usage#sandboxed-google-play)
- Near-complete compatibility including dynamite modules and modular components like Play Games; full Play Store support (in-app purchases, Asset/Feature Delivery, license checks) with standard user-consent install/update flows — [GrapheneOS](https://grapheneos.org/usage#sandboxed-google-play)
- Installed via GrapheneOS App Store (Play services + Play Store, interdependent); battery-optimization exception recommended for background push; sign-in optional except for account-dependent features — [GrapheneOS](https://grapheneos.org/usage#sandboxed-google-play)
- Location requests reroute to GrapheneOS's OS-level reimplementation by default; Google network location is an opt-in path — [GrapheneOS](https://grapheneos.org/usage#sandboxed-google-play-configuration)
- Inherently privileged functions cannot be supported in-sandbox; Android Auto needs a dedicated privileged-access extension (wired needs less than wireless); FIDO2 redirection not yet done — [GrapheneOS](https://grapheneos.org/usage#sandboxed-google-play-limitations); [GrapheneOS](https://grapheneos.org/usage#android-auto)
- GrapheneOS passes SafetyNet `basicIntegrity` but fails `ctsProfileMatch` (not Google-certified) and refuses to spoof checks as fragile; hardware attestation (mandatory since Android 8) ends the spoofing era — [GrapheneOS](https://grapheneos.org/usage#banking-apps)
- GrapheneOS directs developers to the raw hardware-attestation API with aftermarket-OS key allowlisting, which it argues gives higher assurance than Play Integrity without a Play-services dependency — [GrapheneOS](https://grapheneos.org/articles/attestation-compatibility-guide)

### Inferences
- Working setups: (1) certified stock OS with real GMS — everything passes; (2) GrapheneOS sandboxed Play — near-everything except Integrity-gated verdicts and privileged functions; (3) microG — push/location/maps tier only. Aspirational: passing DEVICE/STRONG verdicts on any non-certified build (requires leaked keys or vulnerabilities, fragile by design).
- For Isakai (non-certified by construction): sandboxed-Play-style unprivileged compat is the realistic ceiling; any plan depending on passing Integrity verdicts is not viable.

### Gaps
- Private MADA/GMS licensing terms are behind partner login; android.com/gms is marketing-only.
- CTS pass vs Google certification boundary details (Partner Approvals/keybox process) are behind login.
- SafetyNet-to-Integrity retirement dates were not stated on the fetched overview/verdicts pages.

## Games: Unity/Unreal/Godot export needs (billing, achievements, ads SDKs)

### Takeaway
All three engines route Play Billing, Play Games achievements/leaderboards, and AdMob through Google SDKs that need real GMS/Play Store presence: Unity via its IAP package and Firebase-for-Unity paths, Unreal via OnlineSubsystemGooglePlay, Godot only via third-party Android plugins (vanilla export has none of it built in).

### Cited Findings
- Unity IAP Google Play config: same Product ID in code (`ConfigurationBuilder.AddProduct`) and Play Console; types consumable / non-consumable / subscription; multi-quantity unsupported — [Unity](https://docs.unity3d.com/Packages/com.unity.purchasing@4.9/manual/UnityIAPGoogleConfiguration.html)
- Unity IAP test flow requires a signed non-development APK, stored keystore, Closed/Internal test track upload, and License Testing accounts — [Unity](https://docs.unity3d.com/Packages/com.unity.purchasing@4.9/manual/UnityIAPGoogleConfiguration.html)
- Firebase documents "Add Firebase to a game: Unity, C++" alongside Android setup — games reuse the same GMS/Firebase paths — [Firebase](https://firebase.google.com/docs/android/setup)
- Unreal's OnlineSubsystemGooglePlay plugin handles Play billing and Play Games Services sign-in, achievements/leaderboards via the Online Subsystem — [Epic](https://dev.epicgames.com/documentation/en-us/unreal-engine/online-subsystem-googleplay)
- Godot vanilla Android export covers only SDK/requirements/export — no built-in Play Billing, Play Games achievements/leaderboards, AdMob, or Firebase; all require Android plugins/add-ons — [Godot](https://docs.godotengine.org/en/stable/tutorials/export/exporting_for_android.html)
- Play Games support under sandboxed compat is explicitly called out: many Play games depend on Google Play Games being installed — [GrapheneOS](https://grapheneos.org/usage#sandboxed-google-play)
- Unity3d Ads appears in ~12% of analyzed apps (39,541), AppLovin ~12%, ironSource ~9% — ads SDKs are widespread but these particular ones are not Google-gated the way AdMob/Play Billing are — [Exodus Privacy](https://reports.exodus-privacy.eu.org/en/trackers/stats/)

### Inferences
- Unity/Unreal exports are the hardest to satisfy without real Play: billing test and license flows assume Play Console tracks and Play-installed packages. Under microG, games with license checks or Play Games sign-in as the only login will fail; under sandboxed Play they work (Play Games installable as a module).
- Godot games are the easiest: a vanilla export with no Google plugins has no GMS dependency at all; GMS dependence enters only when the developer adds billing/ads/Firebase plugins.

### Gaps
- Godot Google Play Billing community plugins could not be verified (two known repo URLs 404 via curl — renamed/moved); verify via Godot AssetLib or current maintainer fork before citing install steps.
- Play Games Services 2024–2025 sign-in changes could not be confirmed (developer.android.com/games/pgs pages 404 after docs restructure); the migration hub is the fallback source — [Google](https://developers.google.com/games/services/common/gds-migration-guide).
- Per-engine crash-vs-degrade matrix and top-100 billing/ads SDK penetration were not measured; skipped as beyond reach of primary sources.
