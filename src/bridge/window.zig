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
    /// Ring of 8-jint input events (kinds 1/2/5/6 per the M3 contract).
    events: [256][8]i32 = undefined,
    ev_head: usize = 0,
    ev_len: usize = 0,
    ev_dropped: bool = false,

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

        try self.makePool(self.cur_w, self.cur_h);
        return self;
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
    /// when there is no outstanding mapped buffer (stale/double commit).
    pub fn commit(self: *Window, w: u31, h: u31) !bool {
        const bi = self.last_mapped orelse return false;
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
        // Seat objects: release is version-gated (keyboard/pointer need v3+,
        // seat needs v5+). Bound version is min(advertised, 8); only send
        // release when the bound version supports it.
        const seat_ver: u32 = @min(self.seat_version, 8);
        if (self.keyboard != 0 and seat_ver >= 3) self.sendWords(self.keyboard, 0, &.{}) catch {};
        if (self.pointer != 0 and seat_ver >= 3) self.sendWords(self.pointer, 0, &.{}) catch {};
        if (self.seat != 0 and seat_ver >= 5) self.sendWords(self.seat, 4, &.{}) catch {};
        const gpa = self.gpa;
        self.cleanup();
        gpa.destroy(self);
    }

    fn cleanup(self: *Window) void {
        self.resetXkb();
        self.drainFds();
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
    // M3 input engine: ring buffer, fd FIFO, keymap, seat/keyboard/
    // pointer handlers, client-side repeat. Depends on xkb.zig only via
    // the `xkb` import below (window.zig itself stays std-only: the
    // extern/linkLibC surface lives in xkb.zig).
    // ------------------------------------------------------------------

    /// Push one 8-jint event. Cap 256; on overflow the oldest is dropped
    /// and `ev_dropped` latches (takeInput clears it on read).
    pub fn pushEvent(self: *Window, ev: [8]i32) void {
        if (self.ev_len == self.events.len) {
            self.ev_head = (self.ev_head + 1) % self.events.len;
            self.ev_len -= 1;
            self.ev_dropped = true;
        }
        const idx = (self.ev_head + self.ev_len) % self.events.len;
        self.events[idx] = ev;
        self.ev_len += 1;
    }

    /// Number of queued input events.
    pub fn pendingInputs(self: *const Window) usize {
        return self.ev_len;
    }

    /// True when at least one event was dropped since the last takeInput.
    pub fn inputDropped(self: *const Window) bool {
        return self.ev_dropped;
    }

    /// Drain up to `out.len / 8` events into `out` (must be a multiple of
    /// 8 ints). Returns the event count; clears the dropped flag.
    /// Pure accessor: performs no I/O.
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
        self.ev_dropped = false;
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

    /// wl_seat.capabilities: create keyboard/pointer objects for newly
    /// present capabilities. Never destroys on loss (compositor may send
    /// no further events; objects go away at close).
    fn onSeatCaps(self: *Window, caps: u32) void {
        const have_kb = (caps & 2) != 0;
        const have_ptr = (caps & 1) != 0;
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

    /// wl_fixed (24.8) -> float surface-local pixels.
    fn fixedToFloat(v: i32) f32 {
        return @as(f32, @floatFromInt(v)) / 256.0;
    }

    /// Split a u32 millisecond timestamp into (lo, hi) jint halves.
    /// Wayland time is u32 millis with an undefined base; hi is 0.
    fn splitTime(time: u32) struct { lo: i32, hi: i32 } {
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
                    _ = try c.nextU32(); // serial
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
                    const serial = try c.nextU32();
                    const time = try c.nextU32();
                    const key = try c.nextU32();
                    const st = try c.nextU32();
                    try self.onKey(serial, time, key, st);
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
                    const serial = try c.nextU32();
                    const surface = try c.nextU32();
                    const x = try c.nextI32();
                    const y = try c.nextI32();
                    try self.onPointerEnter(serial, surface, x, y);
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
                    const serial = try c.nextU32();
                    const time = try c.nextU32();
                    const button = try c.nextU32();
                    const st = try c.nextU32();
                    self.onPointerButton(serial, time, button, st);
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
        // Anything else (delete_id, future versions): ignore.
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
// ---------------------------------------------------------------------------
// Tests: byte-exact vectors for header, string padding, array padding.
// ---------------------------------------------------------------------------

test "header encodes id, opcode, length" {
    const h = encodeHeader(2, 1, 12);
    try std.testing.expectEqualSlices(u8, &[_]u8{ 0x02, 0x00, 0x00, 0x00, 0x01, 0x00, 0x0c, 0x00 }, &h);
    try std.testing.expectEqual(@as(usize, 12), frameLen(4));
}

test "string pads to a 4-byte boundary" {
    var out: [32]u8 = undefined;
    // "wl_shm": length 7 incl. NUL, padded 7 -> 8.
    const n = writeString(&out, "wl_shm");
    try std.testing.expectEqual(@as(usize, 12), n);
    try std.testing.expectEqual(@as(usize, 12), stringWireLen("wl_shm"));
    try std.testing.expectEqualSlices(u8, &[_]u8{
        0x07, 0x00, 0x00, 0x00,
        'w',  'l',  '_',  's',
        'h',  'm',  0x00, 0x00,
    }, out[0..n]);
}

test "string with no padding needed" {
    var out: [16]u8 = undefined;
    // "abc": length 4 incl. NUL, already aligned.
    const n = writeString(&out, "abc");
    try std.testing.expectEqualSlices(u8, &[_]u8{
        0x04, 0x00, 0x00, 0x00,
        'a',  'b',  'c',  0x00,
    }, out[0..n]);
}

test "empty string encodes a lone NUL plus padding" {
    var out: [8]u8 = undefined;
    const n = writeString(&out, "");
    try std.testing.expectEqualSlices(u8, &[_]u8{
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    }, out[0..n]);
}

test "array pads to a 4-byte boundary" {
    var out: [32]u8 = undefined;
    const words = [_]u32{ 1, 2, 3 };
    const n = writeArray(&out, std.mem.sliceAsBytes(&words));
    try std.testing.expectEqual(@as(usize, 16), n);
    try std.testing.expectEqual(@as(usize, 16), arrayWireLen(12));
    try std.testing.expectEqualSlices(u8, &[_]u8{
        0x0c, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x00, 0x00,
        0x02, 0x00, 0x00, 0x00,
        0x03, 0x00, 0x00, 0x00,
    }, out[0..n]);
}

test "odd-length array gains padding" {
    var out: [16]u8 = undefined;
    const n = writeArray(&out, &[_]u8{ 0xaa, 0xbb, 0xcc, 0xdd, 0xee });
    try std.testing.expectEqualSlices(u8, &[_]u8{
        0x05, 0x00, 0x00, 0x00,
        0xaa, 0xbb, 0xcc, 0xdd,
        0xee, 0x00, 0x00, 0x00,
    }, out[0..n]);
}

test "decode a registry global event" {
    // wl_registry.global(0) on id 2: name=5, interface="wl_shm", version=1.
    const msg = [_]u8{
        0x02, 0x00, 0x00, 0x00, // object id 2
        0x00, 0x00, 0x1c, 0x00, // length 28, opcode 0
        0x05, 0x00, 0x00, 0x00, // name 5
        0x07, 0x00, 0x00, 0x00, // string length 7
        'w',  'l',  '_',  's',
        'h', 'm', 0x00, 0x00, // "wl_shm\0" + pad
        0x01, 0x00, 0x00, 0x00, // version 1
    };
    try std.testing.expectEqual(@as(usize, 28), msg.len);
    const id = std.mem.readInt(u32, msg[0..4], .little);
    const lo = std.mem.readInt(u32, msg[4..8], .little);
    try std.testing.expectEqual(@as(u32, 2), id);
    try std.testing.expectEqual(@as(u16, 0), @as(u16, @truncate(lo)));
    try std.testing.expectEqual(@as(usize, 28), @as(usize, lo >> 16));
    var c = Cursor.init(msg[8..]);
    try std.testing.expectEqual(@as(u32, 5), try c.nextU32());
    try std.testing.expectEqualStrings("wl_shm", try c.nextString());
    try std.testing.expectEqual(@as(u32, 1), try c.nextU32());
    try std.testing.expectEqual(@as(usize, 0), c.rest().len);
}

test "decode a toplevel configure event" {
    // xdg_toplevel.configure(0) on id 9: 640x480, empty states.
    const msg = [_]u8{
        0x09, 0x00, 0x00, 0x00, // object id 9
        0x00, 0x00, 0x14, 0x00, // length 20, opcode 0
        0x80, 0x02, 0x00, 0x00, // width 640
        0xe0, 0x01, 0x00, 0x00, // height 480
        0x00, 0x00, 0x00, 0x00, // states: empty array
    };
    var c = Cursor.init(msg[8..]);
    try std.testing.expectEqual(@as(i32, 640), try c.nextI32());
    try std.testing.expectEqual(@as(i32, 480), try c.nextI32());
    try std.testing.expectEqual(@as(usize, 0), (try c.nextArray()).len);
    try std.testing.expectEqual(@as(usize, 0), c.rest().len);
}

test "decode rejects a string missing its NUL" {
    const bad = [_]u8{
        0x03, 0x00, 0x00, 0x00,
        'a',  'b',  'c',  0x00,
    };
    var c = Cursor.init(&bad);
    try std.testing.expectError(error.BadString, c.nextString());
}

test "decode rejects truncation" {
    const short = [_]u8{ 0x01, 0x00 };
    var c = Cursor.init(&short);
    try std.testing.expectError(error.Truncated, c.nextU32());
    const cut = [_]u8{
        0x08, 0x00, 0x00, 0x00,
        'a',  'b',
    };
    var c2 = Cursor.init(&cut);
    try std.testing.expectError(error.Truncated, c2.nextString());
}

test "string roundtrip through cursor" {
    var out: [64]u8 = undefined;
    var pos: usize = 0;
    pos += writeString(out[pos..], "xdg_wm_base");
    pos += writeString(out[pos..], "");
    pos += writeString(out[pos..], "compositor");
    var c = Cursor.init(out[0..pos]);
    try std.testing.expectEqualStrings("xdg_wm_base", try c.nextString());
    try std.testing.expectEqualStrings("", try c.nextString());
    try std.testing.expectEqualStrings("compositor", try c.nextString());
    try std.testing.expectEqual(@as(usize, 0), c.rest().len);
}

/// Feed a synthetic xdg_toplevel.configure payload through onEvent.
/// Payload layout: i32 w, i32 h, array states.
fn fakeConfigure(self: *Window, w: i32, h: i32) !void {
    var buf: [12]u8 = undefined;
    std.mem.writeInt(u32, buf[0..4], @bitCast(w), .little);
    std.mem.writeInt(u32, buf[4..8], @bitCast(h), .little);
    std.mem.writeInt(u32, buf[8..12], 0, .little);
    self.toplevel = 99;
    try self.onEvent(99, 0, buf[0..12]);
}

test "takeResize fires once per configure generation" {
    var win = Window{
        .gpa = std.testing.allocator,
        .cur_w = 640,
        .cur_h = 480,
    };
    try std.testing.expectEqual(@as(u64, 0), win.sizeGen());
    try std.testing.expect(win.takeResize() == null);
    try fakeConfigure(&win, 800, 600);
    try std.testing.expectEqual(@as(u64, 1), win.sizeGen());
    const r = win.takeResize() orelse return error.TestUnexpectedResult;
    try std.testing.expectEqual(@as(u31, 800), r.w);
    try std.testing.expectEqual(@as(u31, 600), r.h);
    try std.testing.expect(win.takeResize() == null);
}

test "takeResize ignores zero-size and duplicate configures" {
    var win = Window{
        .gpa = std.testing.allocator,
        .cur_w = 640,
        .cur_h = 480,
    };
    try fakeConfigure(&win, 0, 0);
    try std.testing.expectEqual(@as(u64, 0), win.sizeGen());
    try std.testing.expect(win.takeResize() == null);
    try std.testing.expectEqual(@as(u31, 640), win.size().w);
    try fakeConfigure(&win, 640, 480);
    try std.testing.expectEqual(@as(u64, 0), win.sizeGen());
    try std.testing.expect(win.takeResize() == null);
    try fakeConfigure(&win, 800, 600);
    try fakeConfigure(&win, 800, 600);
    try std.testing.expectEqual(@as(u64, 1), win.sizeGen());
    _ = win.takeResize();
    try std.testing.expect(win.takeResize() == null);
    try std.testing.expect(!win.isClosed());
}

test "close event marks isClosed" {
    var win = Window{
        .gpa = std.testing.allocator,
        .cur_w = 640,
        .cur_h = 480,
    };
    win.toplevel = 99;
    try win.onEvent(99, 1, &.{});
    try std.testing.expect(win.isClosed());
}

// ---------------------------------------------------------------------------
// M3 input tests: synthetic seat/keyboard/pointer payloads -> ring contents.
// ---------------------------------------------------------------------------

/// Blank test window: no socket, gpa for keymap copies only.
fn blankInputWindow() Window {
    return Window{ .gpa = std.testing.allocator };
}

fn putU32Wire(buf: []u8, pos: *usize, v: u32) void {
    putU32(buf, pos, v) catch unreachable;
}

fn putI32Wire(buf: []u8, pos: *usize, v: i32) void {
    putI32(buf, pos, v) catch unreachable;
}
test "seat capabilities create keyboard and pointer" {
    var win = blankInputWindow();
    win.seat = 10;
    var payload: [4]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 3); // pointer|keyboard
    try win.onEvent(10, 0, payload[0..pos]);
    try std.testing.expect(win.keyboard != 0);
    try std.testing.expect(win.pointer != 0);
    try std.testing.expect(win.keyboard != win.pointer);
    // Loss of caps does not destroy objects.
    pos = 0;
    putU32Wire(&payload, &pos, 0);
    try win.onEvent(10, 0, payload[0..pos]);
    try std.testing.expect(win.keyboard != 0);
    try std.testing.expect(win.pointer != 0);
}

test "keyboard enter and leave queue kbd focus events" {
    var win = blankInputWindow();
    win.keyboard = 11;
    var payload: [16]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 1); // serial
    putU32Wire(&payload, &pos, 7); // surface
    putU32Wire(&payload, &pos, 0); // keys: empty array
    try win.onEvent(11, 1, payload[0..pos]);
    pos = 0;
    putU32Wire(&payload, &pos, 2); // serial
    putU32Wire(&payload, &pos, 7); // surface
    try win.onEvent(11, 2, payload[0..pos]);
    try std.testing.expectEqual(@as(usize, 2), win.pendingInputs());
    var out: [16]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 2), n);
    try std.testing.expectEqual(@as(i32, 5), out[0]);
    try std.testing.expectEqual(@as(i32, 1), out[1]);
    try std.testing.expectEqual(@as(i32, 5), out[8]);
    try std.testing.expectEqual(@as(i32, 0), out[9]);
    try std.testing.expectEqual(@as(usize, 0), win.pendingInputs());
}

