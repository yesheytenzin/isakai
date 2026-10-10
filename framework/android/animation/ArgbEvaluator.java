// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Interpolates ARGB colors channel by channel. */
public class ArgbEvaluator implements TypeEvaluator<Integer> {
    private static final ArgbEvaluator INSTANCE = new ArgbEvaluator();
    public static ArgbEvaluator getInstance() { return INSTANCE; }
    @Override public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
        int s = startValue == null ? 0 : startValue;
        int e = endValue == null ? 0 : endValue;
        int sa = (s >> 24) & 0xff, sr = (s >> 16) & 0xff, sg = (s >> 8) & 0xff, sb = s & 0xff;
        int ea = (e >> 24) & 0xff, er = (e >> 16) & 0xff, eg = (e >> 8) & 0xff, eb = e & 0xff;
        int a = sa + Math.round(fraction * (ea - sa));
        int r = sr + Math.round(fraction * (er - sr));
        int g = sg + Math.round(fraction * (eg - sg));
        int b = sb + Math.round(fraction * (eb - sb));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
