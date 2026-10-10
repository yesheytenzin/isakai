// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Rich-content payload: clip plus source flags. */
public class ContentInfo {
    public static final int SOURCE_CLIPBOARD = 0;
    public static final int SOURCE_INPUT_METHOD = 1;
    public static final int SOURCE_DRAG_AND_DROP = 2;

    private final android.content.ClipData clip;
    private final int source;

    public ContentInfo(android.content.ClipData clip, int source) {
        this.clip = clip;
        this.source = source;
    }

    public android.content.ClipData getClip() {
        return clip;
    }

    public int getSource() {
        return source;
    }
}
