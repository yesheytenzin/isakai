// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;
/** Virtual aggregate for input delivered through an active host window. No physical-device discovery. */
public final class InputDevice {
    public static final int SOURCE_KEYBOARD=0x101,SOURCE_MOUSE=0x2002,SOURCE_TOUCHSCREEN=0x1002;
    private static final InputDevice HOST=new InputDevice();
    private InputDevice() {}
    public static InputDevice getDevice(int id) { return id==0 && Boolean.getBoolean("isakai.windowed")?HOST:null; }
    public static int[] getDeviceIds() { return Boolean.getBoolean("isakai.windowed")?new int[]{0}:new int[0]; }
    public int getId(){return 0;}
    public String getName(){return "Isakai host window input";}
    public boolean isVirtual(){return true;}
    public int getSources(){return SOURCE_KEYBOARD|SOURCE_MOUSE|SOURCE_TOUCHSCREEN;}
    public boolean supportsSource(int source){return (getSources() & source)==source;}
}
