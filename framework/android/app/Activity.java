// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.nio.ByteBuffer;
import org.isakai.bridge.IsakaiWindow;

/**
 * Minimal Activity: headless lifecycle (M1) plus windowed rendering (M2)
 * with the M3 input pump. M1 drives onCreate/onStart/onResume/onPause/
 * onStop/onDestroy in order; runWindowed additionally posts the content
 * view to a Wayland toplevel.
 */
public class Activity {
    private Application application;
    private boolean created;
    private View contentView;
    private boolean backPressed;
    private boolean windowFocused;

    public void attach(Application application) {
        this.application = application;
    }

    public Application getApplication() {
        return application;
    }

    public void setContentView(View view) {
        this.contentView = view;
    }

    public View getContentView() {
        return contentView;
    }

    protected void onCreate() {}

    protected void onStart() {}

    protected void onResume() {}

    protected void onPause() {}

    protected void onStop() {}

    protected void onDestroy() {}

    /** Stub: fired on keyboard/pointer focus loss. No pause semantics. */
    protected void onWindowFocusChanged(boolean hasFocus) {}

    /** Default BACK handling: request loop exit. */
    protected void onBackPressed() {
        backPressed = true;
    }

    /** Drive the full headless lifecycle. Returns after onDestroy. */
    public final void runHeadless() {
        if (created) throw new IllegalStateException("activity already ran");
        created = true;
        onCreate();
        onStart();
        onResume();
        onPause();
        onStop();
        onDestroy();
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
            onBackPressed();
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

    /**
     * Drive the windowed lifecycle: onCreate..onResume, render the content
     * view into a Wayland window, dispatch until close, BACK, or the
     * 5000ms budget elapses, then onPause..onDestroy. Uses {@code title}
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
        if (created) throw new IllegalStateException("activity already ran");
        created = true;
        onCreate();
        onStart();
        onResume();
        try {
            View root = contentView != null ? contentView : new View();
            IsakaiWindow.Window window = IsakaiWindow.Window.open(title, packageName, 640, 480);
            try {
                backPressed = false;
                windowFocused = true;
                long deadline = System.currentTimeMillis() + 5000;
                int width = 640;
                int height = 480;
                // Initial frame at the live size, not the 640x480 default.
                long packed = window.size();
                if (packed != 0) {
                    width = IsakaiWindow.unpackW(packed);
                    height = IsakaiWindow.unpackH(packed);
                }
                drawFrame(window, root, width, height);
                int[] batch = new int[8 * 32];
                while (true) {
                    long now = System.currentTimeMillis();
                    if (now >= deadline || backPressed) break;
                    int r = window.run(Math.min(deadline - now, 100));
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
                        dispatchBatch(root, batch, n);
                    }
                    if (r == 0 || backPressed) break;
                    long resize = window.pollSize();
                    if (resize != 0) {
                        width = IsakaiWindow.unpackW(resize);
                        height = IsakaiWindow.unpackH(resize);
                        drawFrame(window, root, width, height);
                        root.isDirtyAndClear();
                    } else if (root.isDirtyAndClear()) {
                        drawFrame(window, root, width, height);
                    }
                }
                // Loop-exit bookkeeping for subclasses/tests.
                if (backPressed) {
                    windowFocused = false;
                }
            } finally {
                window.close();
            }
        } finally {
            onPause();
            onStop();
            onDestroy();
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
                default:
                    break;
            }
        }
    }

    private static long packTime(int lo, int hi) {
        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }

    /** Lay out the root at w×h, draw into a fresh Canvas, present it. */
    private static void drawFrame(IsakaiWindow.Window window, View root, int width, int height) {
        root.measure(width, height);
        root.layout(0, 0, width, height);
        paintRoot(window, root, width, height);
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
}
