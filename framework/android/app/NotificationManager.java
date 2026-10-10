// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import java.util.HashMap;
import java.util.Map;
import org.isakai.bridge.IsakaiDesktop;

/** Desktop notification manager: channels map to urgency, posts map tag:id to platform ids. */
public class NotificationManager {
    private final String packageName;
    private final Map<String, NotificationChannel> channels = new HashMap<String, NotificationChannel>();
    private final Map<String, Long> posted = new HashMap<String, Long>();

    /** Manager for {@code packageName} (used as the desktop app name). */
    public NotificationManager(String packageName) {
        this.packageName = packageName;
    }

    /** Register (or replace) a channel; unknown channels post to "misc" at DEFAULT. */
    public synchronized void createNotificationChannel(NotificationChannel channel) {
        channels.put(channel.getId(), channel);
    }

    /** Post {@code notification} under integer {@code id}. */
    public synchronized void notify(int id, Notification notification) {
        notify(null, id, notification);
    }

    /** Post {@code notification} under {@code tag}:{@code id}. */
    public synchronized void notify(String tag, int id, Notification notification) {
        String key = key(tag, id);
        String channelId = notification.getChannelId();
        NotificationChannel ch = channelId != null ? channels.get(channelId) : null;
        if (ch == null) {
            ch = channels.get("misc");
            if (ch == null) {
                ch = new NotificationChannel("misc", "misc",
                        NotificationChannel.IMPORTANCE_DEFAULT);
                channels.put("misc", ch);
            }
            channelId = "misc";
        }
        int urgency = urgency(ch.getImportance());
        String title = notification.getTitle() != null
                ? notification.getTitle().toString() : "";
        String body = notification.getBody() != null
                ? notification.getBody().toString() : "";
        String[] keys = notification.getActions().toArray(new String[0]);
        long platId = IsakaiDesktop.post(packageName, id, title, body, channelId, urgency, keys);
        cancelLocked(key);
        if (platId != 0) posted.put(key, Long.valueOf(platId));
    }

    /** Withdraw the notification under integer {@code id}. */
    public synchronized void cancel(int id) {
        cancel(null, id);
    }

    /** Withdraw the notification under {@code tag}:{@code id}. */
    public synchronized void cancel(String tag, int id) {
        cancelLocked(key(tag, id));
    }

    /** Withdraw every posted notification. */
    public synchronized void cancelAll() {
        for (Long platId : posted.values()) {
            IsakaiDesktop.cancelNotification(platId.longValue());
        }
        posted.clear();
    }

    /** Urgency for an importance: HIGH+ -> 2, DEFAULT+ -> 1, else 0. */
    private static int urgency(int importance) {
        if (importance >= NotificationChannel.IMPORTANCE_HIGH) return 2;
        if (importance >= NotificationChannel.IMPORTANCE_DEFAULT) return 1;
        return 0;
    }

    /** Map key for a tag:id pair. */
    private static String key(String tag, int id) {
        return (tag != null ? tag : "") + ":" + id;
    }

    /** Withdraw and drop {@code key}; caller must hold the monitor. */
    private void cancelLocked(String key) {
        Long platId = posted.remove(key);
        if (platId != null) IsakaiDesktop.cancelNotification(platId.longValue());
    }
}
