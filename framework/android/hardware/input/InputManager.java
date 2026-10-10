// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.input;

/** Input device enumeration; empty shell. */
public class InputManager {
    public static final int SW_HARD_KEYBOARD = 2;
    public int[] getInputDeviceIds() { return new int[0]; }
    public Object getInputDevice(int id) { return null; }
    public void registerInputDeviceListener(InputDeviceListener listener, android.os.Handler handler) { }
    public void unregisterInputDeviceListener(InputDeviceListener listener) { }
    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);
        void onInputDeviceRemoved(int deviceId);
        void onInputDeviceChanged(int deviceId);
    }
}