test "key press and release forward keycode and unicode" {
    var win = blankInputWindow();
    win.keyboard = 11;
    var key_payload: [16]u8 = undefined;
    // evdev 30 (a): press then release.
    var pos: usize = 0;
    putU32Wire(&key_payload, &pos, 100); // serial
    putU32Wire(&key_payload, &pos, 1000); // time
    putU32Wire(&key_payload, &pos, 30); // key (evdev a)
    putU32Wire(&key_payload, &pos, 1); // pressed
    try win.onEvent(11, 3, key_payload[0..pos]);
    pos = 0;
    putU32Wire(&key_payload, &pos, 101);
    putU32Wire(&key_payload, &pos, 1016);
    putU32Wire(&key_payload, &pos, 30);
    putU32Wire(&key_payload, &pos, 0); // released
    try win.onEvent(11, 3, key_payload[0..pos]);
    // Unknown key still forwards unicode (evdev 30 is 'a' in fake map).
    var out: [16]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 2), n);
    // DOWN: kind 1, action 0, keyCode 29 (A), unicode 'a'.
    try std.testing.expectEqual(@as(i32, 1), out[0]);
    try std.testing.expectEqual(@as(i32, 0), out[1]);
    try std.testing.expectEqual(@as(i32, 29), out[2]);
    try std.testing.expectEqual(@as(i32, 0x61), out[3]);
    try std.testing.expectEqual(@as(i32, 1000), out[5]);
    // UP: kind 1, action 1.
    try std.testing.expectEqual(@as(i32, 1), out[8]);
    try std.testing.expectEqual(@as(i32, 1), out[9]);
    try std.testing.expectEqual(@as(i32, 29), out[10]);
}

