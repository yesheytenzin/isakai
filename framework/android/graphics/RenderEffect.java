// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Render effect shell: blur/color-filter chains, unsupported. */
public class RenderEffect {
    private RenderEffect() {}

    public static RenderEffect createBlurEffect(float radiusX, float radiusY, Shader.TileMode edgeTreatment) {
        return new RenderEffect();
    }

    public static RenderEffect createColorFilterEffect(ColorFilter colorFilter) {
        return new RenderEffect();
    }

    public static RenderEffect createChainEffect(RenderEffect outer, RenderEffect inner) {
        return new RenderEffect();
    }

    public static RenderEffect createBlendModeEffect(RenderEffect dst, RenderEffect src, BlendMode blendMode) {
        return new RenderEffect();
    }
}
