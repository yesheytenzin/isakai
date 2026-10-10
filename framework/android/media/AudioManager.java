// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/** Audio volumes and focus; silent shell. */
public class AudioManager {
    public static final int STREAM_VOICE_CALL = 0;
    public static final int STREAM_SYSTEM = 1;
    public static final int STREAM_RING = 2;
    public static final int STREAM_MUSIC = 3;
    public static final int STREAM_ALARM = 4;
    public static final int STREAM_NOTIFICATION = 5;
    public static final int ADJUST_RAISE = 1;
    public static final int ADJUST_LOWER = -1;
    public static final int ADJUST_MUTE = -100;
    public static final int ADJUST_UNMUTE = 100;
    public static final int FLAG_SHOW_UI = 1;
    public static final int FLAG_ALLOW_RINGER_MODES = 2;
    public static final int RINGER_MODE_SILENT = 0;
    public static final int RINGER_MODE_VIBRATE = 1;
    public static final int RINGER_MODE_NORMAL = 2;
    public static final int AUDIOFOCUS_GAIN = 1;
    public static final int AUDIOFOCUS_LOSS = -1;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final String ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY";
    public static final String RINGER_MODE_CHANGED_ACTION = "android.media.RINGER_MODE_CHANGED";
    public int getRingerMode() { return RINGER_MODE_NORMAL; }
    public void setRingerMode(int ringerMode) { }
    public int getStreamVolume(int streamType) { return 0; }
    public int getStreamMaxVolume(int streamType) { return 0; }
    public int getStreamMinVolume(int streamType) { return 0; }
    public void setStreamVolume(int streamType, int index, int flags) { }
    public void adjustStreamVolume(int streamType, int direction, int flags) { }
    public boolean isMusicActive() { return false; }
    public boolean isMicrophoneMute() { return false; }
    public void setMicrophoneMute(boolean on) { }
    public boolean isSpeakerphoneOn() { return false; }
    public void setSpeakerphoneOn(boolean on) { }
    public int requestAudioFocus(OnAudioFocusChangeListener l, int streamType, int durationHint) { return AUDIOFOCUS_REQUEST_FAILED; }
    public int requestAudioFocus(Object request) { return AUDIOFOCUS_REQUEST_FAILED; }
    public int abandonAudioFocus(OnAudioFocusChangeListener l) { return AUDIOFOCUS_REQUEST_FAILED; }
    public int abandonAudioFocusRequest(Object request) { return AUDIOFOCUS_REQUEST_FAILED; }
    public interface OnAudioFocusChangeListener { void onAudioFocusChange(int focusChange); }
}
