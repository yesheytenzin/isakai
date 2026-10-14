// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.nio.ByteBuffer;
import org.isakai.bridge.IsakaiDesktop;
import org.isakai.bridge.IsakaiWindow;

/**
 * Minimal Activity: headless lifecycle (M1) plus windowed rendering (M2)
 * with the M3 input pump. M1 drives onCreate/onStart/onResume/onPause/
 * onStop/onDestroy in order; runWindowed additionally posts the content
 * view to a Wayland toplevel.
 */
public class Activity extends android.content.Context implements android.view.Window.Callback {
    private Application application;
    private boolean created;
    private View contentView;
    private volatile boolean backPressed;
    private boolean windowFocused;
    /** Result code for a successful startActivityForResult round-trip. */
    public static final int RESULT_OK = -1;
    /** Result code for a cancelled startActivityForResult round-trip. */
    public static final int RESULT_CANCELED = 0;
    // Pending file-picker: request/handle from startActivityForResult, resolved by pollDesktopResults.
    private int pendingFileRequestCode = -1;
    private long pendingFileReq = 0;
    private boolean pendingFileSave = false;
    // Window-bound clipboard manager (re-bound on each runWindowed open).
    private ClipboardManager clipboardManager = new ClipboardManager(null);
    private final android.content.ContentResolver contentResolver = new android.content.ContentResolver() {};
    // Touch staging: kind-7 headers + kind-8 rows may split across
    // native batches; accumulate rows until the header's count arrives.
    private int touchHeaderRaw = -1;
    private int touchHeaderCount = 0;
    private long touchHeaderTime = 0;
    private long gestureDownTime = 0;
    private final java.util.ArrayList<Float> touchXs = new java.util.ArrayList<Float>();
    private final java.util.ArrayList<Float> touchYs = new java.util.ArrayList<Float>();
    private final java.util.ArrayList<Integer> touchIds = new java.util.ArrayList<Integer>();
    /** Storage surface: delegate to the attached Application's Context. */
    private android.content.Context storage() {
        if (application == null) throw new IllegalStateException("activity not attached");
        return application;
    }

    public void attach(Application application) {
        this.application = application;
        super.attach(application.getDataDirString());
        super.attachPackage(application.getPackageName());
    }

    public Application getApplication() {
        return application;
    }

    /**
     * Run immediately on the UI thread, otherwise enqueue on its Looper.
     */
    public void runOnUiThread(Runnable action) {
        if (action == null) return;
        android.os.Looper main = android.os.Looper.getMainLooper();
        if (main != null && main.isCurrentThread()) action.run();
        else new android.os.Handler(main).post(action);
    }

    public void registerActivityLifecycleCallbacks(
            Application.ActivityLifecycleCallbacks callback) {
        if(callback!=null)localLifecycleCallbacks.addIfAbsent(callback);
    }

    public void unregisterActivityLifecycleCallbacks(
            Application.ActivityLifecycleCallbacks callback) {
        localLifecycleCallbacks.remove(callback);
    }

    @Override
    public android.content.res.Resources getResources() {
        return application == null
                ? android.content.res.Resources.getSystem()
                : application.getResources();
    }

    @Override
    public java.io.File getFilesDir() {
        return storage().getFilesDir();
    }

    @Override
    public java.io.File getCacheDir() {
        return storage().getCacheDir();
    }

    @Override
    public java.io.File getCodeCacheDir() {
        return storage().getCodeCacheDir();
    }

    @Override
    public java.io.File getNoBackupFilesDir() {
        return storage().getNoBackupFilesDir();
    }

    @Override
    public java.io.File getDataDir() {
        return storage().getDataDir();
    }

    @Override
    public java.io.File getDir(String name, int mode) {
        return storage().getDir(name, mode);
    }

    @Override
    public java.io.File getFileStreamPath(String name) {
        return storage().getFileStreamPath(name);
    }

    @Override
    public java.io.File getSharedPrefsFile(String name) {
        return storage().getSharedPrefsFile(name);
    }

    @Override
    public java.io.File getDatabasePath(String name) {
        return storage().getDatabasePath(name);
    }

    @Override
    public String[] fileList() {
        return storage().fileList();
    }

    @Override
    public String[] databaseList() {
        return storage().databaseList();
    }

    @Override
    public boolean deleteFile(String name) {
        return storage().deleteFile(name);
    }

    @Override
    public boolean deleteDatabase(String name) {
        return storage().deleteDatabase(name);
    }

    @Override
    public java.io.FileInputStream openFileInput(String name) throws java.io.IOException {
        return storage().openFileInput(name);
    }

    @Override
    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.IOException {
        return storage().openFileOutput(name, mode);
    }

