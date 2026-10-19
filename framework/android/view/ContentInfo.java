// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Rich-content payload: clip plus source flags. */
public class ContentInfo {
    public static final int SOURCE_CLIPBOARD = 0;
    public static final int SOURCE_INPUT_METHOD = 1;
    public static final int SOURCE_DRAG_AND_DROP = 2;
    public static final int FLAG_CONVERT_TO_PLAIN_TEXT = 1;

    private final android.content.ClipData clip;
    private final int source;
    private final int flags;
    private final android.net.Uri linkUri;
    private final android.os.Bundle extras;

    public ContentInfo(android.content.ClipData clip, int source) {
        this(clip, source, 0, null, null);
    }

    private ContentInfo(android.content.ClipData clip, int source, int flags,
            android.net.Uri linkUri, android.os.Bundle extras) {
        this.clip = clip;
        this.source = source;
        this.flags = flags;
        this.linkUri = linkUri;
        this.extras = extras;
    }

    public android.content.ClipData getClip() {
        return clip;
    }

    public int getSource() {
        return source;
    }

    public int getFlags() {
        return flags;
    }

    public android.net.Uri getLinkUri() {
        return linkUri;
    }

    public android.os.Bundle getExtras() {
        return extras;
    }

    /** AOSP builder: clip and source required, flags/link/extras optional. */
    public static final class Builder {
        private android.content.ClipData clip;
        private int source;
        private int flags;
        private android.net.Uri linkUri;
        private android.os.Bundle extras;

        public Builder(android.content.ClipData clip, int source) {
            this.clip = clip;
            this.source = source;
        }

        public Builder setClip(android.content.ClipData clip) {
            this.clip = clip;
            return this;
        }

        public Builder setSource(int source) {
            this.source = source;
            return this;
        }

        public Builder setFlags(int flags) {
            this.flags = flags;
            return this;
        }

        public Builder setLinkUri(android.net.Uri linkUri) {
            this.linkUri = linkUri;
            return this;
        }

        public Builder setExtras(android.os.Bundle extras) {
            this.extras = extras;
            return this;
        }

        public ContentInfo build() {
            return new ContentInfo(clip, source, flags, linkUri, extras);
        }
    }
}
