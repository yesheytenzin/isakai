// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

import java.util.HashMap;
import java.util.Map;

/** Format map shell: string/int entries only. */
public final class MediaFormat {
    public static final String KEY_MIME = "mime";
    public static final String KEY_WIDTH = "width";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_DURATION = "durationUs";
    public static final String KEY_SAMPLE_RATE = "sample-rate";
    public static final String KEY_CHANNEL_COUNT = "channel-count";
    public static final String KEY_BIT_RATE = "bitrate";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_TITLE = "title";
    public static final String KEY_ARTIST = "artist";
    public static final String KEY_ALBUM = "album";
    private final Map<String, Object> map = new HashMap<>();
    public static MediaFormat createVideoFormat(String mime, int width, int height) { MediaFormat f = new MediaFormat(); f.setString(KEY_MIME, mime); f.setInteger(KEY_WIDTH, width); f.setInteger(KEY_HEIGHT, height); return f; }
    public static MediaFormat createAudioFormat(String mime, int sampleRate, int channels) { MediaFormat f = new MediaFormat(); f.setString(KEY_MIME, mime); f.setInteger(KEY_SAMPLE_RATE, sampleRate); f.setInteger(KEY_CHANNEL_COUNT, channels); return f; }
    public String getString(String name) { Object v = map.get(name); return v == null ? null : v.toString(); }
    public int getInteger(String name) { Object v = map.get(name); if (v instanceof Number) return ((Number) v).intValue(); return Integer.parseInt(String.valueOf(v)); }
    public long getLong(String name) { Object v = map.get(name); if (v instanceof Number) return ((Number) v).longValue(); return Long.parseLong(String.valueOf(v)); }
    public void setString(String name, String value) { map.put(name, value); }
    public void setInteger(String name, int value) { map.put(name, value); }
    public void setLong(String name, long value) { map.put(name, value); }
    public boolean containsKey(String name) { return map.containsKey(name); }
}
