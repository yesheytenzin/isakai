// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

/** Animation group: children run together. */
public class AnimationSet extends Animation {
    private final java.util.ArrayList<Animation> children = new java.util.ArrayList<>();
    private final boolean shareInterpolator;

    public AnimationSet(boolean shareInterpolator) {
        this.shareInterpolator = shareInterpolator;
    }

    public void addAnimation(Animation animation) {
        if (animation != null) children.add(animation);
    }

    public boolean getTransformation(long currentTime, Transformation outTransformation) {
        return false;
    }

    public boolean getTransformation(long currentTime, Transformation outTransformation, float scale) {
        return false;
    }
}
