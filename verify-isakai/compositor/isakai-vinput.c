// isakai-vinput: persistent virtual pointer + keyboard for the isolated compositor.
// Commands on stdin (one per line):
//   move X Y            absolute pointer move (compositor-global px)
//   click X Y [HOLD_MS] move + press + optional hold + release
//   down X Y            move + press, stay pressed
//   up                  release
//   type TEXT           type text via virtual keyboard (xkb keysyms, best effort)
//   key KEYSYM          single keysym press+release (decimal keysym or name subset)
//   quit                exit 0
// Replies "ok" or "error <msg>" per line on stdout.
#define _POSIX_C_SOURCE 200809L
#define _DEFAULT_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>
#include <ctype.h>
#include <sys/mman.h>
#include <wayland-client.h>
#include <xkbcommon/xkbcommon.h>
#include "wlr-virtual-pointer-unstable-v1-client-protocol.h"

extern const struct wl_interface zwp_virtual_keyboard_manager_v1_interface;
extern const struct wl_interface zwp_virtual_keyboard_v1_interface;

static uint32_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

struct ctx {
    struct wl_display *display;
    struct wl_seat *seat;
    struct zwlr_virtual_pointer_manager_v1 *ptr_mgr;
    struct zwlr_virtual_pointer_v1 *ptr;
    struct wl_registry *reg_vkbd_mgr;
    void *vkbd_mgr; // struct zwp_virtual_keyboard_manager_v1 *
    void *vkbd;     // struct zwp_virtual_keyboard_v1 *
    struct xkb_context *xkb;
    struct xkb_keymap *keymap;
};

static void reg_global(void *data, struct wl_registry *r, uint32_t name,
                       const char *iface, uint32_t version) {
    struct ctx *c = data;
    if (!strcmp(iface, wl_seat_interface.name)) {
        c->seat = wl_registry_bind(r, name, &wl_seat_interface, 1);
    } else if (!strcmp(iface, zwlr_virtual_pointer_manager_v1_interface.name)) {
        c->ptr_mgr = wl_registry_bind(r, name, &zwlr_virtual_pointer_manager_v1_interface, 1);
    } else if (!strcmp(iface, zwp_virtual_keyboard_manager_v1_interface.name)) {
        c->reg_vkbd_mgr = r;
        c->vkbd_mgr = wl_registry_bind(r, name, &zwp_virtual_keyboard_manager_v1_interface, 1);
    }
}
static void reg_remove(void *d, struct wl_registry *r, uint32_t n) {}
static const struct wl_registry_listener reg_listener = {reg_global, reg_remove};

// virtual keyboard requests (v1): create_virtual_keyboard(seat, id)
static void *create_virtual_keyboard(void *mgr, struct wl_seat *seat) {
    // marshal_constructor: interface, then request args in order (seat, NULL=new_id)
    struct wl_proxy *id = wl_proxy_marshal_constructor(
        (struct wl_proxy *)mgr, 0, &zwp_virtual_keyboard_v1_interface, seat, NULL);
    return id;
}
// keymap(format=1 xkb, fd, size), key(time, key, state), modifiers(...), destroy
static void vkbd_keymap(void *kbd, int fd, uint32_t size) {
    wl_proxy_marshal((struct wl_proxy *)kbd, 0, (uint32_t)1, fd, size);
}
static void vkbd_key(void *kbd, uint32_t time, uint32_t key, uint32_t state) {
    wl_proxy_marshal((struct wl_proxy *)kbd, 1, time, key, state);
}
static void vkbd_modifiers(void *kbd, uint32_t depressed, uint32_t latched,
                           uint32_t locked, uint32_t group) {
    wl_proxy_marshal((struct wl_proxy *)kbd, 2, depressed, latched, locked, group);
}

#define EXTENT 1000
#define BTN_LEFT 0x110

static void move_abs(struct ctx *c, int x, int y) {
    if (x < 0) x = 0; if (y < 0) y = 0;
    if (x > EXTENT - 1) x = EXTENT - 1; if (y > EXTENT - 1) y = EXTENT - 1;
    zwlr_virtual_pointer_v1_motion_absolute(c->ptr, now_ms(), (uint32_t)x, (uint32_t)y, EXTENT, EXTENT);
    zwlr_virtual_pointer_v1_frame(c->ptr);
    wl_display_flush(c->display);
}

static void button(struct ctx *c, uint32_t state) {
    zwlr_virtual_pointer_v1_button(c->ptr, now_ms(), BTN_LEFT, state);
    zwlr_virtual_pointer_v1_frame(c->ptr);
    wl_display_flush(c->display);
}

