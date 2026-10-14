// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;

/** Host Java2D shaping and font fallback, rendered into the existing Wayland buffers. */
public final class HostText {
    static { System.setProperty("java.awt.headless", "true"); }
    private HostText() {}
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static Font font(float size) { return new Font(Font.SANS_SERIF,Font.PLAIN,1).deriveFont(Math.max(1,size)); }
    public static float measure(String text,float size) {
        if(text==null||text.isEmpty())return 0;
        float best=0;
        for(String line:text.split("\n",-1))if(!line.isEmpty())
            best=Math.max(best,new TextLayout(line,font(size),CONTEXT).getAdvance());
        return best;
    }
    public static int lineHeight(float size) {
        return (int)Math.ceil(font(size).getLineMetrics("Ag",CONTEXT).getHeight());
    }
    public static void draw(Canvas canvas,String text,int x,int y,int size,int color) {
        if(text==null||text.isEmpty())return;
        int lineY=y;
        for(String line:text.split("\n",-1)) {
            if(!line.isEmpty())drawLine(canvas,line,x,lineY,size,color);
            lineY+=lineHeight(size);
        }
    }
    private static void drawLine(Canvas canvas,String text,int x,int y,int size,int color) {
        TextLayout layout=new TextLayout(text,font(size),CONTEXT);
        java.awt.Rectangle bounds=layout.getPixelBounds(CONTEXT,0,layout.getAscent());
        // Clip before allocating so a very long app string cannot allocate an unbounded raster.
        int left=Math.max(0,x+bounds.x),top=Math.max(0,y+bounds.y);
        int right=Math.min(canvas.getWidth(),x+bounds.x+bounds.width);
        int bottom=Math.min(canvas.getHeight(),y+bounds.y+bounds.height);
        if(right<=left||bottom<=top)return;
        BufferedImage raster=new BufferedImage(right-left,bottom-top,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=raster.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(new java.awt.Color(color,true));
            layout.draw(g,x-left,y-top+layout.getAscent());
        } finally { g.dispose(); }
        for(int row=0;row<raster.getHeight();row++)for(int col=0;col<raster.getWidth();col++) {
            int fg=raster.getRGB(col,row),alpha=fg>>>24;
            if(alpha==0)continue;
            int bg=canvas.getPixel(left+col,top+row);
            int red=(((fg>>>16)&255)*alpha+((bg>>>16)&255)*(255-alpha)+127)/255;
            int green=(((fg>>>8)&255)*alpha+((bg>>>8)&255)*(255-alpha)+127)/255;
            int blue=((fg&255)*alpha+(bg&255)*(255-alpha)+127)/255;
            canvas.setPixel(left+col,top+row,0xff000000|(red<<16)|(green<<8)|blue);
        }
    }
}
