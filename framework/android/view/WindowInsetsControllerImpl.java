// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * No-op controller: tracks the requested system-bars appearance bits so
 * compat getters read back what setters wrote. Nothing is displayed and
 * the controllable insets never change, so listeners are never notified.
 */
final class WindowInsetsControllerImpl implements WindowInsetsController {
    private int appearance;

    @Override
    public void show(int types) {}

    @Override
    public void hide(int types) {}

    @Override
    public int getSystemBarsAppearance() { return appearance; }

    @Override
    public void setSystemBarsAppearance(int appearance, int mask) {
        this.appearance = (this.appearance & ~mask) | (appearance & mask);
    }

    @Override
    public void setSystemBarsBehavior(int behavior) {}

    @Override
    public void addOnControllableInsetsChangedListener(OnControllableInsetsChangedListener listener) {}

    @Override
    public void removeOnControllableInsetsChangedListener(OnControllableInsetsChangedListener listener) {}
}
