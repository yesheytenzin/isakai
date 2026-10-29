# Isakai packaging Makefile: thin wrapper over `zig build` with a
# PREFIX/DESTDIR-aware install to FHS paths.
#
#   make build      # ReleaseSafe binaries + framework + policy into zig-out/
#   make install                       # builds as user, copies with sudo
#   sudo make install DESTDIR=pkg PREFIX=/usr   # packaging
#   sudo make uninstall
#
# Layout: $(DESTDIR)$(PREFIX)/bin/{isakai,isakai-dbus-bridge,isakai-allocate-uid,isakai-remove-uid,isakai-run-app},
# $(DESTDIR)$(PREFIX)/share/isakai/framework (classes +
# lib/libisakai_jni.so), $(DESTDIR)/usr/share/polkit-1/actions/*.policy.
#
# The polkit policy pins org.freedesktop.policykit.exec.path to
# /usr/bin/isakai-{allocate-uid,remove-uid,run-app}, so install refuses any PREFIX other than /usr unless
# INSTALL_POLICY=0 skips the policy (dev relocatable installs).

PREFIX ?= /usr
DESTDIR ?=
INSTALL_POLICY ?= 1
ZIG ?= zig
BUILD_CACHE ?= .cache/isakai/build-cache
STAGE ?= .stage-install

POLICY_SRC = $(STAGE)/share/polkit-1/actions/id.isakai.policy
POLICY_DST = $(DESTDIR)/usr/share/polkit-1/actions/id.isakai.policy

# One list feeds both install targets so they cannot drift apart.
INSTALL_BINS := isakai isakai-dbus-bridge isakai-allocate-uid isakai-remove-uid isakai-run-app isakai-bionic-exec

.PHONY: build install uninstall clean

build:
	$(ZIG) build --cache-dir $(BUILD_CACHE) -Doptimize=ReleaseSafe

install:
ifeq ($(INSTALL_POLICY),1)
	@if [ "$(PREFIX)" != /usr ]; then \
		echo "error: polkit policy pins wrapper paths under /usr/bin; refusing PREFIX=$(PREFIX) (use PREFIX=/usr, or INSTALL_POLICY=0 to skip the policy)"; \
		exit 1; \
	fi
endif
	# Build before elevation so mise-managed tools and the user's cache work.
	env -u DESTDIR $(ZIG) build --cache-dir $(BUILD_CACHE) -Doptimize=ReleaseSafe --prefix $(STAGE)
	@if [ "$$(id -u)" -eq 0 ] || [ -n "$(DESTDIR)" ]; then \
		$(MAKE) install-files; \
	else \
		sudo $(MAKE) install-files PREFIX="$(PREFIX)" STAGE="$(STAGE)" INSTALL_POLICY="$(INSTALL_POLICY)"; \
	fi

# Copy-only install for a prebuilt staging tree (never invokes zig, so it
# works where the compiler cache is unavailable to root). Build first with:
#   zig build -Doptimize=ReleaseSafe --prefix $(STAGE)
install-files:
	for b in $(INSTALL_BINS); do install -D -m755 $(STAGE)/bin/$$b $(DESTDIR)$(PREFIX)/bin/$$b; done
	mkdir -p $(DESTDIR)$(PREFIX)/share/isakai
	rm -rf $(DESTDIR)$(PREFIX)/share/isakai/framework
	cp -r $(STAGE)/framework $(DESTDIR)$(PREFIX)/share/isakai/framework
ifeq ($(INSTALL_POLICY),1)
	install -D -m644 $(POLICY_SRC) $(POLICY_DST)
	rm -f $(DESTDIR)/usr/share/polkit-1/actions/id.isakai.allocate-uid.policy
endif

uninstall:
	rm -f $(DESTDIR)$(PREFIX)/bin/isakai $(DESTDIR)$(PREFIX)/bin/isakai-dbus-bridge
	rm -f $(DESTDIR)$(PREFIX)/bin/isakai-allocate-uid $(DESTDIR)$(PREFIX)/bin/isakai-remove-uid $(DESTDIR)$(PREFIX)/bin/isakai-run-app
	rm -rf $(DESTDIR)$(PREFIX)/share/isakai
	rm -f $(DESTDIR)/usr/share/polkit-1/actions/id.isakai.policy $(DESTDIR)/usr/share/polkit-1/actions/id.isakai.allocate-uid.policy

clean:
	rm -rf zig-out .zig-cache $(BUILD_CACHE) $(STAGE)
