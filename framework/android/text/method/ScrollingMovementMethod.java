// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

/** Scrolling movement: scrolls on navigation (headless: no-op). */
public class ScrollingMovementMethod extends BaseMovementMethod {
    private static ScrollingMovementMethod instance;

    public static MovementMethod getInstance() {
        if (instance == null) instance = new ScrollingMovementMethod();
        return instance;
    }
}
