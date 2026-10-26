// SPDX-License-Identifier: GPL-3.0-or-later

//! Builds a minimal fixture APK without the Android SDK: a ZIP with a binary
//! AndroidManifest.xml, a tiny classes.dex, and nothing else.
//!
//! Usage: fixture-apk <output.apk> [package] [versionCode]
//! Default package: org.isakai.fixture.

const std = @import("std");

const default_package = "org.isakai.fixture";

fn writeU16(w: *std.Io.Writer, v: u16) !void {
    var b: [2]u8 = undefined;
    std.mem.writeInt(u16, &b, v, .little);
    try w.writeAll(&b);
}

fn writeU32(w: *std.Io.Writer, v: u32) !void {
    var b: [4]u8 = undefined;
    std.mem.writeInt(u32, &b, v, .little);
    try w.writeAll(&b);
}

/// Encode one string-pool entry (UTF-8, dual length prefix).
fn writePoolString(w: *std.Io.Writer, s: []const u8) !void {
    // UTF-16 length then UTF-8 length; ASCII so both equal s.len.
    const len: u8 = @intCast(s.len);
    if (s.len < 128) {
        try w.writeAll(&[_]u8{ len, len });
    } else {
        try w.writeAll(&[_]u8{ 0x80 | @as(u8, @intCast(s.len >> 8)), @as(u8, @intCast(s.len & 0xff)) });
        try w.writeAll(&[_]u8{ 0x80 | @as(u8, @intCast(s.len >> 8)), @as(u8, @intCast(s.len & 0xff)) });
    }
    try w.writeAll(s);
    try w.writeAll(&[_]u8{0});
}

/// Build a binary AndroidManifest.xml for package/app/activity.
fn buildManifest(gpa: std.mem.Allocator, package: []const u8, version_code: u32) ![]u8 {
    var out: std.ArrayListUnmanaged(u8) = .empty;
    errdefer out.deinit(gpa);
    // Assemble in memory via a fixed writer over a large buffer first.
    var mem: [65536]u8 = undefined;
    var fw = std.Io.Writer.fixed(&mem);
    const w = &fw;

    // String pool contents (indices matter below).
    const strings = [_][]const u8{
        "manifest", // 0
        "package", // 1
        package, // 2
        "uses-sdk", // 3
        "http://schemas.android.com/apk/res/android", // 4
        "minSdkVersion", // 5
        "targetSdkVersion", // 6
        "application", // 7
        "name", // 8
        "org.isakai.fixture.FixtureApp", // 9 (placeholder, replaced below)
        "activity", // 10
        "org.isakai.fixture.FixtureActivity", // 11
        "intent-filter", // 12
        "action", // 13
        "android.intent.action.MAIN", // 14
        "category", // 15
        "android.intent.category.LAUNCHER", // 16
        "uses-permission", // 17
        "android.permission.INTERNET", // 18
        "versionCode", // 19
        "versionName", // 20
        "1.0", // 21
    };
    _ = strings;

    // We build the pool dynamically so package substitution is exact.
    var pool_strs: std.ArrayListUnmanaged([]const u8) = .empty;
    defer pool_strs.deinit(gpa);
    try pool_strs.appendSlice(gpa, &.{
        "manifest",
        "package",
        package,
        "uses-sdk",
        "http://schemas.android.com/apk/res/android",
        "minSdkVersion",
        "targetSdkVersion",
        "application",
        "name",
        "org.isakai.fixture.FixtureApp",
        "activity",
        "org.isakai.fixture.FixtureActivity",
        "intent-filter",
        "action",
        "android.intent.action.MAIN",
        "category",
        "android.intent.category.LAUNCHER",
        "uses-permission",
        "android.permission.INTERNET",
        "versionCode",
        "versionName",
        "1.0",
        "label",
    });
    const S = struct {
        manifest: u32 = 0,
        package_attr: u32 = 1,
        package_val: u32 = 2,
        uses_sdk: u32 = 3,
        android_ns: u32 = 4,
        min_sdk: u32 = 5,
        target_sdk: u32 = 6,
        application: u32 = 7,
        name_attr: u32 = 8,
        app_class: u32 = 9,
        activity: u32 = 10,
        activity_class: u32 = 11,
        intent_filter: u32 = 12,
        action: u32 = 13,
        main_action: u32 = 14,
        category: u32 = 15,
        launcher_cat: u32 = 16,
        uses_permission: u32 = 17,
        internet_perm: u32 = 18,
        version_code: u32 = 19,
        version_name: u32 = 20,
        version_name_val: u32 = 21,
        label_attr: u32 = 22,
    }{};

    // Chunk 0: XML header (type 3, header 8).
    try writeU16(w, 3);
    try writeU16(w, 8);
    const total_len_pos = fw.end;
    try writeU32(w, 0); // patched at the end

    // Chunk 1: string pool (type 1).
    try writeU16(w, 1);
    try writeU16(w, 28);
    const pool_len_pos = fw.end;
    try writeU32(w, 0); // patched
    try writeU32(w, @intCast(pool_strs.items.len)); // string count
    try writeU32(w, 0); // style count
    try writeU32(w, 0x100); // flags: UTF-8
    const pool_start_pos = fw.end;
    try writeU32(w, 0); // strings start (patched)
    try writeU32(w, 0); // styles start
    // Offsets.
    var offsets: std.ArrayListUnmanaged(u32) = .empty;
    defer offsets.deinit(gpa);
    var str_data: std.ArrayListUnmanaged(u8) = .empty;
    defer str_data.deinit(gpa);
    for (pool_strs.items) |s| {
        try offsets.append(gpa, @intCast(str_data.items.len));
        // encode into str_data via a temp writer
        var tmp: [4096]u8 = undefined;
        var tw = std.Io.Writer.fixed(&tmp);
        try writePoolString(&tw, s);
        try str_data.appendSlice(gpa, tmp[0..tw.end]);
    }
    for (offsets.items) |o| try writeU32(w, o);
    // Pad offsets to 4 (already are). Record strings start.
    const strings_start: u32 = @intCast(8 + (fw.end - pool_len_pos - 4));
    try str_data.append(gpa, 0); // trailing pad safety
    while (str_data.items.len % 4 != 0) try str_data.append(gpa, 0);
    try w.writeAll(str_data.items);
    // Patch pool chunk length + strings start.
    const pool_end = fw.end;
    std.mem.writeInt(u32, mem[pool_len_pos..][0..4], @intCast(pool_end - (pool_len_pos - 4)), .little);
    std.mem.writeInt(u32, mem[pool_start_pos..][0..4], strings_start, .little);

    // Resource map (type 0x180, empty) — optional but harmless to include empty.
    try writeU16(w, 0x180);
    try writeU16(w, 8);
    try writeU32(w, 8);

    // Namespace start/end helper data.
    const Attr = struct { ns: u32, name: u32, raw: u32, typ: u8, val: u32 };
    const writeStart = struct {
        fn f(
            ww: *std.Io.Writer,
            line: u32,
            ns_idx: u32,
            name_idx: u32,
            attrs: []const Attr,
        ) !void {
            try writeU16(ww, 0x102);
            try writeU16(ww, 16);
            const attr_start: u16 = 36;
            const len: u32 = 36 + @as(u32, @intCast(attrs.len)) * 20;
            try writeU32(ww, len);
            try writeU32(ww, line);
            try writeU32(ww, 0xFFFFFFFF); // comment
            try writeU32(ww, ns_idx);
            try writeU32(ww, name_idx);
            try writeU16(ww, 20); // attributeStart (relative to 16)
            try writeU16(ww, 20); // attributeSize
            try writeU16(ww, @intCast(attrs.len));
            try writeU16(ww, 0); // idIndex
            try writeU16(ww, 0); // classIndex
            try writeU16(ww, 0); // styleIndex
            _ = attr_start;
            for (attrs) |a| {
                try writeU32(ww, a.ns);
                try writeU32(ww, a.name);
                try writeU32(ww, a.raw);
                // Res_value: size:u16=8, res0:u8=0, dataType:u8, data:u32.
                try writeU16(ww, 8);
                try ww.writeAll(&[_]u8{ 0, a.typ });
                try writeU32(ww, a.val);
            }
        }
    }.f;
    const writeEnd = struct {
        fn f(ww: *std.Io.Writer, line: u32, ns_idx: u32, name_idx: u32) !void {
            try writeU16(ww, 0x103);
            try writeU16(ww, 16);
            try writeU32(ww, 24);
            try writeU32(ww, line);
            try writeU32(ww, 0xFFFFFFFF);
            try writeU32(ww, ns_idx);
            try writeU32(ww, name_idx);
        }
    }.f;
    const NONE: u32 = 0xFFFFFFFF;

    // <manifest package=... versionCode=1 versionName="1.0">
    try writeStart(w, 1, NONE, S.manifest, &.{
        .{ .ns = NONE, .name = S.package_attr, .raw = S.package_val, .typ = 3, .val = S.package_val },
        .{ .ns = S.android_ns, .name = S.version_code, .raw = NONE, .typ = 0x10, .val = version_code },
        .{ .ns = S.android_ns, .name = S.version_name, .raw = S.version_name_val, .typ = 3, .val = S.version_name_val },
    });
    // <uses-sdk minSdkVersion=23 targetSdkVersion=33>
    try writeStart(w, 2, NONE, S.uses_sdk, &.{
        .{ .ns = S.android_ns, .name = S.min_sdk, .raw = NONE, .typ = 0x10, .val = 23 },
        .{ .ns = S.android_ns, .name = S.target_sdk, .raw = NONE, .typ = 0x10, .val = 33 },
    });
    try writeEnd(w, 2, NONE, S.uses_sdk);
    // <uses-permission android:name="android.permission.INTERNET"/>
    try writeStart(w, 3, NONE, S.uses_permission, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.internet_perm, .typ = 3, .val = S.internet_perm },
    });
    try writeEnd(w, 3, NONE, S.uses_permission);
    // <application android:name="org.isakai.fixture.FixtureApp" android:label="@string/app_name">
    // The label is a resource reference on purpose: it covers the
    // runtime label path (label id -> "Fixture" from the table).
    try writeStart(w, 4, NONE, S.application, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.app_class, .typ = 3, .val = S.app_class },
        .{ .ns = S.android_ns, .name = S.label_attr, .raw = NONE, .typ = 1, .val = 0x7f110000 },
    });
    // <activity android:name="org.isakai.fixture.FixtureActivity" android:label="@string/activity_label">
    try writeStart(w, 5, NONE, S.activity, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.activity_class, .typ = 3, .val = S.activity_class },
        .{ .ns = S.android_ns, .name = S.label_attr, .raw = NONE, .typ = 1, .val = 0x7f110001 },
    });
    // <intent-filter>
    try writeStart(w, 6, NONE, S.intent_filter, &.{});
    // <action android:name="android.intent.action.MAIN"/>
    try writeStart(w, 7, NONE, S.action, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.main_action, .typ = 3, .val = S.main_action },
    });
    try writeEnd(w, 7, NONE, S.action);
    // <category android:name="android.intent.category.LAUNCHER"/>
    try writeStart(w, 8, NONE, S.category, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.launcher_cat, .typ = 3, .val = S.launcher_cat },
    });
    try writeEnd(w, 8, NONE, S.category);
    try writeEnd(w, 6, NONE, S.intent_filter);
    try writeEnd(w, 5, NONE, S.activity);
    try writeEnd(w, 4, NONE, S.application);
    try writeEnd(w, 1, NONE, S.manifest);

    // Patch total length.
    std.mem.writeInt(u32, mem[total_len_pos..][0..4], @intCast(fw.end), .little);
    try out.appendSlice(gpa, mem[0..fw.end]);
    return out.toOwnedSlice(gpa);
}

