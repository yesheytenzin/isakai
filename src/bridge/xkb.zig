// SPDX-License-Identifier: GPL-3.0-or-later

//! libxkbcommon wrapper: context/keymap/state lifecycle, modifier queries,
//! and per-key symbol/UTF-32 lookups for the Wayland seat input path.
//!
//! All `extern` declarations and every call that resolves a libxkbcommon
//! symbol live in this module. `window.zig` stays std-only and calls these
//! functions. In `zig test` (builtin.is_test) a fake backend answers from
//! in-memory tables so the seat/ring/repeat tests run without a compositor
//! or libxkbcommon; the real backend is used in normal builds.
//!
//! Wire note: evdev keycodes from wl_keyboard.key arrive 8 below the XKB
//! keycode numbering, so callers MUST add 8 before querying.

const std = @import("std");
const builtin = @import("builtin");

const use_fake = builtin.is_test;

const OpaqueCtx = opaque {};
const OpaqueMap = opaque {};
const OpaqueState = opaque {};

pub const Context = if (use_fake) *FakeCtx else *OpaqueCtx;
pub const Keymap = if (use_fake) *FakeMap else *OpaqueMap;
pub const State = if (use_fake) *FakeState else *OpaqueState;

pub const FORMAT_TEXT_V1: c_int = 1;
pub const MODS_EFFECTIVE: c_int = 1 << 3;

const FakeCtx = struct {
    magic: u32 = 0x786b_6378,
};

const FakeMap = struct {
    repeats_default: bool = true,
    no_repeat_key: u32 = 0,
};

const FakeState = struct {
    depressed: u32 = 0,
    latched: u32 = 0,
    locked: u32 = 0,
    shift: bool = false,
    ctrl: bool = false,
    alt: bool = false,
    logo: bool = false,
};

var fake_ctx: FakeCtx = .{};
var fake_map: FakeMap = .{};
var fake_state: FakeState = .{};

/// One fake key entry: XKB keycode -> keysym/utf32 + evdev->Android mapping.
/// `shifted` marks entries whose keysym needs the Shift level (fake backend
/// only; the real backend resolves levels through xkb_state).
const FakeKey = struct {
    key: u32,
    sym: u32,
    utf32: u32,
};

