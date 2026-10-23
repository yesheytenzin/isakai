// isakai-vptr: minimal wlr-virtual-pointer client for the isolated compositor.
// Usage: isakai-vptr abs X Y [X Y ...]        -- move(s), then frame+flush each
//        isakai-vptr click X Y [hold_ms]       -- move, button down, hold, up
//        isakai-vptr down X Y | up             -- split press/release
//        isakai-vptr move X Y                  -- single absolute move
// Coords are compositor-global pixels on the 1000x1000 headless output.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <wayland-client.h>
#include "wlr-virtual-pointer-unstable-v1-client-protocol.h"

static uint32_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

struct ctx {
    struct wl_display *display;
    struct wl_registry *registry;
    struct wl_seat *seat;
    struct zwlr_virtual_pointer_manager_v1 *mgr;
    struct zwlr_virtual_pointer_v1 *ptr;
};

static void registry_global(void *data, struct wl_registry *r, uint32_t name,
                            const char *iface, uint32_t version) {
    struct ctx *c = data;
    if (strcmp(iface, wl_seat_interface.name) == 0) {
        c->seat = wl_registry_bind(r, name, &wl_seat_interface, 1);
    } else if (strcmp(iface, zwlr_virtual_pointer_manager_v1_interface.name) == 0) {
        c->mgr = wl_registry_bind(r, name, &zwlr_virtual_pointer_manager_v1_interface, 1);
    }
}
static void registry_remove(void *d, struct wl_registry *r, uint32_t n) {}
static const struct wl_registry_listener reg_listener = {registry_global, registry_remove};

#define EXTENT 1000
#define BTN_LEFT 0x110

static void move_abs(struct ctx *c, int x, int y) {
    if (x < 0) x = 0; if (y < 0) y = 0;
    if (x > EXTENT - 1) x = EXTENT - 1; if (y > EXTENT - 1) y = EXTENT - 1;
    zwlr_virtual_pointer_v1_motion_absolute(c->ptr, now_ms(), (uint32_t)x, (uint32_t)y, EXTENT, EXTENT);
    zwlr_virtual_pointer_v1_frame(c->ptr);
    wl_display_flush(c->display);
    wl_display_roundtrip(c->display);
}

static void button(struct ctx *c, uint32_t state, int hold_ms) {
    zwlr_virtual_pointer_v1_button(c->ptr, now_ms(), BTN_LEFT, state);
    zwlr_virtual_pointer_v1_frame(c->ptr);
    wl_display_flush(c->display);
    wl_display_roundtrip(c->display);
    if (state == 1 && hold_ms > 0) usleep((useconds_t)hold_ms * 1000);
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: isakai-vptr abs|move|click|down|up ...\n"); return 2; }
    struct ctx c = {0};
    c.display = wl_display_connect(NULL);
    if (!c.display) { fprintf(stderr, "no wayland display\n"); return 1; }
    c.registry = wl_display_get_registry(c.display);
    wl_registry_add_listener(c.registry, &reg_listener, &c);
    wl_display_roundtrip(c.display);
    if (!c.mgr) { fprintf(stderr, "no virtual-pointer manager\n"); return 1; }
    // seat may be NULL; manager accepts null seat
    c.ptr = zwlr_virtual_pointer_manager_v1_create_virtual_pointer(c.mgr, c.seat);
    if (!c.ptr) { fprintf(stderr, "create_virtual_pointer failed\n"); return 1; }
    wl_display_roundtrip(c.display);

    const char *cmd = argv[1];
    if (!strcmp(cmd, "hold") && argc == 5) {
        move_abs(&c, atoi(argv[2]), atoi(argv[3]));
        sleep((unsigned)atoi(argv[4]));
    } else if (!strcmp(cmd, "move") && argc == 4) {
        move_abs(&c, atoi(argv[2]), atoi(argv[3]));
    } else if (!strcmp(cmd, "abs")) {
        for (int i = 2; i + 1 < argc; i += 2)
            move_abs(&c, atoi(argv[i]), atoi(argv[i + 1]));
    } else if (!strcmp(cmd, "click") && (argc == 4 || argc == 5)) {
        int hold = argc == 5 ? atoi(argv[4]) : 80;
        move_abs(&c, atoi(argv[2]), atoi(argv[3]));
        usleep(120 * 1000);
        button(&c, 1, hold);
        button(&c, 0, 0);
    } else if (!strcmp(cmd, "down") && argc == 4) {
        move_abs(&c, atoi(argv[2]), atoi(argv[3]));
        usleep(120 * 1000);
        button(&c, 1, 0);
        // hold the connection briefly so release can be a separate call is NOT
        // possible; down keeps pressed only within this process. Instead sleep
        // 60s max is wrong; just return pressed and disconnect = release.
        // For split semantics the caller must keep us alive: sleep 3600.
        pause();
    } else if (!strcmp(cmd, "up")) {
        button(&c, 0, 0);
    } else {
        fprintf(stderr, "bad args\n");
        return 2;
    }
    zwlr_virtual_pointer_v1_destroy(c.ptr);
    wl_display_disconnect(c.display);
    return 0;
}