test "unknown keycode forwards unicode with keycode zero" {
    var win = blankInputWindow();
    win.keyboard = 11;
    var payload: [16]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 100);
    putU32Wire(&payload, &pos, 1000);
    putU32Wire(&payload, &pos, 84); // evdev 84: unmapped
    putU32Wire(&payload, &pos, 1);
    try win.onEvent(11, 3, payload[0..pos]);
    var out: [8]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 1), n);
    try std.testing.expectEqual(@as(i32, 1), out[0]);
    try std.testing.expectEqual(@as(i32, 0), out[1]);
    try std.testing.expectEqual(@as(i32, 0), out[2]); // KEYCODE_UNKNOWN
}

test "modifiers update metaState on the next key" {
    var win = blankInputWindow();
    win.keyboard = 11;
    var payload: [20]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 50); // serial
    putU32Wire(&payload, &pos, 1); // depressed (Shift bit in fake map)
    putU32Wire(&payload, &pos, 0);
    putU32Wire(&payload, &pos, 0);
    putU32Wire(&payload, &pos, 0);
    try win.onEvent(11, 4, payload[0..pos]);
    // Press 'a' with Shift depressed: unicode uppercases, meta has SHIFT.
    var kp: [16]u8 = undefined;
    pos = 0;
    putU32Wire(&kp, &pos, 51);
    putU32Wire(&kp, &pos, 2000);
    putU32Wire(&kp, &pos, 30);
    putU32Wire(&kp, &pos, 1);
    try win.onEvent(11, 3, kp[0..pos]);
    var out: [8]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 1), n);
    try std.testing.expectEqual(@as(i32, 0x41), out[3]); // 'A'
    try std.testing.expect((out[4] & 0x1) != 0); // META_SHIFT_ON
}

