// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Animator group: starts children together. */
public class AnimatorSet extends Animator {
    private final java.util.ArrayList<Animator> children = new java.util.ArrayList<>();

    public Builder play(Animator animator) {
        if (animator != null) children.add(animator);
        return new Builder(animator);
    }

    public void playTogether(Animator... animators) {
        if (animators != null) for (Animator a : animators) if (a != null) children.add(a);
    }

    public void playSequentially(Animator... animators) {
        playTogether(animators);
    }

    @Override
    public void start() {
        for (Animator a : children) a.start();
        super.start();
    }

    public class Builder {
        Builder(Animator owner) {}

        public Builder with(Animator animator) {
            if (animator != null) children.add(animator);
            return this;
        }

        public Builder before(Animator animator) {
            if (animator != null) children.add(animator);
            return this;
        }

        public Builder after(Animator animator) {
            if (animator != null) children.add(animator);
            return this;
        }
    }
}