/// Minimal fake key table (XKB keycodes = evdev + 8). Symbols follow
/// XKB keysymdefs (0x20..0x7e mirror ASCII; special keys below).
const fake_keys = [_]FakeKey{
    .{ .key = 38, .sym = 0x61, .utf32 = 0x61 }, // a
    .{ .key = 56, .sym = 0x62, .utf32 = 0x62 }, // b
    .{ .key = 54, .sym = 0x63, .utf32 = 0x63 }, // c
    .{ .key = 40, .sym = 0x64, .utf32 = 0x64 }, // d
    .{ .key = 26, .sym = 0x65, .utf32 = 0x65 }, // e
    .{ .key = 41, .sym = 0x66, .utf32 = 0x66 }, // f
    .{ .key = 42, .sym = 0x67, .utf32 = 0x67 }, // g
    .{ .key = 43, .sym = 0x68, .utf32 = 0x68 }, // h
    .{ .key = 31, .sym = 0x69, .utf32 = 0x69 }, // i
    .{ .key = 44, .sym = 0x6a, .utf32 = 0x6a }, // j
    .{ .key = 45, .sym = 0x6b, .utf32 = 0x6b }, // k
    .{ .key = 46, .sym = 0x6c, .utf32 = 0x6c }, // l
    .{ .key = 58, .sym = 0x6d, .utf32 = 0x6d }, // m
    .{ .key = 57, .sym = 0x6e, .utf32 = 0x6e }, // n
    .{ .key = 32, .sym = 0x6f, .utf32 = 0x6f }, // o
    .{ .key = 33, .sym = 0x70, .utf32 = 0x70 }, // p
    .{ .key = 24, .sym = 0x71, .utf32 = 0x71 }, // q
    .{ .key = 27, .sym = 0x72, .utf32 = 0x72 }, // r
    .{ .key = 39, .sym = 0x73, .utf32 = 0x73 }, // s
    .{ .key = 28, .sym = 0x74, .utf32 = 0x74 }, // t
    .{ .key = 30, .sym = 0x75, .utf32 = 0x75 }, // u
    .{ .key = 55, .sym = 0x76, .utf32 = 0x76 }, // v
    .{ .key = 25, .sym = 0x77, .utf32 = 0x77 }, // w
    .{ .key = 53, .sym = 0x78, .utf32 = 0x78 }, // x
    .{ .key = 29, .sym = 0x79, .utf32 = 0x79 }, // y
    .{ .key = 52, .sym = 0x7a, .utf32 = 0x7a }, // z
    .{ .key = 10, .sym = 0x31, .utf32 = 0x31 }, // 1
    .{ .key = 11, .sym = 0x32, .utf32 = 0x32 }, // 2
    .{ .key = 12, .sym = 0x33, .utf32 = 0x33 }, // 3
    .{ .key = 13, .sym = 0x34, .utf32 = 0x34 }, // 4
    .{ .key = 14, .sym = 0x35, .utf32 = 0x35 }, // 5
    .{ .key = 15, .sym = 0x36, .utf32 = 0x36 }, // 6
    .{ .key = 16, .sym = 0x37, .utf32 = 0x37 }, // 7
    .{ .key = 17, .sym = 0x38, .utf32 = 0x38 }, // 8
    .{ .key = 18, .sym = 0x39, .utf32 = 0x39 }, // 9
    .{ .key = 19, .sym = 0x30, .utf32 = 0x30 }, // 0
    .{ .key = 65, .sym = 0x20, .utf32 = 0x20 }, // space
    .{ .key = 36, .sym = 0xff0d, .utf32 = 0x0d }, // Return
    .{ .key = 104, .sym = 0xff0d, .utf32 = 0x0d }, // KP_Enter
    .{ .key = 22, .sym = 0x08, .utf32 = 0x08 }, // BackSpace
    .{ .key = 119, .sym = 0xffff, .utf32 = 0 }, // Delete
    .{ .key = 23, .sym = 0xff09, .utf32 = 0x09 }, // Tab
    .{ .key = 9, .sym = 0xff1b, .utf32 = 0x1b }, // Escape
    .{ .key = 110, .sym = 0xff50, .utf32 = 0 }, // Home
    .{ .key = 113, .sym = 0xff51, .utf32 = 0 }, // Left
    .{ .key = 111, .sym = 0xff52, .utf32 = 0 }, // Up
    .{ .key = 114, .sym = 0xff53, .utf32 = 0 }, // Right
    .{ .key = 116, .sym = 0xff54, .utf32 = 0 }, // Down
    .{ .key = 112, .sym = 0xff55, .utf32 = 0 }, // Page_Up
    .{ .key = 117, .sym = 0xff56, .utf32 = 0 }, // Page_Down
    .{ .key = 115, .sym = 0xff57, .utf32 = 0 }, // End
    .{ .key = 118, .sym = 0xff63, .utf32 = 0 }, // Insert
    .{ .key = 59, .sym = 0x2c, .utf32 = 0x2c }, // comma
    .{ .key = 60, .sym = 0x2e, .utf32 = 0x2e }, // period
    .{ .key = 61, .sym = 0x2f, .utf32 = 0x2f }, // slash
    .{ .key = 49, .sym = 0x60, .utf32 = 0x60 }, // grave
    .{ .key = 20, .sym = 0x2d, .utf32 = 0x2d }, // minus
    .{ .key = 21, .sym = 0x3d, .utf32 = 0x3d }, // equal
    .{ .key = 34, .sym = 0x5b, .utf32 = 0x5b }, // bracketleft
    .{ .key = 35, .sym = 0x5d, .utf32 = 0x5d }, // bracketright
    .{ .key = 51, .sym = 0x5c, .utf32 = 0x5c }, // backslash
    .{ .key = 47, .sym = 0x3b, .utf32 = 0x3b }, // semicolon
    .{ .key = 48, .sym = 0x27, .utf32 = 0x27 }, // apostrophe
    .{ .key = 50, .sym = 0xffe1, .utf32 = 0 }, // Shift_L
    .{ .key = 62, .sym = 0xffe2, .utf32 = 0 }, // Shift_R
    .{ .key = 37, .sym = 0xffe3, .utf32 = 0 }, // Control_L
    .{ .key = 105, .sym = 0xffe4, .utf32 = 0 }, // Control_R
    .{ .key = 64, .sym = 0xffe9, .utf32 = 0 }, // Alt_L
    .{ .key = 108, .sym = 0xffea, .utf32 = 0 }, // Alt_R
    .{ .key = 133, .sym = 0xffeb, .utf32 = 0 }, // Super_L
    .{ .key = 134, .sym = 0xffec, .utf32 = 0 }, // Super_R
    .{ .key = 77, .sym = 0xff7f, .utf32 = 0 }, // Num_Lock
    .{ .key = 66, .sym = 0xffe5, .utf32 = 0 }, // Caps_Lock
};

