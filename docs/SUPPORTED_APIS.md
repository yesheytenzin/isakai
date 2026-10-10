# Supported APIs

Generated from the `framework/` tree (`find framework -name '*.java'`).
Every class below compiles with `javac --release 17` and ships on the app classpath.
One-line status is the class's own leading javadoc, trimmed; "shell"/"stub" means
link-compatible constants and method signatures with safe defaults (null/empty/false/no-op),
not real hardware or platform behavior. Host-side JVM tests (`*Test.java`) run on the host JVM only, not on device.

## android.accessibilityservice
- `AccessibilityServiceInfo` — Accessibility service description stub.

## android.accounts
- `Account` — Value type: account name plus type, parcelable.
- `AccountManager` — Account manager stub: no accounts registered.

## android.animation
- `Animator` — Property animator base: listeners fire, values jump to end.
- `AnimatorListenerAdapter` — No-op animator listener base.
- `AnimatorSet` — Animator group: starts children together.
- `ArgbEvaluator` — Interpolates ARGB colors channel by channel.
- `FloatEvaluator` — Float interpolation.
- `Keyframe` — Keyframe: fraction/value pair used by property animators.
- `LayoutTransition` — Layout-change animation config: recorded only.
- `ObjectAnimator` — Object animator: sets the end value on start.
- `PropertyValuesHolder` — Named value holder for property animation.
- `StateListAnimator` — State-driven animator: records tuples, no playback.
- `TimeInterpolator` — Animation clock mapping elapsed fraction to interpolated fraction.
- `TypeEvaluator` — Value interpolation between keyframes.
- `ValueAnimator` — Value animator: update listeners fire once at the end value.

## android.app
- `ActionBar` — Action bar: title/subtitle record, display flags only.
- `Activity` — Minimal Activity: headless lifecycle (M1) plus windowed rendering (M2) with the M3 input pump. M1 drives onCreate/onStart/onResume/onPause/ onStop/onDestroy in order; runWindowed additionally posts the content view to a Wayland toplevel.
- `ActivityManager` — Task and memory state queries; safe defaults.
- `ActivitySaveTest` — Host-side unit checks for the ACTION_CREATE_DOCUMENT save path (no-bridge fallback). Run on the host JVM.
- `AlarmManager` — Alarm scheduling; no-op shell.
- `AppComponentFactory` — Component factory hook: default instantiation.
- `AppOpsManager` — App-ops checks: everything allowed.
- `Application` — Minimal Application: lifecycle anchor over the Context storage surface.
- `Dialog` — Dialog window: content view plus cancel/dismiss hooks.
- `DownloadManager` — Download queue queries; empty shell.
- `Fragment` — Legacy fragment: lifecycle hooks with activity link.
- `FragmentManager` — Fragment registry with tag lookup.
- `FragmentTransaction` — Fragment transaction: add/remove batch, commit applies.
- `HeadlessWindow` — Headless Window: in-memory decor/callback storage, no compositor link. Lets AppCompat install its delegate callback and content view headlessly.
- `KeyguardManager` — Keyguard/lock state; unlocked shell.
- `LocaleManager` — System locale state; empty shell.
- `Notification` — Desktop notification: title/body/channel plus up to 3 tapped action keys.
- `NotificationChannel` — Desktop notification channel: id + name + importance (drives urgency).
- `NotificationManager` — Desktop notification manager: channels map to urgency, posts map tag:id to platform ids.
- `PendingIntent` — Pending intent: wraps the intent, send is a no-op.
- `SearchManager` — Search UI hooks; no-op shell.
- `Service` — Background service: lifecycle hooks only.
- `SharedElementCallback` — Shared-element transition hooks: empty defaults.
- `UiModeManager` — UI mode (night/car); defaults shell.
- `WallpaperManager` — Wallpaper access; null shell.

## android.app.admin
- `DevicePolicyManager` — Device admin policy; nothing enforced.

## android.app.job
- `JobScheduler` — Background job scheduling; no-op shell.

## android.app.role
- `RoleManager` — Role availability shell: nothing held.

## android.app.usage
- `UsageStatsManager` — Usage stats queries; empty shell.

## android.appwidget
- `AppWidgetManager` — App widgets; empty shell.

## android.bluetooth
- `BluetoothManager` — Bluetooth adapters; absent shell.

