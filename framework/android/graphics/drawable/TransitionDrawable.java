// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;

/** Cross-fade between two layers: instant switch, records timing. */
public class TransitionDrawable extends LayerDrawable {
    private boolean crossFade;
    private int current;
    public TransitionDrawable(Drawable[] layers) { super(layers); }
    public void setCrossFadeEnabled(boolean enabled) { crossFade = enabled; }
    public boolean isCrossFadeEnabled() { return crossFade; }
    public void startTransition(int durationMillis) { current = getNumberOfLayers() > 1 ? 1 : 0; }
    public void reverseTransition(int durationMillis) { current = 0; }
    public void resetTransition() { current = 0; }
    @Override public void draw(Canvas canvas) { Drawable d = getDrawable(current); if (d != null) d.draw(canvas); }
}
