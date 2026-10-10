// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;

/** USB device descriptor shell. */
public class UsbDevice implements Parcelable {
    private final String name;
    private final int vendorId;
    private final int productId;
    private final int deviceClass;
    public static final Parcelable.Creator<UsbDevice> CREATOR = new Parcelable.Creator<UsbDevice>() {
        @Override public UsbDevice createFromParcel(Parcel source) { return new UsbDevice("", 0, 0, 0); }
        @Override public UsbDevice[] newArray(int size) { return new UsbDevice[size]; }
    };
    public UsbDevice(String name, int vendorId, int productId, int deviceClass) { this.name = name; this.vendorId = vendorId; this.productId = productId; this.deviceClass = deviceClass; }
    public String getDeviceName() { return name; }
    public int getVendorId() { return vendorId; }
    public int getProductId() { return productId; }
    public int getDeviceClass() { return deviceClass; }
    public int getDeviceSubclass() { return 0; }
    public int getDeviceProtocol() { return 0; }
    public int getInterfaceCount() { return 0; }
    public UsbInterface getInterface(int index) { return null; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { }
    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof UsbDevice)) return false; UsbDevice d = (UsbDevice) o; return vendorId == d.vendorId && productId == d.productId && eq(name, d.name); }
    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
    @Override public int hashCode() { return vendorId ^ productId; }
}
