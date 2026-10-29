// SPDX-License-Identifier: GPL-3.0-or-later

//! Hand-rolled Wayland client: open an xdg_toplevel, draw XRGB8888 SHM
//! buffers, close cleanly. std only; no libwayland.
//!
//! Wire format (wayland.xml): each message is `u32 object id` + `u32
//! (length << 16 | opcode)` followed by the payload. `u32`/`int` are 4
//! bytes little-endian; `string` is `u32 len-incl-NUL` + bytes + NUL
//! padded to 4; `array` is `u32 byte-len` + bytes padded to 4; `fd`
//! travels out-of-band via SCM_RIGHTS. Object id 1 is wl_display.
//!
//! Opcodes used (0-based position in the XML, verified against
//! /usr/share/wayland/wayland.xml and xdg-shell.xml):
//! wl_display: sync=0 get_registry=1 | error=0 delete_id=1
//! wl_registry: bind=0 | global=0 global_remove=1
//! wl_compositor: create_surface=0 release=2
//! wl_shm: create_pool=0 release=1 | format=0
//! wl_shm_pool: create_buffer=0 destroy=1
//! wl_buffer: destroy=0 | release=0
//! wl_surface: destroy=0 attach=1 damage=2 frame=3 commit=6
//! wl_callback: done=0
//! xdg_wm_base: destroy=0 get_xdg_surface=2 pong=3 | ping=0
//! xdg_surface: destroy=0 get_toplevel=1 ack_configure=4 | configure=0
//! xdg_toplevel: destroy=0 set_title=2 set_app_id=3
//!   | configure=0 close=1 configure_bounds=2 wm_capabilities=3
//!
//! Bind is the one request whose new_id has no static interface, so it
//! encodes name(u) + interface(s) + version(u) + id(u).

const std = @import("std");
const linux = std.os.linux;
const posix = std.posix;
const xkb = @import("xkb");

pub const RunReason = enum { closed, timeout };

/// Cap on any single SHM dimension source: 32M pixels keeps pool math
/// (2 * w * 4 * h) far inside i64/usize range.
const max_pixels: u64 = 32 * 1024 * 1024;

/// Shm pixel format wl_shm.format.xrgb8888.
const fmt_xrgb8888: u32 = 1;

