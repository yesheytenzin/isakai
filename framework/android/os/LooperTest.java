package android.os;
import java.util.ArrayList;
import java.util.List;
public final class LooperTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Looper loop = Looper.getMainLooper();
        Handler h = new Handler(loop);
        List<Integer> events = new ArrayList<>();
        Thread owner = Thread.currentThread();
        Thread worker = new Thread(() -> {
            check(Looper.myLooper() == null, "worker must not inherit main looper");
            h.post(() -> { check(Thread.currentThread() == owner, "wrong thread"); events.add(1); });
        });
        worker.start(); worker.join();
        check(events.isEmpty(), "post must enqueue");
        check(loop.getQueue() == loop.getQueue(), "unstable queue");
        loop.dispatchPending();
        check(events.equals(List.of(1)), "worker callback missing");
        Runnable cancelled = () -> { throw new AssertionError("cancelled callback ran"); };
        h.post(cancelled); h.removeCallbacks(cancelled);
        long start = SystemClock.uptimeMillis();
        h.postDelayed(() -> { check(SystemClock.uptimeMillis() - start >= 60, "early delay"); events.add(2); },60);
        loop.dispatchPending(); check(events.size() == 1, "delay ignored");
        Thread.sleep(80); loop.dispatchPending(); check(events.size() == 2, "delay lost");
        h.post(() -> events.add(4)); h.postAtFrontOfQueue(() -> events.add(3));
        loop.dispatchPending(); check(events.equals(List.of(1,2,3,4)), "ordering");
        int[] calls = {0};
        Runnable repeat = new Runnable() { public void run() { if (++calls[0] < 1000) h.post(this); } };
        h.post(repeat);
        while (calls[0] < 1000) loop.dispatchPending();
        Object token = new Object();
        h.postAtTime(cancelled,token,0); h.removeCallbacksAndMessages(token);
        loop.dispatchPending();
        android.view.Choreographer frames = android.view.Choreographer.getInstance();
        check(frames == android.view.Choreographer.getInstance(), "unstable choreographer");
        int[] frameCalls = {0};
        android.view.Choreographer.FrameCallback frame = nanos -> {
            check(Thread.currentThread() == owner, "frame on wrong thread");
            frameCalls[0]++;
        };
        frames.postFrameCallback(frame);
        check(frameCalls[0] == 0, "inline frame callback");
        loop.dispatchPending(); check(frameCalls[0] == 0, "early frame callback");
        Thread.sleep(30); loop.dispatchPending(); check(frameCalls[0] == 1, "frame missing");
        frames.postFrameCallbackDelayed(frame, 50); frames.removeFrameCallback(frame);
        Thread.sleep(80); loop.dispatchPending(); check(frameCalls[0] == 1, "cancelled frame ran");
        loop.quitSafely(); check(!h.post(() -> {}), "post accepted after quit");
        Thread other = new Thread(() -> { Looper.prepare(); new Handler().post(() -> Looper.myLooper().quit()); Looper.loop(); });
        other.start(); other.join(2000); check(!other.isAlive(), "loop did not quit");
        System.out.println("PASS: queue ownership/delay/order/cancel/nonrecursive/shutdown");
    }
}