/// Counter DEX: one class Lorg/isakai/fixture/Counter; with int count,
/// <init>(), onCreate()V (count=41), bump()I (count+1), get()I.
/// Hand-assembled: string/type/proto/field/method ids, one class def,
/// class data, four code items. Valid checksum + SHA-1.
fn buildDex(gpa: std.mem.Allocator) ![]u8 {
    var dex: std.ArrayListUnmanaged(u8) = .empty;
    errdefer dex.deinit(gpa);
    try dex.appendNTimes(gpa, 0, 0x70); // header placeholder

    const w32 = struct {
        fn f(d: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u32) !void {
            var b: [4]u8 = undefined;
            std.mem.writeInt(u32, &b, v, .little);
            try d.appendSlice(g, &b);
        }
    }.f;
    const w16 = struct {
        fn f(d: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u16) !void {
            var b: [2]u8 = undefined;
            std.mem.writeInt(u16, &b, v, .little);
            try d.appendSlice(g, &b);
        }
    }.f;
    const wStr = struct {
        fn f(d: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, s: []const u8) !void {
            // uleb128 utf16-len then bytes + NUL.
            var len: usize = s.len;
            while (true) {
                var b: u8 = @intCast(len & 0x7F);
                len >>= 7;
                if (len != 0) b |= 0x80;
                try d.append(g, b);
                if (len == 0) break;
            }
            try d.appendSlice(g, s);
            try d.append(g, 0);
        }
    }.f;

    // --- Strings ---
    const strings = [_][]const u8{
        "Lorg/isakai/fixture/Counter;", // 0
        "Ljava/lang/Object;", // 1
        "I", // 2
        "V", // 3
        "<init>", // 4
        "<init>V", // shorty (unused idx, keep simple) -- replaced below
        "onCreate", // 6
        "bump", // 7
        "get", // 8
        "count", // 9
        "V", // shorty dup ok
        "I", // shorty dup ok
    };
    _ = strings;
    // Canonical list (dedup by hand):
    const strs = [_][]const u8{
        "Lorg/isakai/fixture/Counter;", // 0
        "Ljava/lang/Object;", // 1
        "I", // 2
        "V", // 3
        "<init>", // 4
        "onCreate", // 5
        "bump", // 6
        "get", // 7
        "count", // 8
        "Lorg/isakai/fixture/FixtureApp;", // 9
        "Landroid/app/Application;", // 10
        "Ljava/lang/String;", // 11
        "readFile", // 12
        "writeFile", // 13
        "counter", // 14
        "41", // 15
        "Ljava/lang/Integer;", // 16
        "parseInt", // 17
        "valueOf", // 18
        "Ljava/lang/System;", // 19
        "out", // 20
        "Ljava/io/PrintStream;", // 21
        "println", // 22
        "Lorg/isakai/fixture/FixtureActivity;", // 23
        "Landroid/app/Activity;", // 24
        "isakai: fixture activity onCreate", // 25
        "isakai: fixture app onCreate", // 26
        "VI", // 27 shorty (I)V
        "LLL", // 28 shorty (LL)L
        "IL", // 29 shorty (L)I
        "LI", // 30 shorty (I)L
        "VLL", // 31 shorty (LL)V
        "VL", // 32 shorty (L)V
        "Hello Isakai", // 33
        "Landroid/widget/TextView;", // 34
        "setText", // 35
        "setContentView", // 36
        "Landroid/view/View;", // 37
        "Landroid/widget/EditText;", // 38
        "Landroid/widget/Button;", // 39
        "Landroid/view/ViewGroup;", // 40
        "Landroid/view/View$OnClickListener;", // 41
        "addView", // 42
        "setOnClickListener", // 43
        "requestFocus", // 44
        "onClick", // 45
        "layout", // 46
        "edit", // 47
        "Click me", // 48
        "Clicked!", // 49
        "isakai: button clicked", // 50
        "Z", // 51 shorty ()Z
        "VIIII", // 52 shorty (IIII)V
        "Landroid/media/SoundPool;", // 53
        "Ljava/util/concurrent/Future;", // 54
        "load", // 55
        "play", // 56
        "release", // 57
        "getProperty", // 58
        "concat", // 59
        "isakai.codeDir", // 60
        "/assets/tone.wav", // 61
        "LII", // 62 shorty (II)L (unused; kept for table stability)
        "LL", // 63 shorty (L)L (unused; kept for table stability)
        "L", // 64 shorty ()L (unused; kept for table stability)
        "VII", // 65 shorty (II)V
        "Landroid/content/Context;", // 66
        "getSharedPreferences", // 67
        "Landroid/content/SharedPreferences;", // 68
        "edit", // 69
        "Landroid/content/SharedPreferences$Editor;", // 70
        "putInt", // 71
        "commit", // 72
        "apply", // 73
        "getInt", // 74
        "openFileOutput", // 75
        "Ljava/io/FileOutputStream;", // 76
        "write", // 77
        "close", // 78
        "Ljava/io/OutputStream;", // 79
        "openFileInput", // 80
        "Ljava/io/FileInputStream;", // 81
        "Ljava/io/InputStream;", // 82
        "read", // 83
        "prefs_counter", // 84
        "Landroid/database/sqlite/SQLiteDatabase;", // 85
        "openOrCreateDatabase", // 86
        "execSQL", // 87
        "insert", // 88
        "Landroid/content/ContentValues;", // 89
        "put", // 90
        "query", // 91
        "Landroid/database/Cursor;", // 92
        "moveToFirst", // 93
        "getInt", // 94 dup name, new string idx
        "close", // 95 dup name, new string idx
        "value", // 96
        "fixture", // 97
        "t", // 98
        "v", // 99
        "LLL", // 100 shorty (LL)L dup, new idx
        "ILL", // 101 shorty (LL)I
        "VIL", // 102 shorty (IL)V
        "VILL", // 103 shorty (ILL)V
        "ILLLLLL", // 104 shorty (LLLLLL)I
        "Z", // 105 shorty ()Z dup, new idx
        "ZL", // 106 shorty (L)Z
        "IIL", // 107 shorty (IL)I
        "CREATE TABLE IF NOT EXISTS t (v INTEGER)", // 108
        "NULL", // 109 placeholder for null check below
        "1", // 110 insert value
        "INSERT INTO t (v) VALUES (1)", // 111
        "SELECT v FROM t", // 112
        "[Ljava/lang/String;", // 113 String[] descriptor
        "rawQuery", // 114
        "II", // 115 shorty (I)I
        "Ljava/io/File;", // 116
        "getDatabasePath", // 117
        "getPath", // 118
    };
    const string_ids_off: u32 = @intCast(dex.items.len);
    var str_offs: [strs.len]u32 = undefined;
    // Reserve id table.
    for (0..strs.len) |_| try w32(&dex, gpa, 0);
    // String data items.
    for (strs, 0..) |s, i| {
        str_offs[i] = @intCast(dex.items.len);
        try wStr(&dex, gpa, s);
    }
    for (str_offs, 0..) |o, i| {
        std.mem.writeInt(u32, dex.items[string_ids_off + i * 4 ..][0..4], o, .little);
    }

    // --- Types ---
    // 0 Counter, 1 Object, 2 I, 3 V, 4 FixtureApp, 5 Application, 6 String,
    // 7 Integer, 8 System, 9 PrintStream, 10 FixtureActivity, 11 Activity,
    // 12 TextView, 13 View, 14 EditText, 15 Button, 16 ViewGroup,
    // 17 OnClickListener, 18 Z, 19 SoundPool, 20 Future,
    // 21 Context, 22 SharedPreferences, 23 Editor, 24 FileOutputStream,
    // 25 OutputStream, 26 FileInputStream, 27 InputStream, 28 SQLiteDatabase,
    // 29 ContentValues, 30 Cursor, 31 String[], 32 File
    const type_ids_off: u32 = @intCast(dex.items.len);
    const type_strs = [_]u32{ 0, 1, 2, 3, 9, 10, 11, 16, 19, 21, 23, 24, 34, 37, 38, 39, 40, 41, 51, 53, 54, 66, 68, 70, 76, 79, 81, 82, 85, 89, 92, 113, 116 };
    for (type_strs) |s| try w32(&dex, gpa, s);

    // --- Type lists for protos with params (built before protos) ---
    // list_I: [I]; list_LL: [String,String]; list_L: [String]; list_View: [View]
    const list_I_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 1);
    try w16(&dex, gpa, 2);
    while (dex.items.len % 4 != 0) try dex.append(gpa, 0);
    const list_LL_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 2);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 6);
    const list_L_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 1);
    try w16(&dex, gpa, 6);
    while (dex.items.len % 4 != 0) try dex.append(gpa, 0);
    const list_View_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 1);
    try w16(&dex, gpa, 13);
    while (dex.items.len % 4 != 0) try dex.append(gpa, 0);
    // list_OnClick: [OnClickListener(17)]; list_IIII: [I x4]
    const list_OnClick_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 1);
    try w16(&dex, gpa, 17);
    while (dex.items.len % 4 != 0) try dex.append(gpa, 0);
    const list_IIII_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 4);
    try w16(&dex, gpa, 2);
    try w16(&dex, gpa, 2);
    try w16(&dex, gpa, 2);
    try w16(&dex, gpa, 2);
    const list_II_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 2);
    try w16(&dex, gpa, 2);
    // Storage lists: [String,int](LI); [String x7 with int x3] for query.
    const list_LI_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 2);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 2);
    const list_Q_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 7);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 2);
    try w16(&dex, gpa, 2);
    try w16(&dex, gpa, 2);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 6);
    while (dex.items.len % 4 != 0) try dex.append(gpa, 0);
    // list_LArr: [String, String[]] for rawQuery.
    const list_LArr_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 2);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 31);
    // list_LObj: [String, Object] for openOrCreateDatabase.
    const list_LObj_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 2);
    try w16(&dex, gpa, 6);
    try w16(&dex, gpa, 1);

    // 0 ()V, 1 ()I, 2 (I)V, 3 (LL)L, 4 (L)I, 5 (I)L, 6 (LL)V, 7 (L)V,
    // 8 (View)V, 9 (String)V, 10 (OnClickListener)V, 11 ()Z, 12 (IIII)V
    const proto_ids_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 27);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_I_off);
    try w32(&dex, gpa, 28);
    try w32(&dex, gpa, 6);
    try w32(&dex, gpa, list_LL_off);
    try w32(&dex, gpa, 29);
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 30);
    try w32(&dex, gpa, 6);
    try w32(&dex, gpa, list_I_off);
    try w32(&dex, gpa, 31); // shorty VLL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_LL_off);
    try w32(&dex, gpa, 32); // shorty VL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 32); // shorty VL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_View_off);
    try w32(&dex, gpa, 32); // shorty VL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 32); // shorty VL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_OnClick_off);
    try w32(&dex, gpa, 51); // shorty Z
    try w32(&dex, gpa, 18);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 52); // shorty VIIII
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_IIII_off);
    try w32(&dex, gpa, 62); // shorty LII
    try w32(&dex, gpa, 6);
    try w32(&dex, gpa, list_II_off);
    try w32(&dex, gpa, 63); // shorty LL
    try w32(&dex, gpa, 6);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 64); // shorty L
    try w32(&dex, gpa, 6);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 65); // shorty VII
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_II_off);
    // Storage protos (17..31):
    // 17 (LI)L getSharedPreferences; 18 ()L SharedPrefs.edit;
    // 19 (LI)L Editor.putInt; 20 ()Z Editor.commit; 21 ()V Editor.apply;
    // 22 (LI)I SharedPrefs.getInt; 23 (LI)L Context.openFileOutput;
    // 24 (L)V OutputStream.write; 25 ()V Closeable.close;
    // 26 (L)L Context.openFileInput; 27 ()I InputStream.read;
    // 28 (LL)L SQLiteDatabase.openOrCreateDatabase(static);
    // 29 (L)V SQLiteDatabase.execSQL; 30 (LL)V ContentValues.put;
    // 31 (LLLLLLL)L SQLiteDatabase.query
    try w32(&dex, gpa, 100); // shorty LLL
    try w32(&dex, gpa, 22);
    try w32(&dex, gpa, list_LI_off);
    try w32(&dex, gpa, 64); // shorty L
    try w32(&dex, gpa, 23); // return Editor (edit()L)
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 100); // shorty LLL
    try w32(&dex, gpa, 23);
    try w32(&dex, gpa, list_LI_off);
    try w32(&dex, gpa, 105); // shorty Z
    try w32(&dex, gpa, 18); // return Z (commit()Z pushes boolean)
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 101); // shorty ILL
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, list_LI_off);
    try w32(&dex, gpa, 100); // shorty LLL
    try w32(&dex, gpa, 24);
    try w32(&dex, gpa, list_LI_off);
    try w32(&dex, gpa, 102); // shorty VIL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 32); // shorty VL
    try w32(&dex, gpa, 27);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 100); // shorty LLL
    try w32(&dex, gpa, 28);
    try w32(&dex, gpa, list_LL_off);
    try w32(&dex, gpa, 32); // shorty VL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_L_off);
    try w32(&dex, gpa, 103); // shorty VILL
    try w32(&dex, gpa, 3);
    try w32(&dex, gpa, list_LL_off);
    try w32(&dex, gpa, 104); // shorty ILLLLLL
    try w32(&dex, gpa, 30);
    try w32(&dex, gpa, list_Q_off);
    // 32 rawQuery(String,String[]) -> Cursor: shorty LLL (str 100),
    // return Cursor (type 30), params [String, String[]].
    try w32(&dex, gpa, 100); // shorty LLL
    try w32(&dex, gpa, 30);
    try w32(&dex, gpa, list_LArr_off);
    // 33 (I)I: shorty II (str 115), return I (type 2), params [I].
    try w32(&dex, gpa, 115); // shorty II
    try w32(&dex, gpa, 2);
    try w32(&dex, gpa, list_I_off);
    // 34 (L)L FileInputStream: shorty LL (str 63), return FileInputStream (26).
    try w32(&dex, gpa, 63); // shorty LL
    try w32(&dex, gpa, 26);
    try w32(&dex, gpa, list_L_off);
    // 35 (LObject)L: shorty LLL (str 100), return SQLiteDatabase (28),
    // params [String, Object] for openOrCreateDatabase factory arg.
    try w32(&dex, gpa, 100); // shorty LLL
    try w32(&dex, gpa, 28);
    try w32(&dex, gpa, list_LObj_off);
    // 36 (L)L File: shorty LL (str 63), return File (type 32).
    try w32(&dex, gpa, 63); // shorty LL
    try w32(&dex, gpa, 32);
    try w32(&dex, gpa, list_L_off);
    // --- Fields ---
    // 0: Counter.count:I; 1: System.out:PrintStream;
    // 2: FixtureActivity.edit:EditText
    const field_ids_off: u32 = @intCast(dex.items.len);
    try w16(&dex, gpa, 0);
    try w16(&dex, gpa, 2);
    try w32(&dex, gpa, 8);
    try w16(&dex, gpa, 8);
    try w16(&dex, gpa, 9);
    try w32(&dex, gpa, 20);
    try w16(&dex, gpa, 10);
    try w16(&dex, gpa, 14);
    try w32(&dex, gpa, 47);

    // --- Methods ---
    // 0 Object.<init>()V, 1 Counter.<init>()V, 2 Counter.onCreate()V,
    // 3 Counter.bump()I, 4 Counter.get()I,
    // 5 Application.<init>()V, 6 FixtureApp.<init>()V, 7 FixtureApp.onCreate()V,
    // 8 Application.readFile(LL)L, 9 Application.writeFile(LL)V,
    // 10 Integer.parseInt(L)I, 11 Integer.valueOf(I)L, 12 PrintStream.println(I)V,
    // 13 PrintStream.println(L)V, 14 Activity.<init>()V, 15 FixtureActivity.<init>()V,
    // 16 FixtureActivity.onCreate()V, 17 Activity.onCreate()V,
    // 18 TextView.<init>()V, 19 TextView.setText(L)V, 20 Activity.setContentView(View)V,
    // 21 ViewGroup.<init>()V, 22 EditText.<init>()V, 23 Button.<init>()V,
    // 24 ViewGroup.addView(View)V, 25 View.setOnClickListener(OnClick)V,
    // 26 View.requestFocus()Z, 27 View.layout(IIII)V, 28 FixtureActivity.onClick(View)V
    const method_ids_off: u32 = @intCast(dex.items.len);
    const methods = [_]struct { c: u16, p: u16, n: u32 }{
        .{ .c = 1, .p = 0, .n = 4 },
        .{ .c = 0, .p = 0, .n = 4 },
        .{ .c = 0, .p = 0, .n = 5 },
        .{ .c = 0, .p = 1, .n = 6 },
        .{ .c = 0, .p = 1, .n = 7 },
        .{ .c = 5, .p = 0, .n = 4 },
        .{ .c = 4, .p = 0, .n = 4 },
        .{ .c = 4, .p = 0, .n = 5 },
        .{ .c = 5, .p = 3, .n = 12 },
        .{ .c = 5, .p = 6, .n = 13 }, // writeFile(LL)V
        .{ .c = 7, .p = 4, .n = 17 },
        .{ .c = 6, .p = 5, .n = 18 },
        .{ .c = 9, .p = 2, .n = 22 },
        .{ .c = 9, .p = 7, .n = 22 }, // println(L)V
        .{ .c = 11, .p = 0, .n = 4 },
        .{ .c = 10, .p = 0, .n = 4 },
        .{ .c = 10, .p = 0, .n = 5 },
        .{ .c = 11, .p = 0, .n = 5 },
        .{ .c = 12, .p = 0, .n = 4 },
        .{ .c = 12, .p = 9, .n = 35 },
        .{ .c = 11, .p = 8, .n = 36 },
        .{ .c = 16, .p = 0, .n = 4 },
        .{ .c = 14, .p = 0, .n = 4 },
        .{ .c = 15, .p = 0, .n = 4 },
        .{ .c = 16, .p = 8, .n = 42 },
        .{ .c = 13, .p = 10, .n = 43 },
        .{ .c = 13, .p = 11, .n = 44 },
        .{ .c = 13, .p = 12, .n = 46 },
        .{ .c = 10, .p = 8, .n = 45 },
        .{ .c = 19, .p = 0, .n = 4 }, // 29 SoundPool.<init>()V
        .{ .c = 19, .p = 4, .n = 55 }, // 30 SoundPool.load(L)I
        .{ .c = 19, .p = 16, .n = 56 }, // 31 SoundPool.play(II)V
        .{ .c = 19, .p = 0, .n = 57 }, // 32 SoundPool.release()V
        .{ .c = 8, .p = 14, .n = 58 }, // 33 System.getProperty(L)L
        .{ .c = 6, .p = 14, .n = 59 }, // 34 String.concat(L)L
        .{ .c = 20, .p = 15, .n = 7 }, // 35 Future.get()L
        // Storage methods 36..57:
        // 36 Context.getSharedPreferences(LI)L (c=21 virtual on Context)
        // 37 SharedPreferences.edit()L; 38 Editor.putInt(LI)L;
        // 39 Editor.commit()Z; 40 Editor.apply()V; 41 SharedPrefs.getInt(LI)I;
        // 42 Context.openFileOutput(LI)L; 43 OutputStream.write(I)V
        // (FileOutputStream inherits); 44 OutputStream.close()V;
        // 45 Context.openFileInput(L)L; 46 InputStream.read()I;
        // 47 InputStream.close()V; 48 SQLiteDatabase.openOrCreateDatabase(LL)L static;
        // 49 SQLiteDatabase.execSQL(L)V; 50 ContentValues.<init>()V;
        // 51 ContentValues.put(LL)V; 52 SQLiteDatabase.insert(LLL)J NYI->see below;
        // 53 SQLiteDatabase.query(LLLLLLL)L; 54 Cursor.moveToFirst()Z;
        // 55 Cursor.getInt(I)I; 56 Cursor.close()V; 57 ContentValues.put(IL)V(int)
        .{ .c = 21, .p = 17, .n = 67 }, // 36
        .{ .c = 22, .p = 18, .n = 69 }, // 37 edit
        .{ .c = 23, .p = 19, .n = 71 }, // 38 putInt
        .{ .c = 23, .p = 20, .n = 72 }, // 39 commit
        .{ .c = 23, .p = 21, .n = 73 }, // 40 apply
        .{ .c = 22, .p = 22, .n = 74 }, // 41 getInt
        .{ .c = 21, .p = 23, .n = 75 }, // 42 openFileOutput
        .{ .c = 25, .p = 2, .n = 77 }, // 43 write
        .{ .c = 25, .p = 0, .n = 78 }, // 44 close
        .{ .c = 21, .p = 34, .n = 80 }, // 45 openFileInput
        .{ .c = 27, .p = 1, .n = 83 }, // 46 read
        .{ .c = 27, .p = 0, .n = 78 }, // 47 close (dup name idx 78 ok? use 95)
        .{ .c = 28, .p = 35, .n = 86 }, // 48 openOrCreateDatabase static
        .{ .c = 28, .p = 7, .n = 87 }, // 49 execSQL
        .{ .c = 29, .p = 0, .n = 4 }, // 50 ContentValues.<init>
        .{ .c = 29, .p = 30, .n = 90 }, // 51 put(LL)V
        .{ .c = 28, .p = 32, .n = 114 }, // 52 rawQuery(String,String[])L
        .{ .c = 30, .p = 11, .n = 93 }, // 53 moveToFirst
        .{ .c = 30, .p = 33, .n = 94 }, // 54 Cursor.getInt(I)I
        .{ .c = 30, .p = 0, .n = 95 }, // 55 Cursor.close()V
        .{ .c = 28, .p = 0, .n = 95 }, // 56 SQLiteDatabase.close()V
        .{ .c = 21, .p = 36, .n = 117 }, // 57 Context.getDatabasePath(L)L
        .{ .c = 32, .p = 15, .n = 118 }, // 58 File.getPath()L
    };
    for (methods) |m| {
        try w16(&dex, gpa, m.c);
        try w16(&dex, gpa, m.p);
        try w32(&dex, gpa, m.n);
    }

    // --- Interfaces list for FixtureActivity: [OnClickListener(17)] ---
    const ifaces_off: u32 = @intCast(dex.items.len);
    try w32(&dex, gpa, 1);
    try w16(&dex, gpa, 17);
    while (dex.items.len % 4 != 0) try dex.append(gpa, 0);

    // --- Class defs: Counter(0), FixtureApp(4), FixtureActivity(10) ---
    const class_defs_off: u32 = @intCast(dex.items.len);
    const cd_pos: [3]usize = .{ dex.items.len + 24, dex.items.len + 32 + 24, dex.items.len + 64 + 24 };
    // Counter : Object
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0x1);
    try w32(&dex, gpa, 1);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0xFFFFFFFF);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0);
    // FixtureApp : Application
    try w32(&dex, gpa, 4);
    try w32(&dex, gpa, 0x1);
    try w32(&dex, gpa, 5);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0xFFFFFFFF);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0);
    // FixtureActivity : Activity, implements OnClickListener
    try w32(&dex, gpa, 10);
    try w32(&dex, gpa, 0x1);
    try w32(&dex, gpa, 11);
    try w32(&dex, gpa, ifaces_off);
    try w32(&dex, gpa, 0xFFFFFFFF);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0);
    try w32(&dex, gpa, 0);

    // --- Code items first (into side buffers), so class data ulebs are exact ---
    const CodeSpec = struct { regs: u16, ins: u16, outs: u16, insns: []const u16 };
    const specs = [_]CodeSpec{
        // 0 Counter.<init>
        .{ .regs = 1, .ins = 1, .outs = 1, .insns = &[_]u16{ 0x1070, 0x0000, 0x0000, 0x000e } },
        // 1 Counter.onCreate
        .{ .regs = 2, .ins = 1, .outs = 0, .insns = &[_]u16{ 0x0013, 41, 0x1059, 0x0000, 0x000e } },
        // 2 Counter.bump
        .{ .regs = 3, .ins = 1, .outs = 0, .insns = &[_]u16{ 0x2052, 0x0000, 0x00d8, 0x0100, 0x2059, 0x0000, 0x2052, 0x0000, 0x000f } },
        // 3 Counter.get
        .{ .regs = 2, .ins = 1, .outs = 0, .insns = &[_]u16{ 0x1052, 0x0000, 0x000f } },
        // 4 FixtureApp.<init>: invoke-direct {v0}, m5
        .{ .regs = 1, .ins = 1, .outs = 1, .insns = &[_]u16{ 0x1070, 0x0005, 0x0000, 0x000e } },
        // 5 FixtureApp.onCreate: regs=7, this=v6.
        // Prefs counter: getSharedPreferences("prefs_counter",0)->edit/getInt+1/
        // putInt/commit+apply; prints count (1 then 2 across runs), then the
        // legacy file round-trip + SQLite create/insert/select/getInt/close.
        .{ .regs = 9, .ins = 1, .outs = 7, .insns = &[_]u16{
            // prefs(v0) = getSharedPreferences("prefs_counter", 0); this=v8.
            0x001a, 84, // const-string v0, "prefs_counter"
            0x0112, // const/4 v1, 0
            0x306e, 36, 0x0108, // invoke-virtual {v8,v0,v1}, m36
            0x000c, // move-result-object v0
            // count(v1) = prefs.getInt("counter", 41) + 1
            0x021a, 14, // const-string v2, "counter"
            0x0313, 42, // const/16 v3, 42 (defValue 42 -> prints 1..2? see below)
            0x3272, 41, 0x0320, // INVOKE-INTERFACE // invoke-virtual {v0,v2,v3}, m41 getInt
            0x010a, // move-result v1
            0x01d8, 0x0101, // add-int/lit8 v1, v1, 1
            // editor(v2) = prefs.edit()
            0x1072, 37, 0x0000, // INVOKE-INTERFACE // invoke-virtual {v0}, m37 edit
            0x020c, // move-result-object v2
            // editor.putInt("counter", count): regs {v2,v0?,v1}: key must be in a reg;
            // v0 holds prefs (needed later for apply). Copy editor v2->v4,
            // reload key into v0, keep count in v1: call {v4,v0,v1}.
            0x2407, // move-object v4, v2
            0x0507, // move-object v5, v0 (save prefs: v0 reused for key)
            0x001a, 14, // const-string v0, "counter"
            0x3172, 38, 0x0104, // INVOKE-INTERFACE // invoke-virtual {v4,v0,v1}, m38 putInt
            0x040c, // move-result-object v4 (editor, for commit chaining)
            // commit: editor.commit() -> ignore result in v1
            0x1072, 39, 0x0004, // INVOKE-INTERFACE // invoke-virtual {v4}, m39 commit
            0x010a, // move-result v1
            // println(count): reload count? v1 now holds commit result. Re-read:
            // count = prefs.getInt("counter", 42) -> v1
            0x5007, // move-object v0, v5 (restore prefs)
            0x021a, 14, // const-string v2, "counter"
            0x0313, 42, // const/16 v3, 42
            0x3272, 41, 0x0320, // INVOKE-INTERFACE // invoke-virtual {v0,v2,v3}, m41 getInt
            0x010a, // move-result v1
            0x0362, 1, // sget-object v3, System.out
            0x206e, 12, 0x0013, // invoke-virtual {v3,v1}, m12 println(I)
            // apply path: editor2 = prefs.edit(); putInt(count+1); apply()
            0x1072, 37, 0x0000, // INVOKE-INTERFACE // invoke-virtual {v0}, m37 edit
            0x020c, // move-result-object v2
            0x2407, // move-object v4, v2
            0x0507, // move-object v5, v0 (save prefs: v0 reused for key)
            0x001a, 14, // const-string v0, "counter"
            0x01d8, 0x0101, // add-int/lit8 v1, v1, 1 (count+1)
            0x3172, 38, 0x0104, // INVOKE-INTERFACE // invoke-virtual {v4,v0,v1}, m38 putInt
            0x040c, // move-result-object v4
            0x1072, 40, 0x0004, // INVOKE-INTERFACE // invoke-virtual {v4}, m40 apply
            // println(count) again after apply (memory-visible immediately)
            0x5007, // move-object v0, v5 (restore prefs)
            0x021a, 14, // const-string v2, "counter"
            0x0313, 42, // const/16 v3, 42
            0x3272, 41, 0x0320, // INVOKE-INTERFACE // invoke-virtual {v0,v2,v3}, m41 getInt
            0x010a, // move-result v1
            0x0362, 1, // sget-object v3, System.out
            0x206e, 12, 0x0013, // invoke-virtual {v3,v1}, m12 println(I)
            // activity msg
            0x041a, 26, // const-string v4, "isakai: fixture app onCreate"
            0x206e, 13, 0x0043, // invoke-virtual {v3,v4}, m13 println(L)
            // --- file round-trip: openFileOutput("t",0)->write byte->close ---
            0x001a, 98, // const-string v0, "t"
            0x0112, // const/4 v1, 0
            0x316e, 42, 0x0108, // invoke-virtual {v8,v0,v1}, m42 openFileOutput
            0x000c, // move-result-object v0 (FileOutputStream)
            0x0313, 0x2e, // const/16 v3, 46 ('.')
            0x216e, 43, 0x0030, // invoke-virtual {v0,v3}, m43 write(I)V
            0x106e, 44, 0x0000, // invoke-virtual {v0}, m44 close
            // --- file read back: openFileInput("t")->read()->println ---
            0x001a, 98, // const-string v0, "t"
            0x216e, 45, 0x0008, // invoke-virtual {v8,v0}, m45 openFileInput (argc2 C=8,D=0)
            0x000c, // move-result-object v0 (FileInputStream)
            0x106e, 46, 0x0000, // invoke-virtual {v0}, m46 read
            0x010a, // move-result v1
            0x106e, 47, 0x0000, // invoke-virtual {v0}, m47 InputStream.close
            0x0362, 1, // sget-object v3, System.out
            0x206e, 12, 0x0013, // invoke-virtual {v3,v1}, m12 println(I)
            // --- SQLite: path = getDatabasePath("fixture").getPath() ---
            0x001a, 97, // const-string v0, "fixture"
            0x216e, 57, 0x0008, // invoke-virtual {v8,v0}, m57 getDatabasePath
            0x000c, // move-result-object v0 (File)
            0x106e, 58, 0x0000, // invoke-virtual {v0}, m58 File.getPath
            0x000c, // move-result-object v0 (path String)
            // db(v5) = openOrCreateDatabase(path, "NULL"-as-factory)
            0x011a, 109, // const-string v1, "NULL"
            0x2171, 48, 0x0010, // invoke-static {v0,v1}, m48 openOrCreateDatabase
            0x050c, // move-result-object v5
            // db.execSQL(CREATE...)
            0x001a, 108, // const-string v0, create-sql
            0x256e, 49, 0x0005, // invoke-virtual {v5,v0}, m49 execSQL (argc2 C=5,D=0)
            // cv(v6) = new ContentValues()
            0x0622, 29, // new-instance v6, ContentValues (type 29)
            0x1070, 50, 0x0006, // invoke-direct {v6}, m50 <init>
            // cv.put("v", "1"): m51 is (LL)V -> regs {v6,v0,v1}
            0x001a, 99, // const-string v0, "v"
            0x011a, 110, // const-string v1, "1"
            0x326e, 51, 0x0106, // invoke-virtual {v6,v0,v1}, m51 (argc3 C=6,D=0,E=1)
            // db.insert via execSQL (skip long-return insert; use execSQL+query):
            // db.execSQL("INSERT INTO t (v) VALUES (1)")
            0x001a, 111, // const-string v0, insert-sql
            0x256e, 49, 0x0005, // invoke-virtual {v5,v0}, m49 execSQL
            // cur(v0) = db.rawQuery("SELECT v FROM t", new String[0])
            0x001a, 112, // const-string v0, select-sql
            0x0112, // const/4 v1, 0 (size 0)
            0x1123, 31, // new-array v1, v1, String[] (type 31)
            0x326e, 52, 0x0105, // invoke-virtual {v5,v0,v1}, m52 rawQuery
            0x000c, // move-result-object v0 (Cursor)
            0x1072, 53, 0x0000, // INVOKE-INTERFACE // invoke-virtual {v0}, m53 moveToFirst
            0x030a, // move-result v3 (discard boolean)
            0x0212, // const/4 v2, 0 (column index)
            0x2172, 54, 0x0020, // INVOKE-INTERFACE // invoke-virtual {v0,v2}, m54 getInt
            0x010a, // move-result v1
            0x0362, 1, // sget-object v3, System.out
            0x206e, 12, 0x0013, // invoke-virtual {v3,v1}, m12 println(I)
            0x1072, 55, 0x0000, // INVOKE-INTERFACE // invoke-virtual {v0}, m55 Cursor.close
            0x106e, 56, 0x0005, // invoke-virtual {v5}, m56 SQLiteDatabase.close
            0x000e, // return-void
        } },
        // 6 FixtureActivity.<init>: invoke-direct {v0}, m14
        .{ .regs = 1, .ins = 1, .outs = 1, .insns = &[_]u16{ 0x1070, 0x000e, 0x0000, 0x000e } },
        // 7 FixtureActivity.onCreate: regs=8, this=v7.
        // v0=root/path/pool, v1=edit/path-arg, v2=button/soundId, v3..v5 scratch.
        .{ .regs = 8, .ins = 1, .outs = 5, .insns = &[_]u16{
            0x106f, 17, 0x0007, // invoke-super {v7}, Activity.onCreate
            0x0362, 1, // sget-object v3, System.out
            0x001a, 25, // const-string v0, activity msg
            0x206e, 13, 0x0003, // invoke-virtual {v3,v0}, println(L)
            0x0022, 16, // new-instance v0, ViewGroup
            0x1070, 21, 0x0000, // invoke-direct {v0}, ViewGroup.<init>
            0x0122, 14, // new-instance v1, EditText
            0x1070, 22, 0x0001, // invoke-direct {v1}, EditText.<init>
            0x031a, 33, // const-string v3, "Hello Isakai"
            0x206e, 19, 0x0031, // invoke-virtual {v1,v3}, TextView.setText
            0x0313, 20, // const/16 v3, 20
            0x0413, 20, // const/16 v4, 20
            0x0513, 620, // const/16 v5, 620
            0x0613, 80, // const/16 v6, 80
            0x566e, 27, 0x5431, // invoke-virtual {v1,v3,v4,v5,v6}, View.layout
            0x715b, 2, // iput-object v1, v7, edit(f2)
            0x0222, 15, // new-instance v2, Button
            0x1070, 23, 0x0002, // invoke-direct {v2}, Button.<init>
            0x031a, 48, // const-string v3, "Click me"
            0x206e, 19, 0x0032, // invoke-virtual {v2,v3}, TextView.setText
            0x0313, 20, // const/16 v3, 20
            0x0413, 100, // const/16 v4, 100
            0x0513, 220, // const/16 v5, 220
            0x0613, 150, // const/16 v6, 150
            0x566e, 27, 0x5432, // invoke-virtual {v2,v3,v4,v5,v6}, View.layout
            0x206e, 24, 0x0010, // invoke-virtual {v0,v1}, ViewGroup.addView
            0x206e, 24, 0x0020, // invoke-virtual {v0,v2}, ViewGroup.addView
            0x206e, 25, 0x0072, // invoke-virtual {v2,v7}, View.setOnClickListener
            0x106e, 26, 0x0001, // invoke-virtual {v1}, View.requestFocus
            0x030a, // move-result v3
            0x206e, 20, 0x0007, // invoke-virtual {v7,v0}, Activity.setContentView
            0x001a, 60, // const-string v0, "isakai.codeDir"
            0x1071, 33, 0x0000, // invoke-static {v0}, System.getProperty
            0x000c, // move-result-object v0
            0x011a, 61, // const-string v1, "/assets/tone.wav"
            0x206e, 34, 0x0010, // invoke-virtual {v0,v1}, String.concat
            0x010c, // move-result-object v1
            0x0022, 19, // new-instance v0, SoundPool
            0x1070, 29, 0x0000, // invoke-direct {v0}, SoundPool.<init>
            0x206e, 30, 0x0010, // invoke-virtual {v0,v1}, SoundPool.load
            0x020a, // move-result v2
            0x0313, 0, // const/16 v3, 0 (loop once)
            0x306e, 31, 0x0320, // invoke-virtual {v0,v2,v3}, SoundPool.play (blocks)
            0x106e, 32, 0x0000, // invoke-virtual {v0}, SoundPool.release
            0x000e, // return-void
        } },
        // 8 FixtureActivity.onClick: regs=3, this=v2, arg v0=view.
        .{ .regs = 3, .ins = 2, .outs = 2, .insns = &[_]u16{
            0x1054, 2, // iget-object v0, v2, edit(f2)
            0x011a, 49, // const-string v1, "Clicked!"
            0x206e, 19, 0x0010, // invoke-virtual {v0,v1}, TextView.setText
            0x0062, 1, // sget-object v0, System.out
            0x011a, 50, // const-string v1, clicked msg
            0x206e, 13, 0x0010, // invoke-virtual {v0,v1}, println(L)
            0x000e, // return-void
        } },
    };
    var code_bufs: [9]std.ArrayListUnmanaged(u8) = .{ .empty, .empty, .empty, .empty, .empty, .empty, .empty, .empty, .empty };
    defer for (&code_bufs) |*b| b.deinit(gpa);
    for (specs, 0..) |s, i| {
        var b16: [2]u8 = undefined;
        var b32: [4]u8 = undefined;
        std.mem.writeInt(u16, &b16, s.regs, .little);
        try code_bufs[i].appendSlice(gpa, &b16);
        std.mem.writeInt(u16, &b16, s.ins, .little);
        try code_bufs[i].appendSlice(gpa, &b16);
        std.mem.writeInt(u16, &b16, s.outs, .little);
        try code_bufs[i].appendSlice(gpa, &b16);
        std.mem.writeInt(u16, &b16, 0, .little);
        try code_bufs[i].appendSlice(gpa, &b16);
        std.mem.writeInt(u32, &b32, 0, .little);
        try code_bufs[i].appendSlice(gpa, &b32);
        std.mem.writeInt(u32, &b32, @intCast(s.insns.len), .little);
        try code_bufs[i].appendSlice(gpa, &b32);
        for (s.insns) |u| {
            std.mem.writeInt(u16, &b16, u, .little);
            try code_bufs[i].appendSlice(gpa, &b16);
        }
    }
    // Class data lengths (all diffs/flags 1 byte, code ulebs 2 bytes):
    // Counter: 4 + 2 + 4*4 = 22; FixtureApp: 4 + 2*4 = 12;
    // FixtureActivity: 4 + (field 2) + (15) + (16,12) + (28) = 4+2+4+4+4 = 18.
    const cd_lens = [_]usize{ 22, 12, 18 };
    const cd_off = [_]u32{ @intCast(dex.items.len), 0, 0 };
    var cd_offs: [3]u32 = cd_off;
    cd_offs[1] = cd_offs[0] + 22;
    cd_offs[2] = cd_offs[1] + 12;
    var code_off: [9]u32 = undefined;
    {
        var at: usize = cd_offs[2] + 18;
        for (0..9) |i| {
            while (at % 4 != 0) at += 1;
            code_off[i] = @intCast(at);
            at += code_bufs[i].items.len;
        }
    }
    const wUleb = struct {
        fn f(d: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u32) !void {
            var x = v;
            while (true) {
                var b: u8 = @intCast(x & 0x7F);
                x >>= 7;
                if (x != 0) b |= 0x80;
                try d.append(g, b);
                if (x == 0) break;
            }
        }
    }.f;
    // Counter class data.
    try dex.appendSlice(gpa, &[_]u8{ 0, 1, 2, 2 });
    try dex.appendSlice(gpa, &[_]u8{ 0, 1 });
    try dex.appendSlice(gpa, &[_]u8{ 1, 1 });
    try wUleb(&dex, gpa, code_off[0]);
    try dex.appendSlice(gpa, &[_]u8{ 1, 1 });
    try wUleb(&dex, gpa, code_off[1]);
    try dex.appendSlice(gpa, &[_]u8{ 3, 1 });
    try wUleb(&dex, gpa, code_off[2]);
    try dex.appendSlice(gpa, &[_]u8{ 1, 1 });
    try wUleb(&dex, gpa, code_off[3]);
    std.debug.assert(dex.items.len == cd_offs[0] + cd_lens[0]);
    // FixtureApp class data: 0 static, 0 instance, 1 direct (m6), 1 virtual (m7).
    try dex.appendSlice(gpa, &[_]u8{ 0, 0, 1, 1 });
    try dex.appendSlice(gpa, &[_]u8{ 6, 1 });
    try wUleb(&dex, gpa, code_off[4]);
    try dex.appendSlice(gpa, &[_]u8{ 7, 1 });
    try wUleb(&dex, gpa, code_off[5]);
    std.debug.assert(dex.items.len == cd_offs[1] + cd_lens[1]);
    // FixtureActivity class data: 0 static, 1 instance (f2), 1 direct (m15),
    // 2 virtual (m16, m28: diffs 16, 12).
    try dex.appendSlice(gpa, &[_]u8{ 0, 1, 1, 2 });
    try dex.appendSlice(gpa, &[_]u8{ 2, 1 });
    try dex.appendSlice(gpa, &[_]u8{ 15, 1 });
    try wUleb(&dex, gpa, code_off[6]);
    try dex.appendSlice(gpa, &[_]u8{ 16, 1 });
    try wUleb(&dex, gpa, code_off[7]);
    try dex.appendSlice(gpa, &[_]u8{ 12, 1 });
    try wUleb(&dex, gpa, code_off[8]);
    std.debug.assert(dex.items.len == cd_offs[2] + cd_lens[2]);
    for (0..9) |i| {
        while (dex.items.len % 4 != 0) try dex.append(gpa, 0);
        std.debug.assert(dex.items.len == code_off[i]);
        try dex.appendSlice(gpa, code_bufs[i].items);
    }
    for (0..3) |i| {
        std.mem.writeInt(u32, dex.items[cd_pos[i]..][0..4], cd_offs[i], .little);
    }

    // --- Header ---
    @memcpy(dex.items[0..8], "dex\n039\x00");
    std.mem.writeInt(u32, dex.items[32..][0..4], @intCast(dex.items.len), .little); // file_size
    std.mem.writeInt(u32, dex.items[36..][0..4], 0x70, .little); // header_size
    std.mem.writeInt(u32, dex.items[40..][0..4], 0x12345678, .little); // endian
    std.mem.writeInt(u32, dex.items[52..][0..4], 0, .little); // map_off (absent)
    std.mem.writeInt(u32, dex.items[56..][0..4], strs.len, .little);
    std.mem.writeInt(u32, dex.items[60..][0..4], string_ids_off, .little);
    std.mem.writeInt(u32, dex.items[64..][0..4], type_strs.len, .little);
    std.mem.writeInt(u32, dex.items[68..][0..4], type_ids_off, .little);
    std.mem.writeInt(u32, dex.items[72..][0..4], 37, .little);
    std.mem.writeInt(u32, dex.items[76..][0..4], proto_ids_off, .little);
    std.mem.writeInt(u32, dex.items[80..][0..4], 3, .little);
    std.mem.writeInt(u32, dex.items[84..][0..4], field_ids_off, .little);
    std.mem.writeInt(u32, dex.items[88..][0..4], methods.len, .little);
    std.mem.writeInt(u32, dex.items[92..][0..4], method_ids_off, .little);
    std.mem.writeInt(u32, dex.items[96..][0..4], 3, .little);
    std.mem.writeInt(u32, dex.items[100..][0..4], class_defs_off, .little);
    // data section: from end of class defs to EOF.
    const data_start: u32 = class_defs_off + 96;
    std.mem.writeInt(u32, dex.items[104..][0..4], @intCast(dex.items.len - data_start), .little);
    std.mem.writeInt(u32, dex.items[108..][0..4], data_start, .little);
    // SHA-1 over 32..len, Adler over 12..len.
    var digest: [20]u8 = undefined;
    std.crypto.hash.Sha1.hash(dex.items[32..], &digest, .{});
    @memcpy(dex.items[12..32], &digest);
    std.mem.writeInt(u32, dex.items[8..][0..4], std.hash.Adler32.hash(dex.items[12..]), .little);
    return dex.toOwnedSlice(gpa);
}

