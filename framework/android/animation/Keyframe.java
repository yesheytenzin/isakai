// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Keyframe: fraction/value pair used by property animators. */
public class Keyframe implements Cloneable {
    private float mFraction;
    private Object mValue;
    private Class<?> mValueType;
    private boolean mHasValue;

    Keyframe(float fraction, Object value) {
        mFraction = fraction;
        mValue = value;
        mHasValue = value != null;
        mValueType = value == null ? Object.class : value.getClass();
    }

    public static Keyframe ofInt(float fraction, int value) {
        return new Keyframe(fraction, value);
    }

    public static Keyframe ofInt(float fraction) {
        return new Keyframe(fraction, null);
    }

    public static Keyframe ofFloat(float fraction, float value) {
        return new Keyframe(fraction, value);
    }

    public static Keyframe ofFloat(float fraction) {
        return new Keyframe(fraction, null);
    }

    public static Keyframe ofObject(float fraction, Object value) {
        return new Keyframe(fraction, value);
    }

    public static Keyframe ofObject(float fraction) {
        return new Keyframe(fraction, null);
    }

    public float getFraction() {
        return mFraction;
    }

    public void setFraction(float fraction) {
        mFraction = fraction;
    }

    public Object getValue() {
        return mValue;
    }

    public void setValue(Object value) {
        mValue = value;
        mHasValue = value != null;
        if (value != null) mValueType = value.getClass();
    }

    public Class<?> getType() {
        return mValueType;
    }

    public boolean hasValue() {
        return mHasValue;
    }

    @Override
    public Keyframe clone() {
        return new Keyframe(mFraction, mValue);
    }
}
