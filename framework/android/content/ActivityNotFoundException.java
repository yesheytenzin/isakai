// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/** Missing-activity failure. */
public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException() {
        super();
    }

    public ActivityNotFoundException(String message) {
        super(message);
    }
}
