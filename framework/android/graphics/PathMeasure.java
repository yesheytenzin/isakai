// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Contour measurer over Path verbs: control points flatten to segment
 * endpoints (curves approximate as chords).
 */
public class PathMeasure {
    private final List<float[]> contours = new ArrayList<>();
    private int contour;

    public PathMeasure() {}

    public PathMeasure(Path path, boolean forceClosed) {
        setPath(path, forceClosed);
    }

    public void setPath(Path path, boolean forceClosed) {
        contours.clear();
        contour = 0;
        if (path == null) return;
        List<Integer> verbs = path.verbs();
        List<Float> pts = path.points();
        List<Float> cur = new ArrayList<>();
        int pi = 0;
        float startX = 0, startY = 0;
        for (int verb : verbs) {
            switch (verb) {
                case 0: // MOVE
                    if (!cur.isEmpty()) {
                        contours.add(toArray(cur));
                        cur.clear();
                    }
                    startX = pts.get(pi);
                    startY = pts.get(pi + 1);
                    cur.add(startX);
                    cur.add(startY);
                    pi += 2;
                    break;
                case 1: // LINE
                    cur.add(pts.get(pi));
                    cur.add(pts.get(pi + 1));
                    pi += 2;
                    break;
                case 2: // QUAD
                    cur.add(pts.get(pi + 2));
                    cur.add(pts.get(pi + 3));
                    pi += 4;
                    break;
                case 3: // CUBIC
                    cur.add(pts.get(pi + 4));
                    cur.add(pts.get(pi + 5));
                    pi += 6;
                    break;
                case 4: // CLOSE
                    cur.add(startX);
                    cur.add(startY);
                    break;
                default:
                    break;
            }
        }
        if (!cur.isEmpty()) contours.add(toArray(cur));
    }

    private static float[] toArray(List<Float> in) {
        float[] out = new float[in.size()];
        for (int i = 0; i < out.length; i++) out[i] = in.get(i);
        return out;
    }

    public boolean nextContour() {
        if (contour + 1 >= contours.size()) return false;
        contour++;
        return true;
    }

    private float[] current() {
        return contour < contours.size() ? contours.get(contour) : null;
    }

    public float getLength() {
        float[] c = current();
        if (c == null) return 0;
        float len = 0;
        for (int i = 2; i < c.length; i += 2) {
            len += Math.hypot(c[i] - c[i - 2], c[i + 1] - c[i - 1]);
        }
        return len;
    }

    public boolean getPosTan(float distance, float[] pos, float[] tan) {
        float[] c = current();
        if (c == null || c.length < 2) return false;
        float remaining = Math.max(0, distance);
        for (int i = 2; i < c.length; i += 2) {
            float seg = (float) Math.hypot(c[i] - c[i - 2], c[i + 1] - c[i - 1]);
            if (remaining <= seg || i + 2 >= c.length) {
                float f = seg == 0 ? 0 : remaining / seg;
                if (pos != null && pos.length >= 2) {
                    pos[0] = c[i - 2] + (c[i] - c[i - 2]) * f;
                    pos[1] = c[i - 1] + (c[i + 1] - c[i - 1]) * f;
                }
                if (tan != null && tan.length >= 2) {
                    if (seg == 0) {
                        tan[0] = 0;
                        tan[1] = 0;
                    } else {
                        tan[0] = (c[i] - c[i - 2]) / seg;
                        tan[1] = (c[i + 1] - c[i - 1]) / seg;
                    }
                }
                return true;
            }
            remaining -= seg;
        }
        return false;
    }

    public boolean getSegment(float start, float stop, Path dst, boolean startWithMoveTo) {
        if (dst == null) return false;
        float[] pos = new float[2];
        if (!getPosTan(start, pos, null)) return false;
        if (startWithMoveTo) dst.moveTo(pos[0], pos[1]);
        else dst.lineTo(pos[0], pos[1]);
        if (!getPosTan(stop, pos, null)) return false;
        dst.lineTo(pos[0], pos[1]);
        return true;
    }

    public boolean isClosed() { return false; }
}