pub const Window = struct {
    gpa: std.mem.Allocator,
    sock: posix.fd_t = -1,
    memfd: posix.fd_t = -1,
    next_id: u32 = 2,

    registry: u32 = 0,
    compositor: u32 = 0,
    shm: u32 = 0,
    wm_base: u32 = 0,
    surface: u32 = 0,
    xsurface: u32 = 0,
    toplevel: u32 = 0,
    pool: u32 = 0,
    bufs: [2]u32 = .{ 0, 0 },

    map: ?[]align(std.heap.page_size_min) u8 = null,
    pool_w: u31 = 0,
    pool_h: u31 = 0,
    buf_busy: [2]bool = .{ false, false },

    init_w: u31 = 1,
    init_h: u31 = 1,
    cur_w: u31 = 1,
    cur_h: u31 = 1,

    /// Bumped on every toplevel.configure that changes cur_w/cur_h.
    /// Java polls it via nPollSize/nSize to detect resizes.
    size_gen: u64 = 0,
    /// Last generation consumed by takeResize.
    taken_gen: u64 = 0,
    /// Slot index of the last tryMap buffer, for commitSlot. Null when no
    /// mapped buffer is outstanding. STALE after any post/commit/remake:
    /// a second commit of the same slot is a no-op error.
    last_mapped: ?usize = null,
    /// Pool generation at the last tryMap; commit rejects the buffer when
    /// a resize remade the pool (unmapping Java's DirectByteBuffer) since.
    mapped_gen: u64 = 0,
    /// Bumped on every pool (re)make; tryMap stamps it into mapped_gen.
    pool_gen: u64 = 0,
    closed: bool = false,
    sync_id: u32 = 0,
    sync_done: bool = false,
    configured: bool = false,
    pend_w: i32 = 0,
    pend_h: i32 = 0,
    have_pend_size: bool = false,
    frames: [8]u32 = undefined,
    nframes: u8 = 0,
    comp_name: u32 = 0,
    shm_name: u32 = 0,
    xdg_name: u32 = 0,
    seat_name: u32 = 0,

    // --- M3 seat/input state (disjoint from the M2 surface/pool path) ---
    seat: u32 = 0,
    keyboard: u32 = 0,
    pointer: u32 = 0,
    seat_caps: u32 = 0,
    /// Advertised wl_seat version; we bind min(advertised, 8).
    seat_version: u32 = 0,
    /// Queued keymap fd + size from wl_keyboard.keymap (consumed on pump).
    pending_keymap_fd: posix.fd_t = -1,
    pending_keymap_size: u32 = 0,
    pending_keymap_format: u32 = 0,
    have_pending_keymap: bool = false,
    xkb_ctx: ?xkb.Context = null,
    xkb_map: ?xkb.Keymap = null,
    xkb_state: ?xkb.State = null,
    /// Last modifiers masks from wl_keyboard.modifiers (for metaState).
    mods_depressed: u32 = 0,
    mods_latched: u32 = 0,
    mods_locked: u32 = 0,
    mods_group: u32 = 0,
    /// Client-side repeat: active key (evdev), deadline, rate/delay.
    repeat_key: u32 = 0,
    repeat_active: bool = false,
    repeat_next_ms: u64 = 0,
    repeat_rate: i32 = 0,
    repeat_delay: i32 = 0,
    repeat_utf32: u32 = 0,
    repeat_keycode: i32 = 0,
    repeat_meta: i32 = 0,
    /// Pointer position from the last enter/motion (surface-local px).
    ptr_x: f32 = 0,
    ptr_y: f32 = 0,
    have_ptr: bool = false,
    ptr_buttons: u32 = 0,
    /// Accumulated axis detents within a pointer frame (v120/120 each).
    axis_v120: i32 = 0,
    axis_h120: i32 = 0,
    /// Queued input fds from SCM_RIGHTS (keymap fds); consumed on pump.
    /// FIFO: recv pushes, pump shifts.
    pending_fds: [8]posix.fd_t = .{ -1, -1, -1, -1, -1, -1, -1, -1 },
    npending_fds: usize = 0,
    /// Ring of 8-jint input events (kinds 1/2/5/6 per the M3 contract,
    /// plus M4 touch kinds 7/8).
    events: [256][8]i32 = undefined,
    ev_head: usize = 0,
    ev_len: usize = 0,
    ev_dropped: bool = false,
    /// Events dropped since the last full drain (see takeInput).
    ev_drop_count: usize = 0,

    // --- M4 touch/output state (touch agent owns; clipboard agent owns
    // --- data-device state elsewhere in this struct) ---
    touch: u32 = 0,
    /// 10 touch slots: dense lowest-free indices; wl touch id per slot.
    touch_used: [10]bool = .{ false, false, false, false, false, false, false, false, false, false },
    touch_wl: [10]i32 = .{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
    touch_x: [10]f32 = .{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
    touch_y: [10]f32 = .{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
    /// Staged motion within a touch frame; flushed as one MOVE.
    touch_pending: bool = false,
    touch_pending_time: u32 = 0,
    /// First bound wl_output (first global wins); more are ignored.
    output: u32 = 0,
    output_version: u32 = 0,
    out_name: u32 = 0,
    out_version: u32 = 0,
    out_scale: i32 = 1,
    out_mm_w: i32 = 0,
    out_mm_h: i32 = 0,
    /// Bumped on output done and on window-size change; Java polls it via
    /// nPollMetrics into int[8] (see takeMetrics).
    metrics_gen: u64 = 0,
    /// Last metrics generation consumed by takeMetrics.
    taken_metrics_gen: u64 = 0,
    // --- M4 clipboard/data-device state (clipboard agent owns; touch
    // --- agent owns the touch/output block above) ---
    /// Advertised wl_data_device_manager global; 0 when the compositor
    /// offers none (silent local-only clipboard).
    dm_name: u32 = 0,
    /// Advertised manager version; we bind min(advertised, 3).
    dm_version: u32 = 0,
    /// Bound manager + per-seat device (0 when unavailable).
    data_manager: u32 = 0,
    data_device: u32 = 0,
    /// Our live selection source (0 when we hold no selection).
    clip_source: u32 = 0,
    /// Text we last offered (owned slice; served on send-event fds).
    /// Null when we hold no selection.
    clip_text: ?[]u8 = null,
    /// Last input serial from keyboard/pointer enter/key/button events;
    /// set_selection carries it (0 when no input serial seen yet).
    last_serial: u32 = 0,
    /// Remote offer stash: object id + offered-mime flags. One offer at a
    /// time; a new data_offer destroys the previous object.
    clip_offer: u32 = 0,
    clip_offer_utf8: bool = false,
    clip_offer_plain: bool = false,
    clip_offer_legacy: bool = false,

    rx: std.ArrayListUnmanaged(u8) = .empty,

    /// Connect, bind globals, map a toplevel, wait for the first
    /// configure, build the SHM pool. `display_path=null` means
    /// `$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`.
    pub fn open(
        gpa: std.mem.Allocator,
        display_path: ?[]const u8,
        title: [:0]const u8,
        app_id: [:0]const u8,
        width: u31,
        height: u31,
    ) !*Window {
        if (width == 0 or height == 0) return error.BadSize;
        if (@as(u64, width) * @as(u64, height) > max_pixels) return error.BadSize;

        const self = try gpa.create(Window);
        errdefer gpa.destroy(self);
        self.* = .{
            .gpa = gpa,
            .init_w = width,
            .init_h = height,
            .cur_w = width,
            .cur_h = height,
        };
        errdefer self.cleanup();

        var pathbuf: [108]u8 = undefined;
        const path = try resolvePath(gpa, display_path, &pathbuf);
        self.sock = try connectSock(path);

        // Registry + roundtrip: globals arrive before the sync done event.
        self.registry = self.allocId();
        try self.sendWords(1, 1, &.{self.registry});
        try self.doSync(10_000);

        if (self.comp_name == 0) return error.MissingCompositor;
        if (self.shm_name == 0) return error.MissingShm;
        if (self.xdg_name == 0) return error.MissingXdgWmBase;

        self.compositor = self.allocId();
        self.shm = self.allocId();
        self.wm_base = self.allocId();
        try self.sendBind(self.registry, self.comp_name, "wl_compositor", 1, self.compositor);
        try self.sendBind(self.registry, self.shm_name, "wl_shm", 1, self.shm);
        try self.sendBind(self.registry, self.xdg_name, "xdg_wm_base", 1, self.wm_base);

        // M3 seat: bind min(advertised, 8) when a seat was advertised.
        // No seat (headless/Xvfb-style minimal compositors) is fine:
        // input just never arrives.
        if (self.seat_name != 0) {
            const seat_ver: u32 = @min(self.seat_version, 8);
            self.seat = self.allocId();
            try self.sendBind(self.registry, self.seat_name, "wl_seat", seat_ver, self.seat);
        }
        // M4 output: bind the first advertised wl_output at min(ver, 4)
        // for DisplayMetrics (density/scale). Additional outputs are
        // ignored; missing output just leaves scale=1 fallback metrics.
        if (self.out_name != 0) {
            const out_ver: u32 = @min(self.out_version, 4);
            self.output = self.allocId();
            self.output_version = out_ver;
            try self.sendBind(self.registry, self.out_name, "wl_output", out_ver, self.output);
        }
        // M4 clipboard: bind min(advertised, 3) + get_data_device after the
        // seat so set_selection has a device to act on. Missing
        // manager/seat = silent local-only clipboard (no device, no
        // selection, Java falls back to its process-local copy).
        if (self.dm_name != 0 and self.seat != 0) {
            const dm_ver: u32 = @min(self.dm_version, 3);
            self.data_manager = self.allocId();
            try self.sendBind(self.registry, self.dm_name, "wl_data_device_manager", dm_ver, self.data_manager);
            self.data_device = self.allocId();
            try self.sendWords(self.data_manager, 1, &.{ self.data_device, self.seat });
        }

        self.surface = self.allocId();
        try self.sendWords(self.compositor, 0, &.{self.surface});
        self.xsurface = self.allocId();
        try self.sendWords(self.wm_base, 2, &.{ self.xsurface, self.surface });
        self.toplevel = self.allocId();
        try self.sendWords(self.xsurface, 1, &.{self.toplevel});
        try self.sendStr(self.toplevel, 2, title);
        try self.sendStr(self.toplevel, 3, app_id);
        // Initial commit carries no buffer; the compositor answers with
        // toplevel.configure + xdg_surface.configure.
        try self.sendWords(self.surface, 6, &.{});
        try self.waitConfigure(10_000);

        // Publish generation 1 so the first nPollMetrics carries the live
        // size with scale=1 fallback metrics even when the compositor
        // offers no wl_output (v1 globals only) or sends no done batch.
        self.metrics_gen +|= 1;
        try self.makePool(self.cur_w, self.cur_h);
        return self;
    }

    pub fn setTitle(self: *Window, title: []const u8) !void {
        if (std.mem.indexOfScalar(u8, title, 0) != null) return error.InvalidTitle;
        try self.sendStr(self.toplevel, 2, title);
    }

    /// Present one XRGB8888 frame. Row stride in pixels; rows may be
    /// padded (stride >= w). Recreates the pool when the size changes.
    pub fn post(self: *Window, pixels: []const u32, w: u31, h: u31, stride: u31) !void {
        const bi = try self.freeBuffer(w, h, stride, pixels.len);
        {
            const map = self.map.?;
            const buf_len = @as(usize, w) * 4 * @as(usize, h);
            const base: [*]u32 = @ptrCast(@alignCast(&map[@as(usize, bi) * buf_len]));
            for (0..h) |r| {
                const src = pixels[@as(usize, r) * @as(usize, stride) ..][0..w];
                const dst = base[@as(usize, r) * @as(usize, w) ..][0..w];
                @memcpy(dst, src);
            }
        }
        try self.commitSlot(bi, w, h);
    }

    /// Reserve a free SHM slot for w×h, remaking the pool when the size
    /// changes. Returns the slot index, marked busy. The caller must copy
    /// into tryMap(bi) and commitSlot(bi), or the slot stays busy until
    /// the compositor releases it.
    pub fn freeBuffer(self: *Window, w: u31, h: u31, stride: u31, pixels_len: usize) !usize {
        if (w == 0 or h == 0 or stride < w) return error.BadSize;
        if (@as(u64, w) * @as(u64, h) > max_pixels) return error.BadSize;
        const need: usize = (@as(usize, h) - 1) * @as(usize, stride) + @as(usize, w);
        if (pixels_len < need) return error.BadSize;
        if (self.closed) return error.ConnectionClosed;
        if (self.map == null or self.pool_w != w or self.pool_h != h) {
            try self.remakePool(w, h);
        }
        // Wait for a free buffer; the compositor's release events arrive
        // through the same dispatch path.
        const start = nowMs();
        while (true) {
            for (0..2) |i| {
                if (!self.buf_busy[i]) {
                    self.buf_busy[i] = true;
                    self.last_mapped = null;
                    return i;
                }
            }
            if ((nowMs() -| start) >= 2000) return error.BufferBusy;
            try self.pump(200);
            if (self.closed) return error.ConnectionClosed;
        }
    }

    /// Byte view of one pool slot's SHM for direct writes. The slot must
    /// have come from freeBuffer and not be committed yet. The returned
    /// slice is w*h*4 bytes; writes are visible to the compositor only
    /// after commitSlot.
    pub fn tryMap(self: *Window, slot: usize, w: u31, h: u31) ?[]u8 {
        const map = self.map orelse return null;
        if (slot >= 2) return null;
        if (self.pool_w != w or self.pool_h != h) return null;
        const buf_len = @as(usize, w) * 4 * @as(usize, h);
        if ((slot + 1) * buf_len > map.len) return null;
        self.last_mapped = slot;
        self.mapped_gen = self.pool_gen;
        return map[slot * buf_len ..][0..buf_len];
    }

    /// Present slot `bi` (previously reserved by freeBuffer) at size w×h.
    /// Clears last_mapped so a double-commit is a no-op error.
    pub fn commitSlot(self: *Window, bi: usize, w: u31, h: u31) !void {
        if (bi >= 2 or self.bufs[bi] == 0) return error.BadSize;
        if (w == 0 or h == 0) return error.BadSize;
        if (self.closed) return error.ConnectionClosed;
        self.last_mapped = null;
        try self.sendWords(self.surface, 1, &.{ self.bufs[bi], 0, 0 });
        try self.sendWords(self.surface, 2, &.{
            0,
            0,
            @bitCast(@as(i32, @intCast(w))),
            @bitCast(@as(i32, @intCast(h))),
        });
        const fid = self.allocId();
        self.noteFrame(fid);
        try self.sendWords(self.surface, 3, &.{fid});
        try self.sendWords(self.surface, 6, &.{});
    }

    /// Commit the buffer previously handed out by tryMap. Returns false
    /// when there is no outstanding mapped buffer (stale/double commit)
    /// or a resize remade the pool since, unmapping Java's buffer.
    pub fn commit(self: *Window, w: u31, h: u31) !bool {
        const bi = self.last_mapped orelse return false;
        if (self.mapped_gen != self.pool_gen) {
            self.last_mapped = null;
            return false;
        }
        try self.commitSlot(bi, w, h);
        return true;
    }

    /// Poll-dispatch until the compositor closes the toplevel or the
    /// timeout elapses. Answers xdg_wm_base.ping on the way.
    pub fn run(self: *Window, timeout_ms: u64) RunReason {
        const start = nowMs();
        while (true) {
            if (self.closed) return .closed;
            const el = nowMs() -| start;
            if (el >= timeout_ms) return .timeout;
            var rem = timeout_ms - el;
            if (rem > 0x7FFF_FFFF) rem = 0x7FFF_FFFF;
            self.pump(rem) catch return .closed;
        }
    }

    /// Last configure size, or the init size before any configure.
    pub fn size(self: *Window) struct { w: u31, h: u31 } {
        return .{ .w = self.cur_w, .h = self.cur_h };
    }

    /// Current resize generation. Bumped by onEvent on size change.
    pub fn sizeGen(self: *const Window) u64 {
        return self.size_gen;
    }

    /// True once the compositor has asked for a close.
    pub fn isClosed(self: *const Window) bool {
        return self.closed;
    }

    /// Non-blocking resize check for the JNI poll path. Returns the new
    /// size once per generation; null when nothing new arrived. Pure
    /// accessor: performs no I/O, so Java decides when to pump.
    pub fn takeResize(self: *Window) ?struct { w: u31, h: u31 } {
        if (self.taken_gen == self.size_gen) return null;
        self.taken_gen = self.size_gen;
        return .{ .w = self.cur_w, .h = self.cur_h };
    }

    /// Generation-gated DisplayMetrics snapshot for the JNI nPollMetrics
    /// path. Returns the metrics once per generation into `out` (int[8]:
    /// [w,h,densityDpi,density*1000,scaled*1000,xdpi*1000,ydpi*1000,scale]);
    /// null when nothing new arrived. Pure accessor: performs no I/O.
    /// w/h come from the live window size; densityDpi = 160*scale;
    /// xdpi/ydpi derive from the output physical size with a fallback to
    /// densityDpi when the size is unknown.
    pub fn takeMetrics(self: *Window, out: *[8]i32) ?void {
        if (self.taken_metrics_gen == self.metrics_gen) return null;
        self.taken_metrics_gen = self.metrics_gen;
        const scale: i32 = @max(self.out_scale, 1);
        const dpi: i32 = 160 * scale;
        const w: i32 = self.cur_w;
        const h: i32 = self.cur_h;
        var xdpi: i32 = dpi * 1000;
        var ydpi: i32 = dpi * 1000;
        if (self.out_mm_w > 0 and w > 0) {
            xdpi = @intCast(@divTrunc(@as(i64, w) * 25400, @as(i64, self.out_mm_w)));
        }
        if (self.out_mm_h > 0 and h > 0) {
            ydpi = @intCast(@divTrunc(@as(i64, h) * 25400, @as(i64, self.out_mm_h)));
        }
        out.* = .{ w, h, dpi, scale * 1000, scale * 1000, xdpi, ydpi, scale };
        return {};
    }

    /// Best-effort destroy of every protocol object, then free.
    /// wl_compositor and wl_shm are version-1 singletons with no release
    /// request in this protocol version (release is v2+ / inferred), so
    /// sending release opcodes (compositor=2, shm=1) is a protocol error:
    /// only destroy created objects and drop the singletons silently.
    pub fn close(self: *Window) void {
        if (self.toplevel != 0) self.sendWords(self.toplevel, 0, &.{}) catch {};
        if (self.xsurface != 0) self.sendWords(self.xsurface, 0, &.{}) catch {};
        if (self.surface != 0) self.sendWords(self.surface, 0, &.{}) catch {};
        for (self.bufs) |b| {
            if (b != 0) self.sendWords(b, 0, &.{}) catch {};
        }
        if (self.pool != 0) self.sendWords(self.pool, 1, &.{}) catch {};
        if (self.wm_base != 0) self.sendWords(self.wm_base, 0, &.{}) catch {};
        // Seat objects: release is version-gated (keyboard/pointer/touch
        // need seat v3+, seat needs v5+; bound version is min(advertised,
        // 8)). Output release needs wl_output v3+. Only send release when
        // the bound version supports it.
        const seat_ver: u32 = @min(self.seat_version, 8);
        if (self.keyboard != 0 and seat_ver >= 3) self.sendWords(self.keyboard, 0, &.{}) catch {};
        if (self.pointer != 0 and seat_ver >= 3) self.sendWords(self.pointer, 1, &.{}) catch {};
        if (self.touch != 0 and seat_ver >= 3) self.sendWords(self.touch, 0, &.{}) catch {};
        if (self.seat != 0 and seat_ver >= 5) self.sendWords(self.seat, 4, &.{}) catch {};
        // Clipboard objects: source.destroy=0 / offer.destroy=2 always
        // exist; device.release=1 needs v2+; manager.release=2 exists only
        // at v4 (XML), unreachable at our min(advertised, 3) bind.
        if (self.clip_source != 0) self.sendWords(self.clip_source, 0, &.{}) catch {};
        if (self.clip_offer != 0) self.sendWords(self.clip_offer, 2, &.{}) catch {};
        if (self.data_device != 0 and @min(self.dm_version, 3) >= 2) self.sendWords(self.data_device, 1, &.{}) catch {};
        if (self.data_manager != 0 and @min(self.dm_version, 3) >= 4) self.sendWords(self.data_manager, 2, &.{}) catch {};
        const gpa = self.gpa;
        self.cleanup();
        gpa.destroy(self);
    }

    fn cleanup(self: *Window) void {
        self.resetXkb();
        if (self.clip_text) |t| {
            self.gpa.free(t);
            self.clip_text = null;
        }
        if (self.map) |m| {
            posix.munmap(m);
            self.map = null;
        }
        if (self.memfd != -1) {
            _ = linux.close(self.memfd);
            self.memfd = -1;
        }
        if (self.sock != -1) {
            _ = linux.close(self.sock);
            self.sock = -1;
        }
        self.rx.deinit(self.gpa);
    }

    fn allocId(self: *Window) u32 {
        const id = self.next_id;
        self.next_id += 1;
        return id;
    }

    fn noteFrame(self: *Window, id: u32) void {
        if (self.nframes == self.frames.len) {
            std.mem.copyForwards(u32, self.frames[0..7], self.frames[1..8]);
            self.nframes = 7;
        }
        self.frames[self.nframes] = id;
        self.nframes += 1;
    }

    fn noteGlobal(self: *Window, name: u32, iface: []const u8, version: u32) void {
        if (name == 0) return;
        if (std.mem.eql(u8, iface, "wl_compositor")) {
            if (self.comp_name == 0) self.comp_name = name;
        } else if (std.mem.eql(u8, iface, "wl_shm")) {
            if (self.shm_name == 0) self.shm_name = name;
        } else if (std.mem.eql(u8, iface, "xdg_wm_base")) {
            if (self.xdg_name == 0) self.xdg_name = name;
        } else if (std.mem.eql(u8, iface, "wl_seat")) {
            // First seat wins; keep the advertised version for the
            // min(advertised, 8) bind below.
            if (self.seat_name == 0) {
                self.seat_name = name;
                self.seat_version = version;
            }
        } else if (std.mem.eql(u8, iface, "wl_output")) {
            // First output wins; bound at min(advertised, 4) in open.
            if (self.out_name == 0) {
                self.out_name = name;
                self.out_version = version;
            }
        } else if (std.mem.eql(u8, iface, "wl_data_device_manager")) {
            // First manager wins; bound at min(advertised, 3) in open.
            if (self.dm_name == 0) {
                self.dm_name = name;
                self.dm_version = version;
            }
        }
    }

    fn doSync(self: *Window, timeout_ms: u64) !void {
        const id = self.allocId();
        self.sync_id = id;
        self.sync_done = false;
        errdefer {
            self.sync_id = 0;
        }
        try self.sendWords(1, 0, &.{id});
        const start = nowMs();
        while (!self.sync_done) {
            const el = nowMs() -| start;
            if (el >= timeout_ms) return error.SyncTimeout;
            try self.pump(timeout_ms - el);
        }
        self.sync_id = 0;
    }

    fn waitConfigure(self: *Window, timeout_ms: u64) !void {
        const start = nowMs();
        while (!self.configured) {
            const el = nowMs() -| start;
            if (el >= timeout_ms) return error.ConfigureTimeout;
            try self.pump(timeout_ms - el);
            if (self.closed) return error.ConnectionClosed;
        }
        // The toplevel.configure normally rides with the xdg_surface
        // configure; allow a short grace for a split delivery.
        const gstart = nowMs();
        while (!self.have_pend_size) {
            if ((nowMs() -| gstart) >= 500) break;
            try self.pump(500);
            if (self.closed) break;
        }
    }

    /// One poll + receive + dispatch cycle. Returns on poll timeout
    /// without progress; errors only on I/O or protocol violations.
    /// Public for the JNI nPollSize path.
    ///
    /// M3: the poll wait is clamped to the next key-repeat deadline so a
    /// held key repeats even when the compositor sends nothing. Repeat
    /// events are queued after any wire events from this pump.
    pub fn pump(self: *Window, timeout_ms: u64) !void {
        var wait_ms = timeout_ms;
        if (self.repeat_active) {
            const now = nowMs();
            const to_deadline = self.repeat_next_ms -| now;
            if (to_deadline < wait_ms) wait_ms = to_deadline;
        }
        var fds = [_]posix.pollfd{.{ .fd = self.sock, .events = posix.POLL.IN }};
        const ms: i32 = if (wait_ms > 0x7FFF_FFFF) 0x7FFF_FFFF else @intCast(wait_ms);
        const n = try posix.poll(&fds, ms);
        if (n != 0) try self.recv();
        try self.consumeKeymap();
        self.pumpRepeat();
    }
    // ------------------------------------------------------------------
    // M4 clipboard engine (clipboard agent owns): data-device copy/paste.
    // Opcodes (verified against /usr/share/wayland/wayland.xml):
    // wl_data_device_manager: create_data_source=0 get_data_device=1.
    // wl_data_device: set_selection=1 (source nullable, serial);
    //   events data_offer=0 selection=2 (offer id nullable).
    // wl_data_source: offer=0; events target=0 send=1 (mime + fd)
    //   cancelled=2.
    // wl_data_offer: offer=0 (mime string); receive=1 (mime + fd).
    // Selection mime preference: text/plain;charset=utf-8, then
    // text/plain, then UTF8_STRING. No memfd anywhere: the receiver
    // supplies the fd; copy serves send-event fds, paste reads the
    // offer-receive pipe to EOF.
    // ------------------------------------------------------------------

    /// Offered text mime types, in preference order.
    const clip_mime_utf8 = "text/plain;charset=utf-8";
    const clip_mime_plain = "text/plain";
    const clip_mime_legacy = "UTF8_STRING";

    /// True when `mime` is one of the three text types we serve/accept.
    fn isClipTextMime(mime: []const u8) bool {
        return std.mem.eql(u8, mime, clip_mime_utf8) or
            std.mem.eql(u8, mime, clip_mime_plain) or
            std.mem.eql(u8, mime, clip_mime_legacy);
    }

    /// Copy: publish `text` as the Wayland selection. Empty text clears
    /// the selection (null source). Returns false when no manager/seat
    /// device is bound (nothing published); the Java side keeps its own
    /// copy either way. A previous live source is destroyed first.
    /// Best-effort sends: a dead socket leaves the text staged for the
    /// send handler.
    pub fn clipboardCopy(self: *Window, text: []const u8) bool {
        if (self.data_manager == 0 or self.data_device == 0) return false;
        if (self.clip_source != 0) {
            self.sendWords(self.clip_source, 0, &.{}) catch {};
            self.clip_source = 0;
        }
        if (self.clip_text) |t| {
            self.gpa.free(t);
            self.clip_text = null;
        }
        if (text.len == 0) {
            self.sendWords(self.data_device, 1, &.{ 0, self.last_serial }) catch {};
            return true;
        }
        const dup = self.gpa.dupe(u8, text) catch return false;
        const src = self.allocId();
        self.sendWords(self.data_manager, 0, &.{src}) catch {
            self.gpa.free(dup);
            return false;
        };
        self.sendStr(src, 0, clip_mime_utf8) catch {};
        self.sendStr(src, 0, clip_mime_plain) catch {};
        self.sendStr(src, 0, clip_mime_legacy) catch {};
        self.sendWords(self.data_device, 1, &.{ src, self.last_serial }) catch {};
        self.clip_source = src;
        self.clip_text = dup;
        return true;
    }

    /// Best-effort clear of our selection (null source + drop staged text).
    /// Used by close(); clipboardCopy("") is the public clear path.
    fn clearSelection(self: *Window) void {
        if (self.data_device != 0) {
            self.sendWords(self.data_device, 1, &.{ 0, self.last_serial }) catch {};
        }
        if (self.clip_source != 0) {
            self.sendWords(self.clip_source, 0, &.{}) catch {};
            self.clip_source = 0;
        }
        if (self.clip_text) |t| {
            self.gpa.free(t);
            self.clip_text = null;
        }
    }

    /// wl_data_source.send: serve one receiver fd with the staged text.
    /// Non-text mimes get an empty (immediately-closed) transfer. The fd
    /// always closes here. EINTR retries on write; other errors truncate.
    fn onSourceSend(self: *Window, mime: []const u8, wfd: ?posix.fd_t) void {
        const fd = wfd orelse return;
        defer _ = linux.close(fd);
        if (!isClipTextMime(mime)) return;
        const text = self.clip_text orelse return;
        // Strip a NUL terminator if the caller staged one (defensive:
        // clipboardCopy stores raw bytes without NUL).
        var body = text;
        if (body.len > 0 and body[body.len - 1] == 0) body = body[0 .. body.len - 1];
        var off: usize = 0;
        while (off < body.len) {
            const rc = linux.write(fd, body.ptr + off, body.len - off);
            switch (linux.errno(rc)) {
                .SUCCESS => {
                    if (rc == 0) return;
                    off += @intCast(rc);
                },
                .INTR => continue,
                else => return,
            }
        }
    }

    /// wl_data_source.cancelled: our selection was replaced; drop the
    /// source object and the staged text.
    fn onSourceCancelled(self: *Window) void {
        if (self.clip_source != 0) {
            self.sendWords(self.clip_source, 0, &.{}) catch {};
            self.clip_source = 0;
        }
        if (self.clip_text) |t| {
            self.gpa.free(t);
            self.clip_text = null;
        }
    }

    /// wl_data_device.data_offer: stash the new offer id, destroying any
    /// previous offer object first (one live offer at a time).
    fn onDataOffer(self: *Window, offer_id: u32) void {
        if (self.clip_offer != 0) {
            self.sendWords(self.clip_offer, 2, &.{}) catch {};
        }
        self.clip_offer = offer_id;
        self.clip_offer_utf8 = false;
        self.clip_offer_plain = false;
        self.clip_offer_legacy = false;
    }

    /// wl_data_offer.offer: record which text mimes the remote side offers.
    fn onOfferMime(self: *Window, mime: []const u8) void {
        if (std.mem.eql(u8, mime, clip_mime_utf8)) {
            self.clip_offer_utf8 = true;
        } else if (std.mem.eql(u8, mime, clip_mime_plain)) {
            self.clip_offer_plain = true;
        } else if (std.mem.eql(u8, mime, clip_mime_legacy)) {
            self.clip_offer_legacy = true;
        }
    }

    /// wl_data_device.selection: adopt the offer (or null = empty) and
    /// push a kind-7 hint: [7,1,...] text offered, [7,2,...] non-text
    /// offer, [7,0,...] empty. A superseded offer object is destroyed.
    /// Null id with a live stash destroys the stash (defensive: the
    /// compositor normally destroys it, but double-destroy is harmless
    /// since ids are never reused on one connection).
    fn onSelection(self: *Window, offer_id: u32) void {
        if (offer_id == 0) {
            if (self.clip_offer != 0) {
                self.sendWords(self.clip_offer, 2, &.{}) catch {};
                self.clip_offer = 0;
            }
            self.clip_offer_utf8 = false;
            self.clip_offer_plain = false;
            self.clip_offer_legacy = false;
            self.pushEvent(.{ 7, 0, 0, 0, 0, 0, 0, 0 });
            return;
        }
        self.clip_offer = offer_id;
        if (self.clip_offer_utf8 or self.clip_offer_plain or self.clip_offer_legacy) {
            self.pushEvent(.{ 7, 1, 0, 0, 0, 0, 0, 0 });
        } else {
            self.pushEvent(.{ 7, 2, 0, 0, 0, 0, 0, 0 });
        }
    }

    /// Best mime the current offer carries, or null when no text is
    /// offered. Preference: utf8 > plain > legacy.
    fn offerMime(self: *const Window) ?[]const u8 {
        if (self.clip_offer == 0) return null;
        if (self.clip_offer_utf8) return clip_mime_utf8;
        if (self.clip_offer_plain) return clip_mime_plain;
        if (self.clip_offer_legacy) return clip_mime_legacy;
        return null;
    }

    /// True when the current remote offer carries text.
    pub fn clipboardHasText(self: *const Window) bool {
        return self.offerMime() != null;
    }

    /// Paste: receive the offered text into a fresh heap slice. Creates a
    /// pipe, sends wl_data_offer.receive(mime, write-end), then reads the
    /// write-end to EOF after the compositor serves it. Returns null when
    /// no text is offered or any step fails. Caller owns the slice.
    /// Pumps the connection so the receive request is flushed and the
    /// sender's bytes arrive; bounded at ~2s.
    pub fn clipboardPaste(self: *Window) ?[]u8 {
        const mime = self.offerMime() orelse return null;
        if (self.sock == -1) return null;
        var fds: [2]posix.fd_t = .{ -1, -1 };
        const rc = linux.pipe(&fds);
        if (linux.errno(rc) != .SUCCESS) return null;
        self.sendReceive(self.clip_offer, mime, fds[1]) catch {
            _ = linux.close(fds[0]);
            _ = linux.close(fds[1]);
            return null;
        };
        _ = linux.close(fds[1]);
        // Non-blocking reads: the sender serves the fd asynchronously, so
        // a blocking read could wait past the deadline with no way out.
        // O_NONBLOCK via fcntl; errors fall back to blocking (the pump
        // loop still bounds total time, and EOF ends it early).
        const fl = linux.fcntl(fds[0], linux.F.GETFL, 0);
        if (linux.errno(fl) == .SUCCESS) {
            const cur: usize = fl;
            const nonblock: usize = 0o4000; // O_NONBLOCK on all Linux arches
            _ = linux.fcntl(fds[0], linux.F.SETFL, cur | nonblock);
        }
        defer _ = linux.close(fds[0]);
        // Flush the request, then read until EOF or timeout (~2s).
        const start = nowMs();
        var out: std.ArrayListUnmanaged(u8) = .empty;
        defer out.deinit(self.gpa);
        var done = false;
        while (!done) {
            const el = nowMs() -| start;
            if (el >= 2000) break;
            self.pump(100) catch {};
            while (true) {
                var tmp: [4096]u8 = undefined;
                const n = linux.read(fds[0], &tmp, tmp.len);
                switch (linux.errno(n)) {
                    .SUCCESS => {
                        if (n == 0) {
                            done = true;
                            break;
                        }
                        out.appendSlice(self.gpa, tmp[0..@intCast(n)]) catch return null;
                        if (out.items.len > 8 * 1024 * 1024) return null;
                    },
                    .INTR => continue,
                    .AGAIN => break,
                    else => {
                        // Sender hung up without EOF or another read error:
                        // keep what arrived (if anything), stop reading.
                        done = out.items.len > 0;
                        if (!done) return null;
                        break;
                    },
                }
            }
        }
        if (!done and out.items.len == 0) return null;
        // Strip one trailing NUL the sender may have included.
        var items = out.items;
        if (items.len > 0 and items[items.len - 1] == 0) items = items[0 .. items.len - 1];
        return self.gpa.dupe(u8, items) catch null;
    }

    /// wl_data_offer.receive(offer, mime, fd): request text bytes on `fd`.
    /// The fd travels out-of-band via SCM_RIGHTS with this message.
    fn sendReceive(self: *Window, offer: u32, mime: []const u8, fd: posix.fd_t) !void {
        if (stringWireLen(mime) > 240) return error.MessageTooLong;
        var buf: [256]u8 = undefined;
        var pos: usize = 8;
        try putStr(buf[0..], &pos, mime);
        try self.finishSendFd(&buf, pos, offer, 1, fd);
    }

    // ------------------------------------------------------------------
    // M3 input engine: ring buffer, fd FIFO, keymap, seat/keyboard/
    // pointer handlers, client-side repeat. Depends on xkb.zig only via
    // the `xkb` import below (window.zig itself stays std-only: the
    // extern/linkLibC surface lives in xkb.zig).
    // ------------------------------------------------------------------

    /// Push one 8-jint event. Cap 256; on overflow the oldest mergeable
    /// motion event (kind 2) is dropped first — only the newest position
    /// matters — else the oldest event. `ev_dropped` latches with a count
    /// until takeInput fully drains, so a stuck key is never hidden.
    pub fn pushEvent(self: *Window, ev: [8]i32) void {
        if (self.ev_len == self.events.len) {
            var drop: ?usize = null;
            var i: usize = 0;
            while (i < self.ev_len) : (i += 1) {
                if (self.events[(self.ev_head + i) % self.events.len][0] == 2) {
                    drop = i;
                    break;
                }
            }
            if (drop) |d| {
                var j = d;
                while (j + 1 < self.ev_len) : (j += 1) {
                    self.events[(self.ev_head + j) % self.events.len] =
                        self.events[(self.ev_head + j + 1) % self.events.len];
                }
                self.ev_len -= 1;
            } else {
                self.ev_head = (self.ev_head + 1) % self.events.len;
                self.ev_len -= 1;
            }
            self.ev_dropped = true;
            self.ev_drop_count += 1;
        }
        const idx = (self.ev_head + self.ev_len) % self.events.len;
        self.events[idx] = ev;
        self.ev_len += 1;
    }

    /// Number of queued input events.
    pub fn pendingInputs(self: *const Window) usize {
        return self.ev_len;
    }

    /// True when at least one event was dropped since the last full drain.
    pub fn inputDropped(self: *const Window) bool {
        return self.ev_dropped;
    }

    /// Events dropped since the last full drain.
    pub fn inputDropCount(self: *const Window) usize {
        return self.ev_drop_count;
    }

    /// Drain up to `out.len / 8` events into `out` (must be a multiple of
    /// 8 ints). Returns the event count; clears the dropped flag and
    /// count only once fully drained, so a partial drain keeps reporting
    /// the loss. Pure accessor: performs no I/O.
    pub fn takeInput(self: *Window, out: []i32) usize {
        std.debug.assert(out.len % 8 == 0);
        const cap = out.len / 8;
        var n: usize = 0;
        while (n < cap and self.ev_len > 0) : (n += 1) {
            const ev = self.events[self.ev_head];
            @memcpy(out[n * 8 ..][0..8], &ev);
            self.ev_head = (self.ev_head + 1) % self.events.len;
            self.ev_len -= 1;
        }
        if (self.ev_len == 0) {
            self.ev_dropped = false;
            self.ev_drop_count = 0;
        }
        return n;
    }

    /// Queue an SCM_RIGHTS fd for later keymap consumption. FIFO of 8;
    /// overflow closes the fd immediately so nothing leaks.
    fn pushFd(self: *Window, fd: posix.fd_t) void {
        if (self.npending_fds == self.pending_fds.len) {
            _ = linux.close(fd);
            return;
        }
        self.pending_fds[self.npending_fds] = fd;
        self.npending_fds += 1;
    }

    /// Shift one fd off the FIFO; null when empty.
    fn shiftFd(self: *Window) ?posix.fd_t {
        if (self.npending_fds == 0) return null;
        const fd = self.pending_fds[0];
        std.mem.copyForwards(posix.fd_t, self.pending_fds[0..7], self.pending_fds[1..8]);
        self.pending_fds[7] = -1;
        self.npending_fds -= 1;
        return fd;
    }

    /// Close every queued fd without consuming.
    fn drainFds(self: *Window) void {
        while (self.shiftFd()) |fd| _ = linux.close(fd);
        if (self.have_pending_keymap) {
            self.have_pending_keymap = false;
            self.pending_keymap_fd = -1;
            self.pending_keymap_size = 0;
        }
    }

    /// wl_seat.capabilities: create keyboard/pointer/touch objects for
    /// newly present capabilities. Never destroys on loss (compositor may
    /// send no further events; objects go away at close). Touch needs
    /// wl_seat.get_touch (opcode 2); release needs seat v5+.
    fn onSeatCaps(self: *Window, caps: u32) void {
        const have_kb = (caps & 2) != 0;
        const have_ptr = (caps & 1) != 0;
        const have_touch = (caps & 4) != 0;
        // Assign the id before the best-effort send: on a dead socket
        // (or in tests with no socket) the objects still exist locally.
        if (have_kb and self.keyboard == 0) {
            const id = self.allocId();
            self.keyboard = id;
            self.sendWords(self.seat, 1, &.{id}) catch {};
        }
        if (have_ptr and self.pointer == 0) {
            const id = self.allocId();
            self.pointer = id;
            self.sendWords(self.seat, 0, &.{id}) catch {};
        }
        if (have_touch and self.touch == 0) {
            const id = self.allocId();
            self.touch = id;
            self.sendWords(self.seat, 2, &.{id}) catch {};
        }
        // A seat that loses its last device stops routing events to the
        // objects bound while devices were present; the re-added devices
        // only reach freshly bound objects. Release on loss (guarded by
        // seat version like close()) so re-gain re-creates and re-links.
        const seat_ver: u32 = @min(self.seat_version, 8);
        if (!have_kb and self.keyboard != 0) {
            if (seat_ver >= 3) self.sendWords(self.keyboard, 0, &.{}) catch {};
            self.keyboard = 0;
        }
        if (!have_ptr and self.pointer != 0) {
            if (seat_ver >= 3) self.sendWords(self.pointer, 1, &.{}) catch {};
            self.pointer = 0;
        }
        if (!have_touch and self.touch != 0) {
            if (seat_ver >= 3) self.sendWords(self.touch, 0, &.{}) catch {};
            self.touch = 0;
        }
        self.seat_caps = caps;
    }

    /// wl_keyboard.keymap: stash fd+size+format; the actual mmap/compile
    /// happens in consumeKeymap (called at the end of pump, outside
    /// dispatch, since dispatch may run without socket I/O in tests).
    fn onKeymap(self: *Window, format: u32, km_size: u32) !void {
        const fd = self.shiftFd() orelse return;
        // A newer keymap supersedes a stashed-but-unconsumed one.
        if (self.have_pending_keymap) _ = linux.close(self.pending_keymap_fd);
        self.pending_keymap_fd = fd;
        self.pending_keymap_size = km_size;
        self.pending_keymap_format = format;
        self.have_pending_keymap = true;
    }

    /// Compile a stashed keymap (if any): validate format==1, mmap
    /// MAP_PRIVATE, compile via xkb, munmap+close.
    fn consumeKeymap(self: *Window) !void {
        if (!self.have_pending_keymap) return;
        self.have_pending_keymap = false;
        const fd = self.pending_keymap_fd;
        const km_size = self.pending_keymap_size;
        const format = self.pending_keymap_format;
        self.pending_keymap_fd = -1;
        self.pending_keymap_size = 0;
        self.pending_keymap_format = 0;
        defer _ = linux.close(fd);
        if (format != 1) return;
        if (km_size == 0 or km_size > 16 * 1024 * 1024) return;
        const mem = posix.mmap(
            null,
            km_size,
            .{ .READ = true },
            .{ .TYPE = .PRIVATE },
            fd,
            0,
        ) catch return;
        defer posix.munmap(mem);
        // The string compile path needs NUL termination: copy to a
        // NUL-terminated heap buffer (keymaps are small).
        const buf = self.gpa.allocSentinel(u8, km_size, 0) catch return;
        defer self.gpa.free(buf);
        @memcpy(buf[0..km_size], mem[0..km_size]);
        self.installKeymap(buf);
    }

    /// Install a NUL-terminated XKB keymap string: compile, build state,
    /// drop the old map/state, disarm repeat.
    fn installKeymap(self: *Window, text: [:0]const u8) void {
        const ctx = self.ensureXkb() orelse return;
        const new_map = xkb.keymapNewFromString(ctx, text) orelse return;
        const new_state = xkb.stateNew(new_map) orelse {
            xkb.keymapUnref(new_map);
            return;
        };
        // Drop the old map/state but keep the context.
        if (self.xkb_state) |s| {
            xkb.stateUnref(s);
            self.xkb_state = null;
        }
        if (self.xkb_map) |m| {
            xkb.keymapUnref(m);
            self.xkb_map = null;
        }
        self.xkb_map = new_map;
        self.xkb_state = new_state;
        // Modifier events can precede a usable keymap. Apply the retained
        // masks to every replacement state before decoding another key.
        xkb.updateMask(new_state, self.mods_depressed, self.mods_latched, self.mods_locked, self.mods_group);
        self.disarmRepeat();
    }

    /// Lazily create the xkb context. Returns null on failure.
    fn ensureXkb(self: *Window) ?xkb.Context {
        if (self.xkb_ctx) |ctx| return ctx;
        const ctx = xkb.contextNew() orelse return null;
        self.xkb_ctx = ctx;
        return ctx;
    }

    /// Drop the current keymap/state (munmap/close already handled by the
    /// caller); disarm repeat. Keeps the context for the next keymap.
    fn resetXkb(self: *Window) void {
        if (self.xkb_state) |s| {
            xkb.stateUnref(s);
            self.xkb_state = null;
        }
        if (self.xkb_map) |m| {
            xkb.keymapUnref(m);
            self.xkb_map = null;
        }
        if (self.xkb_ctx) |c| {
            xkb.contextUnref(c);
            self.xkb_ctx = null;
        }
        self.disarmRepeat();
    }

    /// wl_keyboard.leave: keyboard focus lost; disarm repeat.
    fn onKeyboardLeave(self: *Window) void {
        self.disarmRepeat();
        self.pushEvent(.{ 5, 0, 0, 0, 0, 0, 0, 0 });
    }

    /// wl_keyboard.key: press/release/repeat handling.
    fn onKey(self: *Window, serial: u32, time: u32, key: u32, state: u32) !void {
        _ = serial;
        const action: i32 = switch (state) {
            0 => 1, // released -> UP
            1 => 0, // pressed -> DOWN
            2 => 0, // repeated (compositor-side) -> DOWN
            else => return,
        };
        const keycode = xkb.androidKeyCode(key);
        const xk: u32 = key + 8;
        var utf32: u32 = 0;
        if (self.xkb_state) |s| {
            utf32 = xkb.keyGetUtf32(s, xk);
        } else {
            utf32 = xkb.keyGetUtf32(null, xk);
        }
        const meta = self.metaState(key, state);
        const t = splitTime(time);
        self.pushEvent(.{ 1, action, keycode, @intCast(utf32), meta, t.lo, t.hi, 0 });
        // Client-side repeat bookkeeping (only for fresh presses).
        if (state == 1) {
            self.armRepeat(key, keycode, utf32, meta);
        } else if (state == 0) {
            if (self.repeat_active and self.repeat_key == key) self.disarmRepeat();
        }
    }

    /// wl_keyboard.modifiers: update the xkb state mask; new keymap
    /// semantics handled through update_mask.
    fn onModifiers(self: *Window, serial: u32, dep: u32, lat: u32, lock: u32, group: u32) void {
        _ = serial;
        self.mods_depressed = dep;
        self.mods_latched = lat;
        self.mods_locked = lock;
        self.mods_group = group;
        // Always update the mask (the fake backend tracks the global
        // fake_state even when xkb_state is null / no keymap yet).
        xkb.updateMask(self.xkb_state, dep, lat, lock, group);
    }

    /// wl_keyboard.repeat_info: store rate/delay for repeat arming.
    /// rate==0 disables repeating.
    fn onRepeatInfo(self: *Window, rate: i32, delay: i32) void {
        self.repeat_rate = rate;
        self.repeat_delay = delay;
        if (rate == 0) self.disarmRepeat();
    }

    /// Android metaState bits from the xkb effective modifiers + the
    /// left/right depressed bits for the key being handled.
    fn metaState(self: *Window, evdev_key: u32, key_state: u32) i32 {
        _ = key_state;
        var meta: i32 = 0;
        const st: ?xkb.State = self.xkb_state;
        // SHIFT/CTRL/ALT/LOGO (+left/right from depressed).
        if (xkb.modActive(st, .shift, 0, self.mods_depressed)) meta |= 0x1;
        if (xkb.modActive(st, .ctrl, 0, self.mods_depressed)) meta |= 0x1000;
        if (xkb.modActive(st, .alt, 0, self.mods_depressed)) meta |= 0x2;
        if (xkb.modActive(st, .logo, 0, self.mods_depressed)) meta |= 0x10000;
        // Left/right refinement from the depressed mask + the key itself
        // when it is a modifier press.
        const dep = self.mods_depressed;
        if (evdev_key == 42 or (dep & 0x1) != 0) {
            if (xkb.modActive(st, .shift, 1, dep) or evdev_key == 42) meta |= 0x40;
        }
        if (evdev_key == 54 or (dep & 0x10) != 0) {
            if (xkb.modActive(st, .shift, 2, dep) or evdev_key == 54) meta |= 0x80;
        }
        if (evdev_key == 29) meta |= 0x2000;
        if (evdev_key == 97) meta |= 0x4000;
        if (evdev_key == 56) meta |= 0x10;
        if (evdev_key == 100) meta |= 0x20;
        if (evdev_key == 125) meta |= 0x20000;
        if (evdev_key == 126) meta |= 0x40000;
        return meta;
    }

    /// Arm client-side repeat for a pressed key when the keymap says the
    /// key repeats and a positive rate is set.
    fn armRepeat(self: *Window, evdev_key: u32, keycode: i32, utf32: u32, meta: i32) void {
        self.disarmRepeat();
        if (self.repeat_rate <= 0) return;
        if (!xkb.keyRepeats(self.xkb_map, evdev_key + 8)) return;
        const delay_ms: u64 = @intCast(@max(self.repeat_delay, 0));
        self.repeat_key = evdev_key;
        self.repeat_keycode = keycode;
        self.repeat_utf32 = utf32;
        self.repeat_meta = meta;
        self.repeat_active = true;
        self.repeat_next_ms = nowMs() + delay_ms;
    }

    fn disarmRepeat(self: *Window) void {
        self.repeat_active = false;
        self.repeat_key = 0;
    }

    /// Emit repeat DOWN events for each elapsed deadline; reschedule by
    /// 1000/rate ms. getRepeatCount stays 0 per the framework contract
    /// (flags carry 0; each repeat is a fresh DOWN).
    fn pumpRepeat(self: *Window) void {
        if (!self.repeat_active) return;
        if (self.repeat_rate <= 0) {
            self.disarmRepeat();
            return;
        }
        const now = nowMs();
        const period: u64 = @max(1, @divFloor(1000, @as(u64, @intCast(self.repeat_rate))));
        var guard: u8 = 0;
        while (self.repeat_active and (now >= self.repeat_next_ms) and guard < 8) : (guard += 1) {
            const t = splitTime(@truncate(now & 0xFFFF_FFFF));
            self.pushEvent(.{ 1, 0, self.repeat_keycode, @intCast(self.repeat_utf32), self.repeat_meta, t.lo, t.hi, 0 });
            self.repeat_next_ms += period;
        }
    }

    /// wl_pointer.enter: pointer focus gained; record position.
    fn onPointerEnter(self: *Window, serial: u32, surface: u32, x: i32, y: i32) !void {
        _ = serial;
        _ = surface;
        const fx = fixedToFloat(x);
        const fy = fixedToFloat(y);
        self.ptr_x = fx;
        self.ptr_y = fy;
        self.have_ptr = true;
        self.pushEvent(.{ 6, 1, @bitCast(fx), @bitCast(fy), 0, 0, 0, 0 });
    }

    /// wl_pointer.leave: pointer focus lost; cancel gesture, disarm repeat.
    fn onPointerLeave(self: *Window) void {
        const fx = self.ptr_x;
        const fy = self.ptr_y;
        self.have_ptr = false;
        self.disarmRepeat();
        // Fold into MOTION CANCEL at the last position per contract.
        self.pushEvent(.{ 2, 3, @bitCast(fx), @bitCast(fy), @intCast(self.ptr_buttons), 0, 0, 0 });
        self.pushEvent(.{ 6, 0, @bitCast(fx), @bitCast(fy), 0, 0, 0, 0 });
    }

    /// wl_pointer.motion: position update; folded into MOTION MOVE with
    /// buttonState 0 when no buttons are held (contract: motion without
    /// buttons => ACTION_MOVE with buttonState 0).
    fn onPointerMotion(self: *Window, time: u32, x: i32, y: i32) void {
        const fx = fixedToFloat(x);
        const fy = fixedToFloat(y);
        self.ptr_x = fx;
        self.ptr_y = fy;
        self.have_ptr = true;
        const t = splitTime(time);
        self.pushEvent(.{ 2, 2, @bitCast(fx), @bitCast(fy), @intCast(self.ptr_buttons), t.lo, t.hi, 0 });
    }

    /// wl_pointer.button: press/release at the last motion/enter position.
    /// DOWN carries the mask AFTER press, UP the mask AFTER release.
    fn onPointerButton(self: *Window, serial: u32, time: u32, button: u32, state: u32) void {
        _ = serial;
        const bit: u32 = switch (button) {
            0x110 => 1, // BTN_LEFT -> primary
            0x111 => 2, // BTN_RIGHT -> secondary
            0x112 => 4, // BTN_MIDDLE -> tertiary
            else => 0,
        };
        if (state == 1) {
            self.ptr_buttons |= bit;
        } else {
            self.ptr_buttons &= ~bit;
        }
        const action: i32 = if (state == 1) 0 else 1;
        const t = splitTime(time);
        self.pushEvent(.{ 2, action, @bitCast(self.ptr_x), @bitCast(self.ptr_y), @intCast(self.ptr_buttons), t.lo, t.hi, 0 });
    }

    /// wl_pointer.axis (continuous scroll values): accumulate; the frame
    /// event folds detents into a plain MOVE.
    fn onPointerAxis(self: *Window, time: u32, axis: u32, value: i32) void {
        _ = self;
        _ = time;
        _ = axis;
        _ = value;
        // Continuous values are informational in M3; detents come from
        // axis_value120. Intentionally ignored.
    }

    /// wl_pointer.axis_value120 (since 8): value/120 detents per axis.
    fn onAxisValue120(self: *Window, axis: u32, value120: i32) void {
        const detents = @divTrunc(value120, 120);
        if (axis == 0) {
            self.axis_v120 += detents;
        } else if (axis == 1) {
            self.axis_h120 += detents;
        }
    }

    /// wl_pointer.frame: fold accumulated scroll detents (one plain MOVE
    /// per detent at the last position with the live mask) and clear.
    fn onPointerFrame(self: *Window) void {
        const v = self.axis_v120;
        const h = self.axis_h120;
        self.axis_v120 = 0;
        self.axis_h120 = 0;
        const total: i64 = @as(i64, @intCast(@abs(v))) + @as(i64, @intCast(@abs(h)));
        var i: i64 = 0;
        while (i < total and i < 64) : (i += 1) {
            const now = nowMs();
            const t = splitTime(@truncate(now & 0xFFFF_FFFF));
            self.pushEvent(.{ 2, 2, @bitCast(self.ptr_x), @bitCast(self.ptr_y), @intCast(self.ptr_buttons), t.lo, t.hi, 0 });
        }
    }

    // ------------------------------------------------------------------
    // M4 touch engine (touch agent owns). 10 dense slots, lowest-free
    // indices; ring kinds 7 (header [7,raw,count,tlo,thi,0,0,0]) + count
    // x kind 8 ([8,id,x,y,0,0,0,0]). wl_touch opcodes: down=0 up=1
    // motion=2 frame=3 cancel=4 shape=5 orientation=6.
    // ------------------------------------------------------------------

    /// Split (lo, hi) timestamp halves shared by splitTime and the
    /// touch header emitter (one named type so call sites unify).
    const TimePair = struct { lo: i32, hi: i32 };

    /// Number of currently active touch slots.
    fn touchActive(self: *const Window) usize {
        var n: usize = 0;
        for (self.touch_used) |u| {
            if (u) n += 1;
        }
        return n;
    }

    /// Slot index for a wl touch id, or null when unknown.
    fn touchSlotOf(self: *const Window, wl_id: i32) ?usize {
        for (0..10) |i| {
            if (self.touch_used[i] and self.touch_wl[i] == wl_id) return i;
        }
        return null;
    }

    /// Lowest free slot, or null when all 10 are in use.
    fn touchFree(self: *const Window) ?usize {
        for (0..10) |i| {
            if (!self.touch_used[i]) return i;
        }
        return null;
    }

    /// Emit the pending staged motion as one MOVE before down/up/cancel.
    fn flushTouchPending(self: *Window) void {
        if (!self.touch_pending) return;
        self.touch_pending = false;
        const t = splitTime(self.touch_pending_time);
        self.pushTouchHeader(2, t);
    }

    /// Push one kind-7 header + one kind-8 row per active slot, in slot
    /// order. `raw` is the MotionEvent-style action (DOWN/POINTER_DOWN/
    /// MOVE/POINTER_UP/UP/CANCEL). Never pushes an empty header: callers
    /// guarantee count >= 1 (clipboard kind-7 uses count == 0 instead).
    fn pushTouchHeader(self: *Window, raw: i32, t: TimePair) void {
        var count: i32 = 0;
        for (self.touch_used) |u| {
            if (u) count += 1;
        }
        if (count == 0) return;
        self.pushEvent(.{ 7, raw, count, t.lo, t.hi, 0, 0, 0 });
        for (0..10) |i| {
            if (!self.touch_used[i]) continue;
            self.pushEvent(.{ 8, @intCast(i), @bitCast(self.touch_x[i]), @bitCast(self.touch_y[i]), 0, 0, 0, 0 });
        }
    }

    /// wl_touch.down: allocate the lowest-free slot; first-down emits
    /// DOWN, later ones POINTER_DOWN | (index << 8). Unknown extra
    /// downs past 10 slots are ignored.
    fn onTouchDown(self: *Window, time: u32, wl_id: i32, x: i32, y: i32) void {
        self.flushTouchPending();
        const slot = self.touchFree() orelse return;
        self.touch_used[slot] = true;
        self.touch_wl[slot] = wl_id;
        self.touch_x[slot] = fixedToFloat(x);
        self.touch_y[slot] = fixedToFloat(y);
        const first = self.touchActive() == 1;
        const raw: i32 = if (first) 0 else 5 | (@as(i32, @intCast(slot)) << 8);
        const t = splitTime(time);
        self.pushTouchHeader(raw, t);
    }

    /// wl_touch.motion: stage the position; the frame emits one MOVE.
    /// Unknown ids are ignored.
    fn onTouchMotion(self: *Window, time: u32, wl_id: i32, x: i32, y: i32) void {
        const slot = self.touchSlotOf(wl_id) orelse return;
        self.touch_x[slot] = fixedToFloat(x);
        self.touch_y[slot] = fixedToFloat(y);
        self.touch_pending = true;
        self.touch_pending_time = time;
    }

    /// wl_touch.frame: emit one MOVE for staged motion.
    fn onTouchFrame(self: *Window) void {
        if (!self.touch_pending) return;
        if (self.touchActive() == 0) {
            self.touch_pending = false;
            return;
        }
        self.touch_pending = false;
        const t = splitTime(self.touch_pending_time);
        self.pushTouchHeader(2, t);
    }

    /// wl_touch.up: free the slot; last-up emits UP, otherwise
    /// POINTER_UP | (index << 8). Unknown ids are ignored.
    fn onTouchUp(self: *Window, time: u32, wl_id: i32) void {
        self.flushTouchPending();
        const slot = self.touchSlotOf(wl_id) orelse return;
        const last = self.touchActive() == 1;
        const raw: i32 = if (last) 1 else 6 | (@as(i32, @intCast(slot)) << 8);
        const t = splitTime(time);
        // Emit with the slot still counted so the header/rows include the
        // lifting pointer at its last position, then free it.
        self.pushTouchHeader(raw, t);
        self.touch_used[slot] = false;
    }

    /// wl_touch.cancel: CANCEL over all active slots, then clear.
    /// wl_touch.shape/orientation: parsed and discarded (see dispatch).
    fn onTouchCancel(self: *Window) void {
        self.flushTouchPending();
        if (self.touchActive() == 0) return;
        const now = nowMs();
        const t = splitTime(@truncate(now & 0xFFFF_FFFF));
        self.pushTouchHeader(3, t);
        for (0..10) |i| self.touch_used[i] = false;
        self.touch_pending = false;
    }

    /// wl_fixed (24.8) -> float surface-local pixels.
    fn fixedToFloat(v: i32) f32 {
        return @as(f32, @floatFromInt(v)) / 256.0;
    }

    /// Split a u32 millisecond timestamp into (lo, hi) jint halves.
    /// Wayland time is u32 millis with an undefined base; hi is 0.
    fn splitTime(time: u32) TimePair {
        return .{ .lo = @bitCast(time), .hi = 0 };
    }

    fn recv(self: *Window) !void {
        var tmp: [8192]u8 = undefined;
        var cbuf: [32]u8 align(8) = undefined;
        var iov = posix.iovec{ .base = &tmp, .len = tmp.len };
        var msg = linux.msghdr{
            .name = null,
            .namelen = 0,
            .iov = @ptrCast(&iov),
            .iovlen = 1,
            .control = @ptrCast(&cbuf),
            .controllen = cbuf.len,
            .flags = 0,
        };
        var n: usize = 0;
        while (true) {
            const rc = linux.recvmsg(self.sock, &msg, 0);
            switch (linux.errno(rc)) {
                .SUCCESS => {
                    n = rc;
                    break;
                },
                .INTR => continue,
                else => return error.RecvFailed,
            }
        }
        if (n == 0) {
            self.closed = true;
            return;
        }
        // Keymap fds arrive out-of-band via SCM_RIGHTS (wl_keyboard.keymap).
        // Keep them in a small FIFO; the keymap event handler consumes one.
        // Unknown fds are still closed so nothing leaks.
        var co: usize = 0;
        while (co + 16 <= msg.controllen) {
            const h: *const linux.cmsghdr = @ptrCast(@alignCast(cbuf[co..].ptr));
            if (h.len < 16 or h.len > msg.controllen - co) break;
            if (h.level == linux.SOL.SOCKET and h.type == linux.SCM.RIGHTS) {
                const count = (h.len - 16) / 4;
                var i: usize = 0;
                while (i < count) : (i += 1) {
                    const fp: *const i32 = @ptrCast(@alignCast(cbuf[co + 16 + i * 4 ..].ptr));
                    self.pushFd(fp.*);
                }
            }
            co += (h.len + 7) & ~@as(usize, 7);
        }
        try self.rx.appendSlice(self.gpa, tmp[0..n]);
        try self.parseAll();
    }

    fn parseAll(self: *Window) !void {
        while (true) {
            if (self.rx.items.len < 8) return;
            const id = std.mem.readInt(u32, self.rx.items[0..4], .little);
            const lo = std.mem.readInt(u32, self.rx.items[4..8], .little);
            const total: usize = @as(usize, lo >> 16);
            const op: u16 = @truncate(lo);
            if (total < 8) return error.BadMessage;
            if (self.rx.items.len < total) return;
            try self.onEvent(id, op, self.rx.items[8..total]);
            try self.rx.replaceRange(self.gpa, 0, total, &.{});
        }
    }

    fn onEvent(self: *Window, id: u32, op: u16, payload: []const u8) !void {
        var c = Cursor.init(payload);
        if (id == 1) {
            switch (op) {
                0 => {
                    _ = try c.nextU32();
                    _ = try c.nextU32();
                    _ = try c.nextString();
                },
                1 => {
                    _ = try c.nextU32();
                },
                else => {},
            }
            return;
        }
        if (id == self.registry) {
            switch (op) {
                0 => {
                    const name = try c.nextU32();
                    const iface = try c.nextString();
                    const ver = try c.nextU32();
                    self.noteGlobal(name, iface, ver);
                },
                1 => {
                    _ = try c.nextU32();
                },
                else => {},
            }
            return;
        }
        if (id == self.wm_base and op == 0) {
            const serial = try c.nextU32();
            try self.sendWords(self.wm_base, 3, &.{serial});
            return;
        }
        if (id == self.xsurface and op == 0) {
            const serial = try c.nextU32();
            try self.sendWords(self.xsurface, 4, &.{serial});
            self.configured = true;
            return;
        }
        if (id == self.toplevel) {
            switch (op) {
                0 => {
                    const w = try c.nextI32();
                    const h = try c.nextI32();
                    _ = try c.nextArray();
                    self.pend_w = w;
                    self.pend_h = h;
                    self.have_pend_size = true;
                    // Zero means "no size hint": keep the init size.
                    if (w > 0 and h > 0) {
                        const nw: u31 = @intCast(w);
                        const nh: u31 = @intCast(h);
                        if (nw != self.cur_w or nh != self.cur_h) {
                            self.cur_w = nw;
                            self.cur_h = nh;
                            self.size_gen +|= 1;
                            // Window size feeds metrics w/h: bump so Java
                            // re-polls DisplayMetrics on resize.
                            self.metrics_gen +|= 1;
                        }
                    }
                },
                1 => {
                    self.closed = true;
                },
                else => {},
            }
            return;
        }
        if (id == self.shm and op == 0) {
            _ = try c.nextU32();
            return;
        }
        for (self.bufs, 0..) |b, i| {
            if (b != 0 and id == b and op == 0) {
                self.buf_busy[i] = false;
                return;
            }
        }
        if (self.sync_id != 0 and id == self.sync_id and op == 0) {
            _ = try c.nextU32();
            self.sync_done = true;
            return;
        }
        if (self.seat != 0 and id == self.seat) {
            // wl_seat: capabilities(0), name(1, since 2). Requests we send:
            // get_pointer=0, get_keyboard=1.
            if (op == 0) {
                const caps = try c.nextU32();
                self.onSeatCaps(caps);
            }
            return;
        }
        if (self.keyboard != 0 and id == self.keyboard) {
            // wl_keyboard: keymap=0 enter=1 leave=2 key=3 modifiers=4
            // repeat_info=6 (since 4).
            switch (op) {
                0 => {
                    const format = try c.nextU32();
                    const km_size = try c.nextU32();
                    try self.onKeymap(format, km_size);
                },
                1 => {
                    const kserial = try c.nextU32();
                    self.last_serial = kserial;
                    _ = try c.nextU32(); // surface
                    _ = try c.nextArray(); // keys (must not emulate)
                    self.pushEvent(.{ 5, 1, 0, 0, 0, 0, 0, 0 });
                },
                2 => {
                    _ = try c.nextU32(); // serial
                    _ = try c.nextU32(); // surface
                    self.onKeyboardLeave();
                },
                3 => {
                    const kserial = try c.nextU32();
                    self.last_serial = kserial;
                    const time = try c.nextU32();
                    const key = try c.nextU32();
                    const st = try c.nextU32();
                    try self.onKey(kserial, time, key, st);
                },
                4 => {
                    _ = try c.nextU32(); // serial
                    const dep = try c.nextU32();
                    const lat = try c.nextU32();
                    const lock = try c.nextU32();
                    const group = try c.nextU32();
                    self.onModifiers(0, dep, lat, lock, group);
                },
                6 => {
                    const rate = try c.nextI32();
                    const delay = try c.nextI32();
                    self.onRepeatInfo(rate, delay);
                },
                else => {},
            }
            return;
        }
        if (self.pointer != 0 and id == self.pointer) {
            // wl_pointer: enter=0 leave=1 motion=2 button=3 axis=4
            // frame=5 axis_source=6 axis_stop=7 axis_discrete=8
            // axis_value120=9 (since 8) axis_relative_direction=10 warp=11.
            switch (op) {
                0 => {
                    const pserial = try c.nextU32();
                    self.last_serial = pserial;
                    const surface = try c.nextU32();
                    const x = try c.nextI32();
                    const y = try c.nextI32();
                    try self.onPointerEnter(pserial, surface, x, y);
                },
                1 => {
                    _ = try c.nextU32(); // serial
                    _ = try c.nextU32(); // surface
                    self.onPointerLeave();
                },
                2 => {
                    const time = try c.nextU32();
                    const x = try c.nextI32();
                    const y = try c.nextI32();
                    self.onPointerMotion(time, x, y);
                },
                3 => {
                    const pserial = try c.nextU32();
                    self.last_serial = pserial;
                    const time = try c.nextU32();
                    const button = try c.nextU32();
                    const st = try c.nextU32();
                    self.onPointerButton(pserial, time, button, st);
                },
                4 => {
                    const time = try c.nextU32();
                    const axis = try c.nextU32();
                    const value = try c.nextI32();
                    self.onPointerAxis(time, axis, value);
                },
                5 => self.onPointerFrame(),
                9 => {
                    const axis = try c.nextU32();
                    const value120 = try c.nextI32();
                    self.onAxisValue120(axis, value120);
                },
                else => {},
            }
            return;
        }
        if (self.touch != 0 and id == self.touch) {
            // wl_touch: down=0 up=1 motion=2 frame=3 cancel=4 shape=5
            // orientation=6. xdg_output-style globals are NOT handled here;
            // shape/orientation are parsed and discarded per contract.
            switch (op) {
                0 => {
                    _ = try c.nextU32(); // serial
                    const time = try c.nextU32();
                    _ = try c.nextU32(); // surface
                    const wl_id = try c.nextI32();
                    const x = try c.nextI32();
                    const y = try c.nextI32();
                    self.onTouchDown(time, wl_id, x, y);
                },
                1 => {
                    _ = try c.nextU32(); // serial
                    const time = try c.nextU32();
                    const wl_id = try c.nextI32();
                    self.onTouchUp(time, wl_id);
                },
                2 => {
                    const time = try c.nextU32();
                    const wl_id = try c.nextI32();
                    const x = try c.nextI32();
                    const y = try c.nextI32();
                    self.onTouchMotion(time, wl_id, x, y);
                },
                3 => self.onTouchFrame(),
                4 => self.onTouchCancel(),
                5 => {
                    _ = try c.nextI32(); // id
                    _ = try c.nextI32(); // major
                    _ = try c.nextI32(); // minor
                },
                6 => {
                    _ = try c.nextI32(); // id
                    _ = try c.nextI32(); // orientation
                },
                else => {},
            }
            return;
        }
        if (self.output != 0 and id == self.output) {
            // wl_output at min(adv,4): geometry=0 mode=1 done=2 scale=3
            // name=4 description=5. xdg_output is DEFERRED (ignored).
            switch (op) {
                0 => {
                    _ = try c.nextI32(); // x
                    _ = try c.nextI32(); // y
                    const mm_w = try c.nextI32();
                    const mm_h = try c.nextI32();
                    _ = try c.nextI32(); // subpixel
                    _ = try c.nextString(); // make
                    _ = try c.nextString(); // model
                    _ = try c.nextI32(); // transform
                    self.out_mm_w = mm_w;
                    self.out_mm_h = mm_h;
                },
                1 => {
                    _ = try c.nextU32(); // flags
                    _ = try c.nextI32(); // width
                    _ = try c.nextI32(); // height
                    _ = try c.nextI32(); // refresh
                },
                2 => {
                    // Atomic batch end: publish a fresh metrics generation.
                    self.metrics_gen +|= 1;
                },
                3 => {
                    const factor = try c.nextI32();
                    if (factor > 0) self.out_scale = factor;
                },
                4 => {
                    _ = try c.nextString(); // name
                },
                5 => {
                    _ = try c.nextString(); // description
                },
                else => {},
            }
            return;
        }
        // --- M4 clipboard/data-device dispatch (clipboard agent owns) ---
        if (self.clip_offer != 0 and id == self.clip_offer) {
            // wl_data_offer: offer=0 only. source_actions/action (3/4)
            // are dnd-only; ignored here.
            if (op == 0) {
                const mime = try c.nextString();
                self.onOfferMime(mime);
            }
            return;
        }
        if (self.clip_source != 0 and id == self.clip_source) {
            // wl_data_source: target=0, send=1, cancelled=2.
            // dnd_* /action (4..7) are dnd-only; ignored here.
            switch (op) {
                0 => {
                    _ = try c.nextString(); // accepted mime (nullable)
                },
                1 => {
                    const mime = try c.nextString();
                    const wfd = self.shiftFd();
                    self.onSourceSend(mime, wfd);
                },
                2 => self.onSourceCancelled(),
                else => {},
            }
            return;
        }
        if (self.data_device != 0 and id == self.data_device) {
            // wl_data_device: data_offer=0, selection=2. enter/leave/motion
            // (1/3..6, dnd) are parsed minimally and otherwise ignored.
            switch (op) {
                0 => {
                    const offer_id = try c.nextU32();
                    self.onDataOffer(offer_id);
                },
                1 => {
                    _ = try c.nextU32(); // serial
                    _ = try c.nextU32(); // surface
                    _ = try c.nextI32(); // x (fixed)
                    _ = try c.nextI32(); // y (fixed)
                    _ = try c.nextU32(); // offer id (nullable object)
                },
                2 => {
                    const offer_id = try c.nextU32();
                    self.onSelection(offer_id);
                },
                3 => {
                    _ = try c.nextU32(); // time
                    _ = try c.nextI32(); // x (fixed)
                    _ = try c.nextI32(); // y (fixed)
                },
                4 => {},
                5 => {},
                6 => {},
                else => {},
            }
            return;
        }
        if (op == 0) {
            for (self.frames[0..self.nframes], 0..) |fid, i| {
                if (fid == id) {
                    _ = try c.nextU32();
                    std.mem.copyForwards(u32, self.frames[i .. self.nframes - 1], self.frames[i + 1 .. self.nframes]);
                    self.nframes -= 1;
                    return;
                }
            }
        }
        // Anything else (delete_id, future versions, xdg_output): ignore.
    }

    fn sendRaw(self: *Window, bytes: []const u8, fd: ?posix.fd_t) !void {
        var cmsg: [24]u8 align(8) = undefined;
        const with_fd = fd != null;
        if (with_fd) {
            const hdr: *linux.cmsghdr = @ptrCast(@alignCast(&cmsg));
            hdr.* = .{
                .len = 16 + 4,
                .level = @as(i32, linux.SOL.SOCKET),
                .type = @as(i32, linux.SCM.RIGHTS),
            };
            const fp: *i32 = @ptrCast(@alignCast(cmsg[16..20].ptr));
            fp.* = fd.?;
        }
        var off: usize = 0;
        var first = true;
        while (off < bytes.len) {
            var iov = posix.iovec_const{ .base = bytes.ptr + off, .len = bytes.len - off };
            const use_cmsg = with_fd and first;
            const msg = linux.msghdr_const{
                .name = null,
                .namelen = 0,
                .iov = @ptrCast(&iov),
                .iovlen = 1,
                .control = if (use_cmsg) @ptrCast(&cmsg) else null,
                .controllen = if (use_cmsg) cmsg.len else 0,
                .flags = 0,
            };
            const rc = linux.sendmsg(self.sock, &msg, 0);
            switch (linux.errno(rc)) {
                .SUCCESS => {
                    off += rc;
                    first = false;
                },
                .INTR => continue,
                .PIPE => return error.ConnectionClosed,
                else => return error.SendFailed,
            }
        }
    }

    fn finishSend(self: *Window, buf: []u8, pos: usize, id: u32, op: u16) !void {
        try self.finishSendFd(buf, pos, id, op, null);
    }

    fn finishSendFd(self: *Window, buf: []u8, pos: usize, id: u32, op: u16, fd: ?posix.fd_t) !void {
        if (pos > 0xFFFF) return error.MessageTooLong;
        std.mem.writeInt(u32, buf[0..4], id, .little);
        std.mem.writeInt(u32, buf[4..8], (@as(u32, @intCast(pos)) << 16) | @as(u32, op), .little);
        try self.sendRaw(buf[0..pos], fd);
    }

    /// Request with only u32/int words in the payload.
    fn sendWords(self: *Window, obj: u32, op: u16, words: []const u32) !void {
        var buf: [64]u8 = undefined;
        if (8 + words.len * 4 > buf.len) return error.MessageTooLong;
        var pos: usize = 8;
        for (words) |w| {
            std.mem.writeInt(u32, buf[pos..][0..4], w, .little);
            pos += 4;
        }
        try self.finishSend(&buf, pos, obj, op);
    }

    fn sendStr(self: *Window, obj: u32, op: u16, s: []const u8) !void {
        const need = 8 + stringWireLen(s);
        if (need > 0xFFFF or s.len + 1 > std.math.maxInt(u32)) return error.MessageTooLong;
        if (need <= 512) {
            var stack: [512]u8 = undefined;
            var pos: usize = 8;
            try putStr(stack[0..], &pos, s);
            try self.finishSend(&stack, pos, obj, op);
        } else {
            const heap = try self.gpa.alloc(u8, need);
            defer self.gpa.free(heap);
            var pos: usize = 8;
            try putStr(heap, &pos, s);
            try self.finishSend(heap, pos, obj, op);
        }
    }

    fn sendBind(self: *Window, reg: u32, name: u32, iface: []const u8, version: u32, new_id: u32) !void {
        var buf: [256]u8 = undefined;
        var pos: usize = 8;
        try putU32(buf[0..], &pos, name);
        try putStr(buf[0..], &pos, iface);
        try putU32(buf[0..], &pos, version);
        try putU32(buf[0..], &pos, new_id);
        try self.finishSend(&buf, pos, reg, 0);
    }

    fn sendCreatePool(self: *Window, shm_id: u32, pool_id: u32, fd: posix.fd_t, pool_size: i32) !void {
        var buf: [64]u8 = undefined;
        var pos: usize = 8;
        try putU32(buf[0..], &pos, pool_id);
        try putI32(buf[0..], &pos, pool_size);
        try self.finishSendFd(&buf, pos, shm_id, 0, fd);
    }

    fn sendCreateBuffer(
        self: *Window,
        pool_id: u32,
        buf_id: u32,
        offset: i32,
        w: u31,
        h: u31,
        byte_stride: usize,
        format: u32,
    ) !void {
        try self.sendWords(pool_id, 0, &.{
            buf_id,
            @bitCast(offset),
            @as(u32, w),
            @as(u32, h),
            @as(u32, @intCast(byte_stride)),
            format,
        });
    }

    fn makePool(self: *Window, w: u31, h: u31) !void {
        const byte_stride: usize = @as(usize, w) * 4;
        const buf_len: usize = byte_stride * @as(usize, h);
        const total: usize = buf_len * 2;
        const fd = posix.memfd_create("isakai-shm", linux.MFD.CLOEXEC) catch return error.ShmFailed;
        errdefer {
            _ = linux.close(fd);
        }
        if (linux.errno(linux.ftruncate(fd, @intCast(total))) != .SUCCESS) return error.ShmFailed;
        const mem = posix.mmap(
            null,
            total,
            .{ .READ = true, .WRITE = true },
            .{ .TYPE = .SHARED },
            fd,
            0,
        ) catch return error.ShmFailed;
        errdefer posix.munmap(mem);

        const pool = self.allocId();
        try self.sendCreatePool(self.shm, pool, fd, @intCast(total));
        const b0 = self.allocId();
        const b1 = self.allocId();
        try self.sendCreateBuffer(pool, b0, 0, w, h, byte_stride, fmt_xrgb8888);
        try self.sendCreateBuffer(pool, b1, @intCast(buf_len), w, h, byte_stride, fmt_xrgb8888);

        self.memfd = fd;
        self.map = mem;
        self.pool = pool;
        self.bufs = .{ b0, b1 };
        self.pool_w = w;
        self.pool_h = h;
        self.buf_busy = .{ false, false };
        self.pool_gen += 1;
    }

    fn remakePool(self: *Window, w: u31, h: u31) !void {
        for (self.bufs) |b| {
            if (b != 0) try self.sendWords(b, 0, &.{});
        }
        if (self.pool != 0) try self.sendWords(self.pool, 1, &.{});
        if (self.map) |m| {
            posix.munmap(m);
            self.map = null;
        }
        if (self.memfd != -1) {
            _ = linux.close(self.memfd);
            self.memfd = -1;
        }
        self.bufs = .{ 0, 0 };
        self.pool = 0;
        self.pool_w = 0;
        self.pool_h = 0;
        self.buf_busy = .{ false, false };
        self.last_mapped = null;
        try self.makePool(w, h);
    }
};

fn nowMs() u64 {
    var ts: posix.timespec = undefined;
    if (linux.errno(linux.clock_gettime(.MONOTONIC, &ts)) != .SUCCESS) return 0;
    const s: u64 = @intCast(@max(ts.sec, 0));
    const ns: u64 = @intCast(@max(ts.nsec, 0));
    return s * 1000 + ns / 1_000_000;
}

fn connectSock(path: []const u8) !posix.fd_t {
    if (path.len + 1 > 108) return error.PathTooLong;
    const rc = linux.socket(
        @as(u32, linux.AF.UNIX),
        @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC),
        0,
    );
    if (linux.errno(rc) != .SUCCESS) return error.SocketFailed;
    const fd: posix.fd_t = @intCast(rc);
    errdefer {
        _ = linux.close(fd);
    }
    var path_arr: [108]u8 = undefined;
    @memset(&path_arr, 0);
    var addr = linux.sockaddr.un{ .path = path_arr };
    @memcpy(addr.path[0..path.len], path);
    const addrlen: linux.socklen_t = @intCast(2 + path.len + 1);
    if (linux.errno(linux.connect(fd, @ptrCast(&addr), addrlen)) != .SUCCESS) {
        return error.ConnectFailed;
    }
    return fd;
}

fn resolvePath(gpa: std.mem.Allocator, display_path: ?[]const u8, out: *[108]u8) ![]const u8 {
    if (display_path) |p| {
        if (p.len + 1 > 108) return error.PathTooLong;
        @memcpy(out[0..p.len], p);
        return out[0..p.len];
    }
    const env = try readEnviron(gpa);
    defer gpa.free(env.buf);
    const runtime = env.runtime orelse return error.NoRuntimeDir;
    const display = env.display orelse "wayland-0";
    if (runtime.len + 1 + display.len + 1 > 108) return error.PathTooLong;
    var pos: usize = 0;
    @memcpy(out[pos..][0..runtime.len], runtime);
    pos += runtime.len;
    out[pos] = '/';
    pos += 1;
    @memcpy(out[pos..][0..display.len], display);
    pos += display.len;
    return out[0..pos];
}

const EnvironResult = struct {
    buf: []u8,
    runtime: ?[]const u8,
    display: ?[]const u8,
};

/// Read our own environment without libc: /proc/self/environ holds
/// NUL-separated KEY=VALUE entries.
fn readEnviron(gpa: std.mem.Allocator) !EnvironResult {
    const fd_rc = linux.openat(linux.AT.FDCWD, "/proc/self/environ", .{}, 0);
    if (linux.errno(fd_rc) != .SUCCESS) return error.NoRuntimeDir;
    const fd: posix.fd_t = @intCast(fd_rc);
    defer {
        _ = linux.close(fd);
    }
    var list: std.ArrayListUnmanaged(u8) = .empty;
    errdefer list.deinit(gpa);
    var tmp: [4096]u8 = undefined;
    while (true) {
        const rc = linux.read(fd, &tmp, tmp.len);
        switch (linux.errno(rc)) {
            .SUCCESS => {},
            .INTR => continue,
            else => return error.NoRuntimeDir,
        }
        if (rc == 0) break;
        try list.appendSlice(gpa, tmp[0..rc]);
    }
    const buf = try list.toOwnedSlice(gpa);
    var res = EnvironResult{ .buf = buf, .runtime = null, .display = null };
    var it = std.mem.splitScalar(u8, buf, 0);
    while (it.next()) |entry| {
        const eq = std.mem.indexOfScalar(u8, entry, '=') orelse continue;
        const k = entry[0..eq];
        const v = entry[eq + 1 ..];
        if (std.mem.eql(u8, k, "XDG_RUNTIME_DIR")) {
            res.runtime = v;
        } else if (std.mem.eql(u8, k, "WAYLAND_DISPLAY")) {
            res.display = v;
        }
    }
    return res;
}

// ---------------------------------------------------------------------------
// Wire codec. Pure helpers; the unit tests below pin byte-exact vectors.
// ---------------------------------------------------------------------------

pub const DecodeError = error{ Truncated, BadString };

/// Total on-wire bytes of a message with this payload length.
fn frameLen(payload_len: usize) usize {
    return 8 + payload_len;
}

/// Encode an 8-byte message header: object id + (length << 16 | opcode).
fn encodeHeader(id: u32, opcode: u16, total_len: u16) [8]u8 {
    var out: [8]u8 = undefined;
    std.mem.writeInt(u32, out[0..4], id, .little);
    std.mem.writeInt(u32, out[4..8], (@as(u32, total_len) << 16) | @as(u32, opcode), .little);
    return out;
}

pub fn padLen(n: usize) usize {
    return (n + 3) & ~@as(usize, 3);
}

/// Shared wire-length for length-prefixed padded blobs: header length + pad.
fn wireLen(header_len: usize) usize {
    return 4 + padLen(header_len);
}

/// On-wire bytes for a string: u32 length (incl. NUL) + bytes + NUL + pad.
pub fn stringWireLen(s: []const u8) usize {
    return wireLen(s.len + 1);
}

/// On-wire bytes for an array: u32 byte length + bytes + pad.
fn arrayWireLen(nbytes: usize) usize {
    return wireLen(nbytes);
}

/// Shared writer for length-prefixed padded blobs (string/array).
fn writeLenPrefixed(out: []u8, header: u32, data: []const u8, extra_nul: bool) usize {
    std.mem.writeInt(u32, out[0..4], header, .little);
    @memcpy(out[4..][0..data.len], data);
    var end: usize = 4 + data.len;
    if (extra_nul) {
        out[end] = 0;
        end += 1;
    }
    const total = wireLen(header);
    @memset(out[end..total], 0);
    return total;
}

/// Append a string to `out`; caller guarantees `stringWireLen(s)` bytes.
/// Returns bytes written.
pub fn writeString(out: []u8, s: []const u8) usize {
    return writeLenPrefixed(out, @intCast(s.len + 1), s, true);
}

/// Append raw array bytes to `out`; caller guarantees
/// `arrayWireLen(data.len)` bytes. Returns bytes written.
fn writeArray(out: []u8, data: []const u8) usize {
    return writeLenPrefixed(out, @intCast(data.len), data, false);
}

fn putU32(buf: []u8, pos: *usize, v: u32) !void {
    if (pos.* + 4 > buf.len) return error.MessageTooLong;
    std.mem.writeInt(u32, buf[pos.*..][0..4], v, .little);
    pos.* += 4;
}

fn putI32(buf: []u8, pos: *usize, v: i32) !void {
    try putU32(buf, pos, @bitCast(v));
}

fn putStr(buf: []u8, pos: *usize, s: []const u8) !void {
    if (s.len + 1 > std.math.maxInt(u32)) return error.MessageTooLong;
    if (pos.* + stringWireLen(s) > buf.len) return error.MessageTooLong;
    pos.* += writeString(buf[pos.*..], s);
}

pub const Cursor = struct {
    buf: []const u8,
    pos: usize = 0,

    pub fn init(buf: []const u8) Cursor {
        return .{ .buf = buf };
    }

    pub fn rest(self: *const Cursor) []const u8 {
        return self.buf[self.pos..];
    }

    pub fn nextU32(self: *Cursor) DecodeError!u32 {
        if (self.pos + 4 > self.buf.len) return error.Truncated;
        const v = std.mem.readInt(u32, self.buf[self.pos..][0..4], .little);
        self.pos += 4;
        return v;
    }

    pub fn nextI32(self: *Cursor) DecodeError!i32 {
        return @bitCast(try self.nextU32());
    }

    /// Shared decode for length-prefixed padded blobs; strings additionally
    /// require non-empty length and a trailing NUL.
    fn nextLenPrefixed(self: *Cursor, is_string: bool) DecodeError![]const u8 {
        const n = try self.nextU32();
        if (is_string and n == 0) return error.BadString;
        const left = self.buf.len - self.pos;
        if (n > left) return error.Truncated;
        if (padLen(n) > left) return error.Truncated;
        const s = self.buf[self.pos..][0..n];
        if (is_string and s[n - 1] != 0) return error.BadString;
        self.pos += padLen(n);
        return if (is_string) s[0 .. n - 1] else s;
    }

    /// String bytes without the terminating NUL (a slice of the input).
    pub fn nextString(self: *Cursor) DecodeError![]const u8 {
        return self.nextLenPrefixed(true);
    }

    pub fn nextArray(self: *Cursor) DecodeError![]const u8 {
        return self.nextLenPrefixed(false);
    }
};
