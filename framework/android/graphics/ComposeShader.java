// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Composed shader shell: records pair and mode. */
public class ComposeShader extends Shader {
    private final Shader a;
    private final Shader b;
    private final PorterDuff.Mode mode;
    public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) { a = shaderA; b = shaderB; this.mode = mode; }
    public ComposeShader(Shader shaderA, Shader shaderB, BlendMode mode) { a = shaderA; b = shaderB; this.mode = null; }
}
