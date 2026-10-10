// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.contentcapture;

import android.view.ViewStructure;

/** Content-capture sink: notify shells only. */
public class ContentCaptureSession {
    public void notifyViewAppeared(long sessionId, ViewStructure node) {}
    public void notifyViewDisappeared(long sessionId, long autofillIdValue) {}
    public void notifyViewTextChanged(long sessionId, long autofillIdValue, CharSequence text) {}
    public void notifySessionResumed() {}
    public void notifySessionPaused() {}
}
