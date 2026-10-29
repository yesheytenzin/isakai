// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/**
 * DRM crypto session shell. Construction always fails: MediaDrm stays
 * unavailable outside ClearKey, and ClearKey is not wired in this spike.
 */
public final class MediaCrypto {
    public MediaCrypto(java.util.UUID uuid, byte[] initData) {
        throw new UnsupportedOperationException("drm unavailable: no MediaDrm outside ClearKey");
    }

    public boolean requiresSecureDecoderComponent(String mime) {
        return false;
    }
}
