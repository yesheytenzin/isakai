// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.camera2;

/** Camera enumeration; empty shell. */
public class CameraManager {
    public String[] getCameraIdList() { return new String[0]; }
    public Object getCameraCharacteristics(String cameraId) { return null; }
    public void openCamera(String cameraId, Object callback, android.os.Handler handler) { }
    public void registerAvailabilityCallback(AvailabilityCallback callback, android.os.Handler handler) { }
    public void unregisterAvailabilityCallback(AvailabilityCallback callback) { }
    public abstract static class AvailabilityCallback {
        public void onCameraAvailable(String cameraId) { }
        public void onCameraUnavailable(String cameraId) { }
    }
}