test "pointer enter motion button leave fold into motion and focus" {
    var win = blankInputWindow();
    win.pointer = 12;
    var payload: [16]u8 = undefined;
    var pos: usize = 0;
    // enter(serial, surface, 100.0, 50.0) in wl_fixed.
    putU32Wire(&payload, &pos, 10);
    putU32Wire(&payload, &pos, 7);
    putI32Wire(&payload, &pos, 100 * 256);
    putI32Wire(&payload, &pos, 50 * 256);
    try win.onEvent(12, 0, payload[0..pos]);
    // motion(time, 110.0, 60.0).
    pos = 0;
    putU32Wire(&payload, &pos, 2000);
    putI32Wire(&payload, &pos, 110 * 256);
    putI32Wire(&payload, &pos, 60 * 256);
    try win.onEvent(12, 2, payload[0..pos]);
    // button left press.
    pos = 0;
    putU32Wire(&payload, &pos, 11);
    putU32Wire(&payload, &pos, 2010);
    putU32Wire(&payload, &pos, 0x110);
    putU32Wire(&payload, &pos, 1);
    try win.onEvent(12, 3, payload[0..pos]);
    // button left release.
    pos = 0;
    putU32Wire(&payload, &pos, 12);
    putU32Wire(&payload, &pos, 2020);
    putU32Wire(&payload, &pos, 0x110);
    putU32Wire(&payload, &pos, 0);
    try win.onEvent(12, 3, payload[0..pos]);
    // leave(serial, surface): CANCEL + focus lost.
    var lp: [8]u8 = undefined;
    pos = 0;
    putU32Wire(&lp, &pos, 13);
    putU32Wire(&lp, &pos, 7);
    try win.onEvent(12, 1, lp[0..pos]);
    try std.testing.expectEqual(@as(usize, 6), win.pendingInputs());
    var out: [48]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 6), n);
    // [0] PTR_FOCUS gained at (100,50).
    try std.testing.expectEqual(@as(i32, 6), out[0]);
    try std.testing.expectEqual(@as(i32, 1), out[1]);
    try std.testing.expectEqual(@as(f32, 100.0), @as(f32, @bitCast(out[2])));
    try std.testing.expectEqual(@as(f32, 50.0), @as(f32, @bitCast(out[3])));
    // [1] MOVE at (110,60), no buttons.
    try std.testing.expectEqual(@as(i32, 2), out[8]);
    try std.testing.expectEqual(@as(i32, 2), out[9]);
    // [2] DOWN with mask-after-press = primary.
    try std.testing.expectEqual(@as(i32, 2), out[16]);
    try std.testing.expectEqual(@as(i32, 0), out[17]);
    try std.testing.expectEqual(@as(i32, 1), out[20]);
    // [3] UP with mask-after-release = 0.
    try std.testing.expectEqual(@as(i32, 2), out[24]);
    try std.testing.expectEqual(@as(i32, 1), out[25]);
    try std.testing.expectEqual(@as(i32, 0), out[28]);
    // [4] CANCEL then [5] PTR_FOCUS lost.
    try std.testing.expectEqual(@as(i32, 2), out[32]);
    try std.testing.expectEqual(@as(i32, 3), out[33]);
    try std.testing.expectEqual(@as(i32, 6), out[40]);
    try std.testing.expectEqual(@as(i32, 0), out[41]);
}