// Minimal keysym map for calculator flows: digits, + - * / . = ( ) BackSpace Enter Escape
static uint32_t keysym_for(const char *name, int *need_shift) {
    *need_shift = 0;
    if (!strcmp(name, "Return") || !strcmp(name, "Enter")) return 0xff0d;
    if (!strcmp(name, "Escape")) return 0xff1b;
    if (!strcmp(name, "BackSpace")) return 0xff08;
    if (!strcmp(name, "space")) return 0x20;
    if (!strcmp(name, "plus")) return 0x2b;
    if (!strcmp(name, "minus")) return 0x2d;
    if (!strcmp(name, "asterisk")) return 0x2a;
    if (!strcmp(name, "slash")) return 0x2f;
    if (!strcmp(name, "period")) return 0x2e;
    if (!strcmp(name, "comma")) return 0x2c;
    if (!strcmp(name, "equal")) return 0x3d;
    if (!strcmp(name, "parenleft")) return 0x28;
    if (!strcmp(name, "parenright")) return 0x29;
    if (strlen(name) == 1) {
        unsigned char ch = name[0];
        if (ch >= '0' && ch <= '9') return ch;
        if (ch >= 'a' && ch <= 'z') return ch;
        if (ch >= 'A' && ch <= 'Z') { *need_shift = 1; return ch + 32; }
        switch (ch) {
        case '+': *need_shift = 1; return 0x3d; // shift+=+ on us layout
        case '=': return 0x3d;
        case '-': return 0x2d;
        case '*': *need_shift = 1; return 0x38;
        case '/': return 0x2f;
        case '.': return 0x2e;
        case ',': return 0x2c;
        case '(': *need_shift = 1; return 0x39;
        case ')': *need_shift = 1; return 0x30;
        case ' ': return 0x20;
        }
    }
    return 0;
}

static int setup_keyboard(struct ctx *c) {
    if (!c->vkbd_mgr || !c->seat) return -1;
    c->vkbd = create_virtual_keyboard(c->vkbd_mgr, c->seat);
    if (!c->vkbd) return -1;
    c->xkb = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
    struct xkb_rule_names names = {.rules = "evdev", .model = "pc105",
                                   .layout = "us", .variant = "", .options = ""};
    c->keymap = xkb_keymap_new_from_names(c->xkb, &names, XKB_KEYMAP_COMPILE_NO_FLAGS);
    if (!c->keymap) return -1;
    char *str = xkb_keymap_get_as_string(c->keymap, XKB_KEYMAP_USE_ORIGINAL_FORMAT);
    size_t len = strlen(str) + 1;
    char tmp[] = "/tmp/isakai-vkbd-XXXXXX";
    int fd = mkstemp(tmp);
    if (fd < 0) return -1;
    unlink(tmp);
    if (write(fd, str, len) != (ssize_t)len) { close(fd); return -1; }
    free(str);
    vkbd_keymap(c->vkbd, fd, len);
    close(fd);
    wl_display_flush(c->display);
    return 0;
}

static int keycode_for_keysym(struct ctx *c, uint32_t keysym) {
    // search keycodes 8..255 for one producing keysym at level 0
    struct xkb_state *st = xkb_state_new(c->keymap);
    for (xkb_keycode_t kc = 8; kc < 256; kc++) {
        const xkb_keysym_t *syms = NULL;
        int n = xkb_state_key_get_syms(st, kc, &syms);
        for (int i = 0; i < n; i++)
            if (syms[i] == keysym) { xkb_state_unref(st); return (int)kc; }
    }
    xkb_state_unref(st);
    return -1;
}

static int press_keysym(struct ctx *c, uint32_t keysym, int need_shift) {
    int kc = keycode_for_keysym(c, keysym);
    if (kc < 0) return -1;
    int shift_kc = -1;
    if (need_shift) {
        shift_kc = keycode_for_keysym(c, 0xffe1); // Shift_L
        if (shift_kc < 0) return -1;
        vkbd_modifiers(c->vkbd, 1, 0, 0, 0);
        vkbd_key(c->vkbd, now_ms(), (uint32_t)(shift_kc - 8), 1);
        wl_display_flush(c->display);
        usleep(15000);
    }
    // evdev keycode = xkb keycode - 8
    vkbd_key(c->vkbd, now_ms(), (uint32_t)(kc - 8), 1);
    wl_display_flush(c->display);
    usleep(15000);
    vkbd_key(c->vkbd, now_ms(), (uint32_t)(kc - 8), 0);
    wl_display_flush(c->display);
    usleep(15000);
    if (need_shift) {
        vkbd_key(c->vkbd, now_ms(), (uint32_t)(shift_kc - 8), 0);
        vkbd_modifiers(c->vkbd, 0, 0, 0, 0);
        wl_display_flush(c->display);
        usleep(15000);
    }
    return 0;
}

static int press_ctrl_chord(struct ctx *c, uint32_t keysym) {
    int kc = keycode_for_keysym(c, keysym);
    int ctrl_kc = keycode_for_keysym(c, 0xffe3); // Control_L
    if (kc < 0 || ctrl_kc < 0) return -1;
    vkbd_modifiers(c->vkbd, 4, 0, 0, 0);
    vkbd_key(c->vkbd, now_ms(), (uint32_t)(ctrl_kc - 8), 1);
    wl_display_flush(c->display);
    usleep(15000);
    vkbd_key(c->vkbd, now_ms(), (uint32_t)(kc - 8), 1);
    wl_display_flush(c->display);
    usleep(15000);
    vkbd_key(c->vkbd, now_ms(), (uint32_t)(kc - 8), 0);
    vkbd_key(c->vkbd, now_ms(), (uint32_t)(ctrl_kc - 8), 0);
    vkbd_modifiers(c->vkbd, 0, 0, 0, 0);
    wl_display_flush(c->display);
    usleep(15000);
    return 0;
}

