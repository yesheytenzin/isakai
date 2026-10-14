// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * ValueAnimator/Animator ABI and state. Regression for the live OpenCalc
 * failure: RecyclerView's item animator calls
 * {@code ValueAnimator.getInterpolator()} during onLayout and the whole app
 * died with NoSuchMethodError when the method was missing.
 */
public final class ValueAnimatorApiTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // The exact call that killed OpenCalc.
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        TimeInterpolator linear = new TimeInterpolator() {
            @Override public float getInterpolation(float input) { return input; }
        };
        check(animator.getInterpolator() == null, "unset interpolator must read back as null");
        animator.setInterpolator(linear);
        check(animator.getInterpolator() == linear, "interpolator must round-trip");

        // Configuration round-trips (apps read these back).
        animator.setDuration(250);
        check(animator.getDuration() == 250, "duration");
        check(animator.setDuration(300) == animator, "setDuration must return the animator");
        animator.setStartDelay(40);
        check(animator.getStartDelay() == 40, "start delay");
        animator.setRepeatCount(ValueAnimator.INFINITE);
        check(animator.getRepeatCount() == ValueAnimator.INFINITE, "repeat count");
        animator.setRepeatMode(ValueAnimator.REVERSE);
        check(animator.getRepeatMode() == ValueAnimator.REVERSE, "repeat mode");
        animator.setFrameDelay(16);
        check(animator.getFrameDelay() == 16, "frame delay");
        TypeEvaluator evaluator = new TypeEvaluator() {
            @Override public Object evaluate(float fraction, Object startValue, Object endValue) { return endValue; }
        };
        animator.setEvaluator(evaluator);
        check(animator.getEvaluator() == evaluator, "evaluator round-trip");

        // Values and callbacks.
        check(animator.getAnimatedValue().equals(1f), "ofFloat end value: " + animator.getAnimatedValue());
        check(animator.getAnimatedFraction() == 1.0f, "synchronous animator is at its end");
        final List<String> events = new ArrayList<>();
        animator.addUpdateListener(a -> events.add("update:" + a.getAnimatedValue()));
        animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator a) { events.add("start"); }
            @Override public void onAnimationEnd(Animator a) { events.add("end"); }
            @Override public void onAnimationCancel(Animator a) { events.add("cancel"); }
            @Override public void onAnimationRepeat(Animator a) { events.add("repeat"); }
        });
        animator.start();
        check(events.toString().equals("[update:1.0, start, end]"), "callback order: " + events);
        check(animator.isStarted(), "started flag");

        // ofInt/ofArgb/ofObject and the static animation switch.
        check(ValueAnimator.ofInt(3, 9).getAnimatedValue().equals(9), "ofInt end value");
        check(ValueAnimator.ofArgb(0xff000000, 0xffffffff).getAnimatedValue().equals(0xffffffff), "ofArgb end value");
        check(ValueAnimator.ofObject(evaluator, "a", "b").getAnimatedValue().equals("b"), "ofObject end value");
        check(ValueAnimator.areAnimatorsEnabled(), "animators enabled by default");
        ValueAnimator.setDurationScale(0f);
        check(!ValueAnimator.areAnimatorsEnabled(), "duration scale 0 disables animators");
        ValueAnimator.setDurationScale(1f);

        // Pause/resume listeners and cancel.
        List<String> lifecycle = new ArrayList<>();
        ValueAnimator other = ValueAnimator.ofFloat(0f, 2f);
        other.addPauseListener(new Animator.AnimatorPauseListener() {
            @Override public void onAnimationPause(Animator a) { lifecycle.add("pause"); }
            @Override public void onAnimationResume(Animator a) { lifecycle.add("resume"); }
        });
        other.pause();
        other.resume();
        other.cancel();
        check(lifecycle.toString().equals("[pause, resume]"), "pause/resume: " + lifecycle);
        System.out.println("PASS: ValueAnimator ABI (getInterpolator/setInterpolator, duration/delay/repeat/frame"
                + " delay/evaluator round-trips, synchronous start callbacks, ofInt/ofArgb/ofObject,"
                + " duration-scale switch, pause/resume)");
    }
}
