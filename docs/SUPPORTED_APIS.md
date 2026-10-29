# Supported APIs

This inventory is maintained by hand alongside framework source paths. Presence here means an API surface exists, not that Android behavior is
complete. Some classes are shells returning null/empty/false or doing nothing;
those defaults can still break apps. Current task status and test evidence live
in [PROGRESS.md](../PROGRESS.md). This file is an API
reference, not a roadmap.
Maintained by hand alongside framework changes.
Behavioral limits below are maintained separately from the listed names.

## Important behavioral limits

- Activity windows persist until close/back; internal activity navigation is incomplete.
- Handler uses thread-owned queues, monotonic delays and cancellation; window dispatch polls at up to 100ms intervals.
- RelativeLayout resolves rules (parent alignment, sibling relations, alignment verbs, centering, margins, start/end direction) during measure/layout, with deterministic fallbacks: an anchor that is not yet placed falls back to the parent edge, so rule cycles terminate instead of looping. Pointer input supports click and long-click (listener-implied clickability, long-press armed on the main queue and cancelled by movement, up or cancel). Text now uses host Java2D shaping/fonts; Unicode editing has regressions, while compositor IME integration and target layout acceptance remain incomplete.
- Drawable layer IDs render; SRC_IN tint works for solid, gradient, bitmap, and filled vector paths. Vector path data supports standard SVG commands, nested group transforms, clipping, strokes (width, cap, join, miter limit, stroke alpha, painted as a separate outline pass over the fill) and trim paths (trimPathStart/trimPathEnd/trimPathOffset cut the flattened geometry). Rasterization samples 2x2 per pixel so thin strokes are not missed; coverage is binary (this rasterizer is not antialiased). Gradient fills, LayerDrawable parent tint, generic color-filter transforms, and non-SRC_IN tint modes remain incomplete. Ripple XML renders static content and retains its mask; animation is unavailable.
- SQLite helper version changes use transactions; custom cursor factories are rejected and prepared-statement caching is unavailable.
- WebView has no HTML renderer or JavaScript engine; its bridge parses selected call-shaped strings.
- Sensors and location expose no provider without a real host backend: no sensor is advertised, registerListener returns false and location reports providers disabled (no fabricated fixes). Synthetic accelerometer/light/location readings exist only behind the explicit test switches `ISAKAI_FAKE_SENSORS=1` / `ISAKAI_FAKE_LOCATION=1` (or `-Disakai.fakeSensors=1` / `-Disakai.fakeLocation=1`); real iio-sensor-proxy/GeoClue2 discovery is still unwired.
- MediaPlayer is partial local-file audio playback, with bounded whole-file decode and incomplete looping. Host decoder processes (ffmpeg/ffprobe) run under a supervisor: the deadline starts before the process is awaited, stdout/stderr drain concurrently with bounded buffers, stderr keeps the tail, and a hung or cancelled child is killed and reaped (stdin is closed so no decoder can block on input).
- Shared external storage, tooltip display and Paint transfer-mode rendering are unavailable and emit symbolic diagnostics; app-scoped storage remains writable.
- Native support is a small symbol shim, not a general bionic compatibility layer.

<!-- Hand-maintained: source presence is not behavioral support. -->

## android.accessibilityservice

- [`AccessibilityServiceInfo`](../framework/android/accessibilityservice/AccessibilityServiceInfo.java)

## android.accounts

- [`Account`](../framework/android/accounts/Account.java)
- [`AccountManager`](../framework/android/accounts/AccountManager.java)
- [`AccountsException`](../framework/android/accounts/AccountsException.java)
- [`NetworkErrorException`](../framework/android/accounts/NetworkErrorException.java)

## android.animation

- [`Animator`](../framework/android/animation/Animator.java)
- [`AnimatorListenerAdapter`](../framework/android/animation/AnimatorListenerAdapter.java)
- [`AnimatorSet`](../framework/android/animation/AnimatorSet.java)
- [`ArgbEvaluator`](../framework/android/animation/ArgbEvaluator.java)
- [`FloatEvaluator`](../framework/android/animation/FloatEvaluator.java)
- [`Keyframe`](../framework/android/animation/Keyframe.java)
- [`LayoutTransition`](../framework/android/animation/LayoutTransition.java)
- [`ObjectAnimator`](../framework/android/animation/ObjectAnimator.java)
- [`PropertyValuesHolder`](../framework/android/animation/PropertyValuesHolder.java)
- [`StateListAnimator`](../framework/android/animation/StateListAnimator.java)
- [`TimeInterpolator`](../framework/android/animation/TimeInterpolator.java)
- [`TypeEvaluator`](../framework/android/animation/TypeEvaluator.java)
- [`ValueAnimator`](../framework/android/animation/ValueAnimator.java)

## android.app

