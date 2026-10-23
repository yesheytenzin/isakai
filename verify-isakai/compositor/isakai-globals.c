// isakai-globals: list all compositor globals to confirm input protocols.
#include <stdio.h>
#include <string.h>
#include <wayland-client.h>
static void reg_global(void *d, struct wl_registry *r, uint32_t name,
                       const char *iface, uint32_t ver) {
    printf("%u %s v%u\n", name, iface, ver);
}
static void reg_remove(void *d, struct wl_registry *r, uint32_t n) {}
static const struct wl_registry_listener reg_listener = {reg_global, reg_remove};
int main() {
    struct wl_display *dp = wl_display_connect(NULL);
    if (!dp) { fprintf(stderr, "no display\n"); return 1; }
    struct wl_registry *reg = wl_display_get_registry(dp);
    wl_registry_add_listener(reg, &reg_listener, NULL);
    wl_display_roundtrip(dp);
    wl_display_roundtrip(dp);
    return 0;
}