test "axis value120 folds detents into moves on frame" {
    var win = blankInputWindow();
    win.pointer = 12;
    var payload: [16]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 10);
    putU32Wire(&payload, &pos, 7);
    putI32Wire(&payload, &pos, 0);
    putI32Wire(&payload, &pos, 0);
    try win.onEvent(12, 0, payload[0..pos]);
    var drain: [8]i32 = undefined;
    _ = win.takeInput(drain[0..]);
    // One vertical detent.
    var ap: [8]u8 = undefined;
    pos = 0;
    putU32Wire(&ap, &pos, 0); // axis vertical
    putI32Wire(&ap, &pos, 120); // value120 = 1 detent
    try win.onEvent(12, 9, ap[0..pos]);
    try win.onEvent(12, 5, &.{}); // frame
    try std.testing.expectEqual(@as(usize, 1), win.pendingInputs());
    var out: [8]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 1), n);
    try std.testing.expectEqual(@as(i32, 2), out[0]);
    try std.testing.expectEqual(@as(i32, 2), out[1]); // MOVE
}

test "ring drops oldest past 256 and takeInput clears the flag" {
    var win = blankInputWindow();
    var i: u32 = 0;
    while (i < 257) : (i += 1) {
        win.pushEvent(.{ 2, 2, 0, 0, 0, @intCast(i), 0, 0 });
    }
    try std.testing.expectEqual(@as(usize, 256), win.pendingInputs());
    try std.testing.expect(win.inputDropped());
    var out: [8 * 256]i32 = undefined;
    const n = win.takeInput(&out);
    try std.testing.expectEqual(@as(usize, 256), n);
    // Oldest dropped: first event carries time lo = 1.
    try std.testing.expectEqual(@as(i32, 1), out[5]);
    try std.testing.expect(!win.inputDropped());
    try std.testing.expectEqual(@as(usize, 0), win.pendingInputs());
}