    @Override
    public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
        return storage().getSharedPreferences(name, mode);
    }

    /** Convenience: activity-private prefs (MODE_PRIVATE). */
    public android.content.SharedPreferences getPreferences(int mode) {
        return getSharedPreferences(getClass().getSimpleName(), mode);
    }

    @Override
    public void drainQueuedWork() {
        storage().drainQueuedWork();
    }

    /**
     * Service lookup: {@code CLIPBOARD_SERVICE} returns the window-bound
     * clipboard manager; misses fall through to the attached Application,
     * then the Context default (layout inflater and future services).
     */
    @Override
    public Object getSystemService(String name) {
        if (ClipboardManager.CLIPBOARD_SERVICE.equals(name)) return clipboardManager;
        if (application != null) {
            Object svc = application.getSystemService(name);
            if (svc != null) return svc;
        }
        return super.getSystemService(name);
    }

    /** In-process content resolver bound to the provider registry. */
    @Override
    public android.content.ContentResolver getContentResolver() {
        return contentResolver;
    }

    /** Result hook for startActivityForResult file picks; empty default. */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}

    /** Notification action tap hook; {@code key} is the Builder action key. */
    protected void onNotificationAction(long platId, String key) {}

    /** Launch an intent: VIEW opens the Uri, pickers delegate (CREATE yields a writable file). */
    public void startActivity(Intent intent) {
        if (intent == null) return;
        if (intent.getComponent() != null) { startActivityForResult(intent, -1); return; }
        String action = intent.getAction();
        if (Intent.ACTION_GET_CONTENT.equals(action) || Intent.ACTION_OPEN_DOCUMENT.equals(action)
                || Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            startActivityForResult(intent, -1);
            return;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data == null) return;
            String scheme = data.getScheme();
            if (scheme == null
                    || scheme.equals("http") || scheme.equals("https")
                    || scheme.equals("content") || scheme.equals("file")) {
                try {
                    IsakaiDesktop.openUri(data.toString());
                } catch (UnsatisfiedLinkError e) {
                    // Headless/no-bridge run: startActivity is a no-op.
                } catch (IllegalStateException e) {
                    // Headless/no-bridge run: startActivity is a no-op.
                }
            }
        }
    }

    /** Launch a file picker; VIEW behaves like {@link #startActivity}. */
    public void startActivityForResult(Intent intent, int requestCode) {
        if (intent == null) return;
        if (intent.getComponent() != null) {
            if (navigator == null) throw new android.content.ActivityNotFoundException("No active activity host");
            navigator.launch(this, intent, requestCode);
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            startActivity(intent);
            return;
        }
        boolean save = Intent.ACTION_CREATE_DOCUMENT.equals(action);
        if (!Intent.ACTION_GET_CONTENT.equals(action) && !Intent.ACTION_OPEN_DOCUMENT.equals(action)
                && !save) {
            return;
        }
        String title = intent.getStringExtra(Intent.EXTRA_TITLE);
        if (title == null) title = getDataDirString();
        // Headless/no-bridge save: the openFile throw below routes to
        // the files/-backed fallback; the bridge path yields a staged
        // (app-uid owned, writable) path instead.
        try {
            long req = IsakaiDesktop.openFile(title);
            pendingFileRequestCode = requestCode;
            pendingFileReq = req;
            pendingFileSave = save;
        } catch (UnsatisfiedLinkError e) {
            if (save) {
                deliverSaveFallback(requestCode, title);
            }
            // Headless/no-bridge run: no picker, no pending request.
        } catch (IllegalStateException e) {
            if (save) {
                deliverSaveFallback(requestCode, title);
            }
            // Headless/no-bridge run: no picker, no pending request.
        }
    }

    /**
     * No-bridge save fallback: create {@code title} (or {@code "save.bin"})
     * under files/ and deliver its file:// Uri as RESULT_OK. Fail-closed:
     * when creation fails, deliver RESULT_CANCELED.
     */
    private void deliverSaveFallback(int requestCode, String title) {
        String name = title == null || title.isEmpty() ? "save.bin" : new java.io.File(title).getName();
        if (name.isEmpty()) name = "save.bin";
        try {
            java.io.File f = getFileStreamPath(name);
            java.io.File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!f.exists() && !f.createNewFile()) {
                onActivityResult(requestCode, RESULT_CANCELED, null);
                return;
            }
            Intent data = new Intent();
            data.setData(Uri.parse("file://" + f.getAbsolutePath()));
            onActivityResult(requestCode, RESULT_OK, data);
        } catch (Exception e) {
            onActivityResult(requestCode, RESULT_CANCELED, null);
        }
    }

    /**
     * Save-mode writeback: the staged path is app-uid owned; ensure the
     * file exists and is writable. Null when it cannot be made
     * writable (fail-closed: caller delivers a dataless RESULT_OK).
     */
    private static String ensureWritableStaged(String path) {
        try {
            java.io.File f = new java.io.File(path);
            java.io.File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!f.exists() && !f.createNewFile()) return null;
            if (!f.canWrite()) return null;
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    /** Drain one desktop event batch: file results, action taps, close requests. */
    private void pollDesktopResults() {
        int[] batch = new int[8 * 8];
        int n;
        try {
            n = IsakaiDesktop.poll(batch);
        } catch (UnsatisfiedLinkError e) {
            return;
        } catch (IllegalStateException e) {
            return;
        }
        if (n <= 0) return;
        for (int i = 0; i < n; i++) {
            int base = i * 8;
            int kind = batch[base];
            if (kind == 12) {
                long req = IsakaiDesktop.join(batch[base + 1], batch[base + 2]);
                int status = batch[base + 3];
                if (req != pendingFileReq || pendingFileReq == 0) {
                    IsakaiDesktop.closeReq(req);
                    continue;
                }
                int code = pendingFileRequestCode;
                boolean save = pendingFileSave;
                pendingFileRequestCode = -1;
                pendingFileReq = 0;
                pendingFileSave = false;
                if (status == 1) {
                    String path = IsakaiDesktop.fileResult(req);
                    Intent data = new Intent();
                    if (path != null && path.length() > 0) {
                        if (save) path = ensureWritableStaged(path);
                        if (path != null) data.setData(Uri.parse("file://" + path));
                    }
                    IsakaiDesktop.closeReq(req);
                    onActivityResult(code, RESULT_OK, data);
                } else if (status == -1) {
                    IsakaiDesktop.closeReq(req);
                    onActivityResult(code, RESULT_CANCELED, null);
                } else {
                    IsakaiDesktop.closeReq(req);
                }
            } else if (kind == 11) {
                long platId = IsakaiDesktop.join(batch[base + 1], batch[base + 2]);
                int slot = batch[base + 3];
                String key = IsakaiDesktop.actionKey(slot);
                if (key != null) onNotificationAction(platId, key);
            } else if (kind == 10) {
                // Portal/notification close (kind "close"/"close-notif"): the
                // handle is released; this is NOT the compositor's toplevel
                // close, which arrives as window.run() == 0.
                long req = IsakaiDesktop.join(batch[base + 1], batch[base + 2]);
                IsakaiDesktop.closeReq(req);
            }
        }
    }

    public void setContentView(View view) {
        this.contentView = view;
        installViewTreeLifecycleOwner(view);
    }

    public void setContentView(View view, android.view.ViewGroup.LayoutParams params) {
        if (view == null) throw new IllegalArgumentException("view == null");
        if (params != null) view.setLayoutParams(params);
        this.contentView = view;
        installViewTreeLifecycleOwner(view);
    }

    /**
     * ComposeView looks up the AndroidX lifecycle owner through the generated
     * view-tree tag before it attaches. The app's translated AndroidX classes
     * provide the owner implementation, so install it without coupling the
     * framework API to an app-private type.
     */
    private void installViewTreeLifecycleOwner(View view) {
        if (view == null) return;
        try {
            ClassLoader loader = application.getClass().getClassLoader();
            Class<?> lifecycleOwner = Class.forName("androidx.lifecycle.v", false, loader);
            Class<?> savedOwner = Class.forName("l4.g", false, loader);
            Class<?> lifecycle = Class.forName("androidx.lifecycle.x", false, loader);
            final Object[] lifecycleRef = new Object[1];
            Object owner = java.lang.reflect.Proxy.newProxyInstance(loader,
                    new Class<?>[]{lifecycleOwner, savedOwner}, (proxy, method, args) -> {
                        if ("k".equals(method.getName())) return lifecycleRef[0];
                        if ("d".equals(method.getName()))
                            return Class.forName("l4.e", false, loader).getConstructor().newInstance();
                        return null;
                    });
            lifecycleRef[0] = lifecycle.getConstructor(lifecycleOwner).newInstance(owner);
            view.setTag(0x7f09034f, owner);
            view.setTag(0x7f090351, owner);
            Class<?> composeOwner = Class.forName("j0.g2", false, loader);
            Class<?> coroutineContext = Class.forName("a9.i", false, loader);
            Object context = Class.forName("a9.j", false, loader).getField("k").get(null);
            view.setTag(0x7f090052, composeOwner.getConstructor(coroutineContext)
                    .newInstance(context));
        } catch (ClassNotFoundException e) {
            // Ordinary apps do not ship translated Compose lifecycle classes.
        } catch (ReflectiveOperationException e) {
            System.err.println("isakai: unable to install translated lifecycle owner: " + e);
        }
    }

    void setContentViewFromWindow(View view) { this.contentView = view; }

    public void setContentView(int resource) {
        setContentView(android.view.LayoutInflater.from(this).inflate(resource, null, false));
    }

    public <T extends View> T findViewById(int id) {
        return contentView == null ? null : (T) contentView.findViewById(id);
    }

    public View getContentView() {
        return contentView;
    }

    private final java.util.concurrent.CopyOnWriteArrayList<Application.ActivityLifecycleCallbacks> localLifecycleCallbacks=new java.util.concurrent.CopyOnWriteArrayList<>();
    private int lifecycleState=-1; // -1 new, 0 created/stopped, 1 started/paused, 2 resumed, -2 destroyed
    private void dispatchLifecycle(java.util.function.Consumer<Application.ActivityLifecycleCallbacks> dispatch) {
        if(application!=null)for(Application.ActivityLifecycleCallbacks callback:application.lifecycleCallbacksSnapshot())dispatch.accept(callback);
        for(Application.ActivityLifecycleCallbacks callback:localLifecycleCallbacks.toArray(new Application.ActivityLifecycleCallbacks[0]))dispatch.accept(callback);
    }
    private void performCreate() {
        if(lifecycleState!=-1)return;
        dispatchLifecycle(callback -> callback.onActivityPreCreated(this,null));
        onCreate();
        lifecycleState=0;
        dispatchLifecycle(callback -> callback.onActivityCreated(this,null));
        dispatchLifecycle(callback -> callback.onActivityPostCreated(this,null));
    }
    private void performStart() {
        if(lifecycleState!=0)return;
        dispatchLifecycle(callback -> callback.onActivityPreStarted(this));
        onStart();
        lifecycleState=1;
        dispatchLifecycle(callback -> callback.onActivityStarted(this));
        dispatchLifecycle(callback -> callback.onActivityPostStarted(this));
    }
    private void performResume() {
        if(lifecycleState!=1)return;
        dispatchLifecycle(callback -> callback.onActivityPreResumed(this));
        onResume();
        lifecycleState=2;
        dispatchLifecycle(callback -> callback.onActivityResumed(this));
        dispatchLifecycle(callback -> callback.onActivityPostResumed(this));
    }
    private void performPause() {
        if(lifecycleState!=2)return;
        dispatchLifecycle(callback -> callback.onActivityPrePaused(this));
        onPause();
        lifecycleState=1;
        dispatchLifecycle(callback -> callback.onActivityPaused(this));
        dispatchLifecycle(callback -> callback.onActivityPostPaused(this));
    }
    private void performStop() {
        if(lifecycleState==2)performPause();
        if(lifecycleState!=1)return;
        dispatchLifecycle(callback -> callback.onActivityPreStopped(this));
        onStop();
        lifecycleState=0;
        dispatchLifecycle(callback -> callback.onActivityStopped(this));
        dispatchLifecycle(callback -> callback.onActivityPostStopped(this));
    }
    private void performDestroy() {
        if(lifecycleState>0)performStop();
        if(lifecycleState<0)return;
        dispatchLifecycle(callback -> callback.onActivityPreDestroyed(this));
        onDestroy();
        lifecycleState=-2;
        dispatchLifecycle(callback -> callback.onActivityDestroyed(this));
        dispatchLifecycle(callback -> callback.onActivityPostDestroyed(this));
        localLifecycleCallbacks.clear();
    }

    protected void onCreate() {
        onCreate(null);
    }

    protected void onCreate(android.os.Bundle savedInstanceState) {}

    protected void onStart() {}

    protected void onResume() {}
    protected void onRestart() {}

    protected void onPause() {
        drainQueuedWork();
    }

    protected void onStop() {
        drainQueuedWork();
    }

    protected void onDestroy() {
        drainQueuedWork();
    }

    /** Stub: fired on keyboard/pointer focus loss. No pause semantics. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        windowFocused = hasFocus;
    }

    /** Headless: options menu unsupported; request is a no-op. */
    public void invalidateOptionsMenu() {}

    /** Default BACK handling: request loop exit. */
    public void onBackPressed() {
        finish();
    }

    private Navigator navigator;
    private Activity resultParent;
    private int requestCode = -1;
    private int resultCode = RESULT_CANCELED;
    private Intent resultData;
    public final void setResult(int code) { setResult(code, null); }
    public final void setResult(int code, Intent data) { resultCode = code; resultData = data; }
    private static final class Navigator {
        IsakaiWindow.Window window;
        final java.util.ArrayList<Activity> stack = new java.util.ArrayList<>();
        final java.util.ArrayDeque<Runnable> pending = new java.util.ArrayDeque<>();
        Activity top() { return stack.get(stack.size() - 1); }
        void launch(Activity caller, Intent intent, int code) {
            Intent deliveredIntent = new Intent(intent);
            android.content.ComponentName component = intent.getComponent();
            if (!caller.getPackageName().equals(component.getPackageName()))
                throw new android.content.ActivityNotFoundException("External explicit activities are unsupported");
            final Activity next;
            final int destinationTheme;
            try {
                String name = component.getClassName();
                if (name.startsWith(".")) name = component.getPackageName() + name;
                else if (name.indexOf('.') < 0) name = component.getPackageName() + "." + name;
                destinationTheme = caller.getPackageManager().getActivityInfo(component, 0).theme;
                next = Class.forName(name).asSubclass(Activity.class).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException
                    | android.content.pm.PackageManager.NameNotFoundException e) {
                throw new android.content.ActivityNotFoundException("Cannot resolve " + component.getClassName());
            }
            pending.add(() -> {
                Activity old = top();
                old.performPause(); old.windowFocused = false;
                next.attach(caller.application);
                next.setIntent(deliveredIntent);
                next.attachTheme(caller.baseThemeForNav(), destinationTheme);
                next.navigator = this;
                next.resultParent = caller; next.requestCode = code;
                next.created = true;
                stack.add(next);
                next.performCreate(); next.performStart(); next.performResume();
                old.performStop();
                next.windowFocused = true;
            });
        }
        void advance() {
            // Bound dispatch so repeated navigation cannot starve the host pump.
            for (int i = 0; i < 64 && !pending.isEmpty(); i++) pending.remove().run();
            // Finished, stopped activities must not remain as a back destination.
            for (int index=stack.size()-2; index>=0; index--) {
                Activity stopped=stack.get(index);
                if (stopped.finishing) {
                    stack.remove(index);
                    stopped.performDestroy(); stopped.drainQueuedWork();
                }
            }
            while (stack.size() > 1 && top().backPressed) {
                Activity ended = stack.remove(stack.size() - 1);
                ended.performPause(); ended.performStop(); ended.performDestroy(); ended.drainQueuedWork();
                Activity previous = top();
                previous.onRestart(); previous.performStart();
                if (ended.requestCode >= 0 && ended.resultParent != null)
                    ended.resultParent.onActivityResult(ended.requestCode, ended.resultCode, ended.resultData);
                previous.performResume(); previous.windowFocused = true;
            }
        }
        void destroy() {
            pending.clear();
            boolean top = true;
            while (!stack.isEmpty()) {
                Activity a = stack.remove(stack.size() - 1);
                if (top) { a.performPause(); a.performStop(); }
                top = false;
                a.performDestroy(); a.drainQueuedWork(); a.navigator = null;
            }
        }
    }
    private Intent launchIntent = new Intent();
    private boolean finishing;

    public Intent getIntent() {
        return launchIntent;
    }


    public void setIntent(Intent intent) {
        this.launchIntent = intent;
    }

    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    public void finish() {
        finishing = true;
        backPressed = true;
    }

    public void finishAffinity() {
        finish();
    }

    public boolean isFinishing() {
        return finishing;
    }

    public boolean isChangingConfigurations() {
        return false;
    }

    public boolean isChild() {
        return false;
    }

    public android.app.ActionBar getActionBar() {
        return null;
    }

    private final HeadlessWindow headlessWindow = new HeadlessWindow(this);

    public android.view.Window getWindow() {
        if (headlessWindow.getCallback() == null) headlessWindow.setCallback(this);
        return headlessWindow;
    }

    /** Currently focused view, or null when nothing holds focus. */
    public android.view.View getCurrentFocus() {
        if (contentView == null) return null;
        return contentView.findFocus();
    }

    private final FragmentManager fragmentManager = new FragmentManager();

    public FragmentManager getFragmentManager() {
        return fragmentManager;
    }

    private int themeRes;
    private int manifestActTheme;

    public void setTheme(int resid) {
        themeRes = resid;
    }

    /** Manifest base themes (application via Context, then activity); setTheme layers above. */
    public void attachTheme(int appTheme, int actTheme) {
        super.attachTheme(appTheme);
        manifestActTheme = actTheme;
    }

    public void setShowWhenLocked(boolean showWhenLocked) {}

    public void setTurnScreenOn(boolean turnScreenOn) {}

    public void setInheritShowWhenLocked(boolean inheritShowWhenLocked) {}


    @Override
    public android.content.res.Resources.Theme getTheme() {
        android.content.res.Resources.Theme theme = super.getTheme();
        if (manifestActTheme != 0) theme.applyStyle(manifestActTheme, true);
        if (themeRes != 0) theme.applyStyle(themeRes, true);
        return theme;
    }


    public android.view.MenuInflater getMenuInflater() {
        return new android.view.MenuInflater(this);
    }

    public android.view.LayoutInflater getLayoutInflater() {
        return android.view.LayoutInflater.from(this);
    }

    private final BackDispatcher backDispatcher = new BackDispatcher();
    private static final class BackDispatcher implements android.window.OnBackInvokedDispatcher {
        private record Entry(int priority,android.window.OnBackInvokedCallback callback) {}
        private final java.util.ArrayList<Entry> callbacks=new java.util.ArrayList<>();
        public synchronized void registerOnBackInvokedCallback(int priority,android.window.OnBackInvokedCallback callback) {
            if(priority<0)throw new IllegalArgumentException("negative priority");
            if(callback==null)throw new NullPointerException("callback");
            unregisterOnBackInvokedCallback(callback);callbacks.add(new Entry(priority,callback));
        }
        public synchronized void unregisterOnBackInvokedCallback(android.window.OnBackInvokedCallback callback) {
            callbacks.removeIf(entry -> entry.callback()==callback);
        }
        boolean dispatch() {
            Entry chosen=null;
            synchronized(this) {
                for(Entry entry:callbacks)if(chosen==null || entry.priority()>=chosen.priority())chosen=entry;
            }
            if(chosen==null)return false;
            chosen.callback().onBackInvoked();return true;
        }
    }
    public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher() { return backDispatcher; }

    public android.content.ComponentName getComponentName() {
        return new android.content.ComponentName(getPackageName(), getClass().getName());
    }

    private CharSequence title;
    public CharSequence getTitle() { return title==null?"":title; }
    public void setTitle(CharSequence title) {
        this.title=title;
        if(navigator!=null && !navigator.stack.isEmpty() && navigator.top()==this && navigator.window!=null)
            navigator.window.setTitle(title==null?"":title.toString());
    }
    public void setTitle(int resource) { setTitle(getResources().getText(resource)); }
    public void setTaskDescription(ActivityManager.TaskDescription description) {
        if(description==null)throw new NullPointerException("description");
        if(description.getLabel()!=null)setTitle(description.getLabel());
        if(description.getIcon()!=null || description.getPrimaryColor()!=0)
            org.isakai.runtime.Capabilities.unavailable("android.app.Activity.taskIconAndColor");
    }

    public Object getLastNonConfigurationInstance() {
        return null;
    }

    public boolean navigateUpTo(Intent upIntent) {
        return false;
    }

    public void closeOptionsMenu() {}

    public void onCreateContextMenu(android.view.ContextMenu menu, android.view.View v,
            android.view.ContextMenu.ContextMenuInfo menuInfo) {}

    public android.view.View onCreateView(String name, android.content.Context context,
            android.util.AttributeSet attrs) {
        return null;
    }

    public android.view.View onCreateView(android.view.View parent, String name,
            android.content.Context context, android.util.AttributeSet attrs) {
        return null;
    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {}

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode,
            android.content.res.Configuration newConfig) {}

    public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {}
    /** Drive the full headless lifecycle. Returns after onDestroy. */
    public final void runHeadless() {
        android.os.Looper.prepareMainLooper();
        if (created) throw new IllegalStateException("activity already ran");
        created = true;
        performCreate();
        performStart();
        performResume();
        performPause();
        performStop();
        performDestroy();
        drainQueuedWork();
    }

    /** Key dispatch: content view first, then onKeyDown/onKeyUp. */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (contentView != null && contentView.dispatchKeyEvent(event)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return onKeyDown(event.getKeyCode(), event);
        } else {
            return onKeyUp(event.getKeyCode(), event);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if(!backDispatcher.dispatch())onBackPressed();
            return true;
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    /** Touch dispatch: content view first, then onTouchEvent. */
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (contentView != null && contentView.dispatchTouchEvent(event)) return true;
        return onTouchEvent(event);
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    // android.view.Window.Callback: Activity is the window's initial
    // callback (real PhoneWindow wiring); AppCompat replaces it.
    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return false;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(
            android.view.accessibility.AccessibilityEvent event) {
        return false;
    }

    @Override
    public View onCreatePanelView(int featureId) {
        return null;
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, android.view.Menu menu) {
        return false;
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, android.view.Menu menu) {
        return false;
    }

    @Override
    public boolean onMenuOpened(int featureId, android.view.Menu menu) {
        return false;
    }

    @Override
    public void onPanelClosed(int featureId, android.view.Menu menu) {}

    @Override
    public boolean onMenuItemSelected(int featureId, android.view.MenuItem item) {
        return false;
    }

    @Override
    public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) {}

    @Override
    public void onContentChanged() {}

    @Override
    public void onAttachedToWindow() {}

    @Override
    public void onDetachedFromWindow() {}

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {}

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    @Override
    public boolean onSearchRequested(android.view.SearchEvent searchEvent) {
        return false;
    }

    @Override
    public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) {
        return null;
    }

    @Override
    public android.view.ActionMode onWindowStartingActionMode(
            android.view.ActionMode.Callback callback, int type) {
        return null;
    }

    @Override
    public void onActionModeStarted(android.view.ActionMode mode) {}

    @Override
    public void onActionModeFinished(android.view.ActionMode mode) {}

    @Override
    public void onProvideKeyboardShortcuts(
            java.util.List<android.view.KeyboardShortcutGroup> data,
            android.view.Menu menu,
            int deviceId) {}

    /**
     * Drive the windowed lifecycle: onCreate..onResume, render the content
     * view into a Wayland window, dispatch until close, BACK, or the
     * finish, then onPause..onDestroy. Uses {@code title}
     * for the window title and {@code packageName} (always the package)
     * for the app id. Renders at the live window size: every configure
     * resize recreates the Canvas and re-lays out the content view.
     * Missing content view falls back to a blank View.
     *
     * <p>Present path prefers zero-copy (direct ByteBuffer over SHM);
     * falls back to the int[] copy path when mapping fails or
     * ISAKAI_ZEROCOPY=0 is set.
     *
     * <p>Input path: each 100ms run slice drains the native batch (8
     * ints/event), builds KeyEvent/MotionEvent values, and dispatches to
     * the content view. Redraws on dirty or resize.
     */
    public final void runWindowed(String title, String packageName) {
        android.os.Looper.prepareMainLooper();
        if (created) throw new IllegalStateException("activity already ran");
        created = true;
        navigator = new Navigator();
        navigator.stack.add(this);
        Throwable failure = null;
        try {
            performCreate();
            performStart();
            performResume();
            navigator.advance();
            Activity active = navigator.top();
            View root = active.contentView != null ? active.contentView : new View();
            IsakaiWindow.Window window = IsakaiWindow.Window.open(title, packageName, 640, 480);
            navigator.window=window;
            if(active.title!=null)window.setTitle(active.title.toString());
            clipboardManager = new ClipboardManager(window);
            try {
                windowFocused = true;
                installViewTreeLifecycleOwner(root);
                root.dispatchHostAttached();
                int width = 640;
                int height = 480;
                // Initial frame at the live size, not the 640x480 default.
                long packed = window.size();
                if (packed != 0) {
                    width = IsakaiWindow.unpackW(packed);
                    height = IsakaiWindow.unpackH(packed);
                }
                active.drawFrame(window, root, width, height);
                int[] batch = new int[8 * 32];
                int[] metrics = new int[8];
                while (true) {
                    android.os.Looper.getMainLooper().dispatchPending();
                    navigator.advance();
                    Activity top = navigator.top();
                    if (top != active || top.contentView != root) {
                        root.dispatchHostDetached();
                        active = top;
                        root = active.contentView != null ? active.contentView : new View();
                        installViewTreeLifecycleOwner(root);
                        root.dispatchHostAttached();
                        active.clipboardManager = clipboardManager;
                        window.setTitle(active.title==null?title:active.title.toString());
                        active.drawFrame(window, root, width, height);
                    }
                    if (active.backPressed) break;
                    int r = window.run(100);
                    if (r == 0) break;
                    // Input drain: poll until the native queue is empty.
                    while (true) {
                        int n;
                        try {
                            n = window.pollInput(batch);
                        } catch (IllegalStateException e) {
                            n = 0;
                            r = 0;
                            break;
                        }
                        if (n <= 0) break;
                        active.dispatchBatch(root, batch, n);
                    }
                    active.pollDesktopResults();
                    if (r == 0) break;
                    long resize = window.pollSize();
                    if (resize != 0) {
                        width = IsakaiWindow.unpackW(resize);
                        height = IsakaiWindow.unpackH(resize);
                        if (!drawFrameIfAlive(window, root, width, height)) break;
                        root.isDirtyAndClear();
                    } else if (root.isDirtyAndClear()) {
                        if (!drawFrameIfAlive(window, root, width, height)) break;
                    }
                    // Per-slice DisplayMetrics poll: fresh int[8] updates
                    // the system Resources snapshot for getResources().
                    try {
                        if (window.pollMetrics(metrics) == 1) applyMetrics(metrics);
                    } catch (IllegalStateException e) {
                        r = 0;
                        break;
                    }
                }
                // Loop-exit bookkeeping for subclasses/tests.
                if (backPressed) {
                    windowFocused = false;
                }
            } catch (RuntimeException | Error primary) {
                failure = primary;
                throw primary;
            } finally {
                try { root.dispatchHostDetached(); }
                catch (RuntimeException | Error cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
                finally {
                    navigator.window=null;
                    window.close();
                }
            }
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            try { navigator.destroy(); }
            catch (RuntimeException | Error cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    /** Backwards-compatible entry: title = package name. */
    public final void runWindowed(String packageName) {
        runWindowed(packageName, packageName);
    }

    /** Dispatch one drained batch (n events of 8 ints) to the root view. */
    private void dispatchBatch(View root, int[] batch, int n) {
        for (int i = 0; i < n; i++) {
            int base = i * 8;
            int kind = batch[base];
            switch (kind) {
                case 1: {
                    int action = batch[base + 1];
                    int keyCode = batch[base + 2];
                    int unicodeChar = batch[base + 3];
                    int metaState = batch[base + 4];
                    long time = packTime(batch[base + 5], batch[base + 6]);
                    int flags = batch[base + 7];
                    KeyEvent event = new KeyEvent(action, keyCode, unicodeChar, metaState, time, flags);
                    dispatchKeyEvent(event);
                    break;
                }
                case 2: {
                    int action = batch[base + 1];
                    float x = Float.intBitsToFloat(batch[base + 2]);
                    float y = Float.intBitsToFloat(batch[base + 3]);
                    int buttons = batch[base + 4];
                    long time = packTime(batch[base + 5], batch[base + 6]);
                    MotionEvent event = MotionEvent.obtain(action, x, y, buttons, time);
                    dispatchTouchEvent(event);
                    break;
                }
                case 5: {
                    boolean gained = batch[base + 1] != 0;
                    if (!gained && windowFocused) {
                        windowFocused = false;
                        onWindowFocusChanged(false);
                    } else if (gained && !windowFocused) {
                        windowFocused = true;
                    }
                    break;
                }
                case 6: {
                    boolean gained = batch[base + 1] != 0;
                    float x = Float.intBitsToFloat(batch[base + 2]);
                    float y = Float.intBitsToFloat(batch[base + 3]);
                    if (!gained && windowFocused) {
                        windowFocused = false;
                        onWindowFocusChanged(false);
                        // Orphaned gesture: cancel the touch target.
                        dispatchTouchEvent(MotionEvent.obtain(
                                MotionEvent.ACTION_CANCEL, x, y, 0, System.currentTimeMillis()));
                    } else if (gained && !windowFocused) {
                        windowFocused = true;
                    }
                    break;
                }
                case 7: {
                    // Clipboard selection hint uses count == 0 (owned by
                    // the clipboard manager); touch headers carry >= 1.
                    if (batch[base + 2] == 0) {
                        clipboardManager.onSelectionChanged(batch[base + 1]);
                        break;
                    }
                    touchHeaderRaw = batch[base + 1];
                    touchHeaderCount = batch[base + 2];
                    touchHeaderTime = packTime(batch[base + 3], batch[base + 4]);
                    touchXs.clear();
                    touchYs.clear();
                    touchIds.clear();
                    break;
                }
                case 8: {
                    if (touchHeaderCount <= 0) break;
                    touchIds.add(batch[base + 1]);
                    touchXs.add(Float.intBitsToFloat(batch[base + 2]));
                    touchYs.add(Float.intBitsToFloat(batch[base + 3]));
                    if (touchIds.size() >= touchHeaderCount) emitTouch();
                    break;
                }
                default:
                    break;
            }
        }
    }

    private static long packTime(int lo, int hi) {
        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }

    /** Build one MotionEvent from staged kind-7/8 rows and dispatch it. */
    private void emitTouch() {
        int n = touchIds.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = touchXs.get(i);
            ys[i] = touchYs.get(i);
            ids[i] = touchIds.get(i);
        }
        int masked = touchHeaderRaw & MotionEvent.ACTION_MASK;
        if (masked == MotionEvent.ACTION_DOWN) gestureDownTime = touchHeaderTime;
        MotionEvent event = MotionEvent.obtain(
                touchHeaderRaw, xs, ys, ids, 0, touchHeaderTime, gestureDownTime);
        touchHeaderRaw = -1;
        touchHeaderCount = 0;
        touchXs.clear();
        touchYs.clear();
        touchIds.clear();
        dispatchTouchEvent(event);
    }

    /** Apply a fresh int[8] metrics snapshot to the system Resources. */
    private static void applyMetrics(int[] m) {
        android.content.res.Resources r = android.content.res.Resources.getSystem();
        r.setMetrics(m[0], m[1], m[2], m[3] / 1000.0f, m[4] / 1000.0f, m[5] / 1000.0f, m[6] / 1000.0f);
    }

    /** Lay out the root at w×h, draw into a fresh Canvas, present it. */
    private static void drawFrame(IsakaiWindow.Window window, View root, int width, int height) {
        root.dispatchHostInsets();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        root.dispatchHostLayout();
        if(root.dispatchHostPreDraw())return;
        paintRoot(window, root, width, height);
    }

    /** True while the window still accepts frames; false once it is gone. */
    private static boolean windowAlive(IsakaiWindow.Window window) {
        try {
            return window.isOpen();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void paintRoot(IsakaiWindow.Window window, View root, int width, int height) {
        int[] slot = new int[1];
        ByteBuffer mapped = null;
        try {
            mapped = window.mapBuffer(slot, width, height);
        } catch (IllegalStateException e) {
            mapped = null;
        }
        if (mapped != null) {
            Canvas canvas = new Canvas(mapped, width, height);
            canvas.drawColor(Color.WHITE);
            root.draw(canvas);
            // ARGB->XRGB fixup for the direct backing: force alpha to 0xFF.
            // (Java-side twin of the nPost loop in src/bridge/jni.zig, which
            // applies the same mask on the copy path; kept separate because
            // one runs on the IntBuffer view, the other on a JVM int[].)
            for (int i = 0; i < width * height; i++) {
                int px = canvas.getIntBuffer().get(i);
                canvas.getIntBuffer().put(i, 0xFF000000 | (px & 0x00FFFFFF));
            }
            window.commit(width, height);
        } else {
            Canvas canvas = new Canvas(width, height);
            canvas.drawColor(Color.WHITE);
            root.draw(canvas);
            window.post(canvas.getPixels(), width, height, width);
        }
    }

    /**
     * Paint a frame, tolerating a window the compositor has destroyed. A
     * closed connection is not an application error: return false so the
     * window loop exits and the handle is closed cleanly in the finally
     * block (the app then reports its normal exit status).
     */
    private static boolean drawFrameIfAlive(IsakaiWindow.Window window, View root, int width, int height) {
        if (!windowAlive(window)) return false;
        try {
            drawFrame(window, root, width, height);
            return true;
        } catch (IllegalStateException gone) {
            return false;
        }
    }
}
