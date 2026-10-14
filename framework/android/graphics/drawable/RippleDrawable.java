// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;

/** Ripple drawable: renders the content layer, ripple ignored. */
public class RippleDrawable extends LayerDrawable {
    private ColorStateList color;

    public RippleDrawable(ColorStateList color, Drawable content, Drawable mask) {
        super(content == null ? (mask == null ? new Drawable[0] : new Drawable[]{mask})
                : (mask == null ? new Drawable[]{content} : new Drawable[]{content, mask}));
        if (mask != null) setId(getNumberOfLayers()-1, 0x0102002e);
        org.isakai.runtime.Capabilities.unavailable("RippleDrawable.animation");
        this.color = color;
    }

    RippleDrawable(ColorStateList color, LayerDrawable source) {
        super(copyLayers(source));
        this.color = color;
        for (int i=0;i<source.getNumberOfLayers();i++) setId(i,source.getId(i));
        org.isakai.runtime.Capabilities.unavailable("RippleDrawable.animation");
    }

    private static Drawable[] copyLayers(LayerDrawable source) {
        Drawable[] result=new Drawable[source.getNumberOfLayers()];
        for (int i=0;i<result.length;i++) result[i]=source.getDrawable(i);
        return result;
    }

    public void setColor(ColorStateList color) {
        this.color = color;
        invalidateSelf();
    }

    public ColorStateList getColor() { return color; }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null) return;
        android.graphics.Rect bounds=getBounds();
        for(int i=0;i<getNumberOfLayers();i++) {
            Drawable child=getDrawable(i);
            if(getId(i)==0x0102002e || child==null || !child.isVisible()) continue;
            child.setBounds(bounds.left+getLayerInsetLeft(i),bounds.top+getLayerInsetTop(i),
                    bounds.right-getLayerInsetRight(i),bounds.bottom-getLayerInsetBottom(i));
            child.draw(canvas);
        }
    }
}
