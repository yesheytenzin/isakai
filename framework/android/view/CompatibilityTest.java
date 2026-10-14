// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.ImageView;

public final class CompatibilityTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        check(Gravity.getAbsoluteGravity(Gravity.START,View.LAYOUT_DIRECTION_RTL)==Gravity.RIGHT,"RTL start");
        check(Gravity.getAbsoluteGravity(Gravity.END,View.LAYOUT_DIRECTION_LTR)==Gravity.RIGHT,"LTR end");
        Rect result=new Rect();
        Gravity.apply(Gravity.CENTER,20,10,new Rect(10,20,110,100),result);
        check(result.left==50 && result.top==55 && result.right==70 && result.bottom==65,"center positioning");
        Gravity.apply(Gravity.END|Gravity.BOTTOM,20,10,new Rect(10,20,110,100),3,4,result,View.LAYOUT_DIRECTION_RTL);
        check(result.left==13 && result.top==86,"relative gravity adjustments");
        result.set(-5,-6,15,14);Gravity.applyDisplay(Gravity.NO_GRAVITY,new Rect(0,0,100,100),result);
        check(result.left==0 && result.top==0 && result.right==20 && result.bottom==20,"display bounds");
        View measured=new View();
        measured.setMeasuredDimension(123|View.MEASURED_STATE_TOO_SMALL,456|View.MEASURED_STATE_TOO_SMALL);
        check(measured.getMeasuredWidth()==123 && measured.getMeasuredHeight()==456,"state bits leaked into dimensions");
        check(measured.getMeasuredState()==0x01000100,"combined measurement state");
        check((View.resolveSizeAndState(200,View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.AT_MOST),0)&View.MEASURED_STATE_TOO_SMALL)!=0,"too-small state lost");
        android.widget.FrameLayout frame=new android.widget.FrameLayout(new android.app.Application());
        View child=new View();frame.addView(child,new ViewGroup.LayoutParams(10,20));
        check(child.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams,"programmatic frame parameters not converted");
        View indexed=new View();frame.addView(indexed,0,new ViewGroup.LayoutParams(10,20));
        check(indexed.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams,"indexed frame parameters not converted");
        final int[] released={0};
        android.database.sqlite.SQLiteClosable closable=new android.database.sqlite.SQLiteClosable() {
            protected void onAllReferencesReleased() { released[0]++; }
        };
        closable.acquireReference();closable.close();check(released[0]==0,"reference released too early");
        closable.releaseReference();check(released[0]==1,"last reference did not close");
        try { closable.acquireReference();throw new AssertionError("closed object reopened"); }catch(IllegalStateException expected) {}
        android.database.DataSetObservable dataset=new android.database.DataSetObservable();
        android.database.Observable<android.database.DataSetObserver> observable=dataset;
        StringBuilder events=new StringBuilder();
        android.database.DataSetObserver first=new android.database.DataSetObserver() {
            public void onChanged() { events.append("A"); }
        };
        android.database.DataSetObserver second=new android.database.DataSetObserver() {
            public void onChanged() { events.append("B"); }
        };
        observable.registerObserver(first);observable.registerObserver(second);dataset.notifyChanged();
        check(events.toString().equals("BA"),"Observable erased ABI/notification order");
        observable.unregisterObserver(first);observable.unregisterObserver(second);
        Bitmap bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);bitmap.setPixel(0,0,0x8000ff00);
        ImageView view=new ImageView(new android.app.Application());
        view.setColorFilter(0x80ff0000,PorterDuff.Mode.SRC_IN);view.setImageBitmap(bitmap);
        view.layout(0,0,1,1);Canvas canvas=new Canvas(1,1);view.draw(canvas);
        check(canvas.getPixel(0,0)==0x40ff0000,"bitmap SRC_IN filter alpha/color");
        view.clearColorFilter();view.draw(canvas);
        check(canvas.getPixel(0,0)==0x8000ff00,"clearing bitmap filter");
        check(new PorterDuffColorFilter(0x80ff0000,PorterDuff.Mode.SRC_ATOP).filterColor(0x800000ff)==0x8080007f,"SRC_ATOP compositing");
        android.media.MediaDataSource source=new android.media.MediaDataSource() {
            public int readAt(long position,byte[] buffer,int offset,int size) { return size==0?0:-1; }
            public long getSize() { return 0; }
            public void close() {}
        };
        check(source instanceof java.io.Closeable,"media source contract");
        System.out.println("PASS: gravity RTL/placement/clipping, bitmap color-filter rendering and media input contract");
    }
}
