// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

/** Arrow-key movement: cursor keys move selection (headless: no-op). */
public class ArrowKeyMovementMethod extends BaseMovementMethod {
    private static ArrowKeyMovementMethod instance;

    public static MovementMethod getInstance() {
        if (instance == null) instance = new ArrowKeyMovementMethod();
        return instance;
    }
}
