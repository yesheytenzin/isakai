// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/**
 * Path effects describe dash/outline treatments for strokes. The host
 * rasterizer applies solid strokes only, so effects are plain values:
 * composable, comparable, and never consulted while drawing.
 */
public class PathEffect {
    public PathEffect() {}

    public static PathEffect createDashPathEffect(float[] intervals, float phase) {
        return new PathEffect();
    }

    public static PathEffect createCornerPathEffect(float radius) {
        return new PathEffect();
    }

    public static PathEffect createDiscretePathEffect(float segLength, float deviation) {
        return new PathEffect();
    }

    public static PathEffect createComposePathEffect(PathEffect outer, PathEffect inner) {
        return new PathEffect();
    }

    public static PathEffect createSumPathEffect(PathEffect first, PathEffect second) {
        return new PathEffect();
    }

    /** Dash treatment carrying its own intervals/phase (AOSP-shaped values). */
    public static class DashPathEffect extends PathEffect {
        public DashPathEffect(float[] intervals, float phase) {}
    }

    /** Rounded-corner treatment. */
    public static class CornerPathEffect extends PathEffect {
        public CornerPathEffect(float radius) {}
    }

    /** A path stamped along another path. */
    public static class PathDashPathEffect extends PathEffect {
        public PathDashPathEffect(Path shape, float advance, float phase, Style style) {}
        public enum Style { TRANSLATE, ROTATE, MORPH }
    }

    /** Runs the outer effect on the result of the inner one. */
    public static class ComposePathEffect extends PathEffect {
        public ComposePathEffect(PathEffect outer, PathEffect inner) {}
    }

    /** Applies both effects' results in sequence on the same path. */
    public static class SumPathEffect extends PathEffect {
        public SumPathEffect(PathEffect first, PathEffect second) {}
    }

    /** Breaks the path into discrete segments with jitter. */
    public static class DiscretePathEffect extends PathEffect {
        public DiscretePathEffect(float segLength, float deviation) {}
    }
}