- [`ActionBar`](../framework/android/app/ActionBar.java)
- [`Activity`](../framework/android/app/Activity.java)
- [`ActivityManager`](../framework/android/app/ActivityManager.java)
- [`AlarmManager`](../framework/android/app/AlarmManager.java)
- [`AppComponentFactory`](../framework/android/app/AppComponentFactory.java)
- [`AppOpsManager`](../framework/android/app/AppOpsManager.java)
- [`Application`](../framework/android/app/Application.java)
- [`Dialog`](../framework/android/app/Dialog.java)
- [`DownloadManager`](../framework/android/app/DownloadManager.java)
- [`Fragment`](../framework/android/app/Fragment.java)
- [`FragmentManager`](../framework/android/app/FragmentManager.java)
- [`FragmentTransaction`](../framework/android/app/FragmentTransaction.java)
- [`HeadlessWindow`](../framework/android/app/HeadlessWindow.java)
- [`KeyguardManager`](../framework/android/app/KeyguardManager.java)
- [`LocaleManager`](../framework/android/app/LocaleManager.java)
- [`Notification`](../framework/android/app/Notification.java)
- [`NotificationChannel`](../framework/android/app/NotificationChannel.java)
- [`NotificationManager`](../framework/android/app/NotificationManager.java)
- [`PendingIntent`](../framework/android/app/PendingIntent.java)
- [`SearchManager`](../framework/android/app/SearchManager.java)
- [`Service`](../framework/android/app/Service.java)
- [`SharedElementCallback`](../framework/android/app/SharedElementCallback.java)
- [`UiModeManager`](../framework/android/app/UiModeManager.java)
- [`WallpaperManager`](../framework/android/app/WallpaperManager.java)

## android.app.admin

- [`DevicePolicyManager`](../framework/android/app/admin/DevicePolicyManager.java)

## android.app.job

- [`JobScheduler`](../framework/android/app/job/JobScheduler.java)

## android.app.role

- [`RoleManager`](../framework/android/app/role/RoleManager.java)

## android.app.usage

- [`UsageStatsManager`](../framework/android/app/usage/UsageStatsManager.java)

## android.appwidget

- [`AppWidgetManager`](../framework/android/appwidget/AppWidgetManager.java)

## android.bluetooth

- [`BluetoothManager`](../framework/android/bluetooth/BluetoothManager.java)

## android.content

- [`ActivityNotFoundException`](../framework/android/content/ActivityNotFoundException.java)
- [`BroadcastReceiver`](../framework/android/content/BroadcastReceiver.java)
- [`ClipData`](../framework/android/content/ClipData.java)
- [`ClipDescription`](../framework/android/content/ClipDescription.java)
- [`ClipboardManager`](../framework/android/content/ClipboardManager.java)
- [`ComponentCallbacks`](../framework/android/content/ComponentCallbacks.java)
- [`ComponentCallbacks2`](../framework/android/content/ComponentCallbacks2.java)
- [`ComponentName`](../framework/android/content/ComponentName.java)
- [`ContentProvider`](../framework/android/content/ContentProvider.java)
- [`ContentProviderClient`](../framework/android/content/ContentProviderClient.java)
- [`ContentProviderOperation`](../framework/android/content/ContentProviderOperation.java)
- [`ContentProviderResult`](../framework/android/content/ContentProviderResult.java)
- [`ContentResolver`](../framework/android/content/ContentResolver.java)
- [`ContentValues`](../framework/android/content/ContentValues.java)
- [`Context`](../framework/android/content/Context.java)
- [`ContextWrapper`](../framework/android/content/ContextWrapper.java)
- [`DialogInterface`](../framework/android/content/DialogInterface.java)
- [`Intent`](../framework/android/content/Intent.java)
- [`IntentFilter`](../framework/android/content/IntentFilter.java)
- [`IntentSender`](../framework/android/content/IntentSender.java)
- [`IsakaiPackageManager`](../framework/android/content/IsakaiPackageManager.java)
- [`OperationApplicationException`](../framework/android/content/OperationApplicationException.java)
- [`RestrictionsManager`](../framework/android/content/RestrictionsManager.java)
- [`ServiceConnection`](../framework/android/content/ServiceConnection.java)
- [`SharedPreferences`](../framework/android/content/SharedPreferences.java)
- [`SharedPreferencesImpl`](../framework/android/content/SharedPreferencesImpl.java)
- [`UriMatcher`](../framework/android/content/UriMatcher.java)
- [`UriPermission`](../framework/android/content/UriPermission.java)

## android.content.pm

- [`ActivityInfo`](../framework/android/content/pm/ActivityInfo.java)
- [`ApplicationInfo`](../framework/android/content/pm/ApplicationInfo.java)
- [`ComponentInfo`](../framework/android/content/pm/ComponentInfo.java)
- [`LauncherApps`](../framework/android/content/pm/LauncherApps.java)
- [`PackageInfo`](../framework/android/content/pm/PackageInfo.java)
- [`PackageItemInfo`](../framework/android/content/pm/PackageItemInfo.java)
- [`PackageManager`](../framework/android/content/pm/PackageManager.java)
- [`ProviderInfo`](../framework/android/content/pm/ProviderInfo.java)
- [`ResolveInfo`](../framework/android/content/pm/ResolveInfo.java)
- [`ServiceInfo`](../framework/android/content/pm/ServiceInfo.java)
- [`ShortcutInfo`](../framework/android/content/pm/ShortcutInfo.java)
- [`ShortcutManager`](../framework/android/content/pm/ShortcutManager.java)
- [`Signature`](../framework/android/content/pm/Signature.java)

## android.content.res

- [`AssetFileDescriptor`](../framework/android/content/res/AssetFileDescriptor.java)
- [`AssetManager`](../framework/android/content/res/AssetManager.java)
- [`ColorStateList`](../framework/android/content/res/ColorStateList.java)
- [`Configuration`](../framework/android/content/res/Configuration.java)
- [`Resources`](../framework/android/content/res/Resources.java)
- [`SystemColors`](../framework/android/content/res/SystemColors.java)
- [`TypedArray`](../framework/android/content/res/TypedArray.java)
- [`XmlResourceParser`](../framework/android/content/res/XmlResourceParser.java)

## android.database

