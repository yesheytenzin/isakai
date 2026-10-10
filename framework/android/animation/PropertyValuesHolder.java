// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Named value holder for property animation. */
public class PropertyValuesHolder {
    private final String propertyName;

    private PropertyValuesHolder(String propertyName) {
        this.propertyName = propertyName;
    }

    public static PropertyValuesHolder ofFloat(String propertyName, float... values) {
        return new PropertyValuesHolder(propertyName);
    }

    public static PropertyValuesHolder ofInt(String propertyName, int... values) {
        return new PropertyValuesHolder(propertyName);
    }

    public String getPropertyName() {
        return propertyName;
    }
}