test "repeat arms on press and disarms on release" {
    var win = blankInputWindow();
    win.keyboard = 11;
    win.repeat_rate = 25;
    win.repeat_delay = 10_000; // far future: pumpRepeat emits nothing
    var payload: [16]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 100);
    putU32Wire(&payload, &pos, 1000);
    putU32Wire(&payload, &pos, 30); // 'a' repeats in fake map
    putU32Wire(&payload, &pos, 1);
    try win.onEvent(11, 3, payload[0..pos]);
    try std.testing.expect(win.repeat_active);
    try std.testing.expectEqual(@as(u32, 30), win.repeat_key);
    // pumpRepeat before the deadline emits nothing.
    win.pumpRepeat();
    try std.testing.expectEqual(@as(usize, 1), win.pendingInputs());
    // Release disarms.
    pos = 0;
    putU32Wire(&payload, &pos, 101);
    putU32Wire(&payload, &pos, 2000);
    putU32Wire(&payload, &pos, 30);
    putU32Wire(&payload, &pos, 0);
    try win.onEvent(11, 3, payload[0..pos]);
    try std.testing.expect(!win.repeat_active);
}

test "repeat emits down events past the deadline" {
    var win = blankInputWindow();
    win.repeat_active = true;
    win.repeat_key = 30;
    win.repeat_keycode = 29;
    win.repeat_utf32 = 0x61;
    win.repeat_meta = 0;
    win.repeat_rate = 1000; // 1ms period
    win.repeat_next_ms = 0; // long past
    win.pumpRepeat();
    try std.testing.expect(win.pendingInputs() > 0);
    var out: [8 * 8]i32 = undefined;
    const n = win.takeInput(out[0..]);
    try std.testing.expect(n > 0);
    try std.testing.expectEqual(@as(i32, 1), out[0]);
    try std.testing.expectEqual(@as(i32, 0), out[1]); // DOWN
    try std.testing.expectEqual(@as(i32, 29), out[2]);
    try std.testing.expectEqual(@as(i32, 0x61), out[3]);
}

