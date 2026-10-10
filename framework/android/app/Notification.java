// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Desktop notification: title/body/channel plus up to 3 tapped action keys. */
public final class Notification {
    private final CharSequence title;
    private final CharSequence body;
    private final String channelId;
    private final List<String> actionKeys;
    private final List<CharSequence> actionTitles;

    private Notification(CharSequence title, CharSequence body,
            String channelId, List<String> keys, List<CharSequence> titles) {
        this.title = title;
        this.body = body;
        this.channelId = channelId;
        this.actionKeys = Collections.unmodifiableList(new ArrayList<String>(keys));
        this.actionTitles = Collections.unmodifiableList(new ArrayList<CharSequence>(titles));
    }

    /** Notification title text. */
    public CharSequence getTitle() {
        return title;
    }

    /** Notification body text. */
    public CharSequence getBody() {
        return body;
    }

    /** Channel id this notification was posted to. */
    public String getChannelId() {
        return channelId;
    }

    /** Stable action keys, parallel to {@link #getActionTitles}. */
    public List<String> getActions() {
        return actionKeys;
    }

    /** Human-readable action titles, parallel to {@link #getActions}. */
    public List<CharSequence> getActionTitles() {
        return actionTitles;
    }

    /** Builder for {@link Notification}; icons are always empty (no icon support). */
    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence body;
        private String channelId;
        private final List<String> keys = new ArrayList<String>();
        private final List<CharSequence> titles = new ArrayList<CharSequence>();

        /** Builder bound to {@code context} (used for the app name at post time). */
        public Builder(Context context) {
            this.context = context;
        }

        /** Ignored: icons are always empty (no icon support). */
        public Builder setSmallIcon(int icon) {
            return this;
        }

        /** Set the notification title. */
        public Builder setContentTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        /** Set the notification body text. */
        public Builder setContentText(CharSequence body) {
            this.body = body;
            return this;
        }

        /** Set the channel id (defaults to "misc" when unknown at post time). */
        public Builder setChannelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        /** Add an action; only the first 3 are honored. */
        public Builder addAction(String actionKey, CharSequence title) {
            if (keys.size() >= 3) return this;
            keys.add(actionKey);
            titles.add(title);
            return this;
        }

        /** Build the immutable {@link Notification}. */
        public Notification build() {
            return new Notification(title, body, channelId, keys, titles);
        }
    }
}