fn fakeLookup(key: u32) ?FakeKey {
    for (fake_keys) |e| {
        if (e.key == key) return e;
    }
    return null;
}

// ---------------------------------------------------------------------------
// Real backend: raw extern declarations (link against -lxkbcommon).
// ---------------------------------------------------------------------------

const RealDecls = if (use_fake) struct {} else struct {
    pub extern fn xkb_context_new(flags: c_int) ?*OpaqueCtx;
    pub extern fn xkb_context_unref(context: ?*OpaqueCtx) void;
    pub extern fn xkb_keymap_new_from_string(
        context: ?*OpaqueCtx,
        string: ?[*:0]const u8,
        format: c_int,
        flags: c_int,
    ) ?*OpaqueMap;
    pub extern fn xkb_keymap_unref(keymap: ?*OpaqueMap) void;
    pub extern fn xkb_state_new(keymap: ?*OpaqueMap) ?*OpaqueState;
    pub extern fn xkb_state_unref(state: ?*OpaqueState) void;
    pub extern fn xkb_state_update_mask(
        state: ?*OpaqueState,
        depressed_mods: u32,
        latched_mods: u32,
        locked_mods: u32,
        depressed_layout: u32,
        latched_layout: u32,
        locked_layout: u32,
    ) c_int;
    pub extern fn xkb_state_key_get_utf32(state: ?*OpaqueState, key: u32) u32;
    pub extern fn xkb_state_mod_name_is_active(
        state: ?*OpaqueState,
        name: ?[*:0]const u8,
        @"type": c_int,
    ) c_int;
    pub extern fn xkb_keymap_key_repeats(keymap: ?*OpaqueMap, key: u32) c_int;
};

// ---------------------------------------------------------------------------
// Public lifecycle API (both backends).
// ---------------------------------------------------------------------------

pub fn contextNew() ?Context {
    if (use_fake) return &fake_ctx;
    return RealDecls.xkb_context_new(0);
}

pub fn contextUnref(ctx: ?Context) void {
    if (use_fake) return;
    RealDecls.xkb_context_unref(ctx);
}

/// Compile a keymap from a NUL-terminated XKB string. Returns null on
/// compile failure.
pub fn keymapNewFromString(ctx: Context, str: [*:0]const u8) ?Keymap {
    if (use_fake) {
        fake_map = .{};
        return &fake_map;
    }
    return RealDecls.xkb_keymap_new_from_string(ctx, str, FORMAT_TEXT_V1, 0);
}

pub fn keymapUnref(map: ?Keymap) void {
    if (use_fake) return;
    RealDecls.xkb_keymap_unref(map);
}

pub fn stateNew(map: Keymap) ?State {
    if (use_fake) {
        fake_state = .{};
        return &fake_state;
    }
    return RealDecls.xkb_state_new(map);
}

pub fn stateUnref(state: ?State) void {
    if (use_fake) return;
    RealDecls.xkb_state_unref(state);
}

pub fn updateMask(
    state: ?State,
    depressed: u32,
    latched: u32,
    locked: u32,
    group: u32,
) void {
    if (use_fake) {
        // Track on the global fake state too: window.zig may pass null
        // when no keymap is installed yet (tests), and lookups read the
        // global fake_state directly.
        fake_state.depressed = depressed;
        fake_state.latched = latched;
        fake_state.locked = locked;
        fake_state.shift = (depressed & 0x1) != 0;
        if (state) |s| {
            s.depressed = depressed;
            s.latched = latched;
            s.locked = locked;
            s.shift = (depressed & 0x1) != 0;
        }
        return;
    }
    const ready_state = state orelse return;
    _ = RealDecls.xkb_state_update_mask(
        ready_state,
        depressed,
        latched,
        locked,
        0,
        0,
        group,
    );
}

