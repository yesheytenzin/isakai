// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

/** JNI doorway to the Zig desktop integration (notifications, URIs, file picks, polls). */
public final class IsakaiDesktop {
    static {
        System.loadLibrary("isakai_jni");
    }

    private IsakaiDesktop() {}

    /** Post a notification; returns the platform id (0 on failure). */
    public static native long nNotify(String appName, int id, String title,
            String body, String channelId, int urgency);

    /** Withdraw the platform notification {@code platId}. */
    public static native void nCancel(long platId);

    /** Open {@code uri} in the desktop handler; 0 ok, -1 fail. */
    public static native int nOpenUri(String uri);

    /** Start a desktop file picker; 0 means failure. */
    public static native long nOpenFileStart(String title);

    /** Poll a file-pick result: null pending, "" cancelled/error, else staged path. */
    public static native String nGetFileResult(long req);

    /** Release a file-pick or close request handle. */
    public static native void nCloseReq(long req);

    /** Drain desktop events into {@code batch} as groups of 8 ints; 0 none, negative error. */
    public static native int nPollDesktop(int[] batch);

    /** Action key for a kind-11 slot; null on bad slot. */
    public static native String nGetActionKey(int slot);

    /** Open {@code uri}; false when the bridge fails or is unavailable. */
    public static boolean openUri(String uri) {
        try {
            return nOpenUri(uri) == 0;
        } catch (UnsatisfiedLinkError e) {
            return false;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Post a notification; keys are kept for tap routing; returns the platform id (0 on failure). */
    public static long post(String appName, int id, String title,
            String body, String channelId, int urgency, String[] actionKeys) {
        try {
            return nNotify(appName, id, title, body, channelId, urgency);
        } catch (UnsatisfiedLinkError e) {
            return 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    /** Withdraw {@code platId}; ignored when the bridge is unavailable. */
    public static void cancelNotification(long platId) {
        try {
            nCancel(platId);
        } catch (UnsatisfiedLinkError e) {
            // Headless/no-bridge run: nothing to withdraw.
        } catch (IllegalStateException e) {
            // Headless/no-bridge run: nothing to withdraw.
        }
    }

    /** Start a file picker; throws when the bridge fails or is unavailable. */
    public static long openFile(String title) {
        try {
            long req = nOpenFileStart(title);
            if (req == 0) throw new IllegalStateException("openFile failed");
            return req;
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException("desktop bridge unavailable", e);
        }
    }

    /** File-pick result: null pending, "" cancelled/error, else staged path. */
    public static String fileResult(long req) {
        try {
            return nGetFileResult(req);
        } catch (UnsatisfiedLinkError e) {
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Release a file-pick or close request handle; ignored when unavailable. */
    public static void closeReq(long req) {
        try {
            nCloseReq(req);
        } catch (UnsatisfiedLinkError e) {
            // Headless/no-bridge run: nothing to release.
        } catch (IllegalStateException e) {
            // Headless/no-bridge run: nothing to release.
        }
    }

    /** Drain desktop events into {@code batch}; 0 none, negative error. */
    public static int poll(int[] batch) {
        try {
            return nPollDesktop(batch);
        } catch (UnsatisfiedLinkError e) {
            return 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    /** Action key for a kind-11 slot; null on bad slot or unavailable bridge. */
    public static String actionKey(int slot) {
        try {
            return nGetActionKey(slot);
        } catch (UnsatisfiedLinkError e) {
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Low 32 bits of a 64-bit handle for int-pair batch encoding. */
    public static int lo(long v) {
        return (int) (v & 0xFFFFFFFFL);
    }

    /** High 32 bits of a 64-bit handle for int-pair batch encoding. */
    public static int hi(long v) {
        return (int) ((v >>> 32) & 0xFFFFFFFFL);
    }

    /** Recombine a lo/hi int pair into a 64-bit handle. */
    public static long join(int lo, int hi) {
        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }
}
