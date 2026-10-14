package android.view;
import android.content.Context;
import android.util.AttributeSet;
/** Constructor failures must survive inflation rather than becoming replacement views. */
public final class InflationTest {
    public static final class Broken extends View {
        public Broken(Context context,AttributeSet attrs) { super(context,attrs);throw new IllegalStateException("constructor canary"); }
    }
    public static final class Good extends View {
        public Good(Context context,AttributeSet attrs) { super(context,attrs); }
    }
    public static void main(String[] args) {
        LayoutInflater inflater=LayoutInflater.from(new android.app.Application());
        if(!(inflater.createView(Good.class.getName(),null,null) instanceof Good))throw new AssertionError("custom view lost");
        try {
            inflater.createView(Broken.class.getName(),null,null);
            throw new AssertionError("constructor failure swallowed");
        } catch(InflateException expected) {
            if(!(expected.getCause() instanceof IllegalStateException))throw new AssertionError("cause lost",expected);
        }
        System.out.println("PASS: custom-view construction and preserved constructor failure");
    }
}
