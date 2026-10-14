// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Software subset of Porter-Duff color filtering. */
public class PorterDuffColorFilter extends ColorFilter {
    private final int color;
    private final PorterDuff.Mode mode;
    public PorterDuffColorFilter(int color, PorterDuff.Mode mode) {
        if (mode == null) throw new NullPointerException("mode");
        this.color=color;this.mode=mode;
    }
    public int getColor() { return color; }
    public PorterDuff.Mode getMode() { return mode; }
    @Override public int filterColor(int destination) {
        int sourceAlpha=color>>>24;
        if (mode==PorterDuff.Mode.SRC_IN)
            return (color&0xffffff) | (((sourceAlpha*(destination>>>24)+127)/255)<<24);
        if (mode==PorterDuff.Mode.SRC_ATOP) {
            int result=destination&0xff000000;
            for(int shift=0;shift<24;shift+=8) {
                int channel=(((color>>>shift)&255)*sourceAlpha+((destination>>>shift)&255)*(255-sourceAlpha)+127)/255;
                result|=channel<<shift;
            }
            return result;
        }
        if (mode==PorterDuff.Mode.SRC) return color;
        if (mode==PorterDuff.Mode.DST) return destination;
        if (mode==PorterDuff.Mode.CLEAR) return 0;
        org.isakai.runtime.Capabilities.unavailable("PorterDuffColorFilter.mode."+mode);
        return destination;
    }
}
