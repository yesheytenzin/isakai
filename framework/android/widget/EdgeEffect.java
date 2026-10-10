// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;

/** Overscroll glow: state recorded, never rendered headless. */
public class EdgeEffect {
    private int width;
    private int height;
    private float pullDistance;
    private boolean finished = true;

    public EdgeEffect(Context context) {
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void onPull(float deltaDistance) {
        pullDistance += deltaDistance;
        finished = false;
    }

    public void onPull(float deltaDistance, float displacement) {
        onPull(deltaDistance);
    }

    public void onAbsorb(int velocity) {
        pullDistance = Math.min(1.0f, Math.abs(velocity) / 10000.0f);
        finished = false;
    }

    public void onRelease() {
        pullDistance = 0.0f;
        finished = true;
    }

    public void finish() {
        pullDistance = 0.0f;
        finished = true;
    }

    public boolean isFinished() {
        return finished;
    }

    public float getDistance() {
        return pullDistance;
    }

    public boolean draw(Canvas canvas) {
        return false;
    }

    public int getMaxHeight() {
        return height;
    }

    public void setColor(int color) {
    }

    public int getColor() {
        return 0;
    }
}
