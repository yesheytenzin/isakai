// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import java.util.ArrayDeque;

/**
 * VelocityTracker: movement history with pixels/sec velocity.
 * Single-pointer plus per-pointer-id queries over recent samples.
 */
public final class VelocityTracker {
    private static final class Sample {
        final long time;
        final float x;
        final float y;
        Sample(long time, float x, float y) {
            this.time = time;
            this.x = x;
            this.y = y;
        }
    }

    private static final int MAX_SAMPLES = 32;
    private final java.util.HashMap<Integer, ArrayDeque<Sample>> histories =
            new java.util.HashMap<>();
    private final java.util.HashMap<Integer, float[]> velocities =
            new java.util.HashMap<>();
    private int units = 1000;

    private VelocityTracker() {}

    public static VelocityTracker obtain() {
        return new VelocityTracker();
    }

    public void recycle() {
        clear();
    }

    public void clear() {
        histories.clear();
        velocities.clear();
    }

    public void addMovement(MotionEvent event) {
        if (event == null) return;
        long t = event.getEventTime();
        int n = Math.max(1, event.getPointerCount());
        for (int i = 0; i < n; i++) {
            int id = n == 1 ? 0 : event.getPointerId(i);
            ArrayDeque<Sample> q = histories.get(id);
            if (q == null) {
                q = new ArrayDeque<>();
                histories.put(id, q);
            }
            q.addLast(new Sample(t, event.getX(i), event.getY(i)));
            while (q.size() > MAX_SAMPLES) q.removeFirst();
        }
    }

    public void computeCurrentVelocity(int units) {
        computeCurrentVelocity(units, Float.MAX_VALUE);
    }

    public void computeCurrentVelocity(int units, float maxVelocity) {
        this.units = units <= 0 ? 1000 : units;
        velocities.clear();
        for (java.util.Map.Entry<Integer, ArrayDeque<Sample>> e : histories.entrySet()) {
            ArrayDeque<Sample> q = e.getValue();
            if (q.size() < 2) {
                velocities.put(e.getKey(), new float[] {0, 0});
                continue;
            }
            Sample first = q.getFirst();
            Sample last = q.getLast();
            long dt = last.time - first.time;
            if (dt <= 0) {
                velocities.put(e.getKey(), new float[] {0, 0});
                continue;
            }
            float vx = (last.x - first.x) / dt * this.units;
            float vy = (last.y - first.y) / dt * this.units;
            float cap = Math.abs(maxVelocity);
            vx = Math.max(-cap, Math.min(cap, vx));
            vy = Math.max(-cap, Math.min(cap, vy));
            velocities.put(e.getKey(), new float[] {vx, vy});
        }
    }

    public float getXVelocity() {
        return getXVelocity(0);
    }

    public float getYVelocity() {
        return getYVelocity(0);
    }

    public float getXVelocity(int id) {
        float[] v = velocities.get(id);
        return v == null ? 0 : v[0];
    }

    public float getYVelocity(int id) {
        float[] v = velocities.get(id);
        return v == null ? 0 : v[1];
    }
}
