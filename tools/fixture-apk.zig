// SPDX-License-Identifier: GPL-3.0-or-later

//! Builds a minimal fixture APK without the Android SDK: a ZIP with a binary
//! AndroidManifest.xml, a tiny classes.dex, and nothing else.
//!
//! Usage: fixture-apk <output.apk> [package]
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
fn buildManifest(gpa: std.mem.Allocator, package: []const u8) ![]u8 {
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
        ".FixtureApp", // 9 (placeholder, replaced below)
        "activity", // 10
        ".FixtureActivity", // 11
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
        ".FixtureApp",
        "activity",
        ".FixtureActivity",
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
        "Fixture",
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
        label_val: u32 = 23,
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
        .{ .ns = S.android_ns, .name = S.version_code, .raw = NONE, .typ = 0x10, .val = 1 },
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
    // <application android:name=".FixtureApp" android:label="Fixture">
    try writeStart(w, 4, NONE, S.application, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.app_class, .typ = 3, .val = S.app_class },
        .{ .ns = S.android_ns, .name = S.label_attr, .raw = S.label_val, .typ = 3, .val = S.label_val },
    });
    // <activity android:name=".FixtureActivity" android:label="Fixture">
    try writeStart(w, 5, NONE, S.activity, &.{
        .{ .ns = S.android_ns, .name = S.name_attr, .raw = S.activity_class, .typ = 3, .val = S.activity_class },
        .{ .ns = S.android_ns, .name = S.label_attr, .raw = S.label_val, .typ = 3, .val = S.label_val },
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
    // 17 OnClickListener, 18 Z
    const type_ids_off: u32 = @intCast(dex.items.len);
    for ([_]u32{ 0, 1, 2, 3, 9, 10, 11, 16, 19, 21, 23, 24, 34, 37, 38, 39, 40, 41, 51 }) |s| try w32(&dex, gpa, s);

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

    // --- Protos ---
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
        // 5 FixtureApp.onCreate: regs=7, this=v6
        .{ .regs = 7, .ins = 1, .outs = 3, .insns = &[_]u16{
            0x001a, 14, // const-string v0, "counter"
            0x011a, 15, // const-string v1, "41"
            0x306e, 8, 0x0106, // invoke-virtual {v6,v0,v1}, readFile
            0x000c, // move-result-object v0
            0x1071, 10, 0x0000, // invoke-static {v0}, parseInt
            0x010a, // move-result v1
            0x01d8, 0x0101, // add-int/lit8 v1, v1, 1
            0x1071, 11, 0x0001, // invoke-static {v1}, valueOf
            0x020c, // move-result-object v2
            0x001a, 14, // const-string v0, "counter"
            0x306e, 9, 0x0206, // invoke-virtual {v6,v0,v2}, writeFile
            0x0362, 1, // sget-object v3, System.out
            0x206e, 12, 0x0013, // invoke-virtual {v3,v1}, println(I)
            0x041a, 26, // const-string v4, msg
            0x206e, 13, 0x0043, // invoke-virtual {v3,v4}, println(L)
            0x000e, // return-void
        } },
        // 6 FixtureActivity.<init>: invoke-direct {v0}, m14
        .{ .regs = 1, .ins = 1, .outs = 1, .insns = &[_]u16{ 0x1070, 0x000e, 0x0000, 0x000e } },
        // 7 FixtureActivity.onCreate: regs=8, this=v7.
        // v0=root, v1=edit, v2=button, v3..v6 scratch/coords.
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
    std.mem.writeInt(u32, dex.items[64..][0..4], 19, .little);
    std.mem.writeInt(u32, dex.items[68..][0..4], type_ids_off, .little);
    std.mem.writeInt(u32, dex.items[72..][0..4], 13, .little);
    std.mem.writeInt(u32, dex.items[76..][0..4], proto_ids_off, .little);
    std.mem.writeInt(u32, dex.items[80..][0..4], 3, .little);
    std.mem.writeInt(u32, dex.items[84..][0..4], field_ids_off, .little);
    std.mem.writeInt(u32, dex.items[88..][0..4], 29, .little);
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

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    if (argv.len < 2) {
        std.debug.print("usage: fixture-apk <output.apk> [package]\n", .{});
        return 2;
    }
    const out_path: []const u8 = argv[1];
    const package: []const u8 = if (argv.len > 2) argv[2] else default_package;

    const manifest = try buildManifest(arena, package);
    const dex = try buildDex(arena);
    try writeZip(io, arena, out_path, &.{
        .{ .name = "AndroidManifest.xml", .data = manifest, .compress = true },
        .{ .name = "classes.dex", .data = dex, .compress = true },
        .{ .name = "resources.arsc", .data = &[_]u8{ 2, 0, 12, 0, 0, 0, 0, 0, 1, 0, 0, 0 }, .compress = false },
    });
    std.debug.print("wrote {s} package={s} manifest={d} dex={d}\n", .{ out_path, package, manifest.len, dex.len });
    return 0;
}
