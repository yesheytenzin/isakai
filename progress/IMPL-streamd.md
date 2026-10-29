# Stream D: microG-tier framework gap survey (survey only, no code)

Goal in own words: name every microG-tier API the framework lacks, with exact
file pointers, so the services phase has a punch list. Survey only.

Sources: `reports/Play Store app compatibility plan.md` (Play-services
section), `research_notes/Play Store app compatibility plan/play-services.md`,
`PROGRESS.md` rules (real-app verification only, no test files).

## Method

For each of push registration plus delivery, maps display, fused location,
and basic auth: listed the client API an app calls, then checked framework
code for it. Status is present, stubbed (compiles, degrades at runtime), or
missing (no class, no branch, `NameNotFoundException`, or hard `false`).
Line counts below are `wc -l` totals; line refs are the deciding lines.

## Gap table

### 1. Push registration plus delivery (rank 1 by unlock count)

FCM underpins messaging and ride-share cores plus degradation paths in a
large share of networked apps. microG rates FCM Full. Framework status:

| Need | Status | Pointer |
|---|---|---|
| `com.google.android.gms` / `com.google.firebase` client classes | missing, no shim tree | `find framework -path "*com/google*"` empty; no `com.google` refs in `framework/`, `src/` |
| GMS package presence (`com.google.android.gms` query) | missing, throws | `framework/android/content/IsakaiPackageManager.java:178-180` (`getPackageInfo` throws `NameNotFoundException` unless own package); `framework/android/content/pm/PackageManager.java:26-27` base throws |
| Service availability check target (`isGooglePlayServicesAvailable`) | missing, nothing to answer it | same package-query pointers; `queryIntentServices` empty at `framework/android/content/pm/PackageManager.java:68-70` |
| FCM token registration (`FirebaseMessaging.getToken`) | missing | no class; consequence of missing client tree above |
| Push transport (persistent connection, daily registration) | missing | no service, no receiver path; no file |
| Push display surface (`NotificationManager.notify`) | present | `framework/android/app/NotificationManager.java:25-30` (`notify`), `:20` (`createNotificationChannel`); service name at `framework/android/content/Context.java:38` |
| Push permission (`POST_NOTIFICATIONS`) handling | missing | no `POST_NOTIFICATIONS` ref in `framework/` or `src/` |

Net: the display end is present. Registration and transport are absent
end to end. Any services phase starts here.

### 2. Fused location (rank 2)

Location-gated social, maps-adjacent, and ride apps degrade without it.
microG rates fused location Full. Framework status:

| Need | Status | Pointer |
|---|---|---|
| `LocationManager.FUSED_PROVIDER` constant plus provider list | present (shape only) | `framework/android/location/LocationManager.java:24` (`FUSED_PROVIDER`), `:28-29` (all four providers listed), file 98 lines |
| Provider enablement | stubbed, always off in production | `framework/android/location/LocationManager.java:37-42` (`isProviderEnabled` delegates); `framework/org/isakai/bridge/IsakaiLocation.java:34` (`hasProvider()` hard `false`), file 77 lines |
| Last known location | stubbed, null unless test switch | `framework/org/isakai/bridge/IsakaiLocation.java:50` (`if (!fakeEnabled()) return null`); test-only synthetic fix at `:50-58` |
| Listener updates | stubbed, no backend | `framework/org/isakai/bridge/IsakaiLocation.java:61-76` (delivers only the synthetic fix or nothing) |
| `getSystemService("location")` / `LOCATION_SERVICE` wiring | missing | no `location` match anywhere in `framework/android/content/Context.java` (497 lines); branches at `:363-386` cover activity, shortcut, window, inflater, input, accessibility, autofill, clipboard, vibrator only |
| `PendingIntent` updates, proximity alerts | stubbed, log-only | `framework/android/location/LocationManager.java:79-81` and `:83-89` (`Log.w ... not supported`) |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` permission path | missing | no refs in `framework/` or `src/` |
| Geofencing family | missing | no class; microG also carries no support (research note) |

Net: API shape exists, backend and service binding do not. GeoClue2 wiring
is the named next step (already anticipated in the `LocationManager`
javadoc), plus `Context` service registration and the permission path.

### 3. Maps display (rank 3)

Maps display unlocks travel, food, and local apps. microG rates Maps v1
Full and Maps v2 Mostly (Mapbox build). Framework status:

| Need | Status | Pointer |
|---|---|---|
| `MapView`, `MapFragment`, `SupportMapFragment`, `GoogleMap`, markers, camera, tile overlays | missing, entire family | `find framework -iname "*mapview*" -o -iname "*mapfragment*"` empty; no `com.google.android.maps` refs |
| `WebView` tile fallback (JS map embeds) | present, headless | `framework/android/webkit/WebView.java` (203 lines): `loadUrl` at `:56-60`, `evaluateJavascript` at `:73-76`, JS-bridge dispatch in `:78-118`; engine is headless HTTP plus optional webkit2gtk helper per class javadoc `:15-23` |
| Location feed maps would consume | stubbed | see fused-location rows above |

Net: no Maps API at all. Cheapest display path is WebView tile embeds,
which itself is headless. Ranked below fused location because maps display
depends on the location feed anyway.

### 4. Basic auth (rank 4)

Password, phone, OAuth, and custom-token paths precede Google auth per the
plan (networked phase hardens identity first). microG rates account auth
and Firebase Auth Partial, with Google sign-in unsupported. Framework status:

| Need | Status | Pointer |
|---|---|---|
| `Account` value type | present | `framework/android/accounts/Account.java` (full file, parcelable name plus type) |
| `AccountManager` | stubbed, empty | `framework/android/accounts/AccountManager.java` (22 lines): `:11-13` (`getAccountTypes` returns empty), `:15-17` (`getAccountsByType` returns empty); no `getAccounts`, `addAccount`, `getAuthToken`, `blockingGetAuthToken`, `getPassword` |
| `LOGIN_ACCOUNTS_CHANGED_ACTION` | present (constant only) | `framework/android/accounts/AccountManager.java:6` |
| `getSystemService("account")` / `ACCOUNT_SERVICE` wiring | missing | no `account` service branch in `framework/android/content/Context.java:362-386` |
| `GET_ACCOUNTS` permission path | missing | no refs in `framework/` or `src/` |
| Google sign-in / Firebase Auth client classes | missing (and unsupported upstream) | no class; microG wiki rates GoogleSignIn unsupported per research note |

Net: value type only. Ranked last because even the reference compat layer
(microG) does not cover Google sign-in, so framework work here unlocks the
fewest apps per week until push, location, and maps land.

## Ranked punch list for the services phase

1. Push: GMS package identity plus availability answer, FCM registration
   API, transport, `POST_NOTIFICATIONS` path. Display end already present.
2. Fused location: GeoClue2 backend in `IsakaiLocation`, `Context`
   location-service wiring, fine/coarse permission path.
3. Maps: Maps API family or a scoped WebView-tile story; needs item 2 first.
4. Auth: `AccountManager` method coverage plus account-service wiring;
   Google sign-in stays out (unsupported upstream), so scope to the
   non-Google paths the networked phase needs.

Explicitly out of scope (declared ceiling, not gaps to fill): Play
Integrity verdicts, SafetyNet, billing enforcement, Play Games sign-in-only
logins, Cast, Auto, Wear, fitness, Nearby, in-app updates. Research note
documents each as broken, dead, or unsupported upstream.

## Verification

- `zig build` exit 0 (no-op, no code changes). `git diff --check` clean.
- No test files created. No fixture started. No sleep used.
- Evidence: this file only (`progress/IMPL-streamd.md`). No shared files
  touched.
