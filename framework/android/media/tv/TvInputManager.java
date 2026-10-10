// SPDX-License-Identifier: GPL-3.0-or-later
package android.media.tv;

import java.util.Collections;
import java.util.List;

/** TV inputs; empty shell. */
public class TvInputManager {
    public static final String ACTION_QUERY_CONTENT_RATING_SYSTEMS = "android.media.tv.action.QUERY_CONTENT_RATING_SYSTEMS";
    public static final int VIDEO_UNAVAILABLE_REASON_UNKNOWN = 0;
    public List getTvInputList() { return Collections.emptyList(); }
    public Object getTvInputInfo(String inputId) { return null; }
    public int getInputState(String inputId) { return 0; }
    public void registerCallback(TvInputCallback callback, android.os.Handler handler) { }
    public void unregisterCallback(TvInputCallback callback) { }
    public abstract static class TvInputCallback {
        public void onInputStateChanged(String inputId, int state) { }
        public void onInputAdded(String inputId) { }
        public void onInputRemoved(String inputId) { }
    }
}