const ZipOutEntry = struct {
    name: []const u8,
    data: []const u8,
    compress: bool,
};

fn writeZip(
    io: std.Io,
    gpa: std.mem.Allocator,
    out_path: []const u8,
    entries: []const ZipOutEntry,
) !void {
    var file = try std.Io.Dir.cwd().createFile(io, out_path, .{});
    defer file.close(io);
    var file_buf: [8192]u8 = undefined;
    var fw = file.writer(io, &file_buf);
    const w = &fw.interface;

    var central: std.ArrayListUnmanaged(struct {
        name: []const u8,
        crc: u32,
        comp_size: u32,
        uncomp_size: u32,
        offset: u32,
        method: u16,
    }) = .empty;
    defer central.deinit(gpa);

    var offset: u32 = 0;
    for (entries) |e| {
        const crc = std.hash.Crc32.hash(e.data);
        var comp_data: []const u8 = e.data;
        var comp_owned: ?[]u8 = null;
        defer if (comp_owned) |c| gpa.free(c);
        var method: u16 = 0;
        if (e.compress and e.data.len > 0) {
            var comp_buf: [131072]u8 = undefined;
            var cw = std.Io.Writer.fixed(&comp_buf);
            var window: [std.compress.flate.max_window_len]u8 = undefined;
            var comp = try std.compress.flate.Compress.init(&cw, &window, .raw, .fastest);
            try comp.writer.writeAll(e.data);
            try comp.finish();
            comp_owned = try gpa.dupe(u8, comp_buf[0..cw.end]);
            comp_data = comp_owned.?;
            method = 8;
        }
        // Local header.
        try writeU32(w, 0x04034b50);
        try writeU16(w, 20);
        try writeU16(w, 0);
        try writeU16(w, method);
        try writeU16(w, 0);
        try writeU16(w, 0);
        try writeU32(w, crc);
        try writeU32(w, @intCast(comp_data.len));
        try writeU32(w, @intCast(e.data.len));
        try writeU16(w, @intCast(e.name.len));
        try writeU16(w, 0);
        try w.writeAll(e.name);
        try w.writeAll(comp_data);
        try central.append(gpa, .{
            .name = e.name,
            .crc = crc,
            .comp_size = @intCast(comp_data.len),
            .uncomp_size = @intCast(e.data.len),
            .offset = offset,
            .method = method,
        });
        offset += 30 + @as(u32, @intCast(e.name.len)) + @as(u32, @intCast(comp_data.len));
    }
    const cd_start = offset;
    for (central.items) |c| {
        try writeU32(w, 0x02014b50);
        try writeU16(w, 20);
        try writeU16(w, 20);
        try writeU16(w, 0);
        try writeU16(w, c.method);
        try writeU16(w, 0);
        try writeU16(w, 0);
        try writeU32(w, c.crc);
        try writeU32(w, c.comp_size);
        try writeU32(w, c.uncomp_size);
        try writeU16(w, @intCast(c.name.len));
        try writeU16(w, 0);
        try writeU16(w, 0);
        try writeU16(w, 0);
        try writeU16(w, 0);
        try writeU32(w, 0);
        try writeU32(w, c.offset);
        try w.writeAll(c.name);
        offset += 46 + @as(u32, @intCast(c.name.len));
    }
    const cd_size = offset - cd_start;
    try writeU32(w, 0x06054b50);
    try writeU16(w, 0);
    try writeU16(w, 0);
    try writeU16(w, @intCast(central.items.len));
    try writeU16(w, @intCast(central.items.len));
    try writeU32(w, cd_size);
    try writeU32(w, cd_start);
    try writeU16(w, 0);
    try w.flush();
}

