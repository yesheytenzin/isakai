// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/**
 * Value animator: update listeners fire once at the end value.
 *
 * <p>Animations complete synchronously (the framework has no frame clock):
 * start() dispatches one update with the final value and the end callbacks.
 * The AOSP-shaped state (duration, delay, repeat count/mode, interpolator,
 * evaluator, frame delay) is stored so callers that configure or read it
 * back behave predictably.
 */
public class ValueAnimator extends Animator {
    public interface AnimatorUpdateListener {
        void onAnimationUpdate(ValueAnimator animator);
    }

    /** AOSP hidden API: observes system duration-scale changes. */
    public interface DurationScaleChangeListener {
        void onChanged(float scale);
    }

    private static final java.util.concurrent.CopyOnWriteArrayList<DurationScaleChangeListener>
            durationScaleListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** AOSP repeat modes. */
    public static final int RESTART = 1;
    public static final int REVERSE = 2;
    /** AOSP sentinel: repeat forever. */
    public static final int INFINITE = -1;

    private final java.util.ArrayList<AnimatorUpdateListener> updateListeners =
            new java.util.ArrayList<>();
    private float[] floatValues = new float[0];
    private int[] intValues = new int[0];
    private Object[] objectValues = new Object[0];
    private boolean isFloat = true;
    private int repeatCount = 0;
    private int repeatMode = RESTART;
    private TypeEvaluator evaluator;
    private long frameDelay = 10;
    private static float durationScale = 1.0f;

    public static ValueAnimator ofFloat(float... values) {
        ValueAnimator a = new ValueAnimator();
        a.setFloatValues(values);
        a.isFloat = true;
        return a;
    }

    public static ValueAnimator ofInt(int... values) {
        ValueAnimator a = new ValueAnimator();
        a.setIntValues(values);
        a.isFloat = false;
        return a;
    }

    public static ValueAnimator ofArgb(int... values) {
        ValueAnimator a = ofInt(values);
        return a;
    }

    public static ValueAnimator ofPropertyValuesHolder(PropertyValuesHolder... values) {
        return new ValueAnimator();
    }

    public static ValueAnimator ofObject(TypeEvaluator evaluator, Object... values) {
        ValueAnimator a = new ValueAnimator();
        a.evaluator = evaluator;
        a.objectValues = values == null ? new Object[0] : values.clone();
        a.isFloat = false;
        return a;
    }

    /** Global animation-duration scale (AOSP: 0 disables animations). */
    public static float getDurationScale() {
        return durationScale;
    }

    public static void setDurationScale(float scale) {
        if (durationScale == scale) return;
        durationScale = scale;
        for (DurationScaleChangeListener listener : durationScaleListeners) {
            listener.onChanged(scale);
        }
    }

    public static void registerDurationScaleChangeListener(DurationScaleChangeListener listener) {
        if (listener != null && !durationScaleListeners.contains(listener)) {
            durationScaleListeners.add(listener);
        }
    }

    public static void unregisterDurationScaleChangeListener(DurationScaleChangeListener listener) {
        durationScaleListeners.remove(listener);
    }

    /** AOSP: false when animators are disabled system-wide. */
    public static boolean areAnimatorsEnabled() {
        return durationScale > 0f;
    }

    public void addUpdateListener(AnimatorUpdateListener listener) {
        if (listener != null && !updateListeners.contains(listener)) updateListeners.add(listener);
    }

    public void removeUpdateListener(AnimatorUpdateListener listener) {
        updateListeners.remove(listener);
    }

    public void removeAllUpdateListeners() {
        updateListeners.clear();
    }

    public void setFloatValues(float... values) {
        floatValues = values == null ? new float[0] : values.clone();
        isFloat = true;
    }

    public void setIntValues(int... values) {
        intValues = values == null ? new int[0] : values.clone();
        isFloat = false;
    }

    public void setObjectValues(Object... values) {
        objectValues = values == null ? new Object[0] : values.clone();
        isFloat = false;
    }

    public Object getAnimatedValue() {
        if (isFloat) return floatValues.length == 0 ? 0f : floatValues[floatValues.length - 1];
        if (intValues.length > 0) return intValues[intValues.length - 1];
        if (objectValues.length > 0) return objectValues[objectValues.length - 1];
        return 0;
    }

    public Object getAnimatedValue(String propertyName) {
        return getAnimatedValue();
    }

    public float getAnimatedFraction() {
        return 1.0f;
    }

    public long getCurrentPlayTime() {
        return getDuration();
    }

    public void setCurrentPlayTime(long playTime) {
        // Synchronous model: the animation is always at its end state.
    }

    public void setCurrentFraction(float fraction) {
        // Synchronous model: the animation is always at its end state.
    }

    public long getFrameDelay() {
        return frameDelay;
    }

    public void setFrameDelay(long frameDelay) {
        this.frameDelay = frameDelay;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int repeatMode) {
        this.repeatMode = repeatMode;
    }

    public TypeEvaluator getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(TypeEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public ValueAnimator setDuration(long duration) {
        super.setDuration(duration);
        return this;
    }

    public void reverse() {
        for (int i = 0, j = floatValues.length - 1; i < j; i++, j--) {
            float t = floatValues[i];
            floatValues[i] = floatValues[j];
            floatValues[j] = t;
        }
        for (int i = 0, j = intValues.length - 1; i < j; i++, j--) {
            int t = intValues[i];
            intValues[i] = intValues[j];
            intValues[j] = t;
        }
        for (int i = 0, j = objectValues.length - 1; i < j; i++, j--) {
            Object t = objectValues[i];
            objectValues[i] = objectValues[j];
            objectValues[j] = t;
        }
    }

    @Override
    public void start() {
        for (AnimatorUpdateListener l : updateListeners.toArray(new AnimatorUpdateListener[0])) {
            l.onAnimationUpdate(this);
        }
        super.start();
    }

    @Override
    public void end() {
        super.end();
    }
}
