// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.isakai.bridge.IsakaiMedia;

/**
 * Track discovery backed by host ffprobe. Reports one {@link MediaFormat}
 * per stream (video size, audio rate/channels, duration); sample transport
 * ({@code readSampleData}) is not wired, so codec input comes from the
 * elementary-stream file for this spike.
 */
public final class MediaExtractor implements AutoCloseable {
    public static final int SEEK_TO_PREVIOUS_SYNC = 0;
    public static final int SEEK_TO_NEXT_SYNC = 1;
    public static final int SEEK_TO_CLOSEST_SYNC = 2;
    public static final int SAMPLE_FLAG_SYNC = 1;
    public static final int SAMPLE_FLAG_PARTIAL_FRAME = 4;

    private final List<IsakaiMedia.Track> tracks = new ArrayList<>();
    private String path;

    public void setDataSource(String path) throws IOException {
        if (path == null) throw new IllegalArgumentException("path == null");
        if (!IsakaiMedia.hasFfprobe()) throw new UnsupportedOperationException("ffprobe not available");
        List<IsakaiMedia.Track> found = IsakaiMedia.probeTracks(path);
        tracks.clear();
        tracks.addAll(found);
        this.path = path;
    }

    public int getTrackCount() { return tracks.size(); }

    public MediaFormat getTrackFormat(int index) {
        IsakaiMedia.Track t = tracks.get(index);
        MediaFormat f;
        if (t.mime.startsWith("video/")) {
            f = MediaFormat.createVideoFormat(t.mime, t.width, t.height);
        } else {
            f = MediaFormat.createAudioFormat(t.mime, t.sampleRate, t.channels);
        }
        if (t.durationUs >= 0) f.setLong(MediaFormat.KEY_DURATION, t.durationUs);
        return f;
    }

    public void selectTrack(int index) {
        tracks.get(index);
    }

    public void unselectTrack(int index) {
        tracks.get(index);
    }

    public void seekTo(long timeUs, int mode) { }

    public void release() {
        tracks.clear();
        path = null;
    }

    @Override public void close() { release(); }
}