- [`ContentObserver`](../framework/android/database/ContentObserver.java)
- [`Cursor`](../framework/android/database/Cursor.java)
- [`CursorIndexOutOfBoundsException`](../framework/android/database/CursorIndexOutOfBoundsException.java)
- [`DataSetObservable`](../framework/android/database/DataSetObservable.java)
- [`DataSetObserver`](../framework/android/database/DataSetObserver.java)
- [`DatabaseErrorHandler`](../framework/android/database/DatabaseErrorHandler.java)
- [`DatabaseUtils`](../framework/android/database/DatabaseUtils.java)
- [`MatrixCursor`](../framework/android/database/MatrixCursor.java)
- [`MergeCursor`](../framework/android/database/MergeCursor.java)
- [`Observable`](../framework/android/database/Observable.java)
- [`SQLException`](../framework/android/database/SQLException.java)

## android.database.sqlite

- [`SQLiteClosable`](../framework/android/database/sqlite/SQLiteClosable.java)
- [`SQLiteConstraintException`](../framework/android/database/sqlite/SQLiteConstraintException.java)
- [`SQLiteCursor`](../framework/android/database/sqlite/SQLiteCursor.java)
- [`SQLiteCursorDriver`](../framework/android/database/sqlite/SQLiteCursorDriver.java)
- [`SQLiteDatabase`](../framework/android/database/sqlite/SQLiteDatabase.java)
- [`SQLiteDatabaseLockedException`](../framework/android/database/sqlite/SQLiteDatabaseLockedException.java)
- [`SQLiteException`](../framework/android/database/sqlite/SQLiteException.java)
- [`SQLiteOpenHelper`](../framework/android/database/sqlite/SQLiteOpenHelper.java)
- [`SQLiteProgram`](../framework/android/database/sqlite/SQLiteProgram.java)
- [`SQLiteQuery`](../framework/android/database/sqlite/SQLiteQuery.java)
- [`SQLiteStatement`](../framework/android/database/sqlite/SQLiteStatement.java)

## android.graphics

- [`Bitmap`](../framework/android/graphics/Bitmap.java)
- [`BitmapFactory`](../framework/android/graphics/BitmapFactory.java)
- [`BitmapFont`](../framework/android/graphics/BitmapFont.java)
- [`BitmapShader`](../framework/android/graphics/BitmapShader.java)
- [`BlendMode`](../framework/android/graphics/BlendMode.java)
- [`BlendModeColorFilter`](../framework/android/graphics/BlendModeColorFilter.java)
- [`Canvas`](../framework/android/graphics/Canvas.java)
- [`Color`](../framework/android/graphics/Color.java)
- [`ColorFilter`](../framework/android/graphics/ColorFilter.java)
- [`ColorSpace`](../framework/android/graphics/ColorSpace.java)
- [`ComposeShader`](../framework/android/graphics/ComposeShader.java)
- [`HostText`](../framework/android/graphics/HostText.java)
- [`ImageDecoder`](../framework/android/graphics/ImageDecoder.java)
- [`Insets`](../framework/android/graphics/Insets.java)
- [`LinearGradient`](../framework/android/graphics/LinearGradient.java)
- [`Matrix`](../framework/android/graphics/Matrix.java)
- [`Outline`](../framework/android/graphics/Outline.java)
- [`Paint`](../framework/android/graphics/Paint.java)
- [`Path`](../framework/android/graphics/Path.java)
- [`PathMeasure`](../framework/android/graphics/PathMeasure.java)
- [`Picture`](../framework/android/graphics/Picture.java)
- [`PixelFormat`](../framework/android/graphics/PixelFormat.java)
- [`Point`](../framework/android/graphics/Point.java)
- [`PointF`](../framework/android/graphics/PointF.java)
- [`PorterDuff`](../framework/android/graphics/PorterDuff.java)
- [`PorterDuffColorFilter`](../framework/android/graphics/PorterDuffColorFilter.java)
- [`PorterDuffXfermode`](../framework/android/graphics/PorterDuffXfermode.java)
- [`RadialGradient`](../framework/android/graphics/RadialGradient.java)
- [`RecordingCanvas`](../framework/android/graphics/RecordingCanvas.java)
- [`Rect`](../framework/android/graphics/Rect.java)
- [`RectF`](../framework/android/graphics/RectF.java)
- [`Region`](../framework/android/graphics/Region.java)
- [`RenderEffect`](../framework/android/graphics/RenderEffect.java)
- [`RenderNode`](../framework/android/graphics/RenderNode.java)
- [`Shader`](../framework/android/graphics/Shader.java)
- [`SweepGradient`](../framework/android/graphics/SweepGradient.java)
- [`Typeface`](../framework/android/graphics/Typeface.java)
- [`Xfermode`](../framework/android/graphics/Xfermode.java)

## android.graphics.drawable

