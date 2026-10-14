package org.isakai.verify;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.TextView;
import java.util.*;

/** Real window navigation, result and owning-thread callback regression. */
public final class NavigationProbe {
    static final List<String> events=new ArrayList<>();
    static final Thread owner=Thread.currentThread();
    static final List<String> observed=new ArrayList<>();
    static int postCreated;
    static final List<TrackingText> views=new ArrayList<>();
    static final class TrackingText extends TextView {
        int attaches,detaches,sizes;
        TrackingText(Activity activity,String label) {
            super(activity);setText(label);setTextSize(24);setTextColor(0xff000000);setBackgroundColor(0xffffffff);
            views.add(this);
        }
        protected void onAttachedToWindow() { super.onAttachedToWindow();attaches++; }
        protected void onDetachedFromWindow() { super.onDetachedFromWindow();detaches++; }
        protected void onSizeChanged(int w,int h,int oldW,int oldH) { sizes++; }
    }
    static class Callbacks implements Application.ActivityLifecycleCallbacks {
        void record(Activity a,String event) { observed.add((a instanceof First?"A.":"B.")+event); }
        public void onActivityCreated(Activity a,Bundle state) { record(a,"create"); }
        public void onActivityStarted(Activity a) { record(a,"start"); }
        public void onActivityResumed(Activity a) { record(a,"resume"); }
        public void onActivityPaused(Activity a) { record(a,"pause"); }
        public void onActivityStopped(Activity a) { record(a,"stop"); }
        public void onActivitySaveInstanceState(Activity a,Bundle state) {}
        public void onActivityDestroyed(Activity a) { record(a,"destroy"); }
    }
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static final class First extends Activity {
        protected void onCreate(Bundle state) {
            registerActivityLifecycleCallbacks(new Callbacks() {
                void record(Activity activity,String event) { check(activity==First.this,"local callback received another activity"); }
                public void onActivityPostCreated(Activity activity,Bundle state) { postCreated++; }
            });
            setTitle("Navigation FIRST");
            events.add("A.create");setContentView(new TrackingText(this,"Navigation: first"));
            Handler main=new Handler(Looper.getMainLooper());
            Thread worker=new Thread(() -> main.postDelayed(() -> {
                check(Thread.currentThread()==owner,"worker callback ran outside UI thread");
                startActivityForResult(new Intent(this,Second.class).putExtra("input","中文"),42);
            },500));worker.start();
        }
        protected void onStart(){events.add("A.start");}
        protected void onResume(){events.add("A.resume");}
        protected void onPause(){events.add("A.pause");}
        protected void onStop(){events.add("A.stop");}
        protected void onRestart(){events.add("A.restart");}
        protected void onDestroy(){events.add("A.destroy");}
        protected void onActivityResult(int request,int result,Intent data) {
            check(request==42 && result==RESULT_OK && data.getStringExtra("output").equals("ཀ"),"result data lost");
            int[] invoked={0};
            android.window.OnBackInvokedCallback low=() -> invoked[0]=1;
            android.window.OnBackInvokedCallback high=() -> invoked[0]=2;
            android.window.OnBackInvokedDispatcher dispatcher=getOnBackInvokedDispatcher();
            dispatcher.registerOnBackInvokedCallback(0,low);dispatcher.registerOnBackInvokedCallback(100,high);
            onKeyDown(android.view.KeyEvent.KEYCODE_BACK,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_BACK));
            check(invoked[0]==2 && !isFinishing(),"back callback priority ignored");
            dispatcher.unregisterOnBackInvokedCallback(high);
            onKeyDown(android.view.KeyEvent.KEYCODE_BACK,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_BACK));
            check(invoked[0]==1,"back callback removal failed");dispatcher.unregisterOnBackInvokedCallback(low);
            check(getContentView().findOnBackInvokedDispatcher()==dispatcher,"view back dispatcher missing");
            events.add("A.result");new Handler().postDelayed(this::finish,500);
        }
    }
    public static final class Second extends Activity {
        protected void onCreate(Bundle state) {
            check(getIntent().getStringExtra("input").equals("中文"),"intent extras lost");
            setTaskDescription(new ActivityManager.TaskDescription("Navigation SECOND 中文 😀",null,0));
            events.add("B.create");setContentView(new TrackingText(this,"Navigation: second 中文"));
            new Handler().postDelayed(() -> {
                check(getContentView().isAttachedToWindow(),"second view never attached to live window");
                setResult(RESULT_OK,new Intent().putExtra("output","ཀ"));finish();
            },500);
        }
        protected void onStart(){events.add("B.start");}
        protected void onResume(){events.add("B.resume");}
        protected void onPause(){events.add("B.pause");}
        protected void onStop(){events.add("B.stop");}
        protected void onDestroy(){events.add("B.destroy");}
    }
    public static void main(String[] args) {
        Looper.prepareMainLooper();Application app=new Application();app.attach(args[0]);app.attachPackage("org.isakai.navigation");
        app.registerActivityLifecycleCallbacks(new Callbacks());
        First first=new First();first.attach(app);first.runWindowed("Isakai navigation regression","org.isakai.navigation");
        List<String> expected=List.of("A.create","A.start","A.resume","A.pause","B.create","B.start","B.resume","A.stop",
                "B.pause","B.stop","B.destroy","A.restart","A.start","A.result","A.resume","A.pause","A.stop","A.destroy");
        check(events.equals(expected),"Lifecycle mismatch: "+events);
        check(observed.equals(expected.stream().filter(event -> !event.endsWith("restart") && !event.endsWith("result")).toList()),"Lifecycle observers mismatch: "+observed);
        check(postCreated==1,"post-created callback registered during onCreate did not run exactly once");
        for(TrackingText view:views) {
            check(view.attaches>0 && view.attaches==view.detaches && !view.isAttachedToWindow(),"view attachment cleanup mismatch");
            check(view.sizes>0,"live window never delivered size callback");
        }
        Looper.myLooper().quit();System.out.println("PASS: live activity navigation/result/lifecycle and main-thread dispatch "+events);
    }
}
