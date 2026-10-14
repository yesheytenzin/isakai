package android.view;
import android.content.ClipData;
import android.content.ClipDescription;
/** Drag event value. Host drag-and-drop delivery is not implemented. */
public final class DragEvent {
    public static final int ACTION_DRAG_STARTED=1,ACTION_DRAG_LOCATION=2,ACTION_DROP=3,
            ACTION_DRAG_ENDED=4,ACTION_DRAG_ENTERED=5,ACTION_DRAG_EXITED=6;
    private final int action;
    private final float x,y;
    private final Object localState;
    private final ClipData data;
    private final ClipDescription description;
    private final boolean result;
    private DragEvent(int action,float x,float y,Object state,ClipDescription description,ClipData data,boolean result) {
        this.action=action;this.x=x;this.y=y;localState=state;this.description=description;this.data=data;this.result=result;
    }
    public static DragEvent obtain(int action,float x,float y,Object state,ClipDescription description,ClipData data,boolean result) {
        return new DragEvent(action,x,y,state,description,data,result);
    }
    public int getAction(){return action;}
    public float getX(){return x;}
    public float getY(){return y;}
    public Object getLocalState(){return localState;}
    public ClipData getClipData(){return data;}
    public ClipDescription getClipDescription(){return description;}
    public boolean getResult(){return result;}
}