- [`Animatable`](../framework/android/graphics/drawable/Animatable.java)
- [`Animatable2`](../framework/android/graphics/drawable/Animatable2.java)
- [`AnimatedImageDrawable`](../framework/android/graphics/drawable/AnimatedImageDrawable.java)
- [`AnimatedStateListDrawable`](../framework/android/graphics/drawable/AnimatedStateListDrawable.java)
- [`AnimatedVectorDrawable`](../framework/android/graphics/drawable/AnimatedVectorDrawable.java)
- [`AnimationDrawable`](../framework/android/graphics/drawable/AnimationDrawable.java)
- [`BitmapDrawable`](../framework/android/graphics/drawable/BitmapDrawable.java)
- [`ClipDrawable`](../framework/android/graphics/drawable/ClipDrawable.java)
- [`ColorDrawable`](../framework/android/graphics/drawable/ColorDrawable.java)
- [`ColorStateListDrawable`](../framework/android/graphics/drawable/ColorStateListDrawable.java)
- [`Drawable`](../framework/android/graphics/drawable/Drawable.java)
- [`DrawableContainer`](../framework/android/graphics/drawable/DrawableContainer.java)
- [`GradientDrawable`](../framework/android/graphics/drawable/GradientDrawable.java)
- [`Icon`](../framework/android/graphics/drawable/Icon.java)
- [`InsetDrawable`](../framework/android/graphics/drawable/InsetDrawable.java)
- [`LayerDrawable`](../framework/android/graphics/drawable/LayerDrawable.java)
- [`RippleDrawable`](../framework/android/graphics/drawable/RippleDrawable.java)
- [`ShapeDrawable`](../framework/android/graphics/drawable/ShapeDrawable.java)
- [`StateListDrawable`](../framework/android/graphics/drawable/StateListDrawable.java)
- [`TransitionDrawable`](../framework/android/graphics/drawable/TransitionDrawable.java)
- [`VectorDrawable`](../framework/android/graphics/drawable/VectorDrawable.java)
- [`VectorPathParser`](../framework/android/graphics/drawable/VectorPathParser.java)

## android.graphics.drawable.shapes

- [`OvalShape`](../framework/android/graphics/drawable/shapes/OvalShape.java)
- [`RectShape`](../framework/android/graphics/drawable/shapes/RectShape.java)
- [`RoundRectShape`](../framework/android/graphics/drawable/shapes/RoundRectShape.java)
- [`Shape`](../framework/android/graphics/drawable/shapes/Shape.java)

## android.graphics.fonts

- [`Font`](../framework/android/graphics/fonts/Font.java)
- [`FontFamily`](../framework/android/graphics/fonts/FontFamily.java)
- [`FontStyle`](../framework/android/graphics/fonts/FontStyle.java)

## android.graphics.text

- [`LineBreakConfig`](../framework/android/graphics/text/LineBreakConfig.java)

## android.hardware

- [`ConsumerIrManager`](../framework/android/hardware/ConsumerIrManager.java)
- [`Sensor`](../framework/android/hardware/Sensor.java)
- [`SensorEvent`](../framework/android/hardware/SensorEvent.java)
- [`SensorEventListener`](../framework/android/hardware/SensorEventListener.java)
- [`SensorManager`](../framework/android/hardware/SensorManager.java)

## android.hardware.biometrics

- [`BiometricManager`](../framework/android/hardware/biometrics/BiometricManager.java)
- [`BiometricPrompt`](../framework/android/hardware/biometrics/BiometricPrompt.java)

## android.hardware.camera2

- [`CameraManager`](../framework/android/hardware/camera2/CameraManager.java)

## android.hardware.display

- [`DisplayManager`](../framework/android/hardware/display/DisplayManager.java)

## android.hardware.fingerprint

- [`FingerprintManager`](../framework/android/hardware/fingerprint/FingerprintManager.java)

## android.hardware.input

- [`InputManager`](../framework/android/hardware/input/InputManager.java)

## android.hardware.usb

- [`UsbDevice`](../framework/android/hardware/usb/UsbDevice.java)
- [`UsbEndpoint`](../framework/android/hardware/usb/UsbEndpoint.java)
- [`UsbInterface`](../framework/android/hardware/usb/UsbInterface.java)
- [`UsbManager`](../framework/android/hardware/usb/UsbManager.java)

## android.icu.util

- [`ULocale`](../framework/android/icu/util/ULocale.java)

## android.location

- [`Criteria`](../framework/android/location/Criteria.java)
- [`Location`](../framework/android/location/Location.java)
- [`LocationListener`](../framework/android/location/LocationListener.java)
- [`LocationManager`](../framework/android/location/LocationManager.java)

## android.media

- [`AudioManager`](../framework/android/media/AudioManager.java)
- [`MediaDataSource`](../framework/android/media/MediaDataSource.java)
- [`MediaExtractor`](../framework/android/media/MediaExtractor.java)
- [`MediaFormat`](../framework/android/media/MediaFormat.java)
- [`MediaMetadataRetriever`](../framework/android/media/MediaMetadataRetriever.java)
- [`MediaPlayer`](../framework/android/media/MediaPlayer.java)
- [`MediaRouter`](../framework/android/media/MediaRouter.java)
- [`MediaScannerConnection`](../framework/android/media/MediaScannerConnection.java)
- [`SoundPool`](../framework/android/media/SoundPool.java)

## android.media.projection

- [`MediaProjectionManager`](../framework/android/media/projection/MediaProjectionManager.java)

## android.media.session

- [`MediaSessionManager`](../framework/android/media/session/MediaSessionManager.java)

## android.media.tv

- [`TvInputManager`](../framework/android/media/tv/TvInputManager.java)

## android.net

- [`ConnectivityManager`](../framework/android/net/ConnectivityManager.java)
- [`Network`](../framework/android/net/Network.java)
- [`Uri`](../framework/android/net/Uri.java)

## android.net.nsd

- [`NsdManager`](../framework/android/net/nsd/NsdManager.java)

## android.net.wifi

- [`WifiManager`](../framework/android/net/wifi/WifiManager.java)

## android.net.wifi.p2p

- [`WifiP2pManager`](../framework/android/net/wifi/p2p/WifiP2pManager.java)