/// UTF-32 codepoint for an XKB keycode (evdev + 8). 0 when none.
pub fn keyGetUtf32(state: ?State, xkb_keycode: u32) u32 {
    if (use_fake) {
        const e = fakeLookup(xkb_keycode) orelse return 0;
        if (fake_state.shift and e.utf32 >= 0x61 and e.utf32 <= 0x7a) {
            return e.utf32 - 0x20;
        }
        return e.utf32;
    }
    return RealDecls.xkb_state_key_get_utf32(state orelse return 0, xkb_keycode);
}

pub const Mod = enum { shift, ctrl, alt, logo };

fn modName(m: Mod) [*:0]const u8 {
    return switch (m) {
        .shift => "Shift",
        .ctrl => "Control",
        .alt => "Alt",
        .logo => "Super",
    };
}

/// True when the named modifier is active in the EFFECTIVE state.
/// `side`: 0 = either, 1 = left, 2 = right (from the depressed mask bit
/// for that side). The real backend asks xkb for "Shift"/"Control" etc.;
/// left/right refine via the depressed mask passed alongside.
pub fn modActive(state: ?State, m: Mod, side: u8, depressed: u32) bool {
    if (use_fake) {
        // Null state (no keymap yet): read the global fake state, which
        // updateMask maintains even for null.
        const s = state orelse &fake_state;
        return switch (m) {
            .shift => if (side == 0)
                s.shift
            else if (side == 1)
                (depressed & 0x1) != 0
            else
                (depressed & 0x10) != 0,
            .ctrl => s.ctrl or ((depressed & 0x4) != 0),
            .alt => s.alt or ((depressed & 0x8) != 0),
            .logo => s.logo or ((depressed & 0x40) != 0),
        };
    }
    const active = RealDecls.xkb_state_mod_name_is_active(state orelse return false, modName(m), MODS_EFFECTIVE);
    if (active != 1) return false;
    // Side refinement comes from the depressed mask tracked by the caller.
    return true;
}

/// Test helper: mark one XKB keycode as non-repeating in the fake map.
pub fn fakeSetNoRepeat(xkb_keycode: u32) void {
    fake_map.no_repeat_key = xkb_keycode;
}

/// True when the key should auto-repeat (client-side repeat arming).
pub fn keyRepeats(map: ?Keymap, xkb_keycode: u32) bool {
    if (use_fake) {
        if (fake_map.no_repeat_key != 0 and fake_map.no_repeat_key == xkb_keycode) return false;
        return fake_map.repeats_default;
    }
    return RealDecls.xkb_keymap_key_repeats(map orelse return false, xkb_keycode) == 1;
}

// ---------------------------------------------------------------------------
// evdev (kernel code, i.e. wl_keyboard.key code) -> Android keyCode table.
// Indexed by evdev keycode 0..255; unknown entries are 0 (KEYCODE_UNKNOWN)
// and the caller still forwards the unicode char.
// Sources: linux/input-event-codes.h vs android/view/KeyEvent.java.
// ---------------------------------------------------------------------------

