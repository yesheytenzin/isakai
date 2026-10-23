// isakai-wev: minimal seat/pointer/keyboard event logger for the isolated compositor.
// Prints pointer motion/button and keyboard key events for N seconds.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <wayland-client.h>

static int seconds = 10;

static void pointer_enter(void *d, struct wl_pointer *p, uint32_t s,
                          struct wl_surface *surf, int32_t x, int32_t y) {
    printf("POINTER enter surface=%p x=%d y=%d\n", (void*)surf, x, y); fflush(stdout);
}
static void pointer_leave(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *surf) {
    printf("POINTER leave\n"); fflush(stdout);
}
static void pointer_motion(void *d, struct wl_pointer *p, uint32_t t, int32_t x, int32_t y) {
    printf("POINTER motion x=%d y=%d\n", x, y); fflush(stdout);
}
static void pointer_button(void *d, struct wl_pointer *p, uint32_t s, uint32_t t,
                           uint32_t b, uint32_t st) {
    printf("POINTER button=%u state=%u\n", b, st); fflush(stdout);
}
static void pointer_axis(void *d, struct wl_pointer *p, uint32_t t, uint32_t a, int32_t v) {}
static void pointer_frame(void *d, struct wl_pointer *p) {}
static void pointer_axis_source(void *d, struct wl_pointer *p, uint32_t s) {}
static void pointer_axis_stop(void *d, struct wl_pointer *p, uint32_t t, uint32_t a) {}
static void pointer_axis_discrete(void *d, struct wl_pointer *p, uint32_t a, int32_t d2) {}
static const struct wl_pointer_listener pointer_listener = {
    pointer_enter, pointer_leave, pointer_motion, pointer_button,
    pointer_axis, pointer_frame, pointer_axis_source, pointer_axis_stop, pointer_axis_discrete
};

static void keymap(void *d, struct wl_keyboard *k, uint32_t f, int32_t fd, uint32_t sz) { close(fd); }
static void kb_enter(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *surf, struct wl_array *keys) {
    printf("KEYBOARD enter surface=%p\n", (void*)surf); fflush(stdout);
}
static void kb_leave(void *d, struct wl_keyboard *k, uint32_t s, struct wl_surface *surf) {
    printf("KEYBOARD leave\n"); fflush(stdout);
}
static void kb_key(void *d, struct wl_keyboard *k, uint32_t s, uint32_t t, uint32_t key, uint32_t st) {
    printf("KEYBOARD key=%u state=%u\n", key, st); fflush(stdout);
}
static void kb_mods(void *d, struct wl_keyboard *k, uint32_t s, uint32_t md, uint32_t ml, uint32_t lk, uint32_t g) {}
static void kb_repeat(void *d, struct wl_keyboard *k, int32_t r, int32_t d2) {}
static const struct wl_keyboard_listener kb_listener = {
    keymap, kb_enter, kb_leave, kb_key, kb_mods, kb_repeat
};

static struct wl_seat *seat;
static struct wl_pointer *pointer;
static struct wl_keyboard *keyboard;
static uint32_t caps = 0;

static void seat_caps(void *d, struct wl_seat *s, uint32_t c) {
    caps = c;
    printf("SEAT caps=%u (pointer=%d keyboard=%d)\n", c, !!(c & 1), !!(c & 2)); fflush(stdout);
    if ((c & 1) && !pointer) {
        pointer = wl_seat_get_pointer(s);
        wl_pointer_add_listener(pointer, &pointer_listener, NULL);
    }
    if ((c & 2) && !keyboard) {
        keyboard = wl_seat_get_keyboard(s);
        wl_keyboard_add_listener(keyboard, &kb_listener, NULL);
    }
}
static void seat_name(void *d, struct wl_seat *s, const char *n) {
    printf("SEAT name=%s\n", n); fflush(stdout);
}
static const struct wl_seat_listener seat_listener = {seat_caps, seat_name};

static void reg_global(void *d, struct wl_registry *r, uint32_t name, const char *iface, uint32_t ver) {
    if (!strcmp(iface, wl_seat_interface.name))
        seat = wl_registry_bind(r, name, &wl_seat_interface, ver > 7 ? 7 : ver);
}
static void reg_remove(void *d, struct wl_registry *r, uint32_t n) {}
static const struct wl_registry_listener reg_listener = {reg_global, reg_remove};

int main(int argc, char **argv) {
    if (argc > 1) seconds = atoi(argv[1]);
    struct wl_display *dp = wl_display_connect(NULL);
    if (!dp) { fprintf(stderr, "no display\n"); return 1; }
    struct wl_registry *reg = wl_display_get_registry(dp);
    wl_registry_add_listener(reg, &reg_listener, NULL);
    wl_display_roundtrip(dp);
    if (!seat) { fprintf(stderr, "no seat\n"); return 1; }
    wl_seat_add_listener(seat, &seat_listener, NULL);
    wl_display_roundtrip(dp);
    time_t end = time(NULL) + seconds;
    while (time(NULL) < end) {
        wl_display_dispatch_pending(dp);
        wl_display_flush(dp);
        usleep(50000);
    }
    printf("DONE caps=%u\n", caps);
    return 0;
}