## android.nfc

- [`NfcManager`](../framework/android/nfc/NfcManager.java)

## android.os

- [`BaseBundle`](../framework/android/os/BaseBundle.java)
- [`BatteryManager`](../framework/android/os/BatteryManager.java)
- [`Binder`](../framework/android/os/Binder.java)
- [`Build`](../framework/android/os/Build.java)
- [`Bundle`](../framework/android/os/Bundle.java)
- [`CancellationSignal`](../framework/android/os/CancellationSignal.java)
- [`DropBoxManager`](../framework/android/os/DropBoxManager.java)
- [`Environment`](../framework/android/os/Environment.java)
- [`Handler`](../framework/android/os/Handler.java)
- [`IBinder`](../framework/android/os/IBinder.java)
- [`LocaleList`](../framework/android/os/LocaleList.java)
- [`Looper`](../framework/android/os/Looper.java)
- [`Message`](../framework/android/os/Message.java)
- [`OperationCanceledException`](../framework/android/os/OperationCanceledException.java)
- [`Parcel`](../framework/android/os/Parcel.java)
- [`ParcelFileDescriptor`](../framework/android/os/ParcelFileDescriptor.java)
- [`Parcelable`](../framework/android/os/Parcelable.java)
- [`PowerManager`](../framework/android/os/PowerManager.java)
- [`RemoteException`](../framework/android/os/RemoteException.java)
- [`ResultReceiver`](../framework/android/os/ResultReceiver.java)
- [`StrictMode`](../framework/android/os/StrictMode.java)
- [`SystemClock`](../framework/android/os/SystemClock.java)
- [`Trace`](../framework/android/os/Trace.java)
- [`UserManager`](../framework/android/os/UserManager.java)
- [`Vibrator`](../framework/android/os/Vibrator.java)

## android.os.ext

- [`SdkExtensions`](../framework/android/os/ext/SdkExtensions.java)

## android.os.storage

- [`StorageManager`](../framework/android/os/storage/StorageManager.java)

## android.print

- [`PrintManager`](../framework/android/print/PrintManager.java)

## android.provider

- [`Settings`](../framework/android/provider/Settings.java)

## android.security.identity

- [`IdentityCredential`](../framework/android/security/identity/IdentityCredential.java)
- [`PresentationSession`](../framework/android/security/identity/PresentationSession.java)

## android.security.keystore

- [`KeyGenParameterSpec`](../framework/android/security/keystore/KeyGenParameterSpec.java)

## android.service.quicksettings

- [`TileService`](../framework/android/service/quicksettings/TileService.java)

## android.telecom

- [`TelecomManager`](../framework/android/telecom/TelecomManager.java)

## android.telephony

- [`SubscriptionManager`](../framework/android/telephony/SubscriptionManager.java)
- [`TelephonyManager`](../framework/android/telephony/TelephonyManager.java)

## android.text

- [`Annotation`](../framework/android/text/Annotation.java)
- [`BoringLayout`](../framework/android/text/BoringLayout.java)
- [`Editable`](../framework/android/text/Editable.java)
- [`GetChars`](../framework/android/text/GetChars.java)
- [`Html`](../framework/android/text/Html.java)
- [`InputFilter`](../framework/android/text/InputFilter.java)
- [`Layout`](../framework/android/text/Layout.java)
- [`PrecomputedText`](../framework/android/text/PrecomputedText.java)
- [`Selection`](../framework/android/text/Selection.java)
- [`SpanWatcher`](../framework/android/text/SpanWatcher.java)
- [`Spannable`](../framework/android/text/Spannable.java)
- [`SpannableString`](../framework/android/text/SpannableString.java)
- [`SpannableStringBuilder`](../framework/android/text/SpannableStringBuilder.java)
- [`Spanned`](../framework/android/text/Spanned.java)
- [`StaticLayout`](../framework/android/text/StaticLayout.java)
- [`TextDirectionHeuristic`](../framework/android/text/TextDirectionHeuristic.java)
- [`TextDirectionHeuristics`](../framework/android/text/TextDirectionHeuristics.java)
- [`TextPaint`](../framework/android/text/TextPaint.java)
- [`TextUtils`](../framework/android/text/TextUtils.java)
- [`TextWatcher`](../framework/android/text/TextWatcher.java)

## android.text.format

- [`DateUtils`](../framework/android/text/format/DateUtils.java)

## android.text.method

- [`ArrowKeyMovementMethod`](../framework/android/text/method/ArrowKeyMovementMethod.java)
- [`BaseMovementMethod`](../framework/android/text/method/BaseMovementMethod.java)
- [`KeyListener`](../framework/android/text/method/KeyListener.java)
- [`LinkMovementMethod`](../framework/android/text/method/LinkMovementMethod.java)
- [`MetaKeyKeyListener`](../framework/android/text/method/MetaKeyKeyListener.java)
- [`MovementMethod`](../framework/android/text/method/MovementMethod.java)
- [`NumberKeyListener`](../framework/android/text/method/NumberKeyListener.java)
- [`PasswordTransformationMethod`](../framework/android/text/method/PasswordTransformationMethod.java)
- [`ScrollingMovementMethod`](../framework/android/text/method/ScrollingMovementMethod.java)
- [`SingleLineTransformationMethod`](../framework/android/text/method/SingleLineTransformationMethod.java)
- [`TransformationMethod`](../framework/android/text/method/TransformationMethod.java)

## android.text.style

