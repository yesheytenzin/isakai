// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Recording canvas shell: accepts draws, replays nothing. */
public class RecordingCanvas extends Canvas {
    public RecordingCanvas() {
        super(1, 1);
    }
}
