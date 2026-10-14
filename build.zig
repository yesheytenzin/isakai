const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const registry_mod = b.createModule(.{
        .root_source_file = b.path("src/registry.zig"),
        .target = target,
        .optimize = optimize,
    });
    const arsc_mod = b.createModule(.{
        .root_source_file = b.path("src/arsc.zig"),
        .target = target,
        .optimize = optimize,
    });
    const install_mod = b.createModule(.{
        .root_source_file = b.path("src/install.zig"),
        .target = target,
        .optimize = optimize,
    });
    install_mod.addImport("arsc", arsc_mod);
    install_mod.addImport("registry", registry_mod);
    const dex_mod = b.createModule(.{
        .root_source_file = b.path("src/dex.zig"),
        .target = target,
        .optimize = optimize,
    });
    const apksig_mod = b.createModule(.{
        .root_source_file = b.path("src/apksig.zig"),
        .target = target,
        .optimize = optimize,
    });
    install_mod.addImport("apksig", apksig_mod);
    const translate_mod = b.createModule(.{
        .root_source_file = b.path("src/translate.zig"),
        .target = target,
        .optimize = optimize,
    });
    translate_mod.addImport("dex", dex_mod);
    // Desktop-bridge endpoint definition shared by the launcher, the
    // privileged helper, the bridge daemon and the in-sandbox JNI client.
    const path_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/path.zig"),
        .target = target,
        .optimize = optimize,
    });
    const launch_mod = b.createModule(.{
        .root_source_file = b.path("src/launch.zig"),
        .target = target,
        .optimize = optimize,
    });
    launch_mod.addImport("dex", dex_mod);
    launch_mod.addImport("translate", translate_mod);
    launch_mod.addImport("registry", registry_mod);
    launch_mod.addImport("install", install_mod);
    launch_mod.addImport("bridge_path", path_mod);
    const helper_mod = b.createModule(.{
        .root_source_file = b.path("src/uid_helper.zig"),
        .target = target,
        .optimize = optimize,
    });
    const confine_mod = b.createModule(.{
        .root_source_file = b.path("src/confine.zig"),
        .target = target,
        .optimize = optimize,
    });
    helper_mod.addImport("confine", confine_mod);
    helper_mod.addImport("bridge_path", path_mod);
    const upgrade_mod = b.createModule(.{
        .root_source_file = b.path("src/upgrade.zig"),
        .target = target,
        .optimize = optimize,
    });
    upgrade_mod.addImport("registry", registry_mod);
    upgrade_mod.addImport("install", install_mod);
    upgrade_mod.addImport("apksig", apksig_mod);
    upgrade_mod.addImport("launch", launch_mod);
    helper_mod.addImport("registry", registry_mod);
    install_mod.addImport("uid_helper", helper_mod);
    const isakai_mod = b.createModule(.{
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
    });
    isakai_mod.addImport("upgrade", upgrade_mod);
    isakai_mod.addImport("registry", registry_mod);
    isakai_mod.addImport("install", install_mod);
    isakai_mod.addImport("launch", launch_mod);
    isakai_mod.addImport("uid_helper", helper_mod);
    const exe = b.addExecutable(.{
        .name = "isakai",
        .root_module = isakai_mod,
    });
    b.installArtifact(exe);
    const window_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/window.zig"),
        .target = target,
        .optimize = optimize,
    });
    const xkb_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/xkb.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    xkb_mod.linkSystemLibrary("xkbcommon", .{});
    window_mod.addImport("xkb", xkb_mod);
    const jni_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/jni.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    // Storage owns this module: the sqlite natives link into libisakai_jni.
    // Other agents MUST NOT add linkSystemLibrary/userland imports here.
    const sqlite_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/sqlite.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    sqlite_mod.linkSystemLibrary("sqlite3", .{});
    sqlite_mod.linkSystemLibrary("icui18n", .{ .use_pkg_config = .no });
    sqlite_mod.linkSystemLibrary("icuuc", .{ .use_pkg_config = .no });
    sqlite_mod.addCSourceFile(.{ .file = b.path("src/bridge/sqlite_collation.c"), .flags = &.{"-std=c11"} });
    const audio_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/audio.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    audio_mod.linkSystemLibrary("pulse-simple", .{});
    const dbus_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/dbus.zig"),
        .target = target,
        .optimize = optimize,
    });
    const desktop_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/desktop.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    const bridge_mod = b.createModule(.{
        .root_source_file = b.path("src/dbus_bridge.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    bridge_mod.addImport("dbus", dbus_mod);
    bridge_mod.addImport("bridge_path", path_mod);
    desktop_mod.addImport("bridge_path", path_mod);
    jni_mod.addImport("window", window_mod);
    jni_mod.addImport("sqlite", sqlite_mod);
    jni_mod.addImport("audio", audio_mod);
    jni_mod.addImport("desktop", desktop_mod);
    const bridge_exe = b.addExecutable(.{
        .name = "isakai-dbus-bridge",
        .root_module = bridge_mod,
    });
    b.installArtifact(bridge_exe);
    const jni_lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "isakai_jni",
        .root_module = jni_mod,
    });
    const jni_install = b.addInstallFile(jni_lib.getEmittedBin(), "framework/lib/libisakai_jni.so");
    b.getInstallStep().dependOn(&jni_install.step);
    // M6a bionic shim: LD_PRELOADed into the JVM so NDK-built app .so
    // files resolve bionic-only symbols (__android_log_*, property_*,
    // android_dlopen_ext) against this library. See src/native/bionic_shim.zig.
    const shim_mod = b.createModule(.{
        .root_source_file = b.path("src/native/bionic_shim.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    const shim_lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "isakai_bionic_shim",
        .root_module = shim_mod,
    });
    const shim_install = b.addInstallFile(shim_lib.getEmittedBin(), "framework/lib/libisakai_bionic_shim.so");
    b.getInstallStep().dependOn(&shim_install.step);
    // Framework: compile android.* + IsakaiMain with the host javac.
    const fw = b.addSystemCommand(&.{
        "javac", "--release", "17", "-d",
    });
    const fw_out = fw.addOutputDirectoryArg("framework");
    fw.addFileArg(b.path("framework/android/accessibilityservice/AccessibilityServiceInfo.java"));
    fw.addFileArg(b.path("framework/android/accounts/Account.java"));
    fw.addFileArg(b.path("framework/android/accounts/AccountManager.java"));
    fw.addFileArg(b.path("framework/android/animation/Animator.java"));
    fw.addFileArg(b.path("framework/android/animation/AnimatorListenerAdapter.java"));
    fw.addFileArg(b.path("framework/android/animation/AnimatorSet.java"));
    fw.addFileArg(b.path("framework/android/animation/ArgbEvaluator.java"));
    fw.addFileArg(b.path("framework/android/animation/FloatEvaluator.java"));
    fw.addFileArg(b.path("framework/android/animation/Keyframe.java"));
    fw.addFileArg(b.path("framework/android/animation/LayoutTransition.java"));
    fw.addFileArg(b.path("framework/android/animation/ObjectAnimator.java"));
    fw.addFileArg(b.path("framework/android/animation/PropertyValuesHolder.java"));
    fw.addFileArg(b.path("framework/android/animation/StateListAnimator.java"));
    fw.addFileArg(b.path("framework/android/animation/TimeInterpolator.java"));
    fw.addFileArg(b.path("framework/android/animation/TypeEvaluator.java"));
    fw.addFileArg(b.path("framework/android/animation/ValueAnimator.java"));
    fw.addFileArg(b.path("framework/android/app/ActionBar.java"));
    fw.addFileArg(b.path("framework/android/app/Activity.java"));
    fw.addFileArg(b.path("framework/android/app/ActivityManager.java"));
    fw.addFileArg(b.path("framework/android/app/AlarmManager.java"));
    fw.addFileArg(b.path("framework/android/app/AppComponentFactory.java"));
    fw.addFileArg(b.path("framework/android/app/AppOpsManager.java"));
    fw.addFileArg(b.path("framework/android/app/Application.java"));
    fw.addFileArg(b.path("framework/android/app/Dialog.java"));
    fw.addFileArg(b.path("framework/android/app/DownloadManager.java"));
    fw.addFileArg(b.path("framework/android/app/Fragment.java"));
    fw.addFileArg(b.path("framework/android/app/FragmentManager.java"));
    fw.addFileArg(b.path("framework/android/app/FragmentTransaction.java"));
    fw.addFileArg(b.path("framework/android/app/HeadlessWindow.java"));
    fw.addFileArg(b.path("framework/android/app/KeyguardManager.java"));
    fw.addFileArg(b.path("framework/android/app/LocaleManager.java"));
    fw.addFileArg(b.path("framework/android/app/Notification.java"));
    fw.addFileArg(b.path("framework/android/app/NotificationChannel.java"));
    fw.addFileArg(b.path("framework/android/app/NotificationManager.java"));
    fw.addFileArg(b.path("framework/android/app/PendingIntent.java"));
    fw.addFileArg(b.path("framework/android/app/SearchManager.java"));
    fw.addFileArg(b.path("framework/android/app/Service.java"));
    fw.addFileArg(b.path("framework/android/app/SharedElementCallback.java"));
    fw.addFileArg(b.path("framework/android/app/UiModeManager.java"));
    fw.addFileArg(b.path("framework/android/app/WallpaperManager.java"));
    fw.addFileArg(b.path("framework/android/app/admin/DevicePolicyManager.java"));
    fw.addFileArg(b.path("framework/android/app/job/JobScheduler.java"));
    fw.addFileArg(b.path("framework/android/app/role/RoleManager.java"));
    fw.addFileArg(b.path("framework/android/app/usage/UsageStatsManager.java"));
    fw.addFileArg(b.path("framework/android/appwidget/AppWidgetManager.java"));
    fw.addFileArg(b.path("framework/android/bluetooth/BluetoothManager.java"));
    fw.addFileArg(b.path("framework/android/content/ActivityNotFoundException.java"));
    fw.addFileArg(b.path("framework/android/content/BroadcastReceiver.java"));
    fw.addFileArg(b.path("framework/android/content/ClipData.java"));
    fw.addFileArg(b.path("framework/android/content/ClipDescription.java"));
    fw.addFileArg(b.path("framework/android/content/ClipboardManager.java"));
    fw.addFileArg(b.path("framework/android/content/ComponentCallbacks.java"));
    fw.addFileArg(b.path("framework/android/content/ComponentCallbacks2.java"));
    fw.addFileArg(b.path("framework/android/content/ComponentName.java"));
    fw.addFileArg(b.path("framework/android/content/ContentProvider.java"));
    fw.addFileArg(b.path("framework/android/content/ContentProviderClient.java"));
    fw.addFileArg(b.path("framework/android/content/ContentProviderOperation.java"));
    fw.addFileArg(b.path("framework/android/content/ContentProviderResult.java"));
    fw.addFileArg(b.path("framework/android/content/ContentResolver.java"));
    fw.addFileArg(b.path("framework/android/content/ContentValues.java"));
    fw.addFileArg(b.path("framework/android/content/Context.java"));
    fw.addFileArg(b.path("framework/android/content/ContextWrapper.java"));
    fw.addFileArg(b.path("framework/android/content/DialogInterface.java"));
    fw.addFileArg(b.path("framework/android/content/Intent.java"));
    fw.addFileArg(b.path("framework/android/content/IntentFilter.java"));
    fw.addFileArg(b.path("framework/android/content/IntentSender.java"));
    fw.addFileArg(b.path("framework/android/content/IsakaiPackageManager.java"));
    fw.addFileArg(b.path("framework/android/content/OperationApplicationException.java"));
    fw.addFileArg(b.path("framework/android/content/RestrictionsManager.java"));
    fw.addFileArg(b.path("framework/android/content/ServiceConnection.java"));
    fw.addFileArg(b.path("framework/android/content/SharedPreferences.java"));
    fw.addFileArg(b.path("framework/android/content/SharedPreferencesImpl.java"));
    fw.addFileArg(b.path("framework/android/content/UriMatcher.java"));
    fw.addFileArg(b.path("framework/android/content/UriPermission.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ActivityInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ApplicationInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ComponentInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/LauncherApps.java"));
    fw.addFileArg(b.path("framework/android/content/pm/PackageInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/PackageItemInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/PackageManager.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ProviderInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ResolveInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ServiceInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ShortcutInfo.java"));
    fw.addFileArg(b.path("framework/android/content/pm/ShortcutManager.java"));
    fw.addFileArg(b.path("framework/android/content/pm/Signature.java"));
    fw.addFileArg(b.path("framework/android/content/res/AssetFileDescriptor.java"));
    fw.addFileArg(b.path("framework/android/content/res/AssetManager.java"));
    fw.addFileArg(b.path("framework/android/content/res/ColorStateList.java"));
    fw.addFileArg(b.path("framework/android/content/res/Configuration.java"));
    fw.addFileArg(b.path("framework/android/content/res/Resources.java"));
    fw.addFileArg(b.path("framework/android/content/res/SystemColors.java"));
    fw.addFileArg(b.path("framework/android/content/res/TypedArray.java"));
    fw.addFileArg(b.path("framework/android/content/res/XmlResourceParser.java"));
    fw.addFileArg(b.path("framework/android/database/ContentObserver.java"));
    fw.addFileArg(b.path("framework/android/database/Cursor.java"));
    fw.addFileArg(b.path("framework/android/database/CursorIndexOutOfBoundsException.java"));
    fw.addFileArg(b.path("framework/android/database/DataSetObservable.java"));
    fw.addFileArg(b.path("framework/android/database/DataSetObserver.java"));
    fw.addFileArg(b.path("framework/android/database/DatabaseErrorHandler.java"));
    fw.addFileArg(b.path("framework/android/database/DatabaseUtils.java"));
    fw.addFileArg(b.path("framework/android/database/MatrixCursor.java"));
    fw.addFileArg(b.path("framework/android/database/MergeCursor.java"));
    fw.addFileArg(b.path("framework/android/database/Observable.java"));
    fw.addFileArg(b.path("framework/android/database/SQLException.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteClosable.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteConstraintException.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteCursor.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteCursorDriver.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteDatabase.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteDatabaseLockedException.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteException.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteOpenHelper.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteProgram.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteQuery.java"));
    fw.addFileArg(b.path("framework/android/database/sqlite/SQLiteStatement.java"));
    fw.addFileArg(b.path("framework/android/graphics/Bitmap.java"));
    fw.addFileArg(b.path("framework/android/graphics/BitmapFactory.java"));
    fw.addFileArg(b.path("framework/android/graphics/BitmapFont.java"));
    fw.addFileArg(b.path("framework/android/graphics/BitmapShader.java"));
    fw.addFileArg(b.path("framework/android/graphics/BlendMode.java"));
    fw.addFileArg(b.path("framework/android/graphics/BlendModeColorFilter.java"));
    fw.addFileArg(b.path("framework/android/graphics/Canvas.java"));
    fw.addFileArg(b.path("framework/android/graphics/Color.java"));
    fw.addFileArg(b.path("framework/android/graphics/ColorFilter.java"));
    fw.addFileArg(b.path("framework/android/graphics/ColorSpace.java"));
    fw.addFileArg(b.path("framework/android/graphics/ComposeShader.java"));
    fw.addFileArg(b.path("framework/android/graphics/HostText.java"));
    fw.addFileArg(b.path("framework/android/graphics/ImageDecoder.java"));
    fw.addFileArg(b.path("framework/android/graphics/Insets.java"));
    fw.addFileArg(b.path("framework/android/graphics/LinearGradient.java"));
    fw.addFileArg(b.path("framework/android/graphics/Matrix.java"));
    fw.addFileArg(b.path("framework/android/graphics/Outline.java"));
    fw.addFileArg(b.path("framework/android/graphics/Paint.java"));
    fw.addFileArg(b.path("framework/android/graphics/Path.java"));
    fw.addFileArg(b.path("framework/android/graphics/PathMeasure.java"));
    fw.addFileArg(b.path("framework/android/graphics/Picture.java"));
    fw.addFileArg(b.path("framework/android/graphics/PixelFormat.java"));
    fw.addFileArg(b.path("framework/android/graphics/Point.java"));
    fw.addFileArg(b.path("framework/android/graphics/PointF.java"));
    fw.addFileArg(b.path("framework/android/graphics/PorterDuff.java"));
    fw.addFileArg(b.path("framework/android/graphics/PorterDuffColorFilter.java"));
    fw.addFileArg(b.path("framework/android/graphics/PorterDuffXfermode.java"));
    fw.addFileArg(b.path("framework/android/graphics/RadialGradient.java"));
    fw.addFileArg(b.path("framework/android/graphics/RecordingCanvas.java"));
    fw.addFileArg(b.path("framework/android/graphics/Rect.java"));
    fw.addFileArg(b.path("framework/android/graphics/RectF.java"));
    fw.addFileArg(b.path("framework/android/graphics/Region.java"));
    fw.addFileArg(b.path("framework/android/graphics/RenderEffect.java"));
    fw.addFileArg(b.path("framework/android/graphics/RenderNode.java"));
    fw.addFileArg(b.path("framework/android/graphics/Shader.java"));
    fw.addFileArg(b.path("framework/android/graphics/SweepGradient.java"));
    fw.addFileArg(b.path("framework/android/graphics/Typeface.java"));
    fw.addFileArg(b.path("framework/android/graphics/Xfermode.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/Animatable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/Animatable2.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/AnimatedImageDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/AnimatedStateListDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/AnimatedVectorDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/AnimationDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/BitmapDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/ClipDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/ColorDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/ColorStateListDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/Drawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/DrawableContainer.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/GradientDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/Icon.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/InsetDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/LayerDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/RippleDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/ShapeDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/StateListDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/TransitionDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/VectorDrawable.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/VectorPathParser.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/shapes/OvalShape.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/shapes/RectShape.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/shapes/RoundRectShape.java"));
    fw.addFileArg(b.path("framework/android/graphics/drawable/shapes/Shape.java"));
    fw.addFileArg(b.path("framework/android/graphics/fonts/Font.java"));
    fw.addFileArg(b.path("framework/android/graphics/fonts/FontFamily.java"));
    fw.addFileArg(b.path("framework/android/graphics/fonts/FontStyle.java"));
    fw.addFileArg(b.path("framework/android/graphics/text/LineBreakConfig.java"));
    fw.addFileArg(b.path("framework/android/hardware/ConsumerIrManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/Sensor.java"));
    fw.addFileArg(b.path("framework/android/hardware/SensorEvent.java"));
    fw.addFileArg(b.path("framework/android/hardware/SensorEventListener.java"));
    fw.addFileArg(b.path("framework/android/hardware/SensorManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/biometrics/BiometricManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/biometrics/BiometricPrompt.java"));
    fw.addFileArg(b.path("framework/android/hardware/camera2/CameraManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/display/DisplayManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/fingerprint/FingerprintManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/input/InputManager.java"));
    fw.addFileArg(b.path("framework/android/hardware/usb/UsbDevice.java"));
    fw.addFileArg(b.path("framework/android/hardware/usb/UsbEndpoint.java"));
    fw.addFileArg(b.path("framework/android/hardware/usb/UsbInterface.java"));
    fw.addFileArg(b.path("framework/android/hardware/usb/UsbManager.java"));
    fw.addFileArg(b.path("framework/android/icu/util/ULocale.java"));
    fw.addFileArg(b.path("framework/android/location/Criteria.java"));
    fw.addFileArg(b.path("framework/android/location/Location.java"));
    fw.addFileArg(b.path("framework/android/location/LocationListener.java"));
    fw.addFileArg(b.path("framework/android/location/LocationManager.java"));
    fw.addFileArg(b.path("framework/android/media/AudioManager.java"));
    fw.addFileArg(b.path("framework/android/media/MediaDataSource.java"));
    fw.addFileArg(b.path("framework/android/media/MediaExtractor.java"));
    fw.addFileArg(b.path("framework/android/media/MediaFormat.java"));
    fw.addFileArg(b.path("framework/android/media/MediaMetadataRetriever.java"));
    fw.addFileArg(b.path("framework/android/media/MediaPlayer.java"));
    fw.addFileArg(b.path("framework/android/media/MediaRouter.java"));
    fw.addFileArg(b.path("framework/android/media/MediaScannerConnection.java"));
    fw.addFileArg(b.path("framework/android/media/SoundPool.java"));
    fw.addFileArg(b.path("framework/android/media/projection/MediaProjectionManager.java"));
    fw.addFileArg(b.path("framework/android/media/session/MediaSessionManager.java"));
    fw.addFileArg(b.path("framework/android/media/tv/TvInputManager.java"));
    fw.addFileArg(b.path("framework/android/net/ConnectivityManager.java"));
    fw.addFileArg(b.path("framework/android/net/Network.java"));
    fw.addFileArg(b.path("framework/android/net/Uri.java"));
    fw.addFileArg(b.path("framework/android/net/nsd/NsdManager.java"));
    fw.addFileArg(b.path("framework/android/net/wifi/WifiManager.java"));
    fw.addFileArg(b.path("framework/android/net/wifi/p2p/WifiP2pManager.java"));
    fw.addFileArg(b.path("framework/android/nfc/NfcManager.java"));
    fw.addFileArg(b.path("framework/android/os/BaseBundle.java"));
    fw.addFileArg(b.path("framework/android/os/BatteryManager.java"));
    fw.addFileArg(b.path("framework/android/os/Binder.java"));
    fw.addFileArg(b.path("framework/android/os/Build.java"));
    fw.addFileArg(b.path("framework/android/os/Bundle.java"));
    fw.addFileArg(b.path("framework/android/os/CancellationSignal.java"));
    fw.addFileArg(b.path("framework/android/os/DropBoxManager.java"));
    fw.addFileArg(b.path("framework/android/os/Environment.java"));
    fw.addFileArg(b.path("framework/android/os/Handler.java"));
    fw.addFileArg(b.path("framework/android/os/IBinder.java"));
    fw.addFileArg(b.path("framework/android/os/LocaleList.java"));
    fw.addFileArg(b.path("framework/android/os/Looper.java"));
    fw.addFileArg(b.path("framework/android/os/Message.java"));
    fw.addFileArg(b.path("framework/android/os/OperationCanceledException.java"));
    fw.addFileArg(b.path("framework/android/os/Parcel.java"));
    fw.addFileArg(b.path("framework/android/os/ParcelFileDescriptor.java"));
    fw.addFileArg(b.path("framework/android/os/Parcelable.java"));
    fw.addFileArg(b.path("framework/android/os/PowerManager.java"));
    fw.addFileArg(b.path("framework/android/os/RemoteException.java"));
    fw.addFileArg(b.path("framework/android/os/ResultReceiver.java"));
    fw.addFileArg(b.path("framework/android/os/StrictMode.java"));
    fw.addFileArg(b.path("framework/android/os/SystemClock.java"));
    fw.addFileArg(b.path("framework/android/os/Trace.java"));
    fw.addFileArg(b.path("framework/android/os/UserManager.java"));
    fw.addFileArg(b.path("framework/android/os/Vibrator.java"));
    fw.addFileArg(b.path("framework/android/os/ext/SdkExtensions.java"));
    fw.addFileArg(b.path("framework/android/os/storage/StorageManager.java"));
    fw.addFileArg(b.path("framework/android/print/PrintManager.java"));
    fw.addFileArg(b.path("framework/android/provider/Settings.java"));
    fw.addFileArg(b.path("framework/android/security/identity/IdentityCredential.java"));
    fw.addFileArg(b.path("framework/android/security/identity/PresentationSession.java"));
    fw.addFileArg(b.path("framework/android/security/keystore/KeyGenParameterSpec.java"));
    fw.addFileArg(b.path("framework/android/service/quicksettings/TileService.java"));
    fw.addFileArg(b.path("framework/android/telecom/TelecomManager.java"));
    fw.addFileArg(b.path("framework/android/telephony/SubscriptionManager.java"));
    fw.addFileArg(b.path("framework/android/telephony/TelephonyManager.java"));
    fw.addFileArg(b.path("framework/android/text/Annotation.java"));
    fw.addFileArg(b.path("framework/android/text/BoringLayout.java"));
    fw.addFileArg(b.path("framework/android/text/Editable.java"));
    fw.addFileArg(b.path("framework/android/text/GetChars.java"));
    fw.addFileArg(b.path("framework/android/text/Html.java"));
    fw.addFileArg(b.path("framework/android/text/InputFilter.java"));
    fw.addFileArg(b.path("framework/android/text/Layout.java"));
    fw.addFileArg(b.path("framework/android/text/PrecomputedText.java"));
    fw.addFileArg(b.path("framework/android/text/Selection.java"));
    fw.addFileArg(b.path("framework/android/text/SpanWatcher.java"));
    fw.addFileArg(b.path("framework/android/text/Spannable.java"));
    fw.addFileArg(b.path("framework/android/text/SpannableString.java"));
    fw.addFileArg(b.path("framework/android/text/SpannableStringBuilder.java"));
    fw.addFileArg(b.path("framework/android/text/Spanned.java"));
    fw.addFileArg(b.path("framework/android/text/StaticLayout.java"));
    fw.addFileArg(b.path("framework/android/text/TextDirectionHeuristic.java"));
    fw.addFileArg(b.path("framework/android/text/TextDirectionHeuristics.java"));
    fw.addFileArg(b.path("framework/android/text/TextPaint.java"));
    fw.addFileArg(b.path("framework/android/text/TextUtils.java"));
    fw.addFileArg(b.path("framework/android/text/TextWatcher.java"));
    fw.addFileArg(b.path("framework/android/text/format/DateUtils.java"));
    fw.addFileArg(b.path("framework/android/text/method/ArrowKeyMovementMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/BaseMovementMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/KeyListener.java"));
    fw.addFileArg(b.path("framework/android/text/method/LinkMovementMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/MetaKeyKeyListener.java"));
    fw.addFileArg(b.path("framework/android/text/method/MovementMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/NumberKeyListener.java"));
    fw.addFileArg(b.path("framework/android/text/method/PasswordTransformationMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/ScrollingMovementMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/SingleLineTransformationMethod.java"));
    fw.addFileArg(b.path("framework/android/text/method/TransformationMethod.java"));
    fw.addFileArg(b.path("framework/android/text/style/AbsoluteSizeSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/BackgroundColorSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/CharacterStyle.java"));
    fw.addFileArg(b.path("framework/android/text/style/ClickableSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/ForegroundColorSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/ImageSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/LeadingMarginSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/LocaleSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/MetricAffectingSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/RelativeSizeSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/ReplacementSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/ScaleXSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/StrikethroughSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/StyleSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/TextAppearanceSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/TtsSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/URLSpan.java"));
    fw.addFileArg(b.path("framework/android/text/style/UnderlineSpan.java"));
    fw.addFileArg(b.path("framework/android/transition/Transition.java"));
    fw.addFileArg(b.path("framework/android/transition/TransitionSet.java"));
    fw.addFileArg(b.path("framework/android/util/AndroidException.java"));
    fw.addFileArg(b.path("framework/android/util/AndroidRuntimeException.java"));
    fw.addFileArg(b.path("framework/android/util/AttributeSet.java"));
    fw.addFileArg(b.path("framework/android/util/DisplayMetrics.java"));
    fw.addFileArg(b.path("framework/android/util/LayoutDirection.java"));
    fw.addFileArg(b.path("framework/android/util/Log.java"));
    fw.addFileArg(b.path("framework/android/util/LongSparseArray.java"));
    fw.addFileArg(b.path("framework/android/util/Pair.java"));
    fw.addFileArg(b.path("framework/android/util/Property.java"));
    fw.addFileArg(b.path("framework/android/util/Size.java"));
    fw.addFileArg(b.path("framework/android/util/SizeF.java"));
    fw.addFileArg(b.path("framework/android/util/SparseArray.java"));
    fw.addFileArg(b.path("framework/android/util/SparseBooleanArray.java"));
    fw.addFileArg(b.path("framework/android/util/SparseIntArray.java"));
    fw.addFileArg(b.path("framework/android/util/SparseLongArray.java"));
    fw.addFileArg(b.path("framework/android/util/TypedValue.java"));
    fw.addFileArg(b.path("framework/android/util/Xml.java"));
    fw.addFileArg(b.path("framework/android/view/AbsSavedState.java"));
    fw.addFileArg(b.path("framework/android/view/ActionMode.java"));
    fw.addFileArg(b.path("framework/android/view/ActionProvider.java"));
    fw.addFileArg(b.path("framework/android/view/Choreographer.java"));
    fw.addFileArg(b.path("framework/android/view/CollapsibleActionView.java"));
    fw.addFileArg(b.path("framework/android/view/ContentInfo.java"));
    fw.addFileArg(b.path("framework/android/view/ContextMenu.java"));
    fw.addFileArg(b.path("framework/android/view/ContextThemeWrapper.java"));
    fw.addFileArg(b.path("framework/android/view/Display.java"));
    fw.addFileArg(b.path("framework/android/view/DisplayCutout.java"));
    fw.addFileArg(b.path("framework/android/view/DisplayListCanvas.java"));
    fw.addFileArg(b.path("framework/android/view/DragEvent.java"));
    fw.addFileArg(b.path("framework/android/view/FocusFinder.java"));
    fw.addFileArg(b.path("framework/android/view/GestureDetector.java"));
    fw.addFileArg(b.path("framework/android/view/Gravity.java"));
    fw.addFileArg(b.path("framework/android/view/InflateException.java"));
    fw.addFileArg(b.path("framework/android/view/InputDevice.java"));
    fw.addFileArg(b.path("framework/android/view/InputEvent.java"));
    fw.addFileArg(b.path("framework/android/view/KeyCharacterMap.java"));
    fw.addFileArg(b.path("framework/android/view/KeyEvent.java"));
    fw.addFileArg(b.path("framework/android/view/KeyboardShortcutGroup.java"));
    fw.addFileArg(b.path("framework/android/view/LayoutInflater.java"));
    fw.addFileArg(b.path("framework/android/view/MeasureSpec.java"));
    fw.addFileArg(b.path("framework/android/view/Menu.java"));
    fw.addFileArg(b.path("framework/android/view/MenuInflater.java"));
    fw.addFileArg(b.path("framework/android/view/MenuItem.java"));
    fw.addFileArg(b.path("framework/android/view/MotionEvent.java"));
    fw.addFileArg(b.path("framework/android/view/OnReceiveContentListener.java"));
    fw.addFileArg(b.path("framework/android/view/PointerIcon.java"));
    fw.addFileArg(b.path("framework/android/view/RoundedCorner.java"));
    fw.addFileArg(b.path("framework/android/view/ScaleGestureDetector.java"));
    fw.addFileArg(b.path("framework/android/view/SearchEvent.java"));
    fw.addFileArg(b.path("framework/android/view/SubMenu.java"));
    fw.addFileArg(b.path("framework/android/view/VelocityTracker.java"));
    fw.addFileArg(b.path("framework/android/view/View.java"));
    fw.addFileArg(b.path("framework/android/view/ViewConfiguration.java"));
    fw.addFileArg(b.path("framework/android/view/ViewGroup.java"));
    fw.addFileArg(b.path("framework/android/view/ViewGroupOverlay.java"));
    fw.addFileArg(b.path("framework/android/view/ViewManager.java"));
    fw.addFileArg(b.path("framework/android/view/ViewOutlineProvider.java"));
    fw.addFileArg(b.path("framework/android/view/ViewParent.java"));
    fw.addFileArg(b.path("framework/android/view/ViewPropertyAnimator.java"));
    fw.addFileArg(b.path("framework/android/view/ViewStructure.java"));
    fw.addFileArg(b.path("framework/android/view/ViewStub.java"));
    fw.addFileArg(b.path("framework/android/view/ViewTreeObserver.java"));
    fw.addFileArg(b.path("framework/android/view/Window.java"));
    fw.addFileArg(b.path("framework/android/view/WindowInsets.java"));
    fw.addFileArg(b.path("framework/android/view/WindowInsetsAnimation.java"));
    fw.addFileArg(b.path("framework/android/view/WindowInsetsController.java"));
    fw.addFileArg(b.path("framework/android/view/WindowManager.java"));
    fw.addFileArg(b.path("framework/android/view/accessibility/AccessibilityEvent.java"));
    fw.addFileArg(b.path("framework/android/view/accessibility/AccessibilityManager.java"));
    fw.addFileArg(b.path("framework/android/view/accessibility/AccessibilityNodeInfo.java"));
    fw.addFileArg(b.path("framework/android/view/accessibility/AccessibilityNodeProvider.java"));
    fw.addFileArg(b.path("framework/android/view/accessibility/AccessibilityRecord.java"));
    fw.addFileArg(b.path("framework/android/view/accessibility/CaptioningManager.java"));
    fw.addFileArg(b.path("framework/android/view/animation/AccelerateDecelerateInterpolator.java"));
    fw.addFileArg(b.path("framework/android/view/animation/AccelerateInterpolator.java"));
    fw.addFileArg(b.path("framework/android/view/animation/Animation.java"));
    fw.addFileArg(b.path("framework/android/view/animation/AnimationSet.java"));
    fw.addFileArg(b.path("framework/android/view/animation/AnimationUtils.java"));
    fw.addFileArg(b.path("framework/android/view/animation/DecelerateInterpolator.java"));
    fw.addFileArg(b.path("framework/android/view/animation/Interpolator.java"));
    fw.addFileArg(b.path("framework/android/view/animation/LinearInterpolator.java"));
    fw.addFileArg(b.path("framework/android/view/animation/PathInterpolator.java"));
    fw.addFileArg(b.path("framework/android/view/animation/Transformation.java"));
    fw.addFileArg(b.path("framework/android/view/autofill/AutofillId.java"));
    fw.addFileArg(b.path("framework/android/view/autofill/AutofillManager.java"));
    fw.addFileArg(b.path("framework/android/view/autofill/AutofillValue.java"));
    fw.addFileArg(b.path("framework/android/view/contentcapture/ContentCaptureSession.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/BaseInputConnection.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/CursorAnchorInfo.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/EditorBoundsInfo.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/EditorInfo.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/ExtractedText.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/InputConnection.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/InputConnectionWrapper.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/InputContentInfo.java"));
    fw.addFileArg(b.path("framework/android/view/inputmethod/InputMethodManager.java"));
    fw.addFileArg(b.path("framework/android/view/textclassifier/TextClassificationManager.java"));
    fw.addFileArg(b.path("framework/android/view/textclassifier/TextClassifier.java"));
    fw.addFileArg(b.path("framework/android/view/textservice/TextServicesManager.java"));
    fw.addFileArg(b.path("framework/android/view/translation/ViewTranslationCallback.java"));
    fw.addFileArg(b.path("framework/android/view/translation/ViewTranslationRequest.java"));
    fw.addFileArg(b.path("framework/android/view/translation/ViewTranslationResponse.java"));
    fw.addFileArg(b.path("framework/android/webkit/CookieManager.java"));
    fw.addFileArg(b.path("framework/android/webkit/JavascriptInterface.java"));
    fw.addFileArg(b.path("framework/android/webkit/ValueCallback.java"));
    fw.addFileArg(b.path("framework/android/webkit/WebResourceError.java"));
    fw.addFileArg(b.path("framework/android/webkit/WebResourceRequest.java"));
    fw.addFileArg(b.path("framework/android/webkit/WebResourceResponse.java"));
    fw.addFileArg(b.path("framework/android/webkit/WebSettings.java"));
    fw.addFileArg(b.path("framework/android/webkit/WebView.java"));
    fw.addFileArg(b.path("framework/android/webkit/WebViewClient.java"));
    fw.addFileArg(b.path("framework/android/widget/AbsListView.java"));
    fw.addFileArg(b.path("framework/android/widget/AbsSeekBar.java"));
    fw.addFileArg(b.path("framework/android/widget/Adapter.java"));
    fw.addFileArg(b.path("framework/android/widget/AdapterView.java"));
    fw.addFileArg(b.path("framework/android/widget/ArrayAdapter.java"));
    fw.addFileArg(b.path("framework/android/widget/AutoCompleteTextView.java"));
    fw.addFileArg(b.path("framework/android/widget/BaseAdapter.java"));
    fw.addFileArg(b.path("framework/android/widget/Button.java"));
    fw.addFileArg(b.path("framework/android/widget/CheckBox.java"));
    fw.addFileArg(b.path("framework/android/widget/Checkable.java"));
    fw.addFileArg(b.path("framework/android/widget/CheckedTextView.java"));
    fw.addFileArg(b.path("framework/android/widget/Chronometer.java"));
    fw.addFileArg(b.path("framework/android/widget/CompoundButton.java"));
    fw.addFileArg(b.path("framework/android/widget/DateTimeView.java"));
    fw.addFileArg(b.path("framework/android/widget/EdgeEffect.java"));
    fw.addFileArg(b.path("framework/android/widget/EditText.java"));
    fw.addFileArg(b.path("framework/android/widget/Filter.java"));
    fw.addFileArg(b.path("framework/android/widget/Filterable.java"));
    fw.addFileArg(b.path("framework/android/widget/FrameLayout.java"));
    fw.addFileArg(b.path("framework/android/widget/GridView.java"));
    fw.addFileArg(b.path("framework/android/widget/HeaderViewListAdapter.java"));
    fw.addFileArg(b.path("framework/android/widget/HorizontalScrollView.java"));
    fw.addFileArg(b.path("framework/android/widget/ImageButton.java"));
    fw.addFileArg(b.path("framework/android/widget/ImageView.java"));
    fw.addFileArg(b.path("framework/android/widget/LinearLayout.java"));
    fw.addFileArg(b.path("framework/android/widget/ListAdapter.java"));
    fw.addFileArg(b.path("framework/android/widget/ListView.java"));
    fw.addFileArg(b.path("framework/android/widget/Magnifier.java"));
    fw.addFileArg(b.path("framework/android/widget/MultiAutoCompleteTextView.java"));
    fw.addFileArg(b.path("framework/android/widget/OverScroller.java"));
    fw.addFileArg(b.path("framework/android/widget/PopupWindow.java"));
    fw.addFileArg(b.path("framework/android/widget/ProgressBar.java"));
    fw.addFileArg(b.path("framework/android/widget/RadioButton.java"));
    fw.addFileArg(b.path("framework/android/widget/RadioGroup.java"));
    fw.addFileArg(b.path("framework/android/widget/RatingBar.java"));
    fw.addFileArg(b.path("framework/android/widget/RelativeLayout.java"));
    fw.addFileArg(b.path("framework/android/widget/RemoteViews.java"));
    fw.addFileArg(b.path("framework/android/widget/ScrollView.java"));
    fw.addFileArg(b.path("framework/android/widget/Scroller.java"));
    fw.addFileArg(b.path("framework/android/widget/SeekBar.java"));
    fw.addFileArg(b.path("framework/android/widget/Space.java"));
    fw.addFileArg(b.path("framework/android/widget/Spinner.java"));
    fw.addFileArg(b.path("framework/android/widget/SpinnerAdapter.java"));
    fw.addFileArg(b.path("framework/android/widget/Switch.java"));
    fw.addFileArg(b.path("framework/android/widget/TableLayout.java"));
    fw.addFileArg(b.path("framework/android/widget/TableRow.java"));
    fw.addFileArg(b.path("framework/android/widget/TextView.java"));
    fw.addFileArg(b.path("framework/android/widget/ThemedSpinnerAdapter.java"));
    fw.addFileArg(b.path("framework/android/widget/Toast.java"));
    fw.addFileArg(b.path("framework/android/widget/ToggleButton.java"));
    fw.addFileArg(b.path("framework/android/window/BackEvent.java"));
    fw.addFileArg(b.path("framework/android/window/OnBackAnimationCallback.java"));
    fw.addFileArg(b.path("framework/android/window/OnBackInvokedCallback.java"));
    fw.addFileArg(b.path("framework/android/window/OnBackInvokedDispatcher.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiAudio.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiDesktop.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiLocation.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiMedia.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiSensor.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiWindow.java"));
    fw.addFileArg(b.path("framework/org/isakai/db/IsakaiSQLite.java"));
    fw.addFileArg(b.path("framework/org/isakai/prefs/IsakaiPrefsWork.java"));
    fw.addFileArg(b.path("framework/org/isakai/runtime/Capabilities.java"));
    fw.addFileArg(b.path("framework/org/isakai/runtime/InterfaceDefaults.java"));
    fw.addFileArg(b.path("framework/org/isakai/runtime/IsakaiMain.java"));
    fw.addFileArg(b.path("framework/org/xmlpull/v1/XmlPullParser.java"));
    fw.addFileArg(b.path("framework/org/xmlpull/v1/XmlPullParserException.java"));
    fw.addFileArg(b.path("framework/org/xmlpull/v1/XmlSerializer.java"));

    const fw_install = b.addInstallDirectory(.{
        .source_dir = fw_out,
        .install_dir = .prefix,
        .install_subdir = "framework",
    });
    b.getInstallStep().dependOn(&fw_install.step);
    // Polkit policy: staged for packagers to copy into
    // share/polkit-1/actions/. Installed under the prefix so `zig build`
    // stays relocatable; the distro install must place the .policy next to
    // the other actions with exec.path=/usr/bin/isakai-*-uid (see the policy).
    // M6f: the policy now pins per-operation wrappers (isakai-allocate-uid
    // etc.); they are staged alongside for `make install` to place in bin/.
    const policy_install = b.addInstallFile(b.path("polkit/id.isakai.policy"), "share/polkit-1/actions/id.isakai.policy");
    b.getInstallStep().dependOn(&policy_install.step);
    const wrapper_names = [_][]const u8{ "isakai-allocate-uid", "isakai-remove-uid", "isakai-run-app" };
    for (wrapper_names) |wrapper| {
        const p = std.fmt.allocPrint(b.allocator, "polkit/bin/{s}", .{wrapper}) catch @panic("OOM");
        const wrapper_install = b.addInstallFile(b.path(p), b.fmt("bin/{s}", .{wrapper}));
        b.getInstallStep().dependOn(&wrapper_install.step);
    }
    const fixture_mod = b.createModule(.{
        .root_source_file = b.path("tools/fixture-apk.zig"),
        .target = target,
        .optimize = optimize,
    });
    const fixture_exe = b.addExecutable(.{
        .name = "fixture-apk",
        .root_module = fixture_mod,
    });
    b.installArtifact(fixture_exe);

    const probe_mod = b.createModule(.{
        .root_source_file = b.path("tools/inspect-test.zig"),
        .target = target,
        .optimize = optimize,
    });
    probe_mod.addImport("install", install_mod);
    const probe_exe = b.addExecutable(.{ .name = "inspect-test", .root_module = probe_mod });
    b.installArtifact(probe_exe);
    const tprobe_mod = b.createModule(.{
        .root_source_file = b.path("tools/translate-test.zig"),
        .target = target,
        .optimize = optimize,
    });
    tprobe_mod.addImport("dex", dex_mod);
    tprobe_mod.addImport("translate", translate_mod);
    const tprobe_exe = b.addExecutable(.{ .name = "translate-test", .root_module = tprobe_mod });
    b.installArtifact(tprobe_exe);
    const fprobe_mod = b.createModule(.{
        .root_source_file = b.path("tools/findA.zig"),
        .target = target,
        .optimize = optimize,
    });
    fprobe_mod.addImport("dex", dex_mod);
    const fprobe_exe = b.addExecutable(.{ .name = "findA", .root_module = fprobe_mod });
    b.installArtifact(fprobe_exe);
    const mprobe_mod = b.createModule(.{
        .root_source_file = b.path("tools/findM.zig"),
        .target = target,
        .optimize = optimize,
    });
    mprobe_mod.addImport("dex", dex_mod);
    const mprobe_exe = b.addExecutable(.{ .name = "findM", .root_module = mprobe_mod });
    b.installArtifact(mprobe_exe);
    const vprobe_mod = b.createModule(.{
        .root_source_file = b.path("tools/verify-test.zig"),
        .target = target,
        .optimize = optimize,
    });
    vprobe_mod.addImport("apksig", apksig_mod);
    const vprobe_exe = b.addExecutable(.{ .name = "verify-test", .root_module = vprobe_mod });
    b.installArtifact(vprobe_exe);
    const runtime_probe_mod = b.createModule(.{
        .root_source_file = b.path("tools/runtime-probe.zig"),
        .target = target,
        .optimize = optimize,
    });
    runtime_probe_mod.addImport("uid_helper", helper_mod);
    const runtime_probe = b.addExecutable(.{ .name = "runtime-probe", .root_module = runtime_probe_mod });
    b.installArtifact(runtime_probe);
    // `zig build run` (no args pass-through on 0.17; use zig-out/bin/isakai).
    const run_step = b.step("run", "Run the isakai CLI");
    const run_cmd = b.addRunArtifact(exe);
    run_step.dependOn(&run_cmd.step);

    // `zig build test` (all unit tests; xkb runs on its hermetic fake
    // backend; jni needs a live JVM so it stays out of this loop).
    const java_tests = b.addSystemCommand(&.{ "python3", "tools/test-framework.py" });
    const java_test_step = b.step("test-java", "Run framework behavioral tests");
    java_test_step.dependOn(&java_tests.step);
    const inventory = b.addSystemCommand(&.{ "python3", "tools/framework-inventory.py", "--check" });
    const inventory_step = b.step("check-inventory", "Check deterministic source and API inventories");
    inventory_step.dependOn(&inventory.step);
    const test_step = b.step("test", "Run unit tests");
    for ([_]*std.Build.Module{ isakai_mod, registry_mod, arsc_mod, install_mod, dex_mod, translate_mod, launch_mod, apksig_mod, helper_mod, upgrade_mod, confine_mod, window_mod, xkb_mod, audio_mod, dbus_mod, desktop_mod, bridge_mod, shim_mod }) |mod| {
        const unit_tests = b.addTest(.{ .root_module = mod });
        const run_tests = b.addRunArtifact(unit_tests);
        test_step.dependOn(&run_tests.step);
    }
}