## android.content
- `ActivityNotFoundException` — Missing-activity failure.
- `BroadcastReceiver` — Broadcast receiver: context саме callback contract.
- `ClipData` — ClipData: text, HTML, and URI-list clips. Items carry at most one of text / HTML / URI; {@link Item#coerceToText} prefers text, then URI string, then HTML stripped of tags (null stays null).
- `ClipDescription` — Minimal ClipDescription: a label plus the offered MIME types. Text-clip MIME only for now (URIs deferred with ClipData).
- `ClipboardManager` — Window-bound clipboard manager: text clips travel through the Wayland data device, with a process-local copy as fallback (and as the whole store before any compositor selection exists). <p>URI lists travel as {@code text/uri-list} over the same channel (newline-joined); HTML clips degrade to their plain-text coercion on the wire and keep full HTML only in the local copy. Reads prefer the composito
- `ClipboardTest` — Host-side unit checks for {@link ClipData} + local {@link ClipboardManager}. Run on the host JVM.
- `ComponentCallbacks` — Configuration-change and memory callbacks.
- `ComponentCallbacks2` — Component callbacks with trim-memory levels.
- `ComponentName` — Package/class identity pair.
- `ContentProvider` — the CRUD hooks plus {@link #openFile}; the static registry routes {@link ContentResolver} calls by URI authority.
- `ContentProviderClient` — Thin client over one registered {@link ContentProvider}: every call forwards in-process (CancellationSignal honored before dispatch).
- `ContentResolver` — In-process ContentResolver: routes {@code content://} URIs to the {@link ContentProvider} registry by authority, serves {@code file://} directly off the filesystem, and resolves {@code android.resource://} against the app's code directory ({@code -Disakai.codeDir}, staged layout {@code res/} + {@code assets/}). Unknown authorities and missing files fail closed (null cursor / FileNotFoundException)
- `ContentValues` — Mutable name/value map for database rows, ported from the AOSP value-holder API. Null keys are rejected; null values are stored as Java null (bound as SQL NULL) and reported by {@link #containsKey}/{@link #isNull}.
- `Context` — Minimal abstract Context: file/prefs/database storage surface (M4). <p>Storage layout under the app data dir (lazy 0700 mkdir handled by the host helper at install; runtime mkdirs defensively re-assert 0700): <pre> files/ cache/ code_cache/ no_backup/ shared_prefs/ databases/ app_&lt;name&gt;/ </pre> {@link #getDir} rejects any name containing a separator; {@link #getDatabasePath} appends {@code .
- `ContextWrapper` — Context delegating every call to a base context; attachBaseContext re-targets the delegate.
- `DialogInterface` — Dialog cancel/dismiss/click/key contracts.
- `Intent` — Plain intent POJO: action + data/type + string/int extras.
- `IntentFilter` — Intent filter: action/category lists, matching by membership.
- `IntentSender` — Intent sender shell: fail-closed, never dispatches.
- `IsakaiPackageManager` — Single-package PackageManager: answers component/package queries for the bound context's own package from defaults (no manifest activity table is persisted yet, so any same-package component resolves with stock fields). Cross-package queries miss, matching the install snapshot boundary.
- `ProviderTest` — Host-side unit checks for in-process provider hosting + resolver routing + file-scheme support. Run on the host JVM.
- `RestrictionsManager` — User restrictions; empty shell.
- `ServiceConnection` — Service bind/unbind callbacks.
- `SharedPreferences` — Minimal SharedPreferences: typed getters, contains, and edit/commit/apply. Implemented by {@link SharedPreferencesImpl}. All values are immutable snapshots; string sets are unmodifiable defensive copies.
- `SharedPreferencesImpl` — File-backed SharedPreferences with the AOSP durable-write protocol. <p>Persistence: {@code <map>} root of {@code <string>}, {@code <int value="">}, {@code <long>}, {@code <float>}, {@code <boolean>}, and {@code <set>} (nested {@code <string>} items) children. The writer is byte-exact with AOSP's {@code writeMapXml}: UTF-8, LF, two-space attribute indentation. The strict reader treats any unknown t
- `UriMatcher` — URI matcher: exact segment match with # (number) and (text) wildcards.
- `UriPermission` — Persisted URI grant shell.

## android.content.pm
- `ActivityInfo` — Activity manifest record.
- `ApplicationInfo` — Application record from the install registry.
- `ComponentInfo` — Shared component fields: name, package, metadata.
- `LauncherApps` — Launcher queries; empty shell.
- `PackageInfo` — Installed-package record.
- `PackageItemInfo` — Named-item base for package records.
- `PackageManager` — PackageManager: answers from the install snapshot (single package); cross-package queries miss or return empty lists.
- `ProviderInfo` — Provider manifest record.
- `ResolveInfo` — Intent-resolution result.
- `ServiceInfo` — Service manifest record.
- `Signature` — APK signature bytes.

## android.content.res
- `AssetFileDescriptor` — Asset file descriptor: offset/length view over a ParcelFileDescriptor.
- `AssetManager` — Asset access rooted at the staged code dir ({@code -Disakai.codeDir}). <p>Serves staged {@code assets/} + {@code res/} paths with bounds: names are rejected when absolute, containing {@code ..}, or escaping the code dir after canonicalization. Missing files throw {@link IOException}.
- `ColorStateList` — State-to-color mapping with default fallback. <p>Parses {@code <selector>} XML: ordered {@code <item>} elements with {@code android:color} plus {@code android:state_} booleans. Colors may be literals ({@code #rgb/#argb/#rrggbb/#aarrggbb}) or {@code @color} / {@code ?attr} references resolved through {@link Resources}; unresolvable references throw {@link Resources.NotFoundException} (fail-closed, 
- `Configuration` — Device configuration snapshot with diff support.
- `Resources` — Resources: system DisplayMetrics plus configuration snapshots, backed by a staged resource table rooted at the staged code dir ({@code -Disakai.codeDir}). <p>Host side ({@code src/install.zig} + {@code src/arsc.zig}) parses {@code resources.arsc} at install time; this class loads the staged copy at runtime: {@code <codeDir>/resources.arsc}. The table format is shared with the Zig reader (string po
- `TypedArray` — Styled-attribute array backed by the {@link Resources} table: each slot resolves its attr id through the resource table, with the caller default on any miss (fail-closed, never a guessed value).
- `XmlResourceParser` — XML resource parser: closed handle, no content.

## android.database
- `ContentObserver` — Change observer: stub delivery, self observer marker.
- `Cursor` — Minimal forward-only Cursor over an in-memory row table. Positions start at -1 (before first); {@link #moveToNext} advances, {@link #moveToFirst} rewinds. Column lookup by name is case-sensitive; unknown names throw {@link IllegalArgumentException}. Type getters coerce like AOSP (numeric strings parse, null yields 0/0.0/false/null).
- `CursorIndexOutOfBoundsException` — Row index out of range (mirrors the AOSP cursor exception).
- `DataSetObservable` — Observer registry with change broadcast.
- `DataSetObserver` — Data-change observer contract.
- `DatabaseUtils` — Static helpers over query results: single-cell getters and row counts. Every method closes nothing; callers own the cursor lifecycle.
- `MatrixCursor` — Mutable in-memory cursor: rows appended before iteration.
- `MergeCursor` — Concatenated view over two cursors.
- `Observable` — Observer registry base.
- `SQLException` — Runtime failure for database errors; the SQLite subclasses carry the native result code via {@link #getResultCode()}.

## android.database.sqlite
- `SQLiteConstraintException` — Constraint violation (UNIQUE/CHECK/FOREIGN KEY/NOT NULL); result code 19.
- `SQLiteCursor` — Eager cursor: all rows are buffered at query time (the native statement is already stepped to DONE and finalized). Position starts before first.
- `SQLiteCursorDriver` — Cursor driver contract.
- `SQLiteDatabase` — SQLite database handle over the native bridge. Open via {@link #openOrCreateDatabase} / {@link #openDatabase}, or hold one through {@link SQLiteOpenHelper}. Statements compile through {@link #compileStatement}; queries return an eager {@link SQLiteCursor} (all rows buffered at query time). <p>Errors: native failures throw {@link SQLiteException}; result code 5 (BUSY) throws {@link SQLiteDatabaseLo
- `SQLiteDatabaseLockedException` — Database-file lock contention (SQLITE_BUSY, result code 5).
- `SQLiteException` — Base SQLite failure; carries the native sqlite3 result code.
- `SQLiteOpenHelper` — Versioned database holder: opens (creating with {@link #onCreate} at version 0) and migrates with {@link #onUpgrade} / {@link #onDowngrade}. The user version is tracked with {@code PRAGMA user_version}; the helper serializes opens and caches one {@link SQLiteDatabase} per instance.
- `SQLiteProgram` — Compiled-statement bind shell.
- `SQLiteQuery` — Query statement shell: no execution.
- `SQLiteStatement` — Compiled one-shot statement: bind args, then execute or query. Indexes are 1-based. {@link #close} finalizes the native statement; methods after close throw {@link IllegalStateException}.

## android.graphics
- `Bitmap` — Pixel buffer with an ARGB int backing store. Only the in-memory API used by apps is present; GPU/hardware paths throw.
- `BitmapFactory` — Software image decoder: PNG/JPEG via {@code javax.imageio} into ARGB_8888 bitmaps. No native code, no Binder. <p>Fail-closed: corrupt, truncated, or oversized input returns {@code null} (or throws {@code IOException} only where the signature requires a stream read); a partially decoded image is never returned.
- `BitmapFont` — Built-in 5x7 ASCII glyphs. Deterministic and dependency-free (no AWT, no font files). Each row is 5 bits, MSB left. Covers A-Z, a-z, 0-9 and common punctuation; anything else renders as '?'.
- `BitmapShader` — Bitmap-backed shader handle.
- `BlendMode` — Blend-mode enum shell.
- `BlendModeColorFilter` — Blend-mode color filter shell: recorded only.
- `Canvas` — Software canvas over a packed ARGB int backing store. All drawing is clipped to the canvas bounds intersected with the current clip rect. Deterministic: no anti-aliasing, no fonts. <p>Two backings: a heap {@code int[]} (copy path) or a native-order {@link IntBuffer} view over a direct {@link ByteBuffer} (zero-copy path, via {@code IsakaiWindow.nMapBuffer}). Pixel access always goes through the ind
- `Color` — Color constants and packers. Colors are packed ARGB ints.
- `ColorFilter` — Color-filter base: recorded, rendering ignores it.
- `ColorSpace` — Color-space shell: identity adapt/convert.
- `ComposeShader` — Composed shader shell: records pair and mode.
- `ImageDecoder` — Image decode shell: always fails closed.
- `Insets` — System window insets: four zero edges.
- `LinearGradient` — Linear-gradient shader handle.
- `Matrix` — 3x3 affine matrix (row-major float[9]) with the android.graphics.Matrix API surface. Perspective entries are stored but mapPoints treats input as 2D points (matches the common affine use; full perspective divide applies when the last row differs from [0 0 1]).
- `Outline` — Outline descriptor: records the last set shape.
- `Paint` — Text-fill state. BitmapFont renders the pixels; this class records the settings calls make so linking succeeds and Canvas can read metrics.
- `Path` — Path recorder: stores verbs plus points so bounds/measure/transform work; boolean ops and rendering are unsupported.
- `PathMeasure` — Contour measurer over Path verbs: control points flatten to segment endpoints (curves approximate as chords).
- `Picture` — Recorded picture: begin/end link, rendering ignores the content.
- `PixelFormat` — Pixel-format constants.
- `Point` — Integer point.
- `PointF` — Float point.
- `PorterDuff` — Porter-Duff compositing modes.
- `PorterDuffColorFilter` — Porter-Duff color filter handle.
- `PorterDuffXfermode` — Porter-Duff transfer-mode handle.
- `RadialGradient` — Radial gradient shader: records geometry and colors.
- `Rect` — Integer rectangle with the android.graphics.Rect API surface.
- `RectF` — Float rectangle with the android.graphics.RectF API surface.
- `Region` — Region: bounds-rect union, path/ops record-only.
- `RenderNode` — Render-node shell: transform bookkeeping, no display list.
- `Shader` — Shader base: records the local matrix; rendering ignores the fill.
- `SweepGradient` — Sweep gradient shader: records center, colors, and positions.
- `Typeface` — Typeface handle. Desktop rendering uses the bitmap font; this class records style/weight so text configuration calls link and round-trip.
- `Xfermode` — Transfer-mode handle: recorded, rendering ignores it.

## android.graphics.drawable
- `Animatable` — Start/stop animation contract.
- `Animatable2` — Animatable with completion callbacks.
- `AnimatedImageDrawable` — Animated-image shell: static first frame.
- `AnimatedStateListDrawable` — State list with transitions: transitions ignored.
- `AnimatedVectorDrawable` — Animated vector: start/stop flip state, callbacks fire immediately.
- `AnimationDrawable` — Frame animation: draws the first frame, timing ignored.
- `BitmapDrawable` — Bitmap drawable: draws the bitmap scaled to its bounds.
- `ClipDrawable` — Clips its child to a level fraction along one axis.
- `ColorDrawable` — Solid-color drawable filling its bounds.
- `ColorStateListDrawable` — Color drawable resolving its color from a state list.
- `Drawable` — Drawable base: bounds/state/level/visibility bookkeeping with abstract draw. Subclasses render into the software Canvas.
- `DrawableContainer` — Holds children, drawing only the selected one.
- `GradientDrawable` — Shape drawable: fills bounds with a solid color; stroke, gradient, corners, and ring/oval shapes are recorded but render as a fill.
- `Icon` — Icon handle: records its source kind plus payload so getters link; loading requires app resources and is unsupported.
- `InsetDrawable` — Wraps a drawable with fixed insets.
- `LayerDrawable` — Stack of drawables drawn in order with per-layer insets.
- `RippleDrawable` — Ripple drawable: renders the content layer, ripple ignored.
- `ShapeDrawable` — Drawable delegating geometry to a Shape with its own Paint.
- `StateListDrawable` — Selects a child drawable by state set, drawing the current match.
- `TransitionDrawable` — Cross-fade between two layers: instant switch, records timing.
- `VectorDrawable` — Vector drawable: parses viewport/dimensions/path data, renders nothing (software raster has no path fill yet; presence + bounds are honored).

## android.graphics.drawable.shapes
- `OvalShape` — Inscribed ellipse fill.
- `RectShape` — Axis-aligned rectangle outline/fill.
- `RoundRectShape` — Rounded rectangle: radii recorded, renders as a fill.
- `Shape` — Shape base: resize plus draw hooks.

## android.graphics.fonts
- `Font` — Font record: metadata handle, desktop rendering uses the bitmap font.
- `FontFamily` — Font-family record: metadata handle.
- `FontStyle` — Requested font style: weight plus slant.

## android.graphics.text
- `LineBreakConfig` — Line-break config: hyphenation/justify policy (headless: recorded only).

## android.hardware
- `ConsumerIrManager` — IR blaster; absent shell.
- `Sensor` — Physical sensor descriptor (type/name/vendor/range/resolution/power).
- `SensorEvent` — Sensor event (sensor/accuracy/timestamp/values).
- `SensorEventListener` — Listener for sensor changes (onSensorChanged/onAccuracyChanged).
- `SensorManager` — SensorManager backed by IsakaiSensor: virtual gravity (9.81) + light (ISAKAI_SENSOR_LIGHT override) when iio-sensor-proxy absent, D-Bus proxy shape ready; ~50 Hz dispatch via Handler.

## android.hardware.biometrics
- `BiometricManager` — Biometric enrollment shell: nothing enrolled.
- `BiometricPrompt` — Biometric auth shell: immediately fails closed.

## android.hardware.camera2
- `CameraManager` — Camera enumeration; empty shell.

## android.hardware.display
- `DisplayManager` — Display enumeration; single default display shell.

## android.hardware.fingerprint
- `FingerprintManager` — Fingerprint shell: no hardware.

## android.hardware.input
- `InputManager` — Input device enumeration; empty shell.

## android.hardware.usb
- `UsbDevice` — USB device descriptor shell.
- `UsbEndpoint` — USB endpoint shell.
- `UsbInterface` — USB interface shell.
- `UsbManager` — USB accessories/devices; empty shell.

## android.icu.util
- `ULocale` — Unicode locale stub: wraps a language tag string.

## android.location
- `Criteria` — Location criteria (accuracy/power/altitude/bearing/speed/cost).
- `Location` — Location fix (provider/lat/lon/accuracy/time/extras).
- `LocationListener` — Location callbacks (onLocationChanged/onStatusChanged/onProviderEnabled/Disabled).
- `LocationManager` — LocationManager via IsakaiLocation: virtual GeoClue fallback (37.42,-122.08) when GeoClue2 absent, D-Bus shape ready; fail-closed when provider disabled.

## android.media
- `AudioManager` — Audio volumes and focus; silent shell.
- `MediaExtractor` — Media extraction shell: no tracks.
- `MediaFormat` — Format map shell: string/int entries only.
- `MediaMetadataRetriever` — Metadata retriever via IsakaiMedia: ffprobe duration/mime + ffmpeg frame extract → Bitmap; null when ffmpeg absent.
- `MediaPlayer` — MediaPlayer state machine (Idle→Initialized→Preparing→Prepared→Started/Paused/Stopped/Completed/Error/End) via IsakaiMedia + IsakaiAudio: ffmpeg decode to S16LE 44.1k stereo + PulseAudio; UnsupportedOperationException when ffmpeg absent, IOException on corrupt.
- `MediaRouter` — Media routing; empty shell.
- `SoundPool` — Minimal SoundPool: load a 16-bit PCM WAV file (mono or stereo, any supported rate) by path and play it once through PulseAudio at its native rate. Only {@code loop == 0} (play once) is supported.
- `SoundPoolTest` — Host-side unit checks for {@link SoundPool#parseWav}. Run on the host JVM.

## android.media.projection
- `MediaProjectionManager` — Screen capture permission; denied shell.

## android.media.session
- `MediaSessionManager` — Media sessions; empty shell.

## android.media.tv
- `TvInputManager` — TV inputs; empty shell.

## android.net
- `ConnectivityManager` — Network state; offline shell.
- `Uri` — Hierarchical URI: scheme/authority/path/query splits over the raw string, plus a Builder. Encoding is preserved verbatim (no escaping).

## android.net.nsd
- `NsdManager` — Network service discovery; no-op shell.

## android.net.wifi
- `WifiManager` — Wi-Fi state; disabled shell.

## android.net.wifi.p2p
- `WifiP2pManager` — Wi-Fi Direct stub: P2P unsupported.

## android.nfc
- `NfcManager` — NFC stub: device has no NFC hardware.

## android.os
- `BaseBundle` — String-keyed value map base; Bundle adds parceling.
- `BatteryManager` — Battery state query stub: always unplugged, unknown status.
- `Binder` — Binder identity shell: alive, same-process only.
- `Build` — Build identity: host Linux masquerading at API 33.
- `Bundle` — String-keyed value map with bundle nesting and parceling.
- `CancellationSignal` — Cancellation token: flag plus listener, nothing polls it.
- `DropBoxManager` — DropBox log stub: entries are discarded.
- `Handler` — Handler: posts run immediately on the calling thread.
- `IBinder` — Binder token: null-object stand-in, no IPC exists.
- `LocaleList` — Ordered immutable locale list (headless: default locale only unless set).
- `Looper` — Thread message queue: single main looper, direct dispatch.
- `Message` — Message envelope: what/arg/obj plus target handler.
- `OperationCanceledException` — Thrown when an operation is canceled.
- `Parcel` — In-process parcel: primitives, strings, and byte arrays round-trip through an internal byte buffer (big-endian DataOutput/DataInput order); binders, parcelables, and bundles travel by reference with a position marker in the stream. There is no cross-process IPC, so parcelables are NOT flattened (writeToParcel is never called here). <p>Write-then-read discipline: the read snapshot is taken lazily a
- `ParcelFileDescriptor` — File-descriptor handle over a real open file. Fail-closed: open rejects null paths, directories, and unknown modes; reads/writes after close throw.
- `ParcelTest` — Host-side unit checks for {@link Parcel} write/read round-trips. Run on the host JVM.
- `Parcelable` — Parcelable contract: write plus creator lookup.
- `PowerManager` — Power state stub: device always interactive, never power-save.
- `RemoteException` — Cross-process failure marker (no IPC exists; never thrown).
- `ResultReceiver` — Result receiver: delivers results via a handler callback.
- `StrictMode` — StrictMode shell: notes recorded nowhere.
- `SystemClock` — Monotonic and wall clocks over System.nanoTime.
- `Trace` — Tracing helpers: no-op stub, tracing disabled.
- `UserManager` — User management stub: single user, no restrictions.
- `Vibrator` — Vibrator stub: device has no vibrator.

## android.os.ext
- `SdkExtensions` — SDK extension versions (headless: no extensions).

## android.os.storage
- `StorageManager` — Storage volume query stub: no volumes mounted.

## android.print
- `PrintManager` — Print service stub: printing unsupported.

## android.security.identity
- `IdentityCredential` — Identity credential shell: no provisioning.
- `PresentationSession` — Presentation session shell.

## android.security.keystore
- `KeyGenParameterSpec` — Key-generation parameters shell.

## android.service.quicksettings
- `TileService` — Quick-settings tile service: lifecycle hooks only.

## android.telecom
- `TelecomManager` — Telecom stub: no active calls.

## android.telephony
- `SubscriptionManager` — Subscription query stub: no active subscriptions.
- `TelephonyManager` — Telephony query stub: no radio present.

## android.text
- `Annotation` — Key/value annotation attached to a text range; parcelable value type.
- `BoringLayout` — Single-line metrics fast path over a fixed width.
- `Editable` — Minimal Editable: a plain mutable character buffer with insert/delete. No spans or watchers in M3.
- `Html` — HTML to spanned text (headless: tag-stripping with bold/italic/underline spans; entities resolved; images/lists unsupported).
- `InputFilter` — Text mutation filter contract.
- `Layout` — Laid-out text block. Lines break on '\n' and wrap at the outer width using bitmap-font cell widths; draw() renders rows through Canvas.
- `PrecomputedText` — Pre-measured text: CharSequence wrapper with paragraph queries.
- `Selection` — Selection marks over a Spannable.
- `SpanWatcher` — Span attach/detach/change callbacks.
- `Spannable` — Mutable span attachment over Spanned.
- `SpannableString` — Immutable text with mutable span marks.
- `SpannableStringBuilder` — Mutable text with mutable span marks.
- `Spanned` — Read-only span queries over marked-up text.
- `StaticLayout` — Static layout over a fixed width. Ellipsis, hyphenation, and break-strategy settings are accepted and ellipsizing applies.
- `TextDirectionHeuristic` — Text-direction heuristic marker.
- `TextDirectionHeuristics` — Standard text-direction heuristics.
- `TextPaint` — Paint plus text density and drawable state.
- `TextUtils` — Text helpers: emptiness, equality, splitting, ellipsizing.
- `TextWatcher` — Text-change listener triple.

## android.text.method
- `ArrowKeyMovementMethod` — Arrow-key movement: cursor keys move selection (headless: no-op).
- `BaseMovementMethod` — Base movement: no-op navigation (headless has no cursor keys).
- `KeyListener` — Key-event to Editable translation contract.
- `LinkMovementMethod` — Link movement: taps activate URLSpans (headless: no tap dispatch).
- `MetaKeyKeyListener` — Meta-key state tracking: no-ops, desktop keys carry their own state.
- `MovementMethod` — Movement method contract: cursor navigation over text.
- `NumberKeyListener` — Key listener admitting only a whitelisted set of characters.
- `PasswordTransformationMethod` — Masks every character as a dot.
- `ScrollingMovementMethod` — Scrolling movement: scrolls on navigation (headless: no-op).
- `SingleLineTransformationMethod` — Single-line transformation: strips newline characters.
- `TransformationMethod` — Display transformation for password fields and suggestions.

## android.text.style
- `AbsoluteSizeSpan` — Fixed text size span, optionally density-independent.
- `BackgroundColorSpan` — Background color span: stores the color; drawing applies it where supported.
- `CharacterStyle` — Base for spans that change paint or metrics.
- `ClickableSpan` — Clickable span: click dispatch is a no-op, draw state underlines.
- `ForegroundColorSpan` — Foreground color span.
- `ImageSpan` — Image placeholder span: drawable/bitmap/uri/context variants with getters.
- `LeadingMarginSpan` — Paragraph leading-margin span with indent getters.
- `LocaleSpan` — Locale span: stores the locale(s); applies the primary to the paint.
- `MetricAffectingSpan` — Base for spans that affect both metrics and draw state.
- `RelativeSizeSpan` — Relative (proportional) text size span.
- `ReplacementSpan` — Span replacing its range with custom content.
- `ScaleXSpan` — Horizontal scale span.
- `StrikethroughSpan` — Strikethrough span. No strike channel on this Paint; presence is the signal.
- `StyleSpan` — Typeface style (bold/italic) span.
- `TextAppearanceSpan` — TextAppearance style span: family/style/size/color/link getters.
- `TtsSpan` — TTS annotation span: type plus argument bundle, with a builder.
- `URLSpan` — URL span: stores the URL; click dispatch is a no-op.
- `UnderlineSpan` — Underline span. No underline channel on this Paint; presence is the signal.

## android.transition
- `Transition` — Scene-transition handle: capture hooks only.
- `TransitionSet` — Ordered/unordered group of child transitions.

## android.util
- `AndroidException` — Base Android exception (checked).
- `AndroidRuntimeException` — Framework runtime failure.
- `AttributeSet` — XML attribute reader contract.
- `DisplayMetrics` — DisplayMetrics: density/scale snapshot from the compositor output. <p>Field layout mirrors the native int[8] snapshot carried over {@code IsakaiWindow.nPollMetrics}: {@code [w, h, densityDpi, density1000, scaled1000, xdpi1000, ydpi1000, scale]}. {@link #setToDefaults} fills the scale=1 fallback the native side publishes when no wl_output is bound; {@link #setFromSnapshot} applies a fresh polled sn
- `LayoutDirection` — Layout-direction constants.
- `Log` — Log sink: stderr with level/tag prefix.
- `LongSparseArray` — Long-keyed sorted map with binary search.
- `Pair` — Immutable pair of two values.
- `Property` — Typed property handle: name plus type.
- `Size` — Immutable width/height pair (pixels).
- `SizeF` — Immutable float width/height pair.
- `SparseArray` — Int-keyed sorted map with binary search.
- `SparseBooleanArray` — Int-keyed boolean-valued sorted map.
- `SparseIntArray` — Int-keyed int-valued sorted map.
- `SparseLongArray` — Int-to-long sparse map (sorted keys, binary search).
- `TypedValue` — Typed resource value: type tag plus raw data.
- `Xml` — XML factory: pull parser / serializer backed by the JDK StAX engine.

## android.view
- `AbsSavedState` — Saved-state chain base: wraps the super state parcelable.
- `ActionMode` — Contextual action bar: finish hooks only.
- `ActionProvider` — Menu-item action view provider base.
- `Choreographer` — Frame scheduler: callbacks run immediately.
- `CollapsibleActionView` — Action-view expand/collapse contract for menu items.
- `ContentInfo` — Rich-content payload: clip plus source flags.
- `ContextMenu` — Context menu: menu plus header hooks.
- `ContextThemeWrapper` — Context with an attached theme override.
- `Display` — Display descriptor: fixed size holder.
- `DisplayCutout` — Display cutout descriptor: empty bounds.
- `DisplayListCanvas` — Recording canvas shell: no display-list backend.
- `FocusFinder` — FocusFinder: directional focus search over the view tree. Picks the nearest focusable in the given direction (beam overlap + distance, AOSP heuristic simplified to center distance).
- `GestureDetector` — Tap/long-press/scroll/fling detection over touch events. Single pointer only: the first pointer drives the gesture; extra pointers cancel tap/long-press tracking (scroll/fling keep the first pointer's stream). <p>Timing comes from MotionEvent timestamps (not wall clock), so headless tests can drive gestures deterministically. Long-press is time-driven: it fires on the first MOVE/UP at or past down
- `GestureTest` — Host-side unit checks for {@link GestureDetector}. Run on the host JVM.
- `InflateException` — Inflate failure: view hierarchy could not be constructed.
- `KeyCharacterMap` — Key-capability map: device key to label/number fallbacks.
- `KeyEvent` — Minimal KeyEvent: DOWN/UP actions, Android keycode/meta/flag constants, unicode char, event time. No ACTION_MULTIPLE in M3 (native never sends repeats; long-press is out of scope).
- `KeyboardShortcutGroup` — Keyboard-shortcut group record.
- `LayoutInflater` — Layout inflater: walks the binary-XML {@link android.content.res.XmlResourceParser} for a layout id, builds the view tree through the Factory/Factory2 chain then a framework widget factory, applies a small set of attributes (id/text/hint/orientation/layout_, visibility, onClick reflection), and attaches per attachToRoot. <p>Unknown tags (androidx/custom views) resolve by reflection against the app
- `MeasureSpec` — Measure-spec packing: size plus mode in one int.
- `Menu` — Menu contract: item list with add/remove.
- `MenuInflater` — Menu inflater: builds Menu structure from binary-XML menu resources ({@code <menu>}, {@code <item>}, {@code <group>}). <p>Group defaults (visible/enabled/checkable) apply to items without explicit values. Item titles resolve {@code @string} refs via resources with a literal fallback; unknown ids fail closed via {@link android.util.AndroidRuntimeException}.
- `MenuItem` — Menu item contract: recorded state, no rendering.
- `MotionEvent` — Minimal MotionEvent: DOWN/MOVE/UP/CANCEL plus multi-pointer POINTER_DOWN/POINTER_UP, full pointer arrays, index bits. Coordinates are surface-absolute floats.
- `OnReceiveContentListener` — Rich-content receiver contract.
- `PointerIcon` — Pointer icon handle: type id only, no bitmap loading.
- `RoundedCorner` — Rounded screen corner descriptor: zero radius.
- `ScaleGestureDetector` — Pinch-scale detector shell: listener dispatch driven by tests.
- `SearchEvent` — Search-request event marker.
- `SubMenu` — Submenu: menu plus its parent item.
- `VelocityTracker` — VelocityTracker: movement history with pixels/sec velocity. Single-pointer plus per-pointer-id queries over recent samples.
- `View` — Minimal View: size, background, measure/layout/draw plus M3 input. Listeners (click/key/touch/focus), key/touch dispatch, a pressed/click machine in onTouchEvent, focusable/focused state, and a dirty flag that walks up to the root so Activity redraws on demand. <p>AppCompat-style callers also need id/tags, visibility, padding, background drawables, elevation/alpha, layout params, the window-token 
- `ViewConfiguration` — Input thresholds: fixed desktop values.
- `ViewGroup` — Minimal ViewGroup: absolute children, reverse paint-order hit testing, touch-target routing (with CANCEL on steal), key routing to the focused child, and a focusNext helper. No TAB traversal in M3.
- `ViewGroupOverlay` — Overlay above host children: records drawables/views without attaching.
- `ViewManager` — View add/update/remove contract.
- `ViewOutlineProvider` — Shadow outline supplier: default rect.
- `ViewParent` — Parent contract: invalidation and layout requests climb here.
- `ViewPropertyAnimator` — Chained property animation: applies end values immediately.
- `ViewStructure` — Autofill/assist structure node: field bag only.
- `ViewStub` — ViewStub: lazily inflated placeholder (inflate resolves via parent).
- `ViewTreeObserver` — Global view-tree listeners: recorded, never fired.
- `Window` — Window handle: flags plus decor view, no compositor link.
- `WindowInsets` — System window insets snapshot: all zero.
- `WindowInsetsAnimation` — Window-insets animation: fraction record, callbacks never fire.
- `WindowInsetsController` — System-bars visibility controller: no-op.
- `WindowManager` — Window manager: view add/remove plus default display.

## android.view.accessibility
- `AccessibilityEvent` — Accessibility event record: type plus text list. Extends the record base like AOSP (translated code calls record methods on events).
- `AccessibilityManager` — Accessibility service registry: disabled, empty lists.
- `AccessibilityNodeInfo` — Accessibility node record: properties plus child list.
- `AccessibilityNodeProvider` — Custom accessibility bridge: no virtual nodes.
- `AccessibilityRecord` — AccessibilityRecord: event/node payload (headless: recorded, never sent).
- `CaptioningManager` — Captioning settings stub: captions disabled.

## android.view.animation
- `AccelerateDecelerateInterpolator` — Ease-in-out curve: cosine blend of the elapsed fraction.
- `AccelerateInterpolator` — Ease-in curve: input raised to twice the factor (default quadratic).
- `Animation` — Legacy animation handle: start/end hooks only.
- `AnimationSet` — Animation group: children run together.
- `DecelerateInterpolator` — Ease-out curve: 1 minus the remaining fraction raised to twice the factor.
- `Interpolator` — Easing curve mapping elapsed fraction to interpolated fraction.
- `LinearInterpolator` — Identity curve: output equals input.
- `PathInterpolator` — Cubic-Bezier easing through the given control points.
- `Transformation` — Transformation record: matrix plus alpha.

## android.view.autofill
- `AutofillId` — Autofill node id: opaque long wrapper.
- `AutofillManager` — Autofill stub: autofill unsupported.
- `AutofillValue` — Autofill value union: text, list index, or toggle.

## android.view.contentcapture
- `ContentCaptureSession` — Content-capture sink: notify shells only.

## android.view.inputmethod
- `BaseInputConnection` — Editable-backed input connection with real buffer plumbing.
- `CursorAnchorInfo` — Cursor/selection anchor snapshot with builder.
- `EditorBoundsInfo` — Editor bounds info: IME-visible regions (headless: recorded only).
- `EditorInfo` — Editor capabilities record.
- `ExtractedText` — Extracted editor snapshot fields.
- `InputConnection` — IME-to-editor bridge contract.
- `InputConnectionWrapper` — Delegating input connection wrapper.
- `InputContentInfo` — IME content payload: uris plus description.
- `InputMethodManager` — Input-method manager: show/hide are no-ops.

## android.view.textclassifier
- `TextClassificationManager` — Text classification service stub: returns the no-op classifier.
- `TextClassifier` — Text classifier stub: no classification available.

## android.view.textservice
- `TextServicesManager` — Text services stub: no spell checker available.

## android.view.translation
- `ViewTranslationRequest` — Translation request for one view with builder.
- `ViewTranslationResponse` — Translation response map with builder.

## android.webkit
- `CookieManager` — Cookie jar (in-memory URL→value, acceptCookie gate, removeAll/flush, hasCookies); real webkit2gtk jar in helper when present.
- `JavascriptInterface` — Annotation marking methods exposed to JavaScript via addJavascriptInterface.
- `ValueCallback` — Single-value async callback (onReceiveValue).
- `WebResourceError` — Web resource error (code + description).
- `WebResourceRequest` — Web resource request (URL/method/headers/forMainFrame/hasGesture).
- `WebResourceResponse` — Web resource response (MIME/encoding/data/status/headers).
- `WebSettings` — Web settings (JS/DOM/file/content/images/network/zoom/cache/UA/mixedContent).
- `WebView` — WebView (View subclass): history (goBack/Forward/reload/clear), loadUrl/loadData, evaluateJavascript via reflection dispatch to @JavascriptInterface, WebViewClient callbacks, ISAKAI_WEBVIEW=0 disable.
- `WebViewClient` — WebView client (6 methods: shouldOverrideUrlLoading×2, onPageStarted/Finished, onReceivedError×2, onReceivedHttpError).

## android.widget
- `AbsListView` — Scrollable list base: renders visible rows, selection optional.
- `AbsSeekBar` — Seek bar base: progress plus a thumb drawable.
- `Adapter` — Adapter contract: count, items, views.
- `AdapterView` — Adapter-bound view group with selection state.
- `ArrayAdapter` — List-backed adapter rendering items as text rows.
- `AutoCompleteTextView` — Text completion field: adapter plus threshold, no popup.
- `BaseAdapter` — Adapter base with observer broadcast.
- `Button` — Minimal Button: clickable + focusable, pressed fill, centered label.
- `CheckBox` — Check box: CompoundButton with box styling.
- `Checkable` — Checkable contract for toggle widgets.
- `CheckedTextView` — Checkable text row with a check-mark drawable.
- `Chronometer` — Chronometer: elapsed-time text display (no ticking loop headless).
- `CompoundButton` — Toggle button: checked state plus button drawable.
- `DateTimeView` — DateTimeView: static date/time text (internal widget, text only).
- `EdgeEffect` — Overscroll glow: state recorded, never rendered headless.
- `EditText` — Minimal single-line EditText: an Editable buffer with a cursor, printable-key insert, DEL/BACKSPACE delete-before-cursor, DPAD move, ENTER clears focus. No multiline, no selection, no IME in M3.
- `Filter` — Async filter flow: performFiltering then publishResults via Delayer.
- `FrameLayout` — Stacks children at the top-left, honoring gravity.
- `GridView` — Grid view: fixed-column list, columns recorded.
- `HeaderViewListAdapter` — ListAdapter wrapper accounting for header/footer rows.
- `HorizontalScrollView` — Horizontal scroll container: single child, offset recorded.
- `ImageButton` — Image button: clickable ImageView with button styling.
- `ImageView` — Image view: draws its drawable fitted by scale type.
- `LinearLayout` — Lays children in a row or column with weights.
- `ListAdapter` — Adapter with stable-id and dropdown-view hooks.
- `ListView` — Vertical list with dividers and header/footer views.
- `Magnifier` — Magnifier loupe shell: visibility flag only.
- `MultiAutoCompleteTextView` — Multi-token completion field with a tokenizer.
- `OverScroller` — OverScroller: overshoot-capable fling physics (overshoot distance recorded but not rendered; headless has no edge glow).
- `PopupWindow` — Floating window: content view plus background, dismissed on demand.
- `ProgressBar` — Progress bar: level fill over the background.
- `RadioButton` — Radio button: CompoundButton with circle styling.
- `RadioGroup` — Exclusive-check group: id dispatch to listener.
- `RatingBar` — Star rating bar: rating fraction fill.
- `RelativeLayout` — RelativeLayout: rules recorded per child, laid out as a vertical stack (headless: rule solving is out of scope; children stay findable).
- `RemoteViews` — Remote view template: records text/visibility/click ops.
- `ScrollView` — Vertical scroll container: single child, offset recorded.
- `Scroller` — Scroller: fling/scroll physics over ints (no rendering link). Time base is uptimeMillis; Abort/startScroll/fling/computeScrollOffset follow AOSP semantics with a viscous-fluid interpolator default.
- `SeekBar` — Seek bar: draggable progress with change listener.
- `Space` — Lightweight spacer view.
- `Spinner` — Spinner: dropdown selection over an adapter.
- `SpinnerAdapter` — Spinner adapter with dropdown views.
- `Switch` — Switch: two-state toggle with text labels.
- `TableLayout` — Table layout: vertical rows, stretch flags recorded.
- `TableRow` — Table row: horizontal LinearLayout with column params.
- `TextView` — Minimal TextView: text drawn over the view background at view-relative (4,4) padding, word-wrapped to the view width. Wrapping is greedy per word at the BitmapFont cell width; a word wider than the line still advances to the next line and overflows.
- `ThemedSpinnerAdapter` — Spinner adapter with a themed dropdown view.
- `Toast` — Toast: transient message (headless: logged, no popup surface).
- `ToggleButton` — On/off toggle button with text labels.

## android.window
- `BackEvent` — Back gesture progress event.
- `OnBackAnimationCallback` — Back-gesture animation callback (extends the invoked hook).
- `OnBackInvokedCallback` — Back-gesture callback.
- `OnBackInvokedDispatcher` — Back-gesture callback registration: recorded, never invoked.

## org.isakai.bridge
- `IsakaiAudio` — JNI doorway to the Zig PulseAudio client ({@code libisakai_jni.so}). <p>Format: S16LE, rate/channels chosen per stream (see {@code nAudioOpenFormat}); {@code nAudioOpen} is the 44100/stereo default. {@code nAudioOpen} return a nonzero handle (0 on failure/unsupported format); {@code nAudioWrite} returns 0 on success, -1 on failure; {@code nAudioDrain} returns 0 on success, -1 on failure. The Java 
- `IsakaiDesktop` — JNI doorway to the Zig desktop integration (notifications, URIs, file picks, polls).
- `IsakaiWindow` — JNI doorway to the Zig Wayland client ({@code libisakai_jni.so}). <p>Two present paths: the copy path ({@link #nPost} from an ARGB {@code int[]}; native rewrites to XRGB) and the zero-copy path ({@link #nMapBuffer} hands out a direct {@link ByteBuffer} over SHM, Java draws into it, {@link #nCommit} presents it). The mapped buffer is only valid until commit/post; a resize remakes the native pool an

## org.isakai.db
- `IsakaiSQLite` — JNI doorway to the Zig SQLite bridge ({@code libisakai_jni.so}, {@code src/bridge/sqlite.zig} against the system libsqlite3). <p>Handles are native {@code sqlite3} / {@code sqlite3_stmt} pointers as jlongs (0 = failure). Opens use FULLMUTEX + a 5s busy timeout; database files are created 0600. Native errors surface as 0/negative returns and string messages; {@code SQLiteDatabase} translates them i

## org.isakai.prefs
- `IsakaiPrefsWork` — Single-thread prefs writer: apply() batches serialize here in call order. {@link #waitToFinish()} blocks until all queued writes complete; the Activity calls it from onPause/onStop/onDestroy so no write is lost.

## org.isakai.runtime
- `IsakaiMain` — Entry point for app runs. Args: &lt;data-dir&gt; &lt;app-class&gt;? &lt;activity-class&gt;? &lt;package&gt;? &lt;title&gt;? Instantiates Application (if named), attaches storage, runs the Activity lifecycle. Selects runWindowed when -Disakai.windowed=true, else runHeadless (M1). The title (5th arg) resolves to launcher label, else app label, else package.

## org.xmlpull.v1
- `XmlPullParser` — Pull-parser contract (Android subset of the XmlPull API).
- `XmlPullParserException` — Pull-parser failure (parse error or API misuse).
- `XmlSerializer` — Serializer contract (Android subset of the XmlPull API).

## Key gaps (absent, fail-closed)

- No `android.media.MediaCodec`, `MediaRecorder`, `AudioRecord`, `Camera`/`CameraDevice` (only the `camera2.CameraManager` enumeration shell), Bluetooth adapters (`BluetoothManager` absent shell), NFC hardware (`NfcManager` stub), telephony radio, or USB host I/O — all present classes in those packages are link-compatible shells returning null/empty/false.
- APKs with native code for non-host ABIs (e.g. `arm64-v8a`, `armeabi-v7a`, `x86`) are rejected with `NativeUnsupported` (`src/install.zig:inspect`); host `lib/x86_64/*.so` are staged RO and LD_PRELOADed with `libisakai_bionic_shim.so` (bionic log/property interposition, `src/native/bionic_shim.zig`, `src/launch.zig` + `src/uid_helper.zig`).
- APK Signature Scheme v2 only; P-521 remains `UnsupportedAlgorithm` (Zig 0.17 std has no secp521r1, `lib/std/crypto/pcurves` + `Certificate.zig:113` unimplemented); RSA >4096 rejected; multi-signer (up to 16, sorted+deduped) verified, upgrade pins via `signer_sha256_set` (`src/apksig.zig`, `src/upgrade.zig`).

## Translator boundary

DEX versions 35–40 parse (`src/dex.zig:parseHeader`); each class is emitted as JVM bytecode major v49 (pure inference verification, no StackMaps — JDK 27 enforces frames at v50; `src/translate.zig`). Unknown opcodes or malformed sections reject the class fail-closed (`UnsupportedOpcode` / `BadSection`); duplicate class names across `classesN.dex` are a loud `DuplicateClass` error (`src/launch.zig:translateApp`).

## Where to look for errors

- `inspect-test <apk>` — manifest/DEX summary or the rejection reason (native code, missing `classes.dex`, bad manifest).
- `verify-test <apk>` — v2 fingerprints (all signers, sorted) or the exact `SigError` (P-521, RSA>4096, digest/signature mismatch).
- `translate-test [--keep-going] <classes.dex> <outdir>` — per-class translation failures without aborting.
- `isakai launch` stderr — JVM discovery (`NoJava`), unreachable framework dir (`FrameworkUnreachable`), missing compositor socket (`NoWaylandSocket`), in-app `VerifyError` (translator scale ladder; see `docs/TESTING.md`).