/// Fixture resources.arsc: package 0x7f with string entries
/// 0x7f110000 "Fixture" (app label), 0x7f110001 "Fixture" (activity
/// label), 0x7f110002 greeting text, color 0x7f060000, dimen
/// 0x7f070000 (48dp), layout 0x7f0c0000 -> res/layout/main.xml, and a
/// string array 0x7f030000 ["Fixture","Clicked!"]. The string type ships
/// a qualified twin chunk so the default-config-wins rule is covered.
fn buildArsc(gpa: std.mem.Allocator) ![]u8 {
    var out: std.ArrayListUnmanaged(u8) = .empty;
    errdefer out.deinit(gpa);
    const wPool = struct {
        fn f(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, strs: []const []const u8) !void {
            try writeU16List(l, g, 1);
            try writeU16List(l, g, 28);
            const len_pos = l.items.len;
            try writeU32List(l, g, 0); // patched
            try writeU32List(l, g, @intCast(strs.len));
            try writeU32List(l, g, 0);
            try writeU32List(l, g, 0x100);
            const start_pos = l.items.len;
            try writeU32List(l, g, 0); // patched
            try writeU32List(l, g, 0);
            var data: std.ArrayListUnmanaged(u8) = .empty;
            defer data.deinit(g);
            var offs: std.ArrayListUnmanaged(u32) = .empty;
            defer offs.deinit(g);
            for (strs) |s| {
                try offs.append(g, @intCast(data.items.len));
                var tmp: [4096]u8 = undefined;
                var tw = std.Io.Writer.fixed(&tmp);
                try writePoolString(&tw, s);
                try data.appendSlice(g, tmp[0..tw.end]);
            }
            for (offs.items) |o| try writeU32List(l, g, o);
            while (data.items.len % 4 != 0) try data.append(g, 0);
            const strings_start: u32 = @intCast(8 + (l.items.len - len_pos - 4));
            try l.appendSlice(g, data.items);
            std.mem.writeInt(u32, l.items[len_pos..][0..4], @intCast(l.items.len - (len_pos - 4)), .little);
            std.mem.writeInt(u32, l.items[start_pos..][0..4], strings_start, .little);
        }
    }.f;
    const wEntry = struct {
        fn f(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, key: u32, dtype: u8, data: u32) !void {
            try writeU16List(l, g, 8); // ResTable_entry header; the Res_value follows it
            try writeU16List(l, g, 0); // simple
            try writeU32List(l, g, key);
            try writeU16List(l, g, 8);
            try l.append(g, 0);
            try l.append(g, dtype);
            try writeU32List(l, g, data);
        }
    }.f;
    const wType = struct {
        fn f(
            l: *std.ArrayListUnmanaged(u8),
            g: std.mem.Allocator,
            tid: u8,
            qualified: bool,
            offs: []const u32,
            entries: []const u8,
        ) !void {
            try writeU16List(l, g, 0x201);
            try writeU16List(l, g, 84);
            const len_pos = l.items.len;
            try writeU32List(l, g, 0); // patched
            try l.append(g, tid);
            try l.append(g, 0);
            try writeU16List(l, g, 0);
            try writeU32List(l, g, @intCast(offs.len));
            try writeU32List(l, g, 84 + @as(u32, @intCast(offs.len)) * 4);
            try writeU32List(l, g, 64);
            var cfg: [60]u8 = std.mem.zeroes([60]u8);
            if (qualified) cfg[10] = 240; // density qualifier copy
            try l.appendSlice(g, &cfg);
            for (offs) |o| try writeU32List(l, g, o);
            try l.appendSlice(g, entries);
            std.mem.writeInt(u32, l.items[len_pos..][0..4], @intCast(l.items.len - (len_pos - 4)), .little);
        }
    }.f;
    const wSpec = struct {
        fn f(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, tid: u8, count: u32) !void {
            try writeU16List(l, g, 0x202);
            try writeU16List(l, g, 16);
            try writeU32List(l, g, 16 + count * 4);
            try l.append(g, tid);
            try l.append(g, 0);
            try writeU16List(l, g, 0);
            try writeU32List(l, g, count);
            for (0..count) |_| try writeU32List(l, g, 0xFFFFFFFF);
        }
    }.f;

    try writeU16List(&out, gpa, 2);
    try writeU16List(&out, gpa, 12);
    const table_len = out.items.len;
    try writeU32List(&out, gpa, 0); // patched
    try writeU32List(&out, gpa, 1);
    // Global pool: 0 "Fixture", 1 "Clicked!", 2 greeting, 3 layout path,
    // 4 hdpi twin, 5 hi string for the array.
    try wPool(&out, gpa, &.{ "Fixture", "Clicked!", "Hello Isakai", "res/layout/main.xml", "Fixture-hdpi", "Hi" });

    const pkg_pos = out.items.len;
    try writeU16List(&out, gpa, 0x200);
    try writeU16List(&out, gpa, 288);
    const pkg_len = out.items.len;
    try writeU32List(&out, gpa, 0); // patched
    try writeU32List(&out, gpa, 0x7f);
    const pkg_bytes = "org.isakai.fixture";
    for (0..128) |i| {
        const w: u16 = if (i < pkg_bytes.len) pkg_bytes[i] else 0;
        try writeU16List(&out, gpa, w);
    }
    const type_rel = out.items.len;
    try writeU32List(&out, gpa, 0); // patched
    try writeU32List(&out, gpa, 0);
    const key_rel = out.items.len;
    try writeU32List(&out, gpa, 0); // patched
    try writeU32List(&out, gpa, 0);
    try writeU32List(&out, gpa, 0);
    const type_pool_off = out.items.len - pkg_pos;
    try wPool(&out, gpa, &.{
        "anim", "animator", "array", "attr", "bool", "color", "dimen", "drawable",
        "id", "integer", "interpolator", "layout", "font", "menu", "mipmap",
        "plurals", "string", "style", "raw", "xml",
    });
    const key_pool_off = out.items.len - pkg_pos;
    try wPool(&out, gpa, &.{
        "app_name", "activity_label", "greeting", "sample_color", "margin",
        "main", "greetings",
    });

    // String type (17): entries 0..2; qualified twin asserts default wins.
    var e_def: std.ArrayListUnmanaged(u8) = .empty;
    defer e_def.deinit(gpa);
    try wEntry(&e_def, gpa, 0, 3, 0); // app_name -> "Fixture"
    try wEntry(&e_def, gpa, 1, 3, 0); // activity_label -> "Fixture"
    try wEntry(&e_def, gpa, 2, 3, 2); // greeting -> "Hello Isakai"
    var e_q: std.ArrayListUnmanaged(u8) = .empty;
    defer e_q.deinit(gpa);
    try wEntry(&e_q, gpa, 0, 3, 4); // hdpi twin (must lose)
    try wSpec(&out, gpa, 17, 3);
    try wType(&out, gpa, 17, false, &.{ 0, 16, 32 }, e_def.items);
    try wType(&out, gpa, 17, true, &.{ 0, 0xFFFFFFFF, 0xFFFFFFFF }, e_q.items);
    // Color type (6): sample_color -> packed ARGB.
    var e_c: std.ArrayListUnmanaged(u8) = .empty;
    defer e_c.deinit(gpa);
    try wEntry(&e_c, gpa, 3, 0x1c, 0xFF112233);
    try wSpec(&out, gpa, 6, 1);
    try wType(&out, gpa, 6, false, &.{0}, e_c.items);
    // Dimen type (7): margin -> 48dp (0x3001: 48 << 8 | dp).
    var e_d: std.ArrayListUnmanaged(u8) = .empty;
    defer e_d.deinit(gpa);
    try wEntry(&e_d, gpa, 4, 5, 0x3001);
    try wSpec(&out, gpa, 7, 1);
    try wType(&out, gpa, 7, false, &.{0}, e_d.items);
    // Layout type (12): main -> res/layout/main.xml.
    var e_l: std.ArrayListUnmanaged(u8) = .empty;
    defer e_l.deinit(gpa);
    try wEntry(&e_l, gpa, 5, 3, 3);
    try wSpec(&out, gpa, 12, 1);
    try wType(&out, gpa, 12, false, &.{0}, e_l.items);
    // Array type (3): greetings -> bag of ["Fixture", "Clicked!"].
    var e_a: std.ArrayListUnmanaged(u8) = .empty;
    defer e_a.deinit(gpa);
    try writeU16List(&e_a, gpa, 16); // entry size
    try writeU16List(&e_a, gpa, 1); // COMPLEX
    try writeU32List(&e_a, gpa, 6); // key: greetings
    try writeU32List(&e_a, gpa, 0); // parent: none
    try writeU32List(&e_a, gpa, 2); // count
    for ([_]u32{ 0, 1 }) |gi| {
        try writeU32List(&e_a, gpa, 0x02000000 | gi); // name: Res_MAKEARRAY(index)
        try writeU16List(&e_a, gpa, 8);
        try e_a.append(gpa, 0);
        try e_a.append(gpa, 3);
        try writeU32List(&e_a, gpa, gi);
    }
    try wSpec(&out, gpa, 3, 1);
    try wType(&out, gpa, 3, false, &.{0}, e_a.items);

    std.mem.writeInt(u32, out.items[type_rel..][0..4], @intCast(type_pool_off), .little);
    std.mem.writeInt(u32, out.items[key_rel..][0..4], @intCast(key_pool_off), .little);
    std.mem.writeInt(u32, out.items[pkg_len..][0..4], @intCast(out.items.len - pkg_pos), .little);
    std.mem.writeInt(u32, out.items[table_len..][0..4], @intCast(out.items.len), .little);
    return out.toOwnedSlice(gpa);
}

