package android.view;
import java.util.concurrent.CopyOnWriteArrayList;
/** View-tree notifications dispatched by the host layout/draw pump. */
public class ViewTreeObserver {
    public interface OnGlobalLayoutListener { void onGlobalLayout(); }
    public interface OnPreDrawListener { boolean onPreDraw(); }
    public interface OnDrawListener { void onDraw(); }
    public interface OnGlobalFocusChangeListener { void onGlobalFocusChanged(View oldFocus,View newFocus); }
    public interface OnScrollChangedListener { void onScrollChanged(); }
    public interface OnTouchModeChangeListener { void onTouchModeChanged(boolean inTouchMode); }
    public interface OnWindowAttachListener { void onWindowAttached(); void onWindowDetached(); }
    public interface OnWindowFocusChangeListener { void onWindowFocusChanged(boolean hasFocus); }
    private final CopyOnWriteArrayList<OnGlobalLayoutListener> layout=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnPreDrawListener> preDraw=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnDrawListener> draw=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnGlobalFocusChangeListener> focus=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnScrollChangedListener> scroll=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnTouchModeChangeListener> touchMode=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnWindowAttachListener> windowAttach=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnWindowFocusChangeListener> windowFocus=new CopyOnWriteArrayList<>();
    public boolean isAlive(){return true;}
    public void addOnGlobalLayoutListener(OnGlobalLayoutListener l){if(l!=null)layout.addIfAbsent(l);}
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){layout.remove(l);}
    public void addOnPreDrawListener(OnPreDrawListener l){if(l!=null)preDraw.addIfAbsent(l);}
    public void removeOnPreDrawListener(OnPreDrawListener l){preDraw.remove(l);}
    public void addOnDrawListener(OnDrawListener l){if(l!=null)draw.addIfAbsent(l);}
    public void removeOnDrawListener(OnDrawListener l){draw.remove(l);}
    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l){if(l!=null)focus.addIfAbsent(l);}
    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l){focus.remove(l);}
    public void addOnScrollChangedListener(OnScrollChangedListener l){if(l!=null)scroll.addIfAbsent(l);}
    public void removeOnScrollChangedListener(OnScrollChangedListener l){scroll.remove(l);}
    public void addOnTouchModeChangeListener(OnTouchModeChangeListener l){if(l!=null)touchMode.addIfAbsent(l);}
    public void removeOnTouchModeChangeListener(OnTouchModeChangeListener l){touchMode.remove(l);}
    public void addOnWindowAttachListener(OnWindowAttachListener l){if(l!=null)windowAttach.addIfAbsent(l);}
    public void removeOnWindowAttachListener(OnWindowAttachListener l){windowAttach.remove(l);}
    public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener l){if(l!=null)windowFocus.addIfAbsent(l);}
    public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener l){windowFocus.remove(l);}
    public void dispatchOnGlobalLayout(){for(OnGlobalLayoutListener l:layout)l.onGlobalLayout();}
    public boolean dispatchOnPreDraw(){boolean cancel=false;for(OnPreDrawListener l:preDraw)cancel|=!l.onPreDraw();return cancel;}
    public void dispatchOnDraw(){for(OnDrawListener l:draw)l.onDraw();}
    public void dispatchOnScrollChanged(){for(OnScrollChangedListener l:scroll)l.onScrollChanged();}
    public void dispatchOnGlobalFocusChanged(View oldView,View newView){for(OnGlobalFocusChangeListener l:focus)l.onGlobalFocusChanged(oldView,newView);}
}