/// Android keyCode values needed by the table (mirror KeyEvent.java).
pub const AKEY = struct {
    pub const UNKNOWN: i32 = 0;
    pub const BACK: i32 = 4;
    pub const KEY_0: i32 = 7;
    pub const KEY_1: i32 = 8;
    pub const KEY_2: i32 = 9;
    pub const KEY_3: i32 = 10;
    pub const KEY_4: i32 = 11;
    pub const KEY_5: i32 = 12;
    pub const KEY_6: i32 = 13;
    pub const KEY_7: i32 = 14;
    pub const KEY_8: i32 = 15;
    pub const KEY_9: i32 = 16;
    pub const STAR: i32 = 17;
    pub const POUND: i32 = 18;
    pub const DPAD_UP: i32 = 19;
    pub const DPAD_DOWN: i32 = 20;
    pub const DPAD_LEFT: i32 = 21;
    pub const DPAD_RIGHT: i32 = 22;
    pub const DPAD_CENTER: i32 = 23;
    pub const VOLUME_UP: i32 = 24;
    pub const VOLUME_DOWN: i32 = 25;
    pub const POWER: i32 = 26;
    pub const CAMERA: i32 = 27;
    pub const CLEAR: i32 = 28;
    pub const A: i32 = 29;
    pub const B: i32 = 30;
    pub const C: i32 = 31;
    pub const D: i32 = 32;
    pub const E: i32 = 33;
    pub const F: i32 = 34;
    pub const G: i32 = 35;
    pub const H: i32 = 36;
    pub const I: i32 = 37;
    pub const J: i32 = 38;
    pub const K: i32 = 39;
    pub const L: i32 = 40;
    pub const M: i32 = 41;
    pub const N: i32 = 42;
    pub const O: i32 = 43;
    pub const P: i32 = 44;
    pub const Q: i32 = 45;
    pub const R: i32 = 46;
    pub const S: i32 = 47;
    pub const T: i32 = 48;
    pub const U: i32 = 49;
    pub const V: i32 = 50;
    pub const W: i32 = 51;
    pub const X: i32 = 52;
    pub const Y: i32 = 53;
    pub const Z: i32 = 54;
    pub const COMMA: i32 = 55;
    pub const PERIOD: i32 = 56;
    pub const ALT_LEFT: i32 = 57;
    pub const ALT_RIGHT: i32 = 58;
    pub const SHIFT_LEFT: i32 = 59;
    pub const SHIFT_RIGHT: i32 = 60;
    pub const TAB: i32 = 61;
    pub const SPACE: i32 = 62;
    pub const ENTER: i32 = 66;
    pub const DEL: i32 = 67;
    pub const GRAVE: i32 = 68;
    pub const MINUS: i32 = 69;
    pub const EQUALS: i32 = 70;
    pub const LEFT_BRACKET: i32 = 71;
    pub const RIGHT_BRACKET: i32 = 72;
    pub const BACKSLASH: i32 = 73;
    pub const SEMICOLON: i32 = 74;
    pub const APOSTROPHE: i32 = 75;
    pub const SLASH: i32 = 76;
    pub const AT: i32 = 77;
    pub const NUM: i32 = 78;
    pub const PLUS: i32 = 81;
    pub const MENU: i32 = 82;
    pub const SEARCH: i32 = 84;
    pub const MEDIA_PLAY_PAUSE: i32 = 85;
    pub const MEDIA_STOP: i32 = 86;
    pub const MEDIA_NEXT: i32 = 87;
    pub const MEDIA_PREVIOUS: i32 = 88;
    pub const MEDIA_REWIND: i32 = 89;
    pub const MEDIA_FAST_FORWARD: i32 = 90;
    pub const MEDIA_PAUSE: i32 = 127;
    pub const MUTE: i32 = 91;
    pub const PAGE_UP: i32 = 92;
    pub const PAGE_DOWN: i32 = 93;
    pub const ESCAPE: i32 = 111;
    pub const FORWARD_DEL: i32 = 112;
    pub const CTRL_LEFT: i32 = 113;
    pub const CTRL_RIGHT: i32 = 114;
    pub const CAPS_LOCK: i32 = 115;
    pub const SCROLL_LOCK: i32 = 116;
    pub const META_LEFT: i32 = 117;
    pub const META_RIGHT: i32 = 118;
    pub const FUNCTION: i32 = 119;
    pub const SYSRQ: i32 = 120;
    pub const BREAK: i32 = 121;
    pub const MOVE_HOME: i32 = 122;
    pub const MOVE_END: i32 = 123;
    pub const INSERT: i32 = 124;
    pub const F1: i32 = 131;
    pub const F2: i32 = 132;
    pub const F3: i32 = 133;
    pub const F4: i32 = 134;
    pub const F5: i32 = 135;
    pub const F6: i32 = 136;
    pub const F7: i32 = 137;
    pub const F8: i32 = 138;
    pub const F9: i32 = 139;
    pub const F10: i32 = 140;
    pub const F11: i32 = 141;
    pub const F12: i32 = 142;
    pub const NUM_LOCK: i32 = 143;
    pub const NUMPAD_0: i32 = 144;
    pub const NUMPAD_1: i32 = 145;
    pub const NUMPAD_2: i32 = 146;
    pub const NUMPAD_3: i32 = 147;
    pub const NUMPAD_4: i32 = 148;
    pub const NUMPAD_5: i32 = 149;
    pub const NUMPAD_6: i32 = 150;
    pub const NUMPAD_7: i32 = 151;
    pub const NUMPAD_8: i32 = 152;
    pub const NUMPAD_9: i32 = 153;
    pub const NUMPAD_DIVIDE: i32 = 154;
    pub const NUMPAD_MULTIPLY: i32 = 155;
    pub const NUMPAD_SUBTRACT: i32 = 156;
    pub const NUMPAD_ADD: i32 = 157;
    pub const NUMPAD_DOT: i32 = 158;
    pub const NUMPAD_COMMA: i32 = 159;
    pub const NUMPAD_ENTER: i32 = 160;
    pub const NUMPAD_EQUALS: i32 = 161;
    pub const VOLUME_MUTE: i32 = 164;
};

