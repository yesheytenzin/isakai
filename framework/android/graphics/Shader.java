// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Shader base: records the local matrix; rendering ignores the fill. */
public class Shader {
    public enum TileMode { CLAMP, REPEAT, MIRROR, DECAL }

    private Matrix localMatrix;

    public boolean setLocalMatrix(Matrix local) {
        this.localMatrix = local == null ? null : new Matrix(local);
        return true;
    }

    public Matrix getLocalMatrix() {
        return localMatrix;
    }
}