- [`AbsoluteSizeSpan`](../framework/android/text/style/AbsoluteSizeSpan.java)
- [`BackgroundColorSpan`](../framework/android/text/style/BackgroundColorSpan.java)
- [`CharacterStyle`](../framework/android/text/style/CharacterStyle.java)
- [`ClickableSpan`](../framework/android/text/style/ClickableSpan.java)
- [`ForegroundColorSpan`](../framework/android/text/style/ForegroundColorSpan.java)
- [`ImageSpan`](../framework/android/text/style/ImageSpan.java)
- [`LeadingMarginSpan`](../framework/android/text/style/LeadingMarginSpan.java)
- [`LocaleSpan`](../framework/android/text/style/LocaleSpan.java)
- [`MetricAffectingSpan`](../framework/android/text/style/MetricAffectingSpan.java)
- [`RelativeSizeSpan`](../framework/android/text/style/RelativeSizeSpan.java)
- [`ReplacementSpan`](../framework/android/text/style/ReplacementSpan.java)
- [`ScaleXSpan`](../framework/android/text/style/ScaleXSpan.java)
- [`StrikethroughSpan`](../framework/android/text/style/StrikethroughSpan.java)
- [`StyleSpan`](../framework/android/text/style/StyleSpan.java)
- [`TextAppearanceSpan`](../framework/android/text/style/TextAppearanceSpan.java)
- [`TtsSpan`](../framework/android/text/style/TtsSpan.java)
- [`URLSpan`](../framework/android/text/style/URLSpan.java)
- [`UnderlineSpan`](../framework/android/text/style/UnderlineSpan.java)

## android.transition

- [`Transition`](../framework/android/transition/Transition.java)
- [`TransitionSet`](../framework/android/transition/TransitionSet.java)

## android.util

- [`AndroidException`](../framework/android/util/AndroidException.java)
- [`AndroidRuntimeException`](../framework/android/util/AndroidRuntimeException.java)
- [`AttributeSet`](../framework/android/util/AttributeSet.java)
- [`DisplayMetrics`](../framework/android/util/DisplayMetrics.java)
- [`LayoutDirection`](../framework/android/util/LayoutDirection.java)
- [`Log`](../framework/android/util/Log.java)
- [`LongSparseArray`](../framework/android/util/LongSparseArray.java)
- [`Pair`](../framework/android/util/Pair.java)
- [`Property`](../framework/android/util/Property.java)
- [`Size`](../framework/android/util/Size.java)
- [`SizeF`](../framework/android/util/SizeF.java)
- [`SparseArray`](../framework/android/util/SparseArray.java)
- [`SparseBooleanArray`](../framework/android/util/SparseBooleanArray.java)
- [`SparseIntArray`](../framework/android/util/SparseIntArray.java)
- [`SparseLongArray`](../framework/android/util/SparseLongArray.java)
- [`StateSet`](../framework/android/util/StateSet.java)
- [`TypedValue`](../framework/android/util/TypedValue.java)
- [`Xml`](../framework/android/util/Xml.java)

## android.view

- [`AbsSavedState`](../framework/android/view/AbsSavedState.java)
- [`ActionMode`](../framework/android/view/ActionMode.java)
- [`ActionProvider`](../framework/android/view/ActionProvider.java)
- [`Choreographer`](../framework/android/view/Choreographer.java)
- [`CollapsibleActionView`](../framework/android/view/CollapsibleActionView.java)
- [`ContentInfo`](../framework/android/view/ContentInfo.java)
- [`ContextMenu`](../framework/android/view/ContextMenu.java)
- [`ContextThemeWrapper`](../framework/android/view/ContextThemeWrapper.java)
- [`Display`](../framework/android/view/Display.java)
- [`DisplayCutout`](../framework/android/view/DisplayCutout.java)
- [`DisplayListCanvas`](../framework/android/view/DisplayListCanvas.java)
- [`DragEvent`](../framework/android/view/DragEvent.java)
- [`FocusFinder`](../framework/android/view/FocusFinder.java)
- [`GestureDetector`](../framework/android/view/GestureDetector.java)
- [`Gravity`](../framework/android/view/Gravity.java)
- [`InflateException`](../framework/android/view/InflateException.java)
- [`InputDevice`](../framework/android/view/InputDevice.java)
- [`InputEvent`](../framework/android/view/InputEvent.java)
- [`KeyCharacterMap`](../framework/android/view/KeyCharacterMap.java)
- [`KeyEvent`](../framework/android/view/KeyEvent.java)
- [`KeyboardShortcutGroup`](../framework/android/view/KeyboardShortcutGroup.java)
- [`LayoutInflater`](../framework/android/view/LayoutInflater.java)
- [`MeasureSpec`](../framework/android/view/MeasureSpec.java)
- [`Menu`](../framework/android/view/Menu.java)
- [`MenuInflater`](../framework/android/view/MenuInflater.java)
- [`MenuItem`](../framework/android/view/MenuItem.java)
- [`MotionEvent`](../framework/android/view/MotionEvent.java)
- [`OnReceiveContentListener`](../framework/android/view/OnReceiveContentListener.java)
- [`PointerIcon`](../framework/android/view/PointerIcon.java)
- [`RoundedCorner`](../framework/android/view/RoundedCorner.java)
- [`ScaleGestureDetector`](../framework/android/view/ScaleGestureDetector.java)
- [`SearchEvent`](../framework/android/view/SearchEvent.java)
- [`SubMenu`](../framework/android/view/SubMenu.java)
- [`VelocityTracker`](../framework/android/view/VelocityTracker.java)
- [`View`](../framework/android/view/View.java)
- [`ViewConfiguration`](../framework/android/view/ViewConfiguration.java)
- [`ViewGroup`](../framework/android/view/ViewGroup.java)
- [`ViewGroupOverlay`](../framework/android/view/ViewGroupOverlay.java)
- [`ViewManager`](../framework/android/view/ViewManager.java)
- [`ViewOutlineProvider`](../framework/android/view/ViewOutlineProvider.java)
- [`ViewParent`](../framework/android/view/ViewParent.java)
- [`ViewPropertyAnimator`](../framework/android/view/ViewPropertyAnimator.java)
- [`ViewStructure`](../framework/android/view/ViewStructure.java)
- [`ViewStub`](../framework/android/view/ViewStub.java)
- [`ViewTreeObserver`](../framework/android/view/ViewTreeObserver.java)
- [`Window`](../framework/android/view/Window.java)
- [`WindowInsets`](../framework/android/view/WindowInsets.java)
- [`WindowInsetsAnimation`](../framework/android/view/WindowInsetsAnimation.java)
- [`WindowInsetsController`](../framework/android/view/WindowInsetsController.java)
- [`WindowManager`](../framework/android/view/WindowManager.java)