/// evdev -> Android keyCode, entries 0..255. 0 = unknown.
pub const evdev_to_android = [256]i32{
    0, // 0 RESERVED
    AKEY.BACK, // 1 ESC -> BACK (M3 gate: ESC closes via onBackPressed)
    AKEY.KEY_1, // 2
    AKEY.KEY_2, // 3
    AKEY.KEY_3, // 4
    AKEY.KEY_4, // 5
    AKEY.KEY_5, // 6
    AKEY.KEY_6, // 7
    AKEY.KEY_7, // 8
    AKEY.KEY_8, // 9
    AKEY.KEY_9, // 10
    AKEY.KEY_0, // 11
    AKEY.MINUS, // 12
    AKEY.EQUALS, // 13
    AKEY.DEL, // 14 BACKSPACE
    AKEY.TAB, // 15
    AKEY.Q, // 16
    AKEY.W, // 17
    AKEY.E, // 18
    AKEY.R, // 19
    AKEY.T, // 20
    AKEY.Y, // 21
    AKEY.U, // 22
    AKEY.I, // 23
    AKEY.O, // 24
    AKEY.P, // 25
    AKEY.LEFT_BRACKET, // 26
    AKEY.RIGHT_BRACKET, // 27
    AKEY.ENTER, // 28
    AKEY.CTRL_LEFT, // 29 LEFTCTRL
    AKEY.A, // 30
    AKEY.S, // 31
    AKEY.D, // 32
    AKEY.F, // 33
    AKEY.G, // 34
    AKEY.H, // 35
    AKEY.J, // 36
    AKEY.K, // 37
    AKEY.L, // 38
    AKEY.SEMICOLON, // 39
    AKEY.APOSTROPHE, // 40
    AKEY.GRAVE, // 41
    AKEY.SHIFT_LEFT, // 42
    AKEY.BACKSLASH, // 43
    AKEY.Z, // 44
    AKEY.X, // 45
    AKEY.C, // 46
    AKEY.V, // 47
    AKEY.B, // 48
    AKEY.N, // 49
    AKEY.M, // 50
    AKEY.COMMA, // 51
    AKEY.PERIOD, // 52
    AKEY.SLASH, // 53
    AKEY.SHIFT_RIGHT, // 54
    AKEY.NUMPAD_MULTIPLY, // 55 KP_ASTERISK
    AKEY.ALT_LEFT, // 56
    AKEY.SPACE, // 57
    AKEY.CAPS_LOCK, // 58
    AKEY.F1, // 59
    AKEY.F2, // 60
    AKEY.F3, // 61
    AKEY.F4, // 62
    AKEY.F5, // 63
    AKEY.F6, // 64
    AKEY.F7, // 65
    AKEY.F8, // 66
    AKEY.F9, // 67
    AKEY.F10, // 68
    AKEY.NUM_LOCK, // 69
    AKEY.SCROLL_LOCK, // 70
    AKEY.NUMPAD_7, // 71 KP7
    AKEY.NUMPAD_8, // 72 KP8
    AKEY.NUMPAD_9, // 73 KP9
    AKEY.NUMPAD_SUBTRACT, // 74 KP_MINUS
    AKEY.NUMPAD_4, // 75 KP4
    AKEY.NUMPAD_5, // 76 KP5
    AKEY.NUMPAD_6, // 77 KP6
    AKEY.NUMPAD_ADD, // 78 KP_PLUS
    AKEY.NUMPAD_1, // 79 KP1
    AKEY.NUMPAD_2, // 80 KP2
    AKEY.NUMPAD_3, // 81 KP3
    AKEY.NUMPAD_0, // 82 KP0
    AKEY.NUMPAD_DOT, // 83 KP_DOT
    0, // 84
    0, // 85 ZENKAKUHANKAKU
    AKEY.AT, // 86 102ND -> AT (compat)
    AKEY.F11, // 87
    AKEY.F12, // 88
    0, // 89 RO
    0, // 90 KATAKANA
    0, // 91 HIRAGANA
    0, // 92 HENKAN
    0, // 93 KATAKANAHIRAGANA
    0, // 94 MUHENKAN
    AKEY.NUMPAD_COMMA, // 95 KP_JPCOMMA
    AKEY.NUMPAD_ENTER, // 96 KP_ENTER
    AKEY.CTRL_RIGHT, // 97 RIGHTCTRL
    AKEY.NUMPAD_DIVIDE, // 98 KP_SLASH
    AKEY.SYSRQ, // 99
    AKEY.ALT_RIGHT, // 100
    0, // 101 LINEFEED
    AKEY.MOVE_HOME, // 102 HOME
    AKEY.DPAD_UP, // 103 UP
    AKEY.PAGE_UP, // 104
    AKEY.DPAD_LEFT, // 105 LEFT
    AKEY.DPAD_RIGHT, // 106 RIGHT
    AKEY.MOVE_END, // 107 END
    AKEY.DPAD_DOWN, // 108 DOWN
    AKEY.PAGE_DOWN, // 109
    AKEY.INSERT, // 110
    AKEY.FORWARD_DEL, // 111 DELETE
    0, // 112 MACRO
    AKEY.MUTE, // 113
    AKEY.VOLUME_DOWN, // 114
    AKEY.VOLUME_UP, // 115
    AKEY.POWER, // 116
    AKEY.NUMPAD_EQUALS, // 117 KP_EQUALS
    0, // 118 KP_PLUSMINUS
    0, // 119 PAUSE
    0, // 120 SCALE
    AKEY.NUMPAD_COMMA, // 121 KP_COMMA
    0, // 122 HANGEUL/HANGUEL
    0, // 123 HANJA
    0, // 124 YEN
    AKEY.META_LEFT, // 125 LEFTMETA
    AKEY.META_RIGHT, // 126 RIGHTMETA
    0, // 127 COMPOSE
    0, // 128 STOP
    0, // 129 AGAIN
    0, // 130 PROPS
    0, // 131 UNDO
    0, // 132 FRONT
    0, // 133 COPY
    0, // 134 OPEN
    0, // 135 PASTE
    0, // 136 FIND
    0, // 137 CUT
    0, // 138 HELP
    AKEY.MENU, // 139
    0, // 140 CALC
    0, // 141 SETUP
    0, // 142 SLEEP
    0, // 143 WAKEUP
    0, // 144 FILE
    0, // 145 SENDFILE
    0, // 146 DELETEFILE
    0, // 147 XFER
    0, // 148 PROG1
    0, // 149 PROG2
    0, // 150 WWW
    0, // 151 MSDOS
    0, // 152 COFFEE/SCREENLOCK
    0, // 153 ROTATE_DISPLAY/DIRECTION
    0, // 154 CYCLEWINDOWS
    0, // 155 MAIL
    0, // 156 BOOKMARKS
    0, // 157 COMPUTER
    AKEY.BACK, // 158 BACK
    0, // 159 FORWARD
    0, // 160 CLOSECD
    0, // 161 EJECTCD
    0, // 162 EJECTCLOSECD
    AKEY.MEDIA_NEXT, // 163 NEXTSONG
    0, // 164 PLAYCD
    AKEY.MEDIA_PREVIOUS, // 165 PREVIOUSSONG
    0, // 166 STOPCD
    0, // 167 RECORD
    0, // 168 REWIND
    0, // 169 PHONE
    0, // 170 ISO
    0, // 171 CONFIG
    0, // 172 HOMEPAGE
    0, // 173 REFRESH
    0, // 174 EXIT
    0, // 175 MOVE
    0, // 176 EDIT
    0, // 177 SCROLLUP
    0, // 178 SCROLLDOWN
    AKEY.NUMPAD_0, // 179 KP_LEFTPAREN (compat)
    AKEY.NUMPAD_0, // 180 KP_RIGHTPAREN (compat)
    0, // 181 NEW
    0, // 182 REDO
    AKEY.FUNCTION, // 183 F13
    AKEY.FUNCTION, // 184 F14
    AKEY.FUNCTION, // 185 F15
    AKEY.FUNCTION, // 186 F16
    AKEY.FUNCTION, // 187 F17
    AKEY.FUNCTION, // 188 F18
    AKEY.FUNCTION, // 189 F19
    AKEY.FUNCTION, // 190 F20
    AKEY.FUNCTION, // 191 F21
    AKEY.FUNCTION, // 192 F22
    AKEY.FUNCTION, // 193 F23
    AKEY.FUNCTION, // 194 F24
    0, // 195
    0, // 196
    0, // 197
    0, // 198
    0, // 199
    AKEY.MEDIA_PLAY_PAUSE, // 200 PLAYCD (compat PLAY)
    AKEY.MEDIA_PAUSE, // 201 PAUSECD (no AKEY_MEDIA_PAUSE const: use 127)
    0, // 202 PROG3
    0, // 203 PROG4
    0, // 204 DASHBOARD
    0, // 205 SUSPEND
    0, // 206 CLOSE
    AKEY.MEDIA_PLAY_PAUSE, // 207 PLAY
    AKEY.MEDIA_FAST_FORWARD, // 208 FASTFORWARD
    0, // 209 BASSBOOST
    0, // 210 PRINT
    0, // 211 HP
    AKEY.CAMERA, // 212
    0, // 213 SOUND
    0, // 214 QUESTION
    0, // 215 EMAIL
    0, // 216 CHAT
    AKEY.SEARCH, // 217
    0, // 218 CONNECT
    0, // 219 FINANCE
    0, // 220 SPORT
    0, // 221 SHOP
    0, // 222 ALTERASE
    0, // 223 CANCEL
    0, // 224 BRIGHTNESSDOWN
    0, // 225 BRIGHTNESSUP
    AKEY.MEDIA_PLAY_PAUSE, // 226 MEDIA
    0, // 227 SWITCHVIDEOMODE
    0, // 228 KBDILLUMTOGGLE
    0, // 229 KBDILLUMDOWN
    0, // 230 KBDILLUMUP
    0, // 231 SEND
    0, // 232 REPLY
    0, // 233 FORWARDMAIL
    0, // 234 SAVE
    0, // 235 DOCUMENTS
    0, // 236 BATTERY
    0, // 237 BLUETOOTH
    0, // 238 WLAN
    0, // 239 UWB
    0, // 240 UNKNOWN
    0, // 241 VIDEO_NEXT
    0, // 242 VIDEO_PREV
    0, // 243 BRIGHTNESS_CYCLE
    0, // 244 BRIGHTNESS_AUTO/BRIGHTNESS_ZERO
    0, // 245 DISPLAY_OFF
    0, // 246 WLAN/WWAN
    0, // 247 RFKILL
    0, // 248 MICMUTE
    0, // 249
    0, // 250
    0, // 251
    0, // 252
    0, // 253
    0, // 254
    0, // 255
};

/// Map an evdev keycode to an Android keyCode. Unknown -> 0.
pub fn androidKeyCode(evdev: u32) i32 {
    if (evdev >= evdev_to_android.len) return 0;
    return evdev_to_android[evdev];
}

// ---------------------------------------------------------------------------
// Tests (fake backend).
// ---------------------------------------------------------------------------