int main() {
    struct ctx c = {0};
    c.display = wl_display_connect(NULL);
    if (!c.display) { fprintf(stderr, "no wayland display\n"); return 1; }
    struct wl_registry *reg = wl_display_get_registry(c.display);
    wl_registry_add_listener(reg, &reg_listener, &c);
    wl_display_roundtrip(c.display);
    if (!c.ptr_mgr) { fprintf(stderr, "no virtual-pointer manager\n"); return 1; }
    c.ptr = zwlr_virtual_pointer_manager_v1_create_virtual_pointer(c.ptr_mgr, c.seat);
    if (!c.ptr) { fprintf(stderr, "create_virtual_pointer failed\n"); return 1; }
    wl_display_roundtrip(c.display);
    int have_kbd = setup_keyboard(&c) == 0;
    fprintf(stderr, "vinput ready kbd=%d\n", have_kbd);
    setvbuf(stdout, NULL, _IONBF, 0);

    char *line = NULL;
    size_t cap = 0;
    ssize_t n;
    while ((n = getline(&line, &cap, stdin)) > 0) {
        // strip trailing newline (but type text may contain leading spaces)
        while (n > 0 && (line[n-1] == '\n' || line[n-1] == '\r')) line[--n] = 0;
        if (!n) { printf("ok\n"); continue; }
        char *sp = strchr(line, ' ');
        size_t cmdlen = sp ? (size_t)(sp - line) : strlen(line);
        char *arg = sp ? sp + 1 : "";
        if (!strncmp(line, "quit", cmdlen) && cmdlen == 4) {
            printf("ok\n"); break;
        } else if (!strncmp(line, "move", cmdlen) && cmdlen == 4) {
            int x, y;
            if (sscanf(arg, "%d %d", &x, &y) != 2) { printf("error bad move\n"); continue; }
            move_abs(&c, x, y);
            usleep(60000);
            printf("ok\n");
        } else if (!strncmp(line, "click", cmdlen) && cmdlen == 5) {
            int x, y, hold = 80;
            if (sscanf(arg, "%d %d %d", &x, &y, &hold) < 2) { printf("error bad click\n"); continue; }
            move_abs(&c, x, y);
            usleep(120000);
            button(&c, 1);
            if (hold > 0) usleep((useconds_t)hold * 1000);
            button(&c, 0);
            usleep(60000);
            printf("ok\n");
        } else if (!strncmp(line, "down", cmdlen) && cmdlen == 4) {
            int x, y;
            if (sscanf(arg, "%d %d", &x, &y) != 2) { printf("error bad down\n"); continue; }
            move_abs(&c, x, y);
            usleep(120000);
            button(&c, 1);
            printf("ok\n");
        } else if (!strncmp(line, "up", cmdlen) && cmdlen == 2) {
            button(&c, 0);
            printf("ok\n");
        } else if (!strncmp(line, "key", cmdlen) && cmdlen == 3) {
            if (!have_kbd) { printf("error no keyboard\n"); continue; }
            uint32_t sym;
            int shift = 0;
            if (!strncmp(arg, "CTRL+", 5) && arg[5] != 0) {
                int chord_shift = 0;
                sym = keysym_for(arg + 5, &chord_shift);
                if (!sym || press_ctrl_chord(&c, sym)) {
                    printf("error no keycode\n");
                    continue;
                }
                usleep(30000);
                printf("ok\n");
                continue;
            } else if (arg[0] >= '0' && arg[0] <= '9' && !strchr(arg, ' ') && strlen(arg) > 1)
                sym = (uint32_t)strtoul(arg, NULL, 0);
            else
                sym = keysym_for(arg, &shift);
            if (!sym) { printf("error unknown key %s\n", arg); continue; }
            if (press_keysym(&c, sym, shift)) { printf("error no keycode\n"); continue; }
            usleep(30000);
            printf("ok\n");
        } else if (!strncmp(line, "type", cmdlen) && cmdlen == 4) {
            if (!have_kbd) { printf("error no keyboard\n"); continue; }
            // type takes the raw remainder (may start with space): re-derive
            const char *text = line + 4;
            if (*text == ' ') text++;
            int failed = 0;
            for (const unsigned char *p = (const unsigned char *)text; *p; p++) {
                char one[2] = {(char)*p, 0};
                int shift = 0;
                uint32_t sym = keysym_for(one, &shift);
                if (!sym) { failed = 1; break; }
                if (press_keysym(&c, sym, shift)) { failed = 1; break; }
                usleep(20000);
            }
            printf(failed ? "error untypable\n" : "ok\n");
        } else {
            printf("error unknown command\n");
        }
    }
    free(line);
    return 0;
}
