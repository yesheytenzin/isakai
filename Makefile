# Isakai packaging Makefile: thin wrapper over `zig build` with a
# PREFIX/DESTDIR-aware install to FHS paths.
#
#   make build      # ReleaseSafe binaries + framework + policy into zig-out/
#   sudo make install                  # PREFIX defaults to /usr
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
STAGE ?= .stage-install

POLICY_SRC = $(STAGE)/share/polkit-1/actions/id.isakai.policy
POLICY_DST = $(DESTDIR)/usr/share/polkit-1/actions/id.isakai.policy

.PHONY: build install uninstall clean

build:
	zig build -Doptimize=ReleaseSafe

install:
ifeq ($(INSTALL_POLICY),1)
	@if [ "$(PREFIX)" != /usr ]; then \
		echo "error: polkit policy pins wrapper paths under /usr/bin; refusing PREFIX=$(PREFIX) (use PREFIX=/usr, or INSTALL_POLICY=0 to skip the policy)"; \
		exit 1; \
	fi
endif
	# DESTDIR belongs to the FHS copy below, not Zig's private staging build.
	env -u DESTDIR zig build -Doptimize=ReleaseSafe --prefix $(STAGE)
	install -D -m755 $(STAGE)/bin/isakai $(DESTDIR)$(PREFIX)/bin/isakai
	install -D -m755 $(STAGE)/bin/isakai-dbus-bridge $(DESTDIR)$(PREFIX)/bin/isakai-dbus-bridge
	install -D -m755 $(STAGE)/bin/isakai-allocate-uid $(DESTDIR)$(PREFIX)/bin/isakai-allocate-uid
	install -D -m755 $(STAGE)/bin/isakai-remove-uid $(DESTDIR)$(PREFIX)/bin/isakai-remove-uid
	install -D -m755 $(STAGE)/bin/isakai-run-app $(DESTDIR)$(PREFIX)/bin/isakai-run-app
	mkdir -p $(DESTDIR)$(PREFIX)/share/isakai
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
	rm -rf zig-out .zig-cache $(STAGE)