test "repeat does not arm for non-repeating keys or zero rate" {
    var win = blankInputWindow();
    win.keyboard = 11;
    xkb.fakeSetNoRepeat(38); // XKB keycode for evdev 30
    defer xkb.fakeSetNoRepeat(0);
    win.repeat_rate = 25;
    win.repeat_delay = 400;
    var payload: [16]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 100);
    putU32Wire(&payload, &pos, 1000);
    putU32Wire(&payload, &pos, 30);
    putU32Wire(&payload, &pos, 1);
    try win.onEvent(11, 3, payload[0..pos]);
    try std.testing.expect(!win.repeat_active);
    xkb.fakeSetNoRepeat(0);
    win.repeat_rate = 0;
    pos = 0;
    putU32Wire(&payload, &pos, 101);
    putU32Wire(&payload, &pos, 2000);
    putU32Wire(&payload, &pos, 30);
    putU32Wire(&payload, &pos, 1);
    try win.onEvent(11, 3, payload[0..pos]);
    try std.testing.expect(!win.repeat_active);
}

test "keymap with bad format is ignored" {
    var win = blankInputWindow();
    win.keyboard = 11;
    // No fd queued: onKeymap without an fd is a no-op, not an error.
    var payload: [8]u8 = undefined;
    var pos: usize = 0;
    putU32Wire(&payload, &pos, 0); // format no_keymap
    putU32Wire(&payload, &pos, 16);
    try win.onEvent(11, 0, payload[0..pos]);
    try std.testing.expect(!win.have_pending_keymap);
    try win.consumeKeymap();
    try std.testing.expect(win.xkb_state == null);
}
