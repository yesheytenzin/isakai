// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.usb;

import android.app.PendingIntent;
import java.util.HashMap;

/** USB accessories/devices; empty shell. */
public class UsbManager {
    public static final String ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_ACCESSORY_ATTACHED = "android.hardware.usb.action.USB_ACCESSORY_ATTACHED";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_ACCESSORY = "accessory";
    public static final String EXTRA_PERMISSION_GRANTED = "permission";
    public HashMap getDeviceList() { return new HashMap(); }
    public Object[] getAccessoryList() { return new Object[0]; }
    public boolean hasPermission(Object device) { return false; }
    public void requestPermission(Object device, PendingIntent pi) { }
    public android.os.ParcelFileDescriptor openDevice(Object device) { return null; }
    public android.os.ParcelFileDescriptor openAccessory(Object accessory) { return null; }
}
