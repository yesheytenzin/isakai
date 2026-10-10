// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

/** Start/stop animation contract. */
public interface Animatable {
    void start();
    void stop();
    boolean isRunning();
}