## android.view.accessibility

- [`AccessibilityEvent`](../framework/android/view/accessibility/AccessibilityEvent.java)
- [`AccessibilityManager`](../framework/android/view/accessibility/AccessibilityManager.java)
- [`AccessibilityNodeInfo`](../framework/android/view/accessibility/AccessibilityNodeInfo.java)
- [`AccessibilityNodeProvider`](../framework/android/view/accessibility/AccessibilityNodeProvider.java)
- [`AccessibilityRecord`](../framework/android/view/accessibility/AccessibilityRecord.java)
- [`CaptioningManager`](../framework/android/view/accessibility/CaptioningManager.java)

## android.view.animation

- [`AccelerateDecelerateInterpolator`](../framework/android/view/animation/AccelerateDecelerateInterpolator.java)
- [`AccelerateInterpolator`](../framework/android/view/animation/AccelerateInterpolator.java)
- [`Animation`](../framework/android/view/animation/Animation.java)
- [`AnimationSet`](../framework/android/view/animation/AnimationSet.java)
- [`AnimationUtils`](../framework/android/view/animation/AnimationUtils.java)
- [`DecelerateInterpolator`](../framework/android/view/animation/DecelerateInterpolator.java)
- [`Interpolator`](../framework/android/view/animation/Interpolator.java)
- [`LinearInterpolator`](../framework/android/view/animation/LinearInterpolator.java)
- [`PathInterpolator`](../framework/android/view/animation/PathInterpolator.java)
- [`Transformation`](../framework/android/view/animation/Transformation.java)

## android.view.autofill

- [`AutofillId`](../framework/android/view/autofill/AutofillId.java)
- [`AutofillManager`](../framework/android/view/autofill/AutofillManager.java)
- [`AutofillValue`](../framework/android/view/autofill/AutofillValue.java)

## android.view.contentcapture

- [`ContentCaptureSession`](../framework/android/view/contentcapture/ContentCaptureSession.java)

## android.view.inputmethod

- [`BaseInputConnection`](../framework/android/view/inputmethod/BaseInputConnection.java)
- [`CursorAnchorInfo`](../framework/android/view/inputmethod/CursorAnchorInfo.java)
- [`EditorBoundsInfo`](../framework/android/view/inputmethod/EditorBoundsInfo.java)
- [`EditorInfo`](../framework/android/view/inputmethod/EditorInfo.java)
- [`ExtractedText`](../framework/android/view/inputmethod/ExtractedText.java)
- [`InputConnection`](../framework/android/view/inputmethod/InputConnection.java)
- [`InputConnectionWrapper`](../framework/android/view/inputmethod/InputConnectionWrapper.java)
- [`InputContentInfo`](../framework/android/view/inputmethod/InputContentInfo.java)
- [`InputMethodManager`](../framework/android/view/inputmethod/InputMethodManager.java)

## android.view.textclassifier

- [`TextClassificationManager`](../framework/android/view/textclassifier/TextClassificationManager.java)
- [`TextClassifier`](../framework/android/view/textclassifier/TextClassifier.java)

## android.view.textservice

- [`TextServicesManager`](../framework/android/view/textservice/TextServicesManager.java)

## android.view.translation

- [`ViewTranslationCallback`](../framework/android/view/translation/ViewTranslationCallback.java)
- [`ViewTranslationRequest`](../framework/android/view/translation/ViewTranslationRequest.java)
- [`ViewTranslationResponse`](../framework/android/view/translation/ViewTranslationResponse.java)

## android.webkit

- [`CookieManager`](../framework/android/webkit/CookieManager.java)
- [`JavascriptInterface`](../framework/android/webkit/JavascriptInterface.java)
- [`ValueCallback`](../framework/android/webkit/ValueCallback.java)
- [`WebResourceError`](../framework/android/webkit/WebResourceError.java)
- [`WebResourceRequest`](../framework/android/webkit/WebResourceRequest.java)
- [`WebResourceResponse`](../framework/android/webkit/WebResourceResponse.java)
- [`WebSettings`](../framework/android/webkit/WebSettings.java)
- [`WebView`](../framework/android/webkit/WebView.java)
- [`WebViewClient`](../framework/android/webkit/WebViewClient.java)

## android.widget

