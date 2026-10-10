// SPDX-License-Identifier: GPL-3.0-or-later
package android.media.session;

import android.content.ComponentName;
import java.util.Collections;
import java.util.List;

/** Media sessions; empty shell. */
public class MediaSessionManager {
    public List getActiveSessions(ComponentName notificationListener) { return Collections.emptyList(); }
    public void addOnActiveSessionsChangedListener(SessionListener listener, ComponentName notificationListener) { }
    public void removeOnActiveSessionsChangedListener(SessionListener listener) { }
    public interface SessionListener {
        void onActiveSessionsChanged(List controllers);
    }
}