fn writeU16List(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u16) !void {
    var b: [2]u8 = undefined;
    std.mem.writeInt(u16, &b, v, .little);
    try l.appendSlice(g, &b);
}

fn writeU32List(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u32) !void {
    var b: [4]u8 = undefined;
    std.mem.writeInt(u32, &b, v, .little);
    try l.appendSlice(g, &b);
}

/// Plain-text layout staged at res/layout/main.xml, referenced by the
/// layout entry above. Covered by the XmlResourceParser path.
fn layoutXml() []const u8 {
    return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"><EditText android:text=\"Hello Isakai\" /></LinearLayout>\n";
}

/// 0.5 s 440 Hz sine, mono S16LE 44100, wrapped in a 44-byte WAV header.
fn buildToneWav(gpa: std.mem.Allocator) ![]u8 {
    const frames: usize = 22050;
    const pcm_len: usize = frames * 2;
    const total: usize = 44 + pcm_len;
    const out = try gpa.alloc(u8, total);
    // RIFF header.
    @memcpy(out[0..4], "RIFF");
    std.mem.writeInt(u32, out[4..8], @intCast(total - 8), .little);
    @memcpy(out[8..12], "WAVE");
    @memcpy(out[12..16], "fmt ");
    std.mem.writeInt(u32, out[16..20], 16, .little);
    std.mem.writeInt(u16, out[20..22], 1, .little); // PCM
    std.mem.writeInt(u16, out[22..24], 1, .little); // mono
    std.mem.writeInt(u32, out[24..28], 44100, .little);
    std.mem.writeInt(u32, out[28..32], 44100 * 2, .little); // byte rate
    std.mem.writeInt(u16, out[32..34], 2, .little); // block align
    std.mem.writeInt(u16, out[34..36], 16, .little);
    @memcpy(out[36..40], "data");
    std.mem.writeInt(u32, out[40..44], @intCast(pcm_len), .little);
    for (0..frames) |i| {
        const t: f64 = @as(f64, @floatFromInt(i)) / 44100.0;
        const v: f64 = @sin(2.0 * std.math.pi * 440.0 * t);
        const s16: i16 = @intFromFloat(v * 16000.0);
        std.mem.writeInt(i16, out[44 + i * 2 ..][0..2], s16, .little);
    }
    return out;
}

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    if (argv.len < 2) {
        std.debug.print("usage: fixture-apk <output.apk> [package] [versionCode]\n", .{});
        return 2;
    }
    const out_path: []const u8 = argv[1];
    const package: []const u8 = if (argv.len > 2) argv[2] else default_package;
    const version_code: u32 = if (argv.len > 3) (std.fmt.parseInt(u32, argv[3], 10) catch 1) else 1;

    const manifest = try buildManifest(arena, package, version_code);
    const dex = try buildDex(arena);
    const tone = try buildToneWav(arena);
    const arsc_bytes = try buildArsc(arena);
    try writeZip(io, arena, out_path, &.{
        .{ .name = "AndroidManifest.xml", .data = manifest, .compress = true },
        .{ .name = "classes.dex", .data = dex, .compress = true },
        .{ .name = "assets/tone.wav", .data = tone, .compress = false },
        .{ .name = "assets/hello.txt", .data = "hello assets", .compress = false },
        .{ .name = "resources.arsc", .data = arsc_bytes, .compress = false },
        .{ .name = "res/layout/main.xml", .data = layoutXml(), .compress = true },
    });
    std.debug.print("wrote {s} package={s} manifest={d} dex={d}\n", .{ out_path, package, manifest.len, dex.len });
    return 0;
}