- [`AbsListView`](../framework/android/widget/AbsListView.java)
- [`AbsSeekBar`](../framework/android/widget/AbsSeekBar.java)
- [`Adapter`](../framework/android/widget/Adapter.java)
- [`AdapterView`](../framework/android/widget/AdapterView.java)
- [`ArrayAdapter`](../framework/android/widget/ArrayAdapter.java)
- [`AutoCompleteTextView`](../framework/android/widget/AutoCompleteTextView.java)
- [`BaseAdapter`](../framework/android/widget/BaseAdapter.java)
- [`Button`](../framework/android/widget/Button.java)
- [`CheckBox`](../framework/android/widget/CheckBox.java)
- [`Checkable`](../framework/android/widget/Checkable.java)
- [`CheckedTextView`](../framework/android/widget/CheckedTextView.java)
- [`Chronometer`](../framework/android/widget/Chronometer.java)
- [`CompoundButton`](../framework/android/widget/CompoundButton.java)
- [`DateTimeView`](../framework/android/widget/DateTimeView.java)
- [`EdgeEffect`](../framework/android/widget/EdgeEffect.java)
- [`EditText`](../framework/android/widget/EditText.java)
- [`Filter`](../framework/android/widget/Filter.java)
- [`Filterable`](../framework/android/widget/Filterable.java)
- [`FrameLayout`](../framework/android/widget/FrameLayout.java)
- [`GridView`](../framework/android/widget/GridView.java)
- [`HeaderViewListAdapter`](../framework/android/widget/HeaderViewListAdapter.java)
- [`HorizontalScrollView`](../framework/android/widget/HorizontalScrollView.java)
- [`ImageButton`](../framework/android/widget/ImageButton.java)
- [`ImageView`](../framework/android/widget/ImageView.java)
- [`LinearLayout`](../framework/android/widget/LinearLayout.java)
- [`ListAdapter`](../framework/android/widget/ListAdapter.java)
- [`ListView`](../framework/android/widget/ListView.java)
- [`Magnifier`](../framework/android/widget/Magnifier.java)
- [`MultiAutoCompleteTextView`](../framework/android/widget/MultiAutoCompleteTextView.java)
- [`OverScroller`](../framework/android/widget/OverScroller.java)
- [`PopupWindow`](../framework/android/widget/PopupWindow.java)
- [`ProgressBar`](../framework/android/widget/ProgressBar.java)
- [`RadioButton`](../framework/android/widget/RadioButton.java)
- [`RadioGroup`](../framework/android/widget/RadioGroup.java)
- [`RatingBar`](../framework/android/widget/RatingBar.java)
- [`RelativeLayout`](../framework/android/widget/RelativeLayout.java)
- [`RemoteViews`](../framework/android/widget/RemoteViews.java)
- [`ScrollView`](../framework/android/widget/ScrollView.java)
- [`Scroller`](../framework/android/widget/Scroller.java)
- [`SeekBar`](../framework/android/widget/SeekBar.java)
- [`Space`](../framework/android/widget/Space.java)
- [`Spinner`](../framework/android/widget/Spinner.java)
- [`SpinnerAdapter`](../framework/android/widget/SpinnerAdapter.java)
- [`Switch`](../framework/android/widget/Switch.java)
- [`TableLayout`](../framework/android/widget/TableLayout.java)
- [`TableRow`](../framework/android/widget/TableRow.java)
- [`TextView`](../framework/android/widget/TextView.java)
- [`ThemedSpinnerAdapter`](../framework/android/widget/ThemedSpinnerAdapter.java)
- [`Toast`](../framework/android/widget/Toast.java)
- [`ToggleButton`](../framework/android/widget/ToggleButton.java)

## android.window

- [`BackEvent`](../framework/android/window/BackEvent.java)
- [`OnBackAnimationCallback`](../framework/android/window/OnBackAnimationCallback.java)
- [`OnBackInvokedCallback`](../framework/android/window/OnBackInvokedCallback.java)
- [`OnBackInvokedDispatcher`](../framework/android/window/OnBackInvokedDispatcher.java)

## org.isakai.bridge

- [`IsakaiAudio`](../framework/org/isakai/bridge/IsakaiAudio.java)
- [`IsakaiDesktop`](../framework/org/isakai/bridge/IsakaiDesktop.java)
- [`IsakaiLocation`](../framework/org/isakai/bridge/IsakaiLocation.java)
- [`IsakaiMedia`](../framework/org/isakai/bridge/IsakaiMedia.java)
- [`IsakaiSensor`](../framework/org/isakai/bridge/IsakaiSensor.java)
- [`IsakaiWindow`](../framework/org/isakai/bridge/IsakaiWindow.java)

## org.isakai.db

- [`IsakaiSQLite`](../framework/org/isakai/db/IsakaiSQLite.java)

## org.isakai.prefs

- [`IsakaiPrefsWork`](../framework/org/isakai/prefs/IsakaiPrefsWork.java)

## org.isakai.runtime

- [`Capabilities`](../framework/org/isakai/runtime/Capabilities.java)
- [`InterfaceDefaults`](../framework/org/isakai/runtime/InterfaceDefaults.java)
- [`IsakaiMain`](../framework/org/isakai/runtime/IsakaiMain.java)

## org.xmlpull.v1

- [`XmlPullParser`](../framework/org/xmlpull/v1/XmlPullParser.java)
- [`XmlPullParserException`](../framework/org/xmlpull/v1/XmlPullParserException.java)
- [`XmlSerializer`](../framework/org/xmlpull/v1/XmlSerializer.java)
