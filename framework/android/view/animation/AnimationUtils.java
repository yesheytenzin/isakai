// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

public final class AnimationUtils {
    private AnimationUtils() {}

    public static long currentAnimationTimeMillis() {
        return System.currentTimeMillis();
    }
}